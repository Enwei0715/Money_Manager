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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.DirectionsBus
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
 * 外觀：上方固定一塊預覽（改了立刻看到），下面分「色彩、文字、版面」三頁；
 * 每一頁的預覽只示範這一頁會改到的東西。
 */
@Composable
fun AppearanceScreen(nav: NavController) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(topBar = { BackTopBar("外觀", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // 固定在上方的預覽＋分頁
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 150.dp)) {
                    when (tab) {
                        0 -> ColorPreview()
                        1 -> TextPreview(s.tabularNums)
                        else -> LayoutPreview(s.showDescription)
                    }
                }
                // 快速套用：不想一項項調的人，選一組就好；之後還能在下面微調
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("快速套用", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    presets.forEach { (name, apply) ->
                        val on = apply(s) == s
                        Surface(
                            color = if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, shape = MaterialTheme.shapes.small,
                            modifier = Modifier.weight(1f).border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                                .clickable { repo.settings.update { apply(it) } },
                        ) { Text(name, fontSize = AppText.secondary, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp)) }
                    }
                }
                com.enwei.moneymanager.ui.components.SubTabs(listOf("色彩", "文字", "版面"), tab) { tab = it }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (tab) {
                    0 -> {
                        Group("主題色") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
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
                        Group("收支顏色") {
                            Segments(listOf("收入藍、支出紅", "收入紅、支出藍"), if (s.colorSetB) 1 else 0) { v -> repo.settings.update { it.copy(colorSetB = v == 1) } }
                        }
                    }
                    1 -> {
                        Group("字體") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Themes.fonts.indices.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { i ->
                                            val on = s.fontChoice == i
                                            Surface(
                                                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                                shape = MaterialTheme.shapes.medium,
                                                modifier = Modifier.weight(1f).border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                                                    .clickable { repo.settings.update { it.copy(fontChoice = i) } },
                                            ) {
                                                Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text("帳理 123", fontSize = AppText.title, fontFamily = Themes.family(i))
                                                    Text(Themes.fonts[i], fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = Themes.family(i))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Text("黑體、明體、楷體第一次選用時會從 Google Fonts 下載，下載完成前先用系統字體", fontSize = AppText.label,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        Group("字體大小") {
                            val opts = listOf(1.0f to "小", 1.1f to "標準", 1.2f to "大", 1.3f to "特大")
                            Segments(opts.map { it.second }, opts.indexOfFirst { kotlin.math.abs(it.first - s.textScale) < 0.01f }.let { if (it < 0) 1 else it }) { i ->
                                repo.settings.update { it.copy(textScale = opts[i].first) }
                            }
                        }
                        SwitchRow("數字等寬", "金額的位數上下對齊", s.tabularNums) { v -> repo.settings.update { it.copy(tabularNums = v) } }
                    }
                    else -> {
                        Group("區塊") { Segments(listOf("線條分隔", "卡片"), if (s.cardStyle) 1 else 0) { v -> repo.settings.update { it.copy(cardStyle = v == 1) } } }
                        Group("列表圖示") { Segments(listOf("顯示", "不顯示"), if (s.rowStyle == 0) 0 else 1) { v -> repo.settings.update { it.copy(rowStyle = v) } } }
                        Group("圓角") { Segments(Themes.corners, s.cornerStyle) { v -> repo.settings.update { it.copy(cornerStyle = v) } } }
                        Group("介面大小") {
                            val opts = listOf(0.85f to "較小", 0.92f to "小", 1f to "標準", 1.1f to "大")
                            Segments(opts.map { it.second }, opts.indexOfFirst { kotlin.math.abs(it.first - s.uiScale) < 0.01f }.let { if (it < 0) 2 else it }) { i ->
                                repo.settings.update { it.copy(uiScale = opts[i].first) }
                            }
                            Text("連同間距、按鈕一起縮放", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        }
                        SwitchRow("顯示說明", "交易列在店名下方顯示說明", s.showDescription) { v -> repo.settings.update { it.copy(showDescription = v) } }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = {
                    repo.settings.update { it.copy(themePreset = "coral", themeMode = 0, fontChoice = 2, textScale = 1.1f, uiScale = 1f, cornerStyle = 0,
                        tabularNums = true, colorSetB = false, cardStyle = false, rowStyle = 0) }
                }) { Text("全部恢復預設") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 推薦組合：一次設定字體、大小、區塊、圖示、圓角（顏色不動）。 */
private val presets: List<Pair<String, (com.enwei.moneymanager.data.AppSettings) -> com.enwei.moneymanager.data.AppSettings>> = listOf(
    "簡潔" to { it.copy(fontChoice = 2, textScale = 1.1f, cardStyle = false, rowStyle = 1, cornerStyle = 2, tabularNums = true) },
    "清晰" to { it.copy(fontChoice = 1, textScale = 1.2f, cardStyle = false, rowStyle = 0, cornerStyle = 1, tabularNums = true) },
    "卡片" to { it.copy(fontChoice = 0, textScale = 1.1f, cardStyle = true, rowStyle = 0, cornerStyle = 0, tabularNums = true) },
)

@Composable
private fun SwitchRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = AppText.body)
            Text(sub, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(on, onChange)
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

/** 色彩預覽：主色按鈕、標籤、收入／支出顏色、表面與底色。 */
@Composable
private fun ColorPreview() {
    val shape = RoundedCornerShape(AppearancePrefs.cardRadius)
    Surface(color = MaterialTheme.colorScheme.surface, shape = shape, modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("本月支出", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$ 18,420", fontSize = AppText.display, fontWeight = FontWeight.SemiBold, color = ExpenseColor)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("收入 $ 32,000", fontSize = AppText.caption, color = IncomeColor)
                    Text("結餘 $ 13,580", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text("主要按鈕", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small) {
                    Text("標籤", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("連結文字", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 文字預覽：標題、內文、說明三種字級，以及上下對齊的金額。 */
@Composable
private fun TextPreview(tabular: Boolean) {
    val shape = RoundedCornerShape(AppearancePrefs.cardRadius)
    Surface(color = MaterialTheme.colorScheme.surface, shape = shape, modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
        Row(Modifier.padding(16.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("十一月帳本", fontSize = AppText.title, fontWeight = FontWeight.SemiBold)
                Text("越香的午餐、捷運通勤", fontSize = AppText.body)
                Text("說明文字：小字與次要資訊", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("1,111", "18,420", "907").forEach { Text("$ $it", fontSize = AppText.body) }
                Text(if (tabular) "位數對齊" else "不對齊", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** 版面預覽：用真的區塊元件畫兩列帳目（跟著區塊樣式、圖示、圓角、說明一起變）。 */
@Composable
private fun LayoutPreview(showDesc: Boolean) {
    com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
        listOf(
            Triple(Icons.Outlined.Restaurant, "越香", "午餐"),
            Triple(Icons.Outlined.DirectionsBus, "台北捷運", "上班通勤"),
            Triple(Icons.Outlined.AccountBalance, "國泰簽帳", null),
        ).forEachIndexed { i, (ic, name, desc) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (AppearancePrefs.icons) {
                    Box(Modifier.size(34.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(ic, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(name, fontSize = AppText.body)
                    if (showDesc && desc != null) Text(desc, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (i == 2) "$ 51,733" else if (i == 0) "-$ 90" else "-$ 20", fontSize = AppText.body, color = if (i == 2) IncomeColor else ExpenseColor)
            }
            if (i < 2) HorizontalDivider(Modifier.padding(start = if (AppearancePrefs.icons) 62.dp else 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        }
    }
}
