package com.enwei.moneymanager.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.data.HoldingView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.StockCalc
import com.enwei.moneymanager.core.StockSide
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.StockTradeEntity
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
    val lk = rememberLookups()
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val open = holdings.filter { it.shares > 0.0001 }
    val closed = holdings.filter { it.shares <= 0.0001 }

    // 畫面開著時每 30 秒更新一次報價；手動按更新時 refreshTick 會變動
    LaunchedEffect(refreshTick, holdings.isNotEmpty()) {
        if (holdings.isEmpty()) return@LaunchedEffect
        var first = true
        while (true) {
            val r = runCatching { SyncWorker.refreshQuotes(repo) }
            if (first && refreshTick > 0) onMessage(if (r.isSuccess) "股價已更新" else "更新失敗：${r.exceptionOrNull()?.message}")
            first = false
            kotlinx.coroutines.delay(30_000)
        }
    }

    val totalValue = open.sumOf { lk.fx.convert(Money.toMinor(it.marketValue), it.currency) }
    val totalCost = open.sumOf { lk.fx.convert(Money.toMinor(it.cost), it.currency) }
    val dayChange = open.sumOf { lk.fx.convert(Money.toMinor(it.dayChange), it.currency) }
    val realized = holdings.sumOf { lk.fx.convert(Money.toMinor(it.realizedPnl + it.dividends), it.currency) }
    val unreal = totalValue - totalCost

    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        item {
            SummaryBarCustom(
                listOf(
                    "市值" to (Money.full(totalValue) to MaterialTheme.colorScheme.onSurface),
                    "未實現損益" to (Money.full(unreal) to pnlColor(unreal.toDouble())),
                    "今日" to (Money.full(dayChange) to pnlColor(dayChange.toDouble())),
                ),
            )
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    if (totalCost > 0) "報酬率 ${formatPct(Money.toDouble(unreal) / Money.toDouble(totalCost) * 100)}" else "",
                    color = pnlColor(unreal.toDouble()), fontSize = 13.sp, modifier = Modifier.weight(1f),
                )
                Text(
                    if (settings.lastQuoteUpdate > 0) "報價 " + SimpleDateFormat("HH:mm:ss", Locale.TAIWAN).format(Date(settings.lastQuoteUpdate)) + "（每 30 秒更新）" else "",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (open.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("尚無持股", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 17.sp)
                Spacer(Modifier.height(8.dp))
                Text("按右上角＋輸入股票代號與買進價格；\n或在「通知記帳」啟用券商 App，成交回報會自動記錄。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { nav.navigate("trade/0") }) { Text("新增股票") }
            }
        } else {
            item {
                StockHeader("持股（${open.size}）", Money.withSymbol(totalValue, lk.base))
            }
            items(open, key = { "${it.market}:${it.symbol}" }) { h -> HoldingRow(h) { nav.navigate("holding/${h.market}/${h.symbol}") } }
        }
        if (closed.isNotEmpty()) {
            item { StockHeader("已出清", "") }
            items(closed, key = { "c${it.market}:${it.symbol}" }) { h -> HoldingRow(h) { nav.navigate("holding/${h.market}/${h.symbol}") } }
        }
        if (holdings.isNotEmpty()) item {
            StockHeader("已實現損益＋股利", Money.withSymbol(realized, lk.base), pnlColor(realized.toDouble()))
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun StockHeader(title: String, right: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
        Text(right, color = color)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HoldingRow(h: HoldingView, onClick: () -> Unit) {
    val dayPct = if (h.price != null && h.previousClose != null && h.previousClose > 0) (h.price - h.previousClose) / h.previousClose * 100 else null
    Column(Modifier.background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${h.symbol}  ${h.name.orEmpty()}", fontSize = 17.sp, maxLines = 1)
                Text(
                    if (h.shares > 0.0001) "${formatNum(h.shares, 0)} 股 · 均價 ${formatNum(h.avgCost)}" else "已實現 ${formatNum(h.realizedPnl + h.dividends, 0)}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.withSymbol(Money.toMinor(h.marketValue), h.currency), fontSize = 16.sp)
                if (h.price != null) Text(
                    "${formatNum(h.price)} " + (dayPct?.let { (if (it >= 0) "▲" else "▼") + formatPct(it) } ?: ""),
                    fontSize = 13.sp, color = pnlColor(dayPct ?: 0.0),
                )
                if (h.shares > 0.0001) Text("損益 ${formatNum(h.unrealized, 0)} (${formatPct(h.unrealizedPct)})", fontSize = 12.sp, color = pnlColor(h.unrealized))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun HoldingScreen(nav: NavController, market: String, symbol: String) {
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    val holdings by repo.holdings.collectAsStateWithLifecycle(emptyList())
    val h = holdings.find { it.market.name == market && it.symbol == symbol }
    val list = trades.filter { it.market.name == market && it.symbol.uppercase() == symbol }
    Scaffold(
        topBar = { BackTopBar("$symbol ${h?.name.orEmpty()}", { nav.popBackStack() }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("trade/0?market=$market&symbol=$symbol") }) { Icon(Icons.Filled.Add, "新增交易") }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad)) {
            if (h != null) item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabelValue("持有股數", formatNum(h.shares, 0))
                            LabelValue("平均成本", formatNum(h.avgCost))
                            LabelValue("現價", h.price?.let { formatNum(it) } ?: "—")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            LabelValue("市值", formatNum(h.marketValue, 0))
                            LabelValue("未實現", formatNum(h.unrealized, 0), valueColor = pnlColor(h.unrealized))
                            LabelValue("已實現＋股利", formatNum(h.realizedPnl + h.dividends, 0), valueColor = pnlColor(h.realizedPnl + h.dividends))
                        }
                    }
                }
            }
            items(list, key = { it.id }) { t ->
                ListItem(
                    headlineContent = { Text("${t.side.label}  ${if (t.side == StockSide.CASH_DIVIDEND) formatNum(t.cashAmount) else "${formatNum(t.shares, 0)} 股 @ ${formatNum(t.price)}"}") },
                    supportingContent = {
                        Text("${LocalDate.ofEpochDay(t.date)} · 手續費 ${formatNum(t.fee, 0)} · 稅 ${formatNum(t.tax, 0)}" +
                            if (t.source != TxnSource.MANUAL) " · ${t.source.label}" else "")
                    },
                    modifier = Modifier.clickable { nav.navigate("trade/${t.id}") },
                )
            }
        }
    }
}

