package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Periods
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.CardStatus
import com.enwei.moneymanager.data.StockTradeEntity
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.data.toMillis
import com.enwei.moneymanager.data.toRecord
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.DualBarChart
import com.enwei.moneymanager.ui.components.EmptyHint
import com.enwei.moneymanager.ui.components.LineChart
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

fun balanceColor(v: Long, default: Color) = when { v > 0 -> IncomeColor; v < 0 -> ExpenseColor; else -> default }

/** 某筆交易對指定帳戶餘額的影響。 */
fun deltaFor(t: TxnEntity, accountId: Long): Long {
    if (t.status != TxnStatus.CONFIRMED) return 0
    return when (t.type) {
        TxnType.EXPENSE -> if (t.accountId == accountId) -t.amount else 0
        TxnType.INCOME -> if (t.accountId == accountId) t.amount else 0
        TxnType.TRANSFER -> (if (t.accountId == accountId) -t.amount else 0) + (if (t.toAccountId == accountId) t.toAmount ?: t.amount else 0)
    }
}

private fun tradeDelta(tr: StockTradeEntity, accountId: Long): Long =
    if (tr.cashAccountId == accountId) Money.toMinor(com.enwei.moneymanager.core.StockCalc.cashFlow(tr.toRecord())) else 0

/** 帳戶在指定時間點（不含）之前的餘額 = 目前餘額 − 之後所有變動。 */
fun balanceBefore(current: Long, accountId: Long, millis: Long, txns: List<TxnEntity>, trades: List<StockTradeEntity>): Long =
    current - txns.filter { it.date >= millis }.sumOf { deltaFor(it, accountId) } -
        trades.filter { LocalDate.ofEpochDay(it.date).toMillis(LocalTime.NOON) >= millis }.sumOf { tradeDelta(it, accountId) }

