package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.ui.AppText
import kotlinx.coroutines.launch

/** 新手導覽：第一次開啟 App 時顯示，之後可從「設定 → 關於 → 新手導覽」再看一次。 */
object Onboarding {
    /** 要不要顯示導覽（設定頁可以手動打開） */
    val show = mutableStateOf(false)

    /** 一頁導覽：標題、一句說明、2～3 個步驟；可附一個「現在去設定」按鈕。appearance 頁直接在導覽裡選外觀。 */
    private data class Page(
        val icon: ImageVector, val title: String, val body: String, val steps: List<String> = emptyList(),
        val action: String? = null, val route: String? = null, val appearance: Boolean = false,
    )

    private val pages = listOf(
        Page(Icons.Outlined.Savings, "歡迎使用帳理", "記帳、預算、資產、往來與股票，集中在一個 App。",
            listOf("下方五個分頁：交易、統計、資產、助理、工具", "每個畫面的右上角都有該頁的設定與更多功能")),
        Page(Icons.Outlined.Palette, "先挑一個順眼的樣子", "之後隨時可以在「設定 → 外觀與顯示」調整。", appearance = true),
        Page(Icons.Outlined.EditNote, "記一筆", "交易頁右下的「＋」。",
            listOf("輸入金額、選分類與帳戶就完成", "上方切換「支出／收入／轉帳／往來／股票」", "幫朋友代墊、分帳選「往來」，會記在對方名下")),
        Page(Icons.Outlined.NotificationsActive, "通知自動記帳", "銀行、信用卡、支付 App 的付款通知會自動記成一筆。",
            listOf("不確定的放在交易頁上方的「待確認」", "開啟定位後會記下消費地點，統計頁可看支出地圖"), "開啟通知記帳", "notif"),
        Page(Icons.Outlined.PieChart, "統計與預算", "看錢花在哪、每月有沒有超支。",
            listOf("統計：分類圓餅、每月比較、支出地圖", "預算：快超過時提醒"), "設定預算", "budget"),
        Page(Icons.Outlined.AccountBalanceWallet, "資產與往來", "帳戶、信用卡、借貸與代墊都在「資產」。",
            listOf("信用卡依每期帳單算應繳與未出帳", "往來：誰欠你、你欠誰，一眼看清楚"), "新增帳戶", "account/0"),
        Page(Icons.Outlined.AutoAwesome, "AI 助理（選用）", "填入 Groq 或 Gemini 的免費金鑰後啟用。",
            listOf("判讀看不懂的通知、檢討收支", "股票助理：每日報告、新聞整理、問 AI"), "設定 AI", "ai"),
    )

    /** 第一次開啟（還沒看過導覽）時顯示；[onOpen] 開啟某個畫面。 */
    @Composable
    fun Host(onOpen: (String) -> Unit) {
        val settings by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(settings.onboarded) { if (!settings.onboarded) show.value = true }
        if (!show.value) return
        fun finish() {
            show.value = false
            MoneyApp.repo.settings.update { it.copy(onboarded = true) }
        }
        Dialog(onDismissRequest = { finish() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            val pager = rememberPagerState { pages.size }
            val scope = rememberCoroutineScope()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        if (pager.currentPage < pages.size - 1) TextButton(onClick = { finish() }) { Text("略過") }
                    }
                    // 進度條
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (pager.currentPage + 1f) / pages.size }, modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                        val p = pages[i]
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 32.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(p.icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(p.title, fontSize = AppText.display, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(p.body, fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp))
                            if (p.steps.isNotEmpty()) {
                                Spacer(Modifier.height(20.dp))
                                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                p.steps.forEachIndexed { n, st ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                                        Text("${n + 1}", fontSize = AppText.body, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.widthIn(min = 28.dp))
                                        Text(st, fontSize = AppText.body)
                                    }
                                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                            if (p.appearance) {
                                Spacer(Modifier.height(20.dp))
                                AppearanceQuick()
                            }
                            if (p.action != null && p.route != null) {
                                Spacer(Modifier.height(20.dp))
                                OutlinedButton(onClick = { finish(); onOpen(p.route) }) { Text(p.action) }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (pager.currentPage > 0) OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            modifier = Modifier.height(48.dp)) { Text("上一步") }
                        Button(onClick = {
                            if (pager.currentPage < pages.size - 1) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else finish()
                        }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(if (pager.currentPage < pages.size - 1) "下一步（${pager.currentPage + 1}/${pages.size}）" else "開始使用", fontSize = AppText.section)
                        }
                    }
                }
            }
        }
    }

    /** 導覽裡的快速外觀設定：主題色、區塊樣式、字體大小（改了整個 App 立刻套用，導覽本身就是預覽）。 */
    @Composable
    private fun AppearanceQuick() {
        val st by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("主題色", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                com.enwei.moneymanager.ui.Themes.all.forEach { t ->
                    val on = st.themePreset == t.key
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { MoneyApp.repo.settings.update { it.copy(themePreset = t.key) } }) {
                        Box(Modifier.size(36.dp).background(t.swatch, CircleShape)
                            .border(if (on) 2.dp else 1.dp, if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                        Text(t.label, fontSize = AppText.label, modifier = Modifier.padding(top = 4.dp),
                            color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("區塊", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choice(listOf("線條分隔", "卡片"), if (st.cardStyle) 1 else 0) { v -> MoneyApp.repo.settings.update { it.copy(cardStyle = v == 1) } }
            Text("字體大小", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val sizes = listOf(1.0f to "小", 1.1f to "標準", 1.2f to "大", 1.3f to "特大")
            Choice(sizes.map { it.second }, sizes.indexOfFirst { kotlin.math.abs(it.first - st.textScale) < 0.01f }.let { if (it < 0) 1 else it }) { i ->
                MoneyApp.repo.settings.update { it.copy(textScale = sizes[i].first) }
            }
            Text("字體", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Choice(com.enwei.moneymanager.ui.Themes.fonts, st.fontChoice) { v -> MoneyApp.repo.settings.update { it.copy(fontChoice = v) } }
        }
    }

    @Composable
    private fun Choice(options: List<String>, selected: Int, onPick: (Int) -> Unit) {
        val shape = MaterialTheme.shapes.small
        Row(Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)) {
            options.forEachIndexed { i, l ->
                val on = i == selected
                Box(Modifier.weight(1f).background(if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, shape)
                    .clickable { onPick(i) }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                    Text(l, fontSize = AppText.secondary, maxLines = 1, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}
