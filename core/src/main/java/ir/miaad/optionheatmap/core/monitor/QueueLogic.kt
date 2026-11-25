package ir.miaad.optionheatmap.core.monitor

import kotlin.math.abs

object QueueLogic {

    private const val EPS = 1e-6

    private fun same(a: Double, b: Double): Boolean = abs(a - b) <= EPS

    /**
     * صف خرید: قیمت سطر اول خریدار = سقف مجاز، حجم سطر اول خریدار > 0،
     *          و (آخرین معامله = سقف مجاز، یا هنوز معاملات شروع نشده).
     * صف فروش: برعکس، با کف مجاز و سطر اول فروشنده.
     * اگر سقف و کف معلوم نباشد، نتیجه NONE است.
     */
    fun detect(best: BestLimit1, lastTrade: Double, limits: DayLimits?, preOpen: Boolean): QueueSide {
        if (limits == null) return QueueSide.NONE
        val buy = limits.maxPrice > 0 &&
            best.bidQty > 0 &&
            same(best.bidPrice, limits.maxPrice) &&
            (preOpen || same(lastTrade, limits.maxPrice))
        val sell = limits.minPrice > 0 &&
            best.askQty > 0 &&
            same(best.askPrice, limits.minPrice) &&
            (preOpen || same(lastTrade, limits.minPrice))
        return when {
            buy && !sell -> QueueSide.BUY
            sell && !buy -> QueueSide.SELL
            else -> QueueSide.NONE
        }
    }

    fun queueVolume(side: QueueSide, best: BestLimit1): Double = when (side) {
        QueueSide.BUY -> best.bidQty
        QueueSide.SELL -> best.askQty
        QueueSide.NONE -> 0.0
    }

    /** درصد تغییر آخرین قیمت نسبت به قیمت پایانی دیروز؛ اگر یکی از دو قیمت صفر باشد null */
    fun changePct(lastTrade: Double, yesterday: Double): Double? =
        if (lastTrade > 0 && yesterday > 0) (lastTrade - yesterday) / yesterday * 100.0 else null

    /** cEtaval با A (بعد از trim) یعنی مجاز؛ هر کد دیگر غیرمجاز؛ خالی یعنی نامشخص */
    fun isTradable(cEtaval: String?): Boolean? {
        val c = cEtaval?.trim()
        if (c.isNullOrEmpty()) return null
        return c == "A"
    }

    /** ۸:۴۵ تا قبل از ۹:۰۰ به وقت تهران (دقیقه از نیمه‌شب) */
    fun isPreOpen(minutesOfDayTehran: Int): Boolean = minutesOfDayTehran in (8 * 60 + 45) until (9 * 60)
}
