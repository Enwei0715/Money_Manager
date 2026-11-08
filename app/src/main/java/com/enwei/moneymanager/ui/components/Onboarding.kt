package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.foundation.layout.systemBarsPadding
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

    /** 一頁導覽：標題、一句說明、2～3 個步驟；可附一個「現在去設定」按鈕。kind：0 一般、1 問卷、2 外觀。 */
    private data class Page(
        val icon: ImageVector, val title: String, val body: String, val steps: List<String> = emptyList(),
        val action: String? = null, val route: String? = null, val kind: Int = 0,
    )

    /** 問卷的選項：要用哪些功能（勾了就幫你打開，導覽也只介紹勾選的） */
    private data class Want(val key: String, val title: String, val desc: String)
    private val wants = listOf(
        Want("notif", "通知自動記帳", "銀行、信用卡、支付 App 的付款通知自動記成一筆"),
        Want("split", "代墊、分帳、借還", "幫朋友付錢、室友分攤房租、借錢還錢，記清楚誰欠誰"),
        Want("map", "支出地圖", "記下付款地點，之後在地圖上看錢花在哪"),
        Want("stocks", "股票與投資", "記錄股票買賣、看持股損益與 K 線"),
        Want("ai", "AI 助理", "AI 判讀通知、檢討收支、每日股票報告（需要免費金鑰）"),
    )
    /** 這次導覽勾了哪些（代墊沒有對應的設定，只影響導覽內容） */
    private val picked = androidx.compose.runtime.mutableStateOf<Set<String>?>(null)

    private fun pagesFor(w: Set<String>) = buildList {
        add(Page(Icons.Outlined.Savings, "歡迎使用帳理", "先花一分鐘告訴我你想怎麼用，幫你把用不到的功能收起來、設定好。",
            listOf("隨時可以按右上「略過」", "之後都能在「設定 → 功能開關」改")))
        add(Page(Icons.Outlined.Checklist, "你想用帳理做哪些事？", "勾了就幫你開啟；沒勾的先收起來，畫面比較簡單。", kind = 1))
        add(Page(Icons.Outlined.Palette, "挑一個順眼的樣子", "之後可在「設定 → 外觀與顯示」細調。", kind = 2))
        add(Page(Icons.Outlined.EditNote, "記一筆", "交易頁右下的「＋」。", listOfNotNull(
            "輸入金額、選分類與帳戶就完成",
            "上方切換支出／收入／轉帳" + (if ("stocks" in w) "／股票" else ""),
            if ("split" in w) "幫別人付錢：在支出裡點「分帳」，可以分給好幾個人" else null,
            if ("split" in w) "別人還錢：收入裡填「誰還的」；借錢、還錢：用「轉帳」選往來對象" else null,
        )))
        if ("notif" in w) add(Page(Icons.Outlined.NotificationsActive, "通知自動記帳", "需要允許讀取通知，App 才看得到付款通知。",
            listOf("不確定的放在交易頁上方的「待確認」", "可以選要聽哪些 App 的通知"), "現在去開啟", "notif"))
        add(Page(Icons.Outlined.PieChart, "統計與預算", "看錢花在哪、每月有沒有超支。", listOfNotNull(
            "統計：分類圓餅、每月比較", if ("map" in w) "支出地圖：統計頁右上的地圖圖示" else null, "預算：快超過時提醒"), "設定預算", "budget"))
        add(Page(Icons.Outlined.AccountBalanceWallet, "資產", "帳戶、信用卡" + (if ("split" in w) "、往來" else "") + "都在這裡。", listOfNotNull(
            "信用卡依每期帳單算應繳與未出帳", if ("split" in w) "往來：誰欠你、你欠誰，一眼看清楚" else null), "新增帳戶", "account/0"))
        if ("stocks" in w || "ai" in w) add(Page(Icons.Outlined.AutoAwesome, if ("ai" in w) "股票與 AI 助理" else "股票", "底部「助理」分頁。", listOfNotNull(
            if ("stocks" in w) "記錄股票買賣，看持股損益、K 線與除權息" else null,
            if ("ai" in w) "填入 Groq 或 Gemini 的免費金鑰後，可產生每日報告、問 AI" else null),
            if ("ai" in w) "設定 AI 金鑰" else null, if ("ai" in w) "ai" else null))
    }

    /** 第一次開啟（還沒看過導覽）時顯示；[onOpen] 開啟某個畫面。整頁蓋在 App 上（不用對話框，底部按鈕不會被導覽列擋住）。 */
    @Composable
    fun Host(onOpen: (String) -> Unit) {
        val settings by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
        androidx.compose.runtime.LaunchedEffect(settings.onboarded) { if (!settings.onboarded) show.value = true }
        if (!show.value) return
        // 問卷預設：依目前的設定
        val w = picked.value ?: buildSet {
            if (settings.featureNotif) add("notif"); if (settings.locationHints) add("map")
            if (settings.featureStocks) add("stocks"); if (settings.aiEnabled) add("ai"); add("split")
        }.also { picked.value = it }
        val pages = pagesFor(w)
        fun finish(tour: Boolean = false) {
            show.value = false
            MoneyApp.repo.settings.update { it.copy(onboarded = true) }
            if (tour) Coach.start(MoneyApp.repo.settings.value, "split" in w)
            picked.value = null
        }
        val pager = rememberPagerState { pages.size }
        val scope = rememberCoroutineScope()
        androidx.activity.compose.BackHandler {
            if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } else finish()
        }
        Surface(Modifier.fillMaxSize().clickable(interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { },
            color = MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        if (pager.currentPage < pages.size - 1) TextButton(onClick = { finish() }) { Text("略過") }
                    }
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { (pager.currentPage + 1f) / pages.size }, modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { i ->
                        val p = pages.getOrNull(i) ?: return@HorizontalPager
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 28.dp)) {
                            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(p.icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(20.dp))
                            Text(p.title, fontSize = AppText.display, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(8.dp))
                            Text(p.body, fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 420.dp))
                            when (p.kind) {
                                1 -> { Spacer(Modifier.height(16.dp)); Survey(w) }
                                2 -> { Spacer(Modifier.height(20.dp)); AppearanceQuick() }
                            }
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
                            if (p.action != null && p.route != null) {
                                Spacer(Modifier.height(20.dp))
                                OutlinedButton(onClick = { finish(); onOpen(p.route) }) { Text(p.action) }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (pager.currentPage > 0) OutlinedButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            modifier = Modifier.height(48.dp)) { Text("上一步") }
                        val last = pager.currentPage >= pages.size - 1
                        Button(onClick = {
                            if (!last) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else finish(tour = true)
                        }, modifier = Modifier.weight(1f).height(48.dp)) {
                            Text(if (!last) "下一步（${pager.currentPage + 1}/${pages.size}）" else "帶我逛一圈", fontSize = AppText.section)
                        }
                    }
                }
        }
    }

    /** 問卷：勾選要用的功能，立刻套用到設定（開啟／關閉對應功能）。 */
    @Composable
    private fun Survey(w: Set<String>) {
        Column {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // 基本功能一定有
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("記帳、預算與統計", fontSize = AppText.body)
                    Text("基本功能，一定會有", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CheckMark(true, enabled = false)
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            wants.forEach { o ->
                val on = o.key in w
                fun toggle() {
                    val next = if (on) w - o.key else w + o.key
                    picked.value = next
                    MoneyApp.repo.settings.update {
                        when (o.key) {
                            "notif" -> it.copy(featureNotif = !on)
                            "map" -> it.copy(locationHints = !on)
                            "stocks" -> it.copy(featureStocks = !on)
                            "ai" -> it.copy(aiEnabled = !on)
                            else -> it
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.title, fontSize = AppText.body)
                        Text(o.desc, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CheckMark(on)
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    /** 勾選框：固定大小，每一列都對齊（不用 Checkbox，停用狀態時大小會不同） */
    @Composable
    private fun CheckMark(on: Boolean, enabled: Boolean = true) {
        val c = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        Box(Modifier.padding(start = 12.dp).size(24.dp)
            .background(if (on) c else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(5.dp))
            .border(1.5.dp, if (on) c else MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center) {
            if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(18.dp))
        }
    }

    /** 外觀預覽：用真的元件畫一小段交易列表，選了什麼馬上看得到 */
    @Composable
    private fun MiniPreview() {
        com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("本月支出", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("$ 18,420", fontSize = AppText.title, fontWeight = FontWeight.SemiBold, color = com.enwei.moneymanager.ui.ExpenseColor)
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            listOf(Triple("越香", "飲食／午餐", "$ 90"), Triple("台北捷運", "交通／捷運", "$ 20")).forEachIndexed { i, (m, sub, a) ->
                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m, fontSize = AppText.body)
                        Text(sub, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(a, fontSize = AppText.body, color = com.enwei.moneymanager.ui.ExpenseColor)
                }
            }
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.small) {
                    Text("主要按鈕", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }

    /** 導覽裡的快速外觀設定：主題色、區塊樣式、字體大小（改了整個 App 立刻套用，導覽本身就是預覽）。 */
    @Composable
    private fun AppearanceQuick() {
        val st by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MiniPreview()
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
            val sizes = listOf(0.9f to "小", 1.0f to "標準", 1.1f to "大", 1.2f to "特大")
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