@Composable
private fun GrayHeader(title: String, right: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        right()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun AccountsTab(nav: NavController, showBack: Boolean = false) {
    val lk = rememberLookups()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var cards by remember { mutableStateOf<Map<Long, CardStatus>>(emptyMap()) }
    var menu by remember { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(all, balances) { cards = repo.cardStatus() }

    val visible = balances.filter { showHidden || !it.account.archived }.sortedWith(compareBy({ it.account.sortOrder }, { it.account.id }))
    val counted = balances.filter { !it.account.archived && it.account.includeInNetWorth }
    val stockValue = holdings.sumOf { lk.fx.convert(Money.toMinor(it.marketValue), it.currency) }
    val assets = counted.filter { it.balance > 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) } + stockValue
    val debts = -counted.filter { it.balance < 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { Text("帳戶") },
                navigationIcon = {
                    if (showBack) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                    IconButton(onClick = { nav.navigate("totalstats") }) { Icon(Icons.Outlined.BarChart, "總資產統計") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "選單") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("新增") }, onClick = { menu = false; nav.navigate("account/0") })
                            DropdownMenuItem(text = { Text(if (showHidden) "隱藏已隱藏帳戶" else "顯示已隱藏帳戶") }, onClick = { menu = false; showHidden = !showHidden })
                            DropdownMenuItem(text = { Text("修改順序") }, onClick = { menu = false; nav.navigate("accountorder") })
                        }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryBarCustom(
                    listOf("資產" to (Money.full(assets) to IncomeColor), "負債" to (Money.full(debts) to ExpenseColor),
                        "合計" to (Money.full(assets - debts) to MaterialTheme.colorScheme.onSurface)),
                )
            }
            visible.groupBy { it.account.type }.toSortedMap().forEach { (type, list) ->
                if (type == AccountType.CREDIT_CARD) {
                    item(key = "g${type.name}") {
                        val payable = list.sumOf { cards[it.account.id]?.payable ?: 0 }
                        val outst = list.sumOf { cards[it.account.id]?.outstanding ?: 0 }
                        GrayHeader(type.label) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
                                Text("應繳餘額", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.withSymbol(payable, lk.base), color = ExpenseColor)
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
                                Text("未出帳", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.withSymbol(outst, lk.base), color = ExpenseColor)
                            }
                        }
                    }
                    items(list, key = { "a${it.account.id}" }) { b ->
                        val st = cards[b.account.id]
                        AccountLine(b.account.name + if (b.account.archived) "（隱藏）" else "", { nav.navigate("acct/${b.account.id}") }) {
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.width(120.dp)) {
                                Text(Money.withSymbol(st?.payable ?: 0, b.account.currency), color = ExpenseColor)
                                Text(Money.withSymbol(b.balance, b.account.currency), style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Money.withSymbol(st?.outstanding ?: 0, b.account.currency), color = ExpenseColor,
                                modifier = Modifier.width(120.dp), textAlign = TextAlign.End)
                        }
                    }
                } else {
                    item(key = "g${type.name}") {
                        val total = list.filter { !it.account.archived }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
                        GrayHeader(type.label) { Text(Money.withSymbol(total, lk.base), color = balanceColor(total, MaterialTheme.colorScheme.onSurface)) }
                    }
                    items(list, key = { "a${it.account.id}" }) { b ->
                        AccountLine(b.account.name + if (b.account.archived) "（隱藏）" else "", { nav.navigate("acct/${b.account.id}") }) {
                            Text(Money.withSymbol(b.balance, b.account.currency), color = balanceColor(b.balance, MaterialTheme.colorScheme.onSurface))
                        }
                    }
                }
            }
            if (holdings.any { it.shares > 0 }) {
                item(key = "ginv") { GrayHeader("投資") { Text(Money.withSymbol(stockValue, lk.base), color = IncomeColor) } }
                item(key = "inv") {
                    AccountLine("股票持股（${holdings.count { it.shares > 0 }} 檔）", { nav.navigate("invest") }) {
                        Text(Money.withSymbol(stockValue, lk.base), color = IncomeColor)
                    }
                }
            }
            if (balances.isEmpty()) item { EmptyHint("點右上角 ⋮ → 新增帳戶") }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun AccountLine(name: String, onClick: () -> Unit, right: @Composable () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            right()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun SummaryBarCustom(cells: List<Pair<String, Pair<String, Color>>>) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            cells.forEach { (label, v) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(v.first, color = v.second, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val detailTabs = listOf("每日", "每月", "每年")

/** 單一帳戶：每日／每月／每年、存入提出與餘額、圖表模式。 */
@Composable
fun AccountDetailScreen(nav: NavController, id: Long) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    var month by rememberSaveable { mutableStateOf(Periods.monthOf(LocalDate.now(), repo.settings.value.monthStartDay)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var chart by rememberSaveable { mutableStateOf(false) }
    val b = balances.find { it.account.id == id }
    val acc = b?.account
    val current = b?.balance ?: 0
    val mine = remember(all, id) { all.filter { it.accountId == id || it.toAccountId == id } }

    val (start, end) = when (tab) {
        0 -> Periods.month(month, settings.monthStartDay)
        1 -> LocalDate.of(month.year, 1, 1) to LocalDate.of(month.year, 12, 31)
        else -> (mine.minOfOrNull { it.date.toLocalDate() } ?: LocalDate.now()) to LocalDate.now().plusYears(1)
    }
    val inRange = mine.filter { val d = it.date.toLocalDate(); !d.isBefore(start) && !d.isAfter(end) }
    val deposit = inRange.sumOf { maxOf(0, deltaFor(it, id)) }
    val withdrawal = -inRange.sumOf { minOf(0, deltaFor(it, id)) }
    val endBalance = balanceBefore(current, id, end.plusDays(1).toMillis(LocalTime.MIDNIGHT), all, trades)
    val cur = acc?.currency ?: lk.base

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text(acc?.name ?: "", maxLines = 1) },
                actions = {
                    if (tab != 2) {
                        IconButton(onClick = { month = if (tab == 0) month.minusMonths(1) else month.minusYears(1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                        Text(if (tab == 0) "${month.year}年${month.monthValue}月" else "${month.year}年")
                        IconButton(onClick = { month = if (tab == 0) month.plusMonths(1) else month.plusYears(1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                TabRow(
                    selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
                    indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
                ) {
                    detailTabs.forEachIndexed { i, t ->
                        Tab(tab == i, { tab = i }, text = { Text(t, fontSize = 16.sp) },
                            selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("結算期間", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (tab == 2) "全部" else "${start.monthValue}.${start.dayOfMonth}.${start.year % 100} ~ ${end.monthValue}.${end.dayOfMonth}.${end.year % 100}",
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { chart = !chart }) {
                        Icon(if (chart) Icons.AutoMirrored.Filled.List else Icons.Outlined.BarChart, if (chart) "列表" else "圖表")
                    }
                    IconButton(onClick = { nav.navigate("account/$id") }) { Icon(Icons.Outlined.Edit, "編輯帳戶") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryBarCustom(
                    listOf(
                        "存入" to (Money.full(deposit) to IncomeColor),
                        "提出" to (Money.full(withdrawal) to ExpenseColor),
                        "合計" to (Money.full(deposit - withdrawal) to MaterialTheme.colorScheme.onSurface),
                        "餘額" to (Money.full(endBalance) to balanceColor(endBalance, MaterialTheme.colorScheme.onSurface)),
                    ),
                )
            }
            if (chart) {
                item {
                    val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
                    val ranges = months.map { Periods.month(it, settings.monthStartDay) }
                    val ends = ranges.map { balanceBefore(current, id, it.second.plusDays(1).toMillis(LocalTime.MIDNIGHT), all, trades) }
                    val deps = ranges.map { r -> mine.filter { val d = it.date.toLocalDate(); !d.isBefore(r.first) && !d.isAfter(r.second) }.sumOf { maxOf(0, deltaFor(it, id)) } }
                    val wds = ranges.map { r -> -mine.filter { val d = it.date.toLocalDate(); !d.isBefore(r.first) && !d.isAfter(r.second) }.sumOf { minOf(0, deltaFor(it, id)) } }
                    val labels = months.map { "${it.monthValue}月" }
                    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                        LineChart(ends, labels, MaterialTheme.colorScheme.primary, autoMin = false)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        DualBarChart(deps, wds, labels, IncomeColor, ExpenseColor)
                    }
                }
            } else when (tab) {
                0 -> {
                    // 每筆交易後的帳戶餘額
                    val sorted = mine.sortedByDescending { it.date }
                    val after = HashMap<Long, Long>()
                    var running = current - trades.filter { LocalDate.ofEpochDay(it.date).toMillis(LocalTime.NOON) > (sorted.firstOrNull()?.date ?: 0) }.sumOf { tradeDelta(it, id) }
                    sorted.forEach { t -> after[t.id] = running; running -= deltaFor(t, id) }
                    if (inRange.isEmpty()) item { EmptyHint("這段期間沒有交易") }
                    inRange.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()).forEach { (d, txns) ->
                        item(key = "d$d") { DayBlock(d, txns, lk, nav, after) }
                    }
                }
                1 -> {
                    val months = (12 downTo 1).map { YearMonth.of(month.year, it) }.filter { !it.isAfter(YearMonth.now()) }
                    items(months, key = { "m$it" }) { m ->
                        val r = Periods.month(m, settings.monthStartDay)
                        val list = mine.filter { val d = it.date.toLocalDate(); !d.isBefore(r.first) && !d.isAfter(r.second) }
                        PeriodRow("${m.monthValue}月", "${r.first.monthValue}/${r.first.dayOfMonth} ~ ${r.second.monthValue}/${r.second.dayOfMonth}",
                            list.sumOf { maxOf(0, deltaFor(it, id)) }, -list.sumOf { minOf(0, deltaFor(it, id)) },
                            balanceBefore(current, id, r.second.plusDays(1).toMillis(LocalTime.MIDNIGHT), all, trades)) {
                            month = m; tab = 0
                        }
                    }
                }
                else -> {
                    val years = (LocalDate.now().year downTo start.year).toList()
                    items(years, key = { "y$it" }) { y ->
                        val list = mine.filter { it.date.toLocalDate().year == y }
                        PeriodRow("${y}年", "", list.sumOf { maxOf(0, deltaFor(it, id)) }, -list.sumOf { minOf(0, deltaFor(it, id)) },
                            balanceBefore(current, id, LocalDate.of(y + 1, 1, 1).toMillis(LocalTime.MIDNIGHT), all, trades)) {
                            month = YearMonth.of(y, 12); tab = 1
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PeriodRow(title: String, sub: String, deposit: Long, withdrawal: Long, balance: Long, onClick: () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    Text(Money.full(deposit), color = IncomeColor, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
                    Text(Money.full(withdrawal), color = ExpenseColor, modifier = Modifier.width(96.dp), textAlign = TextAlign.End)
                }
                Text("餘額 ${Money.full(balance)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 總資產統計：淨資產走勢與每月收支。 */
@Composable
fun TotalStatsScreen(nav: NavController) {
    val lk = rememberLookups()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    var month by rememberSaveable { mutableStateOf(Periods.monthOf(LocalDate.now(), repo.settings.value.monthStartDay)) }
    val counted = balances.filter { !it.account.archived && it.account.includeInNetWorth }
    val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
    val ranges = months.map { Periods.month(it, settings.monthStartDay) }
    val worth = ranges.map { r ->
        val cutoff = r.second.plusDays(1).toMillis(LocalTime.MIDNIGHT)
        counted.sumOf { lk.fx.convert(balanceBefore(it.balance, it.account.id, cutoff, all, trades), it.account.currency) }
    }
    val totals = ranges.map { r -> totalsOf(all.filter { val d = it.date.toLocalDate(); !d.isBefore(r.first) && !d.isAfter(r.second) }, lk.fx) }
    val labels = months.map { "${it.monthValue}月" }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BackTopBar("總資產統計", { nav.popBackStack() }) {
                IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "上個月") }
                Text("${month.year}年${month.monthValue}月")
                IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "下個月") }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(20.dp)) {
                    Text("餘額（不含股票）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.full(worth.last()), fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                LineChart(worth, labels, MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DualBarChart(totals.map { it.income }, totals.map { it.expense }, labels, IncomeColor, ExpenseColor, Modifier.padding(vertical = 12.dp))
            }
        }
    }
}

/** 調整帳戶顯示順序。 */
@Composable
fun AccountOrderScreen(nav: NavController) {
    val accounts by repo.accounts.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val sorted = accounts.sortedWith(compareBy({ it.type }, { it.sortOrder }, { it.id }))
    fun move(a: AccountEntity, dir: Int) {
        val group = sorted.filter { it.type == a.type }
        val idx = group.indexOf(a)
        val j = idx + dir
        if (j !in group.indices) return
        val reordered = group.toMutableList().apply { add(j, removeAt(idx)) }
        scope.launch { reordered.forEachIndexed { i, acc -> if (acc.sortOrder != i) repo.saveAccount(acc.copy(sortOrder = i)) } }
    }
    Scaffold(topBar = { BackTopBar("修改順序", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            sorted.groupBy { it.type }.forEach { (type, list) ->
                item(key = "h$type") { GrayHeader(type.label) }
                items(list, key = { it.id }) { a ->
                    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(a.name, Modifier.weight(1f))
                        IconButton(onClick = { move(a, -1) }) { Icon(Icons.Filled.ArrowUpward, "上移") }
                        IconButton(onClick = { move(a, 1) }) { Icon(Icons.Filled.ArrowDownward, "下移") }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}
