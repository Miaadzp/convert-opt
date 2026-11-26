package ir.miaad.optionheatmap.core.data

import ir.miaad.optionheatmap.core.diagnostics.DiagnosticLog
import ir.miaad.optionheatmap.core.monitor.BestLimit1
import ir.miaad.optionheatmap.core.monitor.DayLimits
import ir.miaad.optionheatmap.core.monitor.PriceInfo
import ir.miaad.optionheatmap.core.network.TsetmcApi
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class InstrumentRepository(private val api: TsetmcApi = TsetmcApi()) {
    private val instrumentCodes = ConcurrentHashMap<String, String>()

    suspend fun resolveInsCode(symbol: String): String {
        instrumentCodes[symbol]?.let {
            DiagnosticLog.i(TAG, "RESOLVE_CACHE symbol=$symbol insCode=$it")
            return it
        }
        val wanted = normalize(symbol)
        DiagnosticLog.i(TAG, "RESOLVE_START symbol=$symbol normalized=$wanted")
        val hits = api.searchInstruments(symbol)
        val matches = hits.filter { normalize(it.symbol) == wanted }.distinctBy { it.insCode }
        DiagnosticLog.i(TAG, "RESOLVE_RESULT symbol=$symbol totalHits=${hits.size} exactMatches=${matches.size} candidates=${matches.map { "${it.symbol}:${it.insCode}" }}")
        if (matches.isEmpty()) throw IOException("نماد دقیق «$symbol» در TSETMC پیدا نشد")
        if (matches.size > 1) throw IOException("برای «$symbol» چند کد دقیق پیدا شد؛ انتخاب خودکار امن نیست")
        return matches.single().insCode.also {
            instrumentCodes[symbol] = it
            DiagnosticLog.i(TAG, "RESOLVE_SELECTED symbol=$symbol insCode=$it")
        }
    }

    suspend fun fetchDayLimits(insCode: String): DayLimits = api.getDayLimits(insCode)
    suspend fun fetchPriceInfo(insCode: String): PriceInfo = api.getPriceInfo(insCode)
    suspend fun fetchBestLimit1(insCode: String): BestLimit1 = api.getBestLimit1(insCode)

    private fun normalize(value: String): String = value.trim()
        .replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "").replace(" ", "")

    private companion object {
        const val TAG = "OH-DIAG-Resolve"
    }
}
