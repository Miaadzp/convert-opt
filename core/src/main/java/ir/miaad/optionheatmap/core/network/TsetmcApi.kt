package ir.miaad.optionheatmap.core.network

import ir.miaad.optionheatmap.core.diagnostics.DiagnosticLog
import ir.miaad.optionheatmap.core.BuildConfig
import ir.miaad.optionheatmap.core.monitor.BestLimit1
import ir.miaad.optionheatmap.core.monitor.DayLimits
import ir.miaad.optionheatmap.core.monitor.PriceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.net.URL
import java.net.URLEncoder

data class InstrumentSearchHit(val insCode: String, val symbol: String, val fullName: String)

class TsetmcApi {
    companion object {
        private const val BASE_URL = "https://cdn.tsetmc.com/api/"
        private const val TIMEOUT_MS = 6_000
        private const val TAG = "OH-DIAG-HTTP"
        private val DEBUG_TRUST_ALL_HOSTS = setOf("cdn.tsetmc.com")
    }

    suspend fun searchInstruments(symbol: String): List<InstrumentSearchHit> {
        val encoded = URLEncoder.encode(symbol, "UTF-8").replace("+", "%20")
        val root = getJson("Instrument/GetInstrumentSearch/$encoded")
        val rows = root.optJSONArray("instrumentSearch")
            ?: throw IOException("پاسخ جست‌وجو فاقد instrumentSearch است")
        return buildList {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val code = row.optString("insCode", "").trim()
                val ticker = row.optString("lVal18AFC", "").trim()
                if (code.isNotEmpty() && ticker.isNotEmpty()) {
                    add(InstrumentSearchHit(code, ticker, row.optString("lVal30", "")))
                }
            }
        }
    }

    suspend fun getDayLimits(insCode: String): DayLimits {
        val root = getJson("Instrument/GetInstrumentInfo/$insCode")
        val info = root.optJSONObject("instrumentInfo")
            ?: throw IOException("پاسخ فاقد instrumentInfo است")
        val threshold = info.optJSONObject("staticThreshold")
            ?: throw IOException("حدود مجاز قیمت در پاسخ موجود نیست")
        val max = requiredDouble(threshold, "psGelStaMax")
        val min = requiredDouble(threshold, "psGelStaMin")
        if (max <= 0.0 || min <= 0.0) throw IOException("حدود مجاز قیمت نامعتبر است")
        return DayLimits(max, min)
    }

    suspend fun getPriceInfo(insCode: String): PriceInfo {
        val root = getJson("ClosingPrice/GetClosingPriceInfo/$insCode")
        val info = root.optJSONObject("closingPriceInfo")
            ?: throw IOException("پاسخ فاقد closingPriceInfo است")
        val state = info.optJSONObject("instrumentState")
        return PriceInfo(
            lastTrade = requiredDouble(info, "pDrCotVal"),
            yesterday = requiredDouble(info, "priceYesterday"),
            totalVolume = requiredDouble(info, "qTotTran5J"),
            cEtaval = state?.optString("cEtaval", null)
        )
    }

    suspend fun getBestLimit1(insCode: String): BestLimit1 {
        val root = getJson("BestLimits/$insCode")
        val rows = root.optJSONArray("bestLimits")
            ?: throw IOException("پاسخ فاقد bestLimits است")
        var first: JSONObject? = null
        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            if (row.optInt("number", -1) == 1) { first = row; break }
        }
        val row = first ?: throw IOException("سطر شماره 1 سفارش‌ها پیدا نشد")
        return BestLimit1(
            bidPrice = requiredDouble(row, "pMeDem"),
            bidQty = requiredDouble(row, "qTitMeDem"),
            askPrice = requiredDouble(row, "pMeOf"),
            askQty = requiredDouble(row, "qTitMeOf")
        )
    }

    private suspend fun getJson(path: String): JSONObject = withContext(Dispatchers.IO) {
        val url = BASE_URL + path
        val startedAt = System.currentTimeMillis()
        var statusCode: Int? = null
        var contentType: String? = null
        var bodyBytes = -1
        DiagnosticLog.i(TAG, "HTTP_START time=$startedAt url=$url")

        val connection = (URL(url).openConnection() as HttpsURLConnection).apply {
            // Debug-only certificate-chain bypass, restricted to the exact TSETMC API host.
            // Hostname verification is deliberately left at the platform default.
            if (BuildConfig.TSETMC_TRUST_ALL && urlHostIsAllowedForDebug(url)) {
                sslSocketFactory = debugTrustAllSocketFactory
            }
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 OptionHeatmap Android")
        }

        try {
            statusCode = connection.responseCode
            contentType = connection.contentType
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            bodyBytes = body.toByteArray(Charsets.UTF_8).size
            val duration = System.currentTimeMillis() - startedAt
            DiagnosticLog.i(TAG, "HTTP_RESPONSE url=$url status=$statusCode contentType=$contentType bodyBytes=$bodyBytes durationMs=$duration")

            if (statusCode !in 200..299) {
                throw IOException("TSETMC HTTP $statusCode: ${body.take(160)}")
            }
            if (body.isBlank()) throw IOException("پاسخ خالی از TSETMC")

            val json = try {
                JSONObject(body)
            } catch (parseError: Exception) {
                DiagnosticLog.e(TAG, "JSON_PARSE_ERROR url=$url bodyBytes=$bodyBytes", parseError)
                throw parseError
            }
            DiagnosticLog.i(TAG, "JSON_PARSE_OK url=$url rootKeys=${json.keys().asSequence().toList()}")
            json
        } catch (error: Exception) {
            val duration = System.currentTimeMillis() - startedAt
            val causes = generateSequence(error as Throwable?) { it.cause }
                .map { "${it.javaClass.name}: ${it.message}" }
                .joinToString(" <- ")
            DiagnosticLog.e(
                TAG,
                "HTTP_FAILURE url=$url status=${statusCode ?: "UNAVAILABLE"} contentType=$contentType bodyBytes=$bodyBytes durationMs=$duration exception=${error.javaClass.name} message=${error.message} causes=$causes\n${DiagnosticLog.stackTrace(error)}"
            )
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun urlHostIsAllowedForDebug(url: String): Boolean {
        val host = URL(url).host.lowercase()
        return host in DEBUG_TRUST_ALL_HOSTS
    }

    private val debugTrustAllSocketFactory by lazy {
        val trustManager = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        }
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        }.socketFactory
    }
    private fun requiredDouble(json: JSONObject, key: String): Double {
        if (!json.has(key) || json.isNull(key)) throw IOException("فیلد $key در پاسخ موجود نیست")
        val value = json.optDouble(key, Double.NaN)
        if (!value.isFinite()) throw IOException("فیلد $key مقدار عددی معتبر ندارد")
        return value
    }
}
