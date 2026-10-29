package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
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
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("股票助理", { nav.popBackStack() }) }) { pad ->
        Box(Modifier.padding(pad)) { AdvisorContent(nav) }
    }
}

/**
 * 股票助理（股票頁的分頁）：今日報告（推薦卡片、重點新聞、持股動作）、對話、新聞資料集、資金與紀律計畫。
 */
@Composable
fun AdvisorContent(nav: NavController) {
    var tab by rememberSaveable { mutableStateOf(0) }
    // 直接訂閱資料集：背景蒐集、標註、產生報告時畫面即時更新
    val live by MoneyApp.news.flow.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { MoneyApp.news.load() }
    val store = live ?: NewsStore()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 8.dp) {
            listOf("今日", "問 AI", "新聞", "資金與紀律").forEachIndexed { i, t ->
                Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 15.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (tab) {
            0 -> TodayTab(nav, store) {}
            1 -> ChatTab(nav, store) {}
            2 -> NewsTab(store)
            else -> PlanTab(nav)
        }
    }
}

@Composable
private fun Disclaimer() = Text(
    "AI 分析僅供參考，不是投資建議。",
    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** 今日報告：大盤、題材、推薦卡片、重點新聞、持股動作、資金建議、檢查清單；可切換看過去的報告。 */
@Composable
private fun TodayTab(nav: NavController, store: NewsStore, reload: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val job by com.enwei.moneymanager.work.ReportJob.state.collectAsStateWithLifecycle()
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r?.let {
                        val today = java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
                        (if (old) "歷史報告 · " else if (today) "今日報告 · " else "報告 · ") + fmt.format(java.util.Date(it.createdAt))
                    } ?: "還沒有報告",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                if (GroqAi.enabled && !old) OutlinedButton(enabled = job?.running != true, onClick = {
                    com.enwei.moneymanager.work.ReportJob.start(ctx)
                }) { Text(if (job?.running == true) "產生中…" else if (r == null) "產生報告" else "重新產生") }
            }
            Text(
                r?.let { "讀了 ${it.newsCount} 則新聞" } ?: "資料集 $recentCount 則新聞",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val count = store.history.size + if (store.report != null) 1 else 0
                if (count > 0) TextButton(onClick = { showHistory = true }) { Text("報告歷史（$count）") }
                if (old) TextButton(onClick = { viewing = null }) { Text("回到最新報告") }
                // 分享報告（純文字，可以傳到 LINE 或存進筆記）
                if (r != null) TextButton(onClick = {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(android.content.Intent.EXTRA_TEXT, reportText(r))
                    ctx.startActivity(android.content.Intent.createChooser(send, null))
                }) { Text("分享") }
            }
        }
        // 背景執行中：步驟、預估剩餘時間與進度（離開畫面也會繼續，完成時通知）
        JobProgress(job)
        if (!GroqAi.enabled) Text("到「設定 → AI 智慧辨識」填入金鑰後才能產生報告。", fontSize = 13.sp)
        if (r != null) ReportBody(r, newsById, nav)
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

