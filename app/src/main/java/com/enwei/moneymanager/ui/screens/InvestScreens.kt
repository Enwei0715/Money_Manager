package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.AppText

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Card
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.enwei.moneymanager.data.adjustGuardAfterSell
import com.enwei.moneymanager.data.guardFor
import com.enwei.moneymanager.ui.components.SectionTitle
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.enwei.moneymanager.ui.components.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.ui.components.FormInput
import com.enwei.moneymanager.ui.components.FormLine
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import com.enwei.moneymanager.data.HoldingView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.StockCalc
import com.enwei.moneymanager.core.StockSide
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.StockTradeEntity
import com.enwei.moneymanager.data.toRecord
import com.enwei.moneymanager.data.TxnSource
import com.enwei.moneymanager.ui.StockDown
import com.enwei.moneymanager.ui.StockUp
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.DateField
import com.enwei.moneymanager.ui.components.DropdownField
import com.enwei.moneymanager.ui.components.EmptyHint
import com.enwei.moneymanager.ui.components.LabelValue
import com.enwei.moneymanager.ui.components.NumberField
import com.enwei.moneymanager.ui.components.formatNum
import com.enwei.moneymanager.ui.components.formatPct
import com.enwei.moneymanager.work.SyncWorker
import kotlinx.coroutines.launch
import com.enwei.moneymanager.ui.components.coachTarget
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

private val StockSide.label get() = when (this) {
    StockSide.BUY -> "買進"; StockSide.SELL -> "賣出"; StockSide.CASH_DIVIDEND -> "現金股利"; StockSide.STOCK_DIVIDEND -> "股票股利"
}

private fun pnlColor(v: Double) = if (v >= 0) StockUp else StockDown

