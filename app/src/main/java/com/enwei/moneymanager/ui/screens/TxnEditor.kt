package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
import com.enwei.moneymanager.ui.AppText

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
            // 已經記過手續費：帶入金額，編輯時可以修改或清空
            repo.feeOf(t)?.let { f -> feeExpr = minorToExpr(f.amount); showFee = true }
            // 有代墊：金額欄顯示整筆總額，往來列出每個人欠你的部分（存檔時更新原本那幾筆，不會重複新增）
            note = com.enwei.moneymanager.data.stripSplitNote(t.note).orEmpty()
            val splits = repo.splitsOf(t)
            if (splits.isNotEmpty()) {
                expr = minorToExpr(t.amount + splits.sumOf { it.amount })
                splitOn = true; splitMe = true
                splitRaw = SplitRow.encode(splits.map { SplitRow(it.toAccountId, "", minorToExpr(it.amount), auto = false) })
            }
        }
    }
    LaunchedEffect(accountId, lk.accounts) {
        if (original == null) lk.account(accountId)?.let { currency = it.currency }
    }
    BackHandler(enabled = panel != Panel.NONE) { panel = Panel.NONE }
    // 第一次記帳：簡短介紹這頁幾個重點
    if (id == 0L) com.enwei.moneymanager.ui.components.PageTour("editor", listOfNotNull(
        com.enwei.moneymanager.ui.components.Coach.Step("ed.amount", "金額", "點選此處以計算機輸入，可輸入算式；右上角可加入手續費。"),
        if (settings.featureSplit) com.enwei.moneymanager.ui.components.Coach.Step("ed.people", "往來", "與他人分帳、由他人代付或收回欠款時，點選對象即可。") else null,
        com.enwei.moneymanager.ui.components.Coach.Step("ed.merchant", "商家",
            if (settings.locationHints) "輸入時會推薦附近及曾記錄的店家，選取後將一併記錄位置。" else "輸入時會推薦曾記錄的店家，並帶入上次使用的分類與帳戶。"),
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
    LaunchedEffect(merchant, type, amountMinor, accountId, date, time) {
        if (type == TxnType.TRANSFER) { catSuggest = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(300)
        val at = date.atTime(time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val sg = repo.suggestCategory(merchant.ifBlank { null }, type, at, amountMinor ?: 0, accountId)
        catSuggest = sg
        if (sg != null && id == 0L && (categoryId == null || catAuto)) { categoryId = sg.categoryId; catAuto = true }
    }

    // 帳戶依習慣帶入：這家店過去最常用的帳戶（最近十筆裡至少兩筆、六成以上）。
    // 自己選過帳戶就不再更動（只會改寫由這裡帶入的值）
    var autoAccount by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(merchant, type, history.size) {
        if (id != 0L || type == TxnType.TRANSFER || initialAccountId != null) return@LaunchedEffect
        if (accountId != null && accountId != autoAccount) return@LaunchedEffect
        val usable = lk.accounts.filter { !it.archived && it.type != com.enwei.moneymanager.data.AccountType.LOAN }.map { it.id }.toSet()
        val m = merchant.trim()
        val recent = if (m.length < 2) emptyList() else history.asSequence()
            .filter { it.type == type && it.status == TxnStatus.CONFIRMED && it.merchant?.trim() == m && it.accountId in usable }
            .sortedByDescending { it.date }.take(10).toList()
        val top = recent.groupingBy { it.accountId!! }.eachCount().maxByOrNull { it.value }
        // 沒有這家店的紀錄就不帶入（不拿「上一次用的帳戶」來猜，避免默默記錯帳戶）
        val pick = top?.takeIf { it.value >= 2 && it.value * 10 >= recent.size * 6 }?.key
        if (pick != null && pick != accountId) { accountId = pick; autoAccount = pick }
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
    fun normName(x: String) = x.lowercase().replace(Regex("[\\s\\-－·・()（）|｜]"), "")
    // 依你自己的記帳習慣：有些人把攤位寫在「說明」、店家寫美食街或賣場（例如 說明「泰刻」＋店家「第一餐廳」）。
    // 過去至少兩筆、且七成以上都是同一個店家的說明，視為「攤位 → 店家」的固定寫法；沒有這種習慣的人不受影響。
    val stallOwner = remember(history) {
        history.asSequence().filter { it.type == TxnType.EXPENSE && !it.merchant.isNullOrBlank() && !it.note.isNullOrBlank() }
            .map { it.note!!.split('・', '·').first().trim() to it.merchant!!.trim() }
            .filter { (stall, m) -> stall.length in 2..12 && stall != m }
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (stall, ms) ->
                val top = ms.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return@mapNotNull null
                if (top.value >= 2 && top.value * 10 >= ms.size * 7) stall to top.key else null
            }.toMap()
    }
    // 定位完成時（幾秒後）要用到最新的習慣對應，不是畫面剛開時還沒載入的空白
    val stallOwnerNow = androidx.compose.runtime.rememberUpdatedState(stallOwner)
    /** 地圖或推薦的店名 → 依習慣拆成（店家, 說明）：店名含有你習慣寫在說明的攤位時，店家用對應的那一家 */
    fun splitByHabit(placeName: String): Pair<String, String?> {
        val clean = cleanPlaceName(placeName)
        val key = normName(clean)
        val owners = stallOwnerNow.value
        // 店名包含攤位、攤位名稱相同，或店名是攤位的開頭（「京都」對「京都日式料理」）；只對到一家店才拆
        if (key.length < 2 || owners.values.any { normName(it) == key }) return clean to null
        val hits = owners.keys.filter { normName(it).let { n -> n.length >= 2 && (key.contains(n) || n.startsWith(key)) } }
        val owner = hits.map { owners.getValue(it) }.distinct().singleOrNull() ?: return clean to null
        return owner to hits.filter { owners[it] == owner }.maxBy { it.length }
    }
    /** 點選推薦或搜尋結果：帶入店家；攤位依習慣放到說明（說明已有內容時不覆蓋） */
    fun pickPlace(placeName: String, at: Pair<Double, Double>?) {
        val (m, stall) = splitByHabit(placeName)
        merchant = m; merchantAuto = false
        // 說明一律不自動帶入（由你自己寫）
        // 位置是攤位的位置：只有店家就是這個地點時才記成店家的位置
        merchantAt = if (stall == null) at else null
    }
    LaunchedEffect(Unit) {
        if (!settings.locationHints) return@LaunchedEffect
        val appCtx = com.enwei.moneymanager.MoneyApp.instance
        // 編輯舊的一筆：用這筆記帳時記下的位置推薦（沒有記下位置就不推薦）；新的一筆用目前位置
        val loc = if (id != 0L) com.enwei.moneymanager.data.PlaceHints.samples(appCtx).firstOrNull { it.txnId == id }?.let { s0 ->
            android.location.Location("sample").apply { latitude = s0.lat; longitude = s0.lon; accuracy = 30f }
        } ?: return@LaunchedEffect
        else kotlinx.coroutines.withTimeoutOrNull(8000) { com.enwei.moneymanager.data.PlaceHints.current(appCtx) } ?: return@LaunchedEffect
        hereLoc = loc
        val ph = com.enwei.moneymanager.data.PlaceHints
        val txns = repo.allTxns()
        // 先列這附近記過的店、已知位置的店（馬上有），再補 Google 反查與地圖上的店
        val hist = ph.historyNear(appCtx, loc, txns)
        val names = txns.filter { it.type == TxnType.EXPENSE }.mapNotNull { it.merchant?.trim() }.toSet()
        val known = ph.knownNear(loc, names)
        // 人就在某家記過的店（已知位置 50 公尺內）：店名直接帶入，分類跟著這家店過去的紀錄（比金額推測可靠）
        // 只有記新的一筆才自動帶入；編輯舊的一筆只列推薦，不改你寫過的內容
        if (id == 0L) ph.storeHere(loc, names)?.let { here ->
            if (merchant.isBlank() || merchantAuto) {
                val (m, stall) = splitByHabit(here)
                merchant = m; merchantAuto = true
            }
        }
        nearby = (hist + known).distinct().take(8)
        // 附近的店：有 Mapbox 金鑰用 Mapbox，否則用 OpenStreetMap（Overpass）
        if (com.enwei.moneymanager.net.PlaceSearch.online) {
            val mb = com.enwei.moneymanager.net.PlaceSearch.nearby(loc.latitude, loc.longitude)
            placeLocs = placeLocs + mb.associate { cleanPlaceName(it.name) to (it.lat to it.lon) }
            // 還沒依位置帶入店名：Google／Mapbox 找到 30 公尺內的店，名稱和記過的店對得上（例如「美麗潔美學牙醫診所 | …」對「美麗潔美學牙醫診所」）就用你記的店名
            if (id == 0L && merchant.isBlank()) {
                // 30 公尺內、和你的紀錄對得上的店（店家名稱，或習慣寫在說明的攤位）
                val cands = mb.filter { (it.meters ?: 999f) <= 30f }.mapNotNull { p ->
                    val pn = normName(p.name)
                    names.firstOrNull { n -> normName(n).let { it.length >= 3 && (pn.contains(it) || it.contains(pn)) } }?.let { it to null as String? }
                        ?: splitByHabit(p.name).takeIf { it.second != null }
                }
                // 只有全部指向同一個店家才自動帶入；有好幾家（美食街）就不猜，留給下方的推薦讓你點
                val ms = cands.map { it.first }.distinct()
                if (ms.size == 1) {
                    merchant = ms.first(); merchantAuto = true
                    val stalls = cands.mapNotNull { it.second }.distinct()
                }
            }
            nearby = (hist + known + mb.map { cleanPlaceName(it.name) }).distinct().take(10)
            if (mb.isEmpty()) nearby = (hist + known + ph.nearbyStores(loc)).distinct().take(8)
        } else nearby = (hist + known + ph.nearbyStores(loc)).distinct().take(8)
    }
    LaunchedEffect(merchant, merchantFocused) {
        // 打字停下來 0.6 秒、至少 2 個字才查；剛從清單選好的店不再查（減少 API 呼叫）
        if (!settings.locationHints || !merchantFocused || merchant.trim().length < 2 || merchantAt != null || !com.enwei.moneymanager.net.PlaceSearch.online) { mbResults = emptyList(); return@LaunchedEffect }
        kotlinx.coroutines.delay(600)
        val c = hereLoc?.let { it.latitude to it.longitude } ?: com.enwei.moneymanager.data.PlaceHints.anchors(com.enwei.moneymanager.MoneyApp.instance).firstOrNull()
        // 只留相關的結果：名稱和輸入的字有共同的兩個字（或英文單字），或就在 300 公尺內；
        // 否則地圖會回傳只有一個字相同、好幾公里外的地方
        val q = normName(merchant)
        val grams = (0 until (q.length - 1).coerceAtLeast(0)).map { q.substring(it, it + 2) }.filter { g -> g.any { ch -> ch.code > 0x2E80 } }.toSet()
        val words = merchant.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 4 }.toSet()
        mbResults = com.enwei.moneymanager.net.PlaceSearch.search(merchant, c, 6).filter { r ->
            val rn = normName(r.name)
            r.name != merchant && ((r.meters ?: 99_999f) <= 300f || grams.any { rn.contains(it) } || (grams.isEmpty() && words.any { rn.contains(it) }))
        }
    }
    data class Guess(val categoryId: Long, val merchant: String?, val score: Double, val count: Int)
    // 依收支類型先整理一次歷史紀錄（歷史變動時才重算），之後每按一個鍵、打一個字只在這份較小的清單裡找，不再掃描全部交易
    val typeHist = remember(history, type) { history.filter { it.type == type } }
    val guessPool = remember(typeHist) {
        val now = System.currentTimeMillis()
        typeHist.filter { it.categoryId != null && it.status == TxnStatus.CONFIRMED && now - it.date < 365L * 86_400_000 }
    }
    val merchantPool = remember(typeHist) { typeHist.filter { !it.merchant.isNullOrBlank() }.distinctBy { it.merchant } }
    val noteCounts = remember(typeHist) { typeHist.mapNotNull { it.note?.trim()?.takeIf { n -> n.isNotEmpty() } }.groupingBy { it }.eachCount() }
    val notesByMerchant = remember(typeHist) {
        typeHist.filter { !it.note.isNullOrBlank() && !it.merchant.isNullOrBlank() }
            .groupBy({ it.merchant!!.trim() }, { it.note!!.trim() })
    }
    val guesses = remember(amountMinor, guessPool, accountId, time) {
        val amt = amountMinor
        if (amt == null || type == TxnType.TRANSFER || id != 0L) emptyList() else {
            val hourNow = time.hour
            val now = System.currentTimeMillis()
            guessPool.asSequence()
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
    // 只有金額時的推測：已有店名（自己填的或依位置帶入的）就以店名為準，不用金額覆蓋；
    // 至少兩筆相近的紀錄一致才自動帶入分類，店名只列在建議裡讓你點選，不自動填（避免 200 元掛號費被填成捷運）
    LaunchedEffect(guesses, merchant, catSuggest) {
        val g = guesses.firstOrNull() ?: return@LaunchedEffect
        if (merchant.isNotBlank() || g.count < 2 || catSuggest != null) return@LaunchedEffect
        val total = guesses.sumOf { it.count }
        if (g.count * 10 < total * 6) return@LaunchedEffect
        if (categoryId == null || catAuto) { categoryId = g.categoryId; catAuto = true }
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

    /**
     * 手續費另記一筆支出（轉帳、支出都可以加；新增與編輯都可以）。
     * 和主交易連在一起：編輯時更新同一筆、清空就刪掉，不會重複新增。
     */
    suspend fun saveFee(t: TxnEntity, mainId: Long) {
        if (t.type != TxnType.TRANSFER && t.type != TxnType.EXPENSE) return
        if (feeMinor == null && original == null) return
        val feeCat = lk.categories.firstOrNull { it.name.contains("手續費") }?.id ?: if (t.type == TxnType.EXPENSE) t.categoryId else null
        repo.saveFeeFor(t, mainId, feeMinor, feeCat)
    }

    fun save(again: Boolean) {
        val t0 = build() ?: return
        // 選了店家位置（搜尋結果、附近的店或在地圖上修改）：存成這家店的位置
        merchantAt?.let { ll -> merchant.trim().takeIf { it.isNotEmpty() }?.let { com.enwei.moneymanager.data.PlaceHints.setMerchantPin(it, ll.first, ll.second, merchantVia) } }
        if (payer != null) {
            scope.launch {
                val pid = payer.person ?: repo.debtPerson(payer.name)
                val t = t0.copy(accountId = pid)
                val mid = repo.saveTxn(t)
                saveFee(t0.copy(accountId = accountId ?: pid), mid)
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
                val mid = repo.saveWithSplits(t, shares)
                saveFee(t, mid)
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
            // 編輯支出：原本有代墊但這次把人都移除了 → 一起刪掉那幾筆代墊
            val newId = if (original != null && t.type == TxnType.EXPENSE) repo.saveWithSplits(t, emptyList()) else repo.saveTxn(t)
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
            saveFee(t, newId)
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
                if (!original?.rawText.isNullOrBlank()) com.enwei.moneymanager.ui.components.BadgedIconButton({ showSource = true }, 0, "來源通知",
                    dot = true, badgeColor = MaterialTheme.colorScheme.primary) {
                    Icon(androidx.compose.material.icons.Icons.Outlined.Notifications, null)
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
                            // 最小 36dp、字大時跟著長高（不會把字底切掉）
                            Modifier.weight(1f).heightIn(min = 36.dp)
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
                    }
                    // 金額＋右邊空白處放手續費（不另外佔一列）：沒填時是「＋ 手續費」，填了顯示金額；點了用下方鍵盤輸入，清空即刪除
                    // 正在輸入哪一個：那一個後面有閃爍的游標，另一個變淡
                    val caretAlpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "caret").animateFloat(
                        1f, 0f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(530), androidx.compose.animation.core.RepeatMode.Reverse), label = "caret",
                    )
                    val feeTyping = showFee && panel == Panel.FEE
                    val amountTyping = panel == Panel.AMOUNT
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val amtColor = if (expr.isEmpty()) MaterialTheme.colorScheme.outlineVariant else if (debtMode) MaterialTheme.colorScheme.onSurface else color
                        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                (if (currency != lk.base) Money.symbolOf(currency) else "$ ") + expr.ifEmpty { "0" },
                                fontSize = androidx.compose.ui.unit.TextUnit(34f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.SemiBold,
                                color = if (feeTyping) amtColor.copy(alpha = 0.45f) else amtColor, maxLines = 1,
                            )
                            if (amountTyping) Box(Modifier.padding(start = 2.dp).size(width = 2.dp, height = 32.dp).background(color.copy(alpha = caretAlpha)))
                        }
                        if ((type == TxnType.TRANSFER || type == TxnType.EXPENSE) && !debtMode) {
                            // 輸入手續費時：按鈕填滿主題色、後面有閃爍游標，一看就知道鍵盤正在輸入手續費
                            val primary = MaterialTheme.colorScheme.primary
                            Row(
                                Modifier.heightIn(min = 40.dp)
                                    .background(if (feeTyping) primary else Color.Transparent, RoundedCornerShape(20.dp))
                                    .border(1.dp, primary.copy(alpha = if (feeTyping) 1f else 0.5f), RoundedCornerShape(20.dp))
                                    .clickable { showFee = true; focusManager.clearFocus(); panel = Panel.FEE }
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,  // 字上下置中
                            ) {
                                val onChip = if (feeTyping) MaterialTheme.colorScheme.onPrimary else primary
                                Text(when {
                                    feeTyping -> "手續費 $ " + feeExpr.ifEmpty { "0" }
                                    showFee && feeExpr.isNotBlank() -> "手續費 $ $feeExpr"
                                    else -> "＋ 手續費"
                                }, fontSize = AppText.secondary, color = onChip, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                if (feeTyping) Box(Modifier.padding(start = 2.dp).size(width = 2.dp, height = 18.dp).background(onChip.copy(alpha = caretAlpha)))
                            }
                        }
                    }
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
                    Text("建議", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    // 有往來對象時，依目前欠款用一句話說明這筆是哪一種往來（不算收入、支出）
                    val fromP = lk.account(accountId)?.takeIf { it.type == com.enwei.moneymanager.data.AccountType.LOAN }
                    val toP = toAcc?.takeIf { it.type == com.enwei.moneymanager.data.AccountType.LOAN }
                    if ((fromP == null) != (toP == null)) {
                        val bals by repo.balances.collectAsStateWithLifecycle(emptyList())
                        val p = toP ?: fromP!!
                        // 編輯既有的這筆時，先扣掉它本身對欠款的影響
                        val selfEffect = original?.takeIf { it.type == TxnType.TRANSFER }?.let { o ->
                            (if (o.toAccountId == p.id) o.amount else 0L) - (if (o.accountId == p.id) o.amount else 0L)
                        } ?: 0L
                        val bal = (bals.firstOrNull { it.account.id == p.id }?.balance ?: 0L) - selfEffect
                        val hint = if (toP != null) {
                            if (bal < 0) "還給 ${p.name}：你欠他的減少" else "借給 ${p.name}：他欠你的增加"
                        } else {
                            if (bal > 0) "${p.name} 還你：他欠你的減少" else "向 ${p.name} 借：你欠他的增加"
                        }
                        Text("$hint（不算收入、支出）", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp))
                    }
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
                            modifier = Modifier.clickable { pickPlace(n, placeLocs[n]); focusManager.clearFocus() },
                        ) { Text(n, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                    }
                }
                val placeIndent = if (com.enwei.moneymanager.ui.AppearancePrefs.icons) 102.dp else 72.dp
                // Mapbox 搜尋結果：店名＋地址與距離，點一下帶入店名與位置
                if (merchantFocused && mbResults.isNotEmpty() && type != TxnType.TRANSFER) Column(Modifier.fillMaxWidth().padding(start = placeIndent, end = 16.dp, top = 4.dp)) {
                    mbResults.take(5).forEach { r ->
                        Column(Modifier.fillMaxWidth().clickable {
                            pickPlace(r.name, r.lat to r.lon); mbResults = emptyList(); focusManager.clearFocus()
                        }.padding(vertical = 6.dp)) {
                            Text(r.name, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Text(listOfNotNull(r.meters?.let { if (it < 1000) "${it.toInt()} 公尺" else "%.1f 公里".format(it / 1000) }, r.address.ifBlank { null }).joinToString("・"),
                                fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        com.enwei.moneymanager.ui.components.LineDivider()
                    }
                }
                // 店家位置預覽：點一下修改位置
                val at = merchantAt ?: knownPlaces[merchant.trim()]
                // 輸入店名時地圖照樣顯示；只有列出搜尋結果時才暫時收起，避免畫面太長
                if (settings.locationHints && at != null && merchant.isNotBlank() && !(merchantFocused && mbResults.isNotEmpty()) && type != TxnType.TRANSFER) {
                    MapPreview(merchant.trim(), at, Modifier.fillMaxWidth().height(130.dp).padding(start = placeIndent, end = 16.dp, top = 6.dp)) { pickMap = true }
                }
                if (pickMap) MapPicker(merchant.trim(), at ?: hereLoc?.let { it.latitude to it.longitude } ?: (25.0136 to 121.5414)) { ll ->
                    if (ll != null) { merchantAt = ll; merchantVia = "地圖" }
                    pickMap = false
                }
                // 自動完成：依過去紀錄建議，點選時一併帶入分類與帳戶
                if (settings.autocomplete && merchantFocused && merchant.isNotBlank()) {
                    val suggestions = remember(merchant, merchantPool) {
                        merchantPool.asSequence()
                            .filter { it.merchant!!.contains(merchant, true) && it.merchant != merchant }
                            .take(8).toList()
                    }
                    // 你習慣寫在說明的攤位：打「京都」也會推薦「第二教學大樓餐廳・京都日式料理」，點一下店家與說明一起帶入
                    val stallHits = remember(merchant, stallOwner) {
                        val k = normName(merchant)
                        if (k.length < 2) emptyList() else stallOwner.filterKeys { normName(it).contains(k) }.entries.take(4).toList()
                    }
                    if (stallHits.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = 96.dp, end = 16.dp, top = 6.dp)) {
                        stallHits.forEach { (stall, owner) ->
                            Surface(
                                shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.padding(end = 6.dp).clickable {
                                    merchant = owner; merchantAuto = false
                                    if (note.isBlank() || note == stall) note = stall
                                    focusManager.clearFocus()
                                },
                            ) { Text("$owner・$stall", Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        }
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
                    val notes = remember(m, typed, noteCounts, notesByMerchant, stallOwner) {
                        val mineList = if (m.isEmpty()) emptyList() else notesByMerchant[m].orEmpty()
                        val mineCounts = mineList.groupingBy { it }.eachCount()
                        // 已有店家：只推薦這家店寫過的說明；這家店沒寫過時，不推薦別家店的攤位名稱（例如 Family 不會出現越香）
                        val base: Map<String, Int> = when {
                            typed.isNotEmpty() || m.isEmpty() -> (noteCounts.keys + mineCounts.keys).associateWith { (noteCounts[it] ?: 0) + (mineCounts[it] ?: 0) }
                            mineCounts.isNotEmpty() -> mineCounts
                            else -> noteCounts.filterKeys { n -> stallOwner[n].let { it == null || it == m } }
                        }
                        base.entries.asSequence()
                            .filter { (n, _) -> n != typed && n.length <= 30 && !n.startsWith("共 ") && (typed.isEmpty() || n.contains(typed, true)) }
                            .sortedWith(compareByDescending<Map.Entry<String, Int>> { e -> e.key in mineCounts }.thenByDescending { it.value })
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
                // 往來功能關閉時不顯示（編輯原本就有往來資料的交易仍保留，避免無法修改）
                if (type != TxnType.TRANSFER && (settings.featureSplit || splitRows.isNotEmpty() || paidBy)) {
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
                        OutlinedButton(onClick = {
                            // 直接刪除、回到上一頁，下方顯示「已刪除・復原」5 秒（不先跳確認框）
                            val gone = original
                            scope.launch {
                                repo.deleteTxn(id)
                                nav.popBackStack()
                                if (gone != null) com.enwei.moneymanager.ui.components.Undo.show(com.enwei.moneymanager.ui.tr("已刪除一筆交易")) { repo.restoreTxn(gone) }
                            }
                        }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(6.dp), contentPadding = smallPad) {
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
                splitTitle = splitRows.getOrNull(splitIdx)?.let { r -> "${r.person?.let { lk.account(it)?.name } ?: r.name.trim()} 欠你" },
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
        textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = AppText.body)),
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
    /** 正在輸入代墊金額時，鍵盤標題寫出是誰的部分 */
    splitTitle: String? = null,
) {
    var currencyMenu by remember { mutableStateOf(false) }
    // 顏色跟隨主題（深淺色、主題色），不再固定黑底與藍紫色
    val headerBg = MaterialTheme.colorScheme.surfaceVariant
    val onHeader = MaterialTheme.colorScheme.onSurfaceVariant
    val barBg = MaterialTheme.colorScheme.primaryContainer
    val onBar = MaterialTheme.colorScheme.onPrimaryContainer
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(headerBg).height(46.dp).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when (panel) {
                    Panel.AMOUNT -> "金額"; Panel.FEE -> "手續費"; Panel.SPLIT -> splitTitle ?: "分帳"; Panel.CATEGORY -> "分類"; Panel.TO_ACCOUNT -> "轉入帳戶"; else -> "帳戶"
                },
                color = onHeader, fontSize = AppText.body, modifier = Modifier.weight(1f),
            )
            when (panel) {
                Panel.AMOUNT, Panel.FEE, Panel.SPLIT -> Box {
                    IconButton(onClick = { currencyMenu = true }) { Icon(Icons.Outlined.Language, "幣別", tint = onHeader) }
                    DropdownMenu(currencyMenu, { currencyMenu = false }) {
                        allCurrencies.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onCurrency(c); currencyMenu = false }) }
                    }
                }
                Panel.CATEGORY -> IconButton(onClick = onEditCategories) { Icon(Icons.Outlined.Edit, "編輯分類", tint = onHeader) }
                else -> IconButton(onClick = onEditAccounts) { Icon(Icons.Outlined.Edit, "編輯帳戶", tint = onHeader) }
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "關閉", tint = onHeader) }
        }
        when (panel) {
            Panel.AMOUNT, Panel.FEE, Panel.SPLIT -> {
                Row(Modifier.fillMaxWidth().background(barBg).height(48.dp).horizontalScroll(rememberScrollState())) {
                    (currencies + listOfNotNull(currency.takeIf { it !in currencies })).forEach { c ->
                        Box(
                            Modifier.width(64.dp).fillMaxHeight().background(if (c == currency) onBar.copy(alpha = 0.16f) else Color.Transparent)
                                .clickable { onCurrency(c) },
                            contentAlignment = Alignment.Center,
                        ) { Text(Money.symbolOf(c).trim().ifEmpty { c }, color = onBar, fontWeight = if (c == currency) androidx.compose.ui.text.font.FontWeight.Bold else null) }
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
                        textStyle = MaterialTheme.typography.bodyLarge.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = AppText.body)),
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
                            com.enwei.moneymanager.ui.components.RowDivider()
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
                                com.enwei.moneymanager.ui.components.RowDivider()
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
                                com.enwei.moneymanager.ui.components.RowDivider()
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
                Text(if (Calculator.hasOperator(expr)) "=" else "完成", color = MaterialTheme.colorScheme.onPrimary, fontSize = AppText.title)
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
    if (person == null && name.isBlank()) return
    ChoiceLine("這筆是", listOf("他還你", "向他借"), kind, color, onKind)
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
 * 手動改過某人的金額就保留他的數字。下方選付款人、每人一列欠你的金額，最後一行是你的支出。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    // 誰付錢：我，或其中一位（別人先幫我付只能是一個人）
    val payers = listOf("我") + rows.map { nameOf(it) }
    ChoiceLine("付款人", payers, if (paidBy) 1 else 0, color) { m ->
        if (m == 0) { onPaidBy(false); onRows(rows.map { it.copy(auto = true) }) }
        else { onPaidBy(true); onRows(listOf(rows[m - 1])) }
    }
    if (paidBy) {
        if (total != null) FormRow("", color, false, onClick = {}) {
            Text("你欠 ${nameOf(rows.first())} ${Money.withSymbol(total, currency)}", fontSize = AppText.secondary, color = muted)
        }
        return
    }
    // 每個人一列：他要還你的金額（預設和你平分，可改）。正在輸入的那一列整列標色、有閃爍游標，並自動捲到鍵盤上方
    val caretAlpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "splitCaret").animateFloat(
        1f, 0f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(530), androidx.compose.animation.core.RepeatMode.Reverse), label = "splitCaret",
    )
    rows.forEachIndexed { i, r ->
        val typing = editing == i
        val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        LaunchedEffect(typing) { if (typing) { kotlinx.coroutines.delay(280); bring.bringIntoView() } }
        val del: @Composable () -> Unit = {
            Text("✕", color = muted, fontSize = AppText.body, modifier = Modifier.clickable { onRows(rows.filterIndexed { j, _ -> j != i }) }
                .padding(start = 10.dp, top = 6.dp, bottom = 6.dp))
        }
        FormRow("", color, typing, onClick = { onEditAmount(i) }, trailing = del) {
            Row(Modifier.bringIntoViewRequester(bring), verticalAlignment = Alignment.CenterVertically) {
                Text("${nameOf(r)} 欠你", fontSize = AppText.body, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Row(
                    Modifier.widthIn(min = 96.dp)
                        .background(if (typing) color.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(6.dp))
                        .border(if (typing) 1.5.dp else 1.dp, if (typing) color else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End,
                ) {
                    Text(Money.symbolOf(currency) + " " + r.expr.ifEmpty { "0" }, fontSize = AppText.body, maxLines = 1,
                        color = if (r.expr.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    if (typing) Box(Modifier.padding(start = 2.dp).size(width = 2.dp, height = 20.dp).background(color.copy(alpha = caretAlpha)))
                }
            }
        }
    }
    val others = rows.sumOf { Calculator.evaluate(it.expr)?.abs()?.let(Money::toMinor) ?: 0L }
    if (total != null) FormRow("", color, false, onClick = {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (others > total) "超過總額 ${Money.withSymbol(total, currency)}" else "你的支出 ${Money.withSymbol(total - others, currency)}",
                fontSize = AppText.secondary, color = if (others > total) MaterialTheme.colorScheme.error else muted, modifier = Modifier.weight(1f))
            // 一鍵平分（含你一份）；手動改過金額後才需要
            if (!rows.all { it.auto } || !includeMe) Text("平分", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onIncludeMe(true); onRows(rows.map { it.copy(auto = true) }) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
        }
    }
}

/** 標籤＋一排小選項（選中的填色），和表單其他列對齊 */
@Composable
private fun ChoiceLine(label: String, options: List<String>, sel: Int, color: Color, onSel: (Int) -> Unit) {
    FormRow("", color, false, onClick = {}) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Text(label, fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 6.dp))
            options.forEachIndexed { i, l ->
                val on = i == sel
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (on) color.copy(alpha = 0.14f) else Color.Transparent,
                    border = BorderStroke(1.dp, if (on) color else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.clickable { onSel(i) },
                ) {
                    Text(l, fontSize = AppText.secondary, color = if (on) color else MaterialTheme.colorScheme.onSurface, maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
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
                    Modifier.weight(1f).heightIn(min = 36.dp).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent, MaterialTheme.shapes.small)
                        .clickable { onKind(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(l, fontSize = AppText.secondary, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
        }
        Text("幫別人付錢（代墊、分攤）請切到「支出」，用下方的「分帳」", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
