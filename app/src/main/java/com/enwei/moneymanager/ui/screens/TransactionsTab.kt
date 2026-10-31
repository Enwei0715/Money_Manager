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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin as OutlinedPushPin
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
import androidx.compose.material.icons.outlined.TableChart
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
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text as MaterialText
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.FxConverter
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Periods
import com.enwei.moneymanager.core.Recurrence
import com.enwei.moneymanager.core.CardCycle
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AppSettings
import com.enwei.moneymanager.data.CardStatus
import com.enwei.moneymanager.data.Memo
import com.enwei.moneymanager.data.RecurringEntity
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnSource
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.cycleFor
import com.enwei.moneymanager.data.shiftFor
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.data.toMillis
import com.enwei.moneymanager.data.toRecord
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

// 「每月」「總計」「備註」分頁已拿掉：備註改從「＋」新增（記一筆的類型選「備註」），預算移到「檢討」
private val subTabs = listOf("每日", "日曆", "檢討")
private val NavyHighlight = Color(0xFF3F4570)

@Composable
fun TransactionsTab(nav: NavController, initialTab: Int = 0) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val accounts by repo.accounts.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 2)) }
    var month by rememberSaveable { mutableStateOf(Periods.monthOf(LocalDate.now(), repo.settings.value.monthStartDay)) }
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val pending by repo.pending.collectAsStateWithLifecycle(emptyList())
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    val recurring by repo.recurring.collectAsStateWithLifecycle(emptyList())
    var memoEditing by remember { mutableStateOf<Memo?>(null) }
    // 「檢討」分頁的期間（月／季／年）：上方的期間切換直接控制檢討，不在下方再放一組
    var reviewSpan by rememberSaveable { mutableStateOf(com.enwei.moneymanager.data.ReviewSpan.MONTH) }
    val yearly = false
    fun step(n: Long) {
        month = when {
            yearly -> month.plusYears(n)
            tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.QUARTER -> month.plusMonths(3 * n)
            tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.YEAR -> month.plusYears(n)
            else -> month.plusMonths(n)
        }
    }
    val startDay = settings.monthStartDay

    val range = if (yearly) Periods.month(YearMonth.of(month.year, 1), startDay).first to Periods.month(YearMonth.of(month.year, 12), startDay).second
    else Periods.month(month, startDay)
    val inPeriod = remember(all, range) { all.filter { inRange(it.date.toLocalDate(), range) } }

    // 不做「上期結轉」：每期只看這期的收支，帳戶餘額本來就會延續
    val carry = 0L
    val base = totalsOf(inPeriod, lk.fx)
    val totals = base

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
                        IconButton(onClick = { step(-1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                        Text(
                            when {
                                yearly -> "${month.year}"
                                tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.QUARTER -> "${month.year}年Q${(month.monthValue - 1) / 3 + 1}"
                                tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.YEAR -> "${month.year}年"
                                else -> "${month.year}年${month.monthValue}月"
                            },
                            fontSize = 18.sp, modifier = Modifier.clickable { month = Periods.monthOf(LocalDate.now(), startDay) },
                        )
                        IconButton(onClick = { step(1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) IconButton(onClick = { nav.navigate("pending") }) {
                        BadgedBox(badge = { Badge { Text("${pending.size}") } }) { Icon(Icons.Filled.NotificationsActive, "待確認") }
                    }
                    IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Outlined.Search, "搜尋") }
                },
            )
        },
        floatingActionButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (tab < 2) FloatingActionButton(
                    onClick = { nav.navigate("txn/0") }, shape = CircleShape, modifier = Modifier.size(64.dp),
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.White,
                ) { Icon(Icons.Filled.Add, "記一筆", modifier = Modifier.size(32.dp)) }
            }
        },
    ) { pad ->
        var dragX by remember { mutableStateOf(0f) }
        val swipeModifier = if (settings.swipe == 1 && tab < 2) Modifier.pointerInput(yearly) {
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
                (if (com.enwei.moneymanager.ui.I18n.english) listOf("Daily", "Calendar", "Review") else subTabs).forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        Text(t, fontSize = if (com.enwei.moneymanager.ui.I18n.english) 13.sp else 14.sp, maxLines = 1, softWrap = false,
                            modifier = Modifier.padding(vertical = 14.dp))
                    }
                }
            }
            if (tab < 2) SummaryBar(totals, lk.base)
            when (tab) {
                0 -> DailyView(inPeriod, lk, nav, range, carry, recurring, trades.filter { inRange(LocalDate.ofEpochDay(it.date), range) },
                    memos = settings.memos.filter { inRange(it.date.toLocalDate(), range) }) { memoEditing = it }
                1 -> CalendarView(range, all, accounts, settings, lk, nav, DayOfWeek.of(settings.weekStartDay), carry, settings.memos) { memoEditing = it }
                // 收支檢討（月、季、年；AI 逐筆檢查並提問）
                else -> ReviewContent(nav, reviewSpan to month) { reviewSpan = it }
            }
        }
    }
    memoEditing?.let { m -> MemoDialog(m) { memoEditing = null } }
}

