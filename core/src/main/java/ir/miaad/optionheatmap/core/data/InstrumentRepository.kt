package ir.miaad.optionheatmap.core.data

import ir.miaad.optionheatmap.core.monitor.BestLimit1
import ir.miaad.optionheatmap.core.monitor.DayLimits
import ir.miaad.optionheatmap.core.monitor.PriceInfo
import ir.miaad.optionheatmap.core.network.TsetmcApi
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

class InstrumentRepository(private val api: TsetmcApi = TsetmcApi()) {
    private val instrumentCodes = ConcurrentHashMap<String, String>()

    suspend fun resolveInsCode(symbol: String): String {
        instrumentCodes[symbol]?.let { return it }
        val wanted = normalize(symbol)
        val matches = api.searchInstruments(symbol)
            .filter { normalize(it.symbol) == wanted }
            .distinctBy { it.insCode }
        if (matches.isEmpty()) throw IOException("نماد دقیق «$symbol» در TSETMC پیدا نشد")
        if (matches.size > 1) throw IOException("برای «$symbol» چند کد دقیق پیدا شد؛ انتخاب خودکار امن نیست")
        return matches.single().insCode.also { instrumentCodes[symbol] = it }
    }

    suspend fun fetchDayLimits(insCode: String): DayLimits = api.getDayLimits(insCode)
    suspend fun fetchPriceInfo(insCode: String): PriceInfo = api.getPriceInfo(insCode)
    suspend fun fetchBestLimit1(insCode: String): BestLimit1 = api.getBestLimit1(insCode)

    private fun normalize(value: String): String = value.trim()
        .replace('ي', 'ی').replace('ك', 'ک').replace("\u200c", "").replace(" ", "")
}
