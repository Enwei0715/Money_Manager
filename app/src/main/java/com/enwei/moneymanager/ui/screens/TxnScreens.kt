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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import com.enwei.moneymanager.ui.components.FormInput
import com.enwei.moneymanager.ui.components.FormLine
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
fun SearchScreen(nav: NavController, startWithFilter: Boolean = false) {
    val lk = rememberLookups()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var q by rememberSaveable { mutableStateOf("") }
    var showFilter by rememberSaveable { mutableStateOf(startWithFilter) }
    var from by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
    var to by rememberSaveable { mutableStateOf<java.time.LocalDate?>(null) }
    var accountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var min by rememberSaveable { mutableStateOf("") }
    var max by rememberSaveable { mutableStateOf("") }
    var pickRange by remember { mutableStateOf(false) }
    var accMenu by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    val minMinor = Money.parseMinor(min)
    val maxMinor = Money.parseMinor(max)
    val active = q.isNotBlank() || from != null || accountId != null || categoryId != null || minMinor != null || maxMinor != null
    val result = remember(q, all, lk, from, to, accountId, categoryId, minMinor, maxMinor) {
        if (!active) emptyList() else all.filter { t ->
            val d = t.date.toLocalDate()
            (q.isBlank() || listOfNotNull(t.merchant, t.note, lk.category(t.categoryId)?.name, lk.account(t.accountId)?.name, lk.account(t.toAccountId)?.name)
                .any { it.contains(q, true) } || Money.full(t.amount).replace(",", "").contains(q.replace(",", ""))) &&
                (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to)) &&
                (accountId == null || t.accountId == accountId || t.toAccountId == accountId) &&
                (categoryId == null || t.categoryId == categoryId) &&
                (minMinor == null || t.amount >= minMinor) && (maxMinor == null || t.amount <= maxMinor)
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopBar("搜尋", { nav.popBackStack() }) {
                IconButton(onClick = { showFilter = !showFilter }) { Icon(Icons.Outlined.Tune, "篩選") }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                androidx.compose.foundation.text.BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    decorationBox = { inner ->
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)).padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Search, null)
                            Spacer(Modifier.width(10.dp))
                            Box { if (q.isEmpty()) Text("商家、備註、分類、帳戶或金額", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (showFilter) {
                    FormLine("期間", onClick = { pickRange = true }) {
                        Text(if (from == null) "" else "$from ~ ${to ?: from}", fontSize = 16.sp)
                    }
                    FormLine("帳戶", onClick = { accMenu = true }) {
                        Box {
                            Text(lk.account(accountId)?.name.orEmpty(), fontSize = 16.sp)
                            DropdownMenu(accMenu, { accMenu = false }) {
                                DropdownMenuItem(text = { Text("全部") }, onClick = { accountId = null; accMenu = false })
                                lk.accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { accountId = a.id; accMenu = false }) }
                            }
                        }
                    }
                    FormLine("分類", onClick = { catMenu = true }) {
                        Box {
                            Text(lk.category(categoryId)?.name.orEmpty(), fontSize = 16.sp)
                            DropdownMenu(catMenu, { catMenu = false }) {
                                DropdownMenuItem(text = { Text("全部") }, onClick = { categoryId = null; catMenu = false })
                                lk.categories.forEach { c -> DropdownMenuItem(text = { Text("${c.kind.label}・${c.name}") }, onClick = { categoryId = c.id; catMenu = false }) }
                            }
                        }
                    }
                    FormLine("金額") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { FormInput(min, { min = it }, "最低", number = true) }
                            Text("  ~  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box(Modifier.weight(1f)) { FormInput(max, { max = it }, "最高", number = true) }
                        }
                    }
                    if (active) TextButton(onClick = { from = null; to = null; accountId = null; categoryId = null; min = ""; max = "" },
                        modifier = Modifier.padding(start = 8.dp)) { Text("清除篩選") }
                }
            }
            if (active) SummaryBar(totalsOf(result, lk.fx), lk.base)
            LazyColumn(Modifier.fillMaxSize()) {
                if (active && result.isEmpty()) item { NoData("找不到符合的交易") }
                result.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()).forEach { (day, txns) ->
                    item(key = "s$day") { DayBlock(day, txns, lk, nav) }
                }
            }
        }
    }
    if (pickRange) {
        val state = androidx.compose.material3.rememberDateRangePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(onClick = {
                    fun conv(ms: Long?) = ms?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                    from = conv(state.selectedStartDateMillis); to = conv(state.selectedEndDateMillis) ?: from
                    pickRange = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text("取消") } },
        ) { androidx.compose.material3.DateRangePicker(state, Modifier.height(480.dp)) }
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
                    "從通知、週期規則自動建立的交易會先放在這裡。點選可修改分類或帳戶後確認；確認後會記住商家對應的分類。",
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
