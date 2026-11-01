package com.enwei.moneymanager.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Periods
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.Text
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 搜尋的圖表勾選篩選：期間（月份或全部）＋勾選的收入分類、支出分類、帳戶。
 * 分類以大分類比對（子分類算在大分類裡）；不同分頁的條件同時成立才算符合。
 */
data class ChartFilter(
    val month: YearMonth? = null,
    val incomeCats: Set<Long> = emptySet(),
    val expenseCats: Set<Long> = emptySet(),
    val accounts: Set<Long> = emptySet(),
) {
    val active: Boolean get() = incomeCats.isNotEmpty() || expenseCats.isNotEmpty() || accounts.isNotEmpty()

    fun range(startDay: Int): Pair<LocalDate, LocalDate>? = month?.let { Periods.month(it, startDay) }

    fun inPeriod(t: TxnEntity, startDay: Int): Boolean {
        val r = range(startDay) ?: return true
        val d = java.time.Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate()
        return !d.isBefore(r.first) && !d.isAfter(r.second)
    }

    /** 符合勾選的分類與帳戶（不含期間）。 */
    fun matches(t: TxnEntity, lk: Lookups): Boolean {
        val cats = incomeCats + expenseCats
        val catOk = cats.isEmpty() || (t.type != TxnType.TRANSFER && (lk.rootId(t.categoryId) ?: 0L) in cats)
        val accOk = accounts.isEmpty() || t.accountId in accounts || t.toAccountId in accounts
        return catOk && accOk
    }

    /** 底部列顯示的已選項目名稱。 */
    fun label(lk: Lookups): String = (
        incomeCats.map { lk.category(it)?.name ?: "未分類" } + expenseCats.map { lk.category(it)?.name ?: "未分類" } +
            accounts.mapNotNull { lk.account(it)?.name }
        ).joinToString("、")
}

/**
 * 篩選頁：上方收入、支出兩個圓環（勾選項目占該期收入／支出的比例，從 0 轉到該有的位置），
 * 下方「收入、支出、帳戶」三個分頁勾選；按「篩選」套用。
 */
