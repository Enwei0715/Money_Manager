package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
import com.enwei.moneymanager.ui.components.groupCell
import androidx.compose.foundation.lazy.itemsIndexed

import androidx.compose.material.icons.outlined.People

import androidx.compose.material.icons.outlined.ShowChart

import androidx.compose.material.icons.outlined.ConfirmationNumber

import androidx.compose.material.icons.outlined.AccountBalanceWallet

import androidx.compose.material.icons.outlined.CreditCard

import androidx.compose.material.icons.outlined.AccountBalance

import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.background
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.text.style.TextOverflow
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
private fun GrayHeader(title: String, right: @Composable RowScope.() -> Unit = {}) {
    val cards = com.enwei.moneymanager.ui.AppearancePrefs.cards
    if (!cards) com.enwei.moneymanager.ui.components.LineDivider()
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)
            .padding(start = if (cards) 24.dp else 16.dp, end = if (cards) 24.dp else 16.dp, top = if (cards) 20.dp else 14.dp, bottom = if (cards) 8.dp else 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppText.caption, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit(0.8f, androidx.compose.ui.unit.TextUnitType.Sp))
        right()
    }
    if (!cards) com.enwei.moneymanager.ui.components.LineDivider()
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AccountsTab(nav: NavController, showBack: Boolean = false, initialTab: Int = 0) {
    // 第一次打開這頁：介紹帳戶、信用卡、往來與股票
    run { val fs = repo.settings.value
    com.enwei.moneymanager.ui.components.PageTour("accounts", listOfNotNull(
        com.enwei.moneymanager.ui.components.Coach.Step("ac.tab0", "帳戶", "所有帳戶依群組列出，頂端為資產、負債與合計；點選帳戶可查看交易紀錄與餘額走勢，右上角 ⋮ →「修改順序」可調整排序。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "信用卡", "依帳單週期計算本期應繳與未出帳金額；設定自動扣款帳戶後，繳款日會自動記錄轉帳。"),
        if (fs.featureSplit) com.enwei.moneymanager.ui.components.Coach.Step(null, "往來", "與他人的借貸、代墊與分帳集中於「往來」，列出每位對象的應收與應付。") else null,
        if (fs.featureStocks) com.enwei.moneymanager.ui.components.Coach.Step("ac.tab1", "股票", "持股、成本與損益；可新增交易、設定價格提醒，點選個股查看 K 線與分析。") else null,
    )) }
    // 往下捲時標題列與分頁列收起，內容用滿畫面；往上捲立刻出現
    val topScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // 叫回收起的標題列（換分頁、切換檢視時）
    LaunchedEffect(com.enwei.moneymanager.ui.components.Chrome.resetTick) { topScroll.state.heightOffset = 0f }
    var section by com.enwei.moneymanager.ui.components.rememberTab("accounts.section", initialTab)
    // 從「助理」入口打開時直接到助理分頁
    remember { if (initialTab != 0) section = initialTab }
    // 狀態列、通知要求切到某個區塊
    androidx.compose.runtime.LaunchedEffect(com.enwei.moneymanager.ui.TabRequest.accountsSection) {
        com.enwei.moneymanager.ui.TabRequest.accountsSection?.let { section = it; com.enwei.moneymanager.ui.TabRequest.accountsSection = null }
    }
    // 整體導覽介紹往來時，切回「帳戶」分頁才看得到
    LaunchedEffect(com.enwei.moneymanager.ui.components.Coach.step) {
        if (com.enwei.moneymanager.ui.components.Coach.active.getOrNull(com.enwei.moneymanager.ui.components.Coach.step)?.target in setOf("ac.debts", "ac.tab0")) section = 0
    }
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
    // 借貸對象不放在帳戶清單，集中在「往來」
    val visible = balances.filter { (showHidden || !it.account.archived) && it.account.type != AccountType.BROKERAGE && it.account.type != AccountType.LOAN }
    val people = balances.filter { it.account.type == AccountType.LOAN && !it.account.archived }
        .sortedWith(compareBy({ it.account.sortOrder }, { it.account.id }))
    val counted = balances.filter { !it.account.archived && it.account.includeInNetWorth && it.account.type != AccountType.BROKERAGE }
    val assets = counted.filter { it.balance > 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
    val debts = -counted.filter { it.balance < 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }

    Scaffold(
        modifier = Modifier.nestedScroll(topScroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            com.enwei.moneymanager.ui.ChromeScale { TopAppBar(
                scrollBehavior = topScroll,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, scrolledContainerColor = MaterialTheme.colorScheme.surface),
                title = { Text("資產") },
                navigationIcon = {
                    if (showBack) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
                actions = {
                  if (section == 1) {
                    IconButton(onClick = { nav.navigate("stocksearch") }) { Icon(Icons.Outlined.Search, "搜尋股票") }
                    IconButton(onClick = { refreshTick++ }) { Icon(Icons.Filled.Refresh, "更新股價") }
                    IconButton(onClick = { nav.navigate("stockfees") }) { Icon(com.enwei.moneymanager.ui.components.LineIcons.ToolReceipt, "手續費與月退") }
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
            ) }
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
        val fitGroup1 = com.enwei.moneymanager.ui.components.rememberFitGroup()
        // 設定裡關掉股票功能：只剩帳戶
        val stocksOn = repo.settings.flow.collectAsStateWithLifecycle().value.featureStocks
        if (!stocksOn && section != 0) section = 0
        // 助理已移到底部分頁：舊的記憶位置改回股票
        if (section > 1) section = 1
        com.enwei.moneymanager.ui.components.CollapsingTop(Modifier.fillMaxSize(), header = {
        if (stocksOn) com.enwei.moneymanager.ui.ChromeScale { TabRow(
            selectedTabIndex = section, containerColor = MaterialTheme.colorScheme.surface,
            indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[section]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
        ) {
            (if (stocksOn) listOf("帳戶", "股票") else listOf("帳戶")).forEachIndexed { i, t ->
                Tab(section == i, { section = i }, modifier = Modifier.coachTarget("ac.tab$i"), text = { com.enwei.moneymanager.ui.components.FitText(t, fontSize = AppText.body, group = fitGroup1) },
                    selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } }
        }) {
        if (section == 1) StockContent(nav, refreshTick) { msg -> scope.launch { snack.showSnackbar(com.enwei.moneymanager.ui.tr(msg)) } }
        else LazyColumn(Modifier.fillMaxSize()) {
            item {
                com.enwei.moneymanager.ui.components.LineDivider()
                // 淨資產是這一頁最重要的數字：單獨一行放大；資產、負債放在下面
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    verticalAlignment = Alignment.Bottom) {
                    Text("淨資產", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(Money.withSymbol(assets - debts, lk.base), fontSize = AppText.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                SummaryBarCustom(
                    listOf("資產" to (Money.full(assets) to IncomeColor), "負債" to (Money.full(debts) to ExpenseColor)),
                )
            }
            // 往來：別人欠我、我欠別人（借貸與代墊），點進去看每個人
            if (st.featureSplit) item(key = "debts") {
                val owed = people.filter { it.balance > 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
                val owe = -people.filter { it.balance < 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).coachTarget("ac.debts").groupCell(true, true).clickable { nav.navigate("debts") }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("往來", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (people.isEmpty()) "借貸與代墊" else "${people.count { it.balance != 0L }} 人未結清",
                            fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        if (owed > 0) Text("別人欠我 ${Money.withSymbol(owed, lk.base)}", fontSize = AppText.secondary, color = IncomeColor)
                        if (owe > 0) Text("我欠別人 ${Money.withSymbol(owe, lk.base)}", fontSize = AppText.secondary, color = ExpenseColor)
                        if (owed == 0L && owe == 0L) Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // 借貸：7 天內要收付款、快到期或已逾期
            if (st.featureSplit && loanNotes.isNotEmpty()) item(key = "loanNotes") {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("借貸提醒", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        loanNotes.forEach { Text(it, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSecondaryContainer) }
                    }
                }
            }
            // 扣款帳戶餘額不夠付接下來的卡費
            items(funding.size, key = { "fund${funding[it].from.id}" }) { i ->
                val w = funding[i]
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.errorContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable { nav.navigate("acct/${w.from.id}") },
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("「${w.from.name}」餘額不足，卡費可能扣款失敗", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            w.cards.joinToString("、") { (c, amt) -> "${c.name} ${Money.withSymbol(amt, c.currency)}" } +
                                "\n${w.date.monthValue}/${w.date.dayOfMonth} 起自動扣繳共 ${Money.withSymbol(w.needed, w.from.currency)}，" +
                                "目前餘額 ${Money.withSymbol(w.balance, w.from.currency)}，還差 ${Money.withSymbol(w.shortBy, w.from.currency)}",
                            fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onErrorContainer,
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
                            com.enwei.moneymanager.ui.components.HelpIcon("應繳、未出帳是什麼", com.enwei.moneymanager.ui.components.HelpText.CREDIT, Modifier.size(24.dp))
                        }
                        Column(Modifier.fillMaxWidth().groupCell(true, false).padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Column {
                                CreditCardMetrics(
                                    listOf(
                                        Triple("應繳餘額", Money.withSymbol(payable, lk.base), ExpenseColor),
                                        Triple("未出帳", Money.withSymbol(outst, lk.base), ExpenseColor),
                                        Triple("卡片數", list.size.toString(), MaterialTheme.colorScheme.onSurfaceVariant),
                                    ),
                                )
                            }
                        }
                    }
                    itemsIndexed(list, key = { _, it -> "a${it.account.id}" }) { idx, b ->
                        val st = cards[b.account.id]
                        val name = b.account.name + if (b.account.archived) "（隱藏）" else ""
                        com.enwei.moneymanager.ui.components.RowDivider(Modifier.padding(horizontal = if (com.enwei.moneymanager.ui.AppearancePrefs.cards) 12.dp else 0.dp))
                        Column(
                            Modifier.fillMaxWidth().groupCell(false, idx == list.lastIndex)
                                .clickable { nav.navigate("acct/${b.account.id}") }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            CreditCardMetrics(
                                listOf(
                                    Triple("應繳餘額", Money.withSymbol(st?.payable ?: 0, b.account.currency), ExpenseColor),
                                    Triple("未出帳", Money.withSymbol(st?.outstanding ?: 0, b.account.currency), ExpenseColor),
                                    Triple("帳戶餘額", Money.withSymbol(b.balance, b.account.currency), MaterialTheme.colorScheme.onSurfaceVariant),
                                ),
                            )
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
                    itemsIndexed(list, key = { _, it -> "a${it.account.id}" }) { idx, b ->
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
                        AccountLine(a.name + if (a.archived) "（隱藏）" else "", { nav.navigate("acct/${a.id}") }, sub, pnlNote, type = a.type, first = idx == 0, last = idx == list.lastIndex) {
                            if (isBroker) {
                                val v = lk.fx.convert(b.balance, a.currency) + stockOf(a.id)
                                Text(Money.withSymbol(v, lk.base), color = balanceColor(v, MaterialTheme.colorScheme.onSurface))
                            } else Text(Money.withSymbol(b.balance, a.currency), color = balanceColor(b.balance, MaterialTheme.colorScheme.onSurface))
                        }
                    }
                }
            }
            if (balances.isEmpty()) item { EmptyHint("點右上角 ⋮ →「新增」建立帳戶") }
            item { Spacer(Modifier.height(24.dp)) }
        }
        }
        }
    }
}

@Composable
private fun AccountLine(
    name: String, onClick: () -> Unit, sub: String? = null,
    /** 第二行的小字（例如股票損益，可自訂顏色） */
    note: Pair<String, Color>? = null,
    /** 帳戶類型：左邊顯示對應的小圖示 */
    type: AccountType? = null,
    first: Boolean = true, last: Boolean = true,
    right: @Composable RowScope.() -> Unit,
) {
    Column(Modifier.groupCell(first, last)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = if (sub != null || note != null) 11.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (type != null && com.enwei.moneymanager.ui.AppearancePrefs.icons) {
                val icon = com.enwei.moneymanager.ui.components.Symbols.account(type)
                Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.surfaceVariant, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                if (!sub.isNullOrEmpty()) Text(sub, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                note?.let { (t, c) -> Text(t, fontSize = AppText.caption, color = c) }
            }
            right()
        }
        if (!last) com.enwei.moneymanager.ui.components.RowDivider(Modifier.padding(start = if (type != null && com.enwei.moneymanager.ui.AppearancePrefs.icons) 64.dp else 16.dp))
    }
}

@Composable
private fun CreditCardMetric(label: String, value: String, color: Color, modifier: Modifier, small: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, color = color, fontSize = if (small) AppText.secondary else AppText.body, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CreditCardMetrics(metrics: List<Triple<String, String, Color>>) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 一律排成一列三欄（不再把第三項擠到下一行）；窄螢幕時金額字小一級
        val columns = if (maxWidth < 240.dp) 2 else metrics.size.coerceAtMost(3)
        val small = maxWidth < 360.dp
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (label, value, color) ->
                        CreditCardMetric(label, value, color, Modifier.weight(1f), small)
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun SummaryBarCustom(cells: List<Pair<String, Pair<String, Color>>>) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            val columns = when {
                cells.size >= 4 && maxWidth < 480.dp -> 2
                maxWidth < 360.dp && cells.size > 2 -> 2
                else -> cells.size.coerceAtLeast(1)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cells.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { (label, value) ->
                            Column(Modifier.weight(if (row.size == 1) columns.toFloat() else 1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                                com.enwei.moneymanager.ui.components.FitText(value.first, color = value.second,
                                    fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = MaterialTheme.typography.titleMedium.fontWeight)
                            }
                        }
                    }
                }
            }
        }
        com.enwei.moneymanager.ui.components.LineDivider()
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
    var tab by com.enwei.moneymanager.ui.components.rememberTab("account.detail")
    var chart by rememberSaveable { mutableStateOf(false) }
    val b = balances.find { it.account.id == id }
    val acc = b?.account
    val cur = acc?.currency ?: lk.base
    val current = b?.balance ?: 0
    // 本帳戶的全部明細（一般交易＋股票交易），所有數字都由這份資料計算
    val ledger = remember(all, trades, id, lk.fx, cur) { ledgerFor(id, cur, all, trades, lk.fx) }
    // 信用卡依每期帳單計算：上次結帳日隔天 ~ 這次結帳日（「11 月帳單」= 10/18 ~ 11/17）
    val sDay = acc?.takeIf { it.type == AccountType.CREDIT_CARD }?.statementDay
    fun stmt(m: YearMonth) = m.atDay(minOf(sDay ?: 1, m.lengthOfMonth()))
    fun period(m: YearMonth): Pair<LocalDate, LocalDate> =
        if (sDay != null) stmt(m.minusMonths(1)).plusDays(1) to stmt(m) else Periods.month(m, settings.monthStartDay)
    val thisPeriod = if (sDay != null) YearMonth.now().let { if (LocalDate.now().isAfter(stmt(it))) it.plusMonths(1) else it }
        else Periods.monthOf(LocalDate.now(), settings.monthStartDay)
    // 第一次打開信用卡：停在目前這一期（帳戶資料載入後才知道結帳日）
    var cardInit by rememberSaveable(id) { mutableStateOf(false) }
    LaunchedEffect(sDay) { if (sDay != null && !cardInit) { cardInit = true; month = thisPeriod } }

    val (start, end) = when (tab) {
        0 -> period(month)
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
            com.enwei.moneymanager.ui.ChromeScale { TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                title = { Text(acc?.name ?: "", maxLines = 1) },
                actions = {
                    if (tab != 2) {
                        IconButton(onClick = { month = if (tab == 0) month.minusMonths(1) else month.minusYears(1) }) { Icon(Icons.Filled.ChevronLeft, "上一期") }
                        Text(if (tab == 0) "${month.year}年${month.monthValue}月" + (if (sDay != null) "帳單" else "") else "${month.year}年")
                        IconButton(onClick = { month = if (tab == 0) month.plusMonths(1) else month.plusYears(1) }) { Icon(Icons.Filled.ChevronRight, "下一期") }
                    }
                },
            ) }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize()) {
            item {
                val fitGroup2 = com.enwei.moneymanager.ui.components.rememberFitGroup()
                com.enwei.moneymanager.ui.ChromeScale { TabRow(
                    selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface,
                    indicator = { pos -> TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), height = 3.dp, color = MaterialTheme.colorScheme.primary) },
                ) {
                    detailTabs.forEachIndexed { i, t ->
                        Tab(tab == i, { tab = i }, text = { com.enwei.moneymanager.ui.components.FitText(t, fontSize = AppText.body, group = fitGroup2) },
                            selectedContentColor = MaterialTheme.colorScheme.onSurface, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("結算期間", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (tab == 2) "全部" else "${start.monthValue}.${start.dayOfMonth}.${start.year % 100} ~ ${end.monthValue}.${end.dayOfMonth}.${end.year % 100}",
                            fontSize = AppText.title, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = { reconcileOpen = true }) { Icon(Icons.Outlined.FactCheck, "對帳") }
                    IconButton(onClick = { chart = !chart }) {
                        Icon(if (chart) Icons.AutoMirrored.Filled.List else Icons.Outlined.BarChart, if (chart) "列表" else "圖表")
                    }
                    IconButton(onClick = { nav.navigate("account/$id") }) { Icon(Icons.Outlined.Edit, "編輯帳戶") }
                }
                com.enwei.moneymanager.ui.components.LineDivider()
                val isLoan = acc?.type == AccountType.LOAN
                SummaryBarCustom(
                    listOf(
                        // 往來對象：用借出、收回的說法；其他帳戶用存入、提出
                        (if (isLoan) "借出" else "存入") to (Money.full(deposit) to IncomeColor),
                        (if (isLoan) "收回" else "提出") to (Money.full(withdrawal) to ExpenseColor),
                        (if (isLoan) "期間淨額" else "合計") to (Money.full(deposit - withdrawal) to MaterialTheme.colorScheme.onSurface),
                        (if (isLoan) (if (endBalance >= 0) "對方欠你" else "你欠對方") else "餘額") to
                            (Money.full(if (isLoan) kotlin.math.abs(endBalance) else endBalance) to balanceColor(endBalance, MaterialTheme.colorScheme.onSurface)),
                    ),
                )
            }
            if (acc?.type == AccountType.BROKERAGE) item {
                val list = byBrokerDetail[acc.id].orEmpty()
                val value = list.sumOf { Money.toMinor(it.marketValue) }
                val open = list.filter { it.shares > 0 }
                val cost = open.sumOf { Money.toMinor(it.netCost) }
                val pnl = open.sumOf { Money.toMinor(it.unrealized) }
                Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("持股市值 ${Money.withSymbol(value)}", fontSize = AppText.body)
                            Text("原幣成本 ${Money.full(cost)} · 預估損益 ${Money.full(pnl)}", fontSize = AppText.secondary,
                                color = if (pnl >= 0) com.enwei.moneymanager.ui.StockUp else com.enwei.moneymanager.ui.StockDown)
                            Text("交割帳戶：${lk.account(acc.linkedAccountId)?.name ?: "未設定（點選右上角編輯）"}", fontSize = AppText.secondary,
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
                com.enwei.moneymanager.ui.components.LineDivider()
            }
            if (acc?.type == AccountType.CREDIT_CARD) item {
                val st = cardInfo[acc.id]
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("應繳 ${Money.withSymbol(st?.payable ?: 0, acc.currency)}" + (st?.dueDate?.let { "　繳款日 ${it.monthValue}/${it.dayOfMonth}" } ?: ""), fontSize = AppText.body)
                        Text("未出帳 ${Money.withSymbol(st?.outstanding ?: 0, acc.currency)}" + if (acc.creditLimit > 0) "　可用額度 ${Money.full(acc.creditLimit + current)}" else "",
                            fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            listOfNotNull(
                                acc.statementDay?.let { "結帳日 $it 日" },
                                settings.cardDueOffset[acc.id]?.let { "結帳後 $it 天繳款" } ?: acc.dueDay?.let { "繳款日 $it 日" },
                                st?.autopayDate?.let { "${it.monthValue}/${it.dayOfMonth} 由「${lk.account(acc.autopayFromId)?.name}」自動扣繳" } ?: "未設定自動扣繳",
                            ).joinToString(" · "),
                            fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.OutlinedButton(onClick = {
                        nav.navigate("txn/0?type=TRANSFER&account=${acc.autopayFromId ?: 0}&to=${acc.id}&amount=${st?.payable ?: 0}")
                    }) { Text("繳卡費") }
                }
                com.enwei.moneymanager.ui.components.LineDivider()
            }
            if (acc?.type == AccountType.LOAN) item { LoanCard(acc, current, lk, nav) }
            if (chart) {
                item {
                    val months = (5 downTo 0).map { month.minusMonths(it.toLong()) }
                    val ranges = months.map { period(it) }
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
                        com.enwei.moneymanager.ui.components.LineDivider(Modifier.padding(vertical = 12.dp))
                        DualBarChart(deps, wds, labels, IncomeColor, ExpenseColor)
                        if (isBroker) Text(
                            "折線：現金＋持股市值　長條：存入／提出",
                            fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                    val months = (12 downTo 1).map { YearMonth.of(month.year, it) }.filter { !it.isAfter(thisPeriod) }
                    items(months, key = { "m$it" }) { m ->
                        val r = period(m)
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = AppText.section, fontWeight = FontWeight.SemiBold)
                if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Row {
                    Text(Money.full(deposit), color = IncomeColor, modifier = Modifier.width(96.dp), textAlign = TextAlign.End,
                        fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Money.full(withdrawal), color = ExpenseColor, modifier = Modifier.width(96.dp), textAlign = TextAlign.End,
                        fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("餘額 ${Money.full(balance)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        com.enwei.moneymanager.ui.components.LineDivider()
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
                com.enwei.moneymanager.ui.components.LineDivider()
                Column(Modifier.padding(20.dp)) {
                    Text("餘額（不含股票）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(Money.full(worth.last()), fontSize = AppText.display, fontWeight = FontWeight.SemiBold)
                }
                com.enwei.moneymanager.ui.components.LineDivider()
                LineChart(worth, labels, MaterialTheme.colorScheme.primary, Modifier.padding(vertical = 12.dp))
                com.enwei.moneymanager.ui.components.LineDivider()
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
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp),
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
                            com.enwei.moneymanager.ui.components.LineDivider()
                        }
                    }
                }
            }
        }
    }
}

fun formatShares(shares: Double): String =
    if (shares >= 1000 && shares % 1000 == 0.0) "${(shares / 1000).toLong()} 張" else "${com.enwei.moneymanager.ui.components.formatNum(shares, 0)} 股"
