package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AiReview
import com.enwei.moneymanager.data.ReviewData
import com.enwei.moneymanager.data.ReviewSpan
import com.enwei.moneymanager.data.spendingReview
import com.enwei.moneymanager.data.habits
import com.enwei.moneymanager.data.lateNight
import com.enwei.moneymanager.data.merchants
import com.enwei.moneymanager.data.sane
import androidx.compose.foundation.clickable
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import com.enwei.moneymanager.ui.components.AiWorkProgress
import com.enwei.moneymanager.ui.components.rememberAiWorkStatus
import com.enwei.moneymanager.work.AiBackgroundWork

/**
 * 收支檢討：選月／季／年，顯示總覽、支出結構、重點觀察（規則），
 * 以及固定格式的 AI 檢討（總評、做得好、需要改善、行動建議、下期目標）。
 */
@Composable
fun ReviewScreen(nav: NavController) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("收支檢討", { nav.popBackStack() }) }) { pad ->
        androidx.compose.foundation.layout.Box(Modifier.padding(pad)) { ReviewContent(nav) }
    }
}

/** 收支檢討內容（交易頁的「檢討」分頁）。 */
@Composable
fun ReviewContent(
    nav: NavController,
    /** 交易頁傳入的期間（月／季／年與上方選的月份）；null 時自己顯示切換箭頭 */
    period: Pair<ReviewSpan, java.time.YearMonth>? = null,
    onSpan: (ReviewSpan) -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val reviewProgress by AiBackgroundWork.reviewProgress.collectAsStateWithLifecycle()
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var ownSpan by rememberSaveable { mutableStateOf(ReviewSpan.MONTH) }
    var ownOffset by rememberSaveable { mutableStateOf(0) }
    val span = period?.first ?: ownSpan
    val offset = period?.let { (sp, ym) ->
        val cur = com.enwei.moneymanager.core.Periods.monthOf(java.time.LocalDate.now(), settings.monthStartDay)
        when (sp) {
            ReviewSpan.MONTH -> (ym.year * 12 + ym.monthValue) - (cur.year * 12 + cur.monthValue)
            ReviewSpan.QUARTER -> (ym.year * 4 + (ym.monthValue - 1) / 3) - (cur.year * 4 + (cur.monthValue - 1) / 3)
            ReviewSpan.YEAR -> ym.year - cur.year
        }
    } ?: ownOffset
    var data by remember { mutableStateOf<ReviewData?>(null) }
    LaunchedEffect(span, offset, all) { data = repo.spendingReview(span, offset) }
    val base = lk.base
    fun m(v: Long) = Money.withSymbol(v, base)

    run {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 英文標籤較長：選項可左右捲動，切換期間的箭頭固定在右邊
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReviewSpan.entries.forEach { sp ->
                        FilterChip(span == sp, { if (period != null) onSpan(sp) else { ownSpan = sp; ownOffset = 0 } }, label = { Text("每${sp.label}", maxLines = 1) })
                    }
                }
                if (period == null) {
                    IconButton(onClick = { ownOffset-- }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                    IconButton(enabled = ownOffset < 0, onClick = { ownOffset++ }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                }
            }
            val d = data ?: return@Column
            // 交易頁上方已顯示期間，避免重複顯示年月或季度。
            if (period == null) Text(d.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

            // ① 總覽
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val netColor = if (d.net >= 0) IncomeColor else ExpenseColor
                        if (maxWidth < 340.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Stat("收入", m(d.income), IncomeColor, Modifier.weight(1f))
                                    Stat("支出", m(d.expense), ExpenseColor, Modifier.weight(1f))
                                }
                                Stat("結餘", m(d.net), netColor, Modifier.fillMaxWidth())
                            }
                        } else {
                            Row {
                                Stat("收入", m(d.income), IncomeColor, Modifier.weight(1f))
                                Stat("支出", m(d.expense), ExpenseColor, Modifier.weight(1f))
                                Stat("結餘", m(d.net), netColor, Modifier.weight(1f))
                            }
                        }
                    }
                    Text(
                        listOfNotNull(
                            if (d.income > 0) "儲蓄率 ${"%.0f".format(d.savingsRate)}%" else null,
                            "日均支出 ${m(d.dailyAvg)}",
                            d.expenseChangePct?.let { "支出比上${d.span.label} ${"%+.0f".format(it)}%" },
                            if (d.avgExpense > 0) "前三${d.span.label}平均 ${m(d.avgExpense)}" else null,
                        ).joinToString(" · "),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // 預算（每月）：原本在「總計」分頁，移到這裡和檢討一起看
            // 目標與預算：每月的支出上限、各分類上限與儲蓄率目標（可在「預算設定」修改，也可以採用 AI 建議）
            if (span == ReviewSpan.MONTH && (settings.budgets.isNotEmpty() || settings.savingsGoal != null)) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("這個月的目標", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { nav.navigate("budget") }) { Text("修改") }
                    }
                    settings.savingsGoal?.let { g ->
                        val rate = if (d.income > 0) d.savingsRate else 0.0
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row {
                                Text("儲蓄率", fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text("${"%.0f".format(rate)}%　目標 ${"%.0f".format(g)}%", fontSize = 13.sp,
                                    color = if (rate >= g) IncomeColor else ExpenseColor)
                            }
                            Box(Modifier.fillMaxWidth().height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                                Box(Modifier.fillMaxWidth((rate / g).toFloat().coerceIn(0f, 1f)).height(8.dp)
                                    .background(if (rate >= g) IncomeColor else ExpenseColor, RoundedCornerShape(4.dp)))
                            }
                        }
                    }
                    settings.budgets.entries.sortedBy { it.key }.forEach { (cid, budget) ->
                        val used = if (cid == 0L) d.expense else d.categories.find { it.id == cid }?.amount ?: 0L
                        BudgetBar(if (cid == 0L) "總預算" else lk.category(cid)?.name ?: "已刪除分類", used, budget)
                    }
                }
            }

            // ② 支出結構：一條比例條＋兩欄圖例（AI 建議留意的分類以 ● 標出）
            if (d.categories.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val focus = settings.reviews[d.key]?.focus.orEmpty()
                    StructureCard(d, ::m, focus)
                }
            }

            // ③ 重點觀察（規則）
            // ③ 消費習慣：全部用圖表（每日支出、時段、平日／週末、常去的商家）
            if (d.expenses.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("消費習慣", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    HabitCharts(d, ::m, lk)
                }
            }

            // ④ AI 檢討（固定格式）
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI 檢討與建議", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val cached = settings.reviews[d.key]
                        if (GroqAi.enabled) OutlinedButton(
                            enabled = !reviewProgress.running && d.txnCount > 0,
                            onClick = {
                                scope.launch {
                                    AiBackgroundWork.runReview(span, offset, d.key)
                                }
                            },
                        ) { Text(if (reviewProgress.running) "產生中…" else if (cached == null) "產生" else "重新產生") }
                    }
                    AiWorkProgress(reviewProgress, Modifier.fillMaxWidth())
                    val r = settings.reviews[d.key]
                    when {
                        // 下期目標依實際的前幾期支出校正（舊的檢討也一樣）
                        r != null -> AiReviewBody(r.sane(d), ::m, d, lk, settings.txnNecessity, nav)
                        !GroqAi.enabled -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("設定 AI 金鑰後即可產生檢討。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { nav.navigate("ai") }) { Text("設定 AI") }
                        }
                        else -> Text("按「產生」寫出這期的檢討與下期目標", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ⑤ 最大幾筆
            if (d.top.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("最大幾筆支出", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                    d.top.take(5).forEach { t -> MmTxnRow(t, lk) { nav.navigate("txn/${t.id}") } }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 14.sp, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** AI 檢討的固定版面：分數＋總評 → 做得好 → 需要改善 → 行動建議 → 下期目標。 */
@Composable
private fun AiReviewBody(
    r: AiReview, m: (Long) -> String, d: ReviewData, lk: Lookups, necessity: Map<Long, String>, nav: NavController,
) {
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically) {
        val c = when {
            r.score >= 80 -> IncomeColor
            r.score >= 60 -> MaterialTheme.colorScheme.tertiary
            else -> ExpenseColor
        }
        Box(Modifier.size(52.dp).background(c.copy(alpha = 0.15f), CircleShape), contentAlignment = Alignment.Center) {
            Text("${r.score}", fontSize = 18.sp, color = c, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(r.headline, fontSize = 14.sp, modifier = Modifier.weight(1f))
    }
    // 可省金額：每個建議約可省多少（圖表取代文字）
    val savings = r.findings.filter { (it.saving ?: 0) > 0 }.map { Triple(it.text, it.saving!!, "約 ${m(it.saving)}／月") }
    com.enwei.moneymanager.ui.components.LabeledBars("建議可省（每月）", savings, IncomeColor, Modifier.padding(top = 8.dp))
    // 每一段像資料夾：點標題展開／收起（預設展開「想跟你確認的幾筆」與「行動建議」，其他收起）
    @Composable
    fun fold(title: String, count: Int, initiallyOpen: Boolean, body: @Composable () -> Unit) {
        if (count == 0) return
        val fold = com.enwei.moneymanager.ui.components.rememberFold("review." + title, initiallyOpen)
        val open = fold.open
        Row(Modifier.fillMaxWidth().clickable { fold.toggle() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            com.enwei.moneymanager.ui.components.FoldToggle(open, count)
        }
        if (open) body()
    }
    @Composable
    fun section(title: String, items: List<String>, mark: String) =
        fold(title, items.size, false) { items.forEach { Text("$mark $it", fontSize = 14.sp) } }
    // 新版：每個發現都附依據與建議
    if (r.findings.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        r.findings.forEach { f ->
            androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(f.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    if (f.basis.isNotBlank()) Text("依據：${f.basis}", fontSize = 12.sp, color = gray)
                    if (f.action.isNotBlank()) Text("→ ${f.action}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    f.saving?.let { Text("約省 ${m(it)}／月", fontSize = 12.sp, color = IncomeColor) }
                }
            }
        }
    }
    // 舊版的文字分析（重新產生後會改成上面的精簡格式）
    val oldCount = r.analysis.size + r.good.size + r.improve.size + r.actions.size
    if (r.findings.isEmpty() && oldCount > 0) fold("舊版文字分析（按「重新產生」改成圖表版）", oldCount, false) {
    fold("多角度分析", r.analysis.size, false) {
        r.analysis.forEach { a ->
            Column {
                Text(a.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(a.text, fontSize = 14.sp)
            }
        }
    }
    section("做得好", r.good, "✓")
    section("可以調整的地方", r.improve, "•")
    fold("行動建議", r.actions.size, false) {
        r.actions.forEachIndexed { i, a ->
            Row {
                Text("${i + 1}. ${a.action}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                a.saving?.let { Text("每月省 ${m(it)}", fontSize = 13.sp, color = IncomeColor) }
            }
        }
    }
    }
    // 下期目標：AI 建議的支出上限、各分類上限、儲蓄率；按「採用」寫進每月預算與目標（之後在檢討頁看進度、在預算設定修改）
    if (r.expenseCap != null || r.savingsTarget != null || r.caps.isNotEmpty()) NextGoals(r, m, lk, nav)
    Text(
        "產生於 " + java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(r.createdAt)),
        fontSize = 11.sp, color = gray,
    )
}


/** AI 對特定交易的疑問：列出交易、問題，讓使用者標「必要／可省／一次性」並補充說明。 */
@Composable
private fun QuestionCard(
    q: com.enwei.moneymanager.data.ReviewQuestion, txns: List<com.enwei.moneymanager.data.TxnEntity>, lk: Lookups,
    necessity: Map<Long, String>, m: (Long) -> String, nav: NavController,
) {
    if (txns.isEmpty()) return
    val current = com.enwei.moneymanager.data.necessityOf(necessity[txns.first().id])
    var note by remember(q) { mutableStateOf(current?.second.orEmpty()) }
    androidx.compose.material3.Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            txns.forEach { t ->
                val dt = java.time.Instant.ofEpochMilli(t.date).atZone(java.time.ZoneId.systemDefault())
                Row(Modifier.fillMaxWidth().clickable { nav.navigate("txn/${t.id}") }) {
                    Text("${dt.monthValue}/${dt.dayOfMonth} ${"%02d:%02d".format(dt.hour, dt.minute)}  ${t.merchant ?: lk.catLabel(t.categoryId) ?: "未命名"}",
                        fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(m(t.amount), fontSize = 13.sp, color = ExpenseColor)
                }
            }
            Text("？ ${q.question}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (q.why.isNotBlank()) Text(q.why, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.enwei.moneymanager.data.Necessity.entries.forEach { n ->
                    FilterChip(current?.first == n, {
                        repo.settings.update { st -> st.copy(txnNecessity = st.txnNecessity + txns.associate { it.id to "${n.name}|${note.trim()}" }) }
                    }, label = { Text(n.label) })
                }
            }
            androidx.compose.material3.OutlinedTextField(
                note, { v -> note = v; if (current != null) repo.settings.update { st -> st.copy(txnNecessity = st.txnNecessity + txns.associate { it.id to "${current.first.name}|${v.trim()}" }) } },
                placeholder = { Text("補充說明（選填）", fontSize = 13.sp) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}


private val StructurePalette = listOf(
    androidx.compose.ui.graphics.Color(0xFFF0605A), androidx.compose.ui.graphics.Color(0xFFFFA000), androidx.compose.ui.graphics.Color(0xFF3C82E6),
    androidx.compose.ui.graphics.Color(0xFF2E9E57), androidx.compose.ui.graphics.Color(0xFF7E57C2), androidx.compose.ui.graphics.Color(0xFF26A69A),
    androidx.compose.ui.graphics.Color(0xFF9E9E9E),
)

/** 支出結構：一條 100% 比例條，下方兩欄圖例（分類、金額、比上期）。前 6 大分類，其餘併成「其他」。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun StructureCard(d: ReviewData, m: (Long) -> String, focus: List<String>) {
    val sorted = d.categories.sortedByDescending { it.amount }.filter { it.amount > 0 }
    val top = sorted.take(6)
    val rest = sorted.drop(6)
    val rows = top.map { Triple(it.name, it.amount, it.prev) } +
        (if (rest.isNotEmpty()) listOf(Triple("其餘 ${rest.size} 類", rest.sumOf { it.amount }, rest.sumOf { it.prev })) else emptyList())
    val total = rows.sumOf { it.second }.coerceAtLeast(1)
    val hot = rows.map { it.first }.filter { n -> focus.any { f -> f.isNotBlank() && (n.contains(f) || f.contains(n)) } }.toSet()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("支出結構", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (hot.isNotEmpty()) Text("● AI 建議留意", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
    }
    Row(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp))) {
        rows.forEachIndexed { i, r ->
            Box(Modifier.weight((r.second.toFloat() / total).coerceAtLeast(0.005f)).fillMaxHeight().background(StructurePalette[i % StructurePalette.size]))
        }
    }
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2) {
        rows.forEachIndexed { i, (name, v, prev) ->
            Row(Modifier.weight(1f).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(StructurePalette[i % StructurePalette.size], CircleShape))
                Text(
                    " " + (if (name in hot) "● " else "") + name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (name in hot) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f),
                )
                Text("${v * 100 / total}% ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (prev == 0L) "新" else "${if (v >= prev) "▲" else "▼"}${kotlin.math.abs(v - prev) * 100 / prev}%",
                    fontSize = 11.sp, color = if (v > prev) ExpenseColor else IncomeColor, modifier = Modifier.padding(end = 6.dp),
                )
            }
        }
    }
    Text("合計 ${m(total)}；點「每日」可看明細", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}


/** 消費習慣圖表：每日（或每週）支出、時段分布、平日／週末、常去的商家（金額與次數）。 */
@Composable
private fun HabitCharts(d: ReviewData, m: (Long) -> String, lk: Lookups) {
    val zone = java.time.ZoneId.systemDefault()
    val last = minOf(d.end, java.time.LocalDate.now())
    val n = (java.time.temporal.ChronoUnit.DAYS.between(d.start, last) + 1).toInt().coerceAtLeast(1)
    val stats = remember(d) {
        val byDay = LongArray(n)
        val slots = LongArray(5)
        var weekday = 0L; var weekend = 0L
        d.expenses.forEach { t ->
            val dt = java.time.Instant.ofEpochMilli(t.date).atZone(zone)
            val v = lk.fx.convert(t.amount, t.currency)
            val i = java.time.temporal.ChronoUnit.DAYS.between(d.start, dt.toLocalDate()).toInt()
            if (i in 0 until n) byDay[i] += v
            val h = dt.hour
            slots[when { h in 5..10 -> 0; h in 11..13 -> 1; h in 14..16 -> 2; h in 17..21 -> 3; else -> 4 }] += v
            if (dt.dayOfWeek == java.time.DayOfWeek.SATURDAY || dt.dayOfWeek == java.time.DayOfWeek.SUNDAY) weekend += v else weekday += v
        }
        Triple(byDay.toList(), slots.toList(), weekday to weekend)
    }
    val weekly = n > 62
    com.enwei.moneymanager.ui.components.DailyBarsChart(
        if (weekly) stats.first.chunked(7).map { it.sum() } else stats.first, m, ExpenseColor, weekly = weekly,
    )
    val slotNames = listOf("早上 5–11", "中午 11–14", "下午 14–17", "晚上 17–22", "深夜 22–5")
    val total = stats.second.sum().coerceAtLeast(1)
    com.enwei.moneymanager.ui.components.LabeledBars(
        "時段", slotNames.indices.map { i -> Triple(slotNames[i], stats.second[i], "${stats.second[i] * 100 / total}%　${m(stats.second[i])}") }, ExpenseColor,
    )
    val (wd, we) = stats.third
    com.enwei.moneymanager.ui.components.LabeledBars(
        "平日／週末（每天平均）",
        listOf(Triple("平日", wd / 5, m(wd / 5) + "／天"), Triple("週末", we / 2, m(we / 2) + "／天")).let { rows ->
            // 換算成每週的「每天平均」：平日 5 天、週末 2 天，再除以期間的週數
            val weeks = (n / 7.0).coerceAtLeast(1.0)
            rows.map { (k, v, _) -> val per = (v / weeks).toLong(); Triple(k, per, m(per) + "／天") }
        },
        MaterialTheme.colorScheme.tertiary,
    )
    com.enwei.moneymanager.ui.components.LabeledBars(
        "常去的商家",
        d.merchants().sortedByDescending { it.total }.take(6).map { Triple(it.name.take(12), it.total, "${it.count} 次　${m(it.total)}") },
        MaterialTheme.colorScheme.primary,
    )
}


/** AI 建議的下期目標，可一鍵採用為每月預算與儲蓄率目標。 */
@Composable
private fun NextGoals(r: AiReview, m: (Long) -> String, lk: Lookups, nav: NavController) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    // 分類名稱對應到大分類
    val roots = lk.categories.filter { it.kind == com.enwei.moneymanager.data.TxnType.EXPENSE && it.parentId == null && !it.archived }
    val capIds = r.caps.mapNotNull { c -> roots.firstOrNull { it.name == c.category } ?: roots.firstOrNull { it.name.contains(c.category) || c.category.contains(it.name) } }
        .zip(r.caps).map { (cat, c) -> cat.id to c }
    val adopted = (r.expenseCap == null || settings.budgets[0L] == r.expenseCap) &&
        capIds.all { (id, c) -> settings.budgets[id] == c.cap } &&
        (r.savingsTarget == null || settings.savingsGoal == r.savingsTarget)
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("下期目標（AI 建議）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            r.expenseCap?.let { Row { Text("總支出上限", fontSize = 13.sp, modifier = Modifier.weight(1f)); Text(m(it), fontSize = 13.sp) } }
            r.savingsTarget?.let { Row { Text("儲蓄率", fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("${"%.0f".format(it)}% 以上", fontSize = 13.sp) } }
            capIds.forEach { (id, c) ->
                Row {
                    Text(lk.category(id)?.name ?: c.category, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("上限 ${m(c.cap)}", fontSize = 13.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (adopted) Text("已採用，每月在檢討頁看進度", fontSize = 12.sp, color = IncomeColor, modifier = Modifier.weight(1f))
                else Text("採用後會寫進每月預算與目標", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (!adopted) TextButton(onClick = {
                    repo.settings.update { s ->
                        s.copy(
                            budgets = s.budgets + listOfNotNull(r.expenseCap?.let { 0L to it }) + capIds.associate { (id, c) -> id to c.cap },
                            savingsGoal = r.savingsTarget ?: s.savingsGoal,
                        )
                    }
                }) { Text("採用") }
                TextButton(onClick = { nav.navigate("budget") }) { Text("修改") }
            }
        }
    }
}
