package com.enwei.moneymanager.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Recurrence
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnSource
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.SaturdayColor
import com.enwei.moneymanager.ui.SundayColor
import com.enwei.moneymanager.ui.TransferColor
import com.enwei.moneymanager.ui.components.EmptyHint
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 期間內的收入、支出（換算基準幣，只計已確認）。 */
data class Totals(val income: Long, val expense: Long) {
    val total get() = income - expense
}

fun totalsOf(list: List<TxnEntity>, fx: FxConverter): Totals {
    var i = 0L
    var e = 0L
    list.forEach {
        if (it.status != TxnStatus.CONFIRMED) return@forEach
        when (it.type) {
            TxnType.INCOME -> i += fx.convert(it.amount, it.currency)
            TxnType.EXPENSE -> e += fx.convert(it.amount, it.currency)
            TxnType.TRANSFER -> Unit
        }
    }
    return Totals(i, e)
}

fun weekdayColor(d: DayOfWeek, default: Color): Color = when (d) {
    DayOfWeek.SATURDAY -> SaturdayColor
    DayOfWeek.SUNDAY -> SundayColor
    else -> default
}

private val subTabs = listOf("每日", "日曆", "每月", "總計")

@Composable
fun TransactionsTab(nav: NavController) {
    val lk = rememberLookups()
    var tab by rememberSaveable { mutableStateOf(0) }
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val pending by repo.pending.collectAsStateWithLifecycle(emptyList())
    val yearly = tab == 2

    val inPeriod = remember(all, month, yearly) {
        all.filter {
            val d = it.date.toLocalDate()
            if (yearly) d.year == month.year else YearMonth.from(d) == month
        }
    }
    val totals = totalsOf(inPeriod, lk.fx)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, "搜尋") }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { month = if (yearly) month.minusYears(1) else month.minusMonths(1) }) {
                            Icon(Icons.Filled.ChevronLeft, "上一期")
                        }
                        Text(
                            if (yearly) "${month.year} 年" else "${month.year} 年 ${month.monthValue} 月",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.clickable { month = YearMonth.now() },
                        )
                        IconButton(onClick = { month = if (yearly) month.plusYears(1) else month.plusMonths(1) }) {
                            Icon(Icons.Filled.ChevronRight, "下一期")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("pending") }) {
                        BadgedBox(badge = { if (pending.isNotEmpty()) Badge { Text("${pending.size}") } }) {
                            Icon(Icons.Filled.NotificationsActive, "待確認")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.navigate("txn/0") },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, "記一筆") }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.surface,
                indicator = { pos ->
                    TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), color = MaterialTheme.colorScheme.primary)
                },
            ) {
                subTabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SummaryBar(totals, lk.base)
            HorizontalDivider()
            when (tab) {
                0 -> DailyView(inPeriod, lk, nav)
                1 -> CalendarView(month, inPeriod, lk, nav)
                2 -> MonthlyView(month.year, inPeriod, lk)
                else -> SummaryView(inPeriod, lk, nav)
            }
        }
    }
}

