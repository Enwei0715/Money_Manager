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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.font.FontWeight
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
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.data.toMillis
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
import com.enwei.moneymanager.ui.TransferColor
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private enum class Panel { NONE, AMOUNT, CATEGORY, ACCOUNT, TO_ACCOUNT }

fun typeColor(t: TxnType) = when (t) { TxnType.INCOME -> IncomeColor; TxnType.EXPENSE -> ExpenseColor; TxnType.TRANSFER -> TransferColor }

private fun minorToExpr(minor: Long) = Money.fromMinor(minor).stripTrailingZeros().toPlainString()

/** 記一筆：表單＋底部計算機／分類格／帳戶格。 */
@Composable
fun TxnEditScreen(nav: NavController, id: Long, initialType: String?) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val history by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var original by remember { mutableStateOf<TxnEntity?>(null) }

    var type by rememberSaveable { mutableStateOf(initialType?.let { TxnType.valueOf(it) } ?: TxnType.EXPENSE) }
    var expr by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(settings.baseCurrency) }
    var accountId by rememberSaveable { mutableStateOf(settings.defaultAccountId) }
    var toAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var toExpr by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now()) }
    var panel by rememberSaveable { mutableStateOf(if (id == 0L) Panel.AMOUNT else Panel.NONE) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var merchantFocused by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == 0L || original != null) return@LaunchedEffect
        repo.getTxn(id)?.let { t ->
            original = t
            type = t.type; expr = minorToExpr(t.amount); currency = t.currency
            accountId = t.accountId; toAccountId = t.toAccountId; toExpr = t.toAmount?.let(::minorToExpr).orEmpty()
            categoryId = t.categoryId; merchant = t.merchant.orEmpty(); note = t.note.orEmpty(); date = t.date.toLocalDate()
        }
    }
    LaunchedEffect(accountId, lk.accounts) {
        if (original == null) lk.account(accountId)?.let { currency = it.currency }
    }
    BackHandler(enabled = panel != Panel.NONE) { panel = Panel.NONE }

    val color = typeColor(type)
    val amountMinor = Calculator.evaluate(expr)?.abs()?.let(Money::toMinor)?.takeIf { it > 0 }
    val toAcc = lk.account(toAccountId)
    val crossCurrency = type == TxnType.TRANSFER && toAcc != null && toAcc.currency != currency
    val canSave = amountMinor != null && accountId != null && (type != TxnType.TRANSFER || toAccountId != null)

    fun build(): TxnEntity? {
        val minor = amountMinor ?: return null
        val base = original ?: TxnEntity(type = type, amount = 0, date = 0)
        val keepTime = original?.date?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
        return base.copy(
            type = type, amount = minor, currency = currency, accountId = accountId,
            toAccountId = if (type == TxnType.TRANSFER) toAccountId else null,
            toAmount = if (crossCurrency) Calculator.evaluate(toExpr)?.abs()?.let(Money::toMinor) else null,
            categoryId = if (type == TxnType.TRANSFER) null else categoryId,
            merchant = merchant.trim().ifBlank { null }, note = note.trim().ifBlank { null },
            date = date.toMillis(keepTime ?: if (date == LocalDate.now()) LocalTime.now() else LocalTime.NOON),
            status = TxnStatus.CONFIRMED,
        )
    }

    fun save(again: Boolean) {
        val t = build() ?: return
        scope.launch {
            repo.saveTxn(t)
            if (again) {
                expr = ""; merchant = ""; note = ""; toExpr = ""; panel = Panel.AMOUNT
                snack.showSnackbar("已儲存，繼續記下一筆")
            } else nav.popBackStack()
        }
    }

    fun afterAmount() {
        panel = when {
            type == TxnType.TRANSFER && toAccountId == null -> Panel.TO_ACCOUNT
            type != TxnType.TRANSFER && categoryId == null -> Panel.CATEGORY
            accountId == null -> Panel.ACCOUNT
            else -> Panel.NONE
        }
    }

    Scaffold(
        topBar = {
            com.enwei.moneymanager.ui.components.BackTopBar(type.label, { nav.popBackStack() }) {
                if (original != null) {
                    IconButton(onClick = {
                        // 複製成今天的新交易
                        val t = build() ?: return@IconButton
                        scope.launch {
                            repo.saveTxn(t.copy(id = 0, date = LocalDate.now().toMillis(LocalTime.now()), externalKey = null, rawText = null))
                            snack.showSnackbar("已複製為今天的新交易")
                        }
                    }) { Icon(Icons.Filled.ContentCopy, "複製") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxnType.entries.forEach { t ->
                        val sel = t == type
                        val c = typeColor(t)
                        Box(
                            Modifier.weight(1f).height(36.dp)
                                .border(BorderStroke(1.dp, if (sel) c else MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(6.dp))
                                .background(if (sel) c.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(6.dp))
                                .clickable {
                                    if (type != t) { type = t; categoryId = null }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(t.label, color = if (sel) c else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (sel) FontWeight.Bold else null) }
                    }
                }
                FormRow("日期", color, active = false, onClick = { panel = Panel.NONE; showDate = true }) {
                    Text("${date.year}/${date.monthValue}/${date.dayOfMonth}（${Recurrence.weekdayName(date.dayOfWeek)}）")
                }
                FormRow("金額", color, active = panel == Panel.AMOUNT, onClick = { focusManager.clearFocus(); panel = Panel.AMOUNT }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(expr.ifEmpty { "0" }, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = color)
                            if (Calculator.hasOperator(expr)) Calculator.evaluate(expr)?.let {
                                Text("= ${Calculator.format(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        CurrencyChip(currency, currencyOptions(lk)) { currency = it }
                    }
                }
                if (currency != lk.base && amountMinor != null && lk.fx.hasRate(currency)) {
                    Text("≈ ${Money.format(lk.fx.convert(amountMinor, currency), lk.base)}（依目前匯率）",
                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 96.dp))
                }
                if (type == TxnType.TRANSFER) {
                    FormRow("轉出", color, panel == Panel.ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.ACCOUNT }) {
                        PlaceholderText(lk.account(accountId)?.name, "選擇帳戶")
                    }
                    FormRow("轉入", color, panel == Panel.TO_ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.TO_ACCOUNT }) {
                        PlaceholderText(toAcc?.name, "選擇帳戶")
                    }
                    if (crossCurrency) FormRow("轉入金額", color, false, onClick = {}) {
                        UnderlineField(toExpr, { toExpr = it.filter { c -> c.isDigit() || c == '.' } }, "${toAcc!!.currency} 金額")
                    }
                } else {
                    FormRow("分類", color, panel == Panel.CATEGORY, onClick = { focusManager.clearFocus(); panel = Panel.CATEGORY }) {
                        PlaceholderText(lk.category(categoryId)?.let { "${it.icon} ${it.name}" }, "選擇分類")
                    }
                    FormRow("帳戶", color, panel == Panel.ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.ACCOUNT }) {
                        PlaceholderText(lk.account(accountId)?.name, "選擇帳戶")
                    }
                }
                FormRow("內容", color, merchantFocused, onClick = {}) {
                    UnderlineField(merchant, { merchant = it }, if (type == TxnType.TRANSFER) "說明" else "商家／品項",
                        Modifier.onFocusChanged { merchantFocused = it.isFocused; if (it.isFocused) panel = Panel.NONE })
                }
                // 依過去紀錄建議內容，點選時一併帶入分類
                if (merchantFocused && merchant.isNotBlank()) {
                    val suggestions = remember(merchant, history) {
                        history.asSequence().filter { it.type == type && it.merchant != null && it.merchant.contains(merchant, true) && it.merchant != merchant }
                            .distinctBy { it.merchant }.take(6).toList()
                    }
                    if (suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 96.dp, end = 16.dp)) {
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
                    UnderlineField(note, { note = it }, "備註", Modifier.onFocusChanged { if (it.isFocused) panel = Panel.NONE }, singleLine = false)
                }
                original?.rawText?.let {
                    Text("原始內容：$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { save(again = false) }, enabled = canSave, modifier = Modifier.weight(2f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = color), shape = RoundedCornerShape(6.dp),
                    ) { Text(if (original?.status == TxnStatus.PENDING) "確認入帳" else "儲存") }
                    if (original == null) OutlinedButton(
                        onClick = { save(again = true) }, enabled = canSave, modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(6.dp),
                    ) { Text("繼續") }
                }
            }
            if (panel != Panel.NONE) BottomPanel(
                panel = panel, color = color, lk = lk, type = type, expr = expr,
                selectedCategory = categoryId, selectedAccount = if (panel == Panel.TO_ACCOUNT) toAccountId else accountId,
                excludeAccount = if (panel == Panel.TO_ACCOUNT) accountId else null,
                onExpr = { expr = it },
                onDoneAmount = {
                    if (Calculator.hasOperator(expr)) Calculator.evaluate(expr)?.let { expr = Calculator.format(it) } else afterAmount()
                },
                onCategory = { categoryId = it; panel = if (accountId == null) Panel.ACCOUNT else Panel.NONE },
                onAccount = { a ->
                    if (panel == Panel.TO_ACCOUNT) { toAccountId = a.id; panel = Panel.NONE }
                    else { accountId = a.id; panel = if (type == TxnType.TRANSFER && toAccountId == null) Panel.TO_ACCOUNT else Panel.NONE }
                },
                onEditCategories = { nav.navigate("categories") },
                onEditAccounts = { nav.navigate("accounts") },
                onClose = { panel = Panel.NONE },
            )
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("確定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } },
        ) { DatePicker(state) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("刪除這筆交易？") },
        confirmButton = { TextButton(onClick = { scope.launch { repo.deleteTxn(id); nav.popBackStack() } }) { Text("刪除") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

@Composable
private fun FormRow(label: String, color: Color, active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.width(80.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            Box(Modifier.weight(1f)) { content() }
        }
        HorizontalDivider(thickness = if (active) 2.dp else 1.dp, color = if (active) color else MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(start = 80.dp))
    }
}

@Composable
private fun PlaceholderText(value: String?, placeholder: String) {
    Text(value ?: placeholder, color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun UnderlineField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp),
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
private fun CurrencyChip(currency: String, options: List<String>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable { open = true }) {
            Text(currency, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onSelect(c); open = false }) }
        }
    }
}

