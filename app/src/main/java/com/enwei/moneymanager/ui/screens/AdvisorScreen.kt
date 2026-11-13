package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
import com.enwei.moneymanager.ui.components.blockSurface
import com.enwei.moneymanager.ui.AppText

import com.enwei.moneymanager.data.hasAiKey
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.InputChip
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Technicals
import com.enwei.moneymanager.core.TradeStyle
import com.enwei.moneymanager.data.AdvisorSnapshot
import com.enwei.moneymanager.data.ChatMsg
import com.enwei.moneymanager.data.DailyReport
import com.enwei.moneymanager.data.InvestPlanSettings
import com.enwei.moneymanager.data.NewsStore
import com.enwei.moneymanager.data.prunedHistory
import com.enwei.moneymanager.data.newChat
import com.enwei.moneymanager.data.openChat
import com.enwei.moneymanager.data.deleteChatPair
import com.enwei.moneymanager.data.deleteCurrentChat
import com.enwei.moneymanager.data.clearChatMessages
import com.enwei.moneymanager.data.deleteThread
import com.enwei.moneymanager.data.deleteAllThreads
import com.enwei.moneymanager.data.StockPick
import com.enwei.moneymanager.data.advisorSnapshot
import com.enwei.moneymanager.data.askAdvisor
import com.enwei.moneymanager.data.buildDailyReport
import com.enwei.moneymanager.data.refreshNews
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.StockDown
import com.enwei.moneymanager.ui.StockUp
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/** 從通知等處直接開啟投資助理（獨立頁）。 */
@Composable
fun AdvisorScreen(nav: NavController) {
    // 第一次打開這頁：逐一介紹四個分頁
    val tourAi = repo.settings.value.aiEnabled
    com.enwei.moneymanager.ui.components.PageTour("advisor", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step("adv.tab0", "今日", "每日報告：大盤概況、可考慮的標的（含依據、時光機推估與進出場建議）與持股應採取的動作。報告每日自動產生，也可手動更新。"),
        com.enwei.moneymanager.ui.components.Coach.Step("adv.tab1", "問 AI", "以自然語言詢問個股、產業或自己的持股，AI 會參考新聞與營收資料回答，回覆中的個股可直接點開 K 線。"),
        com.enwei.moneymanager.ui.components.Coach.Step("adv.tab2", "新聞", "自動蒐集並標註的新聞：依題材整理重點，標示偏多或偏空，點選可閱讀內容。"),
        com.enwei.moneymanager.ui.components.Coach.Step("adv.tab3", "紀律", "資金配置（長線、可投入、預留）、持股的停損停利狀態與自訂的交易紀律；大盤階段提示目前適合加碼或減碼。"),
    // AI 關閉時沒有「今日」與「問 AI」分頁，不介紹
    ).filter { tourAi || it.target !in setOf("adv.tab0", "adv.tab1") })
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("股票助理", { nav.popBackStack() }) }) { pad ->
        Box(Modifier.padding(pad)) { AdvisorContent(nav) }
    }
}

/**
 * 股票助理（股票頁的分頁）：今日報告（推薦卡片、重點新聞、持股動作）、對話、新聞資料集、資金與紀律計畫。
 */
@Composable
fun AdvisorContent(nav: NavController) {
    var tab by com.enwei.moneymanager.ui.components.rememberTab("advisor.tab")
    LaunchedEffect(com.enwei.moneymanager.ui.TabRequest.advisorSub) {
        com.enwei.moneymanager.ui.TabRequest.advisorSub?.let { tab = it; com.enwei.moneymanager.ui.TabRequest.advisorSub = null }
    }
    // 直接訂閱資料集：背景蒐集、標註、產生報告時畫面即時更新
    val live by MoneyApp.news.flow.collectAsStateWithLifecycle()
    // 讀檔失敗不會讓畫面閃退：資料集改用空的並顯示提示
    LaunchedEffect(Unit) { runCatching { MoneyApp.news.load() } }
    val loadError by MoneyApp.news.loadError.collectAsStateWithLifecycle()
    val store = live ?: NewsStore()
    // 設定裡關掉 AI：今日報告與問 AI 需要 AI；新聞改在手機上依關鍵字整理（不花額度），紀律照常
    val aiOn = repo.settings.flow.collectAsStateWithLifecycle().value.aiEnabled
    // 分頁（名稱 → 內容編號）：沒有 AI 時只留新聞與紀律
    val tabs = if (aiOn) listOf("今日" to 0, "問 AI" to 1, "新聞" to 2, "紀律" to 3) else listOf("新聞" to 2, "紀律" to 3)
    val shownTab = if (tabs.any { it.second == tab }) tab else tabs.first().second
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val fitGroup1 = com.enwei.moneymanager.ui.components.rememberFitGroup()
        com.enwei.moneymanager.ui.components.CollapsingTop(Modifier.fillMaxSize(), header = { Column {
        com.enwei.moneymanager.ui.ChromeScale { TabRow(selectedTabIndex = tabs.indexOfFirst { it.second == shownTab }.coerceAtLeast(0), containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { (t, i) ->
                Tab(shownTab == i, { tab = i }, modifier = Modifier.then(Modifier.coachTarget("adv.tab$i")), text = { com.enwei.moneymanager.ui.components.FitText(t, fontSize = AppText.body, group = fitGroup1) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        loadError?.let { e ->
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(e, fontSize = AppText.caption, modifier = Modifier.weight(1f))
                TextButton(onClick = { MoneyApp.news.loadError.value = null }) { Text("知道了", fontSize = AppText.caption) }
            }
        }
        } }) {
        when (shownTab) {
            0 -> TodayTab(nav, store) {}
            1 -> ChatTab(nav, store) {}
            2 -> NewsTab(nav, store)
            else -> PlanTab(nav)
        }
        }
    }
}

/** 股票助理（底部分頁）：今日報告、問 AI、新聞、資金紀律；標題列往下捲時收起。 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(nav: NavController, showBack: Boolean = false) {
    val topScroll = androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior()
    LaunchedEffect(com.enwei.moneymanager.ui.components.Chrome.resetTick) { topScroll.state.heightOffset = 0f }
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    androidx.compose.material3.Scaffold(
        modifier = Modifier.nestedScroll(topScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { com.enwei.moneymanager.ui.ChromeScale {
            androidx.compose.material3.TopAppBar(
                scrollBehavior = topScroll,
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                title = { Text("股票助理") },
                navigationIcon = {
                    if (showBack) androidx.compose.material3.IconButton(onClick = { nav.popBackStack() }) {
                        androidx.compose.material3.Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = { nav.navigate("stocksearch") }) {
                        androidx.compose.material3.Icon(Icons.Outlined.Search, "搜尋股票")
                    }
                },
            )
        } },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (settings.featureStocks) AdvisorContent(nav)
            else com.enwei.moneymanager.ui.components.EmptyHint("股票功能已關閉（設定 → 投資）")
        }
    }
}

@Composable
private fun Disclaimer() = Text(
    "AI 分析僅供參考，不是投資建議。",
    fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** 今日報告：大盤、題材、推薦卡片、重點新聞、持股動作、資金建議、檢查清單；可切換看過去的報告。 */
@Composable
private fun TodayTab(nav: NavController, store: NewsStore, reload: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val job by com.enwei.moneymanager.work.ReportJob.state.collectAsStateWithLifecycle()
    val reportWork = com.enwei.moneymanager.ui.components.rememberAiWorkStatus("report-now")
    // 正在看的歷史報告（建立時間）；null 表示最新一份
    var viewing by rememberSaveable { mutableStateOf<Long?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    val r = viewing?.let { v -> store.history.find { it.createdAt == v } } ?: store.report
    val old = r != null && r !== store.report
    val recentCount by androidx.compose.runtime.produceState(0, System.identityHashCode(store)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { MoneyApp.news.recent(store, 3).size }
    }
    // 上萬則新聞的整理在背景執行緒做，畫面不卡
    val newsById by androidx.compose.runtime.produceState(emptyMap<String, com.enwei.moneymanager.data.NewsItem>(), System.identityHashCode(store)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { store.items.associateBy { it.id } }
    }
    val fmt = java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 標題列：報告時間與新聞數在左，歷史、分享、重新產生用圖示放右邊（一行）
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    r?.let {
                        val today = java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
                        (if (old) "歷史報告 · " else if (today) "今日報告 · " else "報告 · ") + fmt.format(java.util.Date(it.createdAt))
                    } ?: "還沒有報告",
                    fontSize = AppText.section, fontWeight = FontWeight.SemiBold,
                )
                Text(r?.let { "讀了 ${it.newsCount} 則新聞" } ?: "資料集 $recentCount 則新聞", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val count = store.history.size + if (store.report != null) 1 else 0
            if (count > 1) com.enwei.moneymanager.ui.components.BadgedIconButton({ showHistory = true }, count, "報告歷史") {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.History, null)
            }
            if (r != null) androidx.compose.material3.IconButton(onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, reportText(r))
                ctx.startActivity(android.content.Intent.createChooser(send, null))
            }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Share, "分享") }
            if (GroqAi.enabled && !old) androidx.compose.material3.IconButton(
                enabled = job?.running != true && reportWork?.running != true,
                onClick = { com.enwei.moneymanager.work.ReportJob.startNow(ctx) },
            ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Refresh, if (r == null) "產生報告" else "重新產生") }
        }
        if (old) TextButton(onClick = { viewing = null }) { Text("回到最新報告") }
        // 背景執行中：實際步驟與耗時（離開畫面或 App 也會繼續）
        JobProgress(job, reportWork)
        if (!GroqAi.enabled) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("設定 AI 金鑰後即可產生報告。", fontSize = AppText.secondary, modifier = Modifier.weight(1f))
            TextButton(onClick = { nav.navigate("ai") }) { Text("設定 AI") }
        }
        if (r != null) ReportBody(r, newsById, nav, store)
        Disclaimer()
        Spacer(Modifier.height(24.dp))
    }
    if (showHistory) HistoryDialog(store, { viewing = if (it == store.report?.createdAt) null else it; showHistory = false }) { showHistory = false }
}