/** 帳戶頁的「股票」分頁：持股、即時股價、損益（台股漲紅跌綠）。 */
@Composable
fun StockContent(nav: NavController, refreshTick: Int = 0, onMessage: (String) -> Unit = {}) {
    com.enwei.moneymanager.ui.components.PageTour("stocks", listOf(
        com.enwei.moneymanager.ui.components.Coach.Step("stock.view", "持股", "實際買入的股票：市值、預估損益與今日漲跌，計入資產。點選個股查看庫存明細與每筆交易；下方列出各證券帳戶，已出清的股票收合於最後。"),
        com.enwei.moneymanager.ui.components.Coach.Step("stock.view", "觀察", "尚未買入、僅追蹤報價的股票，不計入資產。決定買進時點選「買進」記錄交易，該股即移入持股。"),
        com.enwei.moneymanager.ui.components.Coach.Step(null, "工具列", "右上角依序為搜尋股票、更新股價、手續費設定與新增交易。"),
    ))
    val lk = rememberLookups()
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val open = holdings.filter { it.shares > 0.0001 }
    val closed = holdings.filter { it.shares <= 0.0001 }
    val balances by repo.balances.collectAsStateWithLifecycle(emptyList())
    val byBroker by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    val brokers = balances.filter { it.account.type == AccountType.BROKERAGE && !it.account.archived }
        .sortedWith(compareBy({ it.account.sortOrder }, { it.account.id }))

    // 畫面開著時每 30 秒更新一次報價；手動按更新時 refreshTick 會變動
    val quotes by repo.quotes.collectAsStateWithLifecycle(emptyList())
    var addWatch by remember { mutableStateOf(false) }
    LaunchedEffect(refreshTick, holdings.isNotEmpty(), settings.watchlist) {
        if (holdings.isEmpty() && settings.watchlist.isEmpty()) return@LaunchedEffect
        var first = true
        while (true) {
            val r = runCatching { SyncWorker.refreshQuotes(repo) }
            // 開啟股票頁時（或按更新）順便同步除息
            if (first) MoneyApp.instance.appScope.launch { runCatching { SyncWorker.refreshDividends(repo) } }
            if (first && refreshTick > 0) onMessage(if (r.isSuccess) "股價已更新" else "更新失敗：${r.exceptionOrNull()?.let { com.enwei.moneymanager.ui.friendlyError(it) }.orEmpty()}")
            first = false
            // 台股盤中每 30 秒更新；收盤後價格不會再變，改成每 10 分鐘（美股持股仍會慢慢更新），省電省流量
            kotlinx.coroutines.delay(if (com.enwei.moneymanager.core.TaiwanHolidays.isTwTradingTime()) 30_000 else 600_000)
        }
    }

    val totalValue = open.sumOf { lk.fx.convert(Money.toMinor(it.marketValue), it.currency) }
    val totalCost = open.sumOf { lk.fx.convert(Money.toMinor(it.netCost), it.currency) }
    val dayChange = open.sumOf { lk.fx.convert(Money.toMinor(it.dayChange), it.currency) }
    val realized = holdings.sumOf { lk.fx.convert(Money.toMinor(it.realizedTotal), it.currency) }
    // 與持股明細相同的券商算法（扣預估賣出費用、成本扣股利）
    val unreal = open.sumOf { lk.fx.convert(Money.toMinor(it.unrealized), it.currency) }

    var view by com.enwei.moneymanager.ui.components.rememberTab("stock.view", 0)
    val closedFold = com.enwei.moneymanager.ui.components.rememberFold("invest.closed", false)
    val heldSyms = open.map { it.symbol.uppercase() }.toSet()
    // 報價時間：不是今天的要寫日期，才不會把上週五的價格當成現在的價格；收盤後不寫「每 30 秒更新」
    val quoteTime = if (settings.lastQuoteUpdate > 0) {
        val at = java.time.Instant.ofEpochMilli(settings.lastQuoteUpdate).atZone(java.time.ZoneId.systemDefault())
        val fmt = if (at.toLocalDate() == java.time.LocalDate.now()) "HH:mm" else "M/d HH:mm"
        "報價 " + SimpleDateFormat(fmt, Locale.TAIWAN).format(Date(settings.lastQuoteUpdate)) +
            (if (com.enwei.moneymanager.core.TaiwanHolidays.isTwTradingTime()) " · 每 30 秒更新" else " · 已收盤")
    } else ""

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // 持股（實際買入，計入資產）與觀察（只看報價，不計入資產）分成兩個檢視
        item(key = "seg") {
            // 與股票助理「紀律」分頁相同的子分頁樣式
            com.enwei.moneymanager.ui.components.SubTabs(
                listOf("持股（${open.size}）", "觀察（${settings.watchlist.size}）"), view,
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp).coachTarget("stock.view"),
            ) { view = it }
        }
        if (view == 0) {
            item(key = "sum") {
                SummaryBarCustom(
                    listOf(
                        "市值" to (Money.full(totalValue) to MaterialTheme.colorScheme.onSurface),
                        "預估損益" to (Money.full(unreal) to pnlColor(unreal.toDouble())),
                        "今日" to (Money.full(dayChange) to pnlColor(dayChange.toDouble())),
                    ),
                )
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        if (totalCost > 0) "報酬率 ${formatPct(Money.toDouble(unreal) / Money.toDouble(totalCost) * 100)}" else "",
                        color = pnlColor(unreal.toDouble()), fontSize = AppText.secondary, modifier = Modifier.weight(1f),
                    )
                    Text(quoteTime, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                com.enwei.moneymanager.ui.components.LineDivider()
            }
            if (open.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚無持股", fontSize = AppText.body)
                    Spacer(Modifier.height(6.dp))
                    Text("記錄第一筆買進後，持股、成本與損益將自動計算。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppText.secondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { nav.navigate("trade/0") }) { Text("記錄買進") }
                }
            } else {
                item(key = "hh") { StockHeader("持股 ${open.size} 檔", Money.withSymbol(totalValue, lk.base)) }
                // 一檔股票一個頁面：台股直接打開個股頁（走勢、分析、資訊，持股明細在頁面下方）；美股沒有 K 線資料，維持持股明細
                items(open, key = { "${it.market}:${it.symbol}" }) { h ->
                    HoldingRow(h) { nav.navigate(if (h.market == Market.TW) "stock/${h.symbol}" else "holding/${h.market}/${h.symbol}") }
                }
            }
            item(key = "brk") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("證券帳戶（${brokers.size}）", Modifier.weight(1f), fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { nav.navigate("account/0?type=BROKERAGE") }) { Text("＋ 新增") }
                }
                com.enwei.moneymanager.ui.components.LineDivider()
            }
            items(brokers, key = { "b${it.account.id}" }) { b ->
                val hs = byBroker[b.account.id].orEmpty().filter { it.shares > 0.0001 }
                val stock = hs.sumOf { lk.fx.convert(Money.toMinor(it.marketValue), it.currency) }
                val pnl = hs.sumOf { lk.fx.convert(Money.toMinor(it.unrealized), it.currency) }
                val cost = hs.sumOf { lk.fx.convert(Money.toMinor(it.netCost), it.currency) }
                val cashV = lk.fx.convert(b.balance, b.account.currency)
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.navigate("acct/${b.account.id}") }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(b.account.name, fontSize = AppText.body)
                            Text(
                                "持股 ${hs.size} 檔 ${Money.withSymbol(stock, lk.base)} · 現金 ${Money.withSymbol(cashV, lk.base)}",
                                fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (hs.isNotEmpty()) Text(
                                "預估損益 ${if (pnl > 0) "+" else ""}${Money.full(pnl)}（${formatPct(if (cost > 0) pnl * 100.0 / cost else 0.0)}）",
                                fontSize = AppText.caption, color = pnlColor(pnl.toDouble()),
                            )
                        }
                        Text(Money.withSymbol(stock + cashV, lk.base), fontSize = AppText.body)
                    }
                    com.enwei.moneymanager.ui.components.LineDivider()
                }
            }
            if (closed.isNotEmpty()) {
                item(key = "clh") { FoldHeader("已出清（${closed.size}）", closedFold.open, { closedFold.toggle() }) }
                if (closedFold.open) items(closed, key = { "c${it.market}:${it.symbol}" }) { h -> HoldingRow(h) { nav.navigate("holding/${h.market}/${h.symbol}") } }
            }
            if (holdings.isNotEmpty()) item(key = "real") {
                StockHeader("已實現損益＋股利", Money.withSymbol(realized, lk.base), pnlColor(realized.toDouble()))
            }
        } else {
            item(key = "wh") {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("僅追蹤報價，不計入資產", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (quoteTime.isNotEmpty()) Text(quoteTime, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { addWatch = true }) { Text("＋ 加入") }
                }
                com.enwei.moneymanager.ui.components.LineDivider()
            }
            if (settings.watchlist.isEmpty()) item(key = "wempty") {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("尚未加入觀察的股票", fontSize = AppText.body)
                    Spacer(Modifier.height(6.dp))
                    Text("可依名稱或代號加入，或在個股頁點選☆。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppText.secondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = { addWatch = true }) { Text("加入股票") }
                }
            }
            items(settings.watchlist, key = { "w$it" }) { sym ->
                val mk = com.enwei.moneymanager.core.marketOf(sym)
                val q = quotes.find { it.key == com.enwei.moneymanager.data.quoteKey(mk, sym) }
                val pct = q?.previousClose?.takeIf { it > 0 }?.let { (q.price - it) / it * 100 }
                // 台股顯示中文名稱，美股維持英文
                val nm = (if (mk == Market.TW) com.enwei.moneymanager.net.TwData.nameOf(sym) else null) ?: q?.name.orEmpty()
                val held = sym.uppercase() in heldSyms
                Column(Modifier.background(MaterialTheme.colorScheme.surface).clickable { nav.navigate("stock/$sym") }) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(nm.ifBlank { sym }, fontSize = AppText.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(sym + if (held) " · 已持有" else "", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(q?.let { formatNum(it.price) } ?: "—", fontSize = AppText.body)
                            if (pct != null) Text((if (pct >= 0) "▲" else "▼") + formatPct(pct), fontSize = AppText.secondary, color = pnlColor(pct))
                        }
                        Spacer(Modifier.width(8.dp))
                        // 決定買進：直接記一筆買進，之後就出現在「持股」
                        androidx.compose.material3.OutlinedButton(
                            onClick = { nav.navigate("trade/0?market=${mk.name}&symbol=$sym") },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            modifier = Modifier.height(32.dp),
                        ) { Text("買進", fontSize = AppText.secondary) }
                        IconButton(onClick = { repo.settings.update { it.copy(watchlist = it.watchlist - sym) } }) {
                            Icon(Icons.Filled.Close, "移出觀察", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    com.enwei.moneymanager.ui.components.LineDivider()
                }
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
    if (addWatch) {
        // 打名稱或代號搜尋，點結果加入
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { addWatch = false },
            title = { Text("加入觀察清單") },
            text = {
                com.enwei.moneymanager.ui.components.StockSearch({ s ->
                    repo.settings.update { it.copy(watchlist = (it.watchlist + s.code).distinct()) }
                    addWatch = false
                })
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { addWatch = false }) { Text("取消") } },
        )
    }
}

/** 可收合的區塊標題：點標題展開／收合，右側可放動作 */
@Composable
private fun FoldHeader(title: String, open: Boolean, toggle: () -> Unit, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).clickable(onClick = toggle).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = AppText.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            com.enwei.moneymanager.ui.components.FoldToggle(open)
        }
        action()
    }
    com.enwei.moneymanager.ui.components.LineDivider()
}