@Composable
private fun ReportBody(r: DailyReport, newsById: Map<String, com.enwei.moneymanager.data.NewsItem>, nav: NavController) {
    Section("大盤") {
        StageChip(r.stage.ifBlank { "趨勢" }, com.enwei.moneymanager.data.stripCites(r.trend))
        if (r.themes.isNotEmpty()) Text(r.themes.joinToString("　") { "#$it" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        if (r.summary.isNotBlank()) CitedText(r.summary, r.cites, newsById)
    }
    if (r.picks.isNotEmpty()) {
        Text("今天可以考慮", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        r.picks.forEach { PickCard(it, r.cites, newsById) { nav.navigate("stock/${it.symbol}") } }
    }
    if (r.keyNews.isNotEmpty()) Section("一定要看的新聞（${r.keyNews.size}）", collapsible = true) {
        r.keyNews.forEach { k ->
            newsById[k.id]?.let { n ->
                NewsLine(n, num = r.cites.indexOf(k.id).takeIf { it >= 0 }?.plus(1))
                if (k.why.isNotBlank()) Text("→ ${k.why}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (r.holdings.isNotEmpty()) Section("持股動作（${r.holdings.size}）", collapsible = true) {
        r.holdings.forEach { h ->
            Row(Modifier.fillMaxWidth().clickable { nav.navigate("stock/${h.symbol}") }.padding(vertical = 2.dp)) {
                Text(h.symbol, fontSize = 14.sp, modifier = Modifier.widthIn(min = 56.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.action + (h.stop?.let { "｜停損 ${"%.2f".format(it)}" } ?: ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    CitedText(h.reason, r.cites, newsById, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (r.allocation.isNotBlank()) Section("資金分配", collapsible = true) { CitedText(r.allocation, r.cites, newsById) }
    if (r.checklist.isNotEmpty() || r.warnings.isNotEmpty()) Section("下單前檢查", collapsible = true, initiallyOpen = false) {
        r.checklist.forEach { Text("☐ $it", fontSize = 13.sp) }
        r.warnings.forEach { Text("! $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
    }
}

/** 報告歷史：點一份查看、逐份刪除、刪除全部舊報告，以及自動刪除的保留天數。 */
@Composable
private fun HistoryDialog(store: NewsStore, onOpen: (Long) -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val fmt = java.text.SimpleDateFormat("yyyy/M/d HH:mm", java.util.Locale.TAIWAN)
    val all = listOfNotNull(store.report) + store.history
    var confirmAll by remember { mutableStateOf(false) }
    fun delete(c: Long) = scope.launch {
        MoneyApp.news.update { st ->
            if (st.report?.createdAt == c) st.copy(report = st.history.firstOrNull(), history = st.history.drop(1))
            else st.copy(history = st.history.filter { it.createdAt != c })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("報告歷史") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("自動刪除", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(7 to "7 天後", 30 to "30 天後", 90 to "90 天後", 0 to "不刪除").forEach { (d, l) ->
                        FilterChip(store.historyKeepDays == d, {
                            scope.launch { MoneyApp.news.update { it.copy(historyKeepDays = d).prunedHistory() } }
                        }, label = { Text(l, fontSize = 12.sp) })
                    }
                }
                androidx.compose.material3.HorizontalDivider()
                if (all.isEmpty()) Text("還沒有報告", fontSize = 13.sp)
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    all.forEachIndexed { i, rep ->
                        Row(Modifier.fillMaxWidth().clickable { onOpen(rep.createdAt) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(fmt.format(java.util.Date(rep.createdAt)) + (if (i == 0 && rep === store.report) "（最新）" else ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    listOf(rep.stage, rep.picks.joinToString("、") { it.name.ifBlank { it.symbol } }).filter { it.isNotBlank() }.joinToString(" · ")
                                        .ifBlank { rep.summary.take(30) },
                                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
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
            if (store.history.isNotEmpty()) TextButton(onClick = { confirmAll = true }) { Text("刪除舊報告", color = MaterialTheme.colorScheme.error) }
        },
    )
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("刪除全部舊報告？") },
        text = { Text("會刪除 ${store.history.size} 份過去的報告，保留最新一份。") },
        confirmButton = { TextButton(onClick = { scope.launch { MoneyApp.news.update { it.copy(history = emptyList()) } }; confirmAll = false }) { Text("刪除") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("取消") } },
    )
}

/** 推薦股票卡片：代號名稱、長短線、強弱、業務、理由、進出場；點進去看 K 線與資訊。 */
@Composable
fun PickCard(
    p: StockPick, cites: List<String> = emptyList(), newsById: Map<String, com.enwei.moneymanager.data.NewsItem> = emptyMap(), onClick: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.symbol} ${p.name}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (p.style.isNotBlank()) Tag(p.style, MaterialTheme.colorScheme.secondaryContainer)
                if (p.strength.isNotBlank()) Tag(
                    p.strength,
                    when {
                        p.strength.contains("強") -> StockUp.copy(alpha = 0.18f)
                        p.strength.contains("弱") -> StockDown.copy(alpha = 0.18f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                )
            }
            p.price?.let { Text("現價 ${"%.2f".format(it)}", fontSize = 13.sp) }
                ?: Text("代號未確認，請自行查證", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            if (p.business.isNotBlank()) Text(p.business, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.reason.isNotBlank()) CitedText(p.reason, cites, newsById)
            if (p.entry.isNotBlank()) Text("進場：${p.entry}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.stop.isNotBlank()) Text("停損：${p.stop}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.amount?.let { Text("建議投入約 ${Money.withSymbol(it * 100, "TWD")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("看 K 線與資訊 ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                val settings by repo.settings.flow.collectAsStateWithLifecycle()
                val inList = p.symbol in settings.watchlist
                TextButton(onClick = {
                    repo.settings.update { it.copy(watchlist = if (inList) it.watchlist - p.symbol else (it.watchlist + p.symbol).distinct()) }
                }) { Text(if (inList) "★ 觀察中" else "☆ 加入觀察", fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color) {
    Surface(color = bg, shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(start = 4.dp)) {
        Text(text, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** 問 AI：對話泡泡，AI 回覆附股票卡片與新聞。 */
@Composable
private fun ChatTab(nav: NavController, store: NewsStore, reload: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    // 對話在背景執行：離開畫面也會繼續，回來時看得到回覆；不在畫面上時完成會通知
    val job by com.enwei.moneymanager.work.ChatJob.state.collectAsStateWithLifecycle()
    val busy = job?.running == true
    val err = job?.error
    // 回到對話時停在上次看的位置，不會每次都被捲到最底
    val list = rememberLazyListState(com.enwei.moneymanager.work.ChatJob.scrollIndex, com.enwei.moneymanager.work.ChatJob.scrollOffset)
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.enwei.moneymanager.work.ChatJob.visible = true
        onDispose {
            com.enwei.moneymanager.work.ChatJob.visible = false
            com.enwei.moneymanager.work.ChatJob.scrollIndex = list.firstVisibleItemIndex
            com.enwei.moneymanager.work.ChatJob.scrollOffset = list.firstVisibleItemScrollOffset
        }
    }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(busy) { while (busy) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    // 上萬則新聞的整理在背景執行緒做，畫面不卡
    val newsById by androidx.compose.runtime.produceState(emptyMap<String, com.enwei.moneymanager.data.NewsItem>(), System.identityHashCode(store)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { store.items.associateBy { it.id } }
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
        val n = store.chat.size
        if (last == null) return@LaunchedEffect
        val seen = com.enwei.moneymanager.work.ChatJob.seenLast
        com.enwei.moneymanager.work.ChatJob.seenLast = last
        if (seen < 0) list.scrollToItem(n) else if (last != seen) list.animateScrollToItem(n)
    }
    val canDown by remember { androidx.compose.runtime.derivedStateOf { list.canScrollForward } }
    Column(Modifier.fillMaxSize().imePadding()) {
        // 固定在上方的小工具列：對話則數與清除
        if (store.chat.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("長按訊息可複製或刪除", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(enabled = !busy, onClick = { confirmClear = true }) { Text("清除對話") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (store.chat.isEmpty()) item {
                    Text("問今天該買什麼、持股怎麼處理、資金怎麼分配，或點下方的常見問題。",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                } else item { Spacer(Modifier.height(4.dp)) }
                items(store.chat, key = { "c${it.time}${it.fromUser}" }) { m ->
                    Bubble(m, newsById, nav) { scope.launch { MoneyApp.news.update { st -> st.copy(chat = st.chat.filterNot { it.time == m.time && it.fromUser == m.fromUser }) } } }
                }
                if (busy) item {
                    Text("AI 思考中… ${com.enwei.moneymanager.work.ReportJob.formatDuration(now - (job?.startedAt ?: now))}（離開也會繼續）",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
                err?.let { e ->
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(e, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
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
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("今天該買什麼？", "我的持股怎麼處理？", "現在適合加碼嗎？", "最近有什麼重大新聞？", "短線資金還能用多少？").forEach { q ->
                FilterChip(false, { send(q) }, label = { Text(q, fontSize = 12.sp) }, enabled = !busy && GroqAi.enabled)
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("問股票助理…") }, modifier = Modifier.weight(1f), maxLines = 3)
            // AI 回覆中按鈕變成「停止」
            if (busy) OutlinedButton(onClick = { com.enwei.moneymanager.work.ChatJob.stop(ctx) }, modifier = Modifier.padding(start = 6.dp)) { Text("停止") }
            else Button(enabled = input.isNotBlank() && GroqAi.enabled, onClick = { send(input) }, modifier = Modifier.padding(start = 6.dp)) { Text("送出") }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("清除對話？") },
        text = { Text("刪除全部 ${store.chat.size} 則對話。") },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                com.enwei.moneymanager.work.ChatJob.seenLast = 0
                com.enwei.moneymanager.work.ChatJob.state.value = null
                scope.launch { MoneyApp.news.update { it.copy(chat = emptyList()) }; list.scrollToItem(0) }
            }) { Text("清除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } },
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: ChatMsg, newsById: Map<String, com.enwei.moneymanager.data.NewsItem>, nav: NavController, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
    val text = com.enwei.moneymanager.data.stripCites(m.text)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Box {
            Surface(
                color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = { menu = true }),
            ) { CitedText(m.text, m.newsIds, newsById, modifier = Modifier.padding(10.dp)) }
            // 長按：複製、刪除這則
            androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("複製") }, onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(text)); menu = false })
                androidx.compose.material3.DropdownMenuItem(text = { Text("刪除", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
        m.cards.forEach { c -> Box(Modifier.padding(top = 6.dp)) { PickCard(c, m.newsIds, newsById) { nav.navigate("stock/${c.symbol}") } } }
        // 引用的新聞：編號和回覆裡的 [1][2] 對應
        val ns = m.newsIds.mapIndexedNotNull { i, id -> newsById[id]?.let { i + 1 to it } }
        if (ns.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) { ns.forEach { (k, n) -> NewsLine(n, num = k) } }
    }
}

/** 背景工作（產生報告或更新資料集）的步驟、預估剩餘時間與進度。 */
@Composable
private fun JobProgress(job: com.enwei.moneymanager.work.ReportJob.State?) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(job?.running) { while (job?.running == true) { now = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val j = job ?: return
    if (j.running) {
        val total = (j.etaAt - j.startedAt).coerceAtLeast(1)
        val elapsed = now - j.startedAt
        Text("${j.step}　約剩 ${com.enwei.moneymanager.work.ReportJob.formatDuration(j.etaAt - now)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        LinearProgressIndicator(progress = { (elapsed.toFloat() / total).coerceIn(0.02f, 0.97f) }, modifier = Modifier.fillMaxWidth())
        Text("已執行 ${com.enwei.moneymanager.work.ReportJob.formatDuration(elapsed)}，可以離開這個畫面，完成時會通知你。",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else j.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
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
@Composable
private fun NewsTab(store: NewsStore) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val job by com.enwei.moneymanager.work.ReportJob.state.collectAsStateWithLifecycle()
    val holdings by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    val mine = remember(holdings) { holdings.values.flatten().map { it.symbol }.toSet() }
    val ds = MoneyApp.news
    // 統計與篩選都在背景執行緒算（資料集有上萬則）
    val stats by androidx.compose.runtime.produceState(NewsStats(), System.identityHashCode(store)) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            NewsStats(
                store.items.count { it.tagged }, ds.themes(store), ds.important(store, 3, 30), ds.symbols(store, 3).take(20),
                store.items.filter { it.source.isNotBlank() }.groupingBy { it.source }.eachCount().entries.sortedByDescending { it.value }.take(15).map { it.key to it.value },
                ds.coverage(store),
            )
        }
    }
    val themes = stats.themes
    var showHow by remember { mutableStateOf(false) }
    // 篩選條件
    var query by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(0) }       // 0 全部、1 今天、3、7 天
    var sent by remember { mutableStateOf(0) }       // 0 全部、1 利多、2 利空、3 中性
    var onlyImportant by remember { mutableStateOf(false) }
    var onlyMine by remember { mutableStateOf(false) }
    var sort by remember { mutableStateOf(0) }       // 0 最新、1 最重要、2 多空最強
    var theme by remember { mutableStateOf<String?>(null) }
    var symbol by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var shown by remember { mutableStateOf(100) }
    val anyFilter = query.isNotBlank() || days != 0 || sent != 0 || onlyImportant || onlyMine || theme != null || symbol != null || source != null
    val filteredOrNull by androidx.compose.runtime.produceState<List<com.enwei.moneymanager.data.NewsItem>?>(null,
        System.identityHashCode(store), query, days, sent, onlyImportant, onlyMine, sort, theme, symbol, source, mine) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val zone = java.time.ZoneId.of("Asia/Taipei")
            val since = when (days) {
                0 -> 0L
                1 -> java.time.LocalDate.now(zone).atStartOfDay(zone).toEpochSecond()
                else -> System.currentTimeMillis() / 1000 - days * 86_400L
            }
            val q = query.trim()
            val list = store.items.filter { n ->
                n.epochSec >= since &&
                    (q.isEmpty() || n.title.contains(q, true) || q in n.symbols || n.source.contains(q, true)) &&
                    (theme == null || n.theme == theme) && (symbol == null || symbol in n.symbols) && (source == null || n.source == source) &&
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
    val digestsAll = store.digests.values.groupBy { it.theme }.mapValues { (_, l) -> l.maxBy { it.day } }
    // 沒篩選時列表前面有幾個整理區塊（「跳到新聞列表」用）
    val overviewCount = if (anyFilter) 0 else 1 + (if (themes.isNotEmpty()) 1 else 0) + (if (digestsAll.isNotEmpty()) 1 else 0) + (if (stats.important.isNotEmpty()) 1 else 0)
    val listState = rememberLazyListState()
    var filtersOpen by remember { mutableStateOf(false) }
    val activeCount = listOf(days != 0, sent != 0, onlyImportant, onlyMine, sort != 0, theme != null, symbol != null, source != null).count { it }
    Column(Modifier.fillMaxSize()) {
        // 搜尋與篩選固定在最上方，不用先滑過整理好的題材與重點
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it; shown = 100 }, placeholder = { Text("搜尋標題、代號或來源") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                FilterChip(filtersOpen || activeCount > 0, { filtersOpen = !filtersOpen },
                    label = { Text(if (activeCount > 0) "篩選 $activeCount" else "篩選") })
            }
            if (filtersOpen) Column(Modifier.heightIn(max = 330.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ChipRow("時間") {
                listOf(0 to "全部", 1 to "今天", 3 to "3 天", 7 to "7 天").forEach { (d, l) -> FilterChip(days == d, { days = d; shown = 100 }, label = { Text(l) }) }
            }
            ChipRow("多空") {
                listOf("全部", "利多", "利空", "中性").forEachIndexed { i, l -> FilterChip(sent == i, { sent = i; shown = 100 }, label = { Text(l) }) }
                FilterChip(onlyImportant, { onlyImportant = !onlyImportant; shown = 100 }, label = { Text("只看重要") })
                if (mine.isNotEmpty()) FilterChip(onlyMine, { onlyMine = !onlyMine; shown = 100 }, label = { Text("我的持股") })
            }
            ChipRow("排序") {
                listOf("最新", "最重要", "多空最強").forEachIndexed { i, l -> FilterChip(sort == i, { sort = i; shown = 100 }, label = { Text(l) }) }
            }
            if (themes.isNotEmpty()) ChipRow("題材") {
                themes.forEach { t -> FilterChip(theme == t.key, { theme = if (theme == t.key) null else t.key; shown = 100 }, label = { Text("${t.key} ${t.count}") }) }
            }
            if (stats.symbols.isNotEmpty()) ChipRow("個股") {
                stats.symbols.forEach { t ->
                    FilterChip(symbol == t.key, { symbol = if (symbol == t.key) null else t.key; shown = 100 }, label = { Text("${t.key} ${t.count}") })
                }
            }
            if (stats.sources.isNotEmpty()) ChipRow("來源") {
                stats.sources.forEach { (src, n) -> FilterChip(source == src, { source = if (source == src) null else src; shown = 100 }, label = { Text("$src $n") }) }
            }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text((if (filteredOrNull == null) "整理中…" else "符合 ${filtered.size} 則") + (theme?.let { "　題材：$it" } ?: "") + (symbol?.let { "　個股：$it" } ?: "") + (source?.let { "　來源：$it" } ?: ""),
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (anyFilter || sort != 0) TextButton(onClick = {
                    query = ""; days = 0; sent = 0; onlyImportant = false; onlyMine = false; sort = 0; theme = null; symbol = null; source = null; shown = 100
                }) { Text("清除篩選") }
                else TextButton(onClick = { scope.launch { listState.animateScrollToItem(overviewCount) } }) { Text("跳到新聞列表") }
            }
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!anyFilter) item {
                Section("資料集（${store.items.size} 則）", action = (if (showHow) "收起" else "整理方式") to { showHow = !showHow }, collapsible = true, initiallyOpen = false) {
                    Text("共 ${store.items.size} 則（近 ${com.enwei.moneymanager.data.NewsDataset.KEEP_DAYS} 天），已標註 ${stats.tagged} 則", fontSize = 14.sp)
                    stats.coverage?.let { c ->
                        val pct = if (c.recent > 0) c.digested * 100 / c.recent else 0
                        Text(
                            "近 3 天 ${c.recent} 則，已整理進重點庫 ${c.digested} 則（$pct%）" +
                                (if (c.pending > 0) "；待標註 ${c.pending} 則" else "") +
                                (if (c.fallback > 0) "；AI 連續漏標 3 次歸入「其他」${c.fallback} 則" else ""),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "上次蒐集：" + (if (store.lastCollect > 0) java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(store.lastCollect)) else "還沒有") +
                            "，背景每 3 小時更新一次。",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showHow) HowItWorks()
                    OutlinedButton(enabled = job?.running != true, onClick = { com.enwei.moneymanager.work.ReportJob.start(ctx, refreshOnly = true) }) { Text("立即更新") }
                    JobProgress(job)
                }
            }
            if (themes.isNotEmpty() && !anyFilter) item {
                Section("題材熱度（點選篩選）", collapsible = true) {
                    val max = themes.maxOf { it.count }.coerceAtLeast(1)
                    themes.take(15).forEach { t ->
                        Column(Modifier.clickable { theme = if (theme == t.key) null else t.key; shown = 100 }) {
                            Row {
                                Text((if (theme == t.key) "● " else "") + t.key, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text("${t.count} 則 · " + (if (t.avgSentiment > 0.2) "偏多" else if (t.avgSentiment < -0.2) "偏空" else "中性"), fontSize = 13.sp,
                                    color = if (t.avgSentiment > 0.2) StockUp else if (t.avgSentiment < -0.2) StockDown else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(t.count.toFloat() / max).height(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }
            // 新聞重點庫：每個題材最新一天整理好的重點（同一事件只列一次）
            val digests = store.digests.values.groupBy { it.theme }.mapValues { (_, l) -> l.maxBy { it.day } }
                .values.sortedByDescending { it.count }.filter { theme == null || it.theme == theme }
            if (digests.isNotEmpty() && !anyFilter) item {
                Section("新聞重點（${digests.size} 個題材）", collapsible = true) {
                    // 每個題材像資料夾：預設只看第一個重點，點開看全部
                    digests.take(if (theme == null) 15 else 1).forEach { d -> DigestFolder(d) }
                }
            }
            if (stats.important.isNotEmpty() && !anyFilter) item {
                Section("重點新聞（${stats.important.size}）", collapsible = true, initiallyOpen = false) { stats.important.forEach { NewsLine(it) } }
            }
            if (!anyFilter) item { Text("全部新聞", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            if (filteredOrNull == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (filtered.isEmpty()) item { Text("沒有符合條件的新聞", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(filtered.take(shown), key = { "n" + it.id }) { n ->
                Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) { Box(Modifier.padding(horizontal = 10.dp)) { NewsLine(n) } }
            }
            if (filtered.size > shown) item {
                TextButton(onClick = { shown += 100 }, modifier = Modifier.fillMaxWidth()) { Text("顯示更多（還有 ${filtered.size - shown} 則）") }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** 一個題材的新聞重點：標題列點一下展開／收起。 */
@Composable
private fun DigestFolder(d: com.enwei.moneymanager.data.ThemeDigest) {
    var open by rememberSaveable(d.theme) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${d.theme}　${d.day.substring(5)} · ${d.count} 則", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(if (open) "▴" else "▾ ${d.points.size} 點", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        (if (open) d.points else d.points.take(1)).forEach { p ->
            Text(
                (if (p.sentiment > 0) "▲ " else if (p.sentiment < 0) "▼ " else "• ") + p.text +
                    (if (p.symbols.isNotEmpty()) "（${p.symbols.joinToString(",")}）" else "") + if (p.count > 1) " ×${p.count}" else "",
                fontSize = 13.sp, maxLines = if (open) Int.MAX_VALUE else 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (p.sentiment > 0) StockUp else if (p.sentiment < 0) StockDown else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** 一列可左右捲動的篩選選項，前面有小標題。 */
@Composable
private fun ChipRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(min = 36.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
    }
}

/** 新聞資料集怎麼蒐集、標註與整理（給使用者看的說明）。 */
@Composable
private fun HowItWorks() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            "① 蒐集：每 3 小時用 ${com.enwei.moneymanager.data.NewsDataset.BASE_QUERIES.size} 個主題關鍵字加上你的持股代號查新聞，每個查詢最多約 100 則；標題去掉空白後相同的視為同一則，只留一份。保留 ${com.enwei.moneymanager.data.NewsDataset.KEEP_DAYS} 天、最多 ${com.enwei.moneymanager.data.NewsDataset.MAX_ITEMS} 則。",
            "② 標註：一批 150 則（Gemini）或 40 則（Groq）編號後送給 AI，回傳每則的相關台股代號、題材（固定清單）、多空與重要性。App 用編號逐則核對，AI 漏掉的會留著、下一批優先重送；連續 3 次都沒結果才歸入「其他」，所以每一則都會被算進統計。",
            "③ 重點庫：近 3 天依「日期 × 題材」分組，整組標題交給 AI 合併成不重複的重點，同一件事只寫一次並記下有幾則在講。一組太多時分段送（Gemini 每段 250 則、Groq 60 則），各段重點再合併，不會因為輸入上限漏讀；只有 1～2 則的小組直接用標題當重點。新聞增加 20% 以上就重新整理。",
            "④ 報告與對話：讀題材熱度、個股統計、整個重點庫與重要新聞，再加上大盤、持股與你的紀律，不是只看幾十則標題。",
        ).forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** 資金與紀律：大盤階段、資金分配、持股動態停損、交易紀律、背景報告開關。 */
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = snap
        if (s == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (s != null) Section("大盤") {
            s.index?.let { b ->
                val c = b.close
                Text("加權指數 ${"%,.0f".format(c.last())}" + (Technicals.change(c, 5)?.let { "　5 日 ${"%+.1f".format(it)}%" } ?: ""), fontSize = 16.sp)
            }
            StageChip(s.indexStage.label, s.indexStage.hint)
            if (s.sox != null) Text("費城半導體：${s.soxStage.label}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (s != null) Section("資金控管", action = "調整" to { editPlan = true }) {
            val a = s.alloc
            Text("可動用現金 ${m(a.cash)}（已扣信用卡欠款）· 保留生活費 ${m(a.reserve)}", fontSize = 13.sp)
            Text("可投資 ${m(a.investable)}：長線 ${"%.0f".format(settings.investPlan.longRatio * 100)}% / 短線 ${"%.0f".format(100 - settings.investPlan.longRatio * 100)}%", fontSize = 13.sp)
            Bucket("長線", a.longValue, a.longTarget, a.longRoom, ::m)
            Bucket("短線", a.shortValue, a.shortTarget, a.shortRoom, ::m)
            if (a.reserveShort > 0) Text("⚠ 現金比生活費少 ${m(a.reserveShort)}，先不要加碼", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            if (a.shortOver > 0) Text("⚠ 短線超出計畫 ${m(a.shortOver)}，新的短線單先暫停", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        if (s != null && s.holdings.isNotEmpty() && settings.investPlan.guardOn) Section("保本＋移動停利") {
            s.holdings.forEach { h -> GuardRow(h, nav) { editGuard = h } }
        }
        if (s != null && s.holdings.isNotEmpty() && !settings.investPlan.guardOn) Section("持股紀律（動態停損、分批停利）") {
            s.holdings.forEach { h ->
                Column(Modifier.clickable { nav.navigate("stock/${h.symbol}") }.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${h.symbol} ${h.name.orEmpty()}", fontSize = 15.sp, modifier = Modifier.weight(1f))
                        TradeStyle.entries.forEach { st ->
                            FilterChip(h.style == st, {
                                repo.settings.update { it.copy(stockStyle = it.stockStyle + (h.symbol to st.name)) }
                            }, label = { Text(st.label, fontSize = 12.sp) }, modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                    val g = h.gainPct
                    Text(
                        "均價 ${"%.2f".format(h.avgCost)} → 現價 ${h.price?.let { "%.2f".format(it) } ?: "?"}" +
                            (g?.let { "（${"%+.1f".format(it)}%）" } ?: "") + (h.peak?.let { " · 買進後最高 ${"%.2f".format(it)}" } ?: ""),
                        fontSize = 13.sp, color = if ((g ?: 0.0) >= 0) StockUp else StockDown,
                    )
                    h.advice?.let { ad ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (ad.triggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) { Text(ad.action, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
                            Text(
                                "  " + listOfNotNull(
                                    ad.stop.takeIf { it > 0 }?.let { "停損 ${"%.2f".format(it)}" },
                                    ad.nextTakeProfit?.let { "停利 ${"%.2f".format(it)}" },
                                ).joinToString(" · "),
                                fontSize = 13.sp,
                            )
                        }
                        Text(ad.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Section("我的交易紀律（${settings.investPlan.rules.size} 條）", action = "編輯" to { editRules = true }, collapsible = true, initiallyOpen = false) {
            settings.investPlan.rules.forEachIndexed { i, r -> Text("${i + 1}. $r", fontSize = 14.sp) }
        }
        Section("背景分析") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每天讀新聞並產生報告", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(settings.advisorDaily, { v -> repo.settings.update { it.copy(advisorDaily = v) } }, enabled = GroqAi.enabled)
            }
            Text("每 3 小時更新新聞，早上 7 點後產生報告並通知", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
    if (editPlan) PlanDialog(settings.investPlan) { editPlan = false }
    if (editRules) RulesDialog(settings.investPlan.rules) { editRules = false }
    editGuard?.let { h -> GuardDialog(h, settings.investPlan.guards[h.symbol] ?: com.enwei.moneymanager.data.GuardState()) { editGuard = null } }
}

/** 一檔持股的保本＋移動停利：保本與市值、多賺股數、停利價、60 分 K 均線、日均線趨勢與建議動作。 */
@Composable
private fun GuardRow(h: com.enwei.moneymanager.data.HoldingPlan, nav: NavController, onEdit: () -> Unit) {
    val gi = h.guardInput
    val g = h.guard
    fun f(v: Double?) = v?.let { "%.2f".format(it) } ?: "?"
    // 預設只看重點（建議動作、多賺股數與停利價），點一下展開完整數字
    var open by rememberSaveable(h.symbol) { mutableStateOf(g?.triggered == true) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${h.symbol} ${h.name.orEmpty()}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (g != null) Surface(
                color = if (g.triggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    (if (g.triggered) g.action.label.substringBefore('：') + "：賣 ${g.sellShares.toLong()} 股" else g.action.label.substringBefore('，')),
                    fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (gi == null || g == null) { Text("讀不到現價", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return@Column }
        Text(
            if (g.excessShares >= 1) "多賺 ${g.excessShares.toLong()} 股 · 停利價 ${f(g.takeProfit)}"
            else if (g.excessValue > 0) "多賺 ${"%,.0f".format(g.excessValue)} 元，還不到一股" else "比成本少 ${"%,.0f".format(-g.excessValue)} 元",
            fontSize = 13.sp, color = if (g.excessValue >= 0) StockUp else StockDown,
        )
        if (open) {
            Text(
                "${"%.0f".format(gi.shares)} 股 · 現價 ${f(gi.price)} · 市值 ${"%,.0f".format(gi.shares * gi.price)} · 保本 ${"%,.0f".format(gi.principal)}" +
                    (if (h.autoPrincipal) "（自動）" else "") + (g.peak?.let { " · 最高 ${f(it)}" } ?: ""),
                fontSize = 13.sp,
            )
            Text(
                "60 分 K：20MA ${f(gi.hourMa20)}" + (gi.hourMa20?.let { if (gi.price < it) "（跌破）" else "（之上）" } ?: "") +
                    "、50MA ${f(gi.hourMa50)}" + (gi.hourMa50?.let { if (gi.price < it) "（跌破）" else "（之上）" } ?: "") +
                    (gi.reduceAt?.let { " · 減碼價 ${f(it)}" } ?: ""),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            h.trend?.let { Text("日 K：${it.summary}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(g.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = onEdit) { Text("設定保本與停利", fontSize = 12.sp) }
                TextButton(onClick = { nav.navigate("stock/${h.symbol}") }) { Text("看 K 線", fontSize = 12.sp) }
            }
        }
    }
    androidx.compose.material3.HorizontalDivider()
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
                    OutlinedButton(onClick = { principal = "%.0f".format(gi.shares * gi.price) }) { Text("改成目前市值", fontSize = 12.sp) }
                    OutlinedButton(onClick = { principal = "" }) { Text("自動", fontSize = 12.sp) }
                }
                NumberField(tp, { tp = it }, "手動停利價", suffix = "元")
                NumberField(reduce, { reduce = it }, "減碼價（跌破賣一半）", suffix = "元")
                if (gi != null) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(3, 5, 8).forEach { pct -> OutlinedButton(onClick = { reduce = "%.2f".format(gi.price * (1 - pct / 100.0)) }) { Text("−$pct%", fontSize = 12.sp) } }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("重設移動停利的最高價", fontSize = 13.sp, modifier = Modifier.weight(1f))
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
    var open by rememberSaveable(title.substringBefore('（')) { mutableStateOf(initiallyOpen) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                (if (collapsible) Modifier.clickable { open = !open } else Modifier).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.let { (l, f) -> TextButton(onClick = f) { Text(l) } }
                if (collapsible) Icon(
                    if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    if (open) "收合" else "展開", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp, color: Color = Color.Unspecified, modifier: Modifier = Modifier,
) {
    val link = MaterialTheme.colorScheme.primary
    val annotated = remember(text, ids, System.identityHashCode(newsById)) {
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
            Text(label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        if (hint.isNotBlank()) Text(hint, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Bucket(label: String, used: Long, target: Long, room: Long, m: (Long) -> String) {
    Column {
        Row {
            Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${m(used)} / ${m(target)}", fontSize = 13.sp)
        }
        val f = if (target > 0) (used.toFloat() / target).coerceIn(0f, 1f) else 0f
        Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(f).height(6.dp).background(if (used > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
        }
        Text("還可投入 ${m(room)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var guardOn by remember { mutableStateOf(p.guardOn) }
    var gTrail by remember { mutableStateOf(p.guardTrailPct.toString()) }
    var base by remember { mutableStateOf(p.baseShares.toLong().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("資金與紀律參數") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(reserve, { reserve = it }, "保留生活費", suffix = "元")
                NumberField(longPct, { longPct = it }, "長線占可投資資金", suffix = "%")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("保本＋移動停利紀律", fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(guardOn, { guardOn = it })
                }
                if (guardOn) {
                    NumberField(gTrail, { gTrail = it }, "移動停利：從最高價回落", suffix = "%")
                    NumberField(base, { base = it }, "兩條 60 分 K 均線都跌破時留下的基本股", suffix = "股")
                } else {
                    NumberField(stop, { stop = it }, "短線停損", suffix = "%")
                    NumberField(trail, { trail = it }, "短線移動停損（從高點回落）", suffix = "%")
                    NumberField(longTrail, { longTrail = it }, "長線移動停損（從高點回落）", suffix = "%")
                    NumberField(tp, { tp = it }, "分批停利門檻（長線為兩倍）", suffix = "%")
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
                            guardTrailPct = gTrail.toDoubleOrNull()?.coerceIn(0.5, 30.0) ?: p.guardTrailPct,
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
