package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.AppText

import com.enwei.moneymanager.data.hasAiKey
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Surface
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import com.enwei.moneymanager.ui.components.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.ui.ExpenseColor
import com.enwei.moneymanager.ui.IncomeColor
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
fun SearchScreen(nav: NavController, startWithFilter: Boolean = false, startMonth: java.time.YearMonth? = null) {
    // 第一次打開這頁：簡短介紹
    com.enwei.moneymanager.ui.components.PageTour("search", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step(null, "搜尋與篩選", "輸入店家、說明或金額搜尋；可依分類、帳戶與期間篩選，並檢視篩選結果的圖表。"),
    ))
    val lk = rememberLookups()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    var q by rememberSaveable { mutableStateOf("") }
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    // 篩選方式（設定裡選）：0 圖表勾選頁、1 欄位篩選
    val chartStyle = settings.filterStyle == 0
    var showFilter by rememberSaveable { mutableStateOf(startWithFilter && !chartStyle) }
    var chartOpen by remember { mutableStateOf(startWithFilter && chartStyle) }
    // 搜尋與篩選共用同一個期間：預設是交易頁正在看的月份；點月份可切換「全部期間」
    var period by rememberSaveable { mutableStateOf<String?>((startMonth ?: com.enwei.moneymanager.core.Periods.monthOf(java.time.LocalDate.now(), repo.settings.value.monthStartDay)).toString()) }
    val month = period?.let { java.time.YearMonth.parse(it) }
    var chart by remember { mutableStateOf(ChartFilter(month = month)) }
    var chartApplied by remember { mutableStateOf(false) }
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
    val chartOn = chartApplied && chart.active
    val active = q.isNotBlank() || from != null || accountId != null || categoryId != null || minMinor != null || maxMinor != null || chartOn
    // 自訂日期區間優先；否則用上方的月份（全部期間時不限）
    val periodFilter = ChartFilter(month = month)
    // 沒有任何條件又是全部期間時不列出全部交易（太多），請先輸入或選月份
    val showList = active || month != null
    val result = remember(q, all, lk, from, to, accountId, categoryId, minMinor, maxMinor, chart, chartOn, month) {
        if (!showList) emptyList() else all.filter { t ->
            val d = t.date.toLocalDate()
            (from != null || periodFilter.inPeriod(t, settings.monthStartDay)) &&
            (!chartOn || chart.matches(t, lk)) &&
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
            BackTopBar("搜尋與篩選", { nav.popBackStack() }) {
                IconButton(onClick = { if (chartStyle) chartOpen = true else showFilter = !showFilter }) { Icon(Icons.Outlined.Tune, "篩選") }
            }
        },
    ) { pad ->
        // 搜尋框、期間、篩選與合計會跟著捲動收起，結果清單可以用滿畫面
        Column(Modifier.padding(pad).fillMaxSize()) {
        com.enwei.moneymanager.ui.components.CollapsingTop(Modifier.weight(1f).fillMaxWidth(), header = { Column {
            Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                androidx.compose.foundation.text.BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = AppText.body),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    decorationBox = { inner ->
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background, RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Search, null)
                            Spacer(Modifier.width(10.dp))
                            Box { if (q.isEmpty()) Text("商家、備註、分類、帳戶或金額", color = MaterialTheme.colorScheme.onSurfaceVariant); inner() }
                        }
                    },
                )
                // 期間列：搜尋與篩選共用（自訂日期區間時顯示區間，點一下清除）
                Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(enabled = from == null && month != null, onClick = { period = month?.minusMonths(1)?.toString(); chart = chart.copy(month = month?.minusMonths(1)) }) {
                        Icon(Icons.Filled.ChevronLeft, "上一期")
                    }
                    Text(
                        when {
                            from != null -> "$from ~ ${to ?: from}  ✕"
                            month != null -> "${month.year}年${month.monthValue}月"
                            else -> "全部期間"
                        },
                        fontSize = AppText.body, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f).clickable {
                            if (from != null) { from = null; to = null }
                            else {
                                val next = if (month == null) com.enwei.moneymanager.core.Periods.monthOf(java.time.LocalDate.now(), settings.monthStartDay) else null
                                period = next?.toString(); chart = chart.copy(month = next)
                            }
                        }.padding(vertical = 8.dp),
                    )
                    IconButton(enabled = from == null && month != null, onClick = { period = month?.plusMonths(1)?.toString(); chart = chart.copy(month = month?.plusMonths(1)) }) {
                        Icon(Icons.Filled.ChevronRight, "下一期")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (showFilter) {
                    FormLine("期間", onClick = { pickRange = true }) {
                        Text(if (from == null) "" else "$from ~ ${to ?: from}", fontSize = AppText.body)
                    }
                    FormLine("帳戶", onClick = { accMenu = true }) {
                        Box {
                            Text(lk.account(accountId)?.name.orEmpty(), fontSize = AppText.body)
                            DropdownMenu(accMenu, { accMenu = false }) {
                                DropdownMenuItem(text = { Text("全部") }, onClick = { accountId = null; accMenu = false })
                                lk.accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { accountId = a.id; accMenu = false }) }
                            }
                        }
                    }
                    FormLine("分類", onClick = { catMenu = true }) {
                        Box {
                            Text(lk.category(categoryId)?.name.orEmpty(), fontSize = AppText.body)
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
            // 有搜尋或篩選條件時：收入、支出圓環顯示符合的金額占這段期間的比例；沒有條件時顯示一般合計
            if (showList && active) {
                val part = remember(result, lk) { totalsOf(result, lk.fx) }
                val whole = remember(all, lk, from, to, month) {
                    totalsOf(all.filter { t ->
                        val d = t.date.toLocalDate()
                        (from != null || periodFilter.inPeriod(t, settings.monthStartDay)) && (from == null || !d.isBefore(from)) && (to == null || !d.isAfter(to))
                    }, lk.fx)
                }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Donut("收入", part.income, whole.income, IncomeColor, Modifier.weight(1f), ring = 58.dp)
                    Donut("支出", part.expense, whole.expense, ExpenseColor, Modifier.weight(1f), ring = 58.dp)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("合計", fontSize = AppText.secondary)
                        Text(Money.withSymbol(part.total, lk.base), fontSize = AppText.body, maxLines = 1)
                        Text("${result.size} 筆", fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            } else if (showList) SummaryBar(totalsOf(result, lk.fx), lk.base)
        } }) {
            LazyColumn(Modifier.fillMaxSize()) {
                if (showList && result.isEmpty()) item { NoData("找不到符合的交易") }
                result.groupBy { it.date.toLocalDate() }.toSortedMap(Comparator.reverseOrder()).forEach { (day, txns) ->
                    item(key = "s$day") { DayBlock(day, txns, lk, nav) }
                }
            }
        }
            // 圖表篩選套用中：底部顯示已選項目，可以「編輯」或清除
            if (chartOn) Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(start = 16.dp, end = 8.dp).navigationBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    chart.label(lk), fontSize = AppText.body, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { chartApplied = false }) { Text("清除") }
                TextButton(onClick = { chartOpen = true }) { Text("編輯") }
            }
        }
    }
    if (chartOpen) {
        androidx.activity.compose.BackHandler { chartOpen = false; if (startWithFilter && !chartApplied) nav.popBackStack() }
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { chartOpen = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            ChartFilterPage(
                lk, all, settings.monthStartDay, chart.copy(month = month),
                // 篩選頁換的月份也帶回搜尋（兩邊同一個期間）
                onApply = { chart = it; period = it.month?.toString(); from = null; to = null; chartApplied = true; chartOpen = false },
                onClose = { chartOpen = false; if (startWithFilter && !chartApplied) nav.popBackStack() },
            )
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
    val pendingTrades = repo.settings.flow.collectAsStateWithLifecycle().value.pendingTrades
    Scaffold(topBar = {
        BackTopBar(if (pending.isEmpty()) "待確認" else "待確認（${pending.size}）", { nav.popBackStack() }) {
            if (pending.size > 1) TextButton(onClick = { scope.launch { repo.confirmAllPending() } }) {
                Icon(Icons.Filled.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("全部確認")
            }
        }
    }) { pad ->
        LazyColumn(
            Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // 待確認的交易放最上面，AI 建議與合併重複放在下面
            // 股票交易待確認：成交通知缺股數、價格或證券帳戶，補上後才記入持股
            if (pendingTrades.isNotEmpty()) item {
                PendingSection("股票交易待確認（${pendingTrades.size}）") {
                    pendingTrades.sortedByDescending { it.date }.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        val sell = p.side == "SELL"
                        val missing = listOfNotNull("股數".takeIf { p.shares == null }, "價格".takeIf { p.price == null }, "證券帳戶".takeIf { p.brokerId == null })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((if (sell) "賣出 " else "買進 ") + "${p.symbol} ${p.name.orEmpty()}", fontSize = AppText.body, fontWeight = FontWeight.SemiBold,
                                    color = if (sell) com.enwei.moneymanager.ui.StockDown else com.enwei.moneymanager.ui.StockUp)
                                Text(java.time.LocalDate.ofEpochDay(p.date).let { "${it.monthValue}/${it.dayOfMonth}" } +
                                    (if (missing.isNotEmpty()) "・缺" + missing.joinToString("、") else ""),
                                    fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = {
                                repo.settings.update { st -> st.copy(pendingTrades = st.pendingTrades.filter { it.id != p.id }) }
                            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                            Button(onClick = { nav.navigate("trade/0?market=TW&symbol=${p.symbol}&side=${p.side}&pending=${p.id}") }) { Text("補上") }
                        }
                    }
                }
            }
            if (pending.isEmpty() && pendingTrades.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.CircleShape) {
                        Icon(Icons.Filled.DoneAll, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(18.dp).size(32.dp))
                    }
                    Text("都確認完了", fontSize = AppText.section, fontWeight = FontWeight.SemiBold)
                }
            }
            items(pending, key = { it.id }) { t -> PendingCard(t, lk, nav) }
            // AI 建議分類：只在使用者接受後才修改交易
            item { AiSuggestionSection(lk) }
            // 通知、錢包畫面、手動記帳可能重複記了同一筆：AI 判斷後合併
            item { DuplicateSection(lk) }
        }
    }
}