@Composable
private fun StockHeader(title: String, right: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = AppText.body)
        Text(right, color = color)
    }
    com.enwei.moneymanager.ui.components.LineDivider()
}

@Composable
private fun HoldingRow(h: HoldingView, onClick: () -> Unit) {
    val dayPct = if (h.price != null && h.previousClose != null && h.previousClose > 0) (h.price - h.previousClose) / h.previousClose * 100 else null
    Column(Modifier.background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${h.symbol}  ${h.name.orEmpty()}", fontSize = AppText.body, maxLines = 1)
                Text(
                    if (h.shares > 0.0001) "${formatShares(h.shares)} · 均價 ${formatNum(h.avgCost)}" else "已實現 ${Money.ofUnits(h.realizedTotal, h.currency)}",
                    fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.withSymbol(Money.toMinor(h.marketValue), h.currency), fontSize = AppText.body)
                if (h.price != null) Text(
                    "${formatNum(h.price)} " + (dayPct?.let { (if (it >= 0) "▲" else "▼") + formatPct(it) } ?: ""),
                    fontSize = AppText.secondary, color = pnlColor(dayPct ?: 0.0),
                )
                if (h.shares > 0.0001) Text("損益 ${Money.ofUnits(h.unrealized, h.currency)}（${formatPct(h.unrealizedPct)}）", fontSize = AppText.caption, color = pnlColor(h.unrealized))
            }
        }
        com.enwei.moneymanager.ui.components.LineDivider()
    }
}

