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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Payments
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
import com.enwei.moneymanager.ui.components.Text
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
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.groupLabel
import com.enwei.moneymanager.data.groupOrder
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
import com.enwei.moneymanager.ui.components.ReorderableColumn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

fun balanceColor(v: Long, default: Color) = when { v > 0 -> IncomeColor; v < 0 -> ExpenseColor; else -> default }

/** 某筆交易對指定帳戶餘額的影響。 */
fun deltaFor(t: TxnEntity, accountId: Long, fx: com.enwei.moneymanager.core.FxConverter? = null, accountCurrency: String? = null): Long {
    if (t.status != TxnStatus.CONFIRMED) return 0
    val amt = if (fx != null && accountCurrency != null) fx.convert(t.amount, t.currency, accountCurrency) else t.amount
    return when (t.type) {
        TxnType.EXPENSE -> if (t.accountId == accountId) -amt else 0
        TxnType.INCOME -> if (t.accountId == accountId) amt else 0
        TxnType.TRANSFER -> (if (t.accountId == accountId) -amt else 0) + (if (t.toAccountId == accountId) t.toAmount ?: amt else 0)
    }
}

/** 帳戶在指定時間點（不含）之前的餘額 = 目前餘額 − 之後所有變動（含股票交易，與帳戶餘額同一算法）。 */
fun balanceBefore(
    current: Long, accountId: Long, millis: Long, txns: List<TxnEntity>, trades: List<StockTradeEntity>,
    fx: com.enwei.moneymanager.core.FxConverter, accountCurrency: String,
): Long = balanceAt(current, millis, ledgerFor(accountId, accountCurrency, txns, trades, fx))