@Composable
fun SummaryBar(t: Totals, base: String) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 10.dp)) {
        SummaryCell("收入", Money.format(t.income), IncomeColor, Modifier.weight(1f))
        SummaryCell("支出", Money.format(t.expense), ExpenseColor, Modifier.weight(1f))
        SummaryCell("合計", Money.format(t.total), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, color = color, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---------------- 每日 ----------------

@Composable
private fun DailyView(list: List<TxnEntity>, lk: Lookups, nav: NavController) {
    val days = remember(list) { list.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()) }
    if (days.isEmpty()) { EmptyHint("這個月還沒有記錄\n點右下角＋記一筆"); return }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        days.forEach { (day, txns) ->
            item(key = "d$day") { DayBlock(day, txns, lk, nav) }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
fun DayBlock(day: LocalDate, txns: List<TxnEntity>, lk: Lookups, nav: NavController) {
    val t = totalsOf(txns, lk.fx)
    Column(Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${day.dayOfMonth}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            val wdColor = weekdayColor(day.dayOfWeek, MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(color = wdColor, shape = RoundedCornerShape(4.dp)) {
                Text("週${Recurrence.weekdayName(day.dayOfWeek)}", color = Color.White, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
            }
            Spacer(Modifier.width(6.dp))
            Text("${day.year}.${"%02d".format(day.monthValue)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            if (t.income > 0) Text(Money.format(t.income), color = IncomeColor, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
            Text(if (t.expense > 0) Money.format(t.expense) else "", color = ExpenseColor, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        txns.sortedByDescending { it.date }.forEach { MmTxnRow(it, lk) { nav.navigate("txn/${it.id}") } }
    }
}

/** 一筆交易：左分類、中內容與帳戶、右金額。 */
@Composable
fun MmTxnRow(t: TxnEntity, lk: Lookups, onClick: () -> Unit) {
    val cat = lk.category(t.categoryId)
    val acc = lk.account(t.accountId)
    val (left, middle, sub) = when (t.type) {
        TxnType.TRANSFER -> Triple("轉帳", t.merchant?.takeIf { it.isNotBlank() } ?: "轉帳",
            "${acc?.name ?: "?"} → ${lk.account(t.toAccountId)?.name ?: "?"}")
        else -> Triple(cat?.let { "${it.icon} ${it.name}" } ?: "未分類",
            t.merchant?.takeIf { it.isNotBlank() } ?: t.note?.lineSequence()?.firstOrNull() ?: "", acc?.name ?: "")
    }
    val color = when (t.type) { TxnType.INCOME -> IncomeColor; TxnType.EXPENSE -> ExpenseColor; TxnType.TRANSFER -> TransferColor }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(left, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(middle, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false))
                if (t.status == TxnStatus.PENDING) Tag("待確認", MaterialTheme.colorScheme.primary)
                else if (t.source != TxnSource.MANUAL) Tag(t.source.label, MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.format(t.amount, if (t.currency == lk.base) null else t.currency), color = color,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (t.currency != lk.base && lk.fx.hasRate(t.currency)) Text(
                "≈" + Money.format(lk.fx.convert(t.amount, t.currency)), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Text(text, fontSize = 10.sp, color = color, modifier = Modifier.padding(start = 4.dp)
        .background(color.copy(alpha = 0.12f), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp))
}

// ---------------- 日曆 ----------------

@Composable
private fun CalendarView(month: YearMonth, list: List<TxnEntity>, lk: Lookups, nav: NavController) {
    var selected by rememberSaveable(month) { mutableStateOf(if (YearMonth.now() == month) LocalDate.now() else month.atDay(1)) }
    val byDay = remember(list) { list.groupBy { it.date.toLocalDate() } }
    val first = month.atDay(1)
    val start = first.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    val weeks = ((month.lengthOfMonth() + (first.dayOfWeek.value % 7)) + 6) / 7

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEachIndexed { i, w ->
                        Text(w, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium,
                            color = when (i) { 0 -> SundayColor; 6 -> SaturdayColor; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                    }
                }
                for (w in 0 until weeks) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().height(62.dp)) {
                        for (d in 0 until 7) {
                            val date = start.plusDays((w * 7 + d).toLong())
                            val inMonth = YearMonth.from(date) == month
                            val t = totalsOf(byDay[date].orEmpty(), lk.fx)
                            val bg = when {
                                date == selected -> MaterialTheme.colorScheme.primaryContainer
                                date == LocalDate.now() -> MaterialTheme.colorScheme.surfaceVariant
                                else -> Color.Transparent
                            }
                            Column(
                                Modifier.weight(1f).fillMaxSize().background(bg).clickable(enabled = inMonth) { selected = date }
                                    .padding(horizontal = 3.dp, vertical = 2.dp),
                            ) {
                                Text("${date.dayOfMonth}", fontSize = 12.sp,
                                    color = if (!inMonth) MaterialTheme.colorScheme.outlineVariant
                                    else weekdayColor(date.dayOfWeek, MaterialTheme.colorScheme.onSurface))
                                if (inMonth) {
                                    Spacer(Modifier.weight(1f))
                                    if (t.income > 0) Text(Money.format(t.income), fontSize = 9.sp, color = IncomeColor,
                                        maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                                    if (t.expense > 0) Text(Money.format(t.expense), fontSize = 9.sp, color = ExpenseColor,
                                        maxLines = 1, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                                }
                            }
                        }
                    }
                }
            }
        }
        val dayTxns = byDay[selected].orEmpty()
        item {
            if (dayTxns.isEmpty()) Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("${selected.monthValue}/${selected.dayOfMonth} 沒有交易", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else DayBlock(selected, dayTxns, lk, nav)
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

// ---------------- 每月 ----------------

@Composable
private fun MonthlyView(year: Int, list: List<TxnEntity>, lk: Lookups) {
    var expanded by rememberSaveable { mutableStateOf<Int?>(null) }
    val now = YearMonth.now()
    val months = (12 downTo 1).map { YearMonth.of(year, it) }.filter { !it.isAfter(now) || list.any { t -> YearMonth.from(t.date.toLocalDate()) == it } }
    val byMonth = remember(list) { list.groupBy { YearMonth.from(it.date.toLocalDate()) } }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (months.isEmpty()) item { EmptyHint("這一年沒有資料") }
        items(months, key = { it.monthValue }) { m ->
            val txns = byMonth[m].orEmpty()
            val t = totalsOf(txns, lk.fx)
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = if (expanded == m.monthValue) null else m.monthValue }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.monthValue} 月", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${m.monthValue}/1 ~ ${m.monthValue}/${m.lengthOfMonth()}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row {
                            Text(Money.format(t.income), color = IncomeColor, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
                            Text(Money.format(t.expense), color = ExpenseColor, style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
                        }
                        Text("合計 ${Money.format(t.total)}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(if (expanded == m.monthValue) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (expanded == m.monthValue) {
                    weeksOf(m).forEach { (s, e) ->
                        val wt = totalsOf(txns.filter { val d = it.date.toLocalDate(); !d.isBefore(s) && !d.isAfter(e) }, lk.fx)
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                        ) {
                            Text("${s.monthValue}/${s.dayOfMonth} ~ ${e.monthValue}/${e.dayOfMonth}", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            Text(Money.format(wt.income), color = IncomeColor, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
                            Text(Money.format(wt.expense), color = ExpenseColor, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.width(92.dp), textAlign = TextAlign.End)
                            Spacer(Modifier.width(24.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

/** 月份切成週（週日～週六），頭尾截在月份範圍內。 */
private fun weeksOf(m: YearMonth): List<Pair<LocalDate, LocalDate>> {
    val out = mutableListOf<Pair<LocalDate, LocalDate>>()
    var s = m.atDay(1)
    while (!s.isAfter(m.atEndOfMonth())) {
        val e = minOf(s.with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)), m.atEndOfMonth())
        out += s to e
        s = e.plusDays(1)
    }
    return out.reversed()
}

// ---------------- 總計 ----------------

@Composable
private fun SummaryView(list: List<TxnEntity>, lk: Lookups, nav: NavController) {
    val confirmed = list.filter { it.status == TxnStatus.CONFIRMED }
    val t = totalsOf(confirmed, lk.fx)
    val byAccount = confirmed.filter { it.type == TxnType.EXPENSE }.groupBy { it.accountId }
        .map { (id, l) -> id to l.sumOf { lk.fx.convert(it.amount, it.currency) } }.sortedByDescending { it.second }
    val topCats = confirmed.filter { it.type == TxnType.EXPENSE }.groupBy { it.categoryId }
        .map { (id, l) -> id to l.sumOf { lk.fx.convert(it.amount, it.currency) } }.sortedByDescending { it.second }.take(5)
    val days = list.map { it.date.toLocalDate() }.distinct().size

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp)) {
        item {
            SummaryCard("收支") {
                KV("收入", Money.format(t.income, lk.base), IncomeColor)
                KV("支出", Money.format(t.expense, lk.base), ExpenseColor)
                KV("結餘", Money.format(t.total, lk.base))
                if (t.income > 0) KV("儲蓄率", String.format("%.1f%%", t.total * 100.0 / t.income))
                KV("記帳天數／筆數", "$days 天／${list.size} 筆")
            }
        }
        item {
            SummaryCard("各帳戶支出") {
                if (byAccount.isEmpty()) Text("無", color = MaterialTheme.colorScheme.onSurfaceVariant)
                byAccount.forEach { (id, v) -> KV(lk.account(id)?.let { "${it.type.icon} ${it.name}" } ?: "未指定帳戶", Money.format(v, lk.base)) }
            }
        }
        item {
            SummaryCard("支出分類前五名", onClick = { nav.navigate("stats") }) {
                if (topCats.isEmpty()) Text("無", color = MaterialTheme.colorScheme.onSurfaceVariant)
                topCats.forEach { (id, v) ->
                    val c = lk.category(id)
                    KV("${c?.icon ?: "❔"} ${c?.name ?: "未分類"}", Money.format(v, lk.base) +
                        if (t.expense > 0) "（${v * 100 / t.expense}%）" else "")
                }
            }
        }
        item { Spacer(Modifier.height(88.dp)) }
    }
}

@Composable
private fun SummaryCard(title: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun KV(k: String, v: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(v, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}
