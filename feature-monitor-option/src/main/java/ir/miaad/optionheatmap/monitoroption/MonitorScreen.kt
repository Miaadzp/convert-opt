package ir.miaad.optionheatmap.monitoroption

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sin

// ---------- رنگ‌ها ----------
// چهار رنگ اول را خود میاد داده؛ بقیه حدسی از روی عکس‌اند و بعداً اصلاح می‌شوند
object MonitorColors {
    val Dark = Color(0xFF1F2630)      // پس‌زمینه تیره
    val Light = Color(0xFF29313C)     // پس‌زمینه روشن
    val Up = Color(0xFF2EBB84)        // تغییر مثبت
    val Down = Color(0xFFF2475F)      // تغییر منفی
    val TextMain = Color(0xFFE6EAF0)  // حدسی
    val TextDim = Color(0xFF8A94A3)   // حدسی
    val Neutral = Color(0xFF3A4452)   // حدسی (تغییر صفر)
    val Accent = Color(0xFFF5C84C)    // حدسی (دکمه تنظیمات)
    val Spark = Color(0xFF8EA0B5)     // حدسی (نمودار)
}

// ---------- مدل داده ----------
enum class QueueState { BUY, SELL, NONE }

data class MonitorRow(
    val symbol: String,
    val changePct: Double,          // درصد تغییر نسبت به قیمت پایانی دیروز
    val price: Long,
    val queueState: QueueState,
    val queueVolume: Double,
    val queueChangePct: Double?,
    val volumeRatio: Double?,
    val tradable: Boolean?,         // null = نامشخص (نقطه نشان داده نمی‌شود)
    val spark: List<Float>          // مقدارهای ۰ تا ۱، هر عضو یک دقیقه از ساعت ۸:۴۵
)

private const val TOTAL_SLOTS = 225 // ۸:۴۵ تا ۱۲:۳۰ = ۲۲۵ دقیقه

// ---------- قالب‌بندی (همه عددها انگلیسی) ----------
private fun fmtPrice(v: Long): String = String.format(Locale.US, "%,d", v)

private fun fmtShort(n: Double): String = when {
    n >= 1e9 -> String.format(Locale.US, "%.2fB", n / 1e9)
    n >= 1e6 -> String.format(Locale.US, "%.2fM", n / 1e6)
    n >= 1e3 -> String.format(Locale.US, "%.1fK", n / 1e3)
    else -> String.format(Locale.US, "%d", n.toLong())
}

private fun fmtSigned(v: Double): String =
    if (abs(v) < 0.005) "0.00%" else String.format(Locale.US, "%+.2f%%", v)

private fun fmtQueueChange(v: Double): String =
    if (abs(v) < 0.05) "0.0%" else String.format(Locale.US, "%+.1f%%", v)

// ---------- داده تستی ----------
private fun fakeSpark(seed: Int, count: Int): List<Float> = List(count) { i ->
    (0.5 + 0.32 * sin((i + seed * 7) / 9.0) + 0.1 * sin(i * 0.7 + seed))
        .toFloat().coerceIn(0f, 1f)
}

private fun sampleRows(): List<MonitorRow> = listOf(
    MonitorRow("اهرم", -0.52, 95687, QueueState.BUY, 4.24e6, 0.0, 1.0, true, fakeSpark(1, 150)),
    MonitorRow("تابان", -0.34, 28590, QueueState.BUY, 56.49e6, -2.4, 1.2, true, fakeSpark(2, 150)),
    MonitorRow("خساپا", 0.09, 685, QueueState.BUY, 486.2e6, 0.0, 0.8, true, fakeSpark(3, 150)),
    MonitorRow("شستا", 10.56, 3590, QueueState.NONE, 0.0, null, 3.4, true, fakeSpark(4, 150)),
    MonitorRow("فملی", -0.52, 26140, QueueState.NONE, 0.0, null, 1.0, true, fakeSpark(5, 150)),
    MonitorRow("وبصادر", -0.34, 1075, QueueState.NONE, 0.0, null, null, true, fakeSpark(6, 150)),
    MonitorRow("وبملت", 0.09, 1540, QueueState.SELL, 12.5e6, -8.1, 2.1, true, fakeSpark(7, 150)),
    MonitorRow("فولاد", 0.0, 5270, QueueState.BUY, 239.37e6, 0.0, 1.0, false, fakeSpark(8, 150)),
    MonitorRow("آکو", 10.56, 49810, QueueState.BUY, 5.32e6, 0.0, 1.1, true, fakeSpark(9, 150)),
    MonitorRow("تاپیکو", 10.56, 46000, QueueState.NONE, 0.0, null, null, null, emptyList())
)

// ---------- عرض ستون‌ها (از راست به چپ) ----------
private const val W_SYM = 1.15f
private const val W_CHG = 1.25f
private const val W_PRICE = 1.15f
private const val W_STATE = 1.35f
private const val W_QVOL = 1.0f
private const val W_QCHG = 1.0f
private const val W_RATIO = 1.0f
private const val W_SPARK = 1.4f

@Composable
fun MonitorScreen(rows: List<MonitorRow> = sampleRows()) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MonitorColors.Dark)
    ) {
        TopBar()
        HeaderRow()
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(rows) { index, row ->
                MonitorRowView(row, striped = index % 2 == 1)
            }
        }
    }
}

