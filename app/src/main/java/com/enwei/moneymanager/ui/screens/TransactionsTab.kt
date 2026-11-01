package com.enwei.moneymanager.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.runtime.DisposableEffect
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
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

/** 交易列表的多選：長按一筆開始選取，之後點一下切換；[enabled] 由交易頁的「每日」開啟。 */
object TxnSelect {
    var enabled by mutableStateOf(false)
    var ids by mutableStateOf(setOf<Long>())
    val active get() = enabled && ids.isNotEmpty()
    fun toggle(id: Long) { ids = if (id in ids) ids - id else ids + id }
}

@Composable
fun TransactionsTab(nav: NavController, initialTab: Int = 0) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val accounts by repo.accounts.collectAsStateWithLifecycle(emptyList())
    var tab by rememberSaveable { mutableStateOf(initialTab.coerceIn(0, 2)) }
    LaunchedEffect(com.enwei.moneymanager.ui.TabRequest.txnsTab) {
        com.enwei.moneymanager.ui.TabRequest.txnsTab?.let { tab = it; com.enwei.moneymanager.ui.TabRequest.txnsTab = null }
    }
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
    // 日曆的範圍：0 月、1 季、2 年
    var calSpan by rememberSaveable { mutableStateOf(0) }
    fun step(n: Long) {
        month = when {
            yearly -> month.plusYears(n)
            tab == 1 && calSpan == 2 -> month.plusYears(n)
            tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.QUARTER -> month.plusMonths(3 * n)
            tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.YEAR -> month.plusYears(n)
            else -> month.plusMonths(n)
        }
    }
    val startDay = settings.monthStartDay

    val range = when {
        yearly || (tab == 1 && calSpan == 2) -> Periods.month(YearMonth.of(month.year, 1), startDay).first to Periods.month(YearMonth.of(month.year, 12), startDay).second
        else -> Periods.month(month, startDay)
    }
    val inPeriod = remember(all, range) { all.filter { inRange(it.date.toLocalDate(), range) } }

    // 不做「上期結轉」：每期只看這期的收支，帳戶餘額本來就會延續
    val carry = 0L
    val base = totalsOf(inPeriod, lk.fx)
    val totals = base

    // 多選只在「每日」使用；離開這頁或換分頁時清除
    DisposableEffect(tab) {
        TxnSelect.enabled = tab == 0
        onDispose { TxnSelect.enabled = false; TxnSelect.ids = emptySet() }
    }
    BackHandler(enabled = TxnSelect.active) { TxnSelect.ids = emptySet() }
    val scope = rememberCoroutineScope()
    var batchCat by remember { mutableStateOf(false) }
    var batchAcc by remember { mutableStateOf(false) }
    var batchDelete by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            if (TxnSelect.active) TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                navigationIcon = { IconButton(onClick = { TxnSelect.ids = emptySet() }) { Icon(Icons.Filled.Close, "取消選取") } },
                title = { Text("已選 ${TxnSelect.ids.size} 筆", fontSize = 16.sp) },
                actions = {
                    TextButton(onClick = { TxnSelect.ids = inPeriod.map { it.id }.toSet() }) { Text("全選") }
                    TextButton(onClick = { batchCat = true }) { Text("分類") }
                    TextButton(onClick = { batchAcc = true }) { Text("帳戶") }
                    TextButton(onClick = { batchDelete = true }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                },
            ) else TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = (-12).dp)) {
                        IconButton(onClick = { step(-1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                        Text(
                            when {
                                yearly -> "${month.year}"
                                tab == 1 && calSpan == 2 -> "${month.year}年"
                                tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.QUARTER -> "${month.year}年Q${(month.monthValue - 1) / 3 + 1}"
                                tab == 2 && reviewSpan == com.enwei.moneymanager.data.ReviewSpan.YEAR -> "${month.year}年"
                                else -> "${month.year}年${month.monthValue}月"
                            },
                            fontSize = 16.sp, modifier = Modifier.clickable { month = Periods.monthOf(LocalDate.now(), startDay) },
                        )
                        IconButton(onClick = { step(1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                    }
                },
                actions = {
                    if (pending.isNotEmpty()) IconButton(onClick = { nav.navigate("pending") }) {
                        BadgedBox(badge = { Badge { Text("${pending.size}") } }) { Icon(Icons.Filled.NotificationsActive, "待確認") }
                    }
                    IconButton(onClick = { nav.navigate("search?month=$month") }) { Icon(Icons.Outlined.Search, "搜尋") }
                },
            )
        },
        floatingActionButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 日曆不放＋按鈕（會擋住日期格）：改成長按日期記帳
                if (tab == 0) FloatingActionButton(
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
            val fitGroup1 = com.enwei.moneymanager.ui.components.rememberFitGroup()
            TabRow(
                selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
                indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
            ) {
                (if (com.enwei.moneymanager.ui.I18n.english) listOf("Daily", "Calendar", "Review") else subTabs).forEachIndexed { i, t ->
                    Tab(
                        selected = tab == i, onClick = { tab = i },
                        selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ) {
                        com.enwei.moneymanager.ui.components.FitText(t, fontSize = if (com.enwei.moneymanager.ui.I18n.english) 13.sp else 14.sp, group = fitGroup1,
                            modifier = Modifier.padding(vertical = 10.dp))
                    }
                }
            }
            if (tab < 2) SummaryBar(totals, lk.base)
            // 每日：這個月的總預算進度（有設定總預算、看的是這個月時）
            val budget = settings.budgets[0L]
            if (tab == 0 && budget != null && month == Periods.monthOf(LocalDate.now(), startDay)) {
                val used = totals.expense
                val pct = used.toFloat() / budget.coerceAtLeast(1)
                val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), range.second).coerceAtLeast(0) + 1
                val col = when { pct >= 1f -> MaterialTheme.colorScheme.error; pct >= 0.8f -> Color(0xFFFFA000); else -> MaterialTheme.colorScheme.primary }
                Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { nav.navigate("budget") }
                    .padding(horizontal = 14.dp, vertical = 6.dp)) {
                    Row {
                        Text("預算 ${Money.full(used)} / ${Money.full(budget)}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (used <= budget) "剩 ${Money.full(budget - used)} · 每天約 ${Money.full((budget - used) / daysLeft)}（$daysLeft 天）"
                            else "超出 ${Money.full(used - budget)}",
                            fontSize = 12.sp, color = col,
                        )
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth(pct.coerceIn(0f, 1f)).height(5.dp).background(col, RoundedCornerShape(3.dp)))
                    }
                }
            }
            when (tab) {
                0 -> DailyView(inPeriod, lk, nav, range, carry, recurring, trades.filter { inRange(LocalDate.ofEpochDay(it.date), range) },
                    memos = settings.memos.filter { inRange(it.date.toLocalDate(), range) }) { memoEditing = it }
                1 -> Column(Modifier.fillMaxSize()) {
                    // 月／季／年切換
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(2.dp)) {
                        // 季的檢視已移除：只留月與年
                        listOf(0 to "月", 2 to "年").forEach { (i, l) ->
                            val sel = calSpan == i
                            Box(
                                Modifier.weight(1f).background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { calSpan = i }.padding(vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) { Text(l, fontSize = 12.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                        }
                    }
                    Text("長按日期記一筆", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
                    }
                    when (calSpan) {
                        0, 1 -> CalendarView(range, all, accounts, settings, lk, nav, DayOfWeek.of(settings.weekStartDay), carry, settings.memos) { memoEditing = it }
                        else -> YearCalendar(month.year, all, lk, startDay) { ym -> month = ym; calSpan = 0 }
                    }
                }
                // 收支檢討（月、季、年；AI 逐筆檢查並提問）
                else -> ReviewContent(nav, reviewSpan to month) { reviewSpan = it }
            }
        }
    }
    memoEditing?.let { m -> MemoDialog(m) { memoEditing = null } }
    // 批次操作
    val chosen = all.filter { it.id in TxnSelect.ids }
    if (batchDelete) AlertDialog(
        onDismissRequest = { batchDelete = false },
        title = { Text("刪除 ${chosen.size} 筆交易？") },
        text = { Text("合計 ${Money.full(chosen.sumOf { lk.fx.convert(it.amount, it.currency) })}，刪除後無法復原。") },
        confirmButton = {
            TextButton(onClick = {
                val ids = chosen.map { it.id }
                scope.launch { ids.forEach { repo.deleteTxn(it) } }
                TxnSelect.ids = emptySet(); batchDelete = false
            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { batchDelete = false }) { Text("取消") } },
    )
    if (batchCat) {
        // 只列和選取交易同類型的分類（都是支出或都是收入）
        val kinds = chosen.map { it.type }.filter { it != TxnType.TRANSFER }.distinct()
        AlertDialog(
            onDismissRequest = { batchCat = false },
            title = { Text("改成哪個分類？") },
            text = {
                if (kinds.size != 1) Text("選取的交易要都是支出或都是收入（轉帳沒有分類）。")
                else androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(lk.categories.filter { it.kind == kinds.first() && !it.archived }.sortedBy { lk.catLabel(it.id) }, key = { it.id }) { c ->
                        Text(lk.catLabel(c.id) ?: c.name, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable {
                            val list = chosen.filter { it.type == kinds.first() }
                            scope.launch { list.forEach { repo.saveTxn(it.copy(categoryId = c.id)) } }
                            TxnSelect.ids = emptySet(); batchCat = false
                        }.padding(vertical = 10.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { batchCat = false }) { Text("取消") } },
        )
    }
    if (batchAcc) AlertDialog(
        onDismissRequest = { batchAcc = false },
        title = { Text("改成哪個帳戶？") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(lk.accounts.filter { !it.archived }, key = { it.id }) { a ->
                    Text(a.name, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable {
                        scope.launch { chosen.forEach { repo.saveTxn(it.copy(accountId = a.id)) } }
                        TxnSelect.ids = emptySet(); batchAcc = false
                    }.padding(vertical = 10.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { batchAcc = false }) { Text("取消") } },
    )
}

@Composable
fun SummaryBar(t: Totals, base: String) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
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
        Text(label, fontSize = 12.sp)
        Text(value, color = color, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
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
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
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
    Column(Modifier.padding(top = 8.dp).background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        BoxWithConstraints(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 5.dp, bottom = 5.dp)) {
            val compact = maxWidth < 400.dp
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${day.dayOfMonth}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                val chip = when (day.dayOfWeek) { DayOfWeek.SUNDAY -> SundayColor; DayOfWeek.SATURDAY -> SaturdayColor; else -> Color(0xFF8C8C8C) }
                Surface(color = chip, shape = RoundedCornerShape(3.dp)) {
                    Text("週${Recurrence.weekdayName(day.dayOfWeek)}", color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
                Spacer(Modifier.weight(1f))
                Text(Money.withSymbol(t.income, lk.base), color = IncomeColor, fontSize = if (compact) 11.sp else 13.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Money.withSymbol(t.expense, lk.base), color = ExpenseColor, fontSize = if (compact) 11.sp else 13.sp,
                    modifier = Modifier.weight(1f).padding(start = 4.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (memos.isNotEmpty()) {
            memos.sortedWith(compareByDescending<Memo> { it.pinned }.thenBy { it.id }).forEach { m -> MemoLine(m) { onMemo(m) } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        // 交易之間加細分隔線，左邊的分類、中間內容與右邊金額比較好對齊
        val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        var first = true
        @Composable fun sep() { if (!first) HorizontalDivider(Modifier.padding(start = 14.dp), thickness = 0.5.dp, color = divider); first = false }
        if (ledger != null) ledger.forEach { e ->
            e.txn?.let { sep(); MmTxnRow(it, lk, balances[it.id]) { nav.navigate("txn/${it.id}") } }
            e.trade?.let { tr -> sep(); TradeRow(tr, lk, accountId, tradeBalances[tr.id]) { nav.navigate("trade/${tr.id}") } }
        } else {
            txns.sortedByDescending { it.date }.forEach { sep(); MmTxnRow(it, lk, balances[it.id]) { nav.navigate("txn/${it.id}") } }
            trades.sortedByDescending { it.id }.forEach { tr -> sep(); TradeRow(tr, lk, accountId, tradeBalances[tr.id]) { nav.navigate("trade/${tr.id}") } }
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(80.dp)) {
            Text("股票", color = gray, fontSize = 11.sp)
            Text(side, color = gray, fontSize = 11.sp)
        }
        Column(Modifier.weight(1f)) {
            Text("${tr.symbol} ${tr.name.orEmpty()}".trim(), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(detail, cashAcc?.name ?: "未指定交割帳戶").joinToString(" · "), color = gray, fontSize = 12.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            val affects = accountId == null || tr.cashAccountId == accountId
            // 和其他列一致：金額不帶正負號，流入用收入色、流出用支出色
            Text(Money.withSymbol(kotlin.math.abs(flow), tr.currency), fontSize = 14.sp,
                color = if (!affects || flow == 0L) gray else if (flow > 0) IncomeColor else ExpenseColor)
            if (balanceAfter != null && affects) Text("(${Money.full(balanceAfter)})", fontSize = 11.sp, color = gray)
        }
    }
}

/** 一筆交易：左分類、中內容與帳戶、右金額。 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MmTxnRow(t: TxnEntity, lk: Lookups, balanceAfter: Long? = null, onClick: () -> Unit) {
    val cat = lk.category(t.categoryId)
    val acc = lk.account(t.accountId)
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    val showDesc = repo.settings.flow.collectAsStateWithLifecycle().value.showDescription
    val color = when (t.type) { TxnType.INCOME -> IncomeColor; TxnType.EXPENSE -> ExpenseColor; TxnType.TRANSFER -> MaterialTheme.colorScheme.onSurface }
    val title = t.merchant?.takeIf { it.isNotBlank() }
    val accountText = if (t.type == TxnType.TRANSFER) "${acc?.name ?: "?"} → ${lk.account(t.toAccountId)?.name ?: "?"}" else acc?.name.orEmpty()
    val selecting = TxnSelect.active
    val picked = selecting && t.id in TxnSelect.ids
    Row(
        Modifier.fillMaxWidth()
            .background(if (picked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .combinedClickable(
                onClick = { if (selecting) TxnSelect.toggle(t.id) else onClick() },
                onLongClick = if (TxnSelect.enabled) ({ TxnSelect.toggle(t.id) }) else null,
            )
            .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(80.dp)) {
            val parent = cat?.parentId?.let { lk.category(it) }
            Text(if (t.type == TxnType.TRANSFER) "轉帳" else (parent ?: cat)?.name ?: "未分類", color = gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (parent != null && t.type != TxnType.TRANSFER) Text(cat!!.name, color = gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = when {
                t.status == TxnStatus.PENDING -> "待確認"
                t.source != TxnSource.MANUAL && t.source != TxnSource.IMPORT -> t.source.label
                else -> null
            }
            if (sub != null) Text(sub, color = if (t.status == TxnStatus.PENDING) MaterialTheme.colorScheme.primary else gray, fontSize = 11.sp)
        }
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(accountText, color = gray, fontSize = if (title != null) 14.sp else 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (showDesc && !t.note.isNullOrBlank()) Text(t.note, color = gray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(Money.withSymbol(t.amount, t.currency), color = color, fontSize = 14.sp)
            if (balanceAfter != null) Text("(${Money.full(balanceAfter)})", fontSize = 11.sp, color = gray)
            else if (t.currency != lk.base && lk.fx.hasRate(t.currency)) Text("≈" + Money.full(lk.fx.convert(t.amount, t.currency)), fontSize = 11.sp, color = gray)
        }
    }
}

// ---------------- 日曆 ----------------

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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

    // 格子高度依畫面平均分配：整個月剛好填滿畫面，下方不留大片空白（內容多的週會再自動加高）
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val headerH = if (settings.calendarDots) 46.dp else 30.dp
    val cellMin = ((maxHeight - headerH) / weeks).coerceAtLeast(60.dp)
    // 每週平分剩下的高度，整個月剛好放滿、不用上下滑；畫面太矮（每格不到 64dp）時才改成可捲動
    val fit = (maxHeight - headerH) / weeks >= 64.dp
    Column(Modifier.fillMaxSize().then(if (fit) Modifier else Modifier.verticalScroll(rememberScrollState())).background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 6.dp)) {
            headers.forEach { d ->
                Text("週${Recurrence.weekdayName(d)}", Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 13.sp, color = weekdayColor(d, gray))
            }
        }
        // 標示用顏色小點時，在星期列下方放一行圖例
        if (settings.calendarDots) Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
        ) {
            calendarMarks(SundayColor, IncomeColor, MaterialTheme.colorScheme.primary, ExpenseColor, TransferColor).forEach { (label, c) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(c, CircleShape))
                    Text(" $label", fontSize = 10.sp, color = gray)
                }
            }
        }
        for (w in 0 until weeks) {
            // 一週一列：高度跟著這週內容最多的那格（日期、標示、最多三行金額），不寫死，日期不會被下方擋到
            Row(Modifier.fillMaxWidth().then(if (fit) Modifier.weight(1f) else Modifier.height(IntrinsicSize.Min))) {
                for (d in 0 until 7) {
                    val date = start.plusDays((w * 7 + d).toLong())
                    val inMonth = inRange(date, range)
                    val t = totalsOf(byDay[date].orEmpty(), lk.fx)
                    val alpha = if (inMonth) 1f else 0.45f
                    val isToday = date == today
                    val offDay = !com.enwei.moneymanager.core.TaiwanHolidays.isBusinessDay(date)
                    val dayName = com.enwei.moneymanager.core.TaiwanHolidays.label(date)
                    val holiday = dayName?.takeIf { offDay }
                    val makeupWorkday = dayName?.let { "補行上班" in it || "補班" in it } == true
                    val events = cardEvents[date].orEmpty()
                    // 這天有的標示：休、班、結、繳、扣（完整名稱在點日期後的下方面板）
                    val marks = calendarMarks(SundayColor, IncomeColor, MaterialTheme.colorScheme.primary, ExpenseColor, TransferColor).filter { (label, _) ->
                        when (label) {
                            "休" -> holiday != null && date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
                            "班" -> makeupWorkday
                            "結" -> events.any { it.kind == CardCalendarEventKind.STATEMENT }
                            "繳" -> events.any { it.kind == CardCalendarEventKind.DUE }
                            else -> events.any { it.kind == CardCalendarEventKind.AUTOPAY }
                        }
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight().then(if (fit) Modifier.clipToBounds() else Modifier.heightIn(min = cellMin))
                            .background(if (inMonth) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.background)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            // 點一下看當天明細；長按直接在這天記一筆
                            .combinedClickable(onClick = { sheetDay = date }, onLongClick = { nav.navigate("txn/0?date=${date.toEpochDay()}") })
                            .padding(bottom = 3.dp),
                    ) {
                        // 日期（每月 1 號顯示「月.1」）；今天反白
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (date.dayOfMonth == 1) "${date.monthValue}.1" else "${date.dayOfMonth}",
                                fontSize = 13.sp, maxLines = 1, softWrap = false,
                                color = when {
                                    isToday -> MaterialTheme.colorScheme.surface
                                    holiday != null -> SundayColor.copy(alpha = alpha)
                                    !offDay -> MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                                    else -> weekdayColor(date.dayOfWeek, MaterialTheme.colorScheme.onSurface).copy(alpha = alpha)
                                },
                                modifier = Modifier.background(if (isToday) MaterialTheme.colorScheme.onSurface else Color.Transparent)
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                            memoDays[date]?.let { ms ->
                                Spacer(Modifier.weight(1f))
                                MemoDot(ms.first().color, Modifier.padding(end = 3.dp).size(7.dp).alpha(alpha))
                            }
                        }
                        // 日期與金額之間：休／班／結／繳／扣（文字或顏色小點，可在設定切換）
                        if (marks.isNotEmpty()) Row(
                            Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(if (settings.calendarDots) 3.dp else 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            marks.forEach { (label, c) ->
                                if (settings.calendarDots) Box(Modifier.size(6.dp).background(c.copy(alpha = alpha), CircleShape))
                                else Text(label, fontSize = 9.sp, lineHeight = 10.sp, color = c.copy(alpha = alpha), maxLines = 1, softWrap = false)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        // 收入、支出、合計：完整千分位金額，靠右；放不下時自動縮小字級，不縮寫成「萬」
                        if (t.income > 0) FitAmount(Money.full(t.income), IncomeColor.copy(alpha = alpha))
                        if (t.expense > 0) FitAmount(Money.full(t.expense), ExpenseColor.copy(alpha = alpha))
                        if (t.income > 0 && t.expense > 0) FitAmount(Money.full(t.total), MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
                    }
                }
            }
        }
    }
    }
    sheetDay?.let { day ->
        ModalBottomSheet(onDismissRequest = { sheetDay = null }, containerColor = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${day.year}/${day.monthValue}/${day.dayOfMonth}（${Recurrence.weekdayName(day.dayOfWeek)}）", fontSize = 16.sp)
                    com.enwei.moneymanager.core.TaiwanHolidays.label(day)?.let { Text(it, fontSize = 13.sp, color = SundayColor) }
                }
                TextButton(onClick = { sheetDay = null; nav.navigate("txn/0?date=${day.toEpochDay()}") }) { Text("＋ 記一筆") }
            }
            val dayCardEvents = cardEvents[day].orEmpty()
            if (dayCardEvents.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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

/** 日曆標示：（字, 顏色）——休假、補班、結帳、繳款、扣款。 */
private fun calendarMarks(holiday: Color, workday: Color, statement: Color, due: Color, autopay: Color) = listOf(
    "休" to holiday, "班" to workday, "結" to statement, "繳" to due, "扣" to autopay,
)

/**
 * 日曆格裡的一行金額：靠右、一行顯示完整數字；格子太窄放不下時逐步縮小字級（最小 6sp），
 * 不會截掉也不縮寫。用 onTextLayout 調整，不需要額外量測元件（可放在高度自適應的列裡）。
 */
@Composable
private fun FitAmount(text: String, color: Color) {
    var size by remember(text) { mutableStateOf(10f) }
    var ready by remember(text) { mutableStateOf(false) }
    Text(
        text, color = color, fontSize = size.sp, lineHeight = (size + 2).sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp).drawWithContent { if (ready) drawContent() },
        onTextLayout = { r -> if (r.didOverflowWidth && size > 6f) size -= 0.5f else ready = true },
    )
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
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemoDot(m.color, Modifier.size(12.dp))
        Spacer(Modifier.width(14.dp))
        Text(m.title.ifBlank { m.text.lineSequence().firstOrNull().orEmpty() }, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
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


/** 一天的支出（基準幣別）。 */
private fun dailyExpense(all: List<TxnEntity>, lk: Lookups, from: LocalDate, to: LocalDate): Map<LocalDate, Long> =
    all.asSequence().filter { it.type == TxnType.EXPENSE && it.status == com.enwei.moneymanager.data.TxnStatus.CONFIRMED }
        .map { it.date.toLocalDate() to lk.fx.convert(it.amount, it.currency) }
        .filter { (d, _) -> !d.isBefore(from) && !d.isAfter(to) }
        .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }

/** 支出熱度：越紅花越多（0 為沒有支出）。 */
@Composable
private fun heatColor(v: Long, max: Long): Color =
    if (v <= 0) Color.Transparent else ExpenseColor.copy(alpha = (0.12f + 0.6f * (v.toFloat() / max.coerceAtLeast(1))).coerceAtMost(0.72f))

/**
 * 年曆：12 個月的格子，每格有收入、支出、合計與每天支出的小熱度圖，點了進入該月月曆。
 * 下方列出全年合計與支出最多的月份。
 */
@Composable
private fun YearCalendar(year: Int, all: List<TxnEntity>, lk: Lookups, startDay: Int, onMonth: (YearMonth) -> Unit) {
    val months = (1..12).map { YearMonth.of(year, it) }
    val first = Periods.month(months.first(), startDay).first
    val last = Periods.month(months.last(), startDay).second
    val daily = remember(all, first, last) { dailyExpense(all, lk, first, last) }
    val totals = remember(all, year, startDay) {
        months.associateWith { ym -> val r = Periods.month(ym, startDay); totalsOf(all.filter { inRange(it.date.toLocalDate(), r) }, lk.fx) }
    }
    val max = daily.values.maxOrNull() ?: 1L
    val now = YearMonth.now()
    val year0 = totals.values.fold(0L) { a, t -> a + t.expense }
    val income0 = totals.values.fold(0L) { a, t -> a + t.income }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 四列平分畫面高度，一年剛好放滿、不用滑；畫面太矮（每列不到 110dp）時才改成可捲動
        val fit = (maxHeight - 28.dp) / 4 >= 110.dp
        Column(Modifier.fillMaxSize().then(if (fit) Modifier else Modifier.verticalScroll(rememberScrollState())).padding(horizontal = 6.dp)) {
            months.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().then(if (fit) Modifier.weight(1f) else Modifier.height(110.dp))) {
                    row.forEach { ym ->
                        val t = totals[ym]!!
                        val r = Periods.month(ym, startDay)
                        Column(
                            Modifier.weight(1f).fillMaxHeight().padding(3.dp).clipToBounds()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                                .then(if (ym == now) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier)
                                .clickable { onMonth(ym) }.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            Text("${ym.monthValue}月", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            FitAmount(Money.full(t.income), IncomeColor)
                            FitAmount(Money.full(t.expense), ExpenseColor)
                            FitAmount(Money.full(t.total), MaterialTheme.colorScheme.onSurface)
                            Spacer(Modifier.weight(1f))
                            // 每天支出的小熱度圖（一格一天，7 天一行）
                            val days = generateSequence(r.first) { it.plusDays(1) }.takeWhile { !it.isAfter(r.second) }.toList()
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                days.chunked(7).forEach { wk ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                                        wk.forEach { d ->
                                            val v = daily[d] ?: 0L
                                            Box(Modifier.weight(1f).height(4.dp).background(
                                                if (v > 0) heatColor(v, max) else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(1.dp)))
                                        }
                                        repeat(7 - wk.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val top = totals.maxByOrNull { it.value.expense }?.takeIf { it.value.expense > 0 }
            Text(
                "全年收入 ${Money.full(income0)}　支出 ${Money.full(year0)}" + (top?.let { "　支出最多：${it.key.monthValue}月" } ?: ""),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
