package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material.icons.filled.Close
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
    LaunchedEffect(com.enwei.moneymanager.ui.TabRequest.advisorSub) {
        com.enwei.moneymanager.ui.TabRequest.advisorSub?.let { tab = it; com.enwei.moneymanager.ui.TabRequest.advisorSub = null }
    }
    // 直接訂閱資料集：背景蒐集、標註、產生報告時畫面即時更新
    val live by MoneyApp.news.flow.collectAsStateWithLifecycle()
    // 讀檔失敗不會讓畫面閃退：資料集改用空的並顯示提示
    LaunchedEffect(Unit) { runCatching { MoneyApp.news.load() } }
    val loadError by MoneyApp.news.loadError.collectAsStateWithLifecycle()
    val store = live ?: NewsStore()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val fitGroup1 = com.enwei.moneymanager.ui.components.rememberFitGroup()
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            listOf("今日", "問 AI", "新聞", "資金紀律").forEachIndexed { i, t ->
                Tab(tab == i, { tab = i }, text = { com.enwei.moneymanager.ui.components.FitText(t, fontSize = 14.sp, group = fitGroup1) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        loadError?.let { e ->
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(e, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { MoneyApp.news.loadError.value = null }) { Text("知道了", fontSize = 12.sp) }
            }
        }
        when (tab) {
            0 -> TodayTab(nav, store) {}
            1 -> ChatTab(nav, store) {}
            2 -> NewsTab(nav, store)
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
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r?.let {
                        val today = java.time.Instant.ofEpochMilli(it.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate() == java.time.LocalDate.now()
                        (if (old) "歷史報告 · " else if (today) "今日報告 · " else "報告 · ") + fmt.format(java.util.Date(it.createdAt))
                    } ?: "還沒有報告",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                if (GroqAi.enabled && !old) OutlinedButton(enabled = job?.running != true && reportWork?.running != true, onClick = {
                    com.enwei.moneymanager.work.ReportJob.startNow(ctx)
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
        // 背景執行中：實際步驟與耗時（離開畫面或 App 也會繼續）
        JobProgress(job, reportWork)
        if (!GroqAi.enabled) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("設定 AI 金鑰後即可產生報告。", fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { nav.navigate("ai") }) { Text("設定 AI") }
        }
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
        // 加權指數 K 線：月線、季線，加上 AI 標出的支撐／壓力
        if (r.outlook.isNotBlank()) Text("短期展望：${r.outlook}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = when { r.outlook.contains("多") -> StockUp; r.outlook.contains("空") -> StockDown; else -> MaterialTheme.colorScheme.onSurfaceVariant })
        com.enwei.moneymanager.ui.components.AnnotatedKLine("^TWII", r.levels.map { l ->
            Triple("AI ${l.label}", l.price, if (l.label.contains("壓")) Color(0xFFAB47BC) else Color(0xFF26A69A))
        })
        if (r.summary.isNotBlank()) CitedText(r.summary, r.cites, newsById)
    }
    // 題材熱度：資料集裡報告提到的題材（則數與平均多空）
    Section("資金追逐的題材") { ChatChartView(com.enwei.moneymanager.data.ChatChart("themes", r.themes), nav) }
    if (r.picks.isNotEmpty()) {
        Text("今天可以考慮（${r.picks.size} 檔）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        // 推薦個股走勢比較（前 5 檔）
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) { com.enwei.moneymanager.ui.components.CompareChart(r.picks.take(5).map { it.symbol }) }
        }
        // 推薦個股的月營收年增率比較
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.padding(12.dp)) { com.enwei.moneymanager.ui.components.RevenueCompare(r.picks.map { it.symbol }) }
        }
        // 長線、短線分開列
        r.picks.groupBy { it.style.ifBlank { "其他" } }.toSortedMap(compareBy { listOf("短線", "長線").indexOf(it).let { i -> if (i < 0) 9 else i } }).forEach { (style, list) ->
            Text("$style（${list.size}）", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            list.forEach { PickCard(it, r.cites, newsById) { nav.navigate("stock/${it.symbol}") } }
        }
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
        ChatChartView(com.enwei.moneymanager.data.ChatChart("holdings"), nav)
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
    if (r.allocation.isNotBlank() || r.alloc.isNotEmpty()) Section("資金分配", collapsible = true) {
        // 比例條＋圖例，文字只留一句
        if (r.alloc.isNotEmpty()) {
            val total = r.alloc.sumOf { it.pct }.takeIf { it > 0 } ?: 1.0
            val palette = listOf(Color(0xFF3C82E6), Color(0xFFF0605A), Color(0xFFFFA000), Color(0xFF2E9E57), Color(0xFF7E57C2), Color(0xFF9E9E9E))
            Row(Modifier.fillMaxWidth().height(14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(7.dp))) {
                r.alloc.forEachIndexed { i, a -> Box(Modifier.weight((a.pct / total).toFloat().coerceAtLeast(0.01f)).height(14.dp).background(palette[i % palette.size])) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                r.alloc.forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(palette[i % palette.size], RoundedCornerShape(4.dp)))
                        Text(" ${a.name} ${"%.0f".format(a.pct / total * 100)}%", fontSize = 12.sp)
                    }
                }
            }
        }
        if (r.allocation.isNotBlank()) CitedText(r.allocation, r.cites, newsById)
    }
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
                Text("${p.symbol} ${p.name}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
            // 近期 K 線：一眼看出走勢，不必點進去
            com.enwei.moneymanager.ui.components.MiniKLine(p.symbol)
            com.enwei.moneymanager.ui.components.RevenueLine(p.symbol)
            // 新聞依據：資料集近 3 天有幾則新聞提到這檔、平均多空
            val store by MoneyApp.news.flow.collectAsStateWithLifecycle()
            val ev = remember(System.identityHashCode(store), p.symbol) {
                store?.let { st -> MoneyApp.news.recent(st, 3).filter { p.symbol in it.symbols } }.orEmpty()
            }
            if (ev.isNotEmpty()) {
                val avg = ev.map { it.sentiment }.average()
                Text(
                    "新聞依據：近 3 天 ${ev.size} 則提到 · " + (if (avg > 0.2) "偏多" else if (avg < -0.2) "偏空" else "中性") +
                        " · " + ev.groupingBy { it.theme }.eachCount().entries.sortedByDescending { it.value }.take(2).joinToString("、") { it.key },
                    fontSize = 11.sp, color = if (avg > 0.2) StockUp else if (avg < -0.2) StockDown else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else Text("新聞依據：資料集近 3 天沒有提到這檔", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
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
    val chatWork = com.enwei.moneymanager.ui.components.rememberAiWorkStatus("advisor-chat")
    val busy = job?.running == true || chatWork?.running == true
    val err = job?.error ?: chatWork?.error
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
                    com.enwei.moneymanager.ui.components.AiWorkProgress(
                        chatWork ?: job?.takeIf { it.running }?.let {
                            com.enwei.moneymanager.work.AiWorkStatus(true, it.step.ifBlank { "AI 正在整理你的投資問題…" }, it.startedAt, null, null)
                        },
                    )
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
            if (busy) OutlinedButton(
                onClick = { com.enwei.moneymanager.work.ChatJob.stop(ctx, job?.question) },
                modifier = Modifier.padding(start = 6.dp),
            ) { Text("停止") }
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
            ) {
                // AI 回覆：結論＋重點條列（格式依「設定 → AI 語氣」）
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CitedText(m.text, m.newsIds, newsById)
                    m.points.forEach { p ->
                        Row {
                            Text("•", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 6.dp))
                            CitedText(p, m.newsIds, newsById)
                        }
                    }
                    if (m.basis.isNotBlank()) Text(m.basis, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 長按：複製、刪除這則
            androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("複製") }, onClick = { clip.setText(androidx.compose.ui.text.AnnotatedString(text)); menu = false })
                androidx.compose.material3.DropdownMenuItem(text = { Text("刪除", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; onDelete() })
            }
        }
        // 回覆附的圖表：用實際資料畫（走勢比較、題材熱度、持股損益）
        m.charts.forEach { ch ->
            Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(top = 6.dp).fillMaxWidth()) {
                Box(Modifier.padding(10.dp)) { ChatChartView(ch, nav) }
            }
        }
        m.cards.forEach { c -> Box(Modifier.padding(top = 6.dp)) { PickCard(c, m.newsIds, newsById) { nav.navigate("stock/${c.symbol}") } } }
        // 引用的新聞：編號和回覆裡的 [1][2] 對應
        val ns = m.newsIds.mapIndexedNotNull { i, id -> newsById[id]?.let { i + 1 to it } }
        if (ns.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) { ns.forEach { (k, n) -> NewsLine(n, num = k) } }
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
        // 排程的每個步驟：做完 ✓、進行中 ●、還沒到 ○
        PipelineSteps(step, refreshOnly = job?.refreshOnly == true)
        Text(step, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        if (done != null && total != null && total > 0) {
            LinearProgressIndicator(progress = { (done.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } else LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(
            when {
                startedAt > 0 -> "已執行 ${com.enwei.moneymanager.work.ReportJob.formatDuration(now - startedAt)}；離開畫面或 App 後仍會繼續。"
                step == "等待其他 AI 工作完成…" -> "前面還有 AI 工作；完成後會自動接續。"
                else -> "等待網路或背景排程。"
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        val error = job?.error
        error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
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
    val aiReady = settings.aiEnabled && (settings.groqKey.isNotBlank() || settings.geminiKey.isNotBlank())
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
                    (q.isEmpty() || n.title.contains(q, true) || n.content.contains(q, true) || q in n.symbols || n.source.contains(q, true)) &&
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
    val listState = rememberLazyListState()
    var filtersOpen by remember { mutableStateOf(false) }
    val activeCount = listOf(days != 0, sent != 0, onlyImportant, onlyMine, sort != 0, theme != null, symbol != null, source != null).count { it }
    // 兩個檢視：「重點」（重點新聞＋題材，點題材展開 AI 整理的重點）與「全部新聞」（依日期）。有篩選條件時自動切到全部新聞
    var view by rememberSaveable { mutableStateOf(0) }
    var infoOpen by remember { mutableStateOf(false) }
    var expandedTheme by remember { mutableStateOf<String?>(null) }
    var allThemes by remember { mutableStateOf(false) }
    LaunchedEffect(anyFilter) { if (anyFilter) view = 1 }
    val digestsByTheme = remember(store.digests) { store.digests.values.groupBy { it.theme }.mapValues { (_, l) -> l.sortedByDescending { it.day } } }
    Column(Modifier.fillMaxSize()) {
        // 狀態列：一行看懂資料集狀態，詳細與設定在「詳情」
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { infoOpen = true }.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val running = job?.running == true
            val c = stats.coverage
            Text(
                if (running) job?.step.orEmpty()
                else "${store.items.size} 則" + (c?.takeIf { it.recent > 0 }?.let { " · 重點整理 ${it.digested * 100 / it.recent}%" } ?: "") +
                    (if (store.lastCollect > 0) " · " + java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(store.lastCollect)) + " 更新" else "") +
                    (if (!settings.advisorDaily) " · 自動更新關閉" else ""),
                fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            if (!running) TextButton(enabled = GroqAi.enabled, onClick = { com.enwei.moneymanager.work.ReportJob.startNow(ctx, refreshOnly = true) }) { Text("立即更新", fontSize = 12.sp) }
            TextButton(onClick = { infoOpen = true }) { Text("詳情", fontSize = 12.sp) }
        }
        if (job?.running == true) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            // 新聞頁上方也顯示排程步驟
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 4.dp)) {
                PipelineSteps(job?.step.orEmpty(), refreshOnly = job?.refreshOnly == true)
            }
        }
        // 搜尋與篩選固定在最上方
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it; shown = 100 }, placeholder = { Text("搜尋標題、代號或來源") }, singleLine = true,
                    modifier = Modifier.weight(1f))
                FilterChip(filtersOpen || activeCount > 0, { filtersOpen = !filtersOpen },
                    label = { Text(if (activeCount > 0) "篩選 $activeCount" else "篩選") })
            }
            // 已套用的條件：一個一個列出來，點 ✕ 就移除
            val activeChips = buildList<Pair<String, () -> Unit>> {
                if (days != 0) add((if (days == 1) "今天" else "$days 天內") to { days = 0 })
                if (sent != 0) add(listOf("", "利多", "利空", "中性")[sent] to { sent = 0 })
                if (onlyImportant) add("只看重要" to { onlyImportant = false })
                if (onlyMine) add("我的持股" to { onlyMine = false })
                if (sort != 0) add(listOf("", "最重要", "多空最強")[sort] + "排序" to { sort = 0 })
                theme?.let { add("題材：$it" to { theme = null }) }
                symbol?.let { add("個股：$it" to { symbol = null }) }
                source?.let { add("來源：$it" to { source = null }) }
            }
            if (activeChips.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                activeChips.forEach { (label, remove) ->
                    InputChip(true, { remove(); shown = 100 }, label = { Text(label, fontSize = 12.sp) },
                        trailingIcon = { Icon(Icons.Filled.Close, "移除", Modifier.size(16.dp)) })
                }
                TextButton(onClick = { query = ""; days = 0; sent = 0; onlyImportant = false; onlyMine = false; sort = 0; theme = null; symbol = null; source = null; shown = 100 }) {
                    Text("全部清除", fontSize = 12.sp)
                }
            }
            // 重點／全部新聞
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(3.dp)) {
                listOf("重點", "全部新聞" + (if (filteredOrNull != null) "（${filtered.size}）" else "")).forEachIndexed { i, l ->
                    val sel = view == i
                    Box(
                        Modifier.weight(1f).background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { view = i }.padding(vertical = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(l, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                }
            }
        }
        // 換條件或換檢視後清單回到最上面
        LaunchedEffect(query, days, sent, onlyImportant, onlyMine, sort, theme, symbol, source, view) { listState.scrollToItem(0) }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (view == 0) {
                if (store.items.isEmpty()) item {
                    Text("還沒有新聞。按上方「立即更新」開始蒐集" + (if (!GroqAi.enabled) "（需要先設定 AI 金鑰）" else "") + "。",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                }
                if (stats.important.isNotEmpty()) item {
                    Text("重點新聞", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                items(stats.important.take(6), key = { "i" + it.id }) { n ->
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) { Box(Modifier.padding(horizontal = 10.dp)) { NewsLine(n) } }
                }
                if (stats.important.size > 6) item {
                    TextButton(onClick = { onlyImportant = true; view = 1 }) { Text("看全部重點新聞 ›") }
                }
                if (themes.isNotEmpty()) item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text("題材（近 3 天，共 ${themes.size} 個）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("點題材看 AI 整理的重點", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val maxCount = themes.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                items(themes.take(if (allThemes) themes.size else 15), key = { "t" + it.key }) { t ->
                    val open = expandedTheme == t.key
                    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
                        Column(Modifier.fillMaxWidth().clickable { expandedTheme = if (open) null else t.key }.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.key, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text("${t.count} 則 · " + (if (t.avgSentiment > 0.2) "偏多" else if (t.avgSentiment < -0.2) "偏空" else "中性"), fontSize = 12.sp,
                                    color = if (t.avgSentiment > 0.2) StockUp else if (t.avgSentiment < -0.2) StockDown else MaterialTheme.colorScheme.onSurfaceVariant)
                                Icon(if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(Modifier.fillMaxWidth().height(4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))) {
                                Box(Modifier.fillMaxWidth(t.count.toFloat() / maxCount).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                            }
                            if (open) {
                                val ds2 = digestsByTheme[t.key].orEmpty()
                                if (ds2.isEmpty()) Text("這個題材的重點還在整理中。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ds2.take(3).forEach { d ->
                                    Text("${d.day.substring(5)}（${d.count} 則）", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    d.points.take(5).forEach { p ->
                                        Text("• ${p.text}" + (if (p.symbols.isNotEmpty()) "（${p.symbols.joinToString("、")}）" else ""), fontSize = 13.sp)
                                    }
                                }
                                TextButton(onClick = { theme = t.key; view = 1 }) { Text("看這個題材的 ${t.count} 則新聞 ›") }
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
                else if (filtered.isEmpty()) item { Text("沒有符合條件的新聞", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
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
        // 資料集詳情與設定（自動更新開關、更新、刪除、排程狀態）
        if (infoOpen) androidx.compose.material3.ModalBottomSheet(onDismissRequest = { infoOpen = false }, containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("新聞自動更新與每日報告", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("依設定的頻率蒐集新聞並整理題材，每日報告完成後通知。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = settings.advisorDaily, onCheckedChange = { enabled -> repo.settings.update { it.copy(advisorDaily = enabled) } }, enabled = aiReady)
                }
                if (!aiReady) TextButton(onClick = { infoOpen = false; nav.navigate("ai") }) { Text("需先啟用 AI 並設定金鑰：前往 AI 設定 ›", fontSize = 12.sp) }
                // 背景蒐集新聞與整理題材的頻率
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("自動更新頻率", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    listOf(1, 3, 6).forEach { h ->
                        FilterChip(settings.newsIntervalHours == h, {
                            repo.settings.update { it.copy(newsIntervalHours = h) }
                            com.enwei.moneymanager.work.NewsWorker.schedule(ctx, replace = true)
                        }, label = { Text("每 $h 小時", fontSize = 12.sp) })
                    }
                }
                Text("每次會：蒐集新聞 → AI 標註 → 合併題材 → 整理重點；早上 7 點後資料完整時產生當日報告。", fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("共 ${store.items.size} 則（近 ${com.enwei.moneymanager.data.NewsDataset.KEEP_DAYS} 天），已標註 ${stats.tagged} 則", fontSize = 14.sp)
                stats.coverage?.let { c ->
                    val pct = if (c.recent > 0) c.digested * 100 / c.recent else 0
                    Text("近 3 天 ${c.recent} 則，已整理進重點 ${c.digested} 則（$pct%）" + (if (c.pending > 0) "；待標註 ${c.pending} 則" else ""),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val grid by com.enwei.moneymanager.MoneyApp.news.grid.collectAsStateWithLifecycle()
                if (grid.blocks > 0) Text(
                    "近 3 天 ${grid.blocks} 個區塊（日期×題材），完整 ${grid.complete} 個" + (if (grid.unread > 0) "；待讀 ${grid.unread} 則" else "") +
                        (if (grid.done) "；資料集完整" else ""),
                    fontSize = 12.sp, color = if (grid.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = GroqAi.enabled && job?.running != true && reportWork?.running != true,
                        onClick = { com.enwei.moneymanager.work.ReportJob.startNow(ctx, refreshOnly = true) },
                        modifier = Modifier.weight(1f),
                    ) { Text("立即更新") }
                    OutlinedButton(
                        enabled = (store.items.isNotEmpty() || store.report != null || store.chat.isNotEmpty()) && job?.running != true && reportWork?.running != true &&
                            newsWork?.running != true && chatWork?.running != true,
                        onClick = { clearDatabaseError = null; confirmClearDatabase = true },
                        modifier = Modifier.weight(1f),
                    ) { Text("刪除資料庫") }
                }
                JobProgress(job, reportWork)
                com.enwei.moneymanager.ui.components.AiWorkProgress(newsWork, Modifier.fillMaxWidth())
                NewsScheduleInfo()
                Spacer(Modifier.height(24.dp))
            }
        }
        if (filtersOpen) androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { filtersOpen = false }, containerColor = MaterialTheme.colorScheme.surface,
        ) {
            var themeQuery by remember { mutableStateOf("") }
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("篩選新聞" + (if (filteredOrNull != null) "（符合 ${filtered.size} 則）" else ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { days = 0; sent = 0; onlyImportant = false; onlyMine = false; sort = 0; theme = null; symbol = null; source = null; shown = 100 }) { Text("全部清除") }
                    TextButton(onClick = { filtersOpen = false }) { Text("完成") }
                }
                @Composable fun group(label: String, body: @Composable () -> Unit) {
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { body() }
                }
                group("時間") { listOf(0 to "全部", 1 to "今天", 3 to "3 天", 7 to "7 天").forEach { (d, l) -> FilterChip(days == d, { days = d; shown = 100 }, label = { Text(l) }) } }
                group("多空") {
                    listOf("全部", "利多", "利空", "中性").forEachIndexed { i, l -> FilterChip(sent == i, { sent = i; shown = 100 }, label = { Text(l) }) }
                    FilterChip(onlyImportant, { onlyImportant = !onlyImportant; shown = 100 }, label = { Text("只看重要") })
                    if (mine.isNotEmpty()) FilterChip(onlyMine, { onlyMine = !onlyMine; shown = 100 }, label = { Text("我的持股") })
                }
                group("排序") { listOf("最新", "最重要", "多空最強").forEachIndexed { i, l -> FilterChip(sort == i, { sort = i; shown = 100 }, label = { Text(l) }) } }
                if (themes.isNotEmpty()) {
                    Text("題材（共 ${themes.size} 個，依則數排序）", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(themeQuery, { themeQuery = it }, placeholder = { Text("搜尋題材") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        themes.filter { themeQuery.isBlank() || it.key.contains(themeQuery.trim(), true) }.forEach { t ->
                            FilterChip(theme == t.key, { theme = if (theme == t.key) null else t.key; shown = 100 }, label = { Text("${t.key} ${t.count}") })
                        }
                    }
                }
                if (stats.symbols.isNotEmpty()) group("個股（共 ${stats.symbols.size} 檔）") {
                    stats.symbols.forEach { t -> FilterChip(symbol == t.key, { symbol = if (symbol == t.key) null else t.key; shown = 100 }, label = { Text("${t.key} ${t.count}") }) }
                }
                if (stats.sources.isNotEmpty()) group("來源（共 ${stats.sources.size} 個）") {
                    stats.sources.forEach { (src, n) -> FilterChip(source == src, { source = if (source == src) null else src; shown = 100 }, label = { Text("$src $n") }) }
                }
                Spacer(Modifier.height(24.dp))
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
                                theme = null
                                symbol = null
                                source = null
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
            Text("${d.theme}　${d.day.substring(5)} · ${d.count} 則", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            com.enwei.moneymanager.ui.components.FoldToggle(open, d.points.size)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val s = snap
        if (s == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        // ① 大盤：加權指數 K 線＋階段
        if (s != null) Section("大盤") {
            val idx = s.index
            com.enwei.moneymanager.ui.components.AnnotatedKLine("^TWII", if (idx != null && idx.close.size >= 20) listOf(
                Triple("壓力（60 日高）", idx.high.takeLast(60).max(), Color(0xFFAB47BC)),
                Triple("支撐（60 日低）", idx.low.takeLast(60).min(), Color(0xFF26A69A)),
            ) else emptyList())
            StageChip(s.indexStage.label, s.indexStage.hint)
            if (s.sox != null) Text("費城半導體：${s.soxStage.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // ② 資金配置：比例條（長線、短線、可投入、預留）＋與目標比較
        if (s != null) Section("資金配置", action = "調整" to { editPlan = true }) {
            val a = s.alloc
            val spare = (a.cash - a.reserve).coerceAtLeast(0)
            val parts = listOf(
                Triple("長線", a.longValue, StockUp), Triple("短線", a.shortValue, Color(0xFFFFA000)),
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
                        Text(" $n ${m(v)}", fontSize = 12.sp)
                    }
                }
            }
            Bucket("長線（目標 ${"%.0f".format(settings.investPlan.longRatio * 100)}%）", a.longValue, a.longTarget, a.longRoom, ::m)
            Bucket("短線（目標 ${"%.0f".format(100 - settings.investPlan.longRatio * 100)}%）", a.shortValue, a.shortTarget, a.shortRoom, ::m)
            if (a.reserveShort > 0) Text("⚠ 可動用現金低於預留金額 ${m(a.reserveShort)}", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            if (a.shortOver > 0) Text("⚠ 短線超出計畫 ${m(a.shortOver)}，新的短線單先暫停", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }
        // ③ 持股紀律＋AI：每檔一張小卡（價位圖、紀律動作、今日報告的 AI 動作）
        if (s != null && s.holdings.isNotEmpty()) Section("持股紀律與 AI 建議") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("依你設定的門檻提示停損／停利", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Switch(settings.investPlan.disciplineOn, { on -> repo.settings.update { it.copy(investPlan = it.investPlan.copy(disciplineOn = on)) } })
            }
            val store by MoneyApp.news.flow.collectAsStateWithLifecycle()
            val ai = store?.report?.holdings.orEmpty().associateBy { it.symbol }
            s.holdings.forEach { h ->
                if (settings.investPlan.disciplineOn && settings.investPlan.guardOn) {
                    GuardRow(h, nav) { editGuard = h }
                    ai[h.symbol]?.let { AiHoldLine(it) }
                } else HoldingCard(h, ai[h.symbol], settings.investPlan.disciplineOn, nav)
            }
        }
        Section("我的投資原則（${settings.investPlan.rules.size} 條）", action = "編輯" to { editRules = true }, collapsible = true, initiallyOpen = false) {
            if (settings.investPlan.rules.isEmpty()) Text("尚未設定投資原則。", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            settings.investPlan.rules.forEachIndexed { i, r -> Text("${i + 1}. $r", fontSize = 14.sp) }
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
            Text("${h.symbol} ${h.name.orEmpty()}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (g != null) Surface(
                color = if (g.triggered) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    (if (g.triggered) g.action.label.substringBefore('：') + "：賣 ${g.sellShares.toLong()} 股" else g.action.label.substringBefore('，')),
                    fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            com.enwei.moneymanager.ui.components.FoldToggle(open, modifier = Modifier.padding(start = 6.dp))
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
    val fold = com.enwei.moneymanager.ui.components.rememberFold("advisor." + title.substringBefore('（'), initiallyOpen)
    val open = fold.open
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                (if (collapsible) Modifier.clickable { fold.toggle() } else Modifier).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
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
    var disciplineOn by remember { mutableStateOf(p.disciplineOn) }
    var guardOn by remember { mutableStateOf(p.guardOn) }
    var gTrail by remember { mutableStateOf(p.guardTrailPct.toString()) }
    var base by remember { mutableStateOf(p.baseShares.toLong().toString()) }
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
                        Text("依你設定的參數計算，預設關閉", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(disciplineOn, {
                        disciplineOn = it
                        if (!it) guardOn = false
                    })
                }
                if (disciplineOn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("保本＋移動停利", fontSize = 14.sp, modifier = Modifier.weight(1f))
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
            !GroqAi.enabled -> add("沒有可用的 AI 金鑰，背景不會更新")
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
    val job by com.enwei.moneymanager.work.ReportJob.state.collectAsStateWithLifecycle()
    val chatJob by com.enwei.moneymanager.work.ChatJob.state.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // 問 AI 的對話流程：搜尋新聞 → 讀全文 → AI 回答 → 確認代號
        chatJob?.let { c ->
            if (c.running) {
                Text("AI 對話程序：「${c.question.take(16)}${if (c.question.length > 16) "…" else ""}」", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ChatSteps(c.step)
                Text(c.step.ifBlank { "準備中…" } + " · 已 ${((now - c.startedAt) / 1000).coerceAtLeast(0)} 秒", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            } else if (now - c.startedAt < 6 * 3600_000L) Text("上一次對話（${hm.format(java.util.Date(c.startedAt))}）：${c.error ?: c.step.ifBlank { "已回覆" }}", fontSize = 12.sp,
                color = if (c.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 目前的排程程序（執行中時顯示步驟與進度；結束後顯示上一輪的結果）
        job?.let { j ->
            if (j.running) {
                Text("目前程序", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                PipelineSteps(j.step, refreshOnly = j.refreshOnly)
                Text(j.step + (if (j.done != null && j.total != null) "（${j.done}/${j.total}）" else "") +
                    (j.etaAt?.takeIf { it > now }?.let { " · 約剩 ${com.enwei.moneymanager.work.ReportJob.formatDuration(it - now)}" } ?: ""),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            } else Text("上一輪（${hm.format(java.util.Date(j.startedAt))} 開始）：${j.error ?: j.step}", fontSize = 12.sp,
                color = if (j.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lines.forEach { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (health.paused(now)) TextButton(onClick = { com.enwei.moneymanager.work.AiHealth.resume() }) { Text("恢復背景更新") }
    }
}


/** 對話回覆裡的一張圖表。 */
@Composable
private fun ChatChartView(ch: com.enwei.moneymanager.data.ChatChart, nav: NavController) {
    when (ch.kind) {
        "compare" -> com.enwei.moneymanager.ui.components.CompareChart(ch.items)
        "revenue" -> com.enwei.moneymanager.ui.components.RevenueCompare(ch.items)
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
    if (rows.isEmpty()) { Text("還沒有足夠的產業題材新聞", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); return }
    val max = rows.maxOf { it.first.count }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("產業題材（長條＝新聞則數，顏色＝多空）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        rows.forEach { (t, syms) ->
            val c = when { t.avgSentiment > 0.2 -> StockUp; t.avgSentiment < -0.2 -> StockDown; else -> MaterialTheme.colorScheme.onSurfaceVariant }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t.key, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${t.count} 則 · " + (if (t.avgSentiment > 0.2) "偏多" else if (t.avgSentiment < -0.2) "偏空" else "中性"), fontSize = 11.sp, color = c)
                }
                Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(t.count.toFloat() / max).height(6.dp).background(c, RoundedCornerShape(3.dp)))
                }
                if (syms.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    syms.forEach { sym ->
                        Text(sym, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
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
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 4.dp)) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
            Text("AI：${a.action}", fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        Text(com.enwei.moneymanager.data.stripCites(a.reason), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp, top = 2.dp))
    }
}

/** 一檔持股：名稱、長短線、損益、價位圖、紀律動作與 AI 動作。 */
@Composable
private fun HoldingCard(h: com.enwei.moneymanager.data.HoldingPlan, ai: com.enwei.moneymanager.data.AdvisorHolding?, discipline: Boolean, nav: NavController) {
    val g = h.price?.let { (it / h.avgCost - 1) * 100 }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${h.symbol} ${h.name.orEmpty()}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.weight(1f).clickable { nav.navigate("stock/${h.symbol}") })
            g?.let { Text("%+.1f%%".format(it), fontSize = 13.sp, color = if (it >= 0) StockUp else StockDown) }
            // 長線／短線：點一下切換
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(start = 8.dp).clickable {
                    val next = TradeStyle.entries[(h.style.ordinal + 1) % TradeStyle.entries.size]
                    repo.settings.update { it.copy(stockStyle = it.stockStyle + (h.symbol to next.name)) }
                },
            ) { Text(h.style.label, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
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
            ) { Text("紀律：${ad.action}", fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)) }
            Text(ad.note, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
        }
        ai?.let { AiHoldLine(it) }
    }
    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    StepRow(listOf("持股與大盤", "搜尋新聞", "讀取全文", "AI 回答", "確認代號"), cur)
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
            Text("$mark $s", fontSize = 12.sp, color = col, fontWeight = if (i == cur) FontWeight.SemiBold else FontWeight.Normal)
            if (i < steps.lastIndex) Text("  →  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PipelineSteps(step: String, refreshOnly: Boolean) {
    val steps = listOf("蒐集新聞", "AI 標註", "合併題材", "整理重點") + if (refreshOnly) emptyList() else listOf("產生報告")
    val cur = when {
        step.contains("蒐集") || step.contains("準備") -> 0
        step.contains("標註") -> 1
        step.contains("合併題材") -> 2
        step.contains("整理新聞重點") || step.contains("重點") -> 3
        step.contains("分析資料集") || step.contains("大盤") || step.contains("代號") || step.contains("報告") -> 4
        else -> 0
    }.coerceAtMost(steps.lastIndex)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        steps.forEachIndexed { i, s ->
            val (mark, col) = when {
                i < cur -> "✓" to MaterialTheme.colorScheme.primary
                i == cur -> "●" to MaterialTheme.colorScheme.primary
                else -> "○" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text("$mark $s", fontSize = 12.sp, color = col, fontWeight = if (i == cur) FontWeight.SemiBold else FontWeight.Normal)
            if (i < steps.lastIndex) Text("  →  ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
