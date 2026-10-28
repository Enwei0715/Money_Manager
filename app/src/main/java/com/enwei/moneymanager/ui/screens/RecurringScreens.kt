package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import com.enwei.moneymanager.ui.components.Text
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
import com.enwei.moneymanager.core.Frequency
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.RecurringEntity
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.components.AmountText
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.DateField
import com.enwei.moneymanager.ui.components.DropdownField
import com.enwei.moneymanager.ui.components.SymbolBadge
import com.enwei.moneymanager.ui.components.Symbols
import com.enwei.moneymanager.ui.components.EmptyHint
import com.enwei.moneymanager.ui.components.NumberField
import kotlinx.coroutines.launch
import java.time.LocalDate

private val Frequency.label get() = when (this) {
    Frequency.DAILY -> "每天"; Frequency.WEEKLY -> "每週"; Frequency.MONTHLY -> "每月"; Frequency.YEARLY -> "每年"
}

@Composable
fun RecurringScreen(nav: NavController) {
    val lk = rememberLookups()
    val list by repo.recurring.collectAsStateWithLifecycle(emptyList())
    Scaffold(
        topBar = { BackTopBar("週期交易／自動扣款", { nav.popBackStack() }) },
        floatingActionButton = { FloatingActionButton(onClick = { nav.navigate("recurring/0") }) { Icon(Icons.Filled.Add, "新增") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            item {
                Text(
                    "訂閱、房租、保費、薪水、定期定額等固定收支。到期時自動產生交易（離線也會補上）。\n信用卡「帳單」自動扣繳請到 帳戶 → 信用卡 設定結帳日、繳款日與扣款帳戶。",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp),
                )
            }
            if (list.isEmpty()) item { EmptyHint("尚未設定週期交易") }
            items(list, key = { it.id }) { r ->
                val cat = lk.category(r.categoryId)
                val every = if (r.interval > 1) "每 ${r.interval} ${r.frequency.label.drop(1)}" else r.frequency.label
                val dayText = when (r.frequency) {
                    Frequency.MONTHLY -> " ${r.dayOfMonth ?: LocalDate.ofEpochDay(r.anchorDate).dayOfMonth} 日"
                    else -> ""
                }
                ListItem(
                    leadingContent = { SymbolBadge(if (r.type == TxnType.TRANSFER) Symbols.transfer else Symbols.category(cat?.name, r.type)) },
                    headlineContent = { Text(r.name + if (!r.enabled) "（停用）" else "") },
                    supportingContent = {
                        Text("$every$dayText · ${lk.account(r.accountId)?.name ?: "未指定帳戶"} · 下次 ${repo.nextOccurrence(r)}")
                    },
                    trailingContent = { AmountText(r.amount, r.currency, r.type) },
                    modifier = Modifier.clickable { nav.navigate("recurring/${r.id}") },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecurringEditScreen(nav: NavController, id: Long) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<RecurringEntity?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TxnType.EXPENSE) }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(lk.base) }
    var accountId by remember { mutableStateOf<Long?>(null) }
    var toAccountId by remember { mutableStateOf<Long?>(null) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var freq by remember { mutableStateOf(Frequency.MONTHLY) }
    var interval by remember { mutableStateOf("1") }
    var dayOfMonth by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    var start by remember { mutableStateOf(LocalDate.now()) }
    var hasEnd by remember { mutableStateOf(false) }
    var end by remember { mutableStateOf(LocalDate.now().plusYears(1)) }
    var autoConfirm by remember { mutableStateOf(true) }
    var enabled by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id == 0L) return@LaunchedEffect
        val r = repo.getRecurring(id) ?: return@LaunchedEffect
        original = r
        name = r.name; type = r.type; amount = Money.fromMinor(r.amount).stripTrailingZeros().toPlainString(); currency = r.currency
        accountId = r.accountId; toAccountId = r.toAccountId; categoryId = r.categoryId; freq = r.frequency
        interval = r.interval.toString(); dayOfMonth = (r.dayOfMonth ?: LocalDate.ofEpochDay(r.anchorDate).dayOfMonth).toString()
        start = LocalDate.ofEpochDay(r.anchorDate); hasEnd = r.endDate != null; r.endDate?.let { end = LocalDate.ofEpochDay(it) }
        autoConfirm = r.autoConfirm; enabled = r.enabled; note = r.note.orEmpty()
    }
    val active = lk.accounts.filter { !it.archived }

    Scaffold(topBar = {
        BackTopBar(if (id == 0L) "新增週期交易" else "編輯週期交易", { nav.popBackStack() }) {
            if (original != null) IconButton(onClick = { scope.launch { repo.deleteRecurring(id); nav.popBackStack() } }) {
                Icon(Icons.Filled.Delete, "刪除")
            }
        }
    }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("名稱") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxnType.entries.forEachIndexed { i, t ->
                    SegmentedButton(selected = type == t, onClick = { type = t }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(t.label) }
                }
            }
            Row {
                NumberField(amount, { amount = it }, "金額", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DropdownField("幣別", currencyOptions(lk), currency, { it }, { currency = it }, Modifier.width(120.dp))
            }
            DropdownField(if (type == TxnType.TRANSFER) "轉出帳戶" else "扣款／入帳帳戶", active, lk.account(accountId), { it.name }, { accountId = it.id })
            if (type == TxnType.TRANSFER) {
                DropdownField("轉入帳戶", active.filter { it.id != accountId }, lk.account(toAccountId), { it.name }, { toAccountId = it.id })
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lk.categories.filter { it.kind == type && !it.archived }.forEach { c ->
                        FilterChip(categoryId == c.id, { categoryId = c.id }, label = { Text(c.name) })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DropdownField("頻率", Frequency.entries, freq, { it.label }, { freq = it }, Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                NumberField(interval, { interval = it }, "間隔", Modifier.width(96.dp), decimal = false)
            }
            if (freq == Frequency.MONTHLY) NumberField(dayOfMonth, { dayOfMonth = it }, "每月幾號（超過月底自動取月底）", decimal = false)
            DateField(start, { start = it }, "開始日期")
            Row(verticalAlignment = Alignment.CenterVertically) { Text("設定結束日期", Modifier.weight(1f)); Switch(hasEnd, { hasEnd = it }) }
            if (hasEnd) DateField(end, { end = it }, "結束日期")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("自動入帳")
                    Text("關閉時會先放到「待確認」，適合金額會變動的帳單", style = MaterialTheme.typography.bodySmall)
                }
                Switch(autoConfirm, { autoConfirm = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) { Text("啟用", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
            OutlinedTextField(note, { note = it }, label = { Text("備註") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val minor = Money.parseMinor(amount) ?: return@Button
                    val changedSchedule = original?.let {
                        it.anchorDate != start.toEpochDay() || it.frequency != freq || it.dayOfMonth != dayOfMonth.toIntOrNull()
                    } ?: true
                    val r = (original ?: RecurringEntity(name = name, type = type, amount = minor, accountId = accountId, anchorDate = start.toEpochDay())).copy(
                        name = name.trim(), type = type, amount = minor, currency = currency, accountId = accountId,
                        toAccountId = if (type == TxnType.TRANSFER) toAccountId else null,
                        categoryId = if (type == TxnType.TRANSFER) null else categoryId,
                        frequency = freq, interval = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                        dayOfMonth = if (freq == Frequency.MONTHLY) dayOfMonth.toIntOrNull()?.coerceIn(1, 31) else null,
                        anchorDate = start.toEpochDay(), endDate = if (hasEnd) end.toEpochDay() else null,
                        autoConfirm = autoConfirm, enabled = enabled, note = note.ifBlank { null },
                        // 修改排程時，從今天之前重新開始計算，避免重複補帳
                        lastRunDate = if (changedSchedule && original != null) maxOf(start.minusDays(1), LocalDate.now().minusDays(1)).toEpochDay()
                        else original?.lastRunDate,
                    )
                    scope.launch {
                        repo.saveRecurring(r)
                        repo.runRecurring()
                        nav.popBackStack()
                    }
                },
                enabled = name.isNotBlank() && Money.parseMinor(amount) != null && accountId != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
            Text(
                "提示：新規則若開始日期在過去，會自動補上從開始日到今天的所有交易。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
