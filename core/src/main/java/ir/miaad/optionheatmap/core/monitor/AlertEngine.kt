package ir.miaad.optionheatmap.core.monitor

import java.util.Locale
import kotlin.math.roundToInt

enum class AlertKind { QUEUE_BUY_DROP, QUEUE_SELL_DROP, QUEUE_GONE, VOLUME_SPIKE }

data class Alert(
    val timeMs: Long,
    val symbol: String,
    val kind: AlertKind,
    val text: String
)

data class AlertConfig(
    val dropShortPct: Double = 10.0,    // افت صف نسبت به خوانش قبلی (حدود ۱۰ ثانیه)
    val dropPeakPct: Double = 20.0,     // افت صف از اوج ۱ دقیقه
    val volumeMultiple: Double = 3.0,   // جهش حجم نسبت به میانگین ۵ دقیقه
    val cooldownMs: Long = 30_000L      // فاصله بین دو هشدار هم‌نوع
)

data class MonitorResult(
    val side: QueueSide,
    val queueVolume: Double,
    val queueChangePct: Double?,
    val volumeMultiple: Double?,
    val alerts: List<Alert>
)

fun shortNum(n: Double): String = when {
    n >= 1e9 -> String.format(Locale.US, "%.2fB", n / 1e9)
    n >= 1e6 -> String.format(Locale.US, "%.2fM", n / 1e6)
    n >= 1e3 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> String.format(Locale.US, "%d", n.toLong())
}

/** وضعیت و منطق رصد یک نماد. برای هر نماد یک نمونه جدا بسازید. */
class SymbolMonitor(
    val symbol: String,
    private val config: AlertConfig = AlertConfig()
) {
    private data class QPoint(val t: Long, val q: Double)
    private data class VPoint(val t: Long, val vol: Double)

    private var historySide: QueueSide? = null
    private val queuePts = ArrayList<QPoint>()
    private var prevSide: QueueSide? = null
    private var prevQ = 0.0

    private val volHist = ArrayList<VPoint>()
    private var lastMult: Double? = null

    private val lastFire = HashMap<AlertKind, Long>()

    private fun sideName(side: QueueSide): String = if (side == QueueSide.BUY) "خرید" else "فروش"

    fun process(input: MonitorInput): MonitorResult {
        val t = input.timeMs
        val alerts = ArrayList<Alert>()

        fun fire(kind: AlertKind, text: String) {
            val last = lastFire[kind]
            if (last != null && t - last < config.cooldownMs) return
            lastFire[kind] = t
            alerts.add(Alert(t, symbol, kind, text))
        }

        // ---------- صف ----------
        val side = QueueLogic.detect(input.best, input.info.lastTrade, input.limits, input.preOpen)
        val q = QueueLogic.queueVolume(side, input.best)

        if (historySide != side) {
            historySide = side
            queuePts.clear()
        }
        val prevPt = queuePts.lastOrNull()
        queuePts.add(QPoint(t, q))
        queuePts.removeAll { t - it.t > 65_000L }

        val ps = prevSide
        if (ps != null && ps != QueueSide.NONE && side == QueueSide.NONE) {
            fire(
                AlertKind.QUEUE_GONE,
                "صف ${sideName(ps)} $symbol تموم شد! (قبلاً ${shortNum(prevQ)})"
            )
        }

        if (side != QueueSide.NONE && prevPt != null && prevPt.q > 0) {
            val d10 = (prevPt.q - q) / prevPt.q * 100.0
            val peak = queuePts.maxOf { it.q }
            val d60 = if (peak > 0) (peak - q) / peak * 100.0 else 0.0
            val kind = if (side == QueueSide.BUY) AlertKind.QUEUE_BUY_DROP else AlertKind.QUEUE_SELL_DROP
            val what = if (side == QueueSide.BUY) "دارن می‌فروشن" else "دارن می‌خرن"
            val name = sideName(side)
            if (d10 >= config.dropShortPct) {
                val secs = ((t - prevPt.t) / 1000.0).roundToInt()
                fire(
                    kind,
                    "صف $name $symbol در $secs ثانیه ${String.format(Locale.US, "%.0f", d10)}% کم شد " +
                        "(${shortNum(prevPt.q)} ← ${shortNum(q)}) — $what"
                )
            } else if (d60 >= config.dropPeakPct) {
                fire(
                    kind,
                    "صف $name $symbol از اوج 1 دقیقه‌ی اخیر ${String.format(Locale.US, "%.0f", d60)}% کم شده " +
                        "(${shortNum(peak)} ← ${shortNum(q)}) — $what"
                )
            }
        }

        val qChange: Double? =
            if (prevPt != null && prevPt.q > 0) (q - prevPt.q) / prevPt.q * 100.0 else null

        prevSide = side
        if (q > 0) prevQ = q

        // ---------- حجم ----------
        val vol = input.info.totalVolume
        var skipVolume = false
        val lastV = volHist.lastOrNull()
        if (lastV != null && vol < lastV.vol) {
            if (vol < lastV.vol * 0.5) {
                volHist.clear()          // روز جدید
            } else {
                skipVolume = true        // پاسخ قدیمی‌تر سرور، نادیده گرفته می‌شود
            }
        }

        var mult: Double? = lastMult
        if (!skipVolume) {
            mult = null
            volHist.add(VPoint(t, vol))
            volHist.removeAll { t - it.t > 420_000L }
            if (volHist.size >= 3) {
                val cur = volHist[volHist.size - 1]
                val p = volHist[volHist.size - 2]
                val dt = (cur.t - p.t) / 1000.0
                if (dt >= 3.0 && dt <= 40.0) {
                    val rateCur = (cur.vol - p.vol) / dt * 10.0
                    val from = volHist.first { it.t >= p.t - 300_000L }
                    val span = (p.t - from.t) / 1000.0
                    if (span >= 60.0) {
                        val rateBase = (p.vol - from.vol) / span * 10.0
                        if (rateBase > 0) {
                            val m = rateCur / rateBase
                            mult = m
                            if (rateCur > 0 && m >= config.volumeMultiple) {
                                fire(
                                    AlertKind.VOLUME_SPIKE,
                                    "حجم معامله‌ی $symbol در 10 ثانیه‌ی اخیر " +
                                        "${String.format(Locale.US, "%.1f", m)} برابر میانگین " +
                                        "${span.roundToInt()} ثانیه‌ی قبلش بود " +
                                        "(${shortNum(rateCur)} سهم در برابر ${shortNum(rateBase)})"
                                )
                            }
                        }
                    }
                }
            }
            lastMult = mult
        }

        return MonitorResult(side, q, qChange, mult, alerts)
    }
}
