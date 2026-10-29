package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
    var store by remember { mutableStateOf(NewsStore()) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(tick) { store = MoneyApp.news.load() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ScrollableTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface, edgePadding = 8.dp) {
            listOf("今日", "問 AI", "新聞", "資金與紀律").forEachIndexed { i, t ->
                Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 15.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when (tab) {
            0 -> TodayTab(nav, store) { tick++ }
            1 -> ChatTab(nav, store) { tick++ }
            2 -> NewsTab(store) { tick++ }
            else -> PlanTab(nav)
        }
    }
}

@Composable
private fun Disclaimer() = Text(
    "AI 分析與推薦僅供參考，不是投資建議；下單前請自行判斷並遵守自己的紀律。",
    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** 今日報告：大盤、題材、推薦卡片、重點新聞、持股動作、資金建議、檢查清單。 */
@Composable
private fun TodayTab(nav: NavController, store: NewsStore, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val r = store.report
    val newsById = remember(store.lastCollect, store.items.size, store.items.count { it.tagged }) { store.items.associateBy { it.id } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    r?.let { "今日報告 · " + java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(it.createdAt)) } ?: "還沒有今日報告",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "資料集 ${MoneyApp.news.recent(store, 3).size} 則（近 3 天已標註）" + (r?.let { " · 報告讀了 ${it.newsCount} 則" } ?: ""),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (GroqAi.enabled) OutlinedButton(enabled = step == null, onClick = {
                err = null
                scope.launch {
                    val res = runCatching { repo.buildDailyReport(MoneyApp.news) { step = it } }
                    if (res.getOrNull() == null) err = "產生失敗：${res.exceptionOrNull()?.message ?: GroqAi.lastError ?: "沒有回應"}"
                    step = null
                    reload()
                }
            }) { Text(if (r == null) "產生報告" else "重新產生") }
        }
        step?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        err?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
        if (!GroqAi.enabled) Text("到「設定 → AI 智慧辨識」填入金鑰後，App 會每天在背景讀新聞、整理資料集並產生報告。", fontSize = 13.sp)
        if (r != null) {
            Section("大盤") {
                StageChip(r.stage.ifBlank { "趨勢" }, r.trend)
                if (r.themes.isNotEmpty()) Text(r.themes.joinToString("　") { "#$it" }, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                if (r.summary.isNotBlank()) Text(r.summary, fontSize = 14.sp)
            }
            if (r.picks.isNotEmpty()) {
                Text("今天可以考慮", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                r.picks.forEach { PickCard(it) { nav.navigate("stock/${it.symbol}") } }
            }
            if (r.keyNews.isNotEmpty()) Section("一定要看的新聞") {
                r.keyNews.forEach { k ->
                    newsById[k.id]?.let { n ->
                        NewsLine(n)
                        if (k.why.isNotBlank()) Text("→ ${k.why}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (r.holdings.isNotEmpty()) Section("持股動作") {
                r.holdings.forEach { h ->
                    Row(Modifier.fillMaxWidth().clickable { nav.navigate("stock/${h.symbol}") }.padding(vertical = 2.dp)) {
                        Text(h.symbol, fontSize = 14.sp, modifier = Modifier.widthIn(min = 56.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.action + (h.stop?.let { "｜停損 ${"%.2f".format(it)}" } ?: ""), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(h.reason, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (r.allocation.isNotBlank()) Section("資金分配") { Text(r.allocation, fontSize = 14.sp) }
            if (r.checklist.isNotEmpty() || r.warnings.isNotEmpty()) Section("下單前") {
                r.checklist.forEach { Text("☐ $it", fontSize = 13.sp) }
                r.warnings.forEach { Text("! $it", fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            }
        }
        Disclaimer()
        Spacer(Modifier.height(24.dp))
    }
}

/** 推薦股票卡片：代號名稱、長短線、強弱、業務、理由、進出場；點進去看 K 線與資訊。 */
@Composable
fun PickCard(p: StockPick, onClick: () -> Unit) {
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
            if (p.reason.isNotBlank()) Text(p.reason, fontSize = 14.sp)
            if (p.entry.isNotBlank()) Text("進場：${p.entry}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (p.stop.isNotBlank()) Text("停損：${p.stop}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            p.amount?.let { Text("建議投入約 ${Money.withSymbol(it * 100, "TWD")}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary) }
            Text("看 K 線與資訊 ›", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
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
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val newsById = remember(store.lastCollect, store.items.size, store.items.count { it.tagged }) { store.items.associateBy { it.id } }
    fun send(q: String) {
        if (q.isBlank() || busy) return
        busy = true; err = null; input = ""
        scope.launch {
            val r = runCatching { repo.askAdvisor(MoneyApp.news, q) }.getOrNull()
            if (r == null) err = "AI 沒有回應：${GroqAi.lastError ?: ""}"
            busy = false
            reload()
        }
    }
    LaunchedEffect(store.chat.size) { if (store.chat.isNotEmpty()) list.animateScrollToItem(store.chat.size) }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("可以問今天該買什麼、某檔股票能不能買、持股怎麼處理、資金怎麼分配。AI 會讀新聞資料集、你的持股、資金計畫與紀律再回答。",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
            items(store.chat) { m -> Bubble(m, newsById, nav) }
            if (busy) item { Text("AI 思考中…", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
            err?.let { e -> item { Text(e, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) } }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("今天該買什麼？", "我的持股怎麼處理？", "現在適合加碼嗎？", "最近有什麼重大新聞？", "短線資金還能用多少？").forEach { q ->
                FilterChip(false, { send(q) }, label = { Text(q, fontSize = 12.sp) }, enabled = !busy && GroqAi.enabled)
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, placeholder = { Text("問股票助理…") }, modifier = Modifier.weight(1f), maxLines = 3)
            Button(enabled = !busy && input.isNotBlank() && GroqAi.enabled, onClick = { send(input) }, modifier = Modifier.padding(start = 6.dp)) { Text("送出") }
        }
    }
}

@Composable
private fun Bubble(m: ChatMsg, newsById: Map<String, com.enwei.moneymanager.data.NewsItem>, nav: NavController) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
        Surface(
            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.widthIn(max = 320.dp),
        ) { Text(m.text, fontSize = 14.sp, modifier = Modifier.padding(10.dp)) }
        m.cards.forEach { c -> Box(Modifier.padding(top = 6.dp)) { PickCard(c) { nav.navigate("stock/${c.symbol}") } } }
        val ns = m.newsIds.mapNotNull { newsById[it] }
        if (ns.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) { ns.forEach { NewsLine(it) } }
    }
}

/** 新聞資料集：蒐集狀態、題材熱度、重點新聞與最新新聞。 */
@Composable
private fun NewsTab(store: NewsStore, reload: () -> Unit) {
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf<String?>(null) }
    val ds = MoneyApp.news
    val key = store.items.count { it.tagged }
    val themes = remember(store.lastCollect, key) { ds.themes(store) }
    val important = remember(store.lastCollect, key) { ds.important(store, 3, 30) }
    val tagged = store.items.count { it.tagged }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Section("資料集") {
            Text("共 ${store.items.size} 則（近 ${com.enwei.moneymanager.data.NewsDataset.KEEP_DAYS} 天），已標註 $tagged 則", fontSize = 14.sp)
            Text(
                "上次蒐集：" + (if (store.lastCollect > 0) java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(store.lastCollect)) else "還沒有") +
                    "。背景每 6 小時從 ${com.enwei.moneymanager.data.NewsDataset.BASE_QUERIES.size} 個主題與你的持股抓新聞，AI 逐則標註題材、相關個股、多空與重要性。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(enabled = step == null, onClick = {
                scope.launch { runCatching { repo.refreshNews(ds) { step = it } }; step = null; reload() }
            }) { Text("立即更新") }
            step?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }
        if (themes.isNotEmpty()) Section("題材熱度（近 3 天）") {
            val max = themes.maxOf { it.count }.coerceAtLeast(1)
            themes.take(12).forEach { t ->
                Column {
                    Row {
                        Text(t.key, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("${t.count} 則 · " + (if (t.avgSentiment > 0.2) "偏多" else if (t.avgSentiment < -0.2) "偏空" else "中性"), fontSize = 13.sp,
                            color = if (t.avgSentiment > 0.2) StockUp else if (t.avgSentiment < -0.2) StockDown else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(t.count.toFloat() / max).height(5.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
                    }
                }
            }
        }
        if (important.isNotEmpty()) Section("重點新聞") { important.forEach { NewsLine(it) } }
        val latest = store.items.take(40)
        if (latest.isNotEmpty()) Section("最新新聞") { latest.forEach { NewsLine(it) } }
        Spacer(Modifier.height(24.dp))
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
    LaunchedEffect(holdings, settings.investPlan, settings.stockStyle) { snap = runCatching { repo.advisorSnapshot(fetch = true) }.getOrNull() }
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
        if (s != null && s.holdings.isNotEmpty()) Section("持股紀律（動態停損、分批停利）") {
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
        Section("我的交易紀律", action = "編輯" to { editRules = true }) {
            settings.investPlan.rules.forEachIndexed { i, r -> Text("${i + 1}. $r", fontSize = 14.sp) }
        }
        Section("背景分析") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("每天讀新聞並產生報告", fontSize = 14.sp, modifier = Modifier.weight(1f))
                Switch(settings.advisorDaily, { v -> repo.settings.update { it.copy(advisorDaily = v) } }, enabled = GroqAi.enabled)
            }
            Text("每 6 小時抓一次新聞並標註，早上 7 點後產生當天報告並通知；需要網路且電量不低。", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
    if (editPlan) PlanDialog(settings.investPlan) { editPlan = false }
    if (editRules) RulesDialog(settings.investPlan.rules) { editRules = false }
}

@Composable
private fun Section(title: String, action: Pair<String, () -> Unit>? = null, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.let { (l, f) -> TextButton(onClick = f) { Text(l) } }
            }
            content()
        }
    }
}

@Composable
private fun StageChip(label: String, hint: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
            Text(label, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
        }
        Text("  $hint", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("資金與紀律參數") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(reserve, { reserve = it }, "保留生活費", suffix = "元")
                NumberField(longPct, { longPct = it }, "長線占可投資資金", suffix = "%")
                NumberField(stop, { stop = it }, "短線停損", suffix = "%")
                NumberField(trail, { trail = it }, "短線移動停損（從高點回落）", suffix = "%")
                NumberField(longTrail, { longTrail = it }, "長線移動停損（從高點回落）", suffix = "%")
                NumberField(tp, { tp = it }, "分批停利門檻（長線為兩倍）", suffix = "%")
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
