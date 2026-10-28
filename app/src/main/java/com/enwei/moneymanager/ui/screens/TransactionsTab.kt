package com.enwei.moneymanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Periods
import com.enwei.moneymanager.core.Recurrence
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.CardStatus
import com.enwei.moneymanager.data.Memo
import com.enwei.moneymanager.data.RecurringEntity
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnSource
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.data.toMillis
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.SaturdayColor
import com.enwei.moneymanager.ui.SundayColor
import com.enwei.moneymanager.ui.TransferColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

private fun inRange(d: LocalDate, r: Pair<LocalDate, LocalDate>) = !d.isBefore(r.first) && !d.isAfter(r.second)

private val subTabs = listOf("每日", "日曆", "每月", "總計", "備註")
private val NavyHighlight = Color(0xFF3F4570)

@Composable
fun TransactionsTab(nav: NavController, initialTab: Int = 0) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var month by rememberSaveable { mutableStateOf(Periods.monthOf(LocalDate.now(), repo.settings.value.monthStartDay)) }
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val pending by repo.pending.collectAsStateWithLifecycle(emptyList())
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    val recurring by repo.recurring.collectAsStateWithLifecycle(emptyList())
    var memoEditing by remember { mutableStateOf<Memo?>(null) }
    val yearly = tab == 2
    val startDay = settings.monthStartDay

    val range = if (yearly) Periods.month(YearMonth.of(month.year, 1), startDay).first to Periods.month(YearMonth.of(month.year, 12), startDay).second
    else Periods.month(month, startDay)
    val inPeriod = remember(all, range) { all.filter { inRange(it.date.toLocalDate(), range) } }

    // 上期結轉：期初的淨額（計入總額的帳戶）
    val carry = if (settings.carryOver && tab != 3) {
        val cutoff = range.first.toMillis(LocalTime.MIDNIGHT)
        balances.filter { !it.account.archived && it.account.includeInNetWorth }
            .sumOf { lk.fx.convert(balanceBefore(it.balance, it.account.id, cutoff, all, trades, lk.fx, it.account.currency), it.account.currency) }
    } else 0L
    val base = totalsOf(inPeriod, lk.fx)
    val totals = Totals(base.income + maxOf(0, carry), base.expense + maxOf(0, -carry))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
                        IconButton(onClick = { month = if (yearly) month.minusYears(1) else month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                        Text(
                            if (yearly) "${month.year}" else "${month.year}年${month.monthValue}月",
                            fontSize = 18.sp, modifier = Modifier.clickable { month = Periods.monthOf(LocalDate.now(), startDay) },
                        )
                        IconButton(onClick = { month = if (yearly) month.plusYears(1) else month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) IconButton(onClick = { nav.navigate("pending") }) {
                        BadgedBox(badge = { Badge { Text("${pending.size}") } }) { Icon(Icons.Filled.NotificationsActive, "待確認") }
                    }
                    IconButton(onClick = { nav.navigate("bookmarks") }) { Icon(Icons.Outlined.StarBorder, "書籤") }
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Outlined.Search, "搜尋") }
                    IconButton(onClick = { nav.navigate("search?filter=true") }) { Icon(Icons.Outlined.Tune, "篩選") }
                },
            )
        },
        floatingActionButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if ((tab == 0 && settings.noteButton) || tab == 4) {
                    Surface(
                        shape = CircleShape, color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(60.dp).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { memoEditing = Memo(0, LocalDate.now().toMillis(), "") },
                    ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.EditNote, "新增備註", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) } }
                    Spacer(Modifier.width(16.dp))
                }
                if (tab != 4) FloatingActionButton(
                    onClick = { nav.navigate("txn/0") }, shape = CircleShape, modifier = Modifier.size(64.dp),
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White,
                ) { Icon(Icons.Filled.Add, "記一筆", modifier = Modifier.size(32.dp)) }
            }
        },
    ) { pad ->
        var dragX by remember { mutableStateOf(0f) }
        val swipeModifier = if (settings.swipe == 1) Modifier.pointerInput(yearly) {
            detectHorizontalDragGestures(
                onDragStart = { dragX = 0f },
                onDragEnd = {
                    if (dragX > 160f) month = if (yearly) month.minusYears(1) else month.minusMonths(1)
                    else if (dragX < -160f) month = if (yearly) month.plusYears(1) else month.plusMonths(1)
                    dragX = 0f
                },
            ) { _, d -> dragX += d }
        } else Modifier
        Column(Modifier.padding(pad).fillMaxSize().then(swipeModifier)) {
            TabRow(
                selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
                indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
            ) {
                subTabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t, fontSize = 14.sp, maxLines = 1) },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tab != 4) SummaryBar(if (tab == 3) base else totals, lk.base)
            when (tab) {
                0 -> DailyView(inPeriod, lk, nav, range, carry, recurring)
                1 -> CalendarView(range, all, lk, nav, DayOfWeek.of(settings.weekStartDay), carry)
                2 -> MonthlyView(month.year, all, lk, startDay, DayOfWeek.of(settings.weekStartDay))
                3 -> TotalView(inPeriod, all, lk, nav, month, range)
                else -> MemoView(settings.memos.filter { inRange(it.date.toLocalDate(), range) }) { memoEditing = it }
            }
        }
    }
    memoEditing?.let { m -> MemoDialog(m) { memoEditing = null } }
}

