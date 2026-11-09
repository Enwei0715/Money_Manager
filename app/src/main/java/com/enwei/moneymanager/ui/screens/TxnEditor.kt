package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
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
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Login
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.StickyNote2
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
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

private enum class Panel { NONE, AMOUNT, FEE, SPLIT, CATEGORY, ACCOUNT, TO_ACCOUNT }

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
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
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
    // 可以分給好幾個人：每一列＝對象（既有的往來對象或新名字）＋他的部分
    var splitRaw by rememberSaveable { mutableStateOf(SplitRow.encode(listOfNotNull(initialSplitPerson?.let { SplitRow(it, "", "", auto = true) }))) }
    // 平攤時算不算自己一份（「平攤」＝含我、「他們全付」＝不含我）
    var splitMe by rememberSaveable { mutableStateOf(true) }
    val splitRows = SplitRow.decode(splitRaw)
    // 正在用 App 內鍵盤輸入哪一個人的分帳金額
    var splitIdx by rememberSaveable { mutableStateOf(-1) }
    var debtOpen by remember { mutableStateOf(false) }
    // 收入：別人還我錢時選對象 → 存成「從他的往來帳戶轉進來」，不算收入
    var repayPerson by rememberSaveable { mutableStateOf<Long?>(null) }
    var repayName by rememberSaveable { mutableStateOf("") }
    // 收入的往來方向：0 他還我、1 向他借（記法相同，都是從他的往來帳戶轉進來，只差名稱）
    var repayKind by rememberSaveable { mutableStateOf(0) }
    // 支出「他先付」：這筆由對方先幫我付 → 我欠他，不從我的帳戶扣
    var paidBy by rememberSaveable { mutableStateOf(false) }
    // 往來模式：類型列選「往來」時，在頁面上直接選對象與方向（底層仍是支出＋代墊或轉帳）
    var debtMode by rememberSaveable { mutableStateOf(false) }
    var debtPerson by rememberSaveable { mutableStateOf<Long?>(null) }
    var debtNewName by rememberSaveable { mutableStateOf("") }
    var debtKind by rememberSaveable { mutableStateOf(-1) }
    var date by rememberSaveable { mutableStateOf(initialEpochDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    var time by rememberSaveable { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var panel by rememberSaveable {
        mutableStateOf(if (id != 0L || initialAmount != null) Panel.NONE else if (repo.settings.value.inputFromCategory) Panel.CATEGORY else Panel.AMOUNT)
    }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var merchantFocused by remember { mutableStateOf(false) }
    var noteFocused by remember { mutableStateOf(false) }
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
    // 第一次記帳：簡短介紹這頁幾個重點
    if (id == 0L) com.enwei.moneymanager.ui.components.PageTour("editor", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step("ed.amount", "金額", "點選此處以計算機輸入，可輸入算式；右上角可加入手續費。"),
        com.enwei.moneymanager.ui.components.Coach.Step("ed.people", "往來", "與他人分帳、由他人代付或收回欠款時，點選對象即可。"),
        com.enwei.moneymanager.ui.components.Coach.Step("ed.merchant", "商家", "輸入時會推薦附近及曾記錄的店家，選取後將一併記錄位置。"),
    ))
    // 打開 App 內的鍵盤或選單時，系統鍵盤一定收起來
    LaunchedEffect(panel) { if (panel != Panel.NONE) { focusManager.clearFocus(); keyboard?.hide() } }

    val color = typeColor(type)
    val amountMinor = Calculator.evaluate(expr)?.abs()?.let(Money::toMinor)?.takeIf { it > 0 }
    val feeMinor = if (showFee) Calculator.evaluate(feeExpr)?.abs()?.let(Money::toMinor)?.takeIf { it > 0 } else null
    val toAcc = lk.account(toAccountId)
    val crossCurrency = type == TxnType.TRANSFER && toAcc != null && toAcc.currency != currency
    val payer = if (type == TxnType.EXPENSE && paidBy && !debtMode) splitRows.firstOrNull() else null
    // 編輯舊的一筆：原本就沒有帳戶（例如匯入的資料）也可以直接存，不強迫補帳戶
    val accountOk = accountId != null || payer != null || (original != null && original?.accountId == null && type != TxnType.TRANSFER)
    val canSave = amountMinor != null && accountOk && (type != TxnType.TRANSFER || toAccountId != null)
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
    // 記新的一筆：一打開就讀目前位置，推薦附近的店名（這附近記過的店在前，再來是地圖上離你最近的店）
    var nearby by remember { mutableStateOf<List<String>>(emptyList()) }
    var hereLoc by remember { mutableStateOf<android.location.Location?>(null) }
    // 店家的位置：搜尋結果、附近的店帶的座標；選了店就記下它的位置（儲存時存成這家店的位置）
    var placeLocs by remember { mutableStateOf<Map<String, Pair<Double, Double>>>(emptyMap()) }
    var merchantAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var merchantVia by remember { mutableStateOf("搜尋") }
    var pickMap by remember { mutableStateOf(false) }
    val knownPlaces = remember { com.enwei.moneymanager.data.PlaceHints.merchantGeo().mapNotNull { (k, v) -> v?.let { k to it } }.toMap() + com.enwei.moneymanager.data.PlaceHints.merchantPins() }
    // Mapbox：打字時搜尋附近的店
    var mbResults by remember { mutableStateOf<List<com.enwei.moneymanager.net.Mapbox.Place>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (id != 0L || !settings.locationHints) return@LaunchedEffect
        val appCtx = com.enwei.moneymanager.MoneyApp.instance
        val loc = kotlinx.coroutines.withTimeoutOrNull(8000) { com.enwei.moneymanager.data.PlaceHints.current(appCtx) } ?: return@LaunchedEffect
        hereLoc = loc
        val ph = com.enwei.moneymanager.data.PlaceHints
        val txns = repo.allTxns()
        // 先列這附近記過的店、已知位置的店（馬上有），再補 Google 反查與地圖上的店
        val hist = ph.historyNear(appCtx, loc, txns)
        val known = ph.knownNear(loc, txns.mapNotNull { it.merchant?.trim() }.toSet())
        nearby = (hist + known).distinct().take(8)
        // 附近的店：有 Mapbox 金鑰用 Mapbox，否則用 OpenStreetMap（Overpass）
        if (com.enwei.moneymanager.net.PlaceSearch.online) {
            val mb = com.enwei.moneymanager.net.PlaceSearch.nearby(loc.latitude, loc.longitude)
            placeLocs = placeLocs + mb.associate { it.name to (it.lat to it.lon) }
            nearby = (hist + known + mb.map { it.name }).distinct().take(10)
            if (mb.isEmpty()) nearby = (hist + known + ph.nearbyStores(loc)).distinct().take(8)
        } else nearby = (hist + known + ph.nearbyStores(loc)).distinct().take(8)
    }
    LaunchedEffect(merchant, merchantFocused) {
        // 打字停下來 0.6 秒、至少 2 個字才查；剛從清單選好的店不再查（減少 API 呼叫）
        if (!merchantFocused || merchant.trim().length < 2 || merchantAt != null || !com.enwei.moneymanager.net.PlaceSearch.online) { mbResults = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(600)
        val c = hereLoc?.let { it.latitude to it.longitude } ?: com.enwei.moneymanager.data.PlaceHints.anchors(com.enwei.moneymanager.MoneyApp.instance).firstOrNull()
        mbResults = com.enwei.moneymanager.net.PlaceSearch.search(merchant, c, 6).filter { it.name != merchant }
    }
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

    val splitParts = if (type == TxnType.EXPENSE && !debtMode && !paidBy) splitRows.mapNotNull { r ->
        val v = Calculator.evaluate(r.expr)?.abs()?.let(Money::toMinor)?.takeIf { it > 0 } ?: return@mapNotNull null
        if (r.person == null && r.name.isBlank()) null else r to v
    } else emptyList()
    val splitReady = splitParts.isNotEmpty() && amountMinor != null && splitParts.sumOf { it.second } <= amountMinor

    /** 手續費另記一筆支出（轉帳、支出都可以加） */
    suspend fun saveFee(t: TxnEntity) {
        val fee = feeMinor ?: return
        if (t.type != TxnType.TRANSFER && t.type != TxnType.EXPENSE) return
        val feeCat = lk.categories.firstOrNull { it.name.contains("手續費") }?.id ?: if (t.type == TxnType.EXPENSE) t.categoryId else null
        repo.saveTxn(TxnEntity(
            type = TxnType.EXPENSE, amount = fee, currency = t.currency, accountId = t.accountId, categoryId = feeCat,
            merchant = if (t.type == TxnType.TRANSFER) "轉帳手續費" else listOfNotNull(t.merchant, "手續費").joinToString(" "),
            date = t.date, source = TxnSource.MANUAL, status = TxnStatus.CONFIRMED,
        ))
    }

    fun save(again: Boolean) {
        val t0 = build() ?: return
        // 選了店家位置（搜尋結果、附近的店或在地圖上修改）：存成這家店的位置
        merchantAt?.let { ll -> merchant.trim().takeIf { it.isNotEmpty() }?.let { com.enwei.moneymanager.data.PlaceHints.setMerchantPin(it, ll.first, ll.second, merchantVia) } }
        if (payer != null) {
            scope.launch {
                val pid = payer.person ?: repo.debtPerson(payer.name)
                val t = t0.copy(accountId = pid)
                repo.saveTxn(t)
                saveFee(t0.copy(accountId = accountId ?: pid))
                TxnScroll.focusDay = date
                if (again) {
                    expr = ""; merchant = ""; note = ""; splitRaw = ""; paidBy = false; feeExpr = ""; showFee = false
                    snack.showSnackbar(com.enwei.moneymanager.ui.tr("已儲存，繼續記下一筆"))
                } else nav.popBackStack()
            }
            return
        }
        val t = t0
        if (type == TxnType.INCOME && original == null && (repayPerson != null || repayName.isNotBlank())) {
            scope.launch {
                val pid = repayPerson ?: repo.debtPerson(repayName)
                val who = lk.account(pid)?.name ?: repayName.trim()
                repo.saveTxn(t.copy(type = TxnType.TRANSFER, accountId = pid, toAccountId = t.accountId, categoryId = null,
                    merchant = t.merchant ?: if (repayKind == 0) "$who 還款" else "向 $who 借款"))
                if (again) {
                    expr = ""; merchant = ""; note = ""; repayPerson = null; repayName = ""
                    snack.showSnackbar(com.enwei.moneymanager.ui.tr("已儲存，繼續記下一筆"))
                } else nav.popBackStack()
            }
            return
        }
        if (splitReady) {
            scope.launch {
                val shares = splitParts.map { (r, v) -> (r.person ?: repo.debtPerson(r.name)) to v }
                repo.saveWithSplits(t, shares)
                saveFee(t)
                TxnScroll.focusDay = date
                if (again) {
                    expr = ""; merchant = ""; note = ""; splitRaw = ""; splitMe = true; splitOn = false
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
        // 新記的一筆：回到交易清單時捲到這筆的日期
        if (original == null) TxnScroll.focusDay = date
        scope.launch {
            val newId = repo.saveTxn(t)
            // 新記的支出、時間在 30 分鐘內：記下目前位置，之後在這裡付款時可推測店家
            if (original == null && t.type == TxnType.EXPENSE && settings.locationHints && !t.merchant.isNullOrBlank() &&
                kotlin.math.abs(System.currentTimeMillis() - t.date) <= 30 * 60_000L) {
                val appCtx = com.enwei.moneymanager.MoneyApp.instance
                com.enwei.moneymanager.MoneyApp.instance.appScope.launch {
                    // 用打開記帳時讀到的位置（人還在店裡），沒有才重新讀
                    (hereLoc ?: com.enwei.moneymanager.data.PlaceHints.current(appCtx))?.let { com.enwei.moneymanager.data.PlaceHints.record(appCtx, newId, it) }
                }
            }
            // 手續費另記一筆支出
            if (original == null) saveFee(t)
            if (again) {
                expr = ""; merchant = ""; note = ""; toExpr = ""; feeExpr = ""; showFee = false
                time = LocalTime.now().withSecond(0).withNano(0)
                panel = if (settings.inputFromCategory && type != TxnType.TRANSFER) Panel.CATEGORY else Panel.AMOUNT
                snack.showSnackbar(com.enwei.moneymanager.ui.tr("已儲存，繼續記下一筆"))
            } else nav.popBackStack()
        }
    }

    /** 按儲存時還缺什麼：直接打開那一欄並提示，而不是讓按鈕灰掉不能按 */
    fun trySave(again: Boolean) {
        when {
            amountMinor == null -> { panel = Panel.AMOUNT; scope.launch { snack.showSnackbar("請輸入金額") } }
            !accountOk -> { panel = Panel.ACCOUNT; scope.launch { snack.showSnackbar("請選帳戶") } }
            type == TxnType.TRANSFER && toAccountId == null -> { panel = Panel.TO_ACCOUNT; scope.launch { snack.showSnackbar("請選轉入帳戶") } }
            else -> save(again)
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
    fun applyDebt(person: Long, kind: Int) {
        val name = lk.account(person)?.name.orEmpty()
        when (kind) {
            // 幫他付：一般支出＋代墊（別人的部分預設全部）
            0 -> { type = TxnType.EXPENSE; splitOn = true; splitRaw = SplitRow.encode(listOf(SplitRow(person, "", "", auto = true))); splitMe = false }
            // 借給他、我還他：從我的帳戶轉給他
            1, 4 -> { type = TxnType.TRANSFER; toAccountId = person; if (accountId == person) accountId = null; merchant = if (kind == 1) "借給 $name" else "還給 $name" }
            // 他還我、我向他借：從他轉進我的帳戶
            else -> { type = TxnType.TRANSFER; val mine = accountId?.takeIf { it != person }; accountId = person; toAccountId = mine; merchant = if (kind == 2) "$name 還款" else "向 $name 借" }
        }
        categoryId = null
    }
    if (debtOpen) DebtPicker({ debtOpen = false }) { person, kind -> debtOpen = false; debtMode = true; debtPerson = person; debtKind = kind; applyDebt(person, kind) }
    // 往來：選好對象與方向就套用（新名字先建立對象）
    LaunchedEffect(debtMode, debtPerson, debtKind) {
        if (!debtMode || debtKind < 0) return@LaunchedEffect
        val p = debtPerson ?: return@LaunchedEffect
        applyDebt(p, debtKind)
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            BackTopBar(when {
                debtMode -> "往來"
                type == TxnType.INCOME && original == null && (repayPerson != null || repayName.isNotBlank()) -> if (repayKind == 0) "收到還款" else "借入"
                type == TxnType.EXPENSE && payer != null -> "別人先付"
                else -> type.label
            }, { nav.popBackStack() }) {
                // 當天備註（不是交易）：收在右上角
                if (original == null) androidx.compose.material3.IconButton(onClick = { memoNew = true }) {
                    Icon(com.enwei.moneymanager.ui.components.LineIcons.IcNote, "當天備註")
                }
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
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding()
                .then(Modifier.pointerInput(Unit) { detectTapGestures { focusManager.clearFocus(); keyboard?.hide() } })) {
                // 類型：支出／收入／轉帳（＋股票），一條分段選擇，選中的填色
                val segs = buildList {
                    TxnType.entries.forEach { t -> add(Triple(t.label, !debtMode && type == t, typeColor(t)) to { debtMode = false; if (type != t) { type = t; categoryId = null } }) }
                    // 往來已整合：代墊在「支出 → 分帳」、別人還錢在「收入 → 誰還的」、借錢還錢在「轉帳」選往來對象
                    if (original == null && settings.featureStocks) add(Triple("股票", false, MaterialTheme.colorScheme.onSurfaceVariant) to { nav.popBackStack(); nav.navigate("trade/0") })
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small).padding(3.dp),
                ) {
                    segs.forEach { (info, onClick) ->
                        val (label, sel, c) = info
                        Box(
                            Modifier.weight(1f).height(36.dp)
                                .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent, MaterialTheme.shapes.small)
                                .clickable(onClick = onClick),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, fontSize = AppText.secondary, color = if (sel) c else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal) }
                    }
                }
                // 往來：在頁面上選對象與方向（不跳視窗）
                if (debtMode) DebtInline(debtPerson, { debtPerson = it }, debtNewName, { debtNewName = it }, debtKind, { k ->
                    debtKind = k
                    if (debtPerson == null && debtNewName.isNotBlank()) scope.launch { debtPerson = repo.debtPerson(debtNewName) }
                })
                // 金額：這頁的主角，獨立成一塊大字；點一下開計算機
                Column(
                    Modifier.fillMaxWidth().clickable { focusManager.clearFocus(); panel = Panel.AMOUNT }.coachTarget("ed.amount").padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("金額", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        if ((type == TxnType.TRANSFER || type == TxnType.EXPENSE) && !debtMode && original == null) Text(if (showFee) "手續費 ✓" else "＋手續費", fontSize = AppText.caption, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showFee = !showFee; if (showFee) panel = Panel.FEE }.padding(4.dp))
                    }
                    Text(
                        (if (currency != lk.base) Money.symbolOf(currency) else "$ ") + expr.ifEmpty { "0" },
                        fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.SemiBold,
                        color = if (expr.isEmpty()) MaterialTheme.colorScheme.outlineVariant else if (debtMode) MaterialTheme.colorScheme.onSurface else color,
                    )
                    if (Calculator.hasOperator(expr)) Calculator.evaluate(expr)?.let {
                        Text("= ${Calculator.format(it)}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (currency != lk.base && amountMinor != null && lk.fx.hasRate(currency)) {
                        Text("≈ ${Money.withSymbol(lk.fx.convert(amountMinor, currency), lk.base)}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // 預算：緊貼在金額下方（記這筆之後還剩多少），不把表單隔開
                    if (budget.isNotEmpty() && type == TxnType.EXPENSE) {
                        val add = if (currency == lk.base) (amountMinor ?: 0) else lk.fx.convert(amountMinor ?: 0, currency)
                        Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            budget.forEach { u ->
                                com.enwei.moneymanager.ui.components.BudgetInline(u.name.removeSuffix("預算").ifBlank { u.name }, u.used, u.cap, add, Modifier.fillMaxWidth())
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(top = 8.dp), thickness = if (panel == Panel.AMOUNT) 2.dp else 1.dp,
                        color = if (panel == Panel.AMOUNT) color else MaterialTheme.colorScheme.outlineVariant)
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
                if ((type == TxnType.TRANSFER || type == TxnType.EXPENSE) && showFee && original == null) {
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
                    // 有往來對象時，用一句話說明這筆轉帳代表什麼
                    val fromP = lk.account(accountId)?.takeIf { it.type == com.enwei.moneymanager.data.AccountType.LOAN }
                    val toP = toAcc?.takeIf { it.type == com.enwei.moneymanager.data.AccountType.LOAN }
                    val hint = when {
                        toP != null -> "給 ${toP.name}：借錢給他，或還他錢（他欠你的增加／你欠他的減少）"
                        fromP != null -> "從 ${fromP.name}：他還你錢，或你向他借（他欠你的減少／你欠他的增加）"
                        else -> null
                    }
                    @Suppress("UNUSED_VARIABLE") val unusedHint = hint
                    if (crossCurrency) FormRow("轉入金額", color, false, onClick = {}) {
                        UnderlineField(toExpr, { toExpr = it.filter { c -> c.isDigit() || c == '.' } }, "${toAcc!!.currency} 金額")
                    }
                } else {
                    // 收入選了「誰還的」：是還款不是收入，不需要分類
                    if (!(type == TxnType.INCOME && (repayPerson != null || repayName.isNotBlank()))) FormRow("分類", color, panel == Panel.CATEGORY, onClick = { focusManager.clearFocus(); panel = Panel.CATEGORY }) {
                        PlaceholderText(lk.catLabel(categoryId), "")
                    }
                    if (payer == null) FormRow(if (com.enwei.moneymanager.ui.I18n.english) "Account" else "帳戶", color, panel == Panel.ACCOUNT, onClick = { focusManager.clearFocus(); panel = Panel.ACCOUNT }) {
                        PlaceholderText(lk.account(accountId)?.name, "")
                    }

                }
                FormRow("商家", color, merchantFocused, onClick = {}) {
                    UnderlineField(merchant, { merchant = it; merchantAuto = false; merchantAt = null }, "",
                        Modifier.onFocusChanged { merchantFocused = it.isFocused; if (it.isFocused) panel = Panel.NONE }.coachTarget("ed.merchant"))
                }
                // 附近的店：還沒填商家（或正在填）時，點一下帶入
                if (nearby.isNotEmpty() && type != TxnType.TRANSFER && (merchant.isBlank() || merchantFocused)) Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(start = if (com.enwei.moneymanager.ui.AppearancePrefs.icons) 102.dp else 72.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    nearby.forEach { n ->
                        // 與說明的推薦同樣式：圓角底色小方框
                        Surface(
                            shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius),
                            color = if (merchant == n) color.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { merchant = n; merchantAuto = false; merchantAt = placeLocs[n]; focusManager.clearFocus() },
                        ) { Text(n, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    }
                }
                val placeIndent = if (com.enwei.moneymanager.ui.AppearancePrefs.icons) 102.dp else 72.dp
                // Mapbox 搜尋結果：店名＋地址與距離，點一下帶入店名與位置
                if (merchantFocused && mbResults.isNotEmpty() && type != TxnType.TRANSFER) Column(Modifier.fillMaxWidth().padding(start = placeIndent, end = 16.dp, top = 4.dp)) {
                    mbResults.take(5).forEach { r ->
                        Column(Modifier.fillMaxWidth().clickable {
                            merchant = r.name; merchantAuto = false; merchantAt = r.lat to r.lon; mbResults = emptyList(); focusManager.clearFocus()
                        }.padding(vertical = 6.dp)) {
                            Text(r.name, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(listOfNotNull(r.meters?.let { if (it < 1000) "${it.toInt()} 公尺" else "%.1f 公里".format(it / 1000) }, r.address.ifBlank { null }).joinToString("・"),
                                fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                // 店家位置預覽：點一下修改位置
                val at = merchantAt ?: knownPlaces[merchant.trim()]
                if (at != null && merchant.isNotBlank() && !merchantFocused && type != TxnType.TRANSFER) {
                    MapPreview(merchant.trim(), at, Modifier.fillMaxWidth().height(130.dp).padding(start = placeIndent, end = 16.dp, top = 6.dp)) { pickMap = true }
                }
                if (pickMap) MapPicker(merchant.trim(), at ?: hereLoc?.let { it.latitude to it.longitude } ?: (25.0136 to 121.5414)) { ll ->
                    if (ll != null) { merchantAt = ll; merchantVia = "地圖" }
                    pickMap = false
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
                FormRow("說明", color, noteFocused, onClick = {}) {
                    UnderlineField(note, { note = it }, "", Modifier.onFocusChanged { noteFocused = it.isFocused; if (it.isFocused) panel = Panel.NONE }, singleLine = false)
                }
                // 說明的推薦：這家店以前寫過的說明在前，再來是打的字對得上的；點一下帶入
                if (noteFocused && type != TxnType.TRANSFER) {
                    val m = merchant.trim()
                    val typed = note.trim()
                    val notes = remember(m, typed, history, type) {
                        val pool = history.asSequence().filter { it.type == type && !it.note.isNullOrBlank() }
                        val mine = pool.filter { m.isNotEmpty() && it.merchant?.trim() == m }.mapNotNull { it.note?.trim() }
                        val all = pool.mapNotNull { it.note?.trim() }
                        (mine + all).filter { it != typed && it.length <= 30 && !it.startsWith("共 ") && (typed.isEmpty() || it.contains(typed, true)) }
                            .groupingBy { it }.eachCount().entries
                            .sortedWith(compareByDescending<Map.Entry<String, Int>> { e -> mine.any { it == e.key } }.thenByDescending { it.value })
                            .map { it.key }.take(8).toList()
                    }
                    if (notes.isNotEmpty()) Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .padding(start = if (com.enwei.moneymanager.ui.AppearancePrefs.icons) 102.dp else 72.dp, end = 16.dp, top = 6.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 跟商家的推薦同一個樣子：圓角底色小方框
                        notes.forEach { n ->
                            Surface(
                                shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { note = n; focusManager.clearFocus() },
                            ) { Text(n, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        }
                    }
                }
                // 往來（分帳、別人先付、還款）：放在表單最後，主要欄位一路填下來不被打斷
                if (type != TxnType.TRANSFER) {
                    if (!debtMode && type == TxnType.EXPENSE && (original == null || original?.type == TxnType.EXPENSE)) SplitSection(
                        splitMe, { splitMe = it }, paidBy, { paidBy = it },
                        editing = if (panel == Panel.SPLIT) splitIdx else -1,
                        onEditAmount = { i -> splitIdx = i; panel = Panel.SPLIT },
                        splitRows, { splitRaw = SplitRow.encode(it) }, amountMinor, currency, color,
                        onFieldFocus = { panel = Panel.NONE },
                    )
                    if (type == TxnType.INCOME && original == null) RepayRow(repayPerson, repayName, repayKind, color,
                        onPick = { id, name -> repayPerson = id; repayName = name }, onKind = { repayKind = it },
                        onFieldFocus = { panel = Panel.NONE })
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.background)
                if (original == null) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { trySave(again = false) }, modifier = Modifier.weight(2f).height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), shape = RoundedCornerShape(6.dp),
                        ) { Text("儲存") }
                        OutlinedButton(
                            onClick = { trySave(again = true) }, modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(6.dp),
                        ) { Text("繼續", color = MaterialTheme.colorScheme.onSurface) }
                    }
                } else {
                    Button(
                        onClick = { trySave(again = false) }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(48.dp),
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
                panel = panel, lk = lk, type = type, expr = when (panel) { Panel.FEE -> feeExpr; Panel.SPLIT -> splitRows.getOrNull(splitIdx)?.expr.orEmpty(); else -> expr },
                currency = currency, currencies = listOf(settings.baseCurrency, settings.subCurrency).distinct(),
                allCurrencies = currencyOptions(lk), calcMode = calcMode,
                selectedCategory = categoryId, selectedAccount = if (panel == Panel.TO_ACCOUNT) toAccountId else accountId,
                excludeAccount = if (panel == Panel.TO_ACCOUNT) accountId else null,
                onExpr = { v ->
                    when (panel) {
                        Panel.FEE -> feeExpr = v
                        Panel.SPLIT -> splitRows.getOrNull(splitIdx)?.let { r ->
                            splitRaw = SplitRow.encode(splitRows.toMutableList().also { it[splitIdx] = r.copy(expr = v, auto = false) })
                        }
                        else -> expr = v
                    }
                },
                onCurrency = { currency = it },
                onToggleCalc = { calcMode = !calcMode },
                onDoneAmount = {
                    if (panel == Panel.SPLIT) {
                        splitRows.getOrNull(splitIdx)?.let { r ->
                            val v = Calculator.evaluate(r.expr)?.let(Calculator::format) ?: r.expr
                            splitRaw = SplitRow.encode(splitRows.toMutableList().also { it[splitIdx] = r.copy(expr = v, auto = false) })
                        }
                        panel = Panel.NONE; splitIdx = -1
                        return@BottomPanel
                    }
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
        val icon = when (label) {
            "日期" -> com.enwei.moneymanager.ui.components.LineIcons.IcCalendar; "分類" -> com.enwei.moneymanager.ui.components.LineIcons.IcShapes; "帳戶", "Account", "轉出" -> com.enwei.moneymanager.ui.components.LineIcons.IcWallet
            "轉入" -> com.enwei.moneymanager.ui.components.LineIcons.IcArrowIn; "商家" -> com.enwei.moneymanager.ui.components.LineIcons.IcStore; "說明" -> com.enwei.moneymanager.ui.components.LineIcons.IcLines; "手續費" -> com.enwei.moneymanager.ui.components.LineIcons.IcPercent
            "分帳", "往來" -> com.enwei.moneymanager.ui.components.LineIcons.IcPeople; "對象" -> com.enwei.moneymanager.ui.components.LineIcons.IcPerson
            "對方付", "誰還的" -> com.enwei.moneymanager.ui.components.LineIcons.IcCash
            else -> null
        }
        // 列表圖示關閉時，欄位左邊也不顯示圖示
        if (icon != null && com.enwei.moneymanager.ui.AppearancePrefs.icons) Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 10.dp).size(20.dp))
        // 分帳的第二、三個人：沒有標籤，跟上一列的內容對齊
        else if (label.isEmpty() && com.enwei.moneymanager.ui.AppearancePrefs.icons) Spacer(Modifier.width(30.dp))
        Text(shown, Modifier.width(if (english) 84.dp else 56.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = AppText.secondary, maxLines = 1, softWrap = false)
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
private fun UnderlineField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier, singleLine: Boolean = true, numeric: Boolean = false) {
    val focus = LocalFocusManager.current
    val kb = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    BasicTextField(
        value = value, onValueChange = onChange, singleLine = singleLine,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = if (numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text,
            imeAction = if (singleLine) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { focus.clearFocus(); kb?.hide() }),
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
                    Panel.AMOUNT -> "金額"; Panel.FEE -> "手續費"; Panel.SPLIT -> "分帳"; Panel.CATEGORY -> "分類"; Panel.TO_ACCOUNT -> "轉入帳戶"; else -> "帳戶"
                },
                color = Color.White, fontSize = AppText.body, modifier = Modifier.weight(1f),
            )
            when (panel) {
                Panel.AMOUNT, Panel.FEE, Panel.SPLIT -> Box {
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
            Panel.AMOUNT, Panel.FEE, Panel.SPLIT -> {
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
                        Text(if (type == com.enwei.moneymanager.data.AccountType.LOAN) "往來對象（借錢、還錢）" else st.groupLabel(type), fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
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


/** 往來對象的小標籤：已選的填滿顏色，未選的只有細框。支出、收入共用同一個樣子。 */
@Composable
private fun PersonChip(label: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Text(label, fontSize = AppText.secondary, maxLines = 1,
        color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.background(if (on) color else Color.Transparent, MaterialTheme.shapes.small)
            .border(1.dp, if (on) color else MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp))
}

/** 往來對象一列：常往來的人排前面（可左右滑），最後一個「＋」輸入新名字。 */
@Composable
private fun PeopleRow(selected: (Long) -> Boolean, extra: List<String>, color: Color, onToggle: (Long) -> Unit, onExtra: (String) -> Unit, onAdd: (String) -> Unit, onFieldFocus: () -> Unit) {
    val lk = rememberLookups()
    val people0 = lk.accounts.filter { it.type == com.enwei.moneymanager.data.AccountType.LOAN && !it.archived }
    var freq by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(people0.size) {
        val ids = people0.map { it.id }.toSet()
        freq = repo.allTxns().filter { it.type == TxnType.TRANSFER }.flatMap { listOfNotNull(it.toAccountId, it.accountId) }
            .filter { it in ids }.groupingBy { it }.eachCount()
    }
    val people = people0.sortedByDescending { freq[it.id] ?: 0 }
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    FormRow("往來", color, false, onClick = {}) {
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp).coachTarget("ed.people")) {
            items(extra.size) { k -> PersonChip(extra[k], true, color) { onExtra(extra[k]) } }
            items(people.size) { k -> val p = people[k]; PersonChip(p.name, selected(p.id), color) { onToggle(p.id) } }
            item {
                Text("＋", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                        .clickable { adding = true }.padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
    }
    if (adding) FormRow("", color, true, onClick = {}, trailing = {
        Text("加入", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable {
            if (newName.isNotBlank()) onAdd(newName.trim())
            newName = ""; adding = false
        }.padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
    }) {
        UnderlineField(newName, { newName = it }, "新名字", Modifier.onFocusChanged { if (it.isFocused) onFieldFocus() })
    }
}

/** 一列文字選項（選中的用主題色），右邊可放一段狀態。 */
@Composable
private fun ModeLine(options: List<String>, sel: Int, color: Color, onSel: (Int) -> Unit, end: (@Composable () -> Unit)? = null) {
    val indent = if (com.enwei.moneymanager.ui.AppearancePrefs.icons) 102.dp else 72.dp
    Row(Modifier.fillMaxWidth().padding(start = indent, end = 16.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { i, l ->
            Text(l, fontSize = AppText.secondary, color = if (i == sel) color else MaterialTheme.colorScheme.primary,
                fontWeight = if (i == sel) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.clickable { onSel(i) }.padding(end = 18.dp, top = 4.dp, bottom = 4.dp))
        }
        Spacer(Modifier.weight(1f))
        end?.invoke()
    }
}

/**
 * 收入的往來：和支出同一排對象標籤（單選）。選了就不是收入：他還我（他欠我的減少）或向他借（我欠他的增加）。
 */
@Composable
private fun RepayRow(person: Long?, name: String, kind: Int, color: Color, onPick: (Long?, String) -> Unit, onKind: (Int) -> Unit, onFieldFocus: () -> Unit) {
    PeopleRow(
        selected = { it == person }, extra = listOfNotNull(name.trim().takeIf { person == null && it.isNotEmpty() }), color = color,
        onToggle = { id -> if (person == id) onPick(null, "") else onPick(id, "") },
        onExtra = { onPick(null, "") }, onAdd = { onPick(null, it) }, onFieldFocus = onFieldFocus,
    )
    if (person != null || name.isNotBlank()) ModeLine(listOf("他還我", "向他借"), kind, color, onKind)
}

/** 分帳的一個人：對象（既有往來帳戶 id，或新名字）、他的部分（可輸入算式）、是否自動平攤（手動改過金額就不再自動算）。 */
private data class SplitRow(val person: Long?, val name: String, val expr: String, val auto: Boolean = true) {
    companion object {
        fun encode(rows: List<SplitRow>) = rows.joinToString("\n") {
            "${it.person ?: ""}\t${it.name.replace("\t", " ").replace("\n", " ")}\t${it.expr}\t${if (it.auto) 1 else 0}"
        }
        fun decode(raw: String) = if (raw.isBlank()) emptyList() else raw.split("\n").map { line ->
            val f = line.split("\t")
            SplitRow(f.getOrNull(0)?.toLongOrNull(), f.getOrNull(1).orEmpty(), f.getOrNull(2).orEmpty(), f.getOrNull(3) != "0")
        }.filter { it.person != null || it.name.isNotBlank() }
    }
}

/**
 * 分帳：點常一起分帳的人（小標籤，可左右滑）就加進來，金額自動平攤（含你）；人數、總額變了會重算。
 * 手動改過某人的金額就保留他的數字。所有人下方只有一行：平攤、他們全付、我付多少。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SplitSection(
    includeMe: Boolean, onIncludeMe: (Boolean) -> Unit, paidBy: Boolean, onPaidBy: (Boolean) -> Unit,
    editing: Int, onEditAmount: (Int) -> Unit,
    rows: List<SplitRow>, onRows: (List<SplitRow>) -> Unit, total: Long?, currency: String, color: Color,
    onFieldFocus: () -> Unit,
) {
    val lk = rememberLookups()
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    fun nameOf(r: SplitRow) = r.person?.let { lk.account(it)?.name } ?: r.name.trim()
    // 自動平攤：沒手動改過的人，每人＝（總額 − 手動的部分）÷ 人數（含我時人數＋1）
    LaunchedEffect(total, rows.size, includeMe, rows.count { it.auto }) {
        if (total == null || rows.isEmpty()) return@LaunchedEffect
        val manual = rows.filter { !it.auto }.sumOf { Calculator.evaluate(it.expr)?.abs()?.let(Money::toMinor) ?: 0L }
        val autos = rows.count { it.auto }
        if (autos == 0) return@LaunchedEffect
        // 每人取整數（元），除不盡的零頭算在自己那份
        val each = ((total - manual).coerceAtLeast(0)) / (autos + if (includeMe) 1 else 0) / 100 * 100
        val next = rows.map { if (it.auto) it.copy(expr = minorToExpr(each)) else it }
        if (next != rows) onRows(next)
    }
    // 對象：和收入同一排標籤；「他先付」只能選一個人
    PeopleRow(
        selected = { id -> rows.any { it.person == id } }, extra = rows.filter { it.person == null }.map { it.name.trim() }, color = color,
        onToggle = { id ->
            val on = rows.any { it.person == id }
            onRows(when { on -> rows.filter { it.person != id }; paidBy -> listOf(SplitRow(id, "", "", auto = true)); else -> rows + SplitRow(id, "", "", auto = true) })
        },
        onExtra = { n -> onRows(rows.filter { !(it.person == null && it.name.trim() == n) }) },
        onAdd = { n -> onRows(if (paidBy) listOf(SplitRow(null, n, "", auto = true)) else rows + SplitRow(null, n, "", auto = true)) },
        onFieldFocus = onFieldFocus,
    )
    if (rows.isEmpty()) return
    val modeSel = when { paidBy -> 2; includeMe -> 0; else -> 1 }
    val pick: (Int) -> Unit = { m ->
        onPaidBy(m == 2)
        if (m == 2) onRows(rows.take(1)) else { onIncludeMe(m == 0); onRows(rows.map { it.copy(auto = true) }) }
    }
    if (paidBy) {
        ModeLine(listOf("平攤", "我全付", "他先付"), modeSel, color, pick) {
            if (total != null) Text("我欠 ${Money.withSymbol(total, currency)}", fontSize = AppText.caption, color = muted)
        }
        return
    }
    // 已加入的人：名字、他的部分（可改）、移除
    rows.forEachIndexed { i, r ->
        val del: @Composable () -> Unit = {
            Text("✕", color = muted, fontSize = AppText.body, modifier = Modifier.clickable { onRows(rows.filterIndexed { j, _ -> j != i }) }
                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
        }
        FormRow("", color, false, onClick = {}, trailing = del) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nameOf(r), fontSize = AppText.body, maxLines = 1, modifier = Modifier.weight(1f))
                // 金額用 App 內的計算機鍵盤輸入（不跳系統鍵盤）
                Column(Modifier.width(88.dp).clickable { onEditAmount(i) }) {
                    Text(r.expr.ifEmpty { "0" }, fontSize = AppText.body, maxLines = 1,
                        color = if (r.expr.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(vertical = 4.dp))
                    HorizontalDivider(thickness = if (editing == i) 2.dp else 1.dp, color = if (editing == i) color else MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
    // 所有人下方一行：平攤（含我一份）、我全付（整筆先墊，他們各還我）、他先付（他幫我付，我欠他）
    val others = rows.sumOf { Calculator.evaluate(it.expr)?.abs()?.let(Money::toMinor) ?: 0L }
    ModeLine(listOf("平攤", "我全付", "他先付"), if (rows.all { it.auto }) modeSel else -1, color, pick) {
        if (total != null) Text(if (others > total) "超過總額" else "我付 ${Money.withSymbol(total - others, currency)}", fontSize = AppText.caption,
            color = if (others > total) MaterialTheme.colorScheme.error else muted)
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


/** 往來：對象（既有或新名字）＋方向，直接放在記帳頁上。方向：0 幫他付、1 借給他、2 他還我、3 我向他借、4 我還他。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DebtInline(person: Long?, onPerson: (Long?) -> Unit, newName: String, onNewName: (String) -> Unit, kind: Int, onKind: (Int) -> Unit) {
    val lk = rememberLookups()
    val people = lk.accounts.filter { it.type == com.enwei.moneymanager.data.AccountType.LOAN && !it.archived }
    // 往來只處理借還；幫別人付錢（代墊、分攤）統一在「支出」的「分帳」記，不重複
    val kinds = listOf(1 to "借給他", 2 to "他還我", 3 to "我向他借", 4 to "我還他")
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("對象", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            people.forEach { p ->
                androidx.compose.material3.FilterChip(person == p.id, { onPerson(if (person == p.id) null else p.id); onNewName("") }, label = { Text(p.name, fontSize = AppText.caption) })
            }
        }
        if (person == null) UnderlineField(newName, onNewName, if (people.isEmpty()) "輸入對象名字" else "或輸入新的名字")
        Text("方向", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        Row(Modifier.fillMaxWidth().border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small)) {
            kinds.forEach { (i, l) ->
                val on = i == kind
                Box(
                    Modifier.weight(1f).height(36.dp).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, MaterialTheme.shapes.small)
                        .clickable { onKind(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(l, fontSize = AppText.secondary, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
        }
        Text("幫別人付錢（代墊、分攤）請切到「支出」，用下方的「分帳」", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
