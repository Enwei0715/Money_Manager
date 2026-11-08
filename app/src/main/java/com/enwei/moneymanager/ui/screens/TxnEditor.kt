package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.AppText

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
import com.enwei.moneymanager.data.groupLabel
import com.enwei.moneymanager.data.groupOrder
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
import androidx.compose.material.icons.outlined.Notifications
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
import androidx.compose.ui.text.font.FontWeight
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
import com.enwei.moneymanager.data.budgetUse
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
    /** 從「往來」的「記代墊」進來：預先打開代墊並選好這個人 */
    initialSplitPerson: Long? = null,
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
    // 新增時不預先帶帳戶（之前會一直帶上次的帳戶，容易記錯）；從帳戶頁進來時才帶那個帳戶
    var accountId by rememberSaveable { mutableStateOf(initialAccountId) }
    var toAccountId by rememberSaveable { mutableStateOf(initialToAccountId) }
    var toExpr by rememberSaveable { mutableStateOf("") }
    var categoryId by rememberSaveable { mutableStateOf<Long?>(null) }
    var merchant by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    // 代墊／分帳：幫別人付的部分不算自己的支出，記成對方欠我
    var splitOn by rememberSaveable { mutableStateOf(initialSplitPerson != null) }
    var splitPerson by rememberSaveable { mutableStateOf(initialSplitPerson) }
    var splitNewName by rememberSaveable { mutableStateOf("") }
    var splitExpr by rememberSaveable { mutableStateOf("") }
    var debtOpen by remember { mutableStateOf(false) }
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

    // 預算：選了分類後顯示這個月這個大分類（與總預算）還剩多少、記這筆之後剩多少
    var budget by remember { mutableStateOf<List<com.enwei.moneymanager.data.BudgetUse>>(emptyList()) }
    LaunchedEffect(categoryId, type, date, settings.budgets) {
        budget = if (type != TxnType.EXPENSE || settings.budgets.isEmpty()) emptyList()
        else runCatching { repo.budgetUse(categoryId, date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), excludeId = original?.id) }.getOrDefault(emptyList())
    }

    // 依金額推測：過去同類型、金額相近的交易（同時段、最近、同帳戶加分），推測分類與內容
    var merchantAuto by remember { mutableStateOf(false) }
    data class Guess(val categoryId: Long, val merchant: String?, val score: Double, val count: Int)
    val guesses = remember(amountMinor, type, history, accountId) {
        val amt = amountMinor
        if (amt == null || type == TxnType.TRANSFER || id != 0L) emptyList() else {
            val hourNow = LocalTime.now().hour
            val now = System.currentTimeMillis()
            history.asSequence()
                .filter { it.type == type && it.categoryId != null && it.status == TxnStatus.CONFIRMED && now - it.date < 365L * 86_400_000 }
                .filter { kotlin.math.abs(it.amount - amt) <= maxOf(amt / 10, 100) }
                .groupBy { it.categoryId!! to it.merchant?.trim()?.ifBlank { null } }
                .map { (k, l) ->
                    val score = l.sumOf { t ->
                        val h = Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).hour
                        val dh = minOf(kotlin.math.abs(h - hourNow), 24 - kotlin.math.abs(h - hourNow))
                        1.0 + (if (t.amount == amt) 2.0 else 0.0) + (if (dh <= 2) 1.5 else 0.0) +
                            (if (t.accountId == accountId) 1.0 else 0.0) + (if (now - t.date < 60L * 86_400_000) 1.0 else 0.0)
                    }
                    Guess(k.first, k.second, score, l.size)
                }
                .sortedByDescending { it.score }.take(3)
        }
    }
    // 還沒選分類、內容還沒填時，直接帶入最可能的一組（之後手動改就不再覆蓋）
    LaunchedEffect(guesses) {
        val g = guesses.firstOrNull() ?: return@LaunchedEffect
        if (categoryId == null || catAuto) { categoryId = g.categoryId; catAuto = true }
        if (g.merchant != null && (merchant.isBlank() || merchantAuto)) { merchant = g.merchant; merchantAuto = true }
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

    val splitMinor = if (splitOn && type == TxnType.EXPENSE) Calculator.evaluate(splitExpr)?.abs()?.let(Money::toMinor)?.takeIf { it > 0 } else null
    val splitReady = splitMinor != null && amountMinor != null && splitMinor <= amountMinor && (splitPerson != null || splitNewName.isNotBlank())

    fun save(again: Boolean) {
        val t = build() ?: return
        if (splitReady) {
            scope.launch {
                val person = splitPerson ?: repo.debtPerson(splitNewName)
                repo.saveWithSplit(t, person, splitMinor!!)
                if (again) {
                    expr = ""; merchant = ""; note = ""; splitExpr = ""; splitOn = false
                    snack.showSnackbar(com.enwei.moneymanager.ui.tr("已儲存，繼續記下一筆"))
                } else nav.popBackStack()
            }
            return
        }
        if (original == null) repo.settings.update { st ->
            st.copy(lastPicks = st.lastPicks + listOfNotNull(
                t.accountId?.let { "txn.account" to it.toString() },
                t.toAccountId?.takeIf { t.type == TxnType.TRANSFER }?.let { "txn.to" to it.toString() },
            ))
        }
        scope.launch {
            val newId = repo.saveTxn(t)
            // 新記的支出、時間在 30 分鐘內：記下目前位置，之後在這裡付款時可推測店家
            if (original == null && t.type == TxnType.EXPENSE && settings.locationHints && !t.merchant.isNullOrBlank() &&
                kotlin.math.abs(System.currentTimeMillis() - t.date) <= 30 * 60_000L) {
                val appCtx = com.enwei.moneymanager.MoneyApp.instance
                com.enwei.moneymanager.MoneyApp.instance.appScope.launch {
                    com.enwei.moneymanager.data.PlaceHints.current(appCtx)?.let { com.enwei.moneymanager.data.PlaceHints.record(appCtx, newId, it) }
                }
            }
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

    var showSource by remember { mutableStateOf(false) }
    if (showSource) original?.let { SourceDialog(it) { showSource = false } }
    if (debtOpen) DebtPicker({ debtOpen = false }) { person, kind ->
        debtOpen = false
        val name = lk.account(person)?.name.orEmpty()
        when (kind) {
            // 幫他付：一般支出＋代墊（別人的部分預設全部）
            0 -> { type = TxnType.EXPENSE; splitOn = true; splitPerson = person; if (amountMinor != null) splitExpr = minorToExpr(amountMinor) }
            // 借給他、我還他：從我的帳戶轉給他
            1, 4 -> { type = TxnType.TRANSFER; toAccountId = person; if (accountId == person) accountId = null; merchant = if (kind == 1) "借給 $name" else "還給 $name" }
            // 他還我、我向他借：從他轉進我的帳戶
            else -> { type = TxnType.TRANSFER; val mine = accountId?.takeIf { it != person }; accountId = person; toAccountId = mine; merchant = if (kind == 2) "$name 還款" else "向 $name 借" }
        }
        categoryId = null
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BackTopBar(type.label, { nav.popBackStack() }) {
                // 來源通知收在右上角的通知圖示：點了才看原文，表單保持乾淨
                if (!original?.rawText.isNullOrBlank()) androidx.compose.material3.IconButton(onClick = { showSource = true }) {
                    androidx.compose.material3.BadgedBox(badge = { androidx.compose.material3.Badge(containerColor = MaterialTheme.colorScheme.primary) }) {
                        Icon(androidx.compose.material.icons.Icons.Outlined.Notifications, "來源通知")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()) {
                // 第一列：收入／支出／轉帳（分段選擇，選中的填色）；第二列：往來、股票、備註等其他記錄方式（小膠囊）
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small),
                ) {
                    TxnType.entries.forEach { t ->
                        val sel = t == type
                        val c = typeColor(t)
                        Box(
                            Modifier.weight(1f).height(40.dp)
                                .background(if (sel) c.copy(alpha = 0.14f) else Color.Transparent, MaterialTheme.shapes.small)
                                .clickable { if (type != t) { type = t; categoryId = null } },
                            contentAlignment = Alignment.Center,
                        ) { Text(t.label, color = if (sel) c else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }
                if (original == null) Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    @Composable fun pill(label: String, onClick: () -> Unit) = Box(
                        Modifier.border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(50)).clickable(onClick = onClick)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    ) { Text(label, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    // 往來：借給誰、誰還我、幫誰付（代墊）
                    pill("往來") { debtOpen = true }
                    // 股票交易：換到股票交易編輯（買進、賣出、股利）
                    if (settings.featureStocks) pill("股票") { nav.popBackStack(); nav.navigate("trade/0") }
                    // 備註：寫在當天，每日列表與日曆會顯示
                    pill("備註") { memoNew = true }
                }
                FormRow("日期", color, active = false, onClick = { panel = Panel.NONE; showDate = true }, trailing = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.height(48.dp).clickable { nav.navigate("recurring/0") },
                    ) {
                        Icon(Icons.Outlined.Repeat, "週期", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                        Text("週期/分期", fontSize = AppText.badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        // 金額是這頁的主角：大字、依收支上色；還沒輸入時淡色 0
                        Text(
                            (if (currency != lk.base) Money.symbolOf(currency) else "$ ") + expr.ifEmpty { "0" },
                            fontSize = AppText.display, fontWeight = FontWeight.SemiBold,
                            color = if (expr.isEmpty()) MaterialTheme.colorScheme.outlineVariant else color,
                            modifier = Modifier.padding(vertical = 4.dp),
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
                // 依金額推測的分類與內容：點一下套用
                if (guesses.isNotEmpty()) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("猜你是", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    guesses.forEach { g ->
                        val sel = categoryId == g.categoryId && (g.merchant == null || merchant == g.merchant)
                        androidx.compose.material3.FilterChip(sel, {
                            categoryId = g.categoryId; catAuto = false
                            if (g.merchant != null) { merchant = g.merchant; merchantAuto = false }
                        }, label = {
                            Text(listOfNotNull(g.merchant, lk.catLabel(g.categoryId)).joinToString(" · ") + "（${g.count} 次）", fontSize = AppText.caption, maxLines = 1)
                        })
                    }
                }
                if (type == TxnType.TRANSFER && showFee) {
                    FormRow("手續費", color, panel == Panel.FEE, onClick = { focusManager.clearFocus(); panel = Panel.FEE }) {
                        Text(feeExpr, fontSize = AppText.body)
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
                    // 預算：每個一行，緊貼分類、和欄位內容對齊（不把分類和帳戶隔開）
                    if (budget.isNotEmpty()) {
                        val add = if (currency == lk.base) (amountMinor ?: 0) else lk.fx.convert(amountMinor ?: 0, currency)
                        Column(
                            Modifier.fillMaxWidth().padding(start = 16.dp + (if (com.enwei.moneymanager.ui.I18n.english) 96.dp else 76.dp), end = 16.dp, top = 4.dp, bottom = 2.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            budget.forEach { u ->
                                com.enwei.moneymanager.ui.components.BudgetInline(u.name.removeSuffix("預算").ifBlank { u.name }, u.used, u.cap, add, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    FormRow(if (com.enwei.moneymanager.ui.I18n.english) "Account" else "帳戶", color, panel == Panel.ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.ACCOUNT }) {
                        PlaceholderText(lk.account(accountId)?.name, "")
                    }
                    if (type == TxnType.EXPENSE && (original == null || original?.type == TxnType.EXPENSE)) SplitSection(
                        splitOn, { splitOn = it; if (it && splitExpr.isBlank() && amountMinor != null) splitExpr = minorToExpr(amountMinor / 2) },
                        splitPerson, { splitPerson = it; if (it != null) splitNewName = "" }, splitNewName, { splitNewName = it; splitPerson = null },
                        splitExpr, { splitExpr = it }, amountMinor, currency, color,
                    )

                }
                FormRow("內容", color, merchantFocused, onClick = {}) {
                    UnderlineField(merchant, { merchant = it; merchantAuto = false }, "",
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
                                shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), color = MaterialTheme.colorScheme.surfaceVariant,
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
        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = AppText.body),
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
                color = Color.White, fontSize = AppText.body, modifier = Modifier.weight(1f),
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
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = AppText.body),
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
                                Box { if (query.isEmpty()) Text("搜尋分類", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppText.body); inner() }
                            }
                        },
                    )
                    suggestion?.let { (cid, _) ->
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.clickable { onCategory(cid) },
                        ) { Text("建議：" + (lk.catLabel(cid) ?: ""), fontSize = AppText.body, maxLines = 1, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) }
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
                // 依帳戶頁的群組順序分組（信用卡放在一起、銀行放在一起…），群組內照帳戶排序
                val st = repo.settings.value
                val rank = st.groupOrder()
                val groups = lk.accounts.filter { !it.archived && it.id != excludeAccount }
                    .groupBy { it.type }.toList().sortedBy { (t, _) -> rank.indexOf(t).let { i -> if (i < 0) 99 else i } }
                    .map { (t, l) -> t to l.sortedWith(compareBy({ it.sortOrder }, { it.id })) }
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(300.dp)) {
                    groups.forEach { (type, accs) ->
                    item(key = "h${type.name}", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(st.groupLabel(type), fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 4.dp))
                    }
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
    }) { Text(label, fontSize = AppText.display, textAlign = TextAlign.Center) }

    Column {
        if (calcMode) Row(Modifier.fillMaxWidth()) {
            listOf("+" to '+', "−" to '-', "×" to '×', "÷" to '÷').forEach { (l, k) ->
                Key(Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant, onClick = { onExpr(Calculator.append(expr, k)) }) {
                    Text(l, fontSize = AppText.title)
                }
            }
            Key(Modifier.weight(1f), MaterialTheme.colorScheme.surfaceVariant, onClick = {
                Calculator.evaluate(expr)?.let { onExpr(Calculator.format(it)) }
            }) { Text("=", fontSize = AppText.title) }
        }
        Row(Modifier.fillMaxWidth()) {
            Digit(Modifier.weight(1f), "1"); Digit(Modifier.weight(1f), "2"); Digit(Modifier.weight(1f), "3")
            Key(Modifier.weight(1f), onClick = { onExpr(expr.dropLast(1)) }) { Icon(Icons.AutoMirrored.Outlined.Backspace, "刪除") }
        }
        Row(Modifier.fillMaxWidth()) {
            Digit(Modifier.weight(1f), "4"); Digit(Modifier.weight(1f), "5"); Digit(Modifier.weight(1f), "6")
            Key(Modifier.weight(1f), onClick = { onExpr(Calculator.append(expr, '-')) }) { Text("-", fontSize = AppText.display) }
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
                Text(if (Calculator.hasOperator(expr)) "=" else "完成", color = Color.White, fontSize = AppText.title)
            }
        }
    }
}


/**
 * 來源通知：像手機通知一樣顯示（App 名稱、標題、內容、時間）。
 * 合併過的交易會有多則，用分隔線「—」分開；「#AI」標記代表由 AI 判讀，顯示成小標籤。
 */
@Composable
private fun SourceDialog(t: TxnEntity, onDismiss: () -> Unit) {
    val raw = t.rawText.orEmpty()
    val byAi = raw.contains("#AI")
    val notes = raw.replace("#AI", "").split("\n—\n").map { it.trim() }.filter { it.isNotBlank() }
    val time = java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(t.createdAt))
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(androidx.compose.material.icons.Icons.Outlined.Notifications, null) },
        title = { Text(if (notes.size > 1) "來源通知（${notes.size} 則）" else "來源通知") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text(t.source.label, fontSize = AppText.caption, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    if (byAi) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(4.dp)) {
                        Text("AI 判讀", fontSize = AppText.caption, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
                notes.forEach { n ->
                    val lines = n.lines().map { it.trim() }.filter { it.isNotBlank() }
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(lines.firstOrNull().orEmpty(), fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                Text(time, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            lines.getOrNull(1)?.let { Text(it, fontSize = AppText.section, fontWeight = FontWeight.SemiBold) }
                            if (lines.size > 2) Text(lines.drop(2).joinToString("\n"), fontSize = AppText.secondary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("關閉") } },
    )
}


/**
 * 代墊／分帳：開啟後選「幫誰付」（往來對象或新名字）與別人的部分；
 * 儲存時支出只記自己的部分，別人的部分記成對方欠我（收回時不算收入）。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SplitSection(
    on: Boolean, onToggle: (Boolean) -> Unit,
    person: Long?, onPerson: (Long?) -> Unit, newName: String, onNewName: (String) -> Unit,
    expr: String, onExpr: (String) -> Unit, total: Long?, currency: String, color: Color,
) {
    val lk = rememberLookups()
    val people = lk.accounts.filter { it.type == com.enwei.moneymanager.data.AccountType.LOAN && !it.archived }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("代墊／分帳", fontSize = AppText.body, modifier = Modifier.weight(1f))
            androidx.compose.material3.Switch(on, onToggle)
        }
        if (on) {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                people.forEach { p ->
                    androidx.compose.material3.FilterChip(person == p.id, { onPerson(if (person == p.id) null else p.id) }, label = { Text(p.name, fontSize = AppText.caption) })
                }
            }
            if (person == null) androidx.compose.material3.OutlinedTextField(newName, onNewName, singleLine = true,
                label = { Text(if (people.isEmpty()) "幫誰付" else "或輸入新的名字") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.OutlinedTextField(expr, { onExpr(it.filter { c -> c.isDigit() || c == '.' }) }, singleLine = true,
                    label = { Text("別人的部分") }, modifier = Modifier.weight(1f),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                if (total != null) {
                    androidx.compose.material3.AssistChip({ onExpr(minorToExpr(total / 2)) }, { Text("一半", fontSize = AppText.caption) })
                    androidx.compose.material3.AssistChip({ onExpr(minorToExpr(total)) }, { Text("全部", fontSize = AppText.caption) })
                }
            }
            val split = Calculator.evaluate(expr)?.abs()?.let(Money::toMinor)
            if (total != null && split != null) Text(
                if (split > total) "別人的部分不能超過總額"
                else "自己的支出 ${Money.withSymbol(total - split, currency)}，對方欠你 ${Money.withSymbol(split, currency)}",
                fontSize = AppText.caption, color = if (split > total) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


/**
 * 往來：選對象（或新增名字）與方向。方向：0 幫他付（代墊）、1 借給他、2 他還我、3 我向他借、4 我還他。
 * 選完回到記帳畫面，金額與帳戶照常填（借貸是轉帳，不算收入或支出）。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DebtPicker(onDismiss: () -> Unit, onPick: (Long, Int) -> Unit) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val people = lk.accounts.filter { it.type == com.enwei.moneymanager.data.AccountType.LOAN && !it.archived }
    var person by remember { mutableStateOf<Long?>(null) }
    var newName by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(0) }
    val kinds = listOf("幫他付（代墊）", "借給他", "他還我", "我向他借", "我還他")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("往來") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (people.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    people.forEach { p ->
                        androidx.compose.material3.FilterChip(person == p.id, { person = if (person == p.id) null else p.id; newName = "" }, label = { Text(p.name, fontSize = AppText.caption) })
                    }
                }
                if (person == null) androidx.compose.material3.OutlinedTextField(newName, { newName = it }, singleLine = true,
                    label = { Text(if (people.isEmpty()) "對象名字" else "或輸入新的名字") }, modifier = Modifier.fillMaxWidth())
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    kinds.forEachIndexed { i, l -> androidx.compose.material3.FilterChip(kind == i, { kind = i }, label = { Text(l, fontSize = AppText.caption) }) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = person != null || newName.isNotBlank(), onClick = {
                scope.launch { onPick(person ?: repo.debtPerson(newName), kind) }
            }) { Text("下一步") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