@Composable
private fun GrayHeader(title: String, right: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        right()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun AccountsTab(nav: NavController, showBack: Boolean = false, initialTab: Int = 0) {
    var section by rememberSaveable { mutableStateOf(initialTab) }
    var refreshTick by remember { mutableStateOf(0) }
    val snack = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val lk = rememberLookups()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val byBroker by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    fun stockOf(brokerId: Long?) = byBroker[brokerId].orEmpty().sumOf { lk.fx.convert(Money.toMinor(it.marketValue), it.currency) }
    var cards by remember { mutableStateOf<Map<Long, CardStatus>>(emptyMap()) }
    var menu by remember { mutableStateOf(false) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var funding by remember { mutableStateOf<List<com.enwei.moneymanager.data.Repository.FundingWarning>>(emptyList()) }
    var loanNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(all, balances) { cards = repo.cardStatus(); funding = repo.fundingWarnings(); loanNotes = repo.loanReminders(days = 7) }
    val st by repo.settings.flow.collectAsStateWithLifecycle()
    val groupRank = st.groupOrder()

    // 帳戶頁只放一般帳戶；證券帳戶與股票資產在「股票」分頁另外計算
    val visible = balances.filter { (showHidden || !it.account.archived) && it.account.type != AccountType.BROKERAGE }
        .sortedWith(compareBy({ it.account.sortOrder }, { it.account.id }))
    val counted = balances.filter { !it.account.archived && it.account.includeInNetWorth && it.account.type != AccountType.BROKERAGE }
    val assets = counted.filter { it.balance > 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
    val debts = -counted.filter { it.balance < 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = { Text(listOf("帳戶", "股票", "股票助理")[section]) },
                navigationIcon = {
                    if (showBack) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                  if (section == 2) Unit
                  else if (section == 1) {
                    IconButton(onClick = { refreshTick++ }) { Icon(Icons.Filled.Refresh, "更新股價") }
                    IconButton(onClick = { nav.navigate("stockfees") }) { Icon(Icons.Outlined.Settings, "手續費與月退") }
                    IconButton(onClick = { nav.navigate("trade/0") }) { Icon(Icons.Filled.Add, "新增股票交易") }
                  } else {
                    IconButton(onClick = { nav.navigate("totalstats") }) { Icon(Icons.Outlined.BarChart, "總資產統計") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "選單") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("新增") }, onClick = { menu = false; nav.navigate("account/0") })
                            DropdownMenuItem(text = { Text(if (showHidden) "隱藏已隱藏帳戶" else "顯示已隱藏帳戶") }, onClick = { menu = false; showHidden = !showHidden })
                            DropdownMenuItem(text = { Text("修改順序") }, onClick = { menu = false; nav.navigate("accountorder") })
                        }
                    }
                  }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
        TabRow(
            selectedTabIndex = section, containerColor = MaterialTheme.colorScheme.surface,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[section]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
        ) {
            listOf("帳戶", "股票", "助理").forEachIndexed { i, t ->
                Tab(section == i, { section = i }, text = { Text(t, fontSize = 16.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (section == 2) AdvisorContent(nav)
        else if (section == 1) StockContent(nav, refreshTick) { msg -> scope.launch { snack.showSnackbar(com.enwei.moneymanager.ui.tr(msg)) } }
        else LazyColumn(Modifier.fillMaxSize()) {
            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SummaryBarCustom(
                    listOf("資產" to (Money.full(assets) to IncomeColor), "負債" to (Money.full(debts) to ExpenseColor),
                        "合計" to (Money.full(assets - debts) to MaterialTheme.colorScheme.onSurface)),
                )
            }
            // 借貸：7 天內要收付款、快到期或已逾期
            if (loanNotes.isNotEmpty()) item(key = "loanNotes") {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("借貸提醒", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        loanNotes.forEach { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                }
            }
            // 扣款帳戶餘額不夠付接下來的卡費
            items(funding.size, key = { "fund${funding[it].from.id}" }) { i ->
                val w = funding[i]
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { nav.navigate("acct/${w.from.id}") },
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("「${w.from.name}」餘額不足，卡費可能扣款失敗", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            w.cards.joinToString("、") { (c, amt) -> "${c.name} ${Money.withSymbol(amt, c.currency)}" } +
                                "\n${w.date.monthValue}/${w.date.dayOfMonth} 起自動扣繳共 ${Money.withSymbol(w.needed, w.from.currency)}，" +
                                "目前餘額 ${Money.withSymbol(w.balance, w.from.currency)}，還差 ${Money.withSymbol(w.shortBy, w.from.currency)}",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
            visible.groupBy { it.account.type }.toSortedMap(compareBy { groupRank.indexOf(it) }).forEach { (type, list) ->
                if (type == AccountType.CREDIT_CARD) {
                    item(key = "g${type.name}") {
                        val payable = list.sumOf { cards[it.account.id]?.payable ?: 0 }
                        val outst = list.sumOf { cards[it.account.id]?.outstanding ?: 0 }
                        GrayHeader(st.groupLabel(type)) {
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
                    val isBroker = type == AccountType.BROKERAGE
                    item(key = "g${type.name}") {
                        val total = list.filter { !it.account.archived }.sumOf {
                            lk.fx.convert(it.balance, it.account.currency) + if (isBroker) stockOf(it.account.id) else 0
                        }
                        GrayHeader(st.groupLabel(type)) { Text(Money.withSymbol(total, lk.base), color = balanceColor(total, MaterialTheme.colorScheme.onSurface)) }
                    }
                    items(list, key = { "a${it.account.id}" }) { b ->
                        val a = b.account
                        val sub = when (a.type) {
                            AccountType.BROKERAGE -> {
                                val n = byBroker[a.id].orEmpty().count { it.shares > 0 }
                                listOfNotNull("持股 $n 檔", lk.account(a.linkedAccountId)?.let { "交割：${it.name}" }).joinToString(" · ")
                            }
                            AccountType.EWALLET, AccountType.TRANSIT_CARD -> lk.account(a.linkedAccountId)?.let { "儲值來源：${it.name}" }
                            AccountType.LOAN -> {
                                val info = st.loans[a.id]
                                val due = info?.dueDay?.let(LocalDate::ofEpochDay)
                                listOfNotNull(
                                    info?.counterparty?.takeIf { it.isNotBlank() },
                                    if (b.balance > 0) "別人欠你" else if (b.balance < 0) "你欠別人" else "已結清",
                                    due?.takeIf { b.balance != 0L }?.let { if (it.isBefore(LocalDate.now())) "已逾期" else "${it.monthValue}/${it.dayOfMonth} 到期" },
                                ).joinToString(" · ")
                            }
                            else -> null
                        }
                        // 證券帳戶：小字標示持股損益（同券商預估損益算法）
                        val pnlNote = if (isBroker) byBroker[a.id].orEmpty().filter { it.shares > 0 }.takeIf { it.isNotEmpty() }?.let { hs ->
                            val pnl = hs.sumOf { lk.fx.convert(Money.toMinor(it.unrealized), it.currency) }
                            val cost = hs.sumOf { lk.fx.convert(Money.toMinor(it.netCost), it.currency) }
                            val pct = if (cost > 0) pnl * 100.0 / cost else 0.0
                            "股票損益 ${if (pnl > 0) "+" else ""}${Money.full(pnl)}（${"%+.2f".format(pct)}%）" to
                                (if (pnl >= 0) com.enwei.moneymanager.ui.StockUp else com.enwei.moneymanager.ui.StockDown)
                        } else null
                        AccountLine(a.name + if (a.archived) "（隱藏）" else "", { nav.navigate("acct/${a.id}") }, sub, pnlNote) {
                            if (isBroker) {
                                val v = lk.fx.convert(b.balance, a.currency) + stockOf(a.id)
                                Text(Money.withSymbol(v, lk.base), color = balanceColor(v, MaterialTheme.colorScheme.onSurface))
                            } else Text(Money.withSymbol(b.balance, a.currency), color = balanceColor(b.balance, MaterialTheme.colorScheme.onSurface))
                        }
                    }
                }
            }
            if (balances.isEmpty()) item { EmptyHint("點右上角 ⋮ → 新增帳戶") }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
    }
}

@Composable
private fun AccountLine(
    name: String, onClick: () -> Unit, sub: String? = null,
    /** 第二行的小字（例如股票損益，可自訂顏色） */
    note: Pair<String, Color>? = null,
    right: @Composable () -> Unit,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = if (sub != null || note != null) 12.dp else 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (!sub.isNullOrEmpty()) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                note?.let { (t, c) -> Text(t, fontSize = 12.sp, color = c) }
            }
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
    val cur = acc?.currency ?: lk.base
    val current = b?.balance ?: 0
    // 本帳戶的全部明細（一般交易＋股票交易），所有數字都由這份資料計算
    val ledger = remember(all, trades, id, lk.fx, cur) { ledgerFor(id, cur, all, trades, lk.fx) }

    val (start, end) = when (tab) {
        0 -> Periods.month(month, settings.monthStartDay)
        1 -> LocalDate.of(month.year, 1, 1) to LocalDate.of(month.year, 12, 31)
        else -> (ledger.minOfOrNull { it.date.toLocalDate() } ?: LocalDate.now()) to LocalDate.now().plusYears(1)
    }
    fun entriesIn(a: LocalDate, b: LocalDate) = ledger.filter { val d = it.date.toLocalDate(); !d.isBefore(a) && !d.isAfter(b) }
    val inRange = entriesIn(start, end)
    val deposit = inRange.sumOf { maxOf(0, it.delta) }
    val withdrawal = -inRange.sumOf { minOf(0, it.delta) }
    val endBalance = balanceAt(current, end.plusDays(1).toMillis(LocalTime.MIDNIGHT), ledger)
    var reconcileOpen by remember { mutableStateOf(false) }
    val byBrokerDetail by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    var cardInfo by remember { mutableStateOf<Map<Long, CardStatus>>(emptyMap()) }
    LaunchedEffect(all) { cardInfo = repo.cardStatus() }
    val scope = rememberCoroutineScope()

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
                    IconButton(onClick = { reconcileOpen = true }) { Icon(Icons.Outlined.FactCheck, "對帳") }
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
            if (acc?.type == AccountType.BROKERAGE) item {
                val list = byBrokerDetail[acc.id].orEmpty()
                val value = list.sumOf { Money.toMinor(it.marketValue) }
                val open = list.filter { it.shares > 0 }
                val cost = open.sumOf { Money.toMinor(it.netCost) }
                val pnl = open.sumOf { Money.toMinor(it.unrealized) }
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("持股市值 ${Money.withSymbol(value)}", fontSize = 17.sp)
                            Text("原幣成本 ${Money.full(cost)} · 預估損益 ${Money.full(pnl)}", fontSize = 13.sp,
                                color = if (pnl >= 0) com.enwei.moneymanager.ui.StockUp else com.enwei.moneymanager.ui.StockDown)
                            Text("交割帳戶：${lk.account(acc.linkedAccountId)?.name ?: "未設定（按右上編輯）"}", fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.OutlinedButton(onClick = { nav.navigate("trade/0?broker=${acc.id}") }) { Text("＋ 交易") }
                    }
                    list.filter { it.shares > 0 }.forEach { h ->
                        Row(Modifier.fillMaxWidth().clickable { nav.navigate("holding/${h.market}/${h.symbol}") }.padding(vertical = 8.dp)) {
                            Text("${h.symbol} ${h.name.orEmpty()}", Modifier.weight(1f))
                            Text("${formatShares(h.shares)}　${Money.withSymbol(Money.toMinor(h.marketValue), h.currency)}")
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (acc?.type == AccountType.CREDIT_CARD) item {
                val st = cardInfo[acc.id]
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("應繳 ${Money.withSymbol(st?.payable ?: 0, acc.currency)}" + (st?.dueDate?.let { "　繳款日 ${it.monthValue}/${it.dayOfMonth}" } ?: ""), fontSize = 16.sp)
                        Text("未出帳 ${Money.withSymbol(st?.outstanding ?: 0, acc.currency)}" + if (acc.creditLimit > 0) "　可用額度 ${Money.full(acc.creditLimit + current)}" else "",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            listOfNotNull(
                                acc.statementDay?.let { "結帳日 $it 日" },
                                settings.cardDueOffset[acc.id]?.let { "結帳後 $it 天繳款" } ?: acc.dueDay?.let { "繳款日 $it 日" },
                                st?.autopayDate?.let { "${it.monthValue}/${it.dayOfMonth} 由「${lk.account(acc.autopayFromId)?.name}」自動扣繳" } ?: "未設定自動扣繳",
                            ).joinToString(" · "),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        nav.navigate("txn/0?type=TRANSFER&account=${acc.autopayFromId ?: 0}&to=${acc.id}&amount=${st?.payable ?: 0}")
                    }) { Text("繳卡費") }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (acc?.type == AccountType.LOAN) item { LoanCard(acc, current, lk, nav) }
            if (chart) {
                item {
                    val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
                    val ranges = months.map { Periods.month(it, settings.monthStartDay) }
                    val cashEnds = ranges.map { balanceAt(current, it.second.plusDays(1).toMillis(LocalTime.MIDNIGHT), ledger) }
                    // 證券帳戶：折線＝現金＋當時的持股市值（月底收盤價），買股票不會看起來像資產歸零
                    val isBroker = acc?.type == AccountType.BROKERAGE
                    val brokerTrades = if (isBroker) trades.filter { it.brokerAccountId == id } else emptyList()
                    val symbols = brokerTrades.map { it.market to it.symbol.uppercase() }.distinct()
                    var closes by remember(id) { mutableStateOf<Map<String, Map<YearMonth, Double>>>(emptyMap()) }
                    LaunchedEffect(symbols) {
                        symbols.filter { "${it.first}:${it.second}" !in closes }.forEach { (mk, sym) ->
                            val c = runCatching { com.enwei.moneymanager.net.QuoteService.fetchMonthlyCloses(mk, sym) }.getOrNull() ?: return@forEach
                            closes = closes + ("$mk:$sym" to c)
                        }
                    }
                    val live = byBrokerDetail[id].orEmpty().filter { it.shares > 0 }.associate { "${it.market}:${it.symbol.uppercase()}" to it.marketValue / it.shares }
                    val ends = if (!isBroker) cashEnds else ranges.mapIndexed { i, r ->
                        val endDay = r.second.toEpochDay()
                        val ym = YearMonth.from(r.second)
                        val value = brokerTrades.filter { it.date <= endDay }.groupBy { "${it.market}:${it.symbol.uppercase()}" }.entries.sumOf { (k, list) ->
                            val shares = list.sumOf {
                                when (it.side) {
                                    com.enwei.moneymanager.core.StockSide.BUY, com.enwei.moneymanager.core.StockSide.STOCK_DIVIDEND -> it.shares
                                    com.enwei.moneymanager.core.StockSide.SELL -> -it.shares
                                    else -> 0.0
                                }
                            }
                            if (shares <= 1e-9) return@sumOf 0.0
                            val hist = closes[k].orEmpty()
                            val price = (if (!ym.isBefore(YearMonth.now())) live[k] else null)
                                ?: hist[ym] ?: hist.filterKeys { !it.isAfter(ym) }.maxByOrNull { it.key }?.value
                                ?: list.filter { it.price > 0 }.maxByOrNull { it.date }?.price ?: 0.0
                            shares * price
                        }
                        cashEnds[i] + Money.toMinor(value)
                    }
                    val deps = ranges.map { r -> entriesIn(r.first, r.second).sumOf { maxOf(0, it.delta) } }
                    val wds = ranges.map { r -> -entriesIn(r.first, r.second).sumOf { minOf(0, it.delta) } }
                    val labels = months.map { "${it.monthValue}月" }
                    Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp)) {
                        LineChart(ends, labels, MaterialTheme.colorScheme.primary, autoMin = false)
                        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        DualBarChart(deps, wds, labels, IncomeColor, ExpenseColor)
                        if (isBroker) Text(
                            "折線：現金＋持股市值　長條：存入／提出",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
            } else when (tab) {
                0 -> {
                    // 每筆（含股票交易）之後的帳戶餘額
                    val after = runningBalances(current, ledger)
                    val txnAfter = ledger.mapNotNull { e -> e.txn?.let { it.id to after.getValue(e.key) } }.toMap()
                    val tradeAfter = ledger.mapNotNull { e -> e.trade?.let { it.id to after.getValue(e.key) } }.toMap()
                    if (inRange.isEmpty()) item { EmptyHint("這段期間沒有交易") }
                    inRange.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()).forEach { (d, entries) ->
                        item(key = "d$d") {
                            DayBlock(d, entries.mapNotNull { it.txn }, lk, nav, txnAfter, entries.mapNotNull { it.trade }, tradeAfter, id, ledger = entries)
                        }
                    }
                }
                1 -> {
                    val months = (12 downTo 1).map { YearMonth.of(month.year, it) }.filter { !it.isAfter(YearMonth.now()) }
                    items(months, key = { "m$it" }) { m ->
                        val r = Periods.month(m, settings.monthStartDay)
                        val list = entriesIn(r.first, r.second)
                        PeriodRow("${m.monthValue}月", "${r.first.monthValue}/${r.first.dayOfMonth} ~ ${r.second.monthValue}/${r.second.dayOfMonth}",
                            list.sumOf { maxOf(0, it.delta) }, -list.sumOf { minOf(0, it.delta) },
                            balanceAt(current, r.second.plusDays(1).toMillis(LocalTime.MIDNIGHT), ledger)) {
                            month = m; tab = 0
                        }
                    }
                }
                else -> {
                    val years = (LocalDate.now().year downTo start.year).toList()
                    items(years, key = { "y$it" }) { y ->
                        val list = ledger.filter { it.date.toLocalDate().year == y }
                        PeriodRow("${y}年", "", list.sumOf { maxOf(0, it.delta) }, -list.sumOf { minOf(0, it.delta) },
                            balanceAt(current, LocalDate.of(y + 1, 1, 1).toMillis(LocalTime.MIDNIGHT), ledger)) {
                            month = YearMonth.of(y, 12); tab = 1
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (reconcileOpen && acc != null) {
        var actualText by remember { mutableStateOf("") }
        var catId by remember { mutableStateOf(lk.categories.firstOrNull { it.kind == TxnType.EXPENSE && it.name == "其他" }?.id) }
        var catMenu by remember { mutableStateOf(false) }
        val actual = Money.parseMinor(actualText)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { reconcileOpen = false },
            title = { Text("對帳：${acc.name}") },
            text = {
                Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                    Text("帳理目前餘額：${Money.withSymbol(current, acc.currency)}")
                    androidx.compose.material3.OutlinedTextField(
                        actualText, { actualText = it.filter { c -> c.isDigit() || c == '.' || c == '-' } },
                        label = { Text("App 裡的實際餘額") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    )
                    if (actual != null) {
                        val diff = actual - current
                        Text(
                            when {
                                diff < 0 -> "將記一筆支出 ${Money.withSymbol(-diff, acc.currency)}"
                                diff > 0 -> "將記一筆收入 ${Money.withSymbol(diff, acc.currency)}"
                                else -> "餘額相符，不需調整"
                            },
                            color = if (diff < 0) ExpenseColor else IncomeColor,
                        )
                        if (diff < 0) Box {
                            Text("分類：${lk.category(catId)?.name ?: "未分類"} ▾", color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { catMenu = true }.padding(vertical = 6.dp))
                            DropdownMenu(catMenu, { catMenu = false }) {
                                lk.categories.filter { it.kind == TxnType.EXPENSE && !it.archived }.forEach { c ->
                                    DropdownMenuItem(text = { Text(c.name) }, onClick = { catId = c.id; catMenu = false })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = actual != null, onClick = {
                    val a = actual ?: return@TextButton
                    scope.launch { repo.reconcile(acc, current, a, if (a < current) catId else null) }
                    reconcileOpen = false
                }) { Text("確定") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { reconcileOpen = false }) { Text("取消") } },
        )
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
        counted.sumOf { lk.fx.convert(balanceBefore(it.balance, it.account.id, cutoff, all, trades, lk.fx, it.account.currency), it.account.currency) }
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
    val st by repo.settings.flow.collectAsStateWithLifecycle()
    val rank = st.groupOrder()
    val sorted = accounts.sortedWith(compareBy({ rank.indexOf(it.type) }, { it.sortOrder }, { it.id }))
    Scaffold(topBar = { BackTopBar("修改順序", { nav.popBackStack() }) }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            sorted.groupBy { it.type }.forEach { (type, list) ->
                item(key = "h$type") { GrayHeader(st.groupLabel(type)) }
                item(key = "a$type") {
                    ReorderableColumn(list, { it.id }, { reordered ->
                        scope.launch {
                            reordered.forEachIndexed { index, account ->
                                if (account.sortOrder != index) repo.saveAccount(account.copy(sortOrder = index))
                            }
                        }
                    }) { account, handle ->
                        Column {
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                    Text(account.name)
                                    account.description?.takeIf { it.isNotBlank() }?.let {
                                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.DragHandle, "拖曳排序", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

fun formatShares(shares: Double): String =
    if (shares >= 1000 && shares % 1000 == 0.0) "${(shares / 1000).toLong()} 張" else "${com.enwei.moneymanager.ui.components.formatNum(shares, 0)} 股"
