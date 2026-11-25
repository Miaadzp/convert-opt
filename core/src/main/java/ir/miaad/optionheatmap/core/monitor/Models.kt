package ir.miaad.optionheatmap.core.monitor

enum class QueueSide { BUY, SELL, NONE }

/** سطر اول سفارش‌ها (BestLimits با number = 1) */
data class BestLimit1(
    val bidPrice: Double,   // pMeDem
    val bidQty: Double,     // qTitMeDem
    val askPrice: Double,   // pMeOf
    val askQty: Double      // qTitMeOf
)

/** سقف و کف مجاز قیمت امروز (staticThreshold.psGelStaMax / psGelStaMin) */
data class DayLimits(
    val maxPrice: Double,
    val minPrice: Double
)

/** اطلاعات قیمت (ClosingPriceInfo) */
data class PriceInfo(
    val lastTrade: Double,   // pDrCotVal
    val yesterday: Double,   // priceYesterday
    val totalVolume: Double  // qTotTran5J
)

/** یک خوانش کامل از یک نماد */
data class MonitorInput(
    val timeMs: Long,
    val best: BestLimit1,
    val info: PriceInfo,
    val limits: DayLimits?,
    val preOpen: Boolean     // true یعنی پیش از شروع معاملات (۸:۴۵ تا ۹:۰۰)
)
