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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.EmptyHint
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 往來：借貸與代墊都依「人」整理。每個人一列（對方欠我／我欠對方與金額），
 * 可記代墊、記他幫我付、收回或還款；收付款是轉帳，不會多算收入或支出。
 */
@Composable
fun DebtsScreen(nav: NavController) {
    // 第一次打開這頁：介紹往來
    com.enwei.moneymanager.ui.components.PageTour("debts", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step(null, "往來對象", "每位對象列出應收（對方欠你）與應付（你欠對方）；點選對象可查看每一筆往來明細。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "如何記錄", "代墊或分帳：於記帳表單最下方的「往來」選擇對象；收回欠款：在收入選擇對象並選「他還我」。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "結清", "雙方金額相抵為零即視為結清；點選對象的選單可記錄代墊、他幫我付、收回款項，或設定借貸細節。"),
    ))
    val lk = rememberLookups()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val st by repo.settings.flow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var adding by remember { mutableStateOf(false) }
    var showSettled by remember { mutableStateOf(false) }
    val people = balances.filter { it.account.type == AccountType.LOAN && !it.account.archived }
    val open = people.filter { it.balance != 0L }.sortedByDescending { kotlin.math.abs(lk.fx.convert(it.balance, it.account.currency)) }
    val settled = people.filter { it.balance == 0L }.sortedBy { it.account.name }
    val owedToMe = open.filter { it.balance > 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
    val iOwe = -open.filter { it.balance < 0 }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
    // 每個人最近一次往來的日期
    val lastMove = remember(all) {
        all.flatMap { t -> listOfNotNull(t.accountId, t.toAccountId).map { it to t.date } }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
    }

    Scaffold(topBar = {
        BackTopBar("往來", { nav.popBackStack() }) {
            IconButton(onClick = { adding = true }) { Icon(Icons.Filled.PersonAdd, "新增對象") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            item {
                SummaryBarCustom(listOf(
                    "別人欠我" to (Money.full(owedToMe) to IncomeColor),
                    "我欠別人" to (Money.full(iOwe) to ExpenseColor),
                    "淨額" to (Money.full(owedToMe - iOwe) to MaterialTheme.colorScheme.onSurface),
                ))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            if (people.isEmpty()) item {
                EmptyHint("目前沒有往來對象。可於記帳時使用「往來」，或點選右上角新增")
            }
            items(open, key = { "p${it.account.id}" }) { b -> PersonRow(nav, b.account, b.balance, st.loans[b.account.id], lastMove[b.account.id]) }
            if (settled.isNotEmpty()) item {
                TextButton(onClick = { showSettled = !showSettled }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text((if (showSettled) "收起" else "已結清") + "（${settled.size}）")
                }
            }
            if (showSettled) items(settled, key = { "s${it.account.id}" }) { b -> PersonRow(nav, b.account, 0, st.loans[b.account.id], lastMove[b.account.id]) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("新增往來對象") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("名字") }, modifier = Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    scope.launch { val id = repo.debtPerson(name); adding = false; nav.navigate("acct/$id") }
                }) { Text("新增") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PersonRow(nav: NavController, a: com.enwei.moneymanager.data.AccountEntity, balance: Long, info: com.enwei.moneymanager.data.LoanInfo?, last: Long?) {
    var menu by remember { mutableStateOf(false) }
    val due = info?.dueDay?.let(LocalDate::ofEpochDay)
    val sub = listOfNotNull(
        when { balance > 0 -> "欠我"; balance < 0 -> "我欠"; else -> "已結清" },
        info?.installment?.takeIf { it > 0 && balance != 0L }?.let { "每月 ${Money.withSymbol(it, a.currency)}" },
        due?.takeIf { balance != 0L }?.let { if (it.isBefore(LocalDate.now())) "已逾期" else "${it.monthValue}/${it.dayOfMonth} 到期" },
        last?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }?.let { "最近 ${it.monthValue}/${it.dayOfMonth}" },
    ).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable { nav.navigate("acct/${a.id}") }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(info?.counterparty?.ifBlank { null } ?: a.name, fontSize = AppText.body, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(Money.withSymbol(kotlin.math.abs(balance), a.currency), fontSize = AppText.body,
            color = if (balance > 0) IncomeColor else if (balance < 0) ExpenseColor else MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "更多", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(menu, { menu = false }) {
                DropdownMenuItem(text = { Text("記錄代墊") }, onClick = { menu = false; nav.navigate("txn/0?type=EXPENSE&split=${a.id}") })
                DropdownMenuItem(text = { Text("他幫我付") }, onClick = { menu = false; nav.navigate("txn/0?type=EXPENSE&account=${a.id}") })
                // 收回＝從他的往來帳戶轉進來；還款＝轉給他（金額預設為目前差額）
                DropdownMenuItem(text = { Text(if (balance >= 0) "收回款項" else "還款") }, onClick = {
                    menu = false
                    val amt = kotlin.math.abs(balance)
                    nav.navigate(if (balance >= 0) "txn/0?type=TRANSFER&account=${a.id}&amount=$amt" else "txn/0?type=TRANSFER&to=${a.id}&amount=$amt")
                })
                DropdownMenuItem(text = { Text("借貸細節（分期、利息、到期）") }, onClick = { menu = false; nav.navigate("account/${a.id}") })
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