@Composable
fun HoldingScreen(nav: NavController, market: String, symbol: String) {
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val h = holdings.find { it.market.name == market && it.symbol == symbol }
    val list = trades.filter { it.market.name == market && it.symbol.uppercase() == symbol }
    val lk = rememberLookups()
    var deleteAll by remember { mutableStateOf(false) }
    var deleteOne by remember { mutableStateOf<StockTradeEntity?>(null) }
    Scaffold(
        topBar = {
            BackTopBar("$symbol ${h?.name.orEmpty()}", { nav.popBackStack() }) {
                if (market == "TW") IconButton(onClick = { nav.navigate("stock/$symbol") }) { Icon(Icons.AutoMirrored.Filled.ShowChart, "K 線與資訊") }
                if (list.isNotEmpty()) IconButton(onClick = { deleteAll = true }) { Icon(Icons.Filled.Delete, "刪除整檔持股") }
            }
        },
        floatingActionButton = {
            com.enwei.moneymanager.ui.components.ScrollAwareFab { FloatingActionButton(onClick = { nav.navigate("trade/0?market=$market&symbol=$symbol") }) { Icon(Icons.Filled.Add, "新增交易") } }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (h != null) item {
                com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabelValue("持有股數", formatShares(h.shares))
                            LabelValue("平均成本", formatNum(h.avgCost))
                            LabelValue("現價", h.price?.let { formatNum(it) } ?: "—")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabelValue("市值", Money.ofUnits(h.marketValue, h.currency))
                            LabelValue("預估損益", Money.ofUnits(h.unrealized, h.currency), valueColor = pnlColor(h.unrealized))
                            LabelValue("已實現", Money.ofUnits(h.realizedTotal, h.currency), valueColor = pnlColor(h.realizedTotal))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabelValue("成本（扣股利）", Money.ofUnits(h.netCost, h.currency))
                            LabelValue("已領股利", Money.ofUnits(h.openDividends, h.currency))
                            LabelValue("預估賣出費用", Money.ofUnits(h.estSellCost, h.currency))
                        }
                    }
                }
            }
            if (h != null && h.lots.isNotEmpty()) {
                item { SectionTitle("庫存明細") }
                items(h.lots.sortedBy { it.epochDay }, key = { "lot${it.tradeId}" }) { lot ->
                    val buy = list.find { it.id == lot.tradeId }
                    val pnl = h.lotPnl[lot.tradeId]
                    val gross = lot.shares * lot.price
                    com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("成交日期 ${LocalDate.ofEpochDay(lot.epochDay)}", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                LabelValue("預估損益", pnl?.let { Money.ofUnits(it, h.currency) } ?: "—", valueColor = pnl?.let { pnlColor(it) } ?: Color.Unspecified)
                                LabelValue("現值", h.price?.let { Money.ofUnits(lot.shares * it, h.currency) } ?: "—")
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                LabelValue("庫存／股", formatNum(lot.shares, 0), Modifier.weight(1f))
                                LabelValue("單價", formatNum(lot.price), Modifier.weight(1f))
                                LabelValue("價金", Money.ofUnits(gross, h.currency), Modifier.weight(1f))
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                LabelValue("手續費＋稅", Money.ofUnits(lot.cost - gross, h.currency), Modifier.weight(1f))
                                LabelValue("除息金額", Money.ofUnits(lot.dividends, h.currency), Modifier.weight(1f))
                                LabelValue("原幣成本", Money.ofUnits(lot.netCost, h.currency), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.OutlinedButton(onClick = {
                                    nav.navigate("trade/0?market=$market&symbol=$symbol&lot=${lot.tradeId}&side=CASH_DIVIDEND&broker=${buy?.brokerAccountId ?: 0}")
                                }) { Text("＋ 除息") }
                                TextButton(onClick = { nav.navigate("trade/${lot.tradeId}") }) { Text("編輯這批") }
                            }
                        }
                    }
                }
            }
            item { SectionTitle("全部紀錄") }
            items(list.sortedWith(compareByDescending<StockTradeEntity> { it.date }.thenByDescending { it.id }), key = { it.id }) { t ->
                val lotOf = t.lotTradeId?.let { id -> list.find { it.id == id } }
                ListItem(
                    headlineContent = { Text("${t.side.label}  ${if (t.side == StockSide.CASH_DIVIDEND) Money.ofUnits(t.cashAmount, t.currency) else "${formatNum(t.shares, 0)} 股 @ ${formatNum(t.price)}"}") },
                    supportingContent = {
                        Text(
                            "${LocalDate.ofEpochDay(t.date)}" +
                                (if (t.side == StockSide.CASH_DIVIDEND) lotOf?.let { " · 屬於 ${LocalDate.ofEpochDay(it.date)} 那批" } ?: " · 依持股比例分配"
                                else " · 手續費 ${Money.ofUnits(t.fee, t.currency)} · 稅 ${Money.ofUnits(t.tax, t.currency)}") +
                                (if (t.source != TxnSource.MANUAL) " · ${t.source.label}" else ""),
                        )
                    },
                    trailingContent = {
                        IconButton(onClick = { deleteOne = t }) { Icon(Icons.Filled.Delete, "刪除", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                    modifier = Modifier.clickable { nav.navigate("trade/${t.id}") },
                )
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
    // 刪除前說明對交割帳戶的影響
    fun impact(ts: List<StockTradeEntity>): String {
        val byAcc = ts.filter { it.cashAccountId != null }.groupBy { it.cashAccountId }
            .map { (acc, l) -> (lk.account(acc)?.name ?: "?") to l.sumOf { Money.toMinor(StockCalc.cashFlow(it.toRecord())) } }
            .filter { it.second != 0L }
        return if (byAcc.isEmpty()) "持股與損益會同步更新。"
        else byAcc.joinToString("\n") { (n, f) -> "「$n」餘額${if (f < 0) "增加" else "減少"} ${Money.withSymbol(kotlin.math.abs(f))}" } + "\n持股與損益會同步更新。"
    }
    if (deleteAll) androidx.compose.material3.AlertDialog(
        onDismissRequest = { deleteAll = false },
        title = { Text("刪除 $symbol 的全部 ${list.size} 筆紀錄？") },
        text = { Text(impact(list)) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                MoneyApp.instance.appScope.launch { repo.deleteHolding(Market.valueOf(market), symbol) }
                deleteAll = false
                nav.popBackStack()
            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteAll = false }) { Text("取消") } },
    )
    deleteOne?.let { t ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteOne = null },
            title = { Text("刪除這筆股票交易？") },
            text = { Text(impact(listOf(t))) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    MoneyApp.instance.appScope.launch { repo.deleteTrade(t.id) }
                    deleteOne = null
                }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { deleteOne = null }) { Text("取消") } },
        )
    }
}

