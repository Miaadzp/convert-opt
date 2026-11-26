package ir.miaad.optionheatmap.core.monitor

enum class QueueSide { BUY, SELL, NONE }

data class BestLimit1(
    val bidPrice: Double,
    val bidQty: Double,
    val askPrice: Double,
    val askQty: Double
)

data class DayLimits(val maxPrice: Double, val minPrice: Double)

data class PriceInfo(
    val lastTrade: Double,
    val yesterday: Double,
    val totalVolume: Double,
    val cEtaval: String? = null
)

data class MonitorInput(
    val timeMs: Long,
    val best: BestLimit1,
    val info: PriceInfo,
    val limits: DayLimits?,
    val preOpen: Boolean
)
