package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.EmptyHint
import java.time.LocalDate
import java.time.YearMonth

private fun balanceColor(v: Long, default: Color) = when { v > 0 -> IncomeColor; v < 0 -> ExpenseColor; else -> default }

@Composable
fun AccountsTab(nav: NavController, showBack: Boolean = false) {
    val lk = rememberLookups()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var bills by remember { mutableStateOf<Map<Long, Pair<LocalDate, Long>>>(emptyMap()) }
    LaunchedEffect(all) { bills = repo.upcomingCardBills().associate { (card, due, owed) -> card.id to (due to owed) } }

    val active = balances.filter { !it.account.archived }
    val counted = active.filter { it.account.includeInNetWorth }
    val stockValue = holdings.sumOf { lk.fx.convert(Money.toMinor(it.marketValue), it.currency) }
    val assets = counted.filter { it.balance > 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) } + stockValue
    val debts = -counted.filter { it.balance < 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text("帳戶") },
            navigationIcon = {
                if (showBack) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
            },
            actions = { IconButton(onClick = { nav.navigate("account/0") }) { Icon(Icons.Filled.Add, "新增帳戶") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                SummaryBarCustom(
                    listOf("資產" to (Money.format(assets) to IncomeColor), "負債" to (Money.format(debts) to ExpenseColor),
                        "合計" to (Money.format(assets - debts) to MaterialTheme.colorScheme.onSurface)),
                )
                HorizontalDivider()
            }
            active.groupBy { it.account.type }.toSortedMap().forEach { (type, list) ->
                item(key = "g${type.name}") {
                    val total = list.sumOf { lk.fx.convert(it.balance, it.account.currency) }
                    GroupHeader(type.label, Money.format(total, lk.base), balanceColor(total, MaterialTheme.colorScheme.onSurfaceVariant))
                }
                list.forEach { b ->
                    item(key = "a${b.account.id}") {
                        AccountRow(b.account, b.balance, bills[b.account.id], lk) { nav.navigate("acct/${b.account.id}") }
                    }
                }
            }
            if (holdings.any { it.shares > 0 }) {
                item(key = "ginv") { GroupHeader("投資", Money.format(stockValue, lk.base), IncomeColor) }
                item(key = "inv") {
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { nav.navigate("invest") }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text("📈 股票持股（${holdings.count { it.shares > 0 }} 檔）", Modifier.weight(1f))
                        Text(Money.format(stockValue), color = IncomeColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val archived = balances.filter { it.account.archived }
            if (archived.isNotEmpty()) {
                item(key = "garch") { GroupHeader("已封存", "", MaterialTheme.colorScheme.onSurfaceVariant) }
                archived.forEach { b ->
                    item(key = "x${b.account.id}") { AccountRow(b.account, b.balance, null, lk) { nav.navigate("acct/${b.account.id}") } }
                }
            }
            if (balances.isEmpty()) item { EmptyHint("點右上角＋新增帳戶") }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun SummaryBarCustom(cells: List<Pair<String, Pair<String, Color>>>) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 10.dp)) {
        cells.forEach { (label, v) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(v.first, color = v.second, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, total: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(total, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

@Composable
private fun AccountRow(a: AccountEntity, balance: Long, bill: Pair<LocalDate, Long>?, lk: Lookups, onClick: () -> Unit) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(a.name + (a.cardLast4?.let { "  ••$it" } ?: ""))
                val sub = buildList {
                    if (a.type == AccountType.CREDIT_CARD && bill != null) {
                        add("應繳 ${Money.format(bill.second)} · ${bill.first.monthValue}/${bill.first.dayOfMonth} 繳款")
                        if (a.autopayFromId != null) add("自動扣繳")
                    }
                    if (a.creditLimit > 0) add("可用 ${Money.format(a.creditLimit + balance)}")
                    if (a.currency != lk.base) add(a.currency)
                    if (!a.includeInNetWorth) add("不計入")
                }
                if (sub.isNotEmpty()) Text(sub.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(balance, if (a.currency == lk.base) null else a.currency),
                    color = balanceColor(balance, MaterialTheme.colorScheme.onSurface), fontWeight = FontWeight.SemiBold)
                if (a.currency != lk.base && lk.fx.hasRate(a.currency)) Text("≈" + Money.format(lk.fx.convert(balance, a.currency)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 16.dp))
    }
}

/** 單一帳戶：每月存入／提出與明細。 */
@Composable
fun AccountDetailScreen(nav: NavController, id: Long) {
    val lk = rememberLookups()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    val b = balances.find { it.account.id == id }
    val mine = remember(all, id, month) {
        all.filter { (it.accountId == id || it.toAccountId == id) && YearMonth.from(it.date.toLocalDate()) == month }
    }
    var inflow = 0L
    var outflow = 0L
    mine.filter { it.status == TxnStatus.CONFIRMED }.forEach { t ->
        when {
            t.type == TxnType.INCOME -> inflow += t.amount
            t.type == TxnType.EXPENSE -> outflow += t.amount
            t.toAccountId == id -> inflow += t.toAmount ?: t.amount
            else -> outflow += t.amount
        }
    }

    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, "上個月") }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(b?.account?.name ?: "", style = MaterialTheme.typography.titleSmall)
                        Text("${month.year}/${month.monthValue}", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, "下個月") }
                }
            },
            actions = { IconButton(onClick = { nav.navigate("account/$id") }) { Icon(Icons.Filled.Edit, "編輯帳戶") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                val cur = b?.account?.currency
                val shown = if (cur == lk.base) null else cur
                SummaryBarCustom(
                    listOf(
                        "存入" to (Money.format(inflow, shown) to IncomeColor),
                        "提出" to (Money.format(outflow, shown) to ExpenseColor),
                        "目前餘額" to (Money.format(b?.balance ?: 0, shown) to balanceColor(b?.balance ?: 0, MaterialTheme.colorScheme.onSurface)),
                    ),
                )
                HorizontalDivider()
            }
            if (mine.isEmpty()) item { EmptyHint("這個月沒有交易") }
            mine.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()).forEach { (d, txns) ->
                item(key = "d$d") { DayBlock(d, txns, lk, nav) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