/** 一筆待確認：金額與內容為主，來源、帳戶、時間一行小字，下方三個動作。 */
@Composable
private fun PendingCard(t: com.enwei.moneymanager.data.TxnEntity, lk: Lookups, nav: NavController) {
    val scope = rememberCoroutineScope()
    val cat = lk.catLabel(t.categoryId)
    val dt = java.time.Instant.ofEpochMilli(t.date).atZone(java.time.ZoneId.systemDefault())
    com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth().clickable { nav.navigate("txn/${t.id}") }) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.merchant?.takeIf { it.isNotBlank() } ?: t.type.label, fontSize = AppText.section, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                com.enwei.moneymanager.ui.components.AmountText(t.amount, t.currency, t.type, Modifier.padding(end = 6.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = if (cat == null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(4.dp)) {
                    Text(cat ?: "未分類", fontSize = AppText.caption, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Text(
                    listOfNotNull(lk.account(t.accountId)?.name ?: "未指定帳戶", "%d/%d %02d:%02d".format(dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute), t.source.label)
                        .joinToString(" · "),
                    fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { scope.launch { repo.deleteTxn(t.id) } }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { nav.navigate("txn/${t.id}") }) { Text("修改") }
                Button(onClick = { scope.launch { repo.saveTxn(t.copy(status = TxnStatus.CONFIRMED)) } }, enabled = t.accountId != null) { Text("確認") }
            }
        }
    }
}