@Composable
fun SummaryBar(t: Totals, base: String) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            SummaryCell("收入", Money.full(t.income), IncomeColor, Modifier.weight(1f))
            SummaryCell("支出", Money.full(t.expense), ExpenseColor, Modifier.weight(1f))
            SummaryCell("合計", Money.full(t.total), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = color, fontSize = 17.sp, maxLines = 1)
    }
}

// ---------------- 每日 ----------------

@Composable
private fun DailyView(
    list: List<TxnEntity>, lk: Lookups, nav: NavController, range: Pair<LocalDate, LocalDate>, carry: Long, recurring: List<RecurringEntity>,
) {
    val days = remember(list) { list.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()) }
    val today = LocalDate.now()
    // 本期之後還會發生的週期交易
    val upcoming = recurring.filter { it.enabled }.mapNotNull { r ->
        val next = repo.nextOccurrence(r, today)
        if (inRange(next, range) && !next.isBefore(today)) Triple(r, next, if (r.type == TxnType.EXPENSE) -r.amount else r.amount) else null
    }.sortedBy { it.second }
    LazyColumn(Modifier.fillMaxSize()) {
        if (upcoming.isNotEmpty()) item {
            Column(Modifier.background(MaterialTheme.colorScheme.surface).clickable { nav.navigate("recurring") }) {
                upcoming.forEach { (r, d, signed) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                        Text("週期", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                        Text("${d.monthValue}/${d.dayOfMonth}  ${r.name}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(Money.withSymbol(signed, r.currency))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (days.isEmpty() && carry == 0L) item { NoData() }
        days.forEach { (day, txns) -> item(key = "d$day") { DayBlock(day, txns, lk, nav) } }
        if (carry != 0L) item {
            Column(Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("${range.first.monthValue}/${range.first.dayOfMonth}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
                    Text("上期結轉", Modifier.weight(1f))
                    Text(Money.withSymbol(carry, lk.base), color = if (carry >= 0) IncomeColor else ExpenseColor)
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
fun NoData(text: String = "沒有資料") {
    Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Pets, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(72.dp))
            Icon(Icons.Outlined.Chat, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(28.dp))
        }
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun DayBlock(day: LocalDate, txns: List<TxnEntity>, lk: Lookups, nav: NavController, balances: Map<Long, Long> = emptyMap()) {
    val t = totalsOf(txns, lk.fx)
    Column(Modifier.padding(top = 10.dp).background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${day.dayOfMonth}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            val chip = when (day.dayOfWeek) { DayOfWeek.SUNDAY -> SundayColor; DayOfWeek.SATURDAY -> SaturdayColor; else -> Color(0xFF8C8C8C) }
            Surface(color = chip, shape = RoundedCornerShape(3.dp)) {
                Text("週${Recurrence.weekdayName(day.dayOfWeek)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text("${"%02d".format(day.monthValue)}.${day.year}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(Money.withSymbol(t.income, lk.base), color = IncomeColor, fontSize = 15.sp, modifier = Modifier.width(112.dp), textAlign = TextAlign.End)
            Text(Money.withSymbol(t.expense, lk.base), color = ExpenseColor, fontSize = 15.sp, modifier = Modifier.width(112.dp), textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        txns.sortedByDescending { it.date }.forEach { MmTxnRow(it, lk, balances[it.id]) { nav.navigate("txn/${it.id}") } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 一筆交易：左分類、中內容與帳戶、右金額。 */
@Composable
fun MmTxnRow(t: TxnEntity, lk: Lookups, balanceAfter: Long? = null, onClick: () -> Unit) {
    val cat = lk.category(t.categoryId)
    val acc = lk.account(t.accountId)
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    val showDesc = repo.settings.flow.collectAsStateWithLifecycle().value.showDescription
    val color = when (t.type) { TxnType.INCOME -> IncomeColor; TxnType.EXPENSE -> ExpenseColor; TxnType.TRANSFER -> MaterialTheme.colorScheme.onSurface }
    val title = t.merchant?.takeIf { it.isNotBlank() }
    val accountText = if (t.type == TxnType.TRANSFER) "${acc?.name ?: "?"} → ${lk.account(t.toAccountId)?.name ?: "?"}" else acc?.name.orEmpty()
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(96.dp)) {
            val parent = cat?.parentId?.let { lk.category(it) }
            Text(if (t.type == TxnType.TRANSFER) "轉帳" else (parent ?: cat)?.name ?: "未分類", color = gray, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (parent != null && t.type != TxnType.TRANSFER) Text(cat!!.name, color = gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                t.status == TxnStatus.PENDING -> "待確認"
                t.source != TxnSource.MANUAL && t.source != TxnSource.IMPORT -> t.source.label
                else -> null
            }
            if (sub != null) Text(sub, color = if (t.status == TxnStatus.PENDING) MaterialTheme.colorScheme.primary else gray, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(accountText, color = gray, fontSize = if (title != null) 14.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showDesc && !t.note.isNullOrBlank()) Text(t.note, color = gray, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.withSymbol(t.amount, t.currency), color = color, fontSize = 16.sp)
            if (balanceAfter != null) Text("(${Money.full(balanceAfter)})", fontSize = 12.sp, color = gray)
            else if (t.currency != lk.base && lk.fx.hasRate(t.currency)) Text("≈" + Money.full(lk.fx.convert(t.amount, t.currency)), fontSize = 12.sp, color = gray)
        }
    }
}

// ---------------- 日曆 ----------------

@Composable
private fun CalendarView(
    range: Pair<LocalDate, LocalDate>, all: List<TxnEntity>, lk: Lookups, nav: NavController, weekStart: DayOfWeek, carry: Long,
) {
    var sheetDay by remember { mutableStateOf<LocalDate?>(null) }
    val byDay = remember(all) { all.groupBy { it.date.toLocalDate() } }
    val today = LocalDate.now()
    val start = range.first.with(TemporalAdjusters.previousOrSame(weekStart))
    val end = range.second.with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
    val weeks = ((ChronoUnit.DAYS.between(start, end) + 1) / 7).toInt()
    val headers = (0 until 7).map { weekStart.plus(it.toLong()) }
    val gray = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp)) {
            headers.forEach { d ->
                Text("週${Recurrence.weekdayName(d)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, color = weekdayColor(d, gray))
            }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().height(104.dp)) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val inMonth = inRange(date, range)
                    val t0 = totalsOf(byDay[date].orEmpty(), lk.fx)
                    val t = if (date == range.first && carry != 0L) Totals(t0.income + maxOf(0, carry), t0.expense + maxOf(0, -carry)) else t0
                    val alpha = if (inMonth) 1f else 0.45f
                    Column(
                        Modifier.weight(1f).fillMaxSize()
                            .background(if (inMonth) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .clickable { sheetDay = date },
                    ) {
                        val label = if (date.dayOfMonth == 1) "${date.monthValue}.1" else "${date.dayOfMonth}"
                        val isToday = date == today
                        Text(
                            label, fontSize = 13.sp,
                            color = if (isToday) Color.White else weekdayColor(date.dayOfWeek, MaterialTheme.colorScheme.onSurface).copy(alpha = alpha),
                            modifier = Modifier.fillMaxWidth().background(if (isToday) NavyHighlight else Color.Transparent).padding(horizontal = 4.dp, vertical = 2.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        val lines = listOf(
                            (if (t.income > 0) Money.full(t.income) else "") to IncomeColor,
                            (if (t.expense > 0) Money.full(t.expense) else "") to ExpenseColor,
                            (if (t.income > 0 && t.expense > 0) Money.full(t.total) else "") to MaterialTheme.colorScheme.onSurface,
                        )
                        lines.forEach { (text, c) ->
                            Text(text, fontSize = 10.sp, color = c.copy(alpha = alpha), maxLines = 1, textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth().padding(end = 3.dp, bottom = 3.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
    sheetDay?.let { day ->
        ModalBottomSheet(onDismissRequest = { sheetDay = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${day.year}/${day.monthValue}/${day.dayOfMonth}（${Recurrence.weekdayName(day.dayOfWeek)}）", Modifier.weight(1f), fontSize = 18.sp)
                TextButton(onClick = { sheetDay = null; nav.navigate("txn/0?date=${day.toEpochDay()}") }) { Text("＋ 記一筆") }
            }
            val txns = byDay[day].orEmpty()
            if (txns.isEmpty()) Text("沒有交易", color = gray, modifier = Modifier.padding(20.dp))
            else Column(Modifier.verticalScroll(rememberScrollState())) {
                txns.sortedByDescending { it.date }.forEach { t -> MmTxnRow(t, lk) { sheetDay = null; nav.navigate("txn/${t.id}") } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------------- 每月 ----------------

@Composable
private fun MonthlyView(year: Int, all: List<TxnEntity>, lk: Lookups, startDay: Int, weekStart: DayOfWeek) {
    val now = Periods.monthOf(LocalDate.now(), startDay)
    var expanded by rememberSaveable(year) { mutableStateOf(if (now.year == year) now.monthValue else null) }
    val months = (12 downTo 1).map { YearMonth.of(year, it) }.filter { !it.isAfter(now) }
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (months.isEmpty()) item { NoData() }
        items(months, key = { it.monthValue }) { m ->
            val r = Periods.month(m, startDay)
            val txns = all.filter { inRange(it.date.toLocalDate(), r) }
            val t = totalsOf(txns, lk.fx)
            Column {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = if (expanded == m.monthValue) null else m.monthValue }
                        .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${m.monthValue}月", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        if (expanded == m.monthValue) Text(
                            "%02d.%02d ~ %02d.%02d".format(r.first.monthValue, r.first.dayOfMonth, r.second.monthValue, r.second.dayOfMonth),
                            fontSize = 13.sp, color = gray,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Row {
                            Text(Money.withSymbol(t.income, lk.base), color = IncomeColor, fontSize = 16.sp, modifier = Modifier.width(124.dp), textAlign = TextAlign.End)
                            Text(Money.withSymbol(t.expense, lk.base), color = ExpenseColor, fontSize = 16.sp, modifier = Modifier.width(124.dp), textAlign = TextAlign.End)
                        }
                        Text(Money.withSymbol(t.total, lk.base), fontSize = 13.sp, color = gray)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (expanded == m.monthValue) {
                    val today = LocalDate.now()
                    weeksOf(r, weekStart).forEach { (s, e) ->
                        val wt = totalsOf(txns.filter { inRange(it.date.toLocalDate(), s to e) }, lk.fx)
                        val current = inRange(today, s to e)
                        Row(
                            Modifier.fillMaxWidth()
                                .background(if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.background)
                                .padding(start = 48.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("%02d.%02d ~ %02d.%02d".format(s.monthValue, s.dayOfMonth, e.monthValue, e.dayOfMonth), Modifier.weight(1f), fontSize = 15.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Row {
                                    Text(Money.withSymbol(wt.income, lk.base), color = IncomeColor, fontSize = 15.sp, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                                    Text(Money.withSymbol(wt.expense, lk.base), color = ExpenseColor, fontSize = 15.sp, modifier = Modifier.width(110.dp), textAlign = TextAlign.End)
                                }
                                Text((if (current) "合計 " else "") + Money.withSymbol(wt.total, lk.base), fontSize = 12.sp, color = gray)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** 期間切成週，頭尾截在期間範圍內，新的在前。 */
private fun weeksOf(range: Pair<LocalDate, LocalDate>, weekStart: DayOfWeek): List<Pair<LocalDate, LocalDate>> {
    val out = mutableListOf<Pair<LocalDate, LocalDate>>()
    val weekEnd = weekStart.minus(1)
    var s = range.first
    while (!s.isAfter(range.second)) {
        val e = minOf(s.with(TemporalAdjusters.nextOrSame(weekEnd)), range.second)
        out += s to e
        s = e.plusDays(1)
    }
    return out.reversed()
}

// ---------------- 總計 ----------------

@Composable
private fun TotalView(list: List<TxnEntity>, all: List<TxnEntity>, lk: Lookups, nav: NavController, month: YearMonth, range: Pair<LocalDate, LocalDate>) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var cards by remember { mutableStateOf<Map<Long, CardStatus>>(emptyMap()) }
    LaunchedEffect(all) { cards = repo.cardStatus() }
    val confirmed = list.filter { it.status == TxnStatus.CONFIRMED }
    val expense = totalsOf(confirmed, lk.fx).expense
    val lastRange = Periods.month(month.minusMonths(1), settings.monthStartDay)
    val lastExpense = totalsOf(all.filter { inRange(it.date.toLocalDate(), lastRange) }, lk.fx).expense
    fun isCardLike(id: Long?) = lk.account(id)?.type.let { it == AccountType.CREDIT_CARD || it == AccountType.EWALLET }
    val cashExpense = confirmed.filter { it.type == TxnType.EXPENSE && !isCardLike(it.accountId) }.sumOf { lk.fx.convert(it.amount, it.currency) }
    val cardExpense = confirmed.filter { it.type == TxnType.EXPENSE && isCardLike(it.accountId) }.sumOf { lk.fx.convert(it.amount, it.currency) }
    val cardPayable = cards.values.sumOf { it.payable + it.outstanding }
    val transferOut = confirmed.filter { it.type == TxnType.TRANSFER && !isCardLike(it.accountId) }.sumOf { lk.fx.convert(it.amount, it.currency) }
    val byCat = confirmed.filter { it.type == TxnType.EXPENSE }.groupBy { lk.rootId(it.categoryId) ?: 0L }.mapValues { (_, l) -> l.sumOf { lk.fx.convert(it.amount, it.currency) } }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            withContext(Dispatchers.IO) {
                ctx.contentResolver.openOutputStream(uri)!!.use { it.write(buildCsv(list, lk).toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Savings, null, modifier = Modifier.size(30.dp))
                Text("  預算", fontSize = 20.sp, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.background, modifier = Modifier.clickable { nav.navigate("budget") }) {
                    Text("預算設定 ›", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
            if (settings.budgets.isNotEmpty()) Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                settings.budgets.entries.sortedBy { it.key }.forEach { (cid, budget) ->
                    val used = if (cid == 0L) expense else byCat[cid] ?: 0
                    val name = if (cid == 0L) "總預算" else lk.category(cid)?.name ?: "已刪除分類"
                    BudgetBar(name, used, budget)
                }
            }
            HorizontalDivider(Modifier.padding(top = 12.dp), thickness = 10.dp, color = MaterialTheme.colorScheme.background)
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AccountBalanceWallet, null, modifier = Modifier.size(28.dp))
                Text("  帳戶", fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text("${range.first.monthValue}.${range.first.dayOfMonth}.${range.first.year % 100} ~ ${range.second.monthValue}.${range.second.dayOfMonth}.${range.second.year % 100}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KV("與上月支出相比", if (lastExpense > 0) "${expense * 100 / lastExpense}%" else "—")
                KV("支出（現金、銀行）", Money.withSymbol(cashExpense, lk.base))
                KV("支出（信用卡、支付）", Money.withSymbol(cardExpense, lk.base) + "(${Money.withSymbol(cardPayable, lk.base)})")
                KV("轉帳（現金、銀行 →）", Money.withSymbol(transferOut, lk.base))
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                    .clickable { exporter.launch("記帳本_${month.year}${"%02d".format(month.monthValue)}.csv") }.padding(18.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.TableChart, null, tint = Color(0xFF21A366))
                Text("  匯出資料到 Excel", fontSize = 17.sp)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun BudgetBar(name: String, used: Long, budget: Long) {
    val ratio = if (budget > 0) used.toFloat() / budget else 0f
    Column {
        Row {
            Text(name, Modifier.weight(1f))
            Text("${Money.full(used)} / ${Money.full(budget)}", color = if (ratio > 1f) ExpenseColor else MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp),
            color = if (ratio > 1f) ExpenseColor else if (ratio > 0.8f) Color(0xFFF5A623) else IncomeColor,
            trackColor = MaterialTheme.colorScheme.background,
        )
        Text(if (budget >= used) "剩餘 ${Money.full(budget - used)}" else "超支 ${Money.full(used - budget)}",
            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Excel 可直接開啟的 CSV（含 BOM 以正確顯示中文）。 */
private fun buildCsv(list: List<TxnEntity>, lk: Lookups): String {
    fun esc(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
    val sb = StringBuilder("﻿日期,時間,類型,分類,帳戶,轉入帳戶,內容,說明,金額,幣別\n")
    list.sortedBy { it.date }.forEach { t ->
        val dt = java.time.Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault())
        sb.append(listOf(
            dt.toLocalDate().toString(), "%02d:%02d".format(dt.hour, dt.minute), t.type.label,
            esc(lk.category(t.categoryId)?.name), esc(lk.account(t.accountId)?.name), esc(lk.account(t.toAccountId)?.name),
            esc(t.merchant), esc(t.note), Money.fromMinor(t.amount).toPlainString(), t.currency,
        ).joinToString(",")).append('\n')
    }
    return sb.toString()
}

@Composable
private fun KV(k: String, v: String, color: Color = Color.Unspecified) {
    Row(Modifier.fillMaxWidth()) {
        Text(k, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        Text(v, color = color, fontSize = 16.sp)
    }
}

// ---------------- 備註 ----------------

@Composable
private fun MemoView(memos: List<Memo>, onEdit: (Memo) -> Unit) {
    if (memos.isEmpty()) { NoData(); return }
    LazyColumn(Modifier.fillMaxSize()) {
        items(memos.sortedByDescending { it.date }, key = { it.id }) { m ->
            val d = m.date.toLocalDate()
            Column(Modifier.background(MaterialTheme.colorScheme.surface).clickable { onEdit(m) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("${d.monthValue}/${d.dayOfMonth}（${Recurrence.weekdayName(d.dayOfWeek)}）", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(110.dp))
                    Text(m.text, Modifier.weight(1f))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
private fun MemoDialog(memo: Memo, onDismiss: () -> Unit) {
    var text by remember(memo) { mutableStateOf(memo.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (memo.id == 0L) "新增備註" else "編輯備註") },
        text = { OutlinedTextField(text, { text = it }, placeholder = { Text("例如：這個月要繳房屋稅") }, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = {
                repo.settings.update { s ->
                    val updated = if (memo.id == 0L) s.memos + memo.copy(id = System.currentTimeMillis(), text = text.trim())
                    else s.memos.map { if (it.id == memo.id) it.copy(text = text.trim()) else it }
                    s.copy(memos = updated)
                }
                onDismiss()
            }) { Text("儲存") }
        },
        dismissButton = {
            Row {
                if (memo.id != 0L) TextButton(onClick = {
                    repo.settings.update { s -> s.copy(memos = s.memos.filter { it.id != memo.id }) }
                    onDismiss()
                }) { Text("刪除") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}
