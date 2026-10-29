package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.Modifier
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
import com.enwei.moneymanager.data.aiInput
import com.enwei.moneymanager.data.habits
import com.enwei.moneymanager.data.lateNight
import com.enwei.moneymanager.data.merchants
import androidx.compose.foundation.clickable
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

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
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
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
            // 交易頁上方已顯示期間；季、年再補上實際日期範圍
            if (period == null) Text(d.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            else if (span != ReviewSpan.MONTH) Text(d.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // ① 總覽
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Stat("收入", m(d.income), IncomeColor, Modifier.weight(1f))
                        Stat("支出", m(d.expense), ExpenseColor, Modifier.weight(1f))
                        Stat("結餘", m(d.net), if (d.net >= 0) IncomeColor else ExpenseColor, Modifier.weight(1f))
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

            // ② 支出結構
            if (d.categories.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("支出結構", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    val maxV = d.categories.maxOf { it.amount }.coerceAtLeast(1)
                    d.categories.take(8).forEach { c ->
                        Column {
                            Row {
                                Text(c.name, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Text(m(c.amount), fontSize = 14.sp)
                                Text(
                                    if (c.prev == 0L) "  新" else "  ${if (c.change >= 0) "▲" else "▼"}${"%.0f".format(kotlin.math.abs(c.change) * 100.0 / c.prev)}%",
                                    fontSize = 12.sp, color = if (c.change > 0) ExpenseColor else IncomeColor, modifier = Modifier.width(56.dp),
                                )
                            }
                            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                                Box(Modifier.fillMaxWidth(c.amount.toFloat() / maxV).height(6.dp).background(ExpenseColor.copy(alpha = 0.75f), RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
            }

            // ③ 重點觀察（規則）
            val obs = d.observations(base) + buildList {
                d.merchants().take(3).takeIf { it.isNotEmpty() }?.let { ms -> add("花最多的商家：" + ms.joinToString("、") { "${it.name.take(10)} ${it.count} 次 ${m(it.total)}" }) }
                d.habits().take(3).forEach { add("${it.name.take(10)} 這期 ${it.count} 次、共 ${m(it.total)}，小額但累積起來不少") }
                d.lateNight().takeIf { it.size >= 3 }?.let { add("深夜（22 點後）消費 ${it.size} 筆、共 ${m(it.sumOf { t -> t.amount })}") }
                d.lastYearExpense?.let { ly -> add("去年同期支出 ${m(ly)}，今年 ${if (d.expense >= ly) "多" else "少"} ${m(kotlin.math.abs(d.expense - ly))}") }
                val saved = d.expenses.filter { com.enwei.moneymanager.data.necessityOf(settings.txnNecessity[it.id])?.first == com.enwei.moneymanager.data.Necessity.SAVE }
                if (saved.isNotEmpty()) add("你標為「可省」的 ${saved.size} 筆共 ${m(saved.sumOf { it.amount })}")
            }
            if (obs.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("重點觀察", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    obs.forEach { Text("• $it", fontSize = 14.sp) }
                }
            }

            // ④ AI 檢討（固定格式）
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AI 檢討與建議", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        val cached = settings.reviews[d.key]
                        if (GroqAi.enabled) OutlinedButton(enabled = !busy && d.txnCount > 0, onClick = {
                            busy = true; err = null
                            scope.launch {
                                val (input, list) = d.aiInput(base, settings.txnNecessity, GroqAi.bigContext(GroqAi.AiTask.REVIEW))
                                val r = GroqAi.review(input, base, list.map { it.id })
                                if (r != null) repo.settings.update { it.copy(reviews = (it.reviews + (d.key to r)).entries.toList().takeLast(40).associate { e -> e.key to e.value }) }
                                else err = "AI 連線失敗：${GroqAi.lastError ?: "沒有回應"}"
                                busy = false
                            }
                        }) { Text(if (busy) "產生中…" else if (cached == null) "產生" else "重新產生") }
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    err?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    val r = settings.reviews[d.key]
                    when {
                        r != null -> AiReviewBody(r, ::m, d, lk, settings.txnNecessity, nav)
                        !GroqAi.enabled -> Text("到「設定 → AI 智慧辨識」填入金鑰後，可以產生 AI 檢討。", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else -> Text("按「產生」寫出這期的檢討與下期目標", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ⑤ 最大幾筆
            if (d.top.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 12.dp)) {
                    Text("最大幾筆支出", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
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
        Text(value, fontSize = 16.sp, color = color, maxLines = 1)
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
            Text("${r.score}", fontSize = 20.sp, color = c, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Text(r.headline, fontSize = 15.sp, modifier = Modifier.weight(1f))
    }
    @Composable
    fun section(title: String, items: List<String>, mark: String) {
        if (items.isEmpty()) return
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        items.forEach { Text("$mark $it", fontSize = 14.sp) }
    }
    if (r.analysis.isNotEmpty()) {
        Text("多角度分析", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        r.analysis.forEach { a ->
            Column {
                Text(a.title, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text(a.text, fontSize = 14.sp)
            }
        }
    }
    section("做得好", r.good, "✓")
    if (r.questions.isNotEmpty()) {
        Text("想問你的幾筆", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("回答會記住，下次檢討時會參考", fontSize = 12.sp, color = gray)
        val byId = d.expenses.associateBy { it.id }
        r.questions.forEach { q -> QuestionCard(q, q.txnIds.mapNotNull { byId[it] }, lk, necessity, m, nav) }
    }
    section("需要改善", r.improve, "!")
    if (r.actions.isNotEmpty()) {
        Text("行動建議", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        r.actions.forEachIndexed { i, a ->
            Row {
                Text("${i + 1}. ${a.action}", fontSize = 14.sp, modifier = Modifier.weight(1f))
                a.saving?.let { Text("每月省 ${m(it)}", fontSize = 13.sp, color = IncomeColor) }
            }
        }
    }
    if (r.expenseCap != null || r.savingsTarget != null || r.goals.isNotEmpty()) {
        Text("下期目標", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        listOfNotNull(
            r.expenseCap?.let { "支出上限 ${m(it)}" },
            r.savingsTarget?.let { "儲蓄率 ${"%.0f".format(it)}% 以上" },
        ).forEach { Text("◎ $it", fontSize = 14.sp) }
        r.goals.forEach { Text("◎ $it", fontSize = 14.sp) }
    }
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
