package ir.miaad.optionheatmap.core.network

import ir.miaad.optionheatmap.core.monitor.BestLimit1
import ir.miaad.optionheatmap.core.monitor.DayLimits
import ir.miaad.optionheatmap.core.monitor.PriceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class InstrumentSearchHit(val insCode: String, val symbol: String, val fullName: String)

class TsetmcApi {
    companion object {
        private const val BASE_URL = "https://cdn.tsetmc.com/api/"
        private const val TIMEOUT_MS = 6_000
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
        val connection = (URL(BASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mozilla/5.0 OptionHeatmap Android")
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("TSETMC HTTP $code: ${body.take(160)}")
            if (body.isBlank()) throw IOException("پاسخ خالی از TSETMC")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun requiredDouble(json: JSONObject, key: String): Double {
        if (!json.has(key) || json.isNull(key)) throw IOException("فیلد $key در پاسخ موجود نیست")
        val value = json.optDouble(key, Double.NaN)
        if (!value.isFinite()) throw IOException("فیلد $key مقدار عددی معتبر ندارد")
        return value
    }
}
