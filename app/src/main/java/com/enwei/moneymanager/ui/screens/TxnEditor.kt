package com.enwei.moneymanager.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.core.Calculator
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Recurrence
import com.enwei.moneymanager.data.AccountEntity
import com.enwei.moneymanager.data.TxnEntity
import com.enwei.moneymanager.data.TxnSource
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.components.BackTopBar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private enum class Panel { NONE, AMOUNT, FEE, CATEGORY, ACCOUNT, TO_ACCOUNT }

private val PanelHeader = Color(0xFF111111)
private val CurrencyBar = Color(0xFF3F4570)
private val CurrencyBarSelected = Color(0xFF262B4D)

/** 收入藍、支出紅；轉帳用主題文字色（深色模式為白色，淺色模式為黑色）。 */
@Composable
fun typeColor(t: TxnType): Color = when (t) {
    TxnType.INCOME -> IncomeColor
    TxnType.EXPENSE -> ExpenseColor
    TxnType.TRANSFER -> MaterialTheme.colorScheme.onSurface
}

private fun minorToExpr(minor: Long) = Money.fromMinor(minor).stripTrailingZeros().toPlainString()

/** 記一筆：上方表單、下方依欄位切換計算機鍵盤／分類格／帳戶格。 */
@Composable
fun TxnEditScreen(nav: NavController, id: Long, initialType: String?, initialEpochDay: Long? = null, initialAccountId: Long? = null,
    initialToAccountId: Long? = null, initialAmount: Long? = null,
) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val history by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var original by remember { mutableStateOf<TxnEntity?>(null) }

    var type by rememberSaveable { mutableStateOf(initialType?.let { TxnType.valueOf(it) } ?: TxnType.EXPENSE) }
    var expr by rememberSaveable { mutableStateOf(initialAmount?.let(::minorToExpr).orEmpty()) }
    var feeExpr by rememberSaveable { mutableStateOf("") }
    var showFee by rememberSaveable { mutableStateOf(false) }
    var currency by rememberSaveable { mutableStateOf(settings.baseCurrency) }
    // 新增時帶入上次用的帳戶（轉帳也記住轉入帳戶），沒有才用預設帳戶
    var accountId by rememberSaveable {
        mutableStateOf(initialAccountId ?: settings.lastPicks["txn.account"]?.toLongOrNull()?.takeIf { lk.accounts.isEmpty() || lk.account(it)?.archived == false } ?: settings.defaultAccountId)
    }
    var toAccountId by rememberSaveable {
        mutableStateOf(initialToAccountId ?: settings.lastPicks["txn.to"]?.toLongOrNull()?.takeIf { (lk.accounts.isEmpty() || lk.account(it)?.archived == false) && it != accountId })
    }
    var toExpr by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(initialEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var panel by rememberSaveable {
        mutableStateOf(if (id != 0L || initialAmount != null) Panel.NONE else if (repo.settings.value.inputFromCategory) Panel.CATEGORY else Panel.AMOUNT)
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var merchantFocused by remember { mutableStateOf(false) }
    var calcMode by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (original != null) return@LaunchedEffect
        if (id != 0L) repo.getTxn(id)?.let { t ->
            original = t
            type = t.type; expr = minorToExpr(t.amount); currency = t.currency
            accountId = t.accountId; toAccountId = t.toAccountId; toExpr = t.toAmount?.let(::minorToExpr).orEmpty()
            categoryId = t.categoryId; merchant = t.merchant.orEmpty(); note = t.note.orEmpty()
            date = t.date.toLocalDate(); time = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalTime().withSecond(0).withNano(0)
        }
    }
    LaunchedEffect(accountId, lk.accounts) {
        if (original == null) lk.account(accountId)?.let { currency = it.currency }
    }
    BackHandler(enabled = panel != Panel.NONE) { panel = Panel.NONE }

    val color = typeColor(type)
    val amountMinor = Calculator.evaluate(expr)?.abs()?.let(Money::toMinor)?.takeIf { it > 0 }
    val feeMinor = if (showFee) Calculator.evaluate(feeExpr)?.abs()?.let(Money::toMinor)?.takeIf { it > 0 } else null
    val toAcc = lk.account(toAccountId)
    val crossCurrency = type == TxnType.TRANSFER && toAcc != null && toAcc.currency != currency
    val canSave = amountMinor != null && accountId != null && (type != TxnType.TRANSFER || toAccountId != null)
    // 自動分類：依內容（商家）、過去紀錄、金額與帳戶建議分類；新交易還沒選分類時直接帶入
    var memoNew by remember { mutableStateOf(false) }
    var catSuggest by remember { mutableStateOf<com.enwei.moneymanager.core.CategorySuggester.Suggestion?>(null) }
    var catAuto by remember { mutableStateOf(false) }
    LaunchedEffect(merchant, type, amountMinor, accountId) {
        if (type == TxnType.TRANSFER) { catSuggest = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        val sg = repo.suggestCategory(merchant.ifBlank { null }, type, System.currentTimeMillis(), amountMinor ?: 0, accountId)
        catSuggest = sg
        if (sg != null && id == 0L && (categoryId == null || catAuto)) { categoryId = sg.categoryId; catAuto = true }
    }

    fun build(): TxnEntity? {
        val minor = amountMinor ?: return null
        val base = original ?: TxnEntity(type = type, amount = 0, date = 0)
        return base.copy(
            type = type, amount = minor, currency = currency, accountId = accountId,
            toAccountId = if (type == TxnType.TRANSFER) toAccountId else null,
            toAmount = if (crossCurrency) Calculator.evaluate(toExpr)?.abs()?.let(Money::toMinor) else null,
            categoryId = if (type == TxnType.TRANSFER) null else categoryId,
            merchant = merchant.trim().ifBlank { null }, note = note.trim().ifBlank { null },
            date = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            status = TxnStatus.CONFIRMED,
        )
    }

    fun save(again: Boolean) {
        val t = build() ?: return
        if (original == null) repo.settings.update { st ->
            st.copy(lastPicks = st.lastPicks + listOfNotNull(
                t.accountId?.let { "txn.account" to it.toString() },
                t.toAccountId?.takeIf { t.type == TxnType.TRANSFER }?.let { "txn.to" to it.toString() },
            ))
        }
        scope.launch {
            repo.saveTxn(t)
            // 轉帳手續費另記一筆支出
            if (type == TxnType.TRANSFER && feeMinor != null) repo.saveTxn(
                TxnEntity(
                    type = TxnType.EXPENSE, amount = feeMinor, currency = currency, accountId = accountId,
                    merchant = "轉帳手續費", date = t.date, source = TxnSource.MANUAL,
                ),
            )
            if (again) {
                expr = ""; merchant = ""; note = ""; toExpr = ""; feeExpr = ""; showFee = false
                time = LocalTime.now().withSecond(0).withNano(0)
                panel = if (settings.inputFromCategory && type != TxnType.TRANSFER) Panel.CATEGORY else Panel.AMOUNT
                snack.showSnackbar(com.enwei.moneymanager.ui.tr("已儲存，繼續記下一筆"))
            } else nav.popBackStack()
        }
    }

    fun nextAfterAmount() {
        calcMode = false
        panel = when {
            type == TxnType.TRANSFER && accountId == null -> Panel.ACCOUNT
            type == TxnType.TRANSFER && toAccountId == null -> Panel.TO_ACCOUNT
            type != TxnType.TRANSFER && categoryId == null -> Panel.CATEGORY
            accountId == null -> Panel.ACCOUNT
            else -> Panel.NONE
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { BackTopBar(type.label, { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TxnType.entries.forEach { t ->
                        val sel = t == type
                        val c = typeColor(t)
                        Box(
                            Modifier.weight(1f).height(40.dp)
                                .background(if (sel) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                .border(BorderStroke(if (sel) 1.5.dp else 1.dp, if (sel) c else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
                                .clickable { if (type != t) { type = t; categoryId = null } },
                            contentAlignment = Alignment.Center,
                        ) { Text(t.label, color = if (sel) c else MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    // 備註也從這裡新增：寫在當天，每日列表與日曆會顯示
                    if (original == null) Box(
                        Modifier.weight(1f).height(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
                            .clickable { memoNew = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("備註", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                FormRow("日期", color, active = false, onClick = { panel = Panel.NONE; showDate = true }, trailing = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.height(48.dp).clickable { nav.navigate("recurring/0") },
                    ) {
                        Icon(Icons.Outlined.Repeat, "週期", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text("週期/分期", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }) {
                    Row {
                        Text("${date.year % 100}/${date.monthValue}/${date.dayOfMonth} (${Recurrence.weekdayName(date.dayOfWeek)})")
                        if (settings.showTime) Text("   %02d:%02d".format(time.hour, time.minute), modifier = Modifier.clickable { showTime = true })
                    }
                }
                FormRow("金額", color, active = panel == Panel.AMOUNT, onClick = { focusManager.clearFocus(); panel = Panel.AMOUNT }, trailing = {
                    if (type == TxnType.TRANSFER) OutlinedButton(
                        onClick = { showFee = !showFee; if (showFee) panel = Panel.FEE },
                        shape = RoundedCornerShape(6.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                        modifier = Modifier.height(36.dp),
                    ) { Text("手續費", color = MaterialTheme.colorScheme.onSurface) }
                }) {
                    Column {
                        Text(
                            (if (currency != lk.base) Money.symbolOf(currency) else "") + expr,
                            fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (Calculator.hasOperator(expr)) Calculator.evaluate(expr)?.let {
                            Text("= ${Calculator.format(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (currency != lk.base && amountMinor != null && lk.fx.hasRate(currency)) {
                            Text("≈ ${Money.withSymbol(lk.fx.convert(amountMinor, currency), lk.base)}",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (type == TxnType.TRANSFER && showFee) {
                    FormRow("手續費", color, panel == Panel.FEE, onClick = { focusManager.clearFocus(); panel = Panel.FEE }) {
                        Text(feeExpr, fontSize = 14.sp)
                    }
                }
                if (type == TxnType.TRANSFER) {
                    FormRow("轉出", color, panel == Panel.ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.ACCOUNT }) {
                        PlaceholderText(lk.account(accountId)?.name, "")
                    }
                    FormRow("轉入", color, panel == Panel.TO_ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.TO_ACCOUNT }, trailing = {
                        IconButton(onClick = { val a = accountId; accountId = toAccountId; toAccountId = a }) {
                            Icon(Icons.Filled.SwapVert, "對調", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }) {
                        PlaceholderText(toAcc?.name, "")
                    }
                    if (crossCurrency) FormRow("轉入金額", color, false, onClick = {}) {
                        UnderlineField(toExpr, { toExpr = it.filter { c -> c.isDigit() || c == '.' } }, "${toAcc!!.currency} 金額")
                    }
                } else {
                    FormRow("分類", color, panel == Panel.CATEGORY, onClick = { focusManager.clearFocus(); panel = Panel.CATEGORY }) {
                        PlaceholderText(lk.catLabel(categoryId), "")
                    }
                    FormRow(if (com.enwei.moneymanager.ui.I18n.english) "Account" else "帳戶", color, panel == Panel.ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.ACCOUNT }) {
                        PlaceholderText(lk.account(accountId)?.name, "")
                    }

                }
                FormRow("內容", color, merchantFocused, onClick = {}) {
                    UnderlineField(merchant, { merchant = it }, "",
                        Modifier.onFocusChanged { merchantFocused = it.isFocused; if (it.isFocused) panel = Panel.NONE })
                }
                // 自動完成：依過去紀錄建議，點選時一併帶入分類與帳戶
                if (settings.autocomplete && merchantFocused && merchant.isNotBlank()) {
                    val suggestions = remember(merchant, history, type) {
                        history.asSequence()
                            .filter { it.type == type && it.merchant != null && it.merchant.contains(merchant, true) && it.merchant != merchant }
                            .distinctBy { it.merchant }.take(8).toList()
                    }
                    if (suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 96.dp, end = 16.dp, top = 6.dp)) {
                        suggestions.forEach { s ->
                            Surface(
                                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 6.dp).clickable {
                                    merchant = s.merchant!!
                                    if (categoryId == null) categoryId = s.categoryId
                                    focusManager.clearFocus()
                                },
                            ) { Text(s.merchant!!, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
                FormRow("說明", color, false, onClick = {}) {
                    UnderlineField(note, { note = it }, "", Modifier.onFocusChanged { if (it.isFocused) panel = Panel.NONE }, singleLine = false)
                }
                original?.rawText?.let {
                    Text("原始內容：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.background)
                if (original == null) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { save(again = false) }, enabled = canSave, modifier = Modifier.weight(2f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp),
                        ) { Text("儲存") }
                        OutlinedButton(
                            onClick = { save(again = true) }, enabled = canSave, modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) { Text("繼續", color = MaterialTheme.colorScheme.onSurface) }
                    }
                } else {
                    Button(
                        onClick = { save(again = false) }, enabled = canSave, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp),
                    ) { Text(if (original?.status == TxnStatus.PENDING) "確認入帳" else "儲存") }
                    val smallPad = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp), contentPadding = smallPad) {
                            Text("刪除", color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                        }
                        OutlinedButton(onClick = {
                            val t = build() ?: return@OutlinedButton
                            scope.launch {
                                val now = LocalDate.now().atTime(LocalTime.now()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                repo.saveTxn(t.copy(id = 0, date = now, externalKey = null, rawText = null, source = TxnSource.MANUAL))
                                snack.showSnackbar(com.enwei.moneymanager.ui.tr("已複製為今天的新交易"))
                            }
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp), contentPadding = smallPad) {
                            Text("複製", color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
            if (panel != Panel.NONE) BottomPanel(
                panel = panel, lk = lk, type = type, expr = if (panel == Panel.FEE) feeExpr else expr,
                currency = currency, currencies = listOf(settings.baseCurrency, settings.subCurrency).distinct(),
                allCurrencies = currencyOptions(lk), calcMode = calcMode,
                selectedCategory = categoryId, selectedAccount = if (panel == Panel.TO_ACCOUNT) toAccountId else accountId,
                excludeAccount = if (panel == Panel.TO_ACCOUNT) accountId else null,
                onExpr = { if (panel == Panel.FEE) feeExpr = it else expr = it },
                onCurrency = { currency = it },
                onToggleCalc = { calcMode = !calcMode },
                onDoneAmount = {
                    val current = if (panel == Panel.FEE) feeExpr else expr
                    if (Calculator.hasOperator(current)) Calculator.evaluate(current)?.let {
                        if (panel == Panel.FEE) feeExpr = Calculator.format(it) else expr = Calculator.format(it)
                    } else if (panel == Panel.FEE) panel = Panel.NONE else nextAfterAmount()
                },
                suggestion = catSuggest?.takeIf { lk.category(it.categoryId) != null }?.let { it.categoryId to it.reason },
                onCategory = {
                    categoryId = it; catAuto = false
                    panel = when {
                        amountMinor == null -> Panel.AMOUNT
                        accountId == null -> Panel.ACCOUNT
                        else -> Panel.NONE
                    }
                },
                onAccount = { a ->
                    if (panel == Panel.TO_ACCOUNT) { toAccountId = a.id; panel = Panel.NONE }
                    else { accountId = a.id; panel = if (type == TxnType.TRANSFER && toAccountId == null) Panel.TO_ACCOUNT else Panel.NONE }
                },
                onEditCategories = { nav.navigate("categories?kind=${if (type == TxnType.INCOME) "INCOME" else "EXPENSE"}") },
                onEditAccounts = { nav.navigate("accounts") },
                onClose = { panel = Panel.NONE; calcMode = false },
            )
        }
    }

    if (memoNew) MemoDialog(
        com.enwei.moneymanager.data.Memo(0, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), ""),
        onSaved = { nav.popBackStack() },
    ) { memoNew = false }
        if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                    if (settings.showTime) showTime = true
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }
    if (showTime) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { time = LocalTime.of(state.hour, state.minute); showTime = false }) { Text("確定") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("刪除這筆交易？") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.deleteTxn(id); nav.popBackStack() } }) { Text("刪除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun FormRow(
    label: String, color: Color, active: Boolean, onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null, content: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 英文標籤（Category、Account）較長，欄寬加大並保持一行
        val english = com.enwei.moneymanager.ui.I18n.english
        // 英文用短標籤，避免和右側內容擠在一起
        val shown = if (english) when (label) { "轉出" -> "From"; "轉入" -> "To"; "轉入金額" -> "Received"; else -> label } else label
        Text(shown, Modifier.width(if (english) 96.dp else 76.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge, maxLines = 1, softWrap = false)
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { content() }
                trailing?.invoke()
            }
            HorizontalDivider(thickness = if (active) 2.dp else 1.dp, color = if (active) color else MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun PlaceholderText(value: String?, placeholder: String) {
    Text(value ?: placeholder, color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun UnderlineField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                inner()
            }
        },
    )
}

@Composable
private fun BottomPanel(
    panel: Panel, lk: Lookups, type: TxnType, expr: String, currency: String, currencies: List<String>, allCurrencies: List<String>,
    calcMode: Boolean, selectedCategory: Long?, selectedAccount: Long?, excludeAccount: Long?,
    onExpr: (String) -> Unit, onCurrency: (String) -> Unit, onToggleCalc: () -> Unit, onDoneAmount: () -> Unit,
    onCategory: (Long) -> Unit, onAccount: (AccountEntity) -> Unit,
    /** 自動分類建議：(分類 id, 原因) */
    suggestion: Pair<Long, String>? = null,
    onEditCategories: () -> Unit, onEditAccounts: () -> Unit, onClose: () -> Unit,
) {
    var currencyMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(PanelHeader).height(46.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (panel) {
                    Panel.AMOUNT -> "金額"; Panel.FEE -> "手續費"; Panel.CATEGORY -> "分類"; Panel.TO_ACCOUNT -> "轉入帳戶"; else -> "帳戶"
                },
                color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f),
            )
            when (panel) {
                Panel.AMOUNT, Panel.FEE -> Box {
                    IconButton(onClick = { currencyMenu = true }) { Icon(Icons.Outlined.Language, "幣別", tint = Color.White) }
                    DropdownMenu(currencyMenu, { currencyMenu = false }) {
                        allCurrencies.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onCurrency(c); currencyMenu = false }) }
                    }
                }
                Panel.CATEGORY -> IconButton(onClick = onEditCategories) { Icon(Icons.Outlined.Edit, "編輯分類", tint = Color.White) }
                else -> IconButton(onClick = onEditAccounts) { Icon(Icons.Outlined.Edit, "編輯帳戶", tint = Color.White) }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "關閉", tint = Color.White) }
        }
        when (panel) {
            Panel.AMOUNT, Panel.FEE -> {
                Row(Modifier.fillMaxWidth().background(CurrencyBar).height(48.dp)) {
                    (currencies + listOfNotNull(currency.takeIf { it !in currencies })).forEach { c ->
                        Box(
                            Modifier.width(64.dp).fillMaxHeight().background(if (c == currency) CurrencyBarSelected else Color.Transparent)
                                .clickable { onCurrency(c) },
                            contentAlignment = Alignment.Center,
                        ) { Text(Money.symbolOf(c).trim().ifEmpty { c }, color = Color.White) }
                    }
                }
                Keypad(expr, calcMode, onExpr, onToggleCalc, onDoneAmount)
            }
            Panel.CATEGORY -> {
                // 上方：搜尋分類與自動分類建議；下方：左欄大分類、右欄子分類
                val roots = lk.categories.filter { it.kind == type && !it.archived && it.parentId == null }.sortedBy { it.sortOrder }
                var query by remember(type) { mutableStateOf("") }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Box { if (query.isEmpty()) Text("搜尋分類", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp); inner() }
                            }
                        },
                    )
                    suggestion?.let { (cid, _) ->
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { onCategory(cid) },
                        ) { Text("建議：" + (lk.catLabel(cid) ?: ""), fontSize = 14.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
                    }
                }
                val q = query.trim()
                if (q.isNotEmpty()) {
                    // 搜尋：大分類與子分類一起找，顯示「大分類 / 子分類」
                    val hits = lk.categories.filter { it.kind == type && !it.archived && (it.name.contains(q, true) || lk.catLabel(it.id)?.contains(q, true) == true) }
                    LazyColumn(Modifier.fillMaxWidth().height(280.dp)) {
                        if (hits.isEmpty()) item { Text("找不到「$q」", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                        items(hits, key = { "q${it.id}" }) { c ->
                            Row(Modifier.fillMaxWidth().height(44.dp).clickable { onCategory(c.id) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(lk.catLabel(c.id) ?: c.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (c.id == selectedCategory) MaterialTheme.colorScheme.primary else Color.Unspecified)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                } else {
                    var openParent by remember(type) { mutableStateOf(lk.rootId(selectedCategory)?.takeIf { lk.children(it).isNotEmpty() }) }
                    val subs = openParent?.let { lk.children(it) }.orEmpty()
                    Row(Modifier.fillMaxWidth().height(300.dp)) {
                        LazyColumn(Modifier.weight(1f).fillMaxHeight()) {
                            items(roots, key = { it.id }) { c ->
                                val hasSub = lk.children(c.id).isNotEmpty()
                                val sel = c.id == openParent || (c.id == selectedCategory)
                                Row(
                                    Modifier.fillMaxWidth().height(44.dp)
                                        .background(if (sel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                        .clickable { if (hasSub) openParent = c.id else onCategory(c.id) }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (c.id == lk.rootId(selectedCategory)) MaterialTheme.colorScheme.primary else Color.Unspecified)
                                    if (hasSub) Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                        Box(Modifier.width(0.5.dp).fillMaxHeight().background(MaterialTheme.colorScheme.outlineVariant))
                        LazyColumn(Modifier.weight(1f).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant)) {
                            items(subs, key = { it.id }) { c ->
                                Row(
                                    Modifier.fillMaxWidth().height(44.dp).clickable { onCategory(c.id) }.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(c.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (c.id == selectedCategory) MaterialTheme.colorScheme.primary else Color.Unspecified)
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
            else -> {
                val accs = lk.accounts.filter { !it.archived && it.id != excludeAccount }
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(280.dp)) {
                    items(accs, key = { it.id }) { a ->
                        val sel = a.id == selectedAccount
                        Box(
                            Modifier.height(50.dp).background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).clickable { onAccount(a) }.padding(4.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(a.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Keypad(expr: String, calcMode: Boolean, onExpr: (String) -> Unit, onToggleCalc: () -> Unit, onDone: () -> Unit) {
    val keyHeight = 62.dp
    @Composable
    fun Key(modifier: Modifier, bg: Color = MaterialTheme.colorScheme.surface, onClick: () -> Unit, content: @Composable () -> Unit) {
        Box(
            modifier.height(keyHeight).background(bg).border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
    @Composable
    fun Digit(modifier: Modifier, label: String, value: String = label) = Key(modifier, onClick = {
        var e = expr
        value.forEach { e = Calculator.append(e, it) }
        onExpr(e)
    }) { Text(label, fontSize = 24.sp, textAlign = TextAlign.Center) }

    Column {
        if (calcMode) Row(Modifier.fillMaxWidth()) {
            listOf("+" to '+', "−" to '-', "×" to '×', "÷" to '÷').forEach { (l, k) ->
                Key(Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant, onClick = { onExpr(Calculator.append(expr, k)) }) {
                    Text(l, fontSize = 22.sp)
                }
            }
            Key(Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant, onClick = {
                Calculator.evaluate(expr)?.let { onExpr(Calculator.format(it)) }
            }) { Text("=", fontSize = 22.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            Digit(Modifier.weight(1f), "1"); Digit(Modifier.weight(1f), "2"); Digit(Modifier.weight(1f), "3")
            Key(Modifier.weight(1f), onClick = { onExpr(expr.dropLast(1)) }) { Icon(Icons.AutoMirrored.Outlined.Backspace, "刪除") }
        }
        Row(Modifier.fillMaxWidth()) {
            Digit(Modifier.weight(1f), "4"); Digit(Modifier.weight(1f), "5"); Digit(Modifier.weight(1f), "6")
            Key(Modifier.weight(1f), onClick = { onExpr(Calculator.append(expr, '-')) }) { Text("-", fontSize = 24.sp) }
        }
        Row(Modifier.fillMaxWidth()) {
            Digit(Modifier.weight(1f), "7"); Digit(Modifier.weight(1f), "8"); Digit(Modifier.weight(1f), "9")
            Key(Modifier.weight(1f), if (calcMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, onClick = onToggleCalc) {
                Icon(Icons.Outlined.Calculate, "計算機")
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Digit(Modifier.weight(1f), "00"); Digit(Modifier.weight(1f), "0"); Digit(Modifier.weight(1f), ".")
            Key(Modifier.weight(1f), MaterialTheme.colorScheme.primary, onClick = onDone) {
                Text(if (Calculator.hasOperator(expr)) "=" else "完成", color = Color.White, fontSize = 18.sp)
            }
        }
    }
}