@Composable
fun TradeEditScreen(nav: NavController, id: Long, initMarket: String?, initSymbol: String?) {
    val lk = rememberLookups()
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    var original by remember { mutableStateOf<StockTradeEntity?>(null) }
    var market by remember { mutableStateOf(initMarket?.let { Market.valueOf(it) } ?: Market.TW) }
    var symbol by remember { mutableStateOf(initSymbol.orEmpty()) }
    var name by remember { mutableStateOf("") }
    var side by remember { mutableStateOf(StockSide.BUY) }
    var shares by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var fee by remember { mutableStateOf("") }
    var tax by remember { mutableStateOf("") }
    var cash by remember { mutableStateOf("") }
    var feeEdited by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var cashAccountId by remember { mutableStateOf<Long?>(null) }
    var note by remember { mutableStateOf("") }

    LaunchedEffect(id) {
        if (id == 0L) {
            cashAccountId = lk.accounts.firstOrNull { it.type == AccountType.BROKERAGE }?.id
            return@LaunchedEffect
        }
        val t = repo.getTrade(id) ?: return@LaunchedEffect
        original = t
        market = t.market; symbol = t.symbol; name = t.name.orEmpty(); side = t.side
        shares = formatPlain(t.shares); price = formatPlain(t.price); fee = formatPlain(t.fee); tax = formatPlain(t.tax)
        cash = formatPlain(t.cashAmount); date = LocalDate.ofEpochDay(t.date); cashAccountId = t.cashAccountId; note = t.note.orEmpty()
        feeEdited = true
    }
    // 自動估算手續費與交易稅（可手動覆寫）
    LaunchedEffect(market, symbol, side, shares, price, settings.feeSettings) {
        if (feeEdited) return@LaunchedEffect
        val s = shares.replace(",", "").toDoubleOrNull() ?: return@LaunchedEffect
        val p = price.replace(",", "").toDoubleOrNull() ?: return@LaunchedEffect
        if (side == StockSide.BUY || side == StockSide.SELL) {
            val (f, t) = StockCalc.estimate(market, symbol, side, s, p, settings.feeSettings)
            fee = formatPlain(f); tax = formatPlain(t)
        }
    }
    val isDividend = side == StockSide.CASH_DIVIDEND
    val total = (shares.replace(",", "").toDoubleOrNull() ?: 0.0) * (price.replace(",", "").toDoubleOrNull() ?: 0.0)

    Scaffold(topBar = {
        BackTopBar(if (id == 0L) "新增股票交易" else "編輯股票交易", { nav.popBackStack() }) {
            if (original != null) IconButton(onClick = { scope.launch { repo.deleteTrade(id); nav.popBackStack() } }) { Icon(Icons.Filled.Delete, "刪除") }
        }
    }) { pad ->
        Column(
            Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Market.entries.forEachIndexed { i, m ->
                    SegmentedButton(market == m, { market = m }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(if (m == Market.TW) "台股" else "美股") }
                }
            }
            Row {
                OutlinedTextField(symbol, { symbol = it.uppercase().trim() }, label = { Text("代號") }, singleLine = true, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("名稱（選填）") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            DropdownField("類型", StockSide.entries, side, { it.label }, { side = it })
            if (isDividend) {
                NumberField(cash, { cash = it }, "股利金額")
            } else {
                Row {
                    NumberField(shares, { shares = it }, if (side == StockSide.STOCK_DIVIDEND) "配發股數" else "股數（1 張 = 1000 股）", Modifier.weight(1f))
                    if (side != StockSide.STOCK_DIVIDEND) {
                        Spacer(Modifier.width(8.dp))
                        NumberField(price, { price = it }, "成交價", Modifier.weight(1f))
                    }
                }
                if (side != StockSide.STOCK_DIVIDEND) {
                    Row {
                        NumberField(fee, { fee = it; feeEdited = true }, "手續費", Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        NumberField(tax, { tax = it; feeEdited = true }, "交易稅", Modifier.weight(1f))
                    }
                    Text("成交金額 ${formatNum(total)}；手續費依設定的折扣（${settings.feeDiscount}）自動估算", style = MaterialTheme.typography.bodySmall)
                }
            }
            DateField(date, { date = it })
            DropdownField(
                "交割帳戶（金額會進出此帳戶）", lk.accounts.filter { !it.archived }, lk.account(cashAccountId), { "${it.type.icon} ${it.name}" },
                { cashAccountId = it.id }, noneLabel = "不連動帳戶", onNone = { cashAccountId = null },
            )
            OutlinedTextField(note, { note = it }, label = { Text("備註") }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    val t = (original ?: StockTradeEntity(market = market, symbol = symbol, side = side, shares = 0.0, price = 0.0, date = 0)).copy(
                        market = market, symbol = symbol.uppercase(), name = name.ifBlank { null }, side = side,
                        shares = if (isDividend) 0.0 else shares.replace(",", "").toDoubleOrNull() ?: 0.0,
                        price = if (isDividend || side == StockSide.STOCK_DIVIDEND) 0.0 else price.replace(",", "").toDoubleOrNull() ?: 0.0,
                        fee = fee.toDoubleOrNull() ?: 0.0, tax = tax.toDoubleOrNull() ?: 0.0,
                        cashAmount = if (isDividend) cash.replace(",", "").toDoubleOrNull() ?: 0.0 else 0.0,
                        currency = if (market == Market.TW) "TWD" else "USD",
                        date = date.toEpochDay(), cashAccountId = cashAccountId, note = note.ifBlank { null },
                    )
                    // 用 App 層級的 scope，離開畫面後仍會完成儲存與報價更新
                    MoneyApp.instance.appScope.launch {
                        repo.saveTrade(t)
                        runCatching { SyncWorker.refreshQuotes(repo) }
                    }
                    nav.popBackStack()
                },
                enabled = symbol.isNotBlank(), modifier = Modifier.fillMaxWidth(),
            ) { Text("儲存") }
        }
    }
}

private fun formatPlain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