@Composable
private fun BottomPanel(
    panel: Panel, color: Color, lk: Lookups, type: TxnType, expr: String,
    selectedCategory: Long?, selectedAccount: Long?, excludeAccount: Long?,
    onExpr: (String) -> Unit, onDoneAmount: () -> Unit, onCategory: (Long) -> Unit, onAccount: (AccountEntity) -> Unit,
    onEditCategories: () -> Unit, onEditAccounts: () -> Unit, onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)).padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (panel) { Panel.AMOUNT -> "金額"; Panel.CATEGORY -> "分類"; Panel.TO_ACCOUNT -> "轉入帳戶"; else -> "帳戶" },
                    color = MaterialTheme.colorScheme.surface, modifier = Modifier.weight(1f),
                )
                if (panel == Panel.CATEGORY) TextButton(onClick = onEditCategories) { Text("編輯", color = MaterialTheme.colorScheme.surface) }
                if (panel == Panel.ACCOUNT || panel == Panel.TO_ACCOUNT) TextButton(onClick = onEditAccounts) { Text("編輯", color = MaterialTheme.colorScheme.surface) }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "關閉", tint = MaterialTheme.colorScheme.surface) }
            }
            when (panel) {
                Panel.AMOUNT -> Keypad(expr, color, onExpr, onDoneAmount)
                Panel.CATEGORY -> {
                    val cats = lk.categories.filter { it.kind == type && !it.archived }
                    LazyVerticalGrid(GridCells.Fixed(4), Modifier.height(260.dp)) {
                        items(cats, key = { it.id }) { c ->
                            val sel = c.id == selectedCategory
                            Column(
                                Modifier.aspectRatio(1.15f).background(if (sel) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).clickable { onCategory(c.id) },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text(c.icon, fontSize = 22.sp)
                                Text(c.name, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                            }
                        }
                    }
                }
                else -> {
                    val accs = lk.accounts.filter { !it.archived && it.id != excludeAccount }
                    LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(240.dp)) {
                        items(accs, key = { it.id }) { a ->
                            val sel = a.id == selectedAccount
                            Column(
                                Modifier.height(64.dp).background(if (sel) color.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).clickable { onAccount(a) }.padding(4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                Text(a.type.icon)
                                Text(a.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Keypad(expr: String, color: Color, onExpr: (String) -> Unit, onDone: () -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(".", "0", "⌫", "+"),
    )
    Column {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { k ->
                    val isOp = k in listOf("÷", "×", "−", "+")
                    Box(
                        Modifier.weight(1f).height(52.dp)
                            .background(if (isOp) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            .clickable {
                                onExpr(
                                    when (k) {
                                        "⌫" -> expr.dropLast(1)
                                        "−" -> Calculator.append(expr, '-')
                                        else -> Calculator.append(expr, k[0])
                                    },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Icon(Icons.AutoMirrored.Filled.Backspace, "刪除")
                        else Text(k, fontSize = 20.sp, color = if (isOp) color else MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier.weight(1f).height(48.dp).background(MaterialTheme.colorScheme.surface)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant).clickable { onExpr("") },
                contentAlignment = Alignment.Center,
            ) { Text("清除", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(
                Modifier.weight(3f).height(48.dp).background(color).clickable(onClick = onDone),
                contentAlignment = Alignment.Center,
            ) { Text(if (Calculator.hasOperator(expr)) "=" else "完成", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
