package ir.miaad.optionheatmap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ir.miaad.optionheatmap.core.monitor.DataFreshness
import ir.miaad.optionheatmap.core.monitor.MonitorDataStore
import ir.miaad.optionheatmap.core.monitor.MonitorSymbolSnapshot
import ir.miaad.optionheatmap.core.monitor.QueueSide
import ir.miaad.optionheatmap.monitoroption.MonitorColors
import ir.miaad.optionheatmap.monitoroption.MonitorRow
import ir.miaad.optionheatmap.monitoroption.MonitorScreen
import ir.miaad.optionheatmap.monitoroption.QueueState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val TAB_TITLES = listOf("رصد صف", "آپشن", "جزئیات سفارش", "پول هوشمند")

@Composable
fun AppShell() {
    var tab by remember { mutableIntStateOf(0) }
    val snapshots by MonitorDataStore.snapshots.collectAsStateWithLifecycle()
    val rows = remember(snapshots) { snapshots.map { it.toMonitorRow() } }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = darkColorScheme(background = MonitorColors.Dark, surface = MonitorColors.Dark)) {
            Surface(Modifier.fillMaxSize(), color = MonitorColors.Dark, contentColor = MonitorColors.TextMain) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Box(Modifier.weight(1f)) {
                        if (tab == 0) MonitorScreen(rows)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("این تب هنوز ساخته نشده", color = MonitorColors.TextDim)
                        }
                    }
                    TabBar(selected = tab, onSelect = { tab = it })
                }
            }
        }
    }
}

private fun MonitorSymbolSnapshot.toMonitorRow(): MonitorRow {
    val side = result?.side ?: QueueSide.NONE
    val health = "قیمت ${priceHealth.status.label()} • صف ${bestHealth.status.label()} • حدود ${infoHealth.status.label()}"
    val time = lastCompleteCycleMs?.let {
        val formatter = SimpleDateFormat("HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Tehran")
        }
        "تحلیل ${formatter.format(Date(it))}"
    }.orEmpty()
    val change = price?.let {
        if (it.yesterday > 0.0) (it.lastTrade - it.yesterday) / it.yesterday * 100.0 else 0.0
    } ?: 0.0
    return MonitorRow(
        symbol = symbol,
        changePct = change,
        price = price?.lastTrade?.toLong() ?: 0L,
        queueState = when (side) {
            QueueSide.BUY -> QueueState.BUY
            QueueSide.SELL -> QueueState.SELL
            QueueSide.NONE -> QueueState.NONE
        },
        queueVolume = result?.queueVolume ?: 0.0,
        queueChangePct = result?.queueChangePct,
        volumeRatio = result?.volumeMultiple,
        tradable = tradable,
        spark = spark,
        healthText = health,
        lastUpdateText = time
    )
}

private fun DataFreshness.label(): String = when (this) {
    DataFreshness.WAITING -> "انتظار"
    DataFreshness.FRESH -> "تازه"
    DataFreshness.STALE -> "قدیمی"
    DataFreshness.ERROR -> "خطا"
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().background(MonitorColors.Light)) {
        TAB_TITLES.forEachIndexed { index, title ->
            Box(Modifier.weight(1f).clickable { onSelect(index) }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                Text(
                    title, fontSize = 12.sp,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == selected) MonitorColors.Accent else MonitorColors.TextDim
                )
            }
        }
    }
}