/** 報告的純文字版本（分享用）。 */
private fun reportText(r: DailyReport): String = buildString {
    appendLine("股票助理報告 " + java.text.SimpleDateFormat("yyyy/M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(r.createdAt)))
    appendLine()
    appendLine("【大盤】${r.stage}　${r.trend}")
    if (r.themes.isNotEmpty()) appendLine(r.themes.joinToString(" ") { "#$it" })
    if (r.summary.isNotBlank()) appendLine(com.enwei.moneymanager.data.stripCites(r.summary))
    if (r.picks.isNotEmpty()) {
        appendLine(); appendLine("【今天可以考慮】")
        r.picks.forEach { p -> appendLine("・${p.symbol} ${p.name}（${p.style}${if (p.strength.isNotBlank()) "／${p.strength}" else ""}）${com.enwei.moneymanager.data.stripCites(p.reason)}") }
    }
    if (r.holdings.isNotEmpty()) {
        appendLine(); appendLine("【持股動作】")
        r.holdings.forEach { h -> appendLine("・${h.symbol} ${h.action}：${com.enwei.moneymanager.data.stripCites(h.reason)}") }
    }
    if (r.allocation.isNotBlank()) { appendLine(); appendLine("【資金分配】"); appendLine(com.enwei.moneymanager.data.stripCites(r.allocation)) }
    appendLine(); append("AI 分析僅供參考，不是投資建議。")
}

/**
 * 把 AI 寫的長段落拆成條列重點：依句號、分號、換行切開；只有引用編號的片段併回前一句。
 */
internal fun splitPoints(text: String): List<String> {
    val raw = text
        .split(Regex("(?<=[。；;！!？?])|\\n+|(?<=\\s)[-•・]\\s*"))
        .map { it.trim().trimStart('-', '•', '・', ' ') }.filter { it.isNotBlank() }
    val out = mutableListOf<String>()
    val onlyCite = Regex("^(\\s*[\\[［【]\\s*\\d{1,3}(?:\\s*[,，、]\\s*\\d{1,3})*\\s*[\\]］】])+[。；;]?$")
    raw.forEach { p ->
        if (out.isNotEmpty() && onlyCite.matches(p)) out[out.size - 1] = out.last() + p
        else out += p
    }
    return out
}

/** 條列重點：每點前面一個小圓點；超過 [preview] 點時其餘收起來，點「展開」看全部（像新聞重點）。 */
@Composable
private fun PointList(
    text: String, cites: List<String>, newsById: Map<String, com.enwei.moneymanager.data.NewsItem>,
    foldKey: String? = null, preview: Int = 3, fontSize: androidx.compose.ui.unit.TextUnit = AppText.secondary,
) {
    val points = remember(text) { splitPoints(text) }
    if (points.isEmpty()) return
    val fold = foldKey?.let { com.enwei.moneymanager.ui.components.rememberFold("report.$it", false) }
    val canFold = fold != null && points.size > preview + 1
    val shown = if (canFold && !fold!!.open) points.take(preview) else points
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        shown.forEach { p ->
            Row {
                Box(Modifier.padding(top = 7.dp, end = 8.dp).size(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                CitedText(p, cites, newsById, fontSize = fontSize, modifier = Modifier.weight(1f))
            }
        }
        if (canFold) Row(Modifier.fillMaxWidth().clickable { fold!!.toggle() }, horizontalArrangement = Arrangement.End) {
            com.enwei.moneymanager.ui.components.FoldToggle(fold!!.open, points.size - preview)
        }
    }
}

/** 報告區塊標題下的小標（例如「短線（2）」）。 */
@Composable
private fun SubHead(text: String) =
    Text(text, fontSize = AppText.caption, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun ReportBody(r: DailyReport, newsById: Map<String, com.enwei.moneymanager.data.NewsItem>, nav: NavController, store: NewsStore? = null) {
    // 分段：重點（摘要、大盤、資金、檢查）／個股／持股／新聞／追蹤（過去推薦的表現），每段一個畫面看得完
    var rtab by com.enwei.moneymanager.ui.components.rememberTab("advisor.report")
    com.enwei.moneymanager.ui.components.SubTabs(
        listOf("重點", "個股 ${r.picks.size}", "持股 ${r.holdings.size}", "新聞 ${r.keyNews.size}", "追蹤"), rtab) { rtab = it }
    if (rtab == 4 && store != null) PickTrack(store, nav)
    if (rtab == 1 && r.picks.isEmpty()) Text("這份報告沒有推薦個股。", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (rtab == 2 && r.holdings.isEmpty()) Text("這份報告沒有持股動作。", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (rtab == 3 && r.keyNews.isEmpty()) Text("這份報告沒有標出必看新聞。", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // 提醒：不再是一張勾選清單，收成最上方一行淡色提示，點一下展開全部（含原本的檢查項目）
    if (rtab == 0 && (r.warnings.isNotEmpty() || r.checklist.isNotEmpty())) {
        var open by remember { mutableStateOf(false) }
        val all = r.warnings + r.checklist
        Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("提醒", fontSize = AppText.caption, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(end = 8.dp))
                Text(all.first(), fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (open) 3 else 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (all.size > 1 && !open) Text("＋${all.size - 1}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) all.drop(1).forEach { Text("・$it", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 36.dp, top = 2.dp)) }
        }
    }
    // ① 重點摘要：階段、展望、題材，摘要拆成條列（前三點，其餘可展開）
    if (rtab == 0) Section("重點摘要") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                Text(r.stage.ifBlank { "趨勢" }, fontSize = AppText.secondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
            if (r.outlook.isNotBlank()) {
                val c = when { r.outlook.contains("多") -> StockUp; r.outlook.contains("空") -> StockDown; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
                    Text("短期 ${r.outlook}", fontSize = AppText.secondary, color = c, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }
        }
        if (r.trend.isNotBlank()) Text(com.enwei.moneymanager.data.stripCites(r.trend), fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (r.summary.isNotBlank()) PointList(r.summary, r.cites, newsById, foldKey = "summary")
        // App 算的大盤狀態與過去推薦的實際表現
        if (r.regime.isNotBlank()) Text("大盤：" + r.regime.substringAfterLast("→ ").let { l -> "$l（${r.regime.substringBeforeLast("→").trim().trimEnd('，')}）" },
            fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (r.track.isNotBlank()) Text(r.track, fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)

        if (r.scanned > 0) Text("全市場 ${r.scanned} 檔依趨勢、法人籌碼、基本面、消息、風險評分後挑選", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // ② 大盤 K 線與題材熱度
    if (rtab == 0) Section("大盤與題材") {
        com.enwei.moneymanager.ui.components.AnnotatedKLine("^TWII", r.levels.map { l ->
            Triple("AI ${l.label}", l.price, if (l.label.contains("壓")) Color(0xFFAB47BC) else Color(0xFF26A69A))
        })
        if (r.themes.isNotEmpty()) {
            SubHead("資金追逐的題材")
            ChatChartView(com.enwei.moneymanager.data.ChatChart("themes", r.themes), nav)
        }
    }
    // ③ 今天可以考慮：先比較圖，再依長短線分組；每檔只顯示重點，點開看完整內容
    if (rtab == 1 && r.picks.isNotEmpty()) Section("今天可以考慮（${r.picks.size} 檔）") {
        SubHead("走勢比較")
        com.enwei.moneymanager.ui.components.CompareChart(r.picks.take(5).map { it.symbol })
        SubHead("月營收年增率")
        com.enwei.moneymanager.ui.components.RevenueCompare(r.picks.map { it.symbol })
        r.picks.groupBy { it.style.ifBlank { "其他" } }.toSortedMap(compareBy { listOf("短線", "長線").indexOf(it).let { i -> if (i < 0) 9 else i } }).forEach { (style, list) ->
            SubHead("$style（${list.size}）")
            // 股與股之間用整條分隔線隔開
            list.forEachIndexed { i, it ->
                if (i > 0) com.enwei.moneymanager.ui.components.LineDivider()
                PickCard(it, r.cites, newsById, foldKey = "pick.${it.symbol}") { nav.navigate("stock/${it.symbol}") }
            }
        }
    }
    // ④ 持股動作：動作用色塊標示，理由條列
    if (rtab == 2 && r.holdings.isNotEmpty()) Section("持股動作（${r.holdings.size}）") {
        ChatChartView(com.enwei.moneymanager.data.ChatChart("holdings"), nav)
        r.holdings.forEachIndexed { i, h ->
            if (i > 0) com.enwei.moneymanager.ui.components.RowDivider()
            Column(Modifier.fillMaxWidth().clickable { nav.navigate("stock/${h.symbol}") }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.symbol, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    val c = when {
                        Regex("賣|減|出場|停損").containsMatchIn(h.action) -> StockDown
                        Regex("買|加|進場").containsMatchIn(h.action) -> StockUp
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp)) {
                        Text(h.action, fontSize = AppText.caption, color = c, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                }
                h.stop?.let { Text("停損 ${"%.2f".format(it)}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                PointList(h.reason, r.cites, newsById, foldKey = "hold.${h.symbol}", preview = 2, fontSize = AppText.caption)
            }
        }
    }
    // ⑤ 一定要看的新聞
    if (rtab == 3 && r.keyNews.isNotEmpty()) Section("一定要看的新聞（${r.keyNews.size}）") {
        r.keyNews.forEachIndexed { i, k ->
            newsById[k.id]?.let { n ->
                if (i > 0) com.enwei.moneymanager.ui.components.RowDivider()
                NewsLine(n, num = r.cites.indexOf(k.id).takeIf { it >= 0 }?.plus(1))
                if (k.why.isNotBlank()) Text("為什麼重要：${k.why}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    // ⑥ 資金分配：比例條＋圖例，說明條列
    if (rtab == 0 && (r.allocation.isNotBlank() || r.alloc.isNotEmpty())) Section("資金分配") {
        if (r.alloc.isNotEmpty()) {
            val total = r.alloc.sumOf { it.pct }.takeIf { it > 0 } ?: 1.0
            val palette = com.enwei.moneymanager.ui.components.SeriesColors
            Row(Modifier.fillMaxWidth().height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))) {
                r.alloc.forEachIndexed { i, a -> Box(Modifier.weight((a.pct / total).toFloat().coerceAtLeast(0.01f)).height(14.dp).background(palette[i % palette.size])) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.alloc.forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(palette[i % palette.size], RoundedCornerShape(4.dp)))
                        Text(" ${a.name} ${"%.0f".format(a.pct / total * 100)}%", fontSize = AppText.caption)
                    }
                }
            }
        }
        if (r.allocation.isNotBlank()) PointList(r.allocation, r.cites, newsById, foldKey = "alloc")
    }
}

/** 報告歷史：點一份查看、逐份刪除、刪除全部舊報告，以及自動刪除的保留天數。 */
@Composable
private fun HistoryDialog(store: NewsStore, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    val fmt = java.text.SimpleDateFormat("yyyy/M/d HH:mm", java.util.Locale.TAIWAN)
    // 按下刪除立即從清單消失；實際寫檔放在 App 層級執行，關掉對話框或資料集正在更新時也不會被取消
    var gone by remember { mutableStateOf(emptySet<Long>()) }
    val all = (listOfNotNull(store.report) + store.history).filter { it.createdAt !in gone }
    val history = store.history.filter { it.createdAt !in gone }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var confirmAll by remember { mutableStateOf(false) }
    fun delete(c: Long) {
        gone = gone + c
        MoneyApp.instance.appScope.launch {
            MoneyApp.news.update { st ->
                if (st.report?.createdAt == c) st.copy(report = st.history.firstOrNull(), history = st.history.drop(1))
                else st.copy(history = st.history.filter { it.createdAt != c })
            }
        }
    }
    fun deleteOld() {
        val ids = history.map { it.createdAt }
        gone = gone + ids
        MoneyApp.instance.appScope.launch {
            MoneyApp.news.update { st -> st.copy(history = st.history.filter { it.createdAt !in ids }) }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(ctx, com.enwei.moneymanager.ui.tr("已刪除 ${ids.size} 份舊報告"), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("報告歷史") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("自動刪除", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "7 天後", 30 to "30 天後", 90 to "90 天後", 0 to "不刪除").forEach { (d, l) ->
                        FilterChip(store.historyKeepDays == d, {
                            MoneyApp.instance.appScope.launch { MoneyApp.news.update { it.copy(historyKeepDays = d).prunedHistory() } }
                        }, label = { Text(l, fontSize = AppText.caption) })
                    }
                }
                com.enwei.moneymanager.ui.components.LineDivider()
                if (all.isEmpty()) Text("還沒有報告", fontSize = AppText.secondary)
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    all.forEachIndexed { i, rep ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(rep.createdAt) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fmt.format(java.util.Date(rep.createdAt)) + (if (i == 0 && rep === store.report) "（最新）" else ""), fontSize = AppText.section, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(rep.stage, rep.picks.joinToString("、") { it.name.ifBlank { it.symbol } }).filter { it.isNotBlank() }.joinToString(" · ")
                                        .ifBlank { rep.summary.take(30) },
                                    fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                )
                            }
                            androidx.compose.material3.IconButton(onClick = { delete(rep.createdAt) }) {
                                androidx.compose.material3.Icon(Icons.Filled.Delete, "刪除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
        dismissButton = {
            if (history.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text("刪除舊報告", color = MaterialTheme.colorScheme.error) }
        },
    )
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("刪除全部舊報告？") },
        text = { Text("會刪除 ${history.size} 份過去的報告，保留最新一份。") },
        confirmButton = { TextButton(onClick = { confirmAll = false; deleteOld() }) { Text("刪除") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("取消") } },
    )
}

/** 推薦股票卡片：代號名稱、長短線、強弱、業務、理由、進出場；點進去看 K 線與資訊。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun PickCard(
    p0: StockPick, cites: List<String> = emptyList(), newsById: Map<String, com.enwei.moneymanager.data.NewsItem> = emptyMap(),
    /** 報告裡用：收起時只顯示標題列、依據與第一點理由；展開後依段落（依據、時光機、走勢、理由、操作）以分隔線分開 */
    foldKey: String? = null,
    onClick: () -> Unit,
) {
    // AI 偶爾把「[n]」原樣寫進理由：拿掉
    val p = p0.copy(reason = p0.reason.replace("[n]", "").replace("［n］", "").trim())
    val fold = foldKey?.let { com.enwei.moneymanager.ui.components.rememberFold("report.$it", false) }
    val open = fold?.open ?: true
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val strengthColor = when { p.strength.contains("強") -> StockUp; p.strength.contains("弱") -> StockDown; else -> muted }
    Box(Modifier.fillMaxWidth().blockSurface()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            // 標題列：收起、展開都一樣，右邊的箭頭固定在同一個位置
            Row(Modifier.fillMaxWidth().clickable(enabled = fold != null) { fold?.toggle() }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${p.symbol} ${p.name}", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(p.price?.let { "%.2f".format(it) } ?: "代號未確認", p.industry.ifBlank { null }, p.style.ifBlank { null },
                            p.strength.ifBlank { null }).joinToString(" · "),
                        fontSize = AppText.caption, color = muted, maxLines = 1,
                    )
                }
                p.score?.let { ScoreBadge(it) }
                if (fold != null) Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (open) "收合" else "展開",
                    tint = muted, modifier = Modifier.padding(start = 6.dp).size(22.dp),
                )
            }
            if (!open) {
                if (p.basis.isNotEmpty()) Text(p.basis.joinToString(" · "), fontSize = AppText.label, color = strengthColor.takeIf { it != muted } ?: MaterialTheme.colorScheme.primary,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                splitPoints(p.reason).firstOrNull()?.let {
                    CitedText(it, cites, newsById, fontSize = AppText.caption, color = muted)
                }
                return@Column
            }
            // 段落：上方一條分隔線，標題在線下；有說明時「？」放在標題同一行的最右邊
            @Composable fun section(title: String, help: Pair<String, String>? = null) {
                com.enwei.moneymanager.ui.components.RowDivider(Modifier.padding(top = 10.dp, bottom = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(title, fontSize = AppText.label, color = muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (help != null) com.enwei.moneymanager.ui.components.HelpIcon(help.first, help.second, Modifier.size(24.dp))
                }
            }
            // ① 依據：App 算的標籤與五大面向分數
            if (p.basis.isNotEmpty() || p.scores.size == 5) {
                section("依據", "分數怎麼算" to com.enwei.moneymanager.ui.components.HelpText.SCORES)
                if (p.basis.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    p.basis.forEach { b ->
                        val neg = b.contains("弱於") || b.contains("跌破") || b.contains("連賣") || Regex("[-−]\\d").containsMatchIn(b)
                        Surface(color = (if (neg) StockDown else StockUp).copy(alpha = 0.10f), shape = RoundedCornerShape(4.dp)) {
                            Text(b, fontSize = AppText.label, color = if (neg) StockDown else StockUp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                if (p.scores.size == 5) Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    listOf("趨勢", "籌碼", "基本", "消息", "穩定").zip(p.scores).forEach { (l, v) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$v", fontSize = AppText.body, fontWeight = FontWeight.SemiBold, color = if (v >= 65) StockUp else if (v <= 35) StockDown else MaterialTheme.colorScheme.onSurface)
                            Text(l, fontSize = AppText.label, color = muted)
                        }
                    }
                }
            }
            // ② 時光機
            if (p.simTarget != null && p.simStop != null) {
                section("時光機 · 未來 20 個交易日", "時光機是什麼" to com.enwei.moneymanager.ui.components.HelpText.SIM)
                SimBar(p.simTarget, p.simStop, p.simNone ?: 0, p.simExpR, p.targetPrice, p.stopPrice)
            }
            // ③ 走勢：近期 K 線、營收、新聞熱度
            section("走勢")
            com.enwei.moneymanager.ui.components.MiniKLine(p.symbol)
            com.enwei.moneymanager.ui.components.RevenueLine(p.symbol)
            val store by MoneyApp.news.flow.collectAsStateWithLifecycle()
            val ev = remember(System.identityHashCode(store), p.symbol) {
                store?.let { st -> MoneyApp.news.recent(st, 3).filter { p.symbol in it.symbols } }.orEmpty()
            }
            if (ev.isNotEmpty()) {
                val avg = ev.map { it.sentiment }.average()
                Text("新聞：近 3 天 ${ev.size} 則・" + (if (avg > 0.2) "偏多" else if (avg < -0.2) "偏空" else "中性"),
                    fontSize = AppText.label, color = if (avg > 0.2) StockUp else if (avg < -0.2) StockDown else muted)
            }
            // ④ 理由
            if (p.business.isNotBlank() || p.reason.isNotBlank()) {
                section("理由")
                if (p.business.isNotBlank()) Text(p.business, fontSize = AppText.caption, color = muted)
                if (p.reason.isNotBlank()) PointList(p.reason, cites, newsById)
            }
            // ⑤ 操作：進場、停損、金額，與按鈕
            // 沒有進場、停損、金額時不放「操作」小標，只留按鈕
            if (p.entry.isNotBlank() || p.stop.isNotBlank() || p.amount != null) section("操作")
            if (p.entry.isNotBlank() || p.stop.isNotBlank()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("進場", fontSize = AppText.label, color = muted)
                    Text(p.entry.ifBlank { "—" }, fontSize = AppText.caption, color = StockUp)
                }
                Column(Modifier.weight(1f)) {
                    Text("停損", fontSize = AppText.label, color = muted)
                    Text(p.stop.ifBlank { "—" }, fontSize = AppText.caption, color = StockDown)
                }
            }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.amount?.let { "建議投入約 ${Money.withSymbol(it * 100, "TWD")}" }.orEmpty(), fontSize = AppText.caption,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                val settings by repo.settings.flow.collectAsStateWithLifecycle()
                val inList = p.symbol in settings.watchlist
                TextButton(onClick = {
                    repo.settings.update { it.copy(watchlist = if (inList) it.watchlist - p.symbol else (it.watchlist + p.symbol).distinct()) }
                }) { Text(if (inList) "★ 觀察中" else "☆ 觀察", fontSize = AppText.caption) }
                TextButton(onClick = onClick) { Text("K 線 ›", fontSize = AppText.caption) }
            }
        }
    }
}

/** 綜合分數小徽章（0–100，越高越綠／紅依漲跌色）。 */
@Composable
private fun ScoreBadge(score: Int) {
    val c = if (score >= 65) StockUp else if (score <= 40) StockDown else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(start = 4.dp)) {
        Text("$score 分", fontSize = AppText.caption, color = c, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** 時光機：未來 20 個交易日先到目標／先到停損／都沒到的比例條，與目標價、停損價。 */
@Composable
private fun SimBar(target: Int, stop: Int, none: Int, expR: Double?, targetPrice: Double?, stopPrice: Double?) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            if (target > 0) Box(Modifier.weight(target.toFloat()).fillMaxHeight().background(StockUp))
            if (none > 0) Box(Modifier.weight(none.toFloat()).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
            if (stop > 0) Box(Modifier.weight(stop.toFloat()).fillMaxHeight().background(StockDown))
        }
        Row {
            Column(Modifier.weight(1f)) {
                Text("先到目標 $target%", fontSize = AppText.label, color = StockUp)
                targetPrice?.let { Text("%.1f".format(it), fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("都沒到 $none%", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                expR?.let { Text("期望 ${"%+.2f".format(it)}R", fontSize = AppText.label, color = if (it > 0) StockUp else StockDown) }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("先到停損 $stop%", fontSize = AppText.label, color = StockDown)
                stopPrice?.let { Text("%.1f".format(it), fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(start = 4.dp)) {
        Text(text, fontSize = AppText.caption, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** 問 AI：對話泡泡，AI 回覆附股票卡片與新聞。 */
@Composable
private fun ChatTab(nav: NavController, store: NewsStore, reload: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    // 其他頁面帶過來的問題（例如推薦追蹤的檢討）：放進輸入框，由使用者按送出
    LaunchedEffect(com.enwei.moneymanager.ui.TabRequest.chatQuestion) {
        com.enwei.moneymanager.ui.TabRequest.chatQuestion?.let { input = it; com.enwei.moneymanager.ui.TabRequest.chatQuestion = null }
    }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDeleteChat by remember { mutableStateOf(false) }
    var showThreads by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    // 對話在背景執行：離開畫面也會繼續，回來時看得到回覆；不在畫面上時完成會通知
    val job by com.enwei.moneymanager.work.ChatJob.state.collectAsStateWithLifecycle()
    val chatWork = com.enwei.moneymanager.ui.components.rememberAiWorkStatus("advisor-chat")
    val busy = job?.running == true || chatWork?.running == true
    val err = job?.error ?: chatWork?.error
    // 回到對話時停在上次看的位置，不會每次都被捲到最底
    // 位置用「畫面最上方那則訊息」記住（重開 App 也記得）：圖表、K 線晚一點載入改變高度也不會跑掉
    val chatPrefs = remember { MoneyApp.instance.getSharedPreferences("chat_scroll", android.content.Context.MODE_PRIVATE) }
    val list = com.enwei.moneymanager.work.ChatJob.listState
    val latestChat by androidx.compose.runtime.rememberUpdatedState(store.chat)
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.enwei.moneymanager.work.ChatJob.visible = true
        onDispose {
            com.enwei.moneymanager.work.ChatJob.visible = false
            val first = list.layoutInfo.visibleItemsInfo.firstOrNull { (it.key as? String)?.startsWith("c") == true }
            chatPrefs.edit().putString("key", first?.key as? String).putInt("offset", first?.let { list.firstVisibleItemScrollOffset.takeIf { _ -> it.index == list.firstVisibleItemIndex } ?: 0 } ?: 0)
                .putLong("seen", latestChat.lastOrNull()?.time ?: 0).apply()
        }
    }
    // 上萬則新聞的整理在背景執行緒做，畫面不卡
    val newsById by androidx.compose.runtime.produceState(com.enwei.moneymanager.work.ChatJob.newsCache, System.identityHashCode(store)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { store.items.associateBy { it.id } }
        com.enwei.moneymanager.work.ChatJob.newsCache = value
    }
    fun send(q: String) {
        if (q.isBlank() || busy) return
        input = ""
        com.enwei.moneymanager.work.ChatJob.ask(ctx, q)
    }
    // 只有出現新訊息才捲動，而且捲到那則訊息的開頭（長回覆從頭讀）；這次開 App 第一次進來直接跳到最後一則
    // （對話最多留 40 則，則數可能不變，所以用最後一則的時間判斷有沒有新訊息）
    val last = store.chat.lastOrNull()?.time
    LaunchedEffect(last) {
        if (last == null) return@LaunchedEffect
        val n = store.chat.size
        val job = com.enwei.moneymanager.work.ChatJob
        // 同一次開 App：捲動狀態一直保留，只有出現新訊息才捲
        if (job.restoredOnce) {
            if (last != job.lastShown) { job.lastShown = last; list.animateScrollToItem(n) }
            return@LaunchedEffect
        }
        run {
            job.restoredOnce = true
            job.lastShown = last
            // 第一次進來：沒有新訊息就回到上次看的那則；有新回覆就捲到新回覆的開頭
            val seen = chatPrefs.getLong("seen", -1)
            val idx = chatPrefs.getString("key", null)?.let { k -> store.chat.indexOfFirst { "c${it.time}${it.fromUser}" == k } } ?: -1
            if (last == seen && idx >= 0) list.scrollToItem(idx + 1, chatPrefs.getInt("offset", 0))
            else list.scrollToItem(n)
        }
    }
    val canDown by remember { androidx.compose.runtime.derivedStateOf { list.canScrollForward } }
    Column(Modifier.fillMaxSize().imePadding()) {
        // 上方工具列：目前對話的標題、過去的對話、新對話、更多（分享、清除）
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (store.chat.isEmpty()) "新對話" else com.enwei.moneymanager.data.chatTitle(store.chat), fontSize = AppText.body,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            com.enwei.moneymanager.ui.components.BadgedIconButton({ showThreads = true }, store.chatThreads.size, "過去的對話") {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.History, null)
            }
            androidx.compose.material3.IconButton(enabled = !busy && store.chat.isNotEmpty(), onClick = {
                com.enwei.moneymanager.work.ChatJob.seenLast = 0
                com.enwei.moneymanager.work.ChatJob.state.value = null
                scope.launch { MoneyApp.news.update { it.newChat() }; list.scrollToItem(0) }
            }) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.AddComment, "新對話") }
            Box {
                androidx.compose.material3.IconButton(enabled = store.chat.isNotEmpty() || store.chatThreads.isNotEmpty(), onClick = { moreMenu = true }) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, "更多")
                }
                androidx.compose.material3.DropdownMenu(moreMenu, { moreMenu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("分享這段對話") }, onClick = {
                        moreMenu = false
                        val txt = store.chat.joinToString("\n\n") { m ->
                            (if (m.fromUser) "我：" else "助理：") + com.enwei.moneymanager.data.stripCites(m.text) +
                                m.points.joinToString("") { "\n・" + com.enwei.moneymanager.data.stripCites(it) }
                        } + "\n\nAI 分析僅供參考，不是投資建議。"
                        ctx.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, txt), null))
                    })
                    androidx.compose.material3.DropdownMenuItem(enabled = !busy, text = { Text("清除所有訊息") },
                        onClick = { moreMenu = false; confirmClear = true })
                    androidx.compose.material3.DropdownMenuItem(enabled = !busy, text = { Text("刪除這段對話", color = MaterialTheme.colorScheme.error) },
                        onClick = { moreMenu = false; confirmDeleteChat = true })
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 72.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 空白對話：歡迎卡片＋四個常見問題（點一下就問）
                if (store.chat.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = androidx.compose.foundation.shape.CircleShape) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(14.dp).size(28.dp))
                        }
                        Text("想問什麼？", fontSize = AppText.title, fontWeight = FontWeight.SemiBold)
                        listOf(
                            "今天該買什麼？" to "依大盤階段與新聞挑選",
                            "我的持股怎麼處理？" to "對照你的紀律逐檔說明",
                            "現在適合加碼嗎？" to "看資金配置與大盤位置",
                            "最近有什麼重大新聞？" to "整理近三天的重點",
                        ).chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { (q, hint) ->
                                    com.enwei.moneymanager.ui.components.AppCard(Modifier.weight(1f).height(84.dp).clickable(enabled = !busy && GroqAi.enabled) { send(q) }) {
                                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(q, fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                            Text(hint, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else item { Spacer(Modifier.height(4.dp)) }
                val lastAi = store.chat.lastOrNull { !it.fromUser }
                items(store.chat, key = { "c${it.time}${it.fromUser}" }) { m ->
                    Bubble(
                        m, newsById, nav,
                        onDelete = {
                            val idx = store.chat.indexOf(m)
                            // 回覆進行中時，正在回答的最後一個問題不能刪（否則回覆會被加回成沒有問題的回答）
                            val lastQ = store.chat.indexOfLast { it.fromUser }
                            if (idx >= 0 && !(busy && idx >= lastQ)) {
                                val before = store
                                scope.launch { MoneyApp.news.update { it.deleteChatPair(idx) } }
                                com.enwei.moneymanager.ui.components.Undo.show("已刪除這組問答") { MoneyApp.news.update { it.copy(chat = before.chat) } }
                            }
                        },
                        onEdit = if (m.fromUser) ({ input = m.text }) else null,
                        // 最後一則回覆可以重新產生：拿掉這則回覆，用前一個問題再問一次
                        onRegenerate = if (m === lastAi && !busy && GroqAi.enabled) ({
                            val q = store.chat.subList(0, store.chat.indexOf(m)).lastOrNull { it.fromUser }?.text
                            if (q != null) scope.launch {
                                MoneyApp.news.update { st -> st.copy(chat = st.chat.filterNot { it.time == m.time && !it.fromUser }) }
                                com.enwei.moneymanager.work.ChatJob.ask(ctx, q)
                            }
                        }) else null,
                    )
                }
                if (busy) item {
                    // 對話生成流程：持股與大盤 → 搜尋新聞 → 讀取全文 → AI 回答 → 確認代號
                    job?.takeIf { it.running }?.let { j ->
                        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            ChatSteps(j.step)
                        }
                    }
                    com.enwei.moneymanager.ui.components.AiWorkProgress(
                        chatWork ?: job?.takeIf { it.running }?.let {
                            com.enwei.moneymanager.work.AiWorkStatus(true, it.step.ifBlank { "AI 正在整理你的投資問題…" }, it.startedAt, null, null)
                        },
                    )
                }
                err?.let { e ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                            // 失敗或停止後可以用同一個問題再問一次
                            job?.question?.takeIf { it.isNotBlank() && GroqAi.enabled }?.let { q ->
                                TextButton(onClick = { com.enwei.moneymanager.work.ChatJob.ask(ctx, q) }) { Text("重試") }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(8.dp)) }
            }
            // 往上捲時出現「到最新」按鈕
            if (canDown) androidx.compose.material3.SmallFloatingActionButton(
                onClick = { scope.launch { list.animateScrollToItem((list.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)) } },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            ) { androidx.compose.material3.Icon(Icons.Filled.KeyboardArrowDown, "到最新") }
        }
        // 常見問題：對話開始後、輸入框是空的時候才顯示
        if (store.chat.isNotEmpty() && input.isBlank() && !busy) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("今天該買什麼？", "我的持股怎麼處理？", "現在適合加碼嗎？", "最近有什麼重大新聞？", "短線資金還能用多少？").forEach { q ->
                FilterChip(false, { send(q) }, label = { Text(q, fontSize = AppText.caption) }, enabled = !busy && GroqAi.enabled)
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("問股票助理…") }, modifier = Modifier.weight(1f), maxLines = 4,
                shape = RoundedCornerShape(24.dp))
            // AI 回覆中按鈕變成「停止」
            if (busy) androidx.compose.material3.FilledTonalIconButton(
                onClick = { com.enwei.moneymanager.work.ChatJob.stop(ctx, job?.question) },
                modifier = Modifier.padding(start = 6.dp).size(48.dp),
            ) { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Stop, "停止") }
            else androidx.compose.material3.FilledIconButton(enabled = input.isNotBlank() && GroqAi.enabled, onClick = { send(input) },
                modifier = Modifier.padding(start = 6.dp).size(48.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.Send, "送出")
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清除所有訊息？") },
        text = { Text("清除這段對話的 ${store.chat.size} 則訊息，之後提問不會再參考這些內容。過去的對話不受影響，清除後可復原。") },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                com.enwei.moneymanager.work.ChatJob.seenLast = 0
                com.enwei.moneymanager.work.ChatJob.state.value = null
                val before = store
                scope.launch { MoneyApp.news.update { it.clearChatMessages() }; list.scrollToItem(0) }
                com.enwei.moneymanager.ui.components.Undo.show("已清除所有訊息") { MoneyApp.news.update { it.copy(chat = before.chat) } }
            }) { Text("清除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
    if (confirmDeleteChat) AlertDialog(
        onDismissRequest = { confirmDeleteChat = false },
        title = { Text("刪除這段對話？") },
        text = { Text("刪除後會打開最近一段過去的對話；這段裡的「檢討推薦」結論，之後的每日選股也不再參考。刪除後可復原。") },
        confirmButton = {
            TextButton(onClick = {
                confirmDeleteChat = false
                com.enwei.moneymanager.work.ChatJob.seenLast = -1
                com.enwei.moneymanager.work.ChatJob.state.value = null
                val before = store
                scope.launch { MoneyApp.news.update { it.deleteCurrentChat() }; list.scrollToItem(0) }
                com.enwei.moneymanager.ui.components.Undo.show("已刪除這段對話") { MoneyApp.news.update { it.copy(chat = before.chat, chatThreads = before.chatThreads) } }
            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDeleteChat = false }) { Text("取消") } },
    )
    if (showThreads) ChatThreadsDialog(store, busy, onOpen = { id ->
        showThreads = false
        com.enwei.moneymanager.work.ChatJob.seenLast = -1
        com.enwei.moneymanager.work.ChatJob.state.value = null
        scope.launch { MoneyApp.news.update { it.openChat(id) } }
    }) { showThreads = false }
}

/** 過去的對話：點一段接著聊，可逐段刪除。 */
@Composable
private fun ChatThreadsDialog(store: NewsStore, busy: Boolean, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var confirmAll by remember { mutableStateOf(false) }
    val fmt = java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("過去的對話") },
        text = {
            if (store.chatThreads.isEmpty()) Text("還沒有過去的對話", fontSize = AppText.secondary)
            else Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                store.chatThreads.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onOpen(t.id) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("${fmt.format(java.util.Date(t.updated))} · ${t.msgs.size} 則", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.IconButton(onClick = {
                            val before = store.chatThreads
                            scope.launch { MoneyApp.news.update { it.deleteThread(t.id) } }
                            com.enwei.moneymanager.ui.components.Undo.show("已刪除「${t.title}」") { MoneyApp.news.update { it.copy(chatThreads = before) } }
                        }) { androidx.compose.material3.Icon(Icons.Filled.Delete, "刪除「${t.title}」", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
        dismissButton = if (store.chatThreads.isNotEmpty()) ({
            TextButton(enabled = !busy, onClick = { confirmAll = true }) { Text("全部刪除", color = MaterialTheme.colorScheme.error) }
        }) else null,
    )
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("刪除全部過去的對話？") },
        text = { Text("刪除 ${store.chatThreads.size} 段過去的對話（目前這段保留），刪除後可復原。") },
        confirmButton = {
            TextButton(onClick = {
                confirmAll = false
                val before = store.chatThreads
                scope.launch { MoneyApp.news.update { it.deleteAllThreads() } }
                com.enwei.moneymanager.ui.components.Undo.show("已刪除全部過去的對話") { MoneyApp.news.update { it.copy(chatThreads = before) } }
                onDismiss()
            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("取消") } },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Bubble(
    m: ChatMsg, newsById: Map<String, com.enwei.moneymanager.data.NewsItem>, nav: NavController,
    onDelete: () -> Unit, onEdit: (() -> Unit)? = null, onRegenerate: (() -> Unit)? = null,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val text = com.enwei.moneymanager.data.stripCites(m.text)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Box {
            Surface(
                color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius),
                modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) {
                // AI 回覆：結論＋重點條列（格式依「設定 → AI 語氣」）
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CitedText(m.text, m.newsIds, newsById)
                    m.points.forEach { p ->
                        Row {
                            Text("•", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                            CitedText(p, m.newsIds, newsById)
                        }
                    }
                    if (m.basis.isNotBlank()) Text(m.basis, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 長按：複製、刪除這則
            androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("複製") }, onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(text)); menu = false })
                if (onEdit != null) androidx.compose.material3.DropdownMenuItem(text = { Text("編輯後重問") }, onClick = { menu = false; onEdit() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("刪除", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
        // 回覆附的圖表：用實際資料畫（走勢比較、題材熱度、持股損益）
        m.charts.forEach { ch ->
            Box(Modifier.padding(top = 6.dp).fillMaxWidth().blockSurface()) {
                Box(Modifier.padding(10.dp)) { ChatChartView(ch, nav) }
            }
        }
        m.cards.forEach { c -> Box(Modifier.padding(top = 6.dp)) { PickCard(c, m.newsIds, newsById) { nav.navigate("stock/${c.symbol}") } } }
        // 引用的新聞：編號和回覆裡的 [1][2] 對應
        val ns = m.newsIds.mapIndexedNotNull { i, id -> newsById[id]?.let { i + 1 to it } }
        if (ns.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) { ns.forEach { (k, n) -> NewsLine(n, num = k) } }
        // AI 回覆下方的小動作：複製、分享、重新產生（最後一則）
        if (!m.fromUser) Row(verticalAlignment = Alignment.CenterVertically) {
            val full = text + m.points.joinToString("") { "\n・" + com.enwei.moneymanager.data.stripCites(it) }
            androidx.compose.material3.IconButton(onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(full)) }, modifier = Modifier.size(44.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.ContentCopy, "複製", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.IconButton(onClick = {
                ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(android.content.Intent.EXTRA_TEXT, full + "\n\nAI 分析僅供參考，不是投資建議。"), null))
            }, modifier = Modifier.size(36.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Share, "分享", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onRegenerate != null) androidx.compose.material3.IconButton(onClick = onRegenerate, modifier = Modifier.size(44.dp)) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Refresh, "重新產生", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(java.text.SimpleDateFormat("HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(m.time)), fontSize = AppText.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

/** 背景工作（產生報告或更新資料集）的實際步驟、批次進度與耗時。 */
@Composable
private fun JobProgress(
    job: com.enwei.moneymanager.work.ReportJob.State?,
    work: com.enwei.moneymanager.work.AiWorkStatus?,
) {
    val running = work?.running ?: (job?.running == true)
    if (running) {
        val step = work?.step ?: job?.step ?: "準備中…"
        val done = work?.done ?: job?.done
        val total = work?.total ?: job?.total
        val startedAt = work?.startedAt?.takeIf { it > 0 } ?: job?.startedAt ?: 0
        var now by remember(startedAt) { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(running, startedAt) {
            while (running && startedAt > 0) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
        // 一張小卡：階段進度條＋一行目前步驟（批次、已執行時間）
        Box(Modifier.fillMaxWidth().blockSurface()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PipelineSteps(step, refreshOnly = job?.refreshOnly == true)
                Text(
                    listOfNotNull(
                        step.removeSuffix("…"),
                        if (done != null && total != null && total > 0 && step.contains("$done/").not()) "$done/$total" else null,
                        if (startedAt > 0) "已執行 ${com.enwei.moneymanager.work.ReportJob.formatDuration(now - startedAt)}" else null,
                    ).joinToString("・"),
                    fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        val error = job?.error
        error?.let { Text(it, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.error) }
    }
}

/** 新聞分頁的統計（背景執行緒算好）。 */
private data class NewsStats(
    val tagged: Int = 0,
    val themes: List<com.enwei.moneymanager.data.TopicStat> = emptyList(),
    val important: List<com.enwei.moneymanager.data.NewsItem> = emptyList(),
    val symbols: List<com.enwei.moneymanager.data.TopicStat> = emptyList(),
    val sources: List<Pair<String, Int>> = emptyList(),
    val coverage: com.enwei.moneymanager.data.NewsDataset.Coverage? = null,
)

/** 新聞資料集：蒐集狀態、整理進度、題材熱度、重點，以及可多條件篩選的全部新聞（清單會即時更新）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewsTab(nav: NavController, store: NewsStore) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val job by com.enwei.moneymanager.work.ReportJob.state.collectAsStateWithLifecycle()
    val reportWork = com.enwei.moneymanager.ui.components.rememberAiWorkStatus("report-now")
    val newsWork = com.enwei.moneymanager.ui.components.rememberAiWorkStatus("news")
    val chatWork = com.enwei.moneymanager.ui.components.rememberAiWorkStatus("advisor-chat")
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val aiReady = settings.aiEnabled && settings.hasAiKey
    val holdings by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    val mine = remember(holdings) { holdings.values.flatten().map { it.symbol }.toSet() }
    val ds = MoneyApp.news
    // 統計與篩選都在背景執行緒算（資料集有上萬則）
    val stats by androidx.compose.runtime.produceState(NewsStats(), System.identityHashCode(store)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            NewsStats(
                store.items.count { it.tagged }, ds.themes(store), ds.important(store, 3, 30), ds.symbols(store, 3),
                store.items.filter { it.source.isNotBlank() }.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.map { it.key to it.value },
                ds.coverage(store),
            )
        }
    }
    val themes = stats.themes
    var confirmClearDatabase by rememberSaveable { mutableStateOf(false) }
    var clearDatabaseError by remember { mutableStateOf<String?>(null) }
    var clearingDatabase by remember { mutableStateOf(false) }
    // 篩選條件
    var query by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(0) }       // 0 全部、1 今天、3、7 天
    var sent by remember { mutableStateOf(0) }       // 0 全部、1 利多、2 利空、3 中性
    var onlyImportant by remember { mutableStateOf(false) }
    var onlyMine by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(0) }       // 0 最新、1 最重要、2 多空最強
    // 題材、個股、來源都可以複選
    var theme by remember { mutableStateOf(emptySet<String>()) }
    var symbol by remember { mutableStateOf(emptySet<String>()) }
    var source by remember { mutableStateOf(emptySet<String>()) }
    var shown by remember { mutableStateOf(100) }
    val anyFilter = query.isNotBlank() || days != 0 || sent != 0 || onlyImportant || onlyMine || theme.isNotEmpty() || symbol.isNotEmpty() || source.isNotEmpty()
    val filteredOrNull by androidx.compose.runtime.produceState<List<com.enwei.moneymanager.data.NewsItem>?>(null,
        System.identityHashCode(store), query, days, sent, onlyImportant, onlyMine, sort, theme, symbol, source, mine) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val zone = java.time.ZoneId.of("Asia/Taipei")
            val since = when (days) {
                0 -> 0L
                1 -> java.time.LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
                else -> System.currentTimeMillis() / 1000 - days * 86_400L
            }
            // 關鍵字：空白分開的每個詞都要出現（標題、重點、內文、題材、來源）；公司名稱會換成代號一起比對
            val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val nameCodes = terms.associateWith { t -> com.enwei.moneymanager.net.TwData.cached().filter { it.name.length >= 2 && it.name.contains(t) }.map { it.code }.toSet() }
            val list = store.items.filter { n ->
                n.epochSec >= since &&
                    terms.all { t -> n.title.contains(t, true) || n.note.contains(t, true) || n.content.contains(t, true) || n.theme.contains(t, true) ||
                        t in n.symbols || n.symbols.any { it in nameCodes[t].orEmpty() } || n.source.contains(t, true) } &&
                    (theme.isEmpty() || n.theme in theme) && (symbol.isEmpty() || n.symbols.any { it in symbol }) && (source.isEmpty() || n.source in source) &&
                    (!onlyImportant || n.importance >= 4) && (!onlyMine || n.symbols.any { it in mine }) &&
                    when (sent) { 1 -> n.sentiment > 0; 2 -> n.sentiment < 0; 3 -> n.tagged && n.sentiment == 0; else -> true }
            }
            when (sort) {
                1 -> list.sortedWith(compareByDescending<com.enwei.moneymanager.data.NewsItem> { it.importance }.thenByDescending { it.epochSec })
                2 -> list.sortedWith(compareByDescending<com.enwei.moneymanager.data.NewsItem> { kotlin.math.abs(it.sentiment) }.thenByDescending { it.importance }.thenByDescending { it.epochSec })
                else -> list.sortedByDescending { it.epochSec }
            }
        }
    }
    // 第一次篩選還在背景計算時顯示「整理中」，不要誤顯示 0 則
    val filtered = filteredOrNull.orEmpty()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var pickList by remember { mutableStateOf<String?>(null) }   // 開啟中的清單：題材／個股／來源
    // 兩個檢視：「重點」（重點新聞＋題材，點題材展開 AI 整理的重點）與「全部新聞」（依日期）。有篩選條件時自動切到全部新聞
    var view by rememberSaveable { mutableStateOf(0) }
    var searchOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var infoOpen by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf<String?>(null) }
    var allThemes by remember { mutableStateOf(false) }
    LaunchedEffect(anyFilter) { if (anyFilter) view = 1 }
    val digestsByTheme = remember(store.digests) { store.digests.values.groupBy { it.theme }.mapValues { (_, l) -> l.sortedByDescending { it.day } } }
    Column(Modifier.fillMaxSize()) {
        val filterCount = listOf(days != 0, sent != 0, onlyImportant, onlyMine, theme.isNotEmpty(), symbol.isNotEmpty(), source.isNotEmpty(), sort != 0).count { it }
        com.enwei.moneymanager.ui.components.CollapsingTop(Modifier.fillMaxSize(), header = { Column {
        // 精簡工具列（漸進式揭露）：重點／全部切換＋搜尋、篩選、更多三個圖示；搜尋框與篩選條件點了才展開
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius)).padding(3.dp)) {
                listOf("重點", "全部" + (if (filteredOrNull != null) " ${filtered.size}" else "")).forEachIndexed { i, l ->
                    val sel = view == i
                    Box(
                        Modifier.weight(1f).background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { view = i }.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(l, fontSize = AppText.secondary, maxLines = 1, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
            androidx.compose.material3.IconButton(onClick = { searchOpen = !searchOpen }) {
                Icon(Icons.Filled.Search, "搜尋", tint = if (query.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.enwei.moneymanager.ui.components.BadgedIconButton({ filterOpen = true }, filterCount, "篩選") {
                Icon(androidx.compose.material.icons.Icons.Outlined.Tune, null,
                    tint = if (filterCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.IconButton(onClick = { infoOpen = true }) {
                Icon(androidx.compose.material.icons.Icons.Outlined.Info, "資料集與更新", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 排程進行中：一條細的階段進度（點了看詳情）
        if (job?.running == true) Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { infoOpen = true }
            .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)) {
            PipelineSteps(job?.step.orEmpty(), refreshOnly = job?.refreshOnly == true)
        }
        // 搜尋框：按搜尋圖示才出現
        if (searchOpen || query.isNotEmpty()) androidx.compose.foundation.text.BasicTextField(
            query, { query = it; shown = 100 }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.merge(androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = AppText.body)),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            decorationBox = { inner ->
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius)).padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("關鍵字、公司、代號", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        inner()
                    }
                    if (query.isNotEmpty()) Text("清除", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { query = ""; shown = 100 }.padding(start = 8.dp))
                }
            },
        )
        } }) {
        // 換條件或換檢視後清單回到最上面
        LaunchedEffect(query, days, sent, onlyImportant, onlyMine, sort, theme, symbol, source, view) { listState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState, contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (view == 0) {
                if (store.items.isEmpty()) item {
                    Text(if (job?.running == true) "正在蒐集新聞，完成後顯示於此。" else "尚無新聞。點選右上角的資訊圖示，再按「立即更新」開始蒐集。",
                        fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                }
                // grid：市場大局（所有題材重點的彙整）
                // 有新聞但還沒整理出重點、題材：說明狀況並引導到「全部」看原始新聞，不讓畫面空白
                if (store.items.isNotEmpty() && store.overview.isEmpty() && stats.important.isEmpty() && themes.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("已蒐集 ${store.items.size} 則新聞，重點整理中。", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        androidx.compose.material3.OutlinedButton(onClick = { view = 1 }) { Text("看全部新聞") }
                    }
                }
                if (store.overview.isNotEmpty()) item {
                    com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("市場大局", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(store.overviewAt)),
                                    fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            store.overview.forEach { p ->
                                Row {
                                    Box(Modifier.padding(top = 7.dp, end = 8.dp).size(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                                    Text(p, fontSize = AppText.secondary, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
                if (stats.important.isNotEmpty()) item {
                    Text("重點新聞", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                items(stats.important.take(6), key = { "i" + it.id }) { n ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) { Box(Modifier.padding(horizontal = 10.dp)) { NewsLine(n) } }
                }
                if (stats.important.size > 6) item {
                    TextButton(onClick = { onlyImportant = true; view = 1 }) { Text("看全部重點新聞 ›") }
                }
                if (themes.isNotEmpty()) item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("題材（近 3 天，共 ${themes.size} 個）", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    }
                }
                val maxCount = themes.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                items(themes.take(if (allThemes) themes.size else 15), key = { "t" + it.key }) { t ->
                    val open = expandedTheme == t.key
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().clickable { expandedTheme = if (open) null else t.key }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.key, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("${t.count} 則 · " + (if (t.avgSentiment > 0.2) "偏多" else if (t.avgSentiment < -0.2) "偏空" else "中性"), fontSize = AppText.caption,
                                    color = if (t.avgSentiment > 0.2) StockUp else if (t.avgSentiment < -0.2) StockDown else MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(t.count.toFloat() / maxCount).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                            }
                            if (open) {
                                val ds2 = digestsByTheme[t.key].orEmpty()
                                if (ds2.isEmpty()) Text("這個題材的重點還在整理中。", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ds2.take(3).forEach { d ->
                                    Text("${d.day.substring(5)}（${d.count} 則）", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    d.points.take(5).forEach { p ->
                                        Text("• ${p.text}" + (if (p.symbols.isNotEmpty()) "（${p.symbols.joinToString("、")}）" else ""), fontSize = AppText.secondary)
                                    }
                                }
                                TextButton(onClick = { theme = setOf(t.key); view = 1 }) { Text("看這個題材的 ${t.count} 則新聞 ›") }
                            }
                        }
                    }
                }
                if (themes.size > 15) item {
                    TextButton(onClick = { allThemes = !allThemes }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (allThemes) "只看前 15 個題材" else "顯示全部 ${themes.size} 個題材")
                    }
                }
            } else {
                if (filteredOrNull == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                else if (filtered.isEmpty()) item { Text("沒有符合條件的新聞", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                // 依日期分組（排序為「最新」時），每天一個標題
                val zoneTw = java.time.ZoneId.of("Asia/Taipei")
                val todayTw = java.time.LocalDate.now(zoneTw)
                fun dayOf(n: com.enwei.moneymanager.data.NewsItem) = java.time.Instant.ofEpochSecond(n.epochSec).atZone(zoneTw).toLocalDate()
                val page = filtered.take(shown)
                val dayCounts = if (sort == 0) filtered.groupingBy { dayOf(it) }.eachCount() else emptyMap()
                page.forEachIndexed { i, n ->
                    val d = dayOf(n)
                    if (sort == 0 && (i == 0 || dayOf(page[i - 1]) != d)) item(key = "d$d") {
                        Text(
                            (when (d) { todayTw -> "今天 · "; todayTw.minusDays(1) -> "昨天 · "; else -> "" }) +
                                "${d.monthValue}/${d.dayOfMonth}（${com.enwei.moneymanager.core.Recurrence.weekdayName(d.dayOfWeek)}）· ${dayCounts[d] ?: 0} 則",
                            fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    item(key = "n" + n.id) {
                        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) { Box(Modifier.padding(horizontal = 10.dp)) { NewsLine(n) } }
                    }
                }
                if (filtered.size > shown) item {
                    TextButton(onClick = { shown += 100 }, modifier = Modifier.fillMaxWidth()) { Text("顯示更多（還有 ${filtered.size - shown} 則）") }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
        // 篩選條件（底部面板）：時間、多空、重要、我的持股、題材／個股／來源、排序
        if (filterOpen) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { filterOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
            @Composable fun chip(on: Boolean, label: String, click: () -> Unit) =
                FilterChip(on, { click(); shown = 100 }, label = { Text(label, fontSize = AppText.caption, maxLines = 1) })
            @Composable fun group(title: String, body: @Composable () -> Unit) {
                Text(title, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { body() }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("篩選新聞", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (filterCount > 0) TextButton(onClick = {
                        days = 0; sent = 0; onlyImportant = false; onlyMine = false; sort = 0; theme = emptySet(); symbol = emptySet(); source = emptySet(); shown = 100
                    }) { Text("全部清除") }
                }
                group("時間") { listOf(0 to "全部", 1 to "今天", 3 to "3 天內", 7 to "7 天內").forEach { (d, l) -> chip(days == d, l) { days = d } } }
                group("多空") { listOf(0 to "全部", 1 to "利多", 2 to "利空").forEach { (v, l) -> chip(sent == v, l) { sent = v } } }
                group("範圍") {
                    chip(onlyImportant, "只看重要") { onlyImportant = !onlyImportant }
                    if (mine.isNotEmpty()) chip(onlyMine, "我的持股") { onlyMine = !onlyMine }
                }
                group("分類") {
                    chip(theme.isNotEmpty(), if (theme.isEmpty()) "題材 ▾" else "題材 ${theme.size} ▾") { pickList = "題材" }
                    chip(symbol.isNotEmpty(), if (symbol.isEmpty()) "個股 ▾" else "個股 ${symbol.size} ▾") { pickList = "個股" }
                    chip(source.isNotEmpty(), if (source.isEmpty()) "來源 ▾" else "來源 ${source.size} ▾") { pickList = "來源" }
                }
                group("排序") { listOf("最新", "最重要", "多空最強").forEachIndexed { i, l -> chip(sort == i, l) { sort = i } } }
            }
        }
        // 資料集詳情與設定（自動更新開關、更新、刪除、排程狀態）
        if (infoOpen) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { infoOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("新聞資料集", fontSize = AppText.title, fontWeight = FontWeight.SemiBold)
                // 自動更新：開關＋頻率
                Box(Modifier.blockSurface(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (aiReady) "自動更新與每日報告" else "自動更新新聞", fontSize = AppText.body, modifier = Modifier.weight(1f))
                            Switch(checked = settings.advisorDaily, onCheckedChange = { enabled -> repo.settings.update { it.copy(advisorDaily = enabled) } })
                        }
                        // 沒有 AI：新聞照常蒐集，在手機上依關鍵字分題材；設定 AI 後才有摘要與每日報告
                        if (!aiReady) {
                            Text("未使用 AI：新聞依關鍵字整理。設定 AI 後可產生摘要與每日報告。", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { infoOpen = false; nav.navigate("ai") }) { Text("前往 AI 設定 ›", fontSize = AppText.caption) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                            listOf(1, 3, 6).forEach { h ->
                                FilterChip(settings.newsIntervalHours == h, {
                                    repo.settings.update { it.copy(newsIntervalHours = h) }
                                    com.enwei.moneymanager.work.NewsWorker.schedule(ctx, replace = true)
                                }, label = { Text("每 $h 小時", fontSize = AppText.caption, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                // 資料量：三格數字
                val grid by com.enwei.moneymanager.MoneyApp.news.grid.collectAsStateWithLifecycle()
                val pct = stats.coverage?.let { c -> if (c.recent > 0) c.digested * 100 / c.recent else 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable fun tile(value: String, label: String, sub: String, done: Boolean = false) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(value, fontSize = AppText.title, fontWeight = FontWeight.SemiBold, color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                                Text(label, fontSize = AppText.caption, maxLines = 1)
                                Text(sub, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    tile("${store.items.size}", "則新聞", "近 ${com.enwei.moneymanager.data.NewsDataset.KEEP_DAYS} 天 · 標註 ${stats.tagged}")
                    tile(pct?.let { "$it%" } ?: "–", "近 3 天整理", stats.coverage?.let { "${it.digested}/${it.recent} 則" } ?: "", pct == 100)
                    tile("${grid.complete}/${grid.blocks}", "完整區塊", if (grid.unread > 0) "待讀 ${grid.unread} 則" else "日期×題材", grid.done)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = job?.running != true && reportWork?.running != true,
                        onClick = { com.enwei.moneymanager.work.ReportJob.startNow(ctx, refreshOnly = true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("立即更新") }
                    OutlinedButton(
                        enabled = (store.items.isNotEmpty() || store.report != null || store.chat.isNotEmpty() || store.chatThreads.isNotEmpty()) && job?.running != true && reportWork?.running != true &&
                            newsWork?.running != true && chatWork?.running != true,
                        onClick = { clearDatabaseError = null; confirmClearDatabase = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("刪除資料庫", color = MaterialTheme.colorScheme.error) }
                }
                JobProgress(job, reportWork)
                com.enwei.moneymanager.ui.components.AiWorkProgress(newsWork, Modifier.fillMaxWidth())
                NewsScheduleInfo()
                Spacer(Modifier.height(24.dp))
            }
        }
        // 題材／個股／來源清單：可搜尋、可複選，每列顯示則數與多空
        pickList?.let { kind ->
            androidx.compose.material3.ModalBottomSheet(onDismissRequest = { pickList = null }, containerColor = MaterialTheme.colorScheme.surface) {
                var q by remember { mutableStateOf("") }
                val sel = when (kind) { "題材" -> theme; "個股" -> symbol; else -> source }
                fun setSel(v: Set<String>) { when (kind) { "題材" -> theme = v; "個股" -> symbol = v; else -> source = v }; shown = 100 }
                // (名稱, 顯示名稱, 則數, 平均多空)
                val rows = remember(kind, stats) {
                    when (kind) {
                        "題材" -> themes.map { Quad(it.key, it.key, it.count, it.avgSentiment) }
                        "個股" -> stats.symbols.map { Quad(it.key, it.key + (com.enwei.moneymanager.net.TwData.nameOf(it.key)?.let { n -> " $n" } ?: ""), it.count, it.avgSentiment) }
                        else -> stats.sources.map { (k, n) -> Quad(k, k, n, 0.0) }
                    }
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$kind（${rows.size}）" + (if (sel.isNotEmpty()) " · 已選 ${sel.size}" else ""), fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (sel.isNotEmpty()) TextButton(onClick = { setSel(emptySet()) }) { Text("清除") }
                        TextButton(onClick = { pickList = null }) { Text("完成" + (if (filteredOrNull != null) "（${filtered.size} 則）" else "")) }
                    }
                    OutlinedTextField(q, { q = it }, placeholder = { Text("搜尋$kind") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val maxN = rows.maxOfOrNull { it.c }?.coerceAtLeast(1) ?: 1
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                        // 已選的排最前面
                        val list = rows.filter { q.isBlank() || it.b.contains(q.trim(), true) }.sortedByDescending { it.a in sel }
                        items(list, key = { it.a }) { r ->
                            val on = r.a in sel
                            Row(
                                Modifier.fillMaxWidth().clickable { setSel(if (on) sel - r.a else sel + r.a) }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                androidx.compose.material3.Checkbox(on, { setSel(if (it) sel + r.a else sel - r.a) })
                                Column(Modifier.weight(1f)) {
                                    Text(r.b, fontSize = AppText.body, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Box(Modifier.fillMaxWidth(r.c.toFloat() / maxN).height(3.dp).background(
                                        if (r.d > 0.2) StockUp else if (r.d < -0.2) StockDown else MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
                                }
                                Text("${r.c} 則", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
        if (confirmClearDatabase) AlertDialog(
            onDismissRequest = { if (!clearingDatabase) confirmClearDatabase = false },
            title = { Text("刪除新聞資料庫？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("將永久刪除所有新聞、重點摘要、每日與歷史報告、AI 對話及公司說明快取。持股、帳務與 AI 設定不會變更；若新聞自動更新仍啟用，之後會重新蒐集資料。")
                    clearDatabaseError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = job?.running != true && reportWork?.running != true &&
                        newsWork?.running != true && chatWork?.running != true && !clearingDatabase,
                    onClick = {
                        clearingDatabase = true
                        scope.launch {
                            try {
                                ds.clear()
                                query = ""
                                days = 0
                                sent = 0
                                onlyImportant = false
                                onlyMine = false
                                sort = 0
                                theme = emptySet()
                                symbol = emptySet()
                                source = emptySet()
                                shown = 100
                                confirmClearDatabase = false
                                clearDatabaseError = null
                                listState.scrollToItem(0)
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                clearDatabaseError = "刪除失敗：${e.message ?: e.javaClass.simpleName}"
                            } finally {
                                clearingDatabase = false
                            }
                        }
                    },
                ) { Text(if (clearingDatabase) "刪除中…" else "永久刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(enabled = !clearingDatabase, onClick = { confirmClearDatabase = false }) { Text("取消") }
            },
        )
    }
}

/** 一個題材的新聞重點：標題列點一下展開／收起。 */
@Composable
private fun DigestFolder(d: com.enwei.moneymanager.data.ThemeDigest) {
    val fold = com.enwei.moneymanager.ui.components.rememberFold("digest." + d.theme, false)
    val open = fold.open
    Column(Modifier.fillMaxWidth().clickable { fold.toggle() }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${d.theme}　${d.day.substring(5)} · ${d.count} 則", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            com.enwei.moneymanager.ui.components.FoldToggle(open, d.points.size)
        }
        (if (open) d.points else d.points.take(1)).forEach { p ->
            Text(
                (if (p.sentiment > 0) "▲ " else if (p.sentiment < 0) "▼ " else "• ") + p.text +
                    (if (p.symbols.isNotEmpty()) "（${p.symbols.joinToString(",")}）" else "") + if (p.count > 1) " ×${p.count}" else "",
                fontSize = AppText.secondary, maxLines = if (open) Int.MAX_VALUE else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (p.sentiment > 0) StockUp else if (p.sentiment < 0) StockDown else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 一列可左右捲動的篩選選項，前面有小標題。 */
@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 36.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** 資金與紀律：大盤階段、資金分配、持股動態停損與交易紀律。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlanTab(nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val holdings by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    var snap by remember { mutableStateOf<AdvisorSnapshot?>(null) }
    var editPlan by remember { mutableStateOf(false) }
    var editRules by remember { mutableStateOf(false) }
    var editGuard by remember { mutableStateOf<com.enwei.moneymanager.data.HoldingPlan?>(null) }
    LaunchedEffect(holdings, settings.investPlan.copy(guards = emptyMap()), settings.investPlan.guards.mapValues { it.value.copy(peak = null) }, settings.stockStyle) { snap = runCatching { repo.advisorSnapshot(fetch = true) }.getOrNull() }
    fun m(v: Long) = Money.withSymbol(v, "TWD")
    // 分成三段，每段一個畫面看得完：資金（大盤＋配置）、持股紀律、我的紀律（參數＋原則）
    var sub by com.enwei.moneymanager.ui.components.rememberTab("advisor.plan")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        com.enwei.moneymanager.ui.components.SubTabs(listOf("資金", "持股", "我的紀律"), sub) { sub = it }
        val s = snap
        if (s == null && sub < 2) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (sub == 0) {
        // ① 資金配置（這一頁的重點，放最上面）：比例條（長線、短線、可投入、預留）＋與目標比較
        if (s != null) Section("資金配置", action = "調整" to { editPlan = true }) {
            val a = s.alloc
            // 需要處理的警示放最上面、用醒目的底色（原本是最下面一行小字）
            val warns = listOfNotNull(
                a.reserveShort.takeIf { it > 0 }?.let { "可動用現金低於預留金額 ${m(it)}" },
                a.shortOver.takeIf { it > 0 }?.let { "短線超出計畫 ${m(it)}，新的短線單先暫停" },
            )
            warns.forEach { w ->
                Text(w, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = AppText.body,
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 8.dp))
            }
            val spare = (a.cash - a.reserve).coerceAtLeast(0)
            val parts = listOf(
                Triple("長線", a.longValue, MaterialTheme.colorScheme.primary), Triple("短線", a.shortValue, Color(0xFFFFA000)),
                Triple("可投入", spare, Color(0xFF3C82E6)), Triple("預留", minOf(a.reserve, a.cash).coerceAtLeast(0), MaterialTheme.colorScheme.onSurfaceVariant),
            ).filter { it.second > 0 }
            val total = parts.sumOf { it.second }.coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))) {
                parts.forEach { (_, v, c) -> Box(Modifier.weight((v.toFloat() / total).coerceAtLeast(0.01f)).height(16.dp).background(c)) }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                parts.forEach { (n, v, c) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(c, RoundedCornerShape(4.dp)))
                        Text(" $n ${m(v)}", fontSize = AppText.caption)
                    }
                }
            }
            Bucket("長線・目標 ${"%.0f".format(settings.investPlan.longRatio * 100)}%", a.longValue, a.longTarget, a.longRoom, ::m)
            Bucket("短線・目標 ${"%.0f".format(100 - settings.investPlan.longRatio * 100)}%", a.shortValue, a.shortTarget, a.shortRoom, ::m)
        }
        // ② 大盤：加權指數 K 線＋階段（可收合，預設收起；階段標籤在標題下方就看得到）
        if (s != null) Section("大盤（${s.indexStage.label}）", collapsible = true, initiallyOpen = false) {
            val idx = s.index
            com.enwei.moneymanager.ui.components.AnnotatedKLine("^TWII", if (idx != null && idx.close.size >= 20) listOf(
                Triple("壓力（60 日高）", idx.high.takeLast(60).max(), Color(0xFFAB47BC)),
                Triple("支撐（60 日低）", idx.low.takeLast(60).min(), Color(0xFF26A69A)),
            ) else emptyList())
            StageChip(s.indexStage.label, s.indexStage.hint)
            if (s.sox != null) Text("費城半導體：${s.soxStage.label}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        }
        if (sub == 1 && s != null && s.holdings.isEmpty()) Text("目前沒有持股。", fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // ③ 持股紀律＋AI：每檔一張小卡（價位圖、紀律動作、今日報告的 AI 動作）
        if (sub == 1 && s != null && s.holdings.isNotEmpty()) Section("持股紀律與 AI 建議") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("依你設定的門檻提示停損／停利", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Switch(settings.investPlan.disciplineOn, { on -> repo.settings.update { it.copy(investPlan = it.investPlan.copy(disciplineOn = on)) } })
            }
            val store by MoneyApp.news.flow.collectAsStateWithLifecycle()
            val ai = store?.report?.holdings.orEmpty().associateBy { it.symbol }
            s.holdings.forEach { h ->
                if (settings.investPlan.disciplineOn && settings.investPlan.guardOn) {
                    GuardRow(h, ai[h.symbol], nav, last = h == s.holdings.last()) { editGuard = h }
                } else HoldingCard(h, ai[h.symbol], settings.investPlan.disciplineOn, nav)
            }
        }
        // 紀律參數：分組成「項目｜數值」兩欄，數字一眼對齊；點「調整」修改
        if (sub == 2) Section("我的紀律參數", action = "調整" to { editPlan = true }) {
            val p = settings.investPlan
            @Composable fun group(title: String) = Text(title, fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
            @Composable fun row(label: String, value: String, note: String? = null) {
                Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, fontSize = AppText.body, modifier = Modifier.weight(1f))
                        Text(value, fontSize = AppText.body, fontWeight = FontWeight.SemiBold)
                    }
                    note?.let { Text(it, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                com.enwei.moneymanager.ui.components.RowDivider()
            }
            if (!p.disciplineOn) Text("持股紀律建議目前關閉，點選「調整」開啟。", fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (p.guardOn) {
                group("上漲時")
                row("移動停利（自最高價回落）", "%.1f%%".format(p.guardTrailPct), "保留成本，只賣出獲利的股數")
                row("減碼價跌破時賣出", "約 %.0f%%".format(p.reducePct))
                group("下跌時（60 分 K）")
                row("觀察均線", "${p.hourFast}MA・${p.hourSlow}MA")
                row("跌破其中一條", "賣出約 %.0f%%".format(p.reducePct))
                row("兩條皆跌破", "保留 ${p.baseShares.toLong()} 股", "重新站上兩條均線視為買進訊號")
                group("避免誤判")
                row("容許跌破幅度", "%.1f%%".format(p.tolerancePct))
                row("連續確認", "${p.confirmBars} 根 K 棒")
                group("輔助參考")
                row("日 K 均線", p.dailyMas.joinToString("・") + " 日")
            } else {
                group("短線")
                row("停損（跌破買進價）", "%.0f%%".format(p.shortStopPct))
                row("移動停損（自高點回落）", "%.0f%%".format(p.shortTrailPct))
                group("長線")
                row("移動停損（自高點回落）", "%.0f%%".format(p.longTrailPct))
                group("停利")
                row("分批停利", "獲利 %.0f%% 先賣 1/3".format(p.takeProfitPct), "長線為兩倍門檻")
            }
        }
        // 投資原則：一條一列，可直接刪除；下方一行新增，或從常用原則挑選
        if (sub == 2) Section("我的投資原則（${settings.investPlan.rules.size} 條）") {
            val rules = settings.investPlan.rules
            fun save(list: List<String>) = repo.settings.update { it.copy(investPlan = it.investPlan.copy(rules = list)) }
            if (rules.isEmpty()) Text("尚未設定投資原則。", fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rules.forEachIndexed { i, r ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Text("${i + 1}", fontSize = AppText.body, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(24.dp))
                    Text(r, fontSize = AppText.body, modifier = Modifier.weight(1f))
                    Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { save(rules.filterIndexed { j, _ -> j != i }) }.padding(start = 10.dp, end = 2.dp))
                }
                com.enwei.moneymanager.ui.components.RowDivider()
            }
            var draft by remember { mutableStateOf("") }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OutlinedTextField(draft, { draft = it }, placeholder = { Text("新增一條原則") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(enabled = draft.isNotBlank(), onClick = { save(rules + draft.trim()); draft = "" }) { Text("新增") }
            }
            val presets = listOf("單一個股不超過資金的 20%", "虧損 8% 一律停損，不攤平", "只買看得懂的公司", "財報公布前不加碼",
                "大盤跌破季線時降低持股", "每月固定投入，不追高").filter { it !in rules }
            if (presets.isNotEmpty()) {
                Text("常用原則", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    presets.forEach { pr ->
                        Surface(shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { save(rules + pr) }) {
                            Text("＋ $pr", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (editPlan) PlanDialog(settings.investPlan) { editPlan = false }
    if (editRules) RulesDialog(settings.investPlan.rules) { editRules = false }
    editGuard?.let { h -> GuardDialog(h, settings.investPlan.guards[h.symbol] ?: com.enwei.moneymanager.data.GuardState()) { editGuard = null } }
}

/**
 * 一檔持股的保本＋移動停利：標題、紀律與 AI 的動作標籤、重點數字；展開後看完整數字、均線與 AI 理由。
 * 點標題列展開／收合（不顯示點擊波紋）；每檔之間以分隔線隔開，AI 建議放在同一檔裡。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun GuardRow(h: com.enwei.moneymanager.data.HoldingPlan, ai: com.enwei.moneymanager.data.AdvisorHolding?, nav: NavController, last: Boolean, onEdit: () -> Unit) {
    val gi = h.guardInput
    val g = h.guard
    fun f(v: Double?) = v?.let { "%.2f".format(it) } ?: "?"
    fun n(v: Double) = "%,.0f".format(v)
    var open by rememberSaveable(h.symbol) { mutableStateOf(g?.triggered == true) }
    val quiet = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 標題列：名稱＋展開
        Row(
            Modifier.fillMaxWidth().clickable(interactionSource = quiet, indication = null) { open = !open },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${h.symbol} ${h.name.orEmpty()}", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            com.enwei.moneymanager.ui.components.FoldToggle(open, modifier = Modifier.padding(start = 6.dp))
        }
        // 動作標籤：紀律與 AI 並排
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (g != null) Surface(
                color = if (g.triggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    (if (g.triggered) g.action.label.substringBefore('：') + "：賣 ${g.sellShares.toLong()} 股" else g.action.label.substringBefore('，')),
                    fontSize = AppText.caption, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (ai != null) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                Text("AI：${ai.action}", fontSize = AppText.caption, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
            }
        }
        if (gi == null || g == null) {
            Text("無法取得現價", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                if (g.excessShares >= 1) "多賺 ${g.excessShares.toLong()} 股 · 停利價 ${f(g.takeProfit)}"
                else if (g.excessValue > 0) "多賺 ${n(g.excessValue)} 元，還不到一股" else "比成本少 ${n(-g.excessValue)} 元",
                fontSize = AppText.body, color = if (g.excessValue >= 0) StockUp else StockDown,
            )
            if (open) {
                // 細節放在淡底色的區塊裡，和標題、重點分開
                Column(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    @Composable fun kv(k: String, v: String) = Row {
                        Text(k, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(64.dp))
                        Text(v, fontSize = AppText.caption, modifier = Modifier.weight(1f))
                    }
                    kv("持股", "${"%.0f".format(gi.shares)} 股 · 現價 ${f(gi.price)}")
                    kv("市值", n(gi.shares * gi.price) + (g.peak?.let { " · 最高 ${f(it)}" } ?: ""))
                    kv("保本", n(gi.principal) + if (h.autoPrincipal) "（自動）" else "")
                    kv("60 分 K", "20MA ${f(gi.hourMa20)}" + (gi.hourMa20?.let { if (gi.price < it) "（跌破）" else "（之上）" } ?: "") +
                        "、50MA ${f(gi.hourMa50)}" + (gi.hourMa50?.let { if (gi.price < it) "（跌破）" else "（之上）" } ?: "") +
                        (gi.reduceAt?.let { " · 減碼價 ${f(it)}" } ?: ""))
                    h.trend?.let { kv("日 K", it.summary) }
                    kv("紀律", g.note)
                    ai?.let { kv("AI", com.enwei.moneymanager.data.stripCites(it.reason)) }
                }
                Row {
                    TextButton(onClick = onEdit) { Text("設定保本與停利", fontSize = AppText.caption) }
                    TextButton(onClick = { nav.navigate("stock/${h.symbol}") }) { Text("看 K 線", fontSize = AppText.caption) }
                }
            }
        }
    }
    if (!last) com.enwei.moneymanager.ui.components.LineDivider()
}

/** 改一檔的保本金額、手動停利價、減碼價；也可以重設移動停利的最高價。 */
@Composable
private fun GuardDialog(h: com.enwei.moneymanager.data.HoldingPlan, gs: com.enwei.moneymanager.data.GuardState, onDismiss: () -> Unit) {
    val gi = h.guardInput
    fun t(v: Double?) = v?.let { if (it % 1.0 == 0.0) "%.0f".format(it) else "%.2f".format(it) }.orEmpty()
    var principal by remember { mutableStateOf(t(gs.principal)) }
    var tp by remember { mutableStateOf(t(gs.takeProfit)) }
    var reduce by remember { mutableStateOf(t(gs.reduceAt)) }
    var resetPeak by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${h.symbol} ${h.name.orEmpty()}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(principal, { principal = it }, "保本金額（空白為自動）", suffix = "元")
                if (gi != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { principal = "%.0f".format(gi.shares * gi.price) }) { Text("改成目前市值", fontSize = AppText.caption) }
                    OutlinedButton(onClick = { principal = "" }) { Text("自動", fontSize = AppText.caption) }
                }
                NumberField(tp, { tp = it }, "手動停利價", suffix = "元")
                NumberField(reduce, { reduce = it }, "減碼價（跌破賣一半）", suffix = "元")
                if (gi != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 8).forEach { pct -> OutlinedButton(onClick = { reduce = "%.2f".format(gi.price * (1 - pct / 100.0)) }) { Text("−$pct%", fontSize = AppText.caption) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("重設移動停利的最高價", fontSize = AppText.secondary, modifier = Modifier.weight(1f))
                    Switch(resetPeak, { resetPeak = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val ng = gs.copy(
                    principal = principal.replace(",", "").toDoubleOrNull()?.takeIf { it > 0 },
                    takeProfit = tp.toDoubleOrNull()?.takeIf { it > 0 },
                    reduceAt = reduce.toDoubleOrNull()?.takeIf { it > 0 },
                    peak = if (resetPeak) null else gs.peak,
                )
                repo.settings.update { it.copy(investPlan = it.investPlan.copy(guards = it.investPlan.guards + (h.symbol to ng))) }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun Section(
    title: String, action: Pair<String, () -> Unit>? = null,
    /** 可以點標題收合（像資料夾）；[initiallyOpen] 為預設是否展開 */
    collapsible: Boolean = false, initiallyOpen: Boolean = true,
    content: @Composable () -> Unit,
) {
    val fold = com.enwei.moneymanager.ui.components.rememberFold("advisor." + title.substringBefore('（'), initiallyOpen)
    val open = fold.open
    com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                (if (collapsible) Modifier.clickable { fold.toggle() } else Modifier).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.let { (l, f) -> TextButton(onClick = f) { Text(l) } }
                if (collapsible) com.enwei.moneymanager.ui.components.FoldToggle(open)
            }
            if (!collapsible || open) content()
        }
    }
}

/**
 * AI 寫的文字：[1][2] 顯示成可點的新聞編號，點了開那則新聞；[ids] 是編號對應的新聞 id。
 * 對不到新聞的編號（舊資料）不顯示。
 */
@Composable
fun CitedText(
    text: String, ids: List<String>, newsById: Map<String, com.enwei.moneymanager.data.NewsItem>,
    fontSize: androidx.compose.ui.unit.TextUnit = AppText.body, color: Color = Color.Unspecified, modifier: Modifier = Modifier,
) {
    val link = MaterialTheme.colorScheme.primary
    val annotated = remember(text, ids, System.identityHashCode(newsById)) {
        val text = cleanSourceTags(text)
        androidx.compose.ui.text.buildAnnotatedString {
            var last = 0
            citeGroup.findAll(text).forEach { m ->
                append(text.substring(last, m.range.first)); last = m.range.last + 1
                m.groupValues[1].split(Regex("[,，、\\s]+")).mapNotNull { it.toIntOrNull() }.forEach { k ->
                    val n = ids.getOrNull(k - 1)?.let { newsById[it] } ?: return@forEach
                    val url = n.url.ifBlank { "https://www.google.com/search?tbm=nws&q=" + java.net.URLEncoder.encode(n.title, "UTF-8") }
                    withLink(
                        androidx.compose.ui.text.LinkAnnotation.Url(
                            url, androidx.compose.ui.text.TextLinkStyles(androidx.compose.ui.text.SpanStyle(color = link, fontWeight = FontWeight.Bold)),
                        ),
                    ) { append("[$k]") }
                }
            }
            append(text.substring(last))
        }
    }
    androidx.compose.material3.Text(annotated, fontSize = fontSize, color = color, modifier = modifier)
}

private val citeGroup = Regex("\\s*[\\[［【]\\s*(\\d{1,3}(?:\\s*[,，、]\\s*\\d{1,3})*)\\s*[\\]］】]")

@Composable
private fun StageChip(label: String, hint: String) {
    // 說明放在標籤下方：放旁邊時說明一長，標籤會被擠到中間、左邊留一大塊空白
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
            Text(label, fontSize = AppText.secondary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        if (hint.isNotBlank()) Text(hint, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bucket(label: String, used: Long, target: Long, room: Long, m: (Long) -> String) {
    // 每列一個重點、不換行：上排「名稱｜已投入」，下排「目標｜還可投入」
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = AppText.body, maxLines = 1, modifier = Modifier.weight(1f))
            Text(m(used), fontSize = AppText.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        val f = if (target > 0) (used.toFloat() / target).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).background(if (used > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
        }
        Row {
            Text("目標 ${m(target)}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.weight(1f))
            Text("還可投入 ${m(room)}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun PlanDialog(p: InvestPlanSettings, onDismiss: () -> Unit) {
    var reserve by remember { mutableStateOf(Money.fromMinor(p.reserve).stripTrailingZeros().toPlainString()) }
    var longPct by remember { mutableStateOf("%.0f".format(p.longRatio * 100)) }
    var stop by remember { mutableStateOf(p.shortStopPct.toString()) }
    var trail by remember { mutableStateOf(p.shortTrailPct.toString()) }
    var longTrail by remember { mutableStateOf(p.longTrailPct.toString()) }
    var tp by remember { mutableStateOf(p.takeProfitPct.toString()) }
    var disciplineOn by remember { mutableStateOf(p.disciplineOn) }
    var guardOn by remember { mutableStateOf(p.guardOn) }
    var gTrail by remember { mutableStateOf(p.guardTrailPct.toString()) }
    var base by remember { mutableStateOf(p.baseShares.toLong().toString()) }
    var fast by remember { mutableStateOf(p.hourFast.toString()) }
    var slow by remember { mutableStateOf(p.hourSlow.toString()) }
    var reduce by remember { mutableStateOf("%.0f".format(p.reducePct)) }
    var tol by remember { mutableStateOf(p.tolerancePct.toString()) }
    var confirm by remember { mutableStateOf(p.confirmBars.toString()) }
    var dailyMas by remember { mutableStateOf(p.dailyMas.joinToString(", ")) }
    var aiNote by remember { mutableStateOf<String?>(null) }
    var aiBusy by remember { mutableStateOf(false) }
    val aiScope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("資金與持股建議") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(reserve, { reserve = it }, "預留金額", suffix = "元")
                NumberField(longPct, { longPct = it }, "長線占可投資資金", suffix = "%")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("持股紀律建議")
                        Text("依你設定的參數計算，預設關閉", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(disciplineOn, {
                        disciplineOn = it
                        if (!it) guardOn = false
                    })
                }
                if (disciplineOn) {
                    // 讓 AI 讀「我的投資原則」換算成參數（原則沒提到的不改）
                    if (p.rules.isNotEmpty() && GroqAi.enabled) OutlinedButton(enabled = !aiBusy, onClick = {
                        aiBusy = true
                        aiScope.launch {
                            val r = runCatching { GroqAi.disciplineFromRules(p.rules) }.getOrNull()
                            if (r == null) aiNote = "AI 沒有回應：${GroqAi.lastError ?: "請稍後再試"}"
                            else {
                                val (m, note) = r
                                fun f(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()
                                m["shortStopPct"]?.let { stop = f(it) }
                                m["shortTrailPct"]?.let { trail = f(it) }
                                m["longTrailPct"]?.let { longTrail = f(it) }
                                m["takeProfitPct"]?.let { tp = f(it) }
                                m["guardTrailPct"]?.let { gTrail = f(it) }
                                aiNote = if (m.isEmpty()) "投資原則裡沒有找到可換算的數字" else "已依投資原則帶入 ${m.size} 項（按儲存才生效）：$note"
                            }
                            aiBusy = false
                        }
                    }) { Text(if (aiBusy) "AI 讀取中…" else "依我的投資原則帶入（AI）") }
                    aiNote?.let { Text(it, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("保本＋移動停利", fontSize = AppText.body, modifier = Modifier.weight(1f))
                        Switch(guardOn, { guardOn = it })
                    }
                    if (guardOn) {
                        @Composable fun head(t: String) = Text(t, fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                        @Composable fun note(t: String) = Text(t, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        head("一、股價上漲：保本＋移動停利")
                        note("集保永遠保留「市值約等於原始成本」的股數，只賣多賺的部分；不見價就賣，先設停利價並跟著往上移。")
                        NumberField(gTrail, { gTrail = it }, "移動停利：從最高價回落", suffix = "%")
                        NumberField(reduce, { reduce = it }, "想減碼時，跌破減碼價賣出", suffix = "%")
                        head("二、股價下跌：60 分 K 均線")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { NumberField(fast, { fast = it }, "快線", suffix = "MA") }
                            Box(Modifier.weight(1f)) { NumberField(slow, { slow = it }, "慢線", suffix = "MA") }
                        }
                        NumberField(reduce, { reduce = it }, "跌破其中一條，賣出", suffix = "%")
                        NumberField(base, { base = it }, "兩條都跌破，只留基本股", suffix = "股")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { NumberField(tol, { tol = it }, "容許稍微跌破", suffix = "%") }
                            Box(Modifier.weight(1f)) { NumberField(confirm, { confirm = it }, "連續確認", suffix = "根") }
                        }
                        note("股價突破兩條線是買入訊號；只是短暫稍微跌破（在容許範圍內、或沒有連續確認）不賣，避免來回震盪被洗出場。")
                        head("輔助參考：日 K 均線")
                        OutlinedTextField(dailyMas, { dailyMas = it }, label = { Text("天數（逗號分開）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    } else {
                        NumberField(stop, { stop = it }, "短線停損", suffix = "%")
                        NumberField(trail, { trail = it }, "短線移動停損（從高點回落）", suffix = "%")
                        NumberField(longTrail, { longTrail = it }, "長線移動停損（從高點回落）", suffix = "%")
                        NumberField(tp, { tp = it }, "分批停利門檻（長線為兩倍）", suffix = "%")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                repo.settings.update {
                    it.copy(
                        investPlan = p.copy(
                            reserve = Money.parseMinor(reserve) ?: p.reserve,
                            longRatio = (longPct.toDoubleOrNull() ?: (p.longRatio * 100)).coerceIn(0.0, 100.0) / 100,
                            shortStopPct = stop.toDoubleOrNull()?.coerceIn(1.0, 50.0) ?: p.shortStopPct,
                            shortTrailPct = trail.toDoubleOrNull()?.coerceIn(1.0, 50.0) ?: p.shortTrailPct,
                            longTrailPct = longTrail.toDoubleOrNull()?.coerceIn(1.0, 60.0) ?: p.longTrailPct,
                            takeProfitPct = tp.toDoubleOrNull()?.coerceIn(1.0, 200.0) ?: p.takeProfitPct,
                            guardOn = guardOn,
                            disciplineOn = disciplineOn,
                            guardTrailPct = gTrail.toDoubleOrNull()?.coerceIn(0.5, 30.0) ?: p.guardTrailPct,
                            hourFast = fast.toIntOrNull()?.coerceIn(2, 200) ?: p.hourFast,
                            hourSlow = slow.toIntOrNull()?.coerceIn(2, 300) ?: p.hourSlow,
                            reducePct = reduce.toDoubleOrNull()?.coerceIn(5.0, 100.0) ?: p.reducePct,
                            tolerancePct = tol.toDoubleOrNull()?.coerceIn(0.0, 10.0) ?: p.tolerancePct,
                            confirmBars = confirm.toIntOrNull()?.coerceIn(1, 10) ?: p.confirmBars,
                            dailyMas = dailyMas.split(Regex("[,，、\\s]+")).mapNotNull { it.toIntOrNull()?.takeIf { d -> d in 2..300 } }.distinct().ifEmpty { p.dailyMas },
                            baseShares = base.toDoubleOrNull()?.coerceIn(0.0, 1_000_000.0) ?: p.baseShares,
                        ),
                    )
                }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun RulesDialog(rules: List<String>, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(rules.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("我的交易紀律（一行一條）") },
        text = { OutlinedTextField(text, { text = it }, minLines = 6, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(onClick = {
                val list = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                repo.settings.update { it.copy(investPlan = it.investPlan.copy(rules = list.ifEmpty { InvestPlanSettings().rules })) }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 背景新聞排程的具體狀態：下次執行時間、等待的條件、暫停或限流的原因。 */
@Composable
private fun NewsScheduleInfo() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val health by com.enwei.moneymanager.work.AiHealth.state.collectAsStateWithLifecycle()
    var info by remember { mutableStateOf<androidx.work.WorkInfo?>(null) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val live = androidx.work.WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkLiveData("news")
        val obs = androidx.lifecycle.Observer<List<androidx.work.WorkInfo>> { info = it.firstOrNull { w -> !w.state.isFinished } ?: it.lastOrNull() }
        live.observe(owner, obs)
        onDispose { live.removeObserver(obs) }
    }
    val hm = java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN)
    val now = System.currentTimeMillis()
    val lines = buildList {
        val need = listOf(if (settings.aiWifiOnly) "Wi-Fi" else "網路連線", "電量不低").joinToString("、")
        when {
            !settings.advisorDaily -> add("背景自動更新已關閉（上方開關）")
            health.paused(now) -> add("${health.pauseReason ?: "背景 AI 已暫停"}；${hm.format(java.util.Date(health.pausedUntil))} 恢復")
            info == null -> add("背景排程尚未建立（重開 App 會建立）")
            info!!.state == androidx.work.WorkInfo.State.RUNNING -> add("背景更新執行中")
            else -> {
                val next = info!!.nextScheduleTimeMillis.takeIf { it in 1 until Long.MAX_VALUE }
                add("下次背景更新：" + (next?.let { if (it <= now) "等條件符合就執行" else "約 ${hm.format(java.util.Date(it))}" } ?: "等待系統排程") + "（需要 $need）")
                com.enwei.moneymanager.work.AiHealth.networkProblem(ctx)?.let { add("目前無法執行：$it") }
            }
        }
        GroqAi.coolingUntil()?.takeIf { it > now }?.let { add("AI 額度暫停到 ${hm.format(java.util.Date(it))}") }
        if (health.failures > 0 && !health.paused(now)) add("最近連續失敗 ${health.failures} 次：${health.pauseReason ?: "沒有回應"}")
    }
    val usage = run {
        val models = GroqAi.AiTask.entries.flatMap { t -> GroqAi.routes(t, 0) + GroqAi.routes(t, Int.MAX_VALUE) }.map { it.qid to (it.provider == GroqAi.Provider.GEMINI) }
        com.enwei.moneymanager.work.AiQuota.todaySummary(models)
    }
    val job by com.enwei.moneymanager.work.ReportJob.state.collectAsStateWithLifecycle()
    val chatJob by com.enwei.moneymanager.work.ChatJob.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // 問 AI 的對話流程：搜尋新聞 → 讀全文 → AI 回答 → 確認代號
        chatJob?.let { c ->
            if (c.running) {
                Text("AI 對話程序：「${c.question.take(16)}${if (c.question.length > 16) "…" else ""}」", fontSize = AppText.caption, fontWeight = FontWeight.SemiBold)
                ChatSteps(c.step)
                Text(c.step.ifBlank { "準備中…" } + " · 已 ${((now - c.startedAt) / 1000).coerceAtLeast(0)} 秒", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary)
            } else if (now - c.startedAt < 6 * 3600_000L) Text("上一次對話（${hm.format(java.util.Date(c.startedAt))}）：${c.error ?: c.step.ifBlank { "已回覆" }}", fontSize = AppText.caption,
                color = if (c.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 執行中的進度已在上方顯示；這裡只留上一輪的結果
        job?.let { j ->
            if (!j.running) Text("上一輪（${hm.format(java.util.Date(j.startedAt))}）：${j.error ?: j.step}", fontSize = AppText.caption,
                color = if (j.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lines.forEach { Text(it, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (health.paused(now)) TextButton(onClick = { com.enwei.moneymanager.work.AiHealth.resume() }) { Text("恢復背景更新") }
        // 今日 AI 用量：預設收起，展開後每個模型一行
        if (usage.isNotEmpty()) {
            var open by remember { mutableStateOf(false) }
            Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("今日 AI 用量（${usage.size} 個模型）", fontSize = AppText.caption, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                com.enwei.moneymanager.ui.components.FoldToggle(open)
            }
            if (open) usage.forEach { u ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(u.substringBefore("："), fontSize = AppText.label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(u.substringAfter("：").removePrefix("今天 "), fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}


/** 對話回覆裡的一張圖表。 */
@Composable
private fun ChatChartView(ch: com.enwei.moneymanager.data.ChatChart, nav: NavController) {
    when (ch.kind) {
        "compare" -> com.enwei.moneymanager.ui.components.CompareChart(ch.items)
        "revenue" -> com.enwei.moneymanager.ui.components.RevenueCompare(ch.items)
        "signals" -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ch.items.forEach { sym ->
                Box(Modifier.clickable { nav.navigate("stock/$sym") }) { com.enwei.moneymanager.ui.components.DisciplineChart(sym) }
            }
        }
        "themes" -> {
            val store by MoneyApp.news.flow.collectAsStateWithLifecycle()
            val st = store ?: return
            // 只列產業題材（排除大盤漲跌、總經），每個題材附相關個股
            val all = remember(System.identityHashCode(st)) { MoneyApp.news.investableThemes(st) }
            val picked = (if (ch.items.isEmpty()) all.take(8)
            else all.filter { (t, _) -> ch.items.any { it.contains(t.key) || t.key.contains(it) } }.ifEmpty { all.take(8) }).take(8)
            ThemeBars(picked, nav)
        }
        "holdings" -> {
            val holdings by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
            val rows = holdings.values.flatten().filter { it.shares > 0.0001 && it.price != null }.map { h ->
                val cost = h.avgCost * h.shares
                val pnl = h.marketValue - cost
                Triple("${h.symbol} ${h.name.orEmpty()}".trim(), if (cost > 0) pnl / cost * 100 else 0.0, "%+.1f%%".format(if (cost > 0) pnl / cost * 100 else 0.0))
            }.sortedByDescending { it.second }
            com.enwei.moneymanager.ui.components.SignedBars("持股損益（%）", rows)
        }
    }
}


/** 產業題材熱度：則數長條＋多空顏色＋相關個股（點個股看 K 線）。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ThemeBars(rows: List<Pair<com.enwei.moneymanager.data.TopicStat, List<String>>>, nav: NavController) {
    if (rows.isEmpty()) { Text("還沒有足夠的產業題材新聞", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val max = rows.maxOf { it.first.count }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("產業題材（長條＝新聞則數，顏色＝多空）", fontSize = AppText.caption, fontWeight = FontWeight.SemiBold)
        rows.forEach { (t, syms) ->
            val c = when { t.avgSentiment > 0.2 -> StockUp; t.avgSentiment < -0.2 -> StockDown; else -> MaterialTheme.colorScheme.onSurfaceVariant }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.key, fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${t.count} 則 · " + (if (t.avgSentiment > 0.2) "偏多" else if (t.avgSentiment < -0.2) "偏空" else "中性"), fontSize = AppText.label, color = c)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(t.count.toFloat() / max).height(6.dp).background(c, RoundedCornerShape(3.dp)))
                }
                if (syms.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    syms.forEach { sym ->
                        Text(sym, fontSize = AppText.label, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(4.dp))
                                .clickable { nav.navigate("stock/$sym") }.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
            }
        }
    }
}


/** 今日報告裡 AI 對這檔的動作。 */
@Composable
private fun AiHoldLine(a: com.enwei.moneymanager.data.AdvisorHolding) {
    // AI 動作在上、理由在下（不擠在同一行）
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
            Text("AI：${a.action}", fontSize = AppText.caption, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(com.enwei.moneymanager.data.stripCites(a.reason), fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 一檔持股：名稱、長短線、損益、價位圖、紀律動作與 AI 動作。 */
@Composable
private fun HoldingCard(h: com.enwei.moneymanager.data.HoldingPlan, ai: com.enwei.moneymanager.data.AdvisorHolding?, discipline: Boolean, nav: NavController) {
    val g = h.price?.let { (it / h.avgCost - 1) * 100 }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${h.symbol} ${h.name.orEmpty()}", fontSize = AppText.section, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.weight(1f).clickable { nav.navigate("stock/${h.symbol}") })
            g?.let { Text("%+.1f%%".format(it), fontSize = AppText.secondary, color = if (it >= 0) StockUp else StockDown) }
            // 長線／短線：點一下切換
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(start = 8.dp).clickable {
                    val next = TradeStyle.entries[(h.style.ordinal + 1) % TradeStyle.entries.size]
                    repo.settings.update { it.copy(stockStyle = it.stockStyle + (h.symbol to next.name)) }
                },
            ) { Text(h.style.label, fontSize = AppText.caption, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
        }
        val ad = h.advice
        PriceLadder(buildList {
            add(Triple("均價", h.avgCost, Color(0xFF607D8B)))
            h.price?.let { add(Triple("現價", it, if (it >= h.avgCost) StockUp else StockDown)) }
            h.peak?.let { add(Triple("最高", it, Color(0xFFFFA000))) }
            if (discipline && ad != null) {
                ad.stop.takeIf { it > 0 }?.let { add(Triple("停損", it, StockDown)) }
                ad.nextTakeProfit?.let { add(Triple("停利", it, Color(0xFFE53935))) }
            }
        })
        if (discipline && ad != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (ad.triggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp),
            ) { Text("紀律：${ad.action}", fontSize = AppText.caption, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            Text(ad.note, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        ai?.let { AiHoldLine(it) }
    }
    com.enwei.moneymanager.ui.components.LineDivider()
}


/** 新聞排程的步驟與目前進度。 */
@Composable
private fun ChatSteps(step: String) {
    val cur = when {
        step.contains("確認代號") -> 4
        step.startsWith("AI") -> 3
        step.contains("全文") -> 2
        step.contains("搜尋") -> 1
        else -> 0
    }
    StepRow(listOf("即時行情", "搜尋新聞", "讀取全文", "AI 回答", "確認代號"), cur)
}

@Composable
private fun StepRow(steps: List<String>, cur: Int) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, s ->
            val (mark, col) = when {
                i < cur -> "✓" to MaterialTheme.colorScheme.primary
                i == cur -> "●" to MaterialTheme.colorScheme.primary
                else -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text("$mark $s", fontSize = AppText.caption, color = col, fontWeight = if (i == cur) FontWeight.SemiBold else FontWeight.Normal)
            if (i < steps.lastIndex) Text("  →  ", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PipelineSteps(step: String, refreshOnly: Boolean) =
    com.enwei.moneymanager.ui.components.StageBar(step, withReport = !refreshOnly)

/** 篩選清單的一列：鍵、顯示名稱、則數、平均多空。 */
private data class Quad(val a: String, val b: String, val c: Int, val d: Double)


/** 推薦追蹤的一筆：某一天推薦的某檔股票與當時價格 */
private data class TrackRow(val day: Long, val pick: com.enwei.moneymanager.data.StockPick)

/**
 * 推薦追蹤（今日報告的「追蹤」分頁）：所有報告的推薦依「日期」整理（同一天多份報告的同一檔只算一次，取當天第一次的價格），
 * 用現在的股價算出每檔的漲跌；上方是整體統計。只是事後統計，不代表之後的表現。
 */
@Composable
private fun PickTrack(store: NewsStore, nav: NavController) {
    var span by com.enwei.moneymanager.ui.components.rememberTab("advisor.track", 1)
    val days = listOf(7, 30, 90)[span.coerceIn(0, 2)]
    val today = java.time.LocalDate.now().toEpochDay()
    val rows = remember(System.identityHashCode(store), days) {
        val seen = HashSet<String>()
        (listOfNotNull(store.report) + store.history).filter { it.day >= today - days }.sortedBy { it.createdAt }
            .flatMap { rep -> rep.picks.filter { it.price != null && it.price > 0 && seen.add("${rep.day}|${it.symbol}") }.map { TrackRow(rep.day, it) } }
    }
    // 現在的股價：先用 App 已存的報價，再查即時報價（最多等 15 秒），查不到的用證交所／櫃買中心的收盤價補上
    val saved by repo.quotes.collectAsStateWithLifecycle(emptyList())
    var fetched by remember { mutableStateOf<Map<String, Double>?>(null) }
    val symbols = remember(rows) { rows.map { it.pick.symbol }.distinct() }
    LaunchedEffect(symbols) {
        fetched = null
        fetched = if (symbols.isEmpty()) emptyMap() else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val live = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                runCatching { com.enwei.moneymanager.net.QuoteService.fetch(symbols.map { com.enwei.moneymanager.core.Market.TW to it }) }.getOrNull()
            }.orEmpty().associate { it.key.substringAfter(':') to it.price }
            val missing = symbols.filter { it !in live }
            val board = if (missing.isEmpty()) emptyMap() else runCatching { com.enwei.moneymanager.net.MarketScan.board() }.getOrDefault(emptyList())
                .filter { it.code in missing }.associate { it.code to it.close }
            board + live
        }
    }
    val prices = remember(saved, fetched) {
        saved.filter { it.key.startsWith("TW:") }.associate { it.key.removePrefix("TW:") to it.price } + fetched.orEmpty()
    }
    fun ret(x: TrackRow): Double? = prices[x.pick.symbol]?.takeIf { it > 0 }?.let { (it / x.pick.price!! - 1) * 100 }
    val rets = rows.mapNotNull { x -> ret(x)?.let { x to it } }
    val aiOn = repo.settings.flow.collectAsStateWithLifecycle().value.aiEnabled
    Section("推薦追蹤") {
        com.enwei.moneymanager.ui.components.SubTabs(listOf("近 7 天", "近 30 天", "近 90 天"), span.coerceIn(0, 2)) { span = it }
        when {
            rows.isEmpty() -> Text("這段期間沒有推薦紀錄。", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rets.isEmpty() -> Text(if (fetched == null) "讀取目前股價…" else "無法取得目前股價，請稍後再試。", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val avg = rets.map { it.second }.average()
                val best = rets.maxBy { it.second }; val worst = rets.minBy { it.second }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    @Composable fun tile(value: String, label: String, col: Color = MaterialTheme.colorScheme.onSurface) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                Text(value, fontSize = AppText.title, fontWeight = FontWeight.SemiBold, color = col, maxLines = 1)
                                Text(label, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                    tile("${rets.size}", "推薦檔次")
                    tile("%+.1f%%".format(avg), "平均漲跌", if (avg >= 0) StockUp else StockDown)
                    tile("${rets.count { it.second > 0 } * 100 / rets.size}%", "上漲比例")
                }
                Text("最好：${best.first.pick.symbol} ${best.first.pick.name} ${"%+.1f".format(best.second)}%　最差：${worst.first.pick.symbol} ${worst.first.pick.name} ${"%+.1f".format(worst.second)}%",
                    fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (aiOn) TextButton(onClick = {
                    val lines = rets.sortedByDescending { it.second }.take(40).joinToString("\n") { (x, v) ->
                        "${java.time.LocalDate.ofEpochDay(x.day)} ${x.pick.symbol} ${x.pick.name}（${x.pick.style}）推薦價 ${x.pick.price} → ${"%+.1f".format(v)}%"
                    }
                    com.enwei.moneymanager.ui.TabRequest.chatQuestion = "請檢討近 $days 天的推薦表現：哪些類型選得好、哪些不好，之後選股該怎麼調整？請給具體可執行的規則（這次的結論會用於之後的每日選股）。\n平均 ${"%+.1f".format(avg)}%，上漲比例 ${rets.count { it.second > 0 } * 100 / rets.size}%。\n$lines"
                    com.enwei.moneymanager.ui.TabRequest.advisorSub = 1
                }) { Text("請 AI 檢討這些推薦 ›", fontSize = AppText.caption) }
            }
        }
        // 依日期列出（新到舊）；每檔一列：左邊名稱與推薦價，右邊現價與漲跌
        rows.groupBy { it.day }.entries.sortedByDescending { it.key }.forEach { (day, list) ->
            val dayRets = list.mapNotNull { ret(it) }
            com.enwei.moneymanager.ui.components.LineDivider()
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val d = java.time.LocalDate.ofEpochDay(day)
                Text("${d.monthValue}/${d.dayOfMonth} 推薦 ${list.size} 檔", fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (dayRets.isNotEmpty()) dayRets.average().let { a ->
                    Text("平均 ${"%+.1f".format(a)}%", fontSize = AppText.caption, color = if (a >= 0) StockUp else StockDown)
                }
            }
            list.sortedByDescending { ret(it) ?: -999.0 }.forEach { x ->
                val v = ret(x); val now = prices[x.pick.symbol]
                Row(Modifier.fillMaxWidth().clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                    nav.navigate("stock/${x.pick.symbol}")
                }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${x.pick.symbol} ${x.pick.name}", fontSize = AppText.body, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text(listOfNotNull(x.pick.style.ifBlank { null }, "推薦時 ${"%.2f".format(x.pick.price)}").joinToString(" · "),
                            fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(now?.let { "%.2f".format(it) } ?: "—", fontSize = AppText.body)
                        Text(v?.let { "%+.1f%%".format(it) } ?: "", fontSize = AppText.caption, color = if ((v ?: 0.0) >= 0) StockUp else StockDown)
                    }
                }
            }
        }
        Text("以推薦當時的價格與現在的股價計算，同一天重複推薦的只算一次；事後統計，不代表之後的表現。", fontSize = AppText.label,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

/**
 * AI 偶爾把內部資料來源的標記寫進回覆（「[個股2344、36]」「[紀律」）：這些不是新聞編號，畫面上拿掉。
 * 純數字的新聞編號（[1]、[2, 3]）保留，照常可點。
 */
private val sourceTag = Regex("\\s*[\\[［【](?!\\s*\\d{1,3}(?:\\s*[,，、]\\s*\\d{1,3})*\\s*[\\]］】])[^\\[\\]［］【】\\n]{1,30}[\\]］】]")
private val openTag = Regex("\\s*[\\[［【][^\\[\\]［］【】\\n]{0,24}$", RegexOption.MULTILINE)
internal fun cleanSourceTags(t: String): String = t.replace(sourceTag, "").replace(openTag, "")