/** 待確認頁的區塊卡片：標題列（可帶右側按鈕）＋內容。 */
@Composable
private fun PendingSection(title: String, action: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            content()
        }
    }
}

/** 待確認頁的「AI 建議分類」：AI 整理「不明」花費，逐筆接受或略過。 */
@Composable
private fun AiSuggestionSection(lk: Lookups) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val unknownProgress by com.enwei.moneymanager.work.AiBackgroundWork.unknownProgress.collectAsStateWithLifecycle()
    var unknown by remember { mutableStateOf(0) }
    LaunchedEffect(all, settings.aiSuggestions) { unknown = repo.unknownCount() }
    val open = settings.aiSuggestions.filter { !it.rejected }
    val byId = all.associateBy { it.id }
    val aiOn = settings.aiEnabled && settings.hasAiKey
    if (!aiOn && open.isEmpty()) return
    // 沒有建議也沒有不明花費時不顯示整塊（避免只剩一行「自動整理完成」）
    if (open.isEmpty() && unknown == 0 && !unknownProgress.running && unknownProgress.error == null) return
    PendingSection(
        "AI 建議分類" + if (open.isNotEmpty()) "（${open.size}）" else "",
        action = if (open.size > 1) ({ TextButton(onClick = { scope.launch { open.forEach { repo.acceptAiSuggestion(it) } } }) { Text("全部接受") } }) else null,
    ) {
        if (aiOn && unknown > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Text("有 $unknown 筆不明或未分類的收支", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            OutlinedButton(enabled = !unknownProgress.running, onClick = {
                scope.launch { com.enwei.moneymanager.work.AiBackgroundWork.runUnknownSort() }
            }) { Text(if (unknownProgress.running) "整理中…" else "AI 整理") }
        }
        if (unknownProgress.running || unknownProgress.error != null) com.enwei.moneymanager.ui.components.AiWorkProgress(unknownProgress, Modifier.fillMaxWidth())
        open.forEachIndexed { i, sg ->
            val t = byId[sg.txnId] ?: return@forEachIndexed
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text((t.merchant ?: "未命名") + "　" + com.enwei.moneymanager.core.Money.withSymbol(t.amount, t.currency), fontSize = AppText.body,
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("→ ${lk.catLabel(sg.categoryId) ?: "?"}", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (sg.reason.isNotBlank()) Text(sg.reason, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { repo.rejectAiSuggestion(sg) }) { Text("略過") }
                Button(onClick = { scope.launch { repo.acceptAiSuggestion(sg) } }) { Text("接受") }
            }
        }
    }
}

/** 待確認頁的「可能重複的交易」：同金額、時間相近的兩筆，AI 判斷是不是同一筆並整理名稱與分類，使用者確認後合併。 */
@Composable
private fun DuplicateSection(lk: Lookups) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val all by repo.transactions.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val dupProgress by com.enwei.moneymanager.work.AiBackgroundWork.duplicatesProgress.collectAsStateWithLifecycle()
    val tidyProgress by com.enwei.moneymanager.work.AiBackgroundWork.autoTidyProgress.collectAsStateWithLifecycle()
    var pairs by remember { mutableStateOf<List<com.enwei.moneymanager.data.Repository.DupPair>>(emptyList()) }
    // AI 的判斷會記住（自動整理或按下判斷時存起來）
    val verdicts = settings.dupVerdicts
    val logFold = com.enwei.moneymanager.ui.components.rememberFold("pending.tidyLog", false)
    val showLog = logFold.open
    val aiOn = settings.aiEnabled && settings.hasAiKey
    LaunchedEffect(all, settings.dupDismissed) { pairs = repo.findDuplicates() }
    // 打開待確認頁時，順便請 AI 整理還沒判斷過的重複
    LaunchedEffect(aiOn, settings.aiAutoTidy) {
        if (aiOn && settings.aiAutoTidy) {
            com.enwei.moneymanager.work.AiBackgroundWork.runAutoTidy()
        }
    }
    // AI 自動整理紀錄：收合成一張小卡，點開才看
    if (settings.aiTidyLog.isNotEmpty()) PendingSection("AI 自動整理紀錄（${settings.aiTidyLog.size}）",
        action = { Box(Modifier.clickable { logFold.toggle() }) { com.enwei.moneymanager.ui.components.FoldToggle(showLog) } }) {
        if (showLog) settings.aiTidyLog.take(10).forEach { Text("• $it", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (tidyProgress.running || tidyProgress.error != null)
        com.enwei.moneymanager.ui.components.AiWorkProgress(tidyProgress, Modifier.fillMaxWidth().padding(horizontal = 4.dp))
    if (pairs.isEmpty()) return
    fun line(t: com.enwei.moneymanager.data.TxnEntity): String {
        val time = java.time.Instant.ofEpochMilli(t.date).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        val clock = if (time.toLocalTime() == java.time.LocalTime.MIDNIGHT) "" else " %02d:%02d".format(time.hour, time.minute)
        return "${t.source.label} · ${time.toLocalDate()}$clock · ${lk.account(t.accountId)?.name ?: "未指定"} · ${t.merchant ?: "未命名"}" +
            (lk.catLabel(t.categoryId)?.let { " · $it" } ?: "")
    }
    val sure = pairs.filter { verdicts[it.key]?.same == true }
    PendingSection("可能重複的交易（${pairs.size}）", action = if (sure.size > 1) ({
        TextButton(onClick = {
            scope.launch { sure.forEach { p -> verdicts[p.key].let { v -> repo.mergeDuplicate(p, v?.name, v?.categoryId) } } }
        }) { Text("合併 ${sure.size} 組") }
    }) else null) {
        if (aiOn) OutlinedButton(enabled = !dupProgress.running, onClick = {
            scope.launch { com.enwei.moneymanager.work.AiBackgroundWork.runDuplicatesCheck() }
        }) { Text(if (dupProgress.running) "判斷中…" else "請 AI 判斷並整理名稱") }
        if (dupProgress.running || dupProgress.error != null) com.enwei.moneymanager.ui.components.AiWorkProgress(dupProgress, Modifier.fillMaxWidth())
        pairs.forEach { p ->
            val v = verdicts[p.key]
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Text(com.enwei.moneymanager.core.Money.withSymbol(p.keep.amount, p.keep.currency) + " · ${p.keep.type.label}", fontSize = AppText.section, fontWeight = FontWeight.SemiBold)
            Text("保留：${line(p.keep)}", fontSize = AppText.caption)
            Text("刪除：${line(p.drop)}", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (v != null) Text(
                (if (v.same) "AI：同一筆" else "AI：不是同一筆") +
                    (if (v.same) listOfNotNull(v.name, lk.catLabel(v.categoryId)).joinToString(" · ", prefix = " → ").takeIf { it != " → " }.orEmpty() else "") +
                    (if (v.reason.isNotBlank()) "（${v.reason}）" else ""),
                fontSize = AppText.caption, color = if (v.same) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { repo.dismissDuplicate(p) }) { Text("不是重複") }
                Button(onClick = { scope.launch { repo.mergeDuplicate(p, v?.takeIf { it.same }?.name, v?.takeIf { it.same }?.categoryId) } }) { Text("合併") }
            }
        }
    }
}
