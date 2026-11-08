package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.AppearancePrefs
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.Themes
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text

/**
 * 外觀：主題色、明暗、字體、字體大小、介面大小、圓角、數字等寬、收支顏色。
 * 最上面是即時預覽（改了立刻看到整個 App 的樣子）。
 */
@Composable
fun AppearanceScreen(nav: NavController) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    Scaffold(topBar = { BackTopBar("外觀", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Preview()

            Group("主題色") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Themes.all.forEach { p ->
                        val on = s.themePreset == p.key
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { repo.settings.update { it.copy(themePreset = p.key) } }) {
                            Box(
                                Modifier.size(38.dp).background(p.swatch, CircleShape)
                                    .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                            Text(p.label, fontSize = AppText.label, color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            Group("明暗") { Segments(listOf("跟隨系統", "淺色", "深色"), s.themeMode) { v -> repo.settings.update { it.copy(themeMode = v) } } }
            Group("字體") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Themes.fonts.forEachIndexed { i, l ->
                        val on = s.fontChoice == i
                        Surface(
                            color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.weight(1f).border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                .clickable { repo.settings.update { it.copy(fontChoice = i) } },
                        ) {
                            Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("帳理 123", fontSize = AppText.title, fontFamily = Themes.family(i))
                                Text(l, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Themes.family(i))
                            }
                        }
                    }
                }
                Text("「系統字體」會使用手機設定裡的字型", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Group("字體大小") {
                val opts = listOf(0.9f to "小", 1f to "標準", 1.1f to "大", 1.2f to "特大")
                Segments(opts.map { it.second }, opts.indexOfFirst { kotlin.math.abs(it.first - s.textScale) < 0.01f }.coerceAtLeast(0)) { i ->
                    repo.settings.update { it.copy(textScale = opts[i].first) }
                }
            }
            Group("介面大小") {
                val opts = listOf(0.85f to "較小", 0.92f to "小", 1f to "標準", 1.1f to "大")
                Segments(opts.map { it.second }, opts.indexOfFirst { kotlin.math.abs(it.first - s.uiScale) < 0.01f }.coerceAtLeast(2)) { i ->
                    repo.settings.update { it.copy(uiScale = opts[i].first) }
                }
                Text("字體大小只放大文字；介面大小連同間距、按鈕一起縮放", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            }
            Group("交易列樣式") { Segments(listOf("圖示", "經典（左邊分類欄）"), s.rowStyle) { v -> repo.settings.update { it.copy(rowStyle = v) } } }
            Group("圓角") { Segments(Themes.corners, s.cornerStyle) { v -> repo.settings.update { it.copy(cornerStyle = v) } } }
            Group("收支顏色") {
                Segments(listOf("收入藍、支出紅", "收入紅、支出藍"), if (s.colorSetB) 1 else 0) { v -> repo.settings.update { it.copy(colorSetB = v == 1) } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("數字等寬", fontSize = AppText.body)
                    Text("金額的位數上下對齊，比較整齊", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(s.tabularNums, { v -> repo.settings.update { it.copy(tabularNums = v) } })
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TextButton(onClick = {
                repo.settings.update { it.copy(themePreset = "coral", themeMode = 0, fontChoice = 0, textScale = 1f, uiScale = 1f, cornerStyle = 0, tabularNums = true, colorSetB = false) }
            }) { Text("恢復預設") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 一組設定：小標題＋內容。 */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Column {
        Text(title, fontSize = AppText.caption, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp), modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** 分段選擇（細線框、選中填色）。 */
@Composable
private fun Segments(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
    val shape = MaterialTheme.shapes.small
    Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        options.forEachIndexed { i, l ->
            val on = i == selected
            Box(
                Modifier.weight(1f).background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent, shape).clickable { onPick(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, fontSize = AppText.secondary, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, maxLines = 1) }
        }
    }
}

/** 即時預覽：一天的明細（日期列、兩筆交易）與一個摘要卡。 */
@Composable
private fun Preview() {
    val shape = RoundedCornerShape(AppearancePrefs.cardRadius)
    Surface(color = MaterialTheme.colorScheme.surface, shape = shape, modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("本月支出", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$ 18,420", fontSize = AppText.display, fontWeight = FontWeight.SemiBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("收入 $ 32,000", fontSize = AppText.caption, color = IncomeColor)
                    Text("結餘 $ 13,580", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            listOf(Triple("越香", "飲食／午餐 · 錢包現金", "$ 90"), Triple("台北捷運", "交通／捷運 · 悠遊卡", "$ 20")).forEach { (m, sub, a) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m, fontSize = AppText.body)
                        Text(sub, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(a, fontSize = AppText.body, color = ExpenseColor)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text("主要按鈕", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("標籤", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}