// ---------- نوار بالا ----------
@Composable
private fun TopBar() {
    var monitorOn by remember { mutableStateOf(true) }
    var soundOn by remember { mutableStateOf(true) }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MonitorColors.Light)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            CheckLine("رصد روشن", monitorOn) { monitorOn = it }
            CheckLine("صدا و لرزش", soundOn) { soundOn = it }
        }
        TopButton("تنظیمات", accent = true)
        TopButton("فهرست هشدارها")
        TopButton("ساختار رصد")
        TopButton("واچ‌لیست")
    }
}

@Composable
private fun CheckLine(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) MonitorColors.Accent else MonitorColors.Neutral),
            contentAlignment = Alignment.Center
        ) {
            if (checked) Text("✓", color = MonitorColors.Dark, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(6.dp))
        Text(label, color = MonitorColors.TextMain, fontSize = 11.sp)
    }
}

@Composable
private fun TopButton(label: String, accent: Boolean = false) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (accent) MonitorColors.Accent else MonitorColors.Light)
            .clickable { /* بعداً وصل می‌شود */ }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (accent) MonitorColors.Dark else MonitorColors.TextMain,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// ---------- سرستون ----------
@Composable
private fun HeaderRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MonitorColors.Dark)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("نماد", W_SYM)
        HeaderCell("تغییر", W_CHG)
        HeaderCell("قیمت", W_PRICE)
        HeaderCell("وضعیت", W_STATE)
        HeaderCell("حجم صف", W_QVOL)
        HeaderCell("تغییر صف", W_QCHG)
        HeaderCell("حجم/میانگین", W_RATIO)
        HeaderCell("نمودار", W_SPARK)
    }
    Spacer(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MonitorColors.Light)
    )
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Box(Modifier.weight(weight), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = MonitorColors.TextDim,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

@Composable
private fun RowScope.Cell(weight: Float, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight), contentAlignment = Alignment.Center) { content() }
}

// ---------- هر ردیف ----------
@Composable
private fun MonitorRowView(row: MonitorRow, striped: Boolean) {
    val bg = if (striped) MonitorColors.Light else MonitorColors.Dark
    val tradable = row.tradable
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ۱) نماد و نقطه وضعیت مجاز/غیرمجاز
        Cell(W_SYM) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.symbol, color = MonitorColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                if (tradable != null) {
                    Spacer(Modifier.width(4.dp))
                    Dot(if (tradable) MonitorColors.Up else MonitorColors.Down, 7)
                }
            }
        }
        // ۲) درصد تغییر قیمت داخل کادر رنگی
        Cell(W_CHG) { ChangeChip(row.changePct) }
        // ۳) آخرین قیمت
        Cell(W_PRICE) {
            Text(fmtPrice(row.price), color = MonitorColors.TextMain, fontSize = 12.sp, maxLines = 1)
        }
        // ۴) وضعیت صف
        Cell(W_STATE) { QueueStateView(row.queueState) }
        // ۵) حجم صف
        Cell(W_QVOL) {
            Text(
                if (row.queueState == QueueState.NONE) "—" else fmtShort(row.queueVolume),
                color = MonitorColors.TextMain, fontSize = 11.sp, maxLines = 1
            )
        }
        // ۶) تغییر صف
        Cell(W_QCHG) {
            val q = row.queueChangePct
            if (row.queueState == QueueState.NONE || q == null) {
                Text("—", color = MonitorColors.TextDim, fontSize = 11.sp)
            } else {
                Text(
                    fmtQueueChange(q),
                    color = if (q < 0) MonitorColors.Down else MonitorColors.TextMain,
                    fontSize = 11.sp, maxLines = 1
                )
            }
        }
        // ۷) حجم ۱۰ ثانیه به میانگین ۵ دقیقه
        Cell(W_RATIO) {
            val r = row.volumeRatio
            Text(
                if (r == null) "—" else String.format(Locale.US, "x%.1f", r),
                color = if (r != null && r >= 3.0) MonitorColors.Down else MonitorColors.TextMain,
                fontSize = 11.sp, maxLines = 1
            )
        }
        // ۸) نمودار حجم صف
        Cell(W_SPARK) {
            Sparkline(row.spark, Modifier.fillMaxWidth().height(26.dp))
        }
    }
}

@Composable
private fun Dot(color: Color, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun ChangeChip(pct: Double) {
    val c = when {
        pct > 0.005 -> MonitorColors.Up
        pct < -0.005 -> MonitorColors.Down
        else -> MonitorColors.Neutral
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(c)
            .padding(horizontal = 5.dp, vertical = 3.dp)
    ) {
        Text(fmtSigned(pct), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun QueueStateView(state: QueueState) {
    when (state) {
        QueueState.BUY -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("صف خرید", color = MonitorColors.Up, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Dot(MonitorColors.Up, 6)
        }
        QueueState.SELL -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("صف فروش", color = MonitorColors.Down, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Dot(MonitorColors.Down, 6)
        }
        QueueState.NONE -> Text("—", color = MonitorColors.TextDim, fontSize = 11.sp)
    }
}

// ---------- نمودار کوچک: چپ = ۸:۴۵ ، راست = ۱۲:۳۰ ----------
@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawLine(Color(0x33FFFFFF), Offset(0f, h), Offset(w, h), strokeWidth = 1f)
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = w * i / (TOTAL_SLOTS - 1)
            val y = h - v.coerceIn(0f, 1f) * (h - 2f) - 1f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, MonitorColors.Spark, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}
