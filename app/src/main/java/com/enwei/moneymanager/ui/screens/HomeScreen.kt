package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.netWorth
import com.enwei.moneymanager.data.toMillis
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.AmountText
import com.enwei.moneymanager.ui.components.LabelValue
import com.enwei.moneymanager.ui.components.SectionTitle
import com.enwei.moneymanager.work.SyncWorker
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

@Composable
fun HomeScreen(nav: NavController) {
    val lk = rememberLookups()
    val ctx = LocalContext.current
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val pending by repo.pending.collectAsStateWithLifecycle(emptyList())
    val month = YearMonth.now()
    val monthTxns by remember(month) {
        repo.transactionsInRange(month.atDay(1).toMillis(LocalTime.MIDNIGHT), month.plusMonths(1).atDay(1).toMillis(LocalTime.MIDNIGHT))
    }.collectAsStateWithLifecycle(emptyList())
    val recent by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var bills by remember { mutableStateOf<List<Triple<AccountEntity, LocalDate, Long>>>(emptyList()) }
    LaunchedEffect(recent) { bills = repo.upcomingCardBills() }

    val (income, expense) = repo.monthTotals(monthTxns, lk.fx)
    val worth = netWorth(balances, holdings, lk.fx)
    val assets = balances.filter { it.balance > 0 && it.account.includeInNetWorth }.sumOf { lk.fx.convert(it.balance, it.account.currency) } +
        holdings.sumOf { lk.fx.convert(Money.toMinor(it.marketValue), it.currency) }
    val debts = balances.filter { it.balance < 0 && it.account.includeInNetWorth }.sumOf { lk.fx.convert(it.balance, it.account.currency) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("記帳本") }, actions = {
                IconButton(onClick = { SyncWorker.runNow(ctx) }) { Icon(Icons.Filled.Refresh, "同步匯率與股價") }
            })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.navigate("txn/0") }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("記一筆") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("淨資產（${lk.base}）", style = MaterialTheme.typography.labelLarge)
                        Text(Money.format(worth, lk.base), style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabelValue("資產", Money.format(assets, lk.base))
                            LabelValue("負債", Money.format(debts, lk.base), valueColor = ExpenseColor)
                        }
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${month.monthValue} 月收支", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabelValue("收入", Money.format(income, lk.base), valueColor = IncomeColor)
                            LabelValue("支出", Money.format(expense, lk.base), valueColor = ExpenseColor)
                            LabelValue("結餘", Money.format(income - expense, lk.base))
                        }
                    }
                }
            }
            if (pending.isNotEmpty()) item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp).clickable { nav.navigate("pending") },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.NotificationsActive, null)
                        Text("  有 ${pending.size} 筆自動記錄待確認", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
            if (bills.isNotEmpty()) {
                item { SectionTitle("信用卡帳單") }
                items(bills, key = { "bill" + it.first.id }) { (card, due, owed) ->
                    ListItem(
                        headlineContent = { Text(card.name) },
                        supportingContent = {
                            Text("繳款日 ${due.format(dateFmt)}" + (if (card.autopayFromId != null) " · 自動扣繳（${lk.account(card.autopayFromId)?.name ?: ""}）" else ""))
                        },
                        trailingContent = { AmountText(owed, card.currency) },
                        modifier = Modifier.clickable { nav.navigate("account/${card.id}") },
                    )
                }
            }
            item { SectionTitle("最近交易") }
            items(recent.take(15), key = { it.id }) { t -> TxnRow(t, lk) { nav.navigate("txn/${t.id}") } }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}