/** 新增／編輯股票交易：選證券帳戶 → 輸入代號（自動帶出名稱與現價）→ 張數或股數 → 成交價。 */
@Composable
fun TradeEditScreen(
    nav: NavController, id: Long, initMarket: String?, initSymbol: String?, initBroker: Long? = null,
    initLot: Long? = null, initSide: String? = null,
    /** 從「股票交易待確認」補上：帶入通知看得出的資料，儲存後移出待確認 */
    initPending: Long? = null,
) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var original by remember { mutableStateOf<StockTradeEntity?>(null) }
    var market by remember { mutableStateOf(initMarket?.let { Market.valueOf(it) } ?: Market.TW) }
    var symbol by remember { mutableStateOf(initSymbol.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var side by remember { mutableStateOf(initSide?.let { runCatching { StockSide.valueOf(it) }.getOrNull() } ?: StockSide.BUY) }
    var lotId by remember { mutableStateOf(initLot) }
    val allTrades by repo.trades.collectAsStateWithLifecycle(emptyList())
    var qty by remember { mutableStateOf("") }
    /** 分次成交的股數，例如「10+5」：券商每次成交各自計算手續費並捨去 */
    var fills by remember { mutableStateOf("") }
    var byLot by remember { mutableStateOf(true) }
    var price by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    /** 使用者手動改過手續費／交易稅；之後改了數量、價格、代號等會重新估算 */
    var feeEdited by remember { mutableStateOf(false) }
    /** 編輯既有交易時載入的輸入；輸入沒變就保留原本的手續費（可能是券商帳單的實際金額） */
    var loadedInputs by remember { mutableStateOf<List<Any?>?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var brokerId by remember { mutableStateOf<Long?>(initBroker) }
    var cashAccountId by remember { mutableStateOf<Long?>(null) }
    var cashEdited by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var lookup by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    val brokers = lk.accounts.filter { it.type == AccountType.BROKERAGE && !it.archived }
    val broker = lk.account(brokerId)

    LaunchedEffect(id, lk.accounts) {
        if (original != null || lk.accounts.isEmpty()) return@LaunchedEffect
        if (id == 0L) {
            repo.settings.value.pendingTrades.firstOrNull { it.id == initPending }?.let { p ->
                symbol = p.symbol; name = p.name.orEmpty()
                side = runCatching { StockSide.valueOf(p.side) }.getOrDefault(StockSide.BUY)
                date = LocalDate.ofEpochDay(p.date)
                p.shares?.let { qty = if (it % 1.0 == 0.0) "%.0f".format(it) else it.toString() }
                p.price?.let { price = it.toString() }
                p.brokerId?.let { brokerId = it }
                note = "由成交通知補上"
            }
            if (brokerId == null) brokerId = brokers.firstOrNull()?.id
            return@LaunchedEffect
        }
        val t = repo.getTrade(id) ?: return@LaunchedEffect
        original = t
        market = t.market; symbol = t.symbol; name = t.name.orEmpty(); side = t.side
        val lots = t.market == Market.TW && t.shares >= 1000 && t.shares % 1000 == 0.0
        byLot = lots
        qty = formatPlain(if (lots) t.shares / 1000 else t.shares)
        price = formatPlain(t.price); fee = formatPlain(t.fee); tax = formatPlain(t.tax)
        cash = formatPlain(t.cashAmount); date = LocalDate.ofEpochDay(t.date); note = t.note.orEmpty()
        brokerId = t.brokerAccountId; cashAccountId = t.cashAccountId; lotId = t.lotTradeId
        feeEdited = false; cashEdited = true
        loadedInputs = listOf(t.market, t.symbol, t.side, t.shares, formatPlain(t.price), "", t.brokerAccountId)
    }
    // 交割帳戶預設為證券戶連結的銀行
    LaunchedEffect(brokerId) { if (!cashEdited) cashAccountId = broker?.linkedAccountId ?: broker?.id }
    // 輸入代號後自動查詢名稱與現價
    LaunchedEffect(symbol, market) {
        val sym = symbol.trim()
        if (sym.length < (if (market == Market.TW) 4 else 1)) { lookup = null; return@LaunchedEffect }
        kotlinx.coroutines.delay(600)
        lookup = "查詢中…"
        val q = runCatching { com.enwei.moneymanager.net.QuoteService.fetch(listOf(market to sym)) }.getOrNull()?.firstOrNull()
        lookup = if (q == null) "查無報價（仍可手動輸入）" else {
            if (name.isBlank()) q.name?.let { name = it }
            if (price.isBlank() && original == null) price = formatPlain(q.price)
            "${q.name ?: sym} 現價 ${formatNum(q.price)}"
        }
    }
    val shares = (qty.replace(",", "").toDoubleOrNull() ?: 0.0) * (if (market == Market.TW && byLot) 1000 else 1)
    val feeSettings = settings.feeSettings.let { fs -> broker?.feeDiscount?.let { fs.copy(discount = it) } ?: fs }
    // 自動估算手續費與交易稅（可手動覆寫）
    val fillParts = fills.split('+', '＋', ',', ' ').mapNotNull { it.trim().toDoubleOrNull() }.filter { it > 0 }
    LaunchedEffect(market, symbol, side, shares, price, feeSettings, fills, date) {
        if (feeEdited) return@LaunchedEffect
        if (original != null && loadedInputs == listOf(market, symbol, side, shares, price, fills, brokerId)) return@LaunchedEffect
        val p = price.replace(",", "").toDoubleOrNull() ?: return@LaunchedEffect
        if (shares > 0 && (side == StockSide.BUY || side == StockSide.SELL)) {
            val parts = fillParts.takeIf { it.size > 1 && kotlin.math.abs(it.sum() - shares) < 1e-6 } ?: listOf(shares)
            // 同一天先買後賣同一檔（現股當沖）：交易稅減半
            val dayTrade = side == StockSide.SELL && allTrades.any {
                it.side == StockSide.BUY && it.market == market && it.symbol.equals(symbol.trim(), true) && it.date == date.toEpochDay() && it.id != original?.id
            }
            val est = parts.map { StockCalc.estimate(market, symbol, side, it, p, feeSettings, dayTrade) }
            fee = formatPlain(est.sumOf { it.first }); tax = formatPlain(est.sumOf { it.second })
        }
    }
    val isDividend = side == StockSide.CASH_DIVIDEND
    val amount = shares * (price.replace(",", "").toDoubleOrNull() ?: 0.0)
    val feeV = fee.toDoubleOrNull() ?: 0.0
    val taxV = tax.toDoubleOrNull() ?: 0.0
    val settle = when (side) { StockSide.BUY -> amount + feeV + taxV; StockSide.SELL -> amount - feeV - taxV; else -> 0.0 }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        BackTopBar(if (id == 0L) "新增股票交易" else "編輯股票交易", { nav.popBackStack() }) {
            if (original != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "刪除") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StockSide.entries.forEach { sd ->
                    val sel = sd == side
                    Box(
                        Modifier.weight(1f).height(38.dp)
                            .background(if (sel) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                            .border(if (sel) 1.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                            .clickable { if (side != sd) { side = sd; feeEdited = false } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            sd.label, fontSize = AppText.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 2.dp),
                        )
                    }
                }
            }
            DropdownLine("證券帳戶", broker?.name ?: if (brokers.isEmpty()) "尚未建立（帳戶 → 新增 → 群組選證券）" else "未指定",
                listOf<Pair<Long?, String>>(null to "未指定") + brokers.map { it.id to it.name }) { brokerId = it; feeEdited = false }
            DropdownLine("市場", if (market == Market.TW) "台股" else "美股", listOf(Market.TW to "台股", Market.US to "美股")) {
                market = it; if (it == Market.US) byLot = false; feeEdited = false
            }
            FormLine("代號") {
                FormInput(symbol, {
                    val v = it.uppercase().trim()
                    // 換了代號：名稱重新查詢、手續費重新估算
                    if (v != symbol) { symbol = v; name = ""; feeEdited = false }
                }, "")
            }
            lookup?.let { Text(it, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 108.dp, top = 4.dp)) }
            FormLine("名稱") { FormInput(name, { name = it }, "自動帶出，可修改") }
            if (isDividend) {
                FormLine("股利金額") { FormInput(cash, { cash = it }, "入帳的現金股利（券商的除息金額）", number = true) }
                // 指定給哪一批買進，該批的原幣成本與預估損益才會和券商「除息試算」完全一致
                val buys = allTrades.filter { it.side == StockSide.BUY && it.market == market && it.symbol.equals(symbol.trim(), true) }
                    .sortedBy { it.date }
                DropdownLine(
                    "屬於",
                    buys.find { it.id == lotId }?.let { "${LocalDate.ofEpochDay(it.date)} 買 ${formatNum(it.shares, 0)} 股 @ ${formatNum(it.price)}" } ?: "依持股比例分配",
                    listOf<Pair<Long?, String>>(null to "依持股比例分配") +
                        buys.map { it.id to "${LocalDate.ofEpochDay(it.date)} 買 ${formatNum(it.shares, 0)} 股 @ ${formatNum(it.price)}" },
                ) { lotId = it }
            } else {
                FormLine(if (side == StockSide.STOCK_DIVIDEND) "配股" else "數量", trailing = {
                    if (market == Market.TW) Row {
                        listOf(true to "張", false to "股").forEach { (lot, label) ->
                            Box(
                                Modifier.padding(start = 4.dp).border(1.dp, if (byLot == lot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable { if (byLot != lot) { byLot = lot; feeEdited = false } }.padding(horizontal = 12.dp, vertical = 6.dp),
                            ) { Text(label, color = if (byLot == lot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }) { FormInput(qty, { qty = it; feeEdited = false }, if (market == Market.TW && byLot) "1 張 = 1000 股" else "股數（零股、美股可含小數）", number = true) }
                if (market == Market.TW && byLot && shares > 0) Text("= ${formatNum(shares, 0)} 股", fontSize = AppText.secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 108.dp, top = 4.dp))
                // 賣出時提示保本紀律：多賺幾股可以賣，超過的算減碼
                if (side == StockSide.SELL && market == Market.TW && original == null &&
                    settings.investPlan.disciplineOn && settings.investPlan.guardOn
                ) {
                    val p = price.replace(",", "").toDoubleOrNull() ?: 0.0
                    val g by androidx.compose.runtime.produceState<com.enwei.moneymanager.core.GuardAdvice?>(null, symbol, p) {
                        value = if (symbol.isBlank() || p <= 0) null else repo.guardFor(symbol.trim(), p)
                    }
                    g?.let { a ->
                        Text(
                            "保本紀律：以這個價格多賺 ${a.excessShares.toLong()} 股可以賣" +
                                if (shares > a.excessShares && shares > 0) "；超過的 ${formatNum(shares - a.excessShares, 0)} 股算減碼，存檔後保本金額會依比例調降" else "",
                            fontSize = AppText.caption, color = if (shares > a.excessShares) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 108.dp, top = 4.dp, end = 16.dp),
                        )
                    }
                }
                if (side != StockSide.STOCK_DIVIDEND) {
                    FormLine("成交價") { FormInput(price, { price = it; feeEdited = false }, "", number = true) }
                    if (market == Market.TW) {
                        FormLine("分次成交") { FormInput(fills, { fills = it; feeEdited = false }, "選填，每次成交股數用 + 分隔") }
                        if (fillParts.size > 1 && kotlin.math.abs(fillParts.sum() - shares) >= 1e-6) Text(
                            "分次股數合計 ${formatNum(fillParts.sum(), 0)} 與數量 ${formatNum(shares, 0)} 不同，先以一次成交估算",
                            fontSize = AppText.caption, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = 108.dp, top = 4.dp),
                        )
                    }
                    FormLine("手續費") { FormInput(fee, { fee = it; feeEdited = true }, "", number = true) }
                    FormLine("交易稅") { FormInput(tax, { tax = it; feeEdited = true }, "", number = true) }
                    Text(
                        "成交金額 ${Money.ofUnits(amount, if (market == Market.TW) "TWD" else "USD")}　${if (side == StockSide.BUY) "應付" else "應收"} ${Money.ofUnits(settle, if (market == Market.TW) "TWD" else "USD")}\n" +
                            "手續費依 ${feeSettings.discount} 折估算，請以券商帳單為準",
                        fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            FormLine("日期") { DateField(date, { date = it }) }
            DropdownLine("交割帳戶", lk.account(cashAccountId)?.name ?: "不連動帳戶",
                listOf<Pair<Long?, String>>(null to "不連動帳戶") + lk.accounts.filter { !it.archived }.map { it.id to it.name }) {
                cashAccountId = it; cashEdited = true
            }
            FormLine("備註") { FormInput(note, { note = it }) }
            Button(
                onClick = {
                    val t = (original ?: StockTradeEntity(market = market, symbol = symbol, side = side, shares = 0.0, price = 0.0, date = 0)).copy(
                        market = market, symbol = symbol.uppercase(), name = name.ifBlank { null }, side = side,
                        shares = if (isDividend) 0.0 else shares,
                        price = if (isDividend || side == StockSide.STOCK_DIVIDEND) 0.0 else price.replace(",", "").toDoubleOrNull() ?: 0.0,
                        fee = feeV, tax = taxV,
                        cashAmount = if (isDividend) cash.replace(",", "").toDoubleOrNull() ?: 0.0 else 0.0,
                        currency = if (market == Market.TW) "TWD" else "USD",
                        date = date.toEpochDay(), cashAccountId = cashAccountId, brokerAccountId = brokerId, note = note.ifBlank { null },
                        lotTradeId = if (side == StockSide.CASH_DIVIDEND) lotId else null,
                    ).let { e ->
                        // 改過的自動除息不再被自動同步覆蓋
                        val k = e.externalKey
                        when {
                            k == null -> e
                            k.startsWith("DIV:") -> e.copy(externalKey = "DIVM:" + k.removePrefix("DIV:"), source = TxnSource.MANUAL)
                            k.startsWith("SDIV:") -> e.copy(externalKey = "SDIVM:" + k.removePrefix("SDIV:"), source = TxnSource.MANUAL)
                            else -> e
                        }
                    }
                    // 用 App 層級的 scope，離開畫面後仍會完成儲存與報價更新
                    MoneyApp.instance.appScope.launch {
                        // 新的賣出：先依賣出前的持股調整保本紀律
                        if (original == null && t.side == StockSide.SELL && t.market == Market.TW) runCatching { repo.adjustGuardAfterSell(t.symbol, t.shares, t.price) }
                        repo.saveTrade(t)
                        if (initPending != null) repo.settings.update { st -> st.copy(pendingTrades = st.pendingTrades.filter { it.id != initPending }) }
                        runCatching { SyncWorker.refreshDividends(repo, setOfNotNull(t.symbol.uppercase(), original?.symbol?.uppercase())) }
                        runCatching { SyncWorker.refreshQuotes(repo) }
                    }
                    nav.popBackStack()
                },
                enabled = symbol.isNotBlank() && (isDividend || shares > 0),
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(50.dp), shape = RoundedCornerShape(8.dp),
            ) { Text("儲存") }
            if (original != null) androidx.compose.material3.OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp), shape = RoundedCornerShape(8.dp),
            ) { Text("刪除這筆交易", color = MaterialTheme.colorScheme.error) }
        }
    }
    if (confirmDelete) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("刪除這筆股票交易？") },
        text = {
            val o = original
            if (o != null) {
                val flow = Money.toMinor(StockCalc.cashFlow(o.toRecord()))
                val cashName = lk.account(o.cashAccountId)?.name
                Text(
                    if (cashName != null && flow != 0L) "「$cashName」的餘額會${if (flow < 0) "增加" else "減少"} ${Money.withSymbol(kotlin.math.abs(flow), o.currency)}，持股與損益同步更新。"
                    else "持股與損益會同步更新。",
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                MoneyApp.instance.appScope.launch { repo.deleteTrade(id) }
                confirmDelete = false
                nav.popBackStack()
            }) { Text("刪除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("取消") } },
    )
}

@Composable
fun <T> DropdownLine(label: String, value: String, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FormLine(label, onClick = { open = true }) {
        Box {
            Text(value, fontSize = AppText.body)
            androidx.compose.material3.DropdownMenu(open, { open = false }) {
                options.forEach { (v, l) -> androidx.compose.material3.DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false }) }
            }
        }
    }
}

private fun formatPlain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
