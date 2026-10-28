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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnSource
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.data.toMillis
import com.enwei.moneymanager.ui.components.AmountText
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.DateField
import com.enwei.moneymanager.ui.components.DropdownField
import com.enwei.moneymanager.ui.components.EmojiBadge
import com.enwei.moneymanager.ui.components.EmptyHint
import com.enwei.moneymanager.ui.components.MonthSwitcher
import com.enwei.moneymanager.ui.components.NumberField
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

@Composable
fun TxnRow(t: TxnEntity, lk: Lookups, onClick: () -> Unit) {
    val cat = lk.category(t.categoryId)
    val acc = lk.account(t.accountId)
    val title = when (t.type) {
        TxnType.TRANSFER -> "${acc?.name ?: "?"} → ${lk.account(t.toAccountId)?.name ?: "?"}"
        else -> t.merchant?.takeIf { it.isNotBlank() } ?: cat?.name ?: t.type.label
    }
    val sub = buildList {
        if (t.type != TxnType.TRANSFER) cat?.name?.let(::add)
        if (t.type != TxnType.TRANSFER) acc?.name?.let(::add)
        if (t.source != TxnSource.MANUAL) add(t.source.label)
        if (t.status == TxnStatus.PENDING) add("待確認")
        t.note?.takeIf { it.isNotBlank() }?.let { add(it.lineSequence().first()) }
    }.joinToString(" · ")
    ListItem(
        leadingContent = { EmojiBadge(if (t.type == TxnType.TRANSFER) "🔁" else cat?.icon ?: "❔") },
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(sub, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                AmountText(t.amount, t.currency, t.type)
                Text(Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).format(dateFmt), style = MaterialTheme.typography.labelSmall)
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
fun TxnListScreen(nav: NavController) {
    val lk = rememberLookups()
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val txns by remember(month) {
        repo.transactionsInRange(month.atDay(1).toMillis(LocalTime.MIDNIGHT), month.plusMonths(1).atDay(1).toMillis(LocalTime.MIDNIGHT))
    }.collectAsStateWithLifecycle(emptyList())
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())

    // 搜尋時跨月份搜尋全部資料
    val shown = if (searching && query.isNotBlank()) all.filter { t ->
        listOfNotNull(t.merchant, t.note, lk.category(t.categoryId)?.name, lk.account(t.accountId)?.name).any { it.contains(query, true) } ||
            Money.format(t.amount).contains(query)
    } else txns
    val grouped = shown.groupBy { it.date.toLocalDate() }
    val (income, expense) = repo.monthTotals(shown, lk.fx)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) OutlinedTextField(query, { query = it }, placeholder = { Text("搜尋商家、備註、分類") }, singleLine = true)
                    else Text("明細")
                },
                actions = { IconButton(onClick = { searching = !searching; if (!searching) query = "" }) { Icon(Icons.Filled.Search, "搜尋") } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { nav.navigate("txn/0") }) { Icon(Icons.Filled.Add, "新增") } },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (!searching) item { MonthSwitcher(month, { month = it }) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("收入 ${Money.format(income, lk.base)}", color = com.enwei.moneymanager.ui.IncomeColor)
                    Text("支出 ${Money.format(expense, lk.base)}", color = com.enwei.moneymanager.ui.ExpenseColor)
                    Text("結餘 ${Money.format(income - expense, lk.base)}")
                }
            }
            if (shown.isEmpty()) item { EmptyHint("這段期間沒有交易") }
            grouped.forEach { (day, list) ->
                item(key = "h$day") {
                    val (di, de) = repo.monthTotals(list, lk.fx)
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("${day.monthValue}/${day.dayOfMonth}（${com.enwei.moneymanager.core.Recurrence.weekdayName(day.dayOfWeek)}）",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(buildString {
                            if (di > 0) append("+${Money.format(di)}  ")
                            if (de > 0) append("-${Money.format(de)}")
                        }, style = MaterialTheme.typography.labelLarge)
                    }
                    HorizontalDivider(Modifier.padding(top = 4.dp))
                }
                items(list, key = { it.id }) { t -> TxnRow(t, lk) { nav.navigate("txn/${t.id}") } }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxnEditScreen(nav: NavController, id: Long, initialType: String?) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var loaded by remember { mutableStateOf(id == 0L) }
    var original by remember { mutableStateOf<TxnEntity?>(null) }

    var type by rememberSaveable { mutableStateOf(initialType?.let { TxnType.valueOf(it) } ?: TxnType.EXPENSE) }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(settings.baseCurrency) }
    var accountId by rememberSaveable { mutableStateOf(settings.defaultAccountId) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toAmount by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            repo.getTxn(id)?.let { t ->
                original = t
                type = t.type; amount = Money.fromMinor(t.amount).stripTrailingZeros().toPlainString(); currency = t.currency
                accountId = t.accountId; toAccountId = t.toAccountId
                toAmount = t.toAmount?.let { Money.fromMinor(it).stripTrailingZeros().toPlainString() }.orEmpty()
                categoryId = t.categoryId; merchant = t.merchant.orEmpty(); note = t.note.orEmpty(); date = t.date.toLocalDate()
            }
            loaded = true
        }
    }
    val activeAccounts = lk.accounts.filter { !it.archived }
    LaunchedEffect(accountId, lk.accounts) {
        // 新增時，幣別預設跟隨帳戶
        if (original == null) lk.account(accountId)?.let { currency = it.currency }
    }
    val toAcc = lk.account(toAccountId)
    val crossCurrency = type == TxnType.TRANSFER && toAcc != null && toAcc.currency != currency

    fun save(confirm: Boolean) {
        val minor = Money.parseMinor(amount) ?: return
        val base = original ?: TxnEntity(type = type, amount = 0, date = 0)
        val keepTime = original?.date?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
        val t = base.copy(
            type = type, amount = minor, currency = currency, accountId = accountId,
            toAccountId = if (type == TxnType.TRANSFER) toAccountId else null,
            toAmount = if (crossCurrency) Money.parseMinor(toAmount) else null,
            categoryId = if (type == TxnType.TRANSFER) null else categoryId,
            merchant = merchant.ifBlank { null }, note = note.ifBlank { null },
            date = date.toMillis(keepTime ?: if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON),
            status = if (confirm) TxnStatus.CONFIRMED else base.status,
        )
        scope.launch { repo.saveTxn(t); nav.popBackStack() }
    }

    Scaffold(topBar = {
        BackTopBar(if (id == 0L) "記一筆" else "編輯交易", { nav.popBackStack() }) {
            if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
        }
    }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TxnType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t, onClick = { type = t; categoryId = null },
                        shape = SegmentedButtonDefaults.itemShape(i, TxnType.entries.size),
                    ) { Text(t.label) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(amount, { amount = it }, "金額", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                DropdownField("幣別", currencyOptions(lk), currency, { it }, { currency = it }, Modifier.width(120.dp))
            }
            if (currency != lk.base) Money.parseMinor(amount)?.let {
                Text("≈ ${Money.format(lk.fx.convert(it, currency), lk.base)}（依目前匯率）", style = MaterialTheme.typography.bodySmall)
            }
            DropdownField(
                if (type == TxnType.TRANSFER) "轉出帳戶" else "帳戶", activeAccounts, lk.account(accountId),
                { "${it.type.icon} ${it.name}" }, { accountId = it.id },
            )
            if (type == TxnType.TRANSFER) {
                DropdownField("轉入帳戶", activeAccounts.filter { it.id != accountId }, toAcc, { "${it.type.icon} ${it.name}" }, { toAccountId = it.id })
                if (crossCurrency) NumberField(toAmount, { toAmount = it }, "轉入金額（${toAcc!!.currency}）")
            } else {
                Text("分類", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lk.categories.filter { it.kind == type && !it.archived }.forEach { c ->
                        FilterChip(selected = categoryId == c.id, onClick = { categoryId = c.id }, label = { Text("${c.icon} ${c.name}") })
                    }
                }
            }
            DateField(date, { date = it })
            OutlinedTextField(merchant, { merchant = it }, label = { Text(if (type == TxnType.TRANSFER) "說明" else "商家／項目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("備註") }, modifier = Modifier.fillMaxWidth())
            original?.rawText?.let {
                Text("原始內容：\n$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (original?.status == TxnStatus.PENDING) {
                Button(onClick = { save(confirm = true) }, enabled = Money.parseMinor(amount) != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Check, null); Text(" 確認入帳")
                }
            } else {
                Button(onClick = { save(confirm = false) }, enabled = Money.parseMinor(amount) != null, modifier = Modifier.fillMaxWidth()) { Text("儲存") }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("刪除這筆交易？") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.deleteTxn(id); nav.popBackStack() } }) { Text("刪除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

fun currencyOptions(lk: Lookups): List<String> =
    (listOf(lk.base) + lk.accounts.map { it.currency } + com.enwei.moneymanager.net.FxService.common).distinct()

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
                    TxnRow(t, lk) { nav.navigate("txn/${t.id}") }
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
