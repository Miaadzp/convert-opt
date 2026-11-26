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

object MonitorColors {
    val Dark = Color(0xFF1F2630)
    val Light = Color(0xFF29313C)
    val Up = Color(0xFF2EBB84)
    val Down = Color(0xFFF2475F)
    val TextMain = Color(0xFFE6EAF0)
    val TextDim = Color(0xFF8A94A3)
    val Neutral = Color(0xFF3A4452)
    val Accent = Color(0xFFF5C84C)
    val Spark = Color(0xFF8EA0B5)
}

enum class QueueState { BUY, SELL, NONE }

data class MonitorRow(
    val symbol: String,
    val changePct: Double,
    val price: Long,
    val queueState: QueueState,
    val queueVolume: Double,
    val queueChangePct: Double?,
    val volumeRatio: Double?,
    val tradable: Boolean?,
    val spark: List<Float>,
    val healthText: String = "",
    val lastUpdateText: String = ""
)

private const val TOTAL_SLOTS = 225
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

private const val W_SYM = 1.15f
private const val W_CHG = 1.25f
private const val W_PRICE = 1.15f
private const val W_STATE = 1.35f
private const val W_QVOL = 1.0f
private const val W_QCHG = 1.0f
private const val W_RATIO = 1.0f
private const val W_SPARK = 1.4f

@Composable
fun MonitorScreen(rows: List<MonitorRow> = emptyList()) {
    Column(Modifier.fillMaxSize().background(MonitorColors.Dark)) {
        TopBar()
        HeaderRow()
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("در انتظار دریافت داده از TSETMC...", color = MonitorColors.TextDim)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(rows) { index, row -> MonitorRowView(row, striped = index % 2 == 1) }
            }
        }
    }
}

@Composable
private fun TopBar() {
    var monitorOn by remember { mutableStateOf(true) }
    var soundOn by remember { mutableStateOf(true) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(10.dp)).background(MonitorColors.Light)
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
    Row(Modifier.clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(15.dp).clip(RoundedCornerShape(3.dp))
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
        Modifier.clip(RoundedCornerShape(10.dp))
            .background(if (accent) MonitorColors.Accent else MonitorColors.Light)
            .clickable { }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (accent) MonitorColors.Dark else MonitorColors.TextMain, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HeaderRow() {
    Row(
        Modifier.fillMaxWidth().background(MonitorColors.Dark).padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeaderCell("نماد", W_SYM); HeaderCell("تغییر", W_CHG); HeaderCell("قیمت", W_PRICE)
        HeaderCell("وضعیت", W_STATE); HeaderCell("حجم صف", W_QVOL); HeaderCell("تغییر صف", W_QCHG)
        HeaderCell("حجم/میانگین", W_RATIO); HeaderCell("نمودار", W_SPARK)
    }
    Spacer(Modifier.fillMaxWidth().height(1.dp).background(MonitorColors.Light))
}

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Box(Modifier.weight(weight), contentAlignment = Alignment.Center) {
        Text(text, color = MonitorColors.TextDim, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1)
    }
}
@Composable
private fun RowScope.Cell(weight: Float, content: @Composable () -> Unit) {
    Box(Modifier.weight(weight), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun MonitorRowView(row: MonitorRow, striped: Boolean) {
    val bg = if (striped) MonitorColors.Light else MonitorColors.Dark
    Row(Modifier.fillMaxWidth().background(bg).padding(horizontal = 6.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(W_SYM) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.symbol, color = MonitorColors.TextMain, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    if (row.tradable != null) {
                        Spacer(Modifier.width(4.dp))
                        Dot(if (row.tradable) MonitorColors.Up else MonitorColors.Down, 7)
                    }
                }
                if (row.healthText.isNotBlank()) Text(row.healthText, color = MonitorColors.TextDim, fontSize = 7.sp, textAlign = TextAlign.Center, maxLines = 2)
                if (row.lastUpdateText.isNotBlank()) Text(row.lastUpdateText, color = MonitorColors.TextDim, fontSize = 7.sp, maxLines = 1)
            }
        }
        Cell(W_CHG) { ChangeChip(row.changePct) }
        Cell(W_PRICE) { Text(fmtPrice(row.price), color = MonitorColors.TextMain, fontSize = 12.sp, maxLines = 1) }
        Cell(W_STATE) { QueueStateView(row.queueState) }
        Cell(W_QVOL) {
            Text(if (row.queueState == QueueState.NONE) "—" else fmtShort(row.queueVolume), color = MonitorColors.TextMain, fontSize = 11.sp, maxLines = 1)
        }
        Cell(W_QCHG) {
            val q = row.queueChangePct
            if (row.queueState == QueueState.NONE || q == null) Text("—", color = MonitorColors.TextDim, fontSize = 11.sp)
            else Text(fmtQueueChange(q), color = if (q < 0) MonitorColors.Down else MonitorColors.TextMain, fontSize = 11.sp, maxLines = 1)
        }
        Cell(W_RATIO) {
            val r = row.volumeRatio
            Text(if (r == null) "—" else String.format(Locale.US, "x%.1f", r),
                color = if (r != null && r >= 3.0) MonitorColors.Down else MonitorColors.TextMain, fontSize = 11.sp, maxLines = 1)
        }
        Cell(W_SPARK) { Sparkline(row.spark, Modifier.fillMaxWidth().height(26.dp)) }
    }
}

@Composable
private fun Dot(color: Color, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(CircleShape).background(color))
}
@Composable
private fun ChangeChip(pct: Double) {
    val color = when { pct > 0.005 -> MonitorColors.Up; pct < -0.005 -> MonitorColors.Down; else -> MonitorColors.Neutral }
    Box(Modifier.clip(RoundedCornerShape(4.dp)).background(color).padding(horizontal = 5.dp, vertical = 3.dp)) {
        Text(fmtSigned(pct), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
@Composable
private fun QueueStateView(state: QueueState) {
    when (state) {
        QueueState.BUY -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("صف خرید", color = MonitorColors.Up, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(4.dp)); Dot(MonitorColors.Up, 6)
        }
        QueueState.SELL -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("صف فروش", color = MonitorColors.Down, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.width(4.dp)); Dot(MonitorColors.Down, 6)
        }
        QueueState.NONE -> Text("—", color = MonitorColors.TextDim, fontSize = 11.sp)
    }
}
@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawLine(Color(0x33FFFFFF), Offset(0f, h), Offset(w, h), strokeWidth = 1f)
        if (values.size < 2) return@Canvas
        val path = Path()
        values.forEachIndexed { i, value ->
            val x = w * i / (TOTAL_SLOTS - 1)
            val y = h - value.coerceIn(0f, 1f) * (h - 2f) - 1f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, MonitorColors.Spark, style = Stroke(width = 2f, cap = StrokeCap.Round))
    }
}
