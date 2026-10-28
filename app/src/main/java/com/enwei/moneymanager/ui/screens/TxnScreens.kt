package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.EmptyHint
import kotlinx.coroutines.launch

fun currencyOptions(lk: Lookups): List<String> =
    (listOf(lk.base) + lk.accounts.map { it.currency } + com.enwei.moneymanager.net.FxService.common).distinct()

@Composable
fun SearchScreen(nav: NavController) {
    val lk = rememberLookups()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var q by rememberSaveable { mutableStateOf("") }
    val result = remember(q, all, lk) {
        if (q.isBlank()) emptyList() else all.filter { t ->
            listOfNotNull(t.merchant, t.note, lk.category(t.categoryId)?.name, lk.account(t.accountId)?.name, lk.account(t.toAccountId)?.name)
                .any { it.contains(q, true) } || Money.format(t.amount).replace(",", "").contains(q.replace(",", ""))
        }
    }
    Scaffold(topBar = { BackTopBar("搜尋", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(q, { q = it }, placeholder = { Text("商家、備註、分類、帳戶或金額") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            if (q.isNotBlank()) SummaryBar(totalsOf(result, lk.fx), lk.base)
            LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                if (q.isNotBlank() && result.isEmpty()) item { EmptyHint("找不到符合的交易") }
                result.groupBy { it.date.toLocalDate() }.forEach { (day, txns) ->
                    item(key = "s$day") { DayBlock(day, txns, lk, nav) }
                }
            }
        }
    }
}

@Composable
fun PendingScreen(nav: NavController) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val pending by repo.pending.collectAsStateWithLifecycle(emptyList())
    Scaffold(topBar = {
        BackTopBar("待確認（${pending.size}）", { nav.popBackStack() }) {
            if (pending.isNotEmpty()) IconButton(onClick = { scope.launch { repo.confirmAllPending() } }) { Icon(Icons.Filled.DoneAll, "全部確認") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text(
                    "從通知、電子發票、週期規則自動建立的交易會先放在這裡。點選可修改分類或帳戶後確認；確認後會記住商家對應的分類。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
            if (pending.isEmpty()) item { EmptyHint("沒有待確認的交易") }
            items(pending, key = { it.id }) { t ->
                Column {
                    MmTxnRow(t, lk) { nav.navigate("txn/${t.id}") }
                    Text(
                        "${t.date.toLocalDate()} · ${t.source.label}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 100.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = { scope.launch { repo.deleteTxn(t.id) } }) { Text("刪除") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { scope.launch { repo.saveTxn(t.copy(status = TxnStatus.CONFIRMED)) } }) { Text("確認") }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
