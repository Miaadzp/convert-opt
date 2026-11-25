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
import ir.miaad.optionheatmap.monitoroption.MonitorColors
import ir.miaad.optionheatmap.monitoroption.MonitorScreen

private val TAB_TITLES = listOf("رصد صف", "آپشن", "جزئیات سفارش", "پول هوشمند")

@Composable
fun AppShell() {
    var tab by remember { mutableIntStateOf(0) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                background = MonitorColors.Dark,
                surface = MonitorColors.Dark
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MonitorColors.Dark,
                contentColor = MonitorColors.TextMain
            ) {
                Column(Modifier.fillMaxSize().systemBarsPadding()) {
                    Box(Modifier.weight(1f)) {
                        if (tab == 0) {
                            MonitorScreen()
                        } else {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("این تب هنوز ساخته نشده", color = MonitorColors.TextDim)
                            }
                        }
                    }
                    TabBar(selected = tab, onSelect = { tab = it })
                }
            }
        }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MonitorColors.Light)
    ) {
        TAB_TITLES.forEachIndexed { index, title ->
            Box(
                Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    fontSize = 12.sp,
                    fontWeight = if (index == selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (index == selected) MonitorColors.Accent else MonitorColors.TextDim
                )
            }
        }
    }
}
