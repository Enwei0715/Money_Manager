package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.Discipline
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.Technicals
import com.enwei.moneymanager.data.NewsItem
import com.enwei.moneymanager.data.rule
import com.enwei.moneymanager.data.styleOf
import com.enwei.moneymanager.net.DailyBars
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.net.InvestData
import com.enwei.moneymanager.ui.StockDown
import com.enwei.moneymanager.ui.StockUp
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/** 股票資訊：K 線（含均線、成交量）、基本數據、相對大盤強弱、公司業務、持股紀律與相關新聞。 */
@Composable
fun StockDetailScreen(nav: NavController, symbol: String) {
    // 英文字母開頭的代號是美股（沒有月營收；大盤比較改用 S&P 500）
    val market = com.enwei.moneymanager.core.marketOf(symbol)
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val holdings by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    var bars by remember { mutableStateOf<DailyBars?>(null) }
    var index by remember { mutableStateOf<DailyBars?>(null) }
    var loading by remember { mutableStateOf(true) }
    // K 線：日 K／60 分 K，可雙指縮放（看的 K 棒數）與左右拖曳（往回看）
    // K 線的設定（日／60 分、看幾根、均線、布林、副圖）會記住，換一檔股票或下次打開都一樣
    var hourly by remember { mutableStateOf(KlinePrefs.hourly) }
    var hourBars by remember { mutableStateOf<DailyBars?>(null) }
    var count by remember { mutableStateOf(KlinePrefs.count(hourly)) }
    var back by remember { mutableStateOf(0f) }
    var mas by remember { mutableStateOf(KlinePrefs.mas(hourly)) }
    var picked by remember(hourly) { mutableStateOf<Int?>(null) }
    var business by remember { mutableStateOf<String?>(null) }
    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    var divs by remember { mutableStateOf<List<com.enwei.moneymanager.core.DividendEvent>>(emptyList()) }
    var indicator by remember { mutableStateOf(KlinePrefs.indicator) }
    var showBB by remember { mutableStateOf(KlinePrefs.bb) }
    LaunchedEffect(hourly, count, mas, indicator, showBB) { KlinePrefs.save(hourly, count, mas, indicator, showBB) }
    val trades by repo.trades.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(symbol) {
        bars = InvestData.bars(symbol, "2y", tw = market == Market.TW)
        index = InvestData.bars(if (market == Market.TW) "^TWII" else "^GSPC", "1y")
        val st = MoneyApp.news.load()
        business = st.business[symbol]
        news = MoneyApp.news.about(st, symbol)
        loading = false
        divs = runCatching { com.enwei.moneymanager.net.QuoteService.fetchDividends(market, symbol) }.getOrDefault(emptyList())
    }
    val held = holdings.values.flatten().filter { it.market == market && it.symbol == symbol && it.shares > 0.0001 }
    // 名稱用股票清單的中文名稱（Yahoo 的是英文全名）
    val name = held.firstNotNullOfOrNull { it.name } ?: com.enwei.moneymanager.net.TwData.nameOf(symbol) ?: bars?.name
    // K 線上的參考線：均價、停利價、減碼價、提醒的目標價與停損價
    val levels = buildList {
        // 近 60 日的高點（壓力）與低點（支撐）
        bars?.let { bb ->
            if (bb.close.size >= 20) {
                add(Triple(bb.high.takeLast(60).max(), Color(0xFFAB47BC), "壓力"))
                add(Triple(bb.low.takeLast(60).min(), Color(0xFF26A69A), "支撐"))
            }
        }
        if (held.isNotEmpty()) {
            val sh = held.sumOf { it.shares }
            add(Triple(held.sumOf { it.avgCost * it.shares } / sh, Color(0xFF607D8B), "均價"))
        }
        settings.investPlan.guards[symbol]?.let { g ->
            g.takeProfit?.let { add(Triple(it, Color(0xFFE53935), "停利")) }
            g.reduceAt?.let { add(Triple(it, Color(0xFFFB8C00), "減碼")) }
        }
        settings.priceAlerts[symbol]?.let { a ->
            a.target?.let { add(Triple(it, Color(0xFF8E24AA), "目標")) }
            a.stop?.let { add(Triple(it, Color(0xFF43A047), "停損")) }
        }
    }

    val watched = symbol in settings.watchlist
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        BackTopBar("$symbol ${name.orEmpty()}", { nav.popBackStack() }) {
            // ☆ 加入／移出觀察清單（沒持有也能在股票頁看報價）
            androidx.compose.material3.IconButton(onClick = {
                repo.settings.update { it.copy(watchlist = if (watched) it.watchlist - symbol else (it.watchlist + symbol).distinct()) }
            }) {
                androidx.compose.material3.Icon(
                    if (watched) androidx.compose.material.icons.Icons.Filled.Star else androidx.compose.material.icons.Icons.Outlined.StarBorder,
                    if (watched) "移出觀察清單" else "加入觀察清單",
                    tint = if (watched) Color(0xFFFFA000) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            val b = bars
            if (!loading && b == null) Text("讀不到這檔股票的價格資料（代號可能不正確）", color = MaterialTheme.colorScheme.error)
            if (b != null) {
                val c = b.close
                val last = c.last()
                val prev = c.getOrNull(c.size - 2) ?: last
                val chg = last - prev
                val color = if (chg >= 0) StockUp else StockDown
                // 報價
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text("%.2f".format(last), fontSize = 28.sp, color = color, fontWeight = FontWeight.SemiBold)
                            Text("  ${if (chg >= 0) "▲" else "▼"} ${"%.2f".format(kotlin.math.abs(chg))}（${"%+.2f".format(chg / prev * 100)}%）",
                                fontSize = 14.sp, color = color, modifier = Modifier.padding(top = 10.dp))
                        }
                        val n = b.close.size - 1
                        val yearHigh = b.high.takeLast(250).max()
                        val yearLow = b.low.takeLast(250).min()
                        InfoGrid(
                            listOf(
                                "開盤" to "%.2f".format(b.open[n]), "最高" to "%.2f".format(b.high[n]), "最低" to "%.2f".format(b.low[n]),
                                "成交量" to (b.volume.getOrNull(n)?.let { "%,.0f 張".format(it / 1000) } ?: "-"),
                                "52 週高" to "%.2f".format(yearHigh), "52 週低" to "%.2f".format(yearLow),
                                "月線" to (Technicals.sma(c, 20)?.let { "%.2f".format(it) } ?: "-"),
                                "季線" to (Technicals.sma(c, 60)?.let { "%.2f".format(it) } ?: "-"),
                                "RSI" to (Technicals.rsi(c)?.let { "%.0f".format(it) } ?: "-"),
                            ),
                        )
                    }
                }
                // K 線
                LaunchedEffect(hourly, held.isNotEmpty(), settings.investPlan.disciplineOn, settings.investPlan.guardOn) {
                    if ((hourly || held.isNotEmpty() && settings.investPlan.disciplineOn && settings.investPlan.guardOn) && hourBars == null) {
                        hourBars = InvestData.bars(symbol, "3mo", tw = market == Market.TW, interval = "60m")
                    }
                }
                val kb = if (hourly) hourBars else b
                // 依紀律參數找出的買點、賣點（日 K）：長線用較寬的移動停損、分批停利門檻加倍；開啟保本＋移動停利時用它的回落 %
                val style = settings.styleOf(symbol)
                val plan = settings.investPlan
                val sigParams = com.enwei.moneymanager.core.TradeSignals.Params(
                    long = style == com.enwei.moneymanager.core.TradeStyle.LONG,
                    stopPct = plan.shortStopPct,
                    trailPct = if (plan.guardOn) plan.guardTrailPct else if (style == com.enwei.moneymanager.core.TradeStyle.LONG) plan.longTrailPct else plan.shortTrailPct,
                    takeProfitPct = plan.takeProfitPct,
                )
                val signals = remember(System.identityHashCode(b), sigParams) {
                    com.enwei.moneymanager.core.TradeSignals.scan(b.open, b.high, b.low, b.close, b.volume, sigParams)
                }
                var showSignals by remember { mutableStateOf(true) }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(!hourly, { hourly = false; count = KlinePrefs.count(false); back = 0f; mas = KlinePrefs.mas(false) }, label = { Text("日 K") })
                            FilterChip(hourly, { hourly = true; count = KlinePrefs.count(true); back = 0f; mas = KlinePrefs.mas(true) }, label = { Text("60 分 K") })
                            (if (hourly) listOf(25 to "5 天", 50 to "10 天", 100 to "1 個月") else listOf(60 to "3 個月", 120 to "6 個月", 250 to "1 年"))
                                .forEach { (n, l) -> FilterChip(count.toInt() == n && back < 1f, { count = n.toFloat(); back = 0f }, label = { Text(l) }) }
                        }
                        if (kb == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 40.dp))
                        if (kb != null) {
                            val total = kb.close.size
                            val n = count.toInt().coerceIn(minOf(10, total), total)
                            val from = (total - n - back.toInt()).coerceIn(0, total - n)
                            // 點一下 K 棒看當天的開高低收與量
                            val sel = picked?.takeIf { it in from until from + n }
                            if (sel != null) {
                                val dd = java.time.LocalDate.ofEpochDay(kb.days[sel])
                                val chg = if (sel > 0) (kb.close[sel] / kb.close[sel - 1] - 1) * 100 else null
                                Text(
                                    "${dd.monthValue}/${dd.dayOfMonth}　開 ${"%.2f".format(kb.open[sel])}　高 ${"%.2f".format(kb.high[sel])}　低 ${"%.2f".format(kb.low[sel])}　收 ${"%.2f".format(kb.close[sel])}" +
                                        (chg?.let { "（${"%+.2f".format(it)}%）" } ?: "") + (kb.volume.getOrNull(sel)?.let { "　量 ${"%,.0f".format(it / 1000)} 張" } ?: ""),
                                    fontSize = 12.sp, color = if ((chg ?: 0.0) >= 0) StockUp else StockDown, modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                            val visibleSignals = if (hourly || !showSignals) emptyList() else signals.filter { it.index in from until from + n }
                            sel?.let { j -> visibleSignals.firstOrNull { it.index == j } }?.let { sg ->
                                Text((if (sg.buy) "買點：" else "賣點：") + sg.reason, fontSize = 12.sp, color = if (sg.buy) StockUp else StockDown)
                            }
                            CandleChart(kb, from, n, showBB, mas.sorted(), signals = visibleSignals,
                                onZoom = { z -> count = (count / z).coerceIn(10f, total.toFloat()); back = back.coerceIn(0f, (total - count).coerceAtLeast(0f)) },
                                onPan = { bars -> back = (back + bars).coerceIn(0f, (total - count).coerceAtLeast(0f)) },
                                levels = levels.map { it.first to it.second }, selected = sel,
                                onTap = { f -> val j = from + (f * n).toInt().coerceIn(0, n - 1); picked = if (picked == j) null else j },
                            )
                            if (levels.isNotEmpty()) Text(
                                levels.joinToString("　") { "┄ ${it.third} ${"%.2f".format(it.first)}" }, fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            fun d(i: Int) = java.time.LocalDate.ofEpochDay(kb.days[i]).let { "${it.monthValue}/${it.dayOfMonth}" }
                            Text("${d(from)} – ${d(from + n - 1)}", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // 紀律買賣點：圈出的 K 棒與理由（依「資金紀律」的參數）
                            if (!hourly) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("紀律買賣點（${style.label}：停損 ${"%.0f".format(sigParams.stopPct)}%、移動停損 ${"%.0f".format(sigParams.trailPct)}%、停利 ${"%.0f".format(if (sigParams.long) sigParams.takeProfitPct * 2 else sigParams.takeProfitPct)}%）",
                                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    androidx.compose.material3.Switch(showSignals, { showSignals = it }, Modifier.scale(0.8f))
                                }
                                if (showSignals) {
                                    if (visibleSignals.isEmpty()) Text("這段期間沒有符合紀律的買賣點", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    visibleSignals.takeLast(6).forEach { sg ->
                                        Text("${if (sg.buy) "● 買" else "● 賣"} ${d(sg.index)}　${sg.reason}", fontSize = 12.sp,
                                            color = if (sg.buy) StockUp else StockDown,
                                            modifier = Modifier.clickable { picked = sg.index })
                                    }
                                    Text("參數在「助理 → 資金紀律」調整，也可以讓 AI 依你的投資原則帶入；僅供參考。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (back >= 1f) TextButton(onClick = { back = 0f }) { Text("回到最新") }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                (if (hourly) listOf(20, 50) else listOf(5, 10, 20, 60, 72, 100, 200)).forEach { len ->
                                    FilterChip(len in mas, { mas = if (len in mas) mas - len else mas + len },
                                        label = { Text("${len}${if (hourly) "" else "日"}", fontSize = 12.sp, color = maColor(len)) })
                                }
                                FilterChip(showBB, { showBB = !showBB }, label = { Text("布林", fontSize = 12.sp) })
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("成交量", "RSI", "MACD", "KD").forEachIndexed { i, l -> FilterChip(indicator == i, { indicator = i }, label = { Text(l, fontSize = 12.sp) }) }
                            }
                            IndicatorChart(kb, from, n, indicator)
                            IndicatorNote(kb, indicator)
                        }
                    }
                }
                // 強弱與階段
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("強弱", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        val stage = Technicals.stage(c)
                        Text("${stage.label}　${stage.hint}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val idx = index?.close
                        // 個股與大盤近 60 日走勢（以 60 日前為 0%）
                        RelativeChart(c, idx)
                        // 20 日、60 日漲跌：個股與大盤並排
                        com.enwei.moneymanager.ui.components.SignedBars(
                            "漲跌比較（%）",
                            listOf(20, 60).flatMap { d ->
                                listOfNotNull(
                                    Technicals.change(c, d)?.let { Triple("$d 日 個股", it, "%+.1f%%".format(it)) },
                                    idx?.let { Technicals.change(it, d) }?.let { Triple("$d 日 大盤", it, "%+.1f%%".format(it)) },
                                )
                            },
                        )
                    }
                }
                if (market == Market.TW) RevenueCard(symbol)
                ProjectionCard(b)
                if (held.isNotEmpty() && settings.investPlan.disciplineOn) Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val shares = held.sumOf { it.shares }
                        val avg = held.sumOf { it.avgCost * it.shares } / shares
                        val first = held.flatMap { it.lots }.minOfOrNull { it.epochDay }
                        val peak = first?.let { b.peakSince(it) } ?: last
                        val style = settings.styleOf(symbol)
                        val ad = Discipline.advise(style, avg, last, peak, settings.investPlan.rule())
                        Text("我的持股（${style.label}）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("${"%.0f".format(shares)} 股 · 損益 ${"%+.1f".format((last / avg - 1) * 100)}%", fontSize = 14.sp,
                            color = if (last >= avg) StockUp else StockDown)
                        // 價位圖：均價、現價、買進後最高與停利／減碼／提醒價的相對位置
                        PriceLadder(buildList {
                            add(Triple("均價", avg, Color(0xFF607D8B)))
                            add(Triple("現價", last, if (last >= avg) StockUp else StockDown))
                            add(Triple("買進後最高", peak, Color(0xFFFFA000)))
                            levels.filter { it.third != "均價" }.forEach { add(Triple(it.third, it.first, it.second)) }
                        })
                        if (!settings.investPlan.guardOn) Text("${ad.action}：${ad.note}", fontSize = 14.sp, color = if (ad.triggered) MaterialTheme.colorScheme.error else Color.Unspecified)
                        if (settings.investPlan.guardOn) {
                            // 保本＋移動停利（60 分 K 均線在「資金與紀律」頁一起看）
                            val gs = settings.investPlan.guards[symbol]
                            val principal = gs?.principal ?: com.enwei.moneymanager.data.autoPrincipal(trades.filter { it.market == market && it.symbol.equals(symbol, true) })
                            val hc = hourBars?.close
                            val g = com.enwei.moneymanager.core.ProfitGuard.advise(
                                com.enwei.moneymanager.core.GuardInput(shares, last, principal, gs?.peak, gs?.takeProfit, gs?.reduceAt,
                                    hc?.let { Technicals.sma(it, 20) }, hc?.let { Technicals.sma(it, 50) }),
                                com.enwei.moneymanager.core.GuardRule(settings.investPlan.guardTrailPct, settings.investPlan.baseShares),
                            )
                            Text("保本 ${"%,.0f".format(principal)} · 市值 ${"%,.0f".format(shares * last)}" +
                                (if (g.excessShares >= 1) " · 多賺 ${g.excessShares.toLong()} 股 · 停利價 ${"%.2f".format(g.takeProfit ?: 0.0)}" else ""), fontSize = 14.sp)
                            Text("${g.action.label}：${g.note}", fontSize = 14.sp, color = if (g.triggered) MaterialTheme.colorScheme.error else Color.Unspecified)
                            Text("日 K：" + com.enwei.moneymanager.core.ProfitGuard.dailyTrend(c).summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            // 除權息
            if (divs.isNotEmpty() && b != null) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("除權息", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val today = java.time.LocalDate.now().toEpochDay()
                    val yearCash = divs.filter { it.exEpochDay in (today - 365)..today }.sumOf { it.perShare }
                    val last = b.close.last()
                    Text(
                        "近一年現金股利 ${"%.2f".format(yearCash)} 元" + (if (last > 0 && yearCash > 0) "，殖利率約 ${"%.2f".format(yearCash / last * 100)}%" else ""),
                        fontSize = 14.sp,
                    )
                    divs.filter { it.exEpochDay > today }.forEach { e ->
                        Text("即將除權息：${java.time.LocalDate.ofEpochDay(e.exEpochDay)}（${e.exEpochDay - today} 天後）", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    // 歷年現金股利長條圖（舊到新）
                    com.enwei.moneymanager.ui.components.LabeledBars(
                        "歷年現金股利（元／股）",
                        divs.filter { it.perShare > 0 }.sortedByDescending { it.exEpochDay }.take(8).reversed().map { e ->
                            val dt = java.time.LocalDate.ofEpochDay(e.exEpochDay)
                            Triple("${dt.year}/${dt.monthValue}", (e.perShare * 10_000).toLong(), "%.2f".format(e.perShare))
                        },
                        MaterialTheme.colorScheme.primary,
                    )
                    // 明細：點開才看
                    var allDivs by remember { mutableStateOf(false) }
                    val divList = divs.sortedByDescending { it.exEpochDay }
                    divList.take(if (allDivs) 20 else 0).forEach { e ->
                        Row {
                            Text(java.time.LocalDate.ofEpochDay(e.exEpochDay).toString(), fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(listOfNotNull(
                                e.perShare.takeIf { it > 0 }?.let { "現金 ${"%.4f".format(it).trimEnd('0').trimEnd('.')} 元" },
                                e.stockPerShare.takeIf { it > 0 }?.let { "股票 ${"%.4f".format(it * 10).trimEnd('0').trimEnd('.')} 元" },
                            ).joinToString("、"), fontSize = 13.sp)
                        }
                    }
                    if (divList.isNotEmpty()) TextButton(onClick = { allDivs = !allDivs }) { Text(if (allDivs) "收起明細" else "除權息明細（${minOf(divList.size, 20)} 次）") }
                    val mine = trades.filter { it.market == market && it.symbol.equals(symbol, true) && it.side == com.enwei.moneymanager.core.StockSide.CASH_DIVIDEND }
                    if (mine.isNotEmpty()) Text("我已領現金股利 ${"%,.0f".format(mine.sumOf { it.cashAmount })} 元（${mine.map { it.date }.distinct().size} 次）", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            // 價格提醒
            if (b != null) PriceAlertCard(symbol, b.close.last(), held.isNotEmpty())
            // 公司業務
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("這家公司做什麼", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    var info by remember(symbol) { mutableStateOf(com.enwei.moneymanager.data.CompanyInfo.get(ctx, symbol)) }
                    var busy by remember { mutableStateOf(false) }
                    fun refresh() {
                        if (busy || !GroqAi.enabled) return
                        busy = true
                        scope.launch {
                            com.enwei.moneymanager.data.CompanyInfo.refresh(ctx, symbol, name.orEmpty(), news)?.let { info = it; business = it.text }
                            busy = false
                        }
                    }
                    // 超過 14 天自動重新整理（依最新的新聞與營收）
                    LaunchedEffect(symbol, loading) {
                        if (!loading && (info != null || business != null) && com.enwei.moneymanager.data.CompanyInfo.stale(info)) refresh()
                    }
                    val text = info?.text ?: business
                    text?.let { Text(it, fontSize = 14.sp) }
                    info?.recent?.takeIf { it.isNotBlank() }?.let { Text("近況：$it", fontSize = 13.sp) }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (info != null) Text(
                        "AI 依 ${info!!.basis} 整理 · " + java.text.SimpleDateFormat("M/d", java.util.Locale.TAIWAN).format(java.util.Date(info!!.at)),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (GroqAi.enabled) TextButton(onClick = { refresh() }, enabled = !busy) { Text(if (text == null) "請 AI 依新聞與營收說明" else "重新整理") }
                    else if (text == null) Text("開啟 AI 後可以查看", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 相關新聞
            if (news.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    var allNews by remember { mutableStateOf(false) }
                    Text("相關新聞（${news.size}）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    news.take(if (allNews) news.size else 3).forEach { NewsLine(it) }
                    if (news.size > 3) TextButton(onClick = { allNews = !allNews }) { Text(if (allNews) "收起" else "顯示全部 ${news.size} 則") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate("trade/0?market=${market.name}&symbol=$symbol") }) { Text("記一筆交易") }
                if (held.isNotEmpty()) OutlinedButton(onClick = { nav.navigate("holding/TW/$symbol") }) { Text("持股明細") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoGrid(items: List<Pair<String, String>>) {
    items.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { (k, v) ->
                Column(Modifier.weight(1f)) {
                    Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(v, fontSize = 14.sp)
                }
            }
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** 一則新聞：多空色點＋標題、來源與時間。 */
@Composable
fun NewsLine(n: NewsItem, num: Int? = null) {
    val c = when {
        n.sentiment > 0 -> StockUp
        n.sentiment < 0 -> StockDown
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    // 點新聞開啟原文；舊資料沒有連結時改用搜尋
    val link = n.url.ifBlank { "https://www.google.com/search?tbm=nws&q=" + java.net.URLEncoder.encode(n.title, "UTF-8") }
    Column(Modifier.fillMaxWidth().clickable { runCatching { uri.openUri(link) } }.padding(vertical = 4.dp)) {
        Text((num?.let { "[$it] " } ?: "") + (if (n.sentiment > 0) "▲ " else if (n.sentiment < 0) "▼ " else "• ") + n.title, fontSize = 13.sp, color = c)
        Text(
            listOfNotNull(
                n.source.takeIf { it.isNotBlank() },
                if (n.contentLevel == "title") "只有標題" else n.contentLevel,
                "合併 ${n.duplicateCount.coerceAtLeast(1)} 篇".takeIf { n.duplicateCount > 1 },
                n.theme.takeIf { it.isNotBlank() },
                java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(n.epochSec * 1000)),
            ).joinToString(" · ") + (n.note.takeIf { it.isNotBlank() }?.let { "　$it" } ?: ""),
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 均線顏色（日 K 與 60 分 K 共用）。 */
private fun maColor(len: Int): Color = when (len) {
    5 -> Color(0xFFFFA000)
    10 -> Color(0xFF42A5F5)
    20 -> Color(0xFF7E57C2)
    50 -> Color(0xFF26A69A)
    60 -> Color(0xFF26A69A)
    72 -> Color(0xFFEC407A)
    100 -> Color(0xFF8D6E63)
    else -> Color(0xFF546E7A)
}

/**
 * K 線：紅漲綠跌（台股慣例），疊選擇的均線，下方成交量。顯示 [from] 起的 [n] 根；
 * 雙指縮放改變根數（[onZoom] 傳縮放倍率），單指左右拖曳往回看（[onPan] 傳移動的根數，正數為往過去）。
 */
@Composable
private fun CandleChart(
    b: DailyBars, from: Int, n: Int, showBB: Boolean, mas: List<Int>, onZoom: (Float) -> Unit, onPan: (Float) -> Unit,
    levels: List<Pair<Double, Color>> = emptyList(), selected: Int? = null, onTap: (Float) -> Unit = {},
    signals: List<com.enwei.moneymanager.core.TradeSignals.Signal> = emptyList(),
) {
    val bb = remember(System.identityHashCode(b)) { Technicals.bollinger(b.close) }
    // 月線、季線（畫交叉標記用）
    val ma20 = remember(System.identityHashCode(b)) { b.close.indices.map { Technicals.sma(b.close, 20, it + 1) } }
    val ma60 = remember(System.identityHashCode(b)) { b.close.indices.map { Technicals.sma(b.close, 60, it + 1) } }
    val maSeries = remember(System.identityHashCode(b), mas) { mas.associateWith { len -> b.close.indices.map { i -> Technicals.sma(b.close, len, i + 1) } } }
    val up = StockUp
    val down = StockDown
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zoom by androidx.compose.runtime.rememberUpdatedState(onZoom)
    val pan by androidx.compose.runtime.rememberUpdatedState(onPan)
    val nNow by androidx.compose.runtime.rememberUpdatedState(n)
    val tap by androidx.compose.runtime.rememberUpdatedState(onTap)
    Canvas(
        Modifier.fillMaxWidth().height(260.dp).padding(top = 8.dp)
            // 兩指才算縮放，單指上下滑仍可捲動頁面
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val e = awaitPointerEvent()
                        if (e.changes.count { it.pressed } >= 2) {
                            val z = e.calculateZoom()
                            if (z != 1f) zoom(z)
                            e.changes.forEach { it.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                }
            }
            .pointerInput(Unit) { detectTapGestures { o -> tap(o.x / size.width) } }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, dx ->
                    change.consume()
                    pan(dx / size.width * nNow)
                }
            },
    ) {
        val to = from + n
        val priceH = size.height * 0.75f
        val volTop = size.height * 0.80f
        val hi = (from until to).maxOf { b.high[it] }
        val lo = (from until to).minOf { b.low[it] }
        val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        val cw = size.width / n
        fun y(v: Double) = ((hi - v) / range * priceH).toFloat()
        for (k in 0..3) drawLine(grid, Offset(0f, priceH * k / 3), Offset(size.width, priceH * k / 3), 1f)
        val maxVol = (from until to).maxOf { b.volume.getOrNull(it) ?: 0.0 }.takeIf { it > 0 } ?: 1.0
        for (i in 0 until n) {
            val j = from + i
            val o = b.open[j]; val c = b.close[j]
            val col = if (c >= o) up else down
            val cx = i * cw + cw / 2
            drawLine(col, Offset(cx, y(b.high[j])), Offset(cx, y(b.low[j])), 1.2f)
            val top = y(maxOf(o, c)); val bot = y(minOf(o, c))
            drawRect(col, Offset(i * cw + cw * 0.15f, top), Size(cw * 0.7f, maxOf(1f, bot - top)))
            val v = b.volume.getOrNull(j) ?: 0.0
            val vh = ((v / maxVol) * (size.height - volTop)).toFloat()
            drawRect(col.copy(alpha = 0.5f), Offset(i * cw + cw * 0.15f, size.height - vh), Size(cw * 0.7f, vh))
        }
        fun series(values: List<Double?>, col: Color, w: Float) {
            val p = Path()
            var started = false
            for (i in 0 until n) {
                val v = values[from + i] ?: continue
                val pt = Offset(i * cw + cw / 2, y(v).coerceIn(0f, priceH))
                if (!started) { p.moveTo(pt.x, pt.y); started = true } else p.lineTo(pt.x, pt.y)
            }
            drawPath(p, col, style = Stroke(width = w))
        }
        if (showBB) listOf(bb.first, bb.third).forEach { series(it, Color(0xFF90A4AE), 1.5f) }
        maSeries.forEach { (len, v) -> series(v, maColor(len), 2f) }
        // 均價、停利價、減碼價、提醒價：虛線（在畫面價格範圍內才畫）
        val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
        levels.forEach { (v, col) -> if (v in lo..hi) drawLine(col, Offset(0f, y(v)), Offset(size.width, y(v)), 2f, pathEffect = dash) }
        // 月線與季線交叉：▲ 黃金交叉（K 棒下方）、▼ 死亡交叉（K 棒上方）
        for (i in 1 until n) {
            val j = from + i
            val a0 = ma20[j - 1]; val b0 = ma60[j - 1]; val a1 = ma20[j]; val b1 = ma60[j]
            if (a0 == null || b0 == null || a1 == null || b1 == null) continue
            val cx = i * cw + cw / 2
            val s = minOf(cw * 0.9f, 14f).coerceAtLeast(7f)
            if (a0 <= b0 && a1 > b1) {
                val yy = (y(b.low[j]) + 6f).coerceAtMost(priceH - 2f)
                drawPath(Path().apply { moveTo(cx, yy); lineTo(cx - s / 2, yy + s); lineTo(cx + s / 2, yy + s); close() }, up)
            } else if (a0 >= b0 && a1 < b1) {
                val yy = (y(b.high[j]) - 6f).coerceAtLeast(2f)
                drawPath(Path().apply { moveTo(cx, yy); lineTo(cx - s / 2, yy - s); lineTo(cx + s / 2, yy - s); close() }, down)
            }
        }
        // 紀律買賣點：買點圈在 K 棒下方、賣點圈在上方
        signals.forEach { sg ->
            val i = sg.index - from
            if (i !in 0 until n) return@forEach
            val cx = i * cw + cw / 2
            val col = if (sg.buy) up else down
            val r = maxOf(cw * 0.9f, 9f).coerceAtMost(16f)
            val cy = if (sg.buy) (y(b.low[sg.index]) + r + 4f).coerceAtMost(priceH - r) else (y(b.high[sg.index]) - r - 4f).coerceAtLeast(r)
            drawCircle(col, radius = r, center = Offset(cx, cy), style = Stroke(2.5f))
            drawCircle(col, radius = r * 0.35f, center = Offset(cx, cy))
        }
        // 點選的 K 棒：直線標示
        selected?.takeIf { it in from until to }?.let { j ->
            val x = (j - from) * cw + cw / 2
            drawLine(grid.copy(alpha = 1f), Offset(x, 0f), Offset(x, size.height), 1.5f)
        }
    }
}

/** 價格提醒：目標價、停損價（盤中每 15 分鐘檢查，達到時通知）。 */
@Composable
private fun PriceAlertCard(symbol: String, price: Double, held: Boolean) {
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val a = settings.priceAlerts[symbol]
    var target by remember(a) { mutableStateOf(a?.target?.toString().orEmpty()) }
    var stop by remember(a) { mutableStateOf(a?.stop?.toString().orEmpty()) }
    fun save() = repo.settings.update { st ->
        val pa = com.enwei.moneymanager.data.PriceAlert(target.toDoubleOrNull()?.takeIf { it > 0 }, stop.toDoubleOrNull()?.takeIf { it > 0 })
        st.copy(priceAlerts = if (pa.target == null && pa.stop == null) st.priceAlerts - symbol else st.priceAlerts + (symbol to pa))
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("價格提醒", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.enwei.moneymanager.ui.components.NumberField(target, { target = it; save() }, "目標價", Modifier.weight(1f))
                com.enwei.moneymanager.ui.components.NumberField(stop, { stop = it; save() }, "停損價", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { target = "%.2f".format(price * 1.1); save() }) { Text("+10%") }
                OutlinedButton(onClick = { stop = "%.2f".format(price * 0.92); save() }) { Text("−8%") }
            }
        }
    }
}

/** K 線下方的指標圖：成交量、RSI（70／30）、MACD（DIF、DEA、柱狀體）、KD（80／20）。 */
@Composable
private fun IndicatorChart(b: DailyBars, from: Int, n: Int, kind: Int) {
    val up = StockUp
    val down = StockDown
    val grid = MaterialTheme.colorScheme.outlineVariant
    val c1 = Color(0xFF7E57C2)
    val c2 = Color(0xFFFFA000)
    val macd = remember(System.identityHashCode(b)) { Technicals.macd(b.close) }
    val rsi = remember(System.identityHashCode(b)) { Technicals.rsiSeries(b.close) }
    val kd = remember(System.identityHashCode(b)) { Technicals.kd(b.high, b.low, b.close) }
    Canvas(Modifier.fillMaxWidth().height(110.dp).padding(top = 6.dp)) {
        val to = from + n
        val cw = size.width / n
        fun line(values: List<Double?>, lo: Double, hi: Double, col: Color) {
            val p = Path(); var started = false
            for (i in 0 until n) {
                val v = values.getOrNull(from + i) ?: continue
                val y = ((hi - v) / (hi - lo) * size.height).toFloat().coerceIn(0f, size.height)
                val x = i * cw + cw / 2
                if (!started) { p.moveTo(x, y); started = true } else p.lineTo(x, y)
            }
            drawPath(p, col, style = Stroke(width = 2f))
        }
        fun hline(v: Double, lo: Double, hi: Double) {
            val y = ((hi - v) / (hi - lo) * size.height).toFloat()
            drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
        }
        when (kind) {
            0 -> {
                val maxVol = (from until to).maxOf { b.volume.getOrNull(it) ?: 0.0 }.takeIf { it > 0 } ?: 1.0
                for (i in 0 until n) {
                    val j = from + i
                    val v = b.volume.getOrNull(j) ?: 0.0
                    val h = (v / maxVol * size.height).toFloat()
                    drawRect((if (b.close[j] >= b.open[j]) up else down).copy(alpha = 0.6f), Offset(i * cw + cw * 0.15f, size.height - h), Size(cw * 0.7f, h))
                }
            }
            1 -> { hline(70.0, 0.0, 100.0); hline(30.0, 0.0, 100.0); line(rsi, 0.0, 100.0, c1) }
            2 -> {
                val vals = (from until to).flatMap { listOf(macd.dif[it], macd.signal[it], macd.hist[it]) }
                val m = vals.maxOf { kotlin.math.abs(it) }.takeIf { it > 0 } ?: 1.0
                hline(0.0, -m, m)
                for (i in 0 until n) {
                    val h = macd.hist[from + i]
                    val y0 = size.height / 2
                    val y1 = ((m - h) / (2 * m) * size.height).toFloat()
                    drawRect((if (h >= 0) up else down).copy(alpha = 0.6f), Offset(i * cw + cw * 0.2f, minOf(y0, y1)), Size(cw * 0.6f, kotlin.math.abs(y1 - y0)))
                }
                line(macd.dif, -m, m, c2); line(macd.signal, -m, m, c1)
            }
            else -> { hline(80.0, 0.0, 100.0); hline(20.0, 0.0, 100.0); line(kd.first, 0.0, 100.0, c2); line(kd.second, 0.0, 100.0, c1) }
        }
    }
}

/** 指標目前數值與簡單判讀。 */
@Composable
private fun IndicatorNote(b: DailyBars, kind: Int) {
    val txt = remember(System.identityHashCode(b), kind) { indicatorText(b, kind) }
    if (txt.isNotBlank()) Text(txt, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun indicatorText(b: DailyBars, kind: Int): String {
    return when (kind) {
        0 -> {
            val v = b.volume
            val v5 = if (v.size >= 5) v.takeLast(5).average() else null
            val v20 = if (v.size >= 20) v.takeLast(20).average() else null
            if (v5 != null && v20 != null && v20 > 0) "5 日均量 ${"%,.0f".format(v5 / 1000)} 張、20 日均量 ${"%,.0f".format(v20 / 1000)} 張" +
                if (v5 > v20 * 1.5) "（量增）" else if (v5 < v20 * 0.6) "（量縮）" else "" else ""
        }
        1 -> Technicals.rsi(b.close)?.let { r -> "RSI ${"%.0f".format(r)}" + if (r >= 70) "（過熱，追高小心）" else if (r <= 30) "（超賣，留意反彈）" else "" }.orEmpty()
        2 -> Technicals.macd(b.close).let { m ->
            val n = m.dif.size
            val cross = if (n >= 2 && m.hist[n - 2] < 0 && m.hist[n - 1] >= 0) "，剛出現黃金交叉" else if (n >= 2 && m.hist[n - 2] > 0 && m.hist[n - 1] <= 0) "，剛出現死亡交叉" else ""
            "DIF ${"%.2f".format(m.dif.last())}、DEA ${"%.2f".format(m.signal.last())}、柱 ${"%+.2f".format(m.hist.last())}$cross"
        }
        else -> Technicals.kd(b.high, b.low, b.close).let { (k, d) ->
            "K ${"%.0f".format(k.last())}、D ${"%.0f".format(d.last())}" + when {
                k.last() >= 80 -> "（高檔）"
                k.last() <= 20 -> "（低檔）"
                else -> ""
            } + if (k.size >= 2 && k[k.size - 2] < d[d.size - 2] && k.last() >= d.last()) "，K 向上穿越 D" else ""
        }
    }
}


/** K 線設定的記憶（存在 App 的偏好設定）。 */
private object KlinePrefs {
    private val p by lazy { MoneyApp.instance.getSharedPreferences("kline", android.content.Context.MODE_PRIVATE) }
    val hourly get() = p.getBoolean("hourly", false)
    val indicator get() = p.getInt("indicator", 0)
    val bb get() = p.getBoolean("bb", false)
    fun count(hourly: Boolean) = p.getFloat(if (hourly) "countH" else "countD", if (hourly) 50f else 120f)
    fun mas(hourly: Boolean): Set<Int> = p.getString(if (hourly) "masH" else "masD", null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()
        ?: if (hourly) setOf(20, 50) else setOf(5, 20, 72)
    fun save(hourly: Boolean, count: Float, mas: Set<Int>, indicator: Int, bb: Boolean) {
        p.edit().putBoolean("hourly", hourly).putFloat(if (hourly) "countH" else "countD", count)
            .putString(if (hourly) "masH" else "masD", mas.joinToString(",")).putInt("indicator", indicator).putBoolean("bb", bb).apply()
    }
}

/** 個股與大盤近 [days] 日走勢比較（以第一天為 0%）。 */
@Composable
private fun RelativeChart(stock: List<Double>, index: List<Double>?, days: Int = 60) {
    val s = stock.takeLast(days)
    if (s.size < 2 || s.first() <= 0) return
    val sp = s.map { (it / s.first() - 1) * 100 }
    val ip = index?.takeLast(days)?.takeIf { it.size >= 2 && it.first() > 0 }?.let { l -> l.map { (it / l.first() - 1) * 100 } }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val idxColor = MaterialTheme.colorScheme.onSurfaceVariant
    val sc = if (sp.last() >= 0) StockUp else StockDown
    Column {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp).padding(vertical = 4.dp)) {
            val all = sp + ip.orEmpty()
            val hi = maxOf(all.max(), 0.0); val lo = minOf(all.min(), 0.0)
            val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            fun y(v: Double) = ((hi - v) / span * size.height).toFloat()
            drawLine(grid, androidx.compose.ui.geometry.Offset(0f, y(0.0)), androidx.compose.ui.geometry.Offset(size.width, y(0.0)), 1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            fun line(v: List<Double>, color: Color, w: Float) {
                val step = size.width / (v.size - 1)
                for (i in 1 until v.size) drawLine(color, androidx.compose.ui.geometry.Offset(step * (i - 1), y(v[i - 1])), androidx.compose.ui.geometry.Offset(step * i, y(v[i])), w)
            }
            ip?.let { line(it, idxColor, 2f) }
            line(sp, sc, 3.5f)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(12.dp).height(3.dp).background(sc))
            Text(" 個股 ${"%+.1f".format(sp.last())}%　", fontSize = 11.sp)
            if (ip != null) {
                Box(Modifier.width(12.dp).height(3.dp).background(idxColor))
                Text(" 大盤 ${"%+.1f".format(ip.last())}%", fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("近 ${s.size} 個交易日", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 價位圖：把幾個價位畫在同一條軸上，看出現價離均價、高點、停利／停損多遠。 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PriceLadder(points: List<Triple<String, Double, Color>>) {
    val pts = points.filter { it.second > 0 }
    if (pts.size < 2) return
    val lo = pts.minOf { it.second } * 0.97
    val hi = pts.maxOf { it.second } * 1.03
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(22.dp)) {
            val cy = size.height / 2
            drawLine(track, androidx.compose.ui.geometry.Offset(0f, cy), androidx.compose.ui.geometry.Offset(size.width, cy), 6f)
            pts.forEach { (_, v, col) ->
                val x = ((v - lo) / (hi - lo) * size.width).toFloat()
                drawCircle(col, 7f, androidx.compose.ui.geometry.Offset(x, cy))
                drawLine(col, androidx.compose.ui.geometry.Offset(x, cy - 10f), androidx.compose.ui.geometry.Offset(x, cy + 10f), 2f)
            }
        }
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pts.sortedBy { it.second }.forEach { (label, v, col) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(col, androidx.compose.foundation.shape.CircleShape))
                    Text(" $label ${"%.2f".format(v)}", fontSize = 11.sp)
                }
            }
        }
    }
}


/** 月營收：近 13 個月營收長條（億元），顏色依年增率；附最新一個月的年增、月增。 */
@Composable
private fun RevenueCard(symbol: String) {
    val rev by produceState<List<com.enwei.moneymanager.net.TwData.MonthRevenue>?>(null, symbol) {
        value = runCatching { com.enwei.moneymanager.net.TwData.monthRevenue(symbol) }.getOrDefault(emptyList())
    }
    val list = rev ?: return
    if (list.isEmpty()) return
    val show = list.takeLast(13)
    val last = show.last()
    val max = show.maxOf { it.revenue }.takeIf { it > 0 } ?: 1.0
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("月營收", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${last.year}/${last.month}：${"%,.1f".format(last.revenue / 1e8)} 億" +
                    (last.yoy?.let { "　年增 ${"%+.1f".format(it)}%" } ?: "") + (last.mom?.let { "　月增 ${"%+.1f".format(it)}%" } ?: ""),
                fontSize = 13.sp, color = if ((last.yoy ?: 0.0) >= 0) StockUp else StockDown,
            )
            val track = MaterialTheme.colorScheme.onSurfaceVariant
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val w = size.width / show.size
                show.forEachIndexed { i, m ->
                    val h = (m.revenue / max * size.height * 0.92).toFloat()
                    val col = when { m.yoy == null -> track; m.yoy >= 0 -> StockUp; else -> StockDown }
                    drawRect(col.copy(alpha = if (i == show.lastIndex) 1f else 0.6f), Offset(w * i + w * 0.15f, size.height - h), Size(w * 0.7f, h))
                }
            }
            Row {
                Text("${show.first().year % 100}/${show.first().month}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("紅＝年增、綠＝年減", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("${last.year % 100}/${last.month}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 年增率走勢（近 13 個月）
            val yoys = show.mapNotNull { it.yoy }
            if (yoys.size >= 3) Text(
                "近 ${yoys.size} 個月年增率：平均 ${"%+.1f".format(yoys.average())}%，" +
                    (if (yoys.takeLast(3).average() > yoys.average()) "最近三個月高於平均（成長加速）" else "最近三個月低於平均（成長放緩）"),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 趨勢推估：用近 120 日收盤價的對數線性回歸算出趨勢，加上近期波動算出未來 20 個交易日的 1 個標準差區間。
 * 只是統計推估，不是預測或投資建議。
 */
/**
 * 趨勢推估：找出波段高低點畫壓力線與支撐線（通道、楔形、三角收斂…），往後延伸 20 個交易日；
 * 統計扇形（近期趨勢＋波動）淡淡地放在後面。線型太亂畫不出趨勢線時，只用扇形。
 */
@Composable
private fun ProjectionCard(b: DailyBars) {
    val close = b.close
    if (close.size < 40) return
    val trend = remember(System.identityHashCode(b)) { com.enwei.moneymanager.core.TrendLines.find(b.high, b.low, close) }
    val hist = close.takeLast(120)
    val ys = hist.map { kotlin.math.ln(it) }
    val nn = ys.size
    val xm = (nn - 1) / 2.0
    val ym = ys.average()
    val slope = ys.indices.sumOf { (it - xm) * (ys[it] - ym) } / ys.indices.sumOf { (it - xm) * (it - xm) }
    val rets = ys.zipWithNext { a, c -> c - a }.takeLast(60)
    val vol = kotlin.math.sqrt(rets.map { (it - rets.average()) * (it - rets.average()) }.average())
    val ahead = 20
    val last = close.last()
    val mid = (1..ahead).map { k -> last * kotlin.math.exp(slope * k) }
    val upB = (1..ahead).map { k -> last * kotlin.math.exp(slope * k + vol * kotlin.math.sqrt(k.toDouble())) }
    val loB = (1..ahead).map { k -> last * kotlin.math.exp(slope * k - vol * kotlin.math.sqrt(k.toDouble())) }
    val total = close.size
    val showFrom = (trend?.start?.minus(5) ?: (total - 60)).coerceIn(0, (total - 40).coerceAtLeast(0)).coerceAtLeast(total - 120)
    val endIdx = (total - 1 + ahead).toDouble()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("趨勢推估（未來 20 個交易日）", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (trend != null) {
                val col = when (trend.bias) { 1 -> StockUp; -1 -> StockDown; else -> MaterialTheme.colorScheme.onSurface }
                Text("${trend.pattern}：${trend.hint}" + (trend.breakout?.let { "；$it" } ?: ""), fontSize = 13.sp, color = col)
                Text(
                    "20 日後 壓力線約 ${"%.2f".format(trend.upper.at(endIdx))}、支撐線約 ${"%.2f".format(trend.lower.at(endIdx))}（目前 ${"%.2f".format(last)}）",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val trendPct = (kotlin.math.exp(slope * ahead) - 1) * 100
                Text("線型較亂，畫不出明確的趨勢線；依近 $nn 日趨勢約 ${"%+.1f".format(trendPct)}%，區間 ${"%.2f".format(loB.last())}～${"%.2f".format(upB.last())}",
                    fontSize = 13.sp, color = if (trendPct >= 0) StockUp else StockDown)
            }
            val up = StockUp; val down = StockDown
            val bandCol = MaterialTheme.colorScheme.primary.copy(alpha = if (trend != null) 0.08f else 0.18f)
            val midCol = MaterialTheme.colorScheme.primary
            val resCol = Color(0xFFAB47BC); val supCol = Color(0xFF26A69A)
            val pivotCol = MaterialTheme.colorScheme.onSurfaceVariant
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(170.dp)) {
                val n = total - showFrom
                val slots = n + ahead
                val w = size.width / slots
                val lines = trend?.let { t -> listOf(t.upper.at(showFrom.toDouble()), t.upper.at(endIdx), t.lower.at(showFrom.toDouble()), t.lower.at(endIdx)) }.orEmpty()
                val all = (showFrom until total).flatMap { listOf(b.high[it], b.low[it]) } + upB + loB + lines
                val hi = all.max(); val lo = all.min(); val r = (hi - lo).takeIf { it > 0 } ?: 1.0
                fun y(v: Double) = ((hi - v) / r * size.height).toFloat()
                fun x(i: Double) = ((i - showFrom) * w + w / 2).toFloat()
                // 統計扇形（後面）
                val band = Path().apply {
                    moveTo(x((total - 1).toDouble()), y(last))
                    upB.forEachIndexed { k, v -> lineTo(x((total + k).toDouble()), y(v)) }
                    for (k in loB.indices.reversed()) lineTo(x((total + k).toDouble()), y(loB[k]))
                    this.close()
                }
                drawPath(band, bandCol)
                // K 棒
                for (i in showFrom until total) {
                    val o = b.open[i]; val c = b.close[i]
                    val colr = if (c >= o) up else down
                    val cx = x(i.toDouble())
                    drawLine(colr, Offset(cx, y(b.high[i])), Offset(cx, y(b.low[i])), 1f)
                    val top = y(maxOf(o, c)); val bot = y(minOf(o, c))
                    drawRect(colr, Offset(cx - w * 0.35f, top), Size(w * 0.7f, maxOf(1f, bot - top)))
                }
                val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                if (trend != null) {
                    // 壓力線、支撐線：實線畫到今天，往後延伸為虛線
                    listOf(trend.upper to resCol, trend.lower to supCol).forEach { (ln, colr) ->
                        val s0 = maxOf(trend.start, showFrom).toDouble(); val today = (total - 1).toDouble()
                        drawLine(colr, Offset(x(s0), y(ln.at(s0))), Offset(x(today), y(ln.at(today))), 3f)
                        drawLine(colr, Offset(x(today), y(ln.at(today))), Offset(x(endIdx), y(ln.at(endIdx))), 3f, pathEffect = dash)
                    }
                    // 波段高低點
                    trend.pivots.filter { it.index >= showFrom }.forEach { p ->
                        drawCircle(pivotCol, radius = 5f, center = Offset(x(p.index.toDouble()), y(p.price)), style = Stroke(2f))
                    }
                    // 型態的方向箭頭
                    if (trend.bias != 0) {
                        val sx = x((total - 1).toDouble()); val sy = y(last)
                        val target = if (trend.bias > 0) trend.upper.at(endIdx) else trend.lower.at(endIdx)
                        val ex = x(endIdx); val ey = y(target)
                        val arrowCol = if (trend.bias > 0) up else down
                        drawLine(arrowCol, Offset(sx, sy), Offset(ex, ey), 4f)
                        val ang = kotlin.math.atan2((ey - sy).toDouble(), (ex - sx).toDouble())
                        val len = 16.0
                        listOf(2.6, -2.6).forEach { d ->
                            drawLine(arrowCol, Offset(ex, ey), Offset((ex - len * kotlin.math.cos(ang + d / 6)).toFloat(), (ey - len * kotlin.math.sin(ang + d / 6)).toFloat()), 4f)
                        }
                    }
                } else {
                    var px = x((total - 1).toDouble()); var py = y(last)
                    mid.forEachIndexed { k, v -> val nx = x((total + k).toDouble()); val ny = y(v); drawLine(midCol, Offset(px, py), Offset(nx, ny), 2.5f, pathEffect = dash); px = nx; py = ny }
                }
            }
            if (trend != null) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("━ 壓力線", fontSize = 11.sp, color = resCol)
                Text("━ 支撐線", fontSize = 11.sp, color = supCol)
                Text("○ 波段高低點", fontSize = 11.sp, color = pivotCol)
                Text("▒ 統計區間", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text("依波段高低點畫線與統計波動推估，不是預測或投資建議。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