@Composable
fun ChartFilterPage(
    lk: Lookups, all: List<TxnEntity>, startDay: Int, initial: ChartFilter,
    onApply: (ChartFilter) -> Unit, onClose: () -> Unit,
) {
    var month by remember { mutableStateOf(initial.month) }
    var incomeSel by remember { mutableStateOf(initial.incomeCats) }
    var expenseSel by remember { mutableStateOf(initial.expenseCats) }
    var accSel by remember { mutableStateOf(initial.accounts) }
    var tab by remember { mutableStateOf(if (initial.accounts.isNotEmpty() && initial.incomeCats.isEmpty() && initial.expenseCats.isEmpty()) 2 else 1) }
    val draft = ChartFilter(month, incomeSel, expenseSel, accSel)
    val inPeriod = remember(all, month, startDay) {
        all.filter { it.status == TxnStatus.CONFIRMED && ChartFilter(month).inPeriod(it, startDay) }
    }
    fun conv(t: TxnEntity) = lk.fx.convert(t.amount, t.currency)
    val totalIncome = inPeriod.filter { it.type == TxnType.INCOME }.sumOf(::conv)
    val totalExpense = inPeriod.filter { it.type == TxnType.EXPENSE }.sumOf(::conv)
    val matched = inPeriod.filter { draft.active && draft.matches(it, lk) }
    val selIncome = matched.filter { it.type == TxnType.INCOME }.sumOf(::conv)
    val selExpense = matched.filter { it.type == TxnType.EXPENSE }.sumOf(::conv)
    val gray = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        // 期間：月份切換；點標題切換「全部期間」
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).height(52.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(enabled = month != null, onClick = { month = month?.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
            Text(
                month?.let { "${it.year}年${it.monthValue}月" } ?: "全部期間", fontSize = 17.sp,
                modifier = Modifier.clickable { month = if (month == null) Periods.monthOf(LocalDate.now(), startDay) else null },
            )
            IconButton(enabled = month != null, onClick = { month = month?.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "關閉") }
        }
        // 已選項目＋「篩選」按鈕
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).padding(start = 16.dp, end = 8.dp).height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (draft.active) draft.label(lk) else "勾選要篩選的項目", fontSize = 14.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            if (draft.active) Surface(
                shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).clickable { onApply(draft) },
            ) { Text("篩選", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) }
        }
        // 收入、支出圓環與合計
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Donut("收入", selIncome, totalIncome, IncomeColor, Modifier.weight(1f))
            Donut("支出", selExpense, totalExpense, ExpenseColor, Modifier.weight(1f))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("合計", fontSize = 13.sp)
                Text(
                    Money.withSymbol(if (draft.active) selIncome - selExpense else totalIncome - totalExpense, lk.base),
                    fontSize = 15.sp, maxLines = 1,
                )
            }
        }
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
            listOf("收入" to incomeSel, "支出" to expenseSel, "帳戶" to accSel).forEachIndexed { i, (t, sel) ->
                Tab(tab == i, { tab = i }, text = { Text(if (sel.isEmpty()) t else "$t •", fontSize = 14.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = gray)
            }
        }
        when (tab) {
            0, 1 -> {
                val type = if (tab == 0) TxnType.INCOME else TxnType.EXPENSE
                val sel = if (tab == 0) incomeSel else expenseSel
                fun set(v: Set<Long>) { if (tab == 0) incomeSel = v else expenseSel = v }
                // 大分類（子分類合計進來），依金額排序；沒有分類的交易算「未分類」
                val byCat = inPeriod.filter { it.type == type }.groupBy { lk.rootId(it.categoryId) ?: 0L }
                    .mapValues { (_, l) -> l.sumOf(::conv) }.entries.sortedByDescending { it.value }
                val ids = (byCat.map { it.key } + lk.categories.filter { it.kind == type && it.parentId == null && !it.archived }.map { it.id }).distinct()
                val amounts = byCat.associate { it.key to it.value }
                FilterHeader(sel.size == ids.size && ids.isNotEmpty(), { set(if (it) ids.toSet() else emptySet()) }) {
                    Text(type.label, fontSize = 12.sp, color = if (type == TxnType.INCOME) IncomeColor else ExpenseColor)
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ids, key = { "c$it" }) { id ->
                        FilterRow(lk.category(id)?.name ?: "未分類", id in sel, { set(if (it) sel + id else sel - id) }) {
                            Text(amounts[id]?.let { Money.withSymbol(it, lk.base) } ?: "", fontSize = 13.sp,
                                color = if (type == TxnType.INCOME) IncomeColor else ExpenseColor)
                        }
                    }
                }
            }
            else -> {
                val accs = lk.accounts.filter { !it.archived }
                val ids = accs.map { it.id }
                FilterHeader(accSel.size == ids.size && ids.isNotEmpty(), { accSel = if (it) ids.toSet() else emptySet() }) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("收入／轉入", fontSize = 11.sp, color = IncomeColor)
                        Text("支出／轉出", fontSize = 11.sp, color = ExpenseColor)
                    }
                }
                LazyColumn(Modifier.fillMaxSize()) {
                    AccountType.entries.forEach { type ->
                        val group = accs.filter { it.type == type }
                        if (group.isEmpty()) return@forEach
                        item(key = "g$type") {
                            Text(type.label, fontSize = 12.sp, color = gray,
                                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 16.dp, top = 10.dp, bottom = 4.dp))
                        }
                        items(group, key = { "a${it.id}" }) { a ->
                            val inc = inPeriod.filter { it.type == TxnType.INCOME && it.accountId == a.id }.sumOf(::conv)
                            val exp = inPeriod.filter { it.type == TxnType.EXPENSE && it.accountId == a.id }.sumOf(::conv)
                            val tIn = inPeriod.filter { it.type == TxnType.TRANSFER && it.toAccountId == a.id }
                                .sumOf { t -> lk.fx.convert(t.toAmount ?: t.amount, lk.account(t.toAccountId)?.currency ?: t.currency) }
                            val tOut = inPeriod.filter { it.type == TxnType.TRANSFER && it.accountId == a.id }.sumOf(::conv)
                            FilterRow(a.name, a.id in accSel, { accSel = if (it) accSel + a.id else accSel - a.id }) {
                                if (inc + exp + tIn + tOut > 0) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Money.withSymbol(inc, lk.base), fontSize = 13.sp, color = IncomeColor)
                                        Text(Money.withSymbol(tIn, lk.base), fontSize = 11.sp, color = gray)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(Money.withSymbol(exp, lk.base), fontSize = 13.sp, color = ExpenseColor)
                                        Text(Money.withSymbol(tOut, lk.base), fontSize = 11.sp, color = gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 分頁上方的「全部」勾選列，右邊放欄位說明。 */
@Composable
private fun FilterHeader(allOn: Boolean, onAll: (Boolean) -> Unit, right: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onAll(!allOn) }.padding(end = 16.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(allOn, onAll, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Text("全部", fontSize = 15.sp, modifier = Modifier.weight(1f))
        right()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** 一列勾選項目：勾選框、名稱、右邊金額。 */
@Composable
private fun FilterRow(name: String, checked: Boolean, onCheck: (Boolean) -> Unit, right: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { onCheck(!checked) }.padding(end = 16.dp).height(46.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked, onCheck, colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary))
        Text(name, fontSize = 15.sp, maxLines = 1, modifier = Modifier.weight(1f))
        right()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

/**
 * 圓環：勾選項目占總額的比例。第一次出現從 0 度轉到該有的位置，之後勾選改變時從目前位置轉到新的比例。
 */
@Composable
fun Donut(label: String, part: Long, total: Long, color: Color, modifier: Modifier = Modifier) {
    val target = if (total > 0) (part.toFloat() / total).coerceIn(0f, 1f) else 0f
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(700, easing = FastOutSlowInEasing)) }
    val track = color.copy(alpha = 0.22f)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 13.sp)
        Box(Modifier.padding(vertical = 6.dp).size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 9.dp.toPx()
                val d = size.minDimension - w
                val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(w))
                drawArc(color, -90f, 360f * anim.value, false, topLeft, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("${(anim.value * 100).toInt()}%", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(Money.full(if (part > 0) part else total), fontSize = 13.sp, maxLines = 1)
        if (part > 0) Text(Money.full(total), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