@Composable
fun SummaryBar(t: Totals, base: String) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            if (maxWidth < 360.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        SummaryCell("收入", Money.full(t.income), IncomeColor, Modifier.weight(1f))
                        SummaryCell("支出", Money.full(t.expense), ExpenseColor, Modifier.weight(1f))
                    }
                    SummaryCell("合計", Money.full(t.total), MaterialTheme.colorScheme.onSurface, Modifier.fillMaxWidth())
                }
            } else {
                Row(Modifier.fillMaxWidth()) {
                    SummaryCell("收入", Money.full(t.income), IncomeColor, Modifier.weight(1f))
                    SummaryCell("支出", Money.full(t.expense), ExpenseColor, Modifier.weight(1f))
                    SummaryCell("合計", Money.full(t.total), MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SummaryCell(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = color, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------- 每日 ----------------

@Composable
private fun DailyView(
    list: List<TxnEntity>, lk: Lookups, nav: NavController, range: Pair<LocalDate, LocalDate>, carry: Long, recurring: List<RecurringEntity>,
    trades: List<com.enwei.moneymanager.data.StockTradeEntity> = emptyList(),
    memos: List<Memo> = emptyList(),
    onMemo: (Memo) -> Unit = {},
) {
    val days = remember(list) { list.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()) }
    // 股票交易顯示在當天（不計入收支）
    val tradeDays = remember(trades) { trades.groupBy { LocalDate.ofEpochDay(it.date) } }
    // 有備註的日子也列出來（備註顯示在當天標題下方）
    val memoDays = remember(memos) { memos.groupBy { it.date.toLocalDate() } }
    val allDays = (days.keys + tradeDays.keys + memoDays.keys).toSortedSet(Comparator.reverseOrder())
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
                        Text("週期", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 72.dp))
                        Text("${d.monthValue}/${d.dayOfMonth}  ${r.name}", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Text(Money.withSymbol(signed, r.currency), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (allDays.isEmpty() && carry == 0L) item { NoData() }
        allDays.forEach { day -> item(key = "d$day") { DayBlock(day, days[day].orEmpty(), lk, nav, trades = tradeDays[day].orEmpty(), memos = memoDays[day].orEmpty(), onMemo = onMemo) } }
        if (carry != 0L) item {
            Column(Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Text("${range.first.monthValue}/${range.first.dayOfMonth}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 72.dp))
                    Text("上期結轉", Modifier.weight(1f))
                    Text(Money.withSymbol(carry, lk.base), color = if (carry >= 0) IncomeColor else ExpenseColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable
fun NoData(text: String = "沒有資料") {
    Column(Modifier.fillMaxWidth().padding(top = 120.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        // 自己的空白圖示（不沿用舊 App 的貓咪對話框）
        Icon(com.enwei.moneymanager.ui.components.LineIcons.Inbox, null, tint = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.size(64.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun DayBlock(
    day: LocalDate, txns: List<TxnEntity>, lk: Lookups, nav: NavController, balances: Map<Long, Long> = emptyMap(),
    trades: List<com.enwei.moneymanager.data.StockTradeEntity> = emptyList(), tradeBalances: Map<Long, Long> = emptyMap(),
    /** 從帳戶明細進入時的帳戶；股票金額依此帳戶顯示 */
    accountId: Long? = null,
    /** 帳戶明細：這一天在帳本上的項目（新到舊）。有給時標題顯示這個帳戶的存入／提出（含轉帳、股票），並依帳本順序列出 */
    ledger: List<LedgerEntry>? = null,
    /** 這一天的備註（顯示在標題下方，點一下編輯） */
    memos: List<Memo> = emptyList(),
    onMemo: (Memo) -> Unit = {},
) {
    val t = if (ledger != null) Totals(ledger.sumOf { maxOf(0, it.delta) }, -ledger.sumOf { minOf(0, it.delta) }) else totalsOf(txns, lk.fx)
    Column(Modifier.padding(top = 10.dp).background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            val compact = maxWidth < 400.dp
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${day.dayOfMonth}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                val chip = when (day.dayOfWeek) { DayOfWeek.SUNDAY -> SundayColor; DayOfWeek.SATURDAY -> SaturdayColor; else -> Color(0xFF8C8C8C) }
                Surface(color = chip, shape = RoundedCornerShape(3.dp)) {
                    Text("週${Recurrence.weekdayName(day.dayOfWeek)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(Money.withSymbol(t.income, lk.base), color = IncomeColor, fontSize = if (compact) 12.sp else 14.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.withSymbol(t.expense, lk.base), color = ExpenseColor, fontSize = if (compact) 12.sp else 14.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (memos.isNotEmpty()) {
            memos.sortedWith(compareByDescending<Memo> { it.pinned }.thenBy { it.id }).forEach { m -> MemoLine(m) { onMemo(m) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (ledger != null) ledger.forEach { e ->
            e.txn?.let { MmTxnRow(it, lk, balances[it.id]) { nav.navigate("txn/${it.id}") } }
            e.trade?.let { tr -> TradeRow(tr, lk, accountId, tradeBalances[tr.id]) { nav.navigate("trade/${tr.id}") } }
        } else {
            txns.sortedByDescending { it.date }.forEach { MmTxnRow(it, lk, balances[it.id]) { nav.navigate("txn/${it.id}") } }
            trades.sortedByDescending { it.id }.forEach { tr -> TradeRow(tr, lk, accountId, tradeBalances[tr.id]) { nav.navigate("trade/${tr.id}") } }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 一筆股票交易（不算收入／支出）：顯示買賣內容與對交割帳戶的金額影響。
 * [accountId] 為目前檢視的帳戶；不是交割帳戶時金額以灰色顯示（證券帳戶只看持股）。
 */
@Composable
fun TradeRow(tr: com.enwei.moneymanager.data.StockTradeEntity, lk: Lookups, accountId: Long?, balanceAfter: Long?, onClick: () -> Unit) {
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    val side = when (tr.side) {
        com.enwei.moneymanager.core.StockSide.BUY -> "買進"; com.enwei.moneymanager.core.StockSide.SELL -> "賣出"
        com.enwei.moneymanager.core.StockSide.CASH_DIVIDEND -> "現金股利"; com.enwei.moneymanager.core.StockSide.STOCK_DIVIDEND -> "股票股利"
    }
    val flow = Money.toMinor(com.enwei.moneymanager.core.StockCalc.cashFlow(tr.toRecord()))
    val cashAcc = lk.account(tr.cashAccountId)
    val detail = when (tr.side) {
        com.enwei.moneymanager.core.StockSide.CASH_DIVIDEND -> null
        else -> "${com.enwei.moneymanager.ui.components.formatNum(tr.shares, 0)} 股" +
            if (tr.side != com.enwei.moneymanager.core.StockSide.STOCK_DIVIDEND) " @ ${com.enwei.moneymanager.ui.components.formatNum(tr.price)}" else ""
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(96.dp)) {
            Text("股票", color = gray, fontSize = 14.sp)
            Text(side, color = gray, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("${tr.symbol} ${tr.name.orEmpty()}".trim(), fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(detail, cashAcc?.name ?: "未指定交割帳戶").joinToString(" · "), color = gray, fontSize = 14.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val affects = accountId == null || tr.cashAccountId == accountId
            // 和其他列一致：金額不帶正負號，流入用收入色、流出用支出色
            Text(Money.withSymbol(kotlin.math.abs(flow), tr.currency), fontSize = 16.sp,
                color = if (!affects || flow == 0L) gray else if (flow > 0) IncomeColor else ExpenseColor)
            if (balanceAfter != null && affects) Text("(${Money.full(balanceAfter)})", fontSize = 12.sp, color = gray)
        }
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
    range: Pair<LocalDate, LocalDate>, all: List<TxnEntity>, accounts: List<AccountEntity>, settings: AppSettings,
    lk: Lookups, nav: NavController, weekStart: DayOfWeek, carry: Long,
    memos: List<Memo> = emptyList(), onMemo: (Memo) -> Unit = {},
) {
    var sheetDay by remember { mutableStateOf<LocalDate?>(null) }
    val byDay = remember(all) { all.groupBy { it.date.toLocalDate() } }
    val memoDays = remember(memos) { memos.groupBy { it.date.toLocalDate() } }
    val today = LocalDate.now()
    val start = range.first.with(TemporalAdjusters.previousOrSame(weekStart))
    val end = range.second.with(TemporalAdjusters.nextOrSame(weekStart.minus(1)))
    val weeks = ((ChronoUnit.DAYS.between(start, end) + 1) / 7).toInt()
    val gridStart = start
    val gridEnd = end
    val cardEvents = remember(accounts, settings, gridStart, gridEnd) {
        val events = mutableMapOf<LocalDate, MutableList<CardCalendarEvent>>()
        fun addEvent(date: LocalDate, account: AccountEntity, kind: CardCalendarEventKind) {
            if (date in gridStart..gridEnd) {
                events.getOrPut(date) { mutableListOf() } += CardCalendarEvent(account.name, kind)
            }
        }
        val monthsBack = ((settings.cardDueOffset.values.maxOrNull() ?: 0) / 30 + 2).coerceIn(2, 24)
        accounts.asSequence().filter { it.type == AccountType.CREDIT_CARD && !it.archived }.forEach { card ->
            val cycle = settings.cycleFor(card)
            val statementCycle = card.statementDay?.let {
                CardCycle(it, card.dueDay ?: 1, settings.shiftFor(card.id))
            }
            val autopayCycle = cycle ?: card.dueDay?.let {
                CardCycle(card.statementDay ?: 1, it, settings.shiftFor(card.id))
            }
            var month = YearMonth.from(gridStart).minusMonths(monthsBack.toLong())
            val lastMonth = YearMonth.from(gridEnd).plusMonths(2)
            while (!month.isAfter(lastMonth)) {
                val statement = statementCycle?.statementIn(month)
                statement?.let { addEvent(it, card, CardCalendarEventKind.STATEMENT) }
                val due = when {
                    cycle != null && statement != null -> cycle.dueFor(statement)
                    card.dueDay != null -> com.enwei.moneymanager.core.TaiwanHolidays.nextBusinessDay(
                        Recurrence.dayInMonth(month, card.dueDay),
                    )
                    else -> null
                }
                due?.let { addEvent(it, card, CardCalendarEventKind.DUE) }
                if (card.autopayFromId != null && due != null && autopayCycle != null) {
                    addEvent(autopayCycle.autopayDateFor(due, card.autopayDay), card, CardCalendarEventKind.AUTOPAY)
                }
                month = month.plusMonths(1)
            }
        }
        events.mapValues { (_, value) -> value.toList() }
    }
    val headers = (0 until 7).map { weekStart.plus(it.toLong()) }
    val gray = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp)) {
            headers.forEach { d ->
                Text("週${Recurrence.weekdayName(d)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, color = weekdayColor(d, gray))
            }
        }
        for (w in 0 until weeks) {
            Row(Modifier.fillMaxWidth().heightIn(min = 90.dp)) {
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
                        val label = date.dayOfMonth.toString()
                        val isToday = date == today
                        // 假日以符號標示，完整名稱在點擊日期後顯示。
                        val offDay = !com.enwei.moneymanager.core.TaiwanHolidays.isBusinessDay(date)
                        val holiday = com.enwei.moneymanager.core.TaiwanHolidays.label(date)?.takeIf { offDay }
                        val dayName = com.enwei.moneymanager.core.TaiwanHolidays.label(date)
                        val makeupWorkday = dayName?.let { "補行上班" in it || "補班" in it } == true
                        val events = cardEvents[date].orEmpty()
                        val markerPrimary = MaterialTheme.colorScheme.primary
                        Column(Modifier.fillMaxWidth().heightIn(min = 30.dp, max = 34.dp)) {
                            Row(
                                Modifier.fillMaxWidth().height(18.dp)
                                    .background(if (isToday) NavyHighlight else Color.Transparent)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    label, fontSize = 13.sp,
                                    color = when {
                                        isToday -> Color.White
                                        holiday != null -> SundayColor.copy(alpha = alpha)
                                        !offDay -> MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                        else -> weekdayColor(date.dayOfWeek, MaterialTheme.colorScheme.onSurface).copy(alpha = alpha)
                                    },
                                )
                                memoDays[date]?.let { ms ->
                                    Spacer(Modifier.weight(1f))
                                    MemoDot(ms.first().color, Modifier.size(8.dp).alpha(alpha))
                                }
                            }
                            val markers = buildAnnotatedString {
                                fun marker(label: String, color: Color) {
                                    withStyle(SpanStyle(color = color)) { append(label) }
                                }
                                if (holiday != null) marker("休", SundayColor.copy(alpha = alpha))
                                if (makeupWorkday) marker("班", IncomeColor.copy(alpha = alpha))
                                if (events.any { it.kind == CardCalendarEventKind.STATEMENT }) {
                                    marker("結", markerPrimary.copy(alpha = alpha))
                                }
                                if (events.any { it.kind == CardCalendarEventKind.DUE }) {
                                    marker("繳", ExpenseColor.copy(alpha = alpha))
                                }
                                if (events.any { it.kind == CardCalendarEventKind.AUTOPAY }) {
                                    marker("扣", TransferColor.copy(alpha = alpha))
                                }
                            }
                            MaterialText(
                                markers,
                                modifier = Modifier.fillMaxWidth().height(14.dp).padding(horizontal = 2.dp),
                                fontSize = 8.sp,
                                lineHeight = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                            )
                        }
                        val lines = listOf(
                            (if (t.income > 0) formatCalendarAmount(t.income) else "") to IncomeColor,
                            (if (t.expense > 0) formatCalendarAmount(t.expense) else "") to ExpenseColor,
                        )
                        BoxWithConstraints(
                            Modifier.fillMaxWidth().padding(top = 2.dp, start = 2.dp, end = 2.dp),
                        ) {
                            val cellFontSize = if (maxWidth < 46.dp) 8.sp else 9.sp
                            val cellLineHeight = if (maxWidth < 46.dp) 10.sp else 11.sp
                            Column(
                                Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                lines.forEach { (text, c) ->
                                    if (text.isNotEmpty()) {
                                        Text(
                                            text,
                                            fontSize = cellFontSize,
                                            lineHeight = cellLineHeight,
                                            color = c.copy(alpha = alpha),
                                            maxLines = 1,
                                            textAlign = TextAlign.End,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.fillMaxWidth().height(13.dp).padding(end = 1.dp),
                                        )
                                    }
                                }
                            }
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
                Column(Modifier.weight(1f)) {
                    Text("${day.year}/${day.monthValue}/${day.dayOfMonth}（${Recurrence.weekdayName(day.dayOfWeek)}）", fontSize = 18.sp)
                    com.enwei.moneymanager.core.TaiwanHolidays.label(day)?.let { Text(it, fontSize = 13.sp, color = SundayColor) }
                }
                TextButton(onClick = { sheetDay = null; nav.navigate("txn/0?date=${day.toEpochDay()}") }) { Text("＋ 記一筆") }
            }
            val dayCardEvents = cardEvents[day].orEmpty()
            if (dayCardEvents.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("信用卡日程", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    dayCardEvents.forEach { event ->
                        Text(
                            "${event.kind.label} · ${event.accountName}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            val txns = byDay[day].orEmpty()
            val dayMemos = memoDays[day].orEmpty()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                dayMemos.forEach { m -> MemoLine(m) { sheetDay = null; onMemo(m) } }
                Row {
                    TextButton(onClick = { sheetDay = null; onMemo(Memo(0, day.toMillis(), "")) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("＋ 備註")
                    }
                    // 平日才需要：颱風等天然災害臺北市停止上班時，銀行也停止營業，信用卡結帳、繳款與扣繳會順延
                    if (day.dayOfWeek != DayOfWeek.SATURDAY && day.dayOfWeek != DayOfWeek.SUNDAY) {
                        val typhoon = com.enwei.moneymanager.core.TaiwanHolidays.holidayName(day) == "颱風假"
                        val holiday = !com.enwei.moneymanager.core.TaiwanHolidays.isBusinessDay(day)
                        if (typhoon || !holiday) TextButton(onClick = { repo.setTyphoonDay(day, !typhoon) }) {
                            Text(if (typhoon) "取消颱風假" else "標記為颱風假（停止上班）")
                        }
                    }
                }
                if (dayMemos.isNotEmpty() || txns.isNotEmpty()) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (txns.isEmpty()) Text("沒有交易", color = gray, modifier = Modifier.padding(20.dp))
                else txns.sortedByDescending { it.date }.forEach { t -> MmTxnRow(t, lk) { sheetDay = null; nav.navigate("txn/${t.id}") } }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class CardCalendarEventKind(val label: String) {
    STATEMENT("結帳日"),
    DUE("繳款日"),
    AUTOPAY("自動扣繳日"),
}

private data class CardCalendarEvent(val accountName: String, val kind: CardCalendarEventKind)

private fun formatCalendarAmount(amountMinor: Long): String {
    val amount = amountMinor / 100.0
    val (scale, unit) = when {
        amount >= 100_000_000 -> 100_000_000.0 to "億"
        amount >= 10_000 -> 10_000.0 to "萬"
        else -> return java.text.DecimalFormat("#,##0").format(amount)
    }
    return java.text.DecimalFormat("0.#").format(amount / scale) + unit
}

@Composable
fun BudgetBar(name: String, used: Long, budget: Long) {
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
fun buildCsv(list: List<TxnEntity>, lk: Lookups): String {
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

// ---------------- 備註 ----------------

/** 備註標示：空心小圓圈，有主題色時用主題色。 */
@Composable
private fun MemoDot(color: Int, modifier: Modifier = Modifier) {
    val tint = com.enwei.moneymanager.data.memoColors.getOrElse(color) { 0L }
    Box(modifier.border(1.5.dp, if (tint == 0L) MaterialTheme.colorScheme.primary else Color(tint), CircleShape))
}

/** 每日列表與日曆當天的一則備註：小圓圈＋標題（沒有標題時顯示內容第一行）。 */
@Composable
private fun MemoLine(m: Memo, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemoDot(m.color, Modifier.size(12.dp))
        Spacer(Modifier.width(14.dp))
        Text(m.title.ifBlank { m.text.lineSequence().firstOrNull().orEmpty() }, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f))
        if (m.pinned) Icon(Icons.Filled.PushPin, "置頂", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
    }
}

/** 備註：維持小視窗，可設定標題、內容、主題色與置頂。 */
@Composable
fun MemoDialog(memo: Memo, onSaved: () -> Unit = {}, onDismiss: () -> Unit) {
    var title by remember(memo) { mutableStateOf(memo.title) }
    var text by remember(memo) { mutableStateOf(memo.text) }
    var color by remember(memo) { mutableStateOf(memo.color) }
    var pinned by remember(memo) { mutableStateOf(memo.pinned) }
    var date by remember(memo) { mutableStateOf(memo.date.toLocalDate()) }
    val tint = com.enwei.moneymanager.data.memoColors.getOrElse(color) { 0L }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (tint == 0L) MaterialTheme.colorScheme.surfaceContainerHigh
        else androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surfaceContainerHigh, Color(tint), 0.18f),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (memo.id == 0L) "新增備註" else "編輯備註", Modifier.weight(1f))
                IconButton(onClick = { pinned = !pinned }) {
                    Icon(
                        if (pinned) Icons.Filled.PushPin else Icons.Outlined.OutlinedPushPin, "置頂",
                        tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 備註記在哪一天：每日列表與日曆會在那天顯示小圓圈
                com.enwei.moneymanager.ui.components.DateField(date, { date = it })
                OutlinedTextField(title, { title = it }, label = { Text("標題") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(text, { text = it }, label = { Text("內容") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                Text("主題色", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 12 種主題色，兩排各 6 個；第一個為不上色
                com.enwei.moneymanager.data.memoColors.chunked(6).forEachIndexed { row, list ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        list.forEachIndexed { j, c ->
                            val idx = row * 6 + j
                            val sel = idx == color
                            Box(
                                Modifier.size(30.dp)
                                    .border(if (sel) 2.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                    .padding(3.dp)
                                    .background(if (c == 0L) Color.Transparent else Color(c), CircleShape)
                                    .clickable { color = idx },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank() || title.isNotBlank(), onClick = {
                repo.settings.update { s ->
                    val edited = memo.copy(date = date.toMillis(), title = title.trim(), text = text.trim(), color = color, pinned = pinned)
                    val updated = if (memo.id == 0L) s.memos + edited.copy(id = System.currentTimeMillis())
                    else s.memos.map { if (it.id == memo.id) edited else it }
                    s.copy(memos = updated)
                }
                onSaved()
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
