package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.CardCycle
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.ui.components.AmountText
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.DropdownField
import com.enwei.moneymanager.ui.components.EmojiBadge
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.SectionTitle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun AccountsScreen(nav: NavController) {
    val lk = rememberLookups()
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        topBar = { BackTopBar("帳戶", { nav.popBackStack() }) },
        floatingActionButton = { FloatingActionButton(onClick = { nav.navigate("account/0") }) { Icon(Icons.Filled.Add, "新增帳戶") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            balances.groupBy { it.account.type }.toSortedMap().forEach { (type, list) ->
                item(key = type.name) {
                    val total = list.filter { !it.account.archived }.sumOf { lk.fx.convert(it.balance, it.account.currency) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(type.label)
                        Text(Money.format(total, lk.base), modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelLarge)
                    }
                }
                items(list, key = { it.account.id }) { b ->
                    val a = b.account
                    ListItem(
                        leadingContent = { EmojiBadge(a.type.icon) },
                        headlineContent = { Text(a.name + if (a.archived) "（已封存）" else "") },
                        supportingContent = {
                            val parts = buildList {
                                a.cardLast4?.let { add("末四碼 $it") }
                                if (a.statementDay != null && a.dueDay != null) add("結帳 ${a.statementDay} 日／繳款 ${a.dueDay} 日")
                                if (a.autopayFromId != null) add("自動扣繳")
                                if (a.creditLimit > 0) add("可用 ${Money.format(a.creditLimit + b.balance, a.currency)}")
                            }
                            if (parts.isNotEmpty()) Text(parts.joinToString(" · "))
                        },
                        trailingContent = { AmountText(b.balance, a.currency, colorBySign = true) },
                        modifier = Modifier.clickable { nav.navigate("account/${a.id}") },
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
fun AccountEditScreen(nav: NavController, id: Long) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<AccountEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var currency by remember { mutableStateOf(lk.base) }
    var initial by remember { mutableStateOf("0") }
    var limit by remember { mutableStateOf("") }
    var last4 by remember { mutableStateOf("") }
    var statementDay by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("") }
    var autopayFrom by remember { mutableStateOf<Long?>(null) }
    var includeNet by remember { mutableStateOf(true) }
    var archived by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == 0L) return@LaunchedEffect
        val a = repo.accounts.first().find { it.id == id } ?: return@LaunchedEffect
        original = a
        name = a.name; type = a.type; currency = a.currency
        initial = Money.fromMinor(a.initialBalance).stripTrailingZeros().toPlainString()
        limit = if (a.creditLimit > 0) Money.fromMinor(a.creditLimit).stripTrailingZeros().toPlainString() else ""
        last4 = a.cardLast4.orEmpty(); statementDay = a.statementDay?.toString().orEmpty(); dueDay = a.dueDay?.toString().orEmpty()
        autopayFrom = a.autopayFromId; includeNet = a.includeInNetWorth; archived = a.archived
    }
    val isCard = type == AccountType.CREDIT_CARD
    val sDay = statementDay.toIntOrNull()?.takeIf { it in 1..31 }
    val dDay = dueDay.toIntOrNull()?.takeIf { it in 1..31 }

    Scaffold(topBar = {
        BackTopBar(if (id == 0L) "新增帳戶" else "編輯帳戶", { nav.popBackStack() }) {
            if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
        }
    }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            DropdownField("類型", AccountType.entries, type, { "${it.icon} ${it.label}" }, { type = it })
            DropdownField("幣別", currencyOptions(lk), currency, { it }, { currency = it })
            NumberField(initial, { initial = it }, if (isCard) "目前欠款（輸入正數）" else "期初餘額", decimal = true)
            if (isCard) {
                NumberField(limit, { limit = it }, "信用額度")
                OutlinedTextField(
                    last4, { s -> last4 = s.filter(Char::isDigit).take(4) }, label = { Text("卡號末四碼（用於辨識通知）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    NumberField(statementDay, { statementDay = it }, "結帳日", Modifier.weight(1f), decimal = false)
                    Spacer(Modifier.width(8.dp))
                    NumberField(dueDay, { dueDay = it }, "繳款日", Modifier.weight(1f), decimal = false)
                }
                DropdownField(
                    "自動扣繳帳戶", lk.accounts.filter { it.type != AccountType.CREDIT_CARD && !it.archived }, lk.account(autopayFrom),
                    { "${it.type.icon} ${it.name}" }, { autopayFrom = it.id }, noneLabel = "不自動扣繳", onNone = { autopayFrom = null },
                )
                if (sDay != null && dDay != null) {
                    val cycle = CardCycle(sDay, dDay)
                    val due = cycle.nextDueDate(LocalDate.now().minusDays(1))
                    Text(
                        "下次繳款日 $due（帳單結帳日 ${cycle.statementDateFor(due)}）" +
                            if (autopayFrom != null) "\n到期時會自動從扣款帳戶轉帳「結帳日欠款 − 已繳金額」。" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                OutlinedTextField(
                    last4, { s -> last4 = s.filter(Char::isDigit).take(4) }, label = { Text("帳號／卡號末四碼（選填）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("計入淨資產", Modifier.weight(1f)); Switch(includeNet, { includeNet = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("封存（不再顯示於選單）", Modifier.weight(1f)); Switch(archived, { archived = it })
            }
            Button(
                onClick = {
                    val init = Money.parseMinor(initial) ?: 0
                    val a = (original ?: AccountEntity(name = name, type = type)).copy(
                        name = name.trim(), type = type, currency = currency,
                        initialBalance = if (isCard) -kotlin.math.abs(init) else init,
                        creditLimit = Money.parseMinor(limit) ?: 0,
                        cardLast4 = last4.ifBlank { null },
                        statementDay = if (isCard) sDay else null, dueDay = if (isCard) dDay else null,
                        autopayFromId = if (isCard) autopayFrom else null,
                        includeInNetWorth = includeNet, archived = archived,
                    )
                    scope.launch { repo.saveAccount(a); nav.popBackStack() }
                },
                enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("刪除帳戶？") },
        text = { Text("相關交易會保留但不再屬於任何帳戶。若只是不再使用，建議改用「封存」。") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.deleteAccount(original!!); nav.popBackStack() } }) { Text("刪除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}
