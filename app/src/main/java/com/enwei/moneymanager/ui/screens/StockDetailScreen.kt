package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    val holdings by repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    var bars by remember { mutableStateOf<DailyBars?>(null) }
    var index by remember { mutableStateOf<DailyBars?>(null) }
    var loading by remember { mutableStateOf(true) }
    var span by remember { mutableStateOf(120) }
    var business by remember { mutableStateOf<String?>(null) }
    var news by remember { mutableStateOf<List<NewsItem>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(symbol) {
        bars = InvestData.bars(symbol, "2y")
        index = InvestData.bars("^TWII", "1y")
        val st = MoneyApp.news.load()
        business = st.business[symbol]
        news = MoneyApp.news.about(st, symbol)
        loading = false
    }
    val held = holdings.values.flatten().filter { it.market == Market.TW && it.symbol == symbol && it.shares > 0.0001 }
    val name = held.firstNotNullOfOrNull { it.name } ?: bars?.name

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("$symbol ${name.orEmpty()}", { nav.popBackStack() }) }) { pad ->
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
                                fontSize = 15.sp, color = color, modifier = Modifier.padding(top = 10.dp))
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
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(60 to "3 個月", 120 to "6 個月", 250 to "1 年").forEach { (n, l) -> FilterChip(span == n, { span = n }, label = { Text(l) }) }
                        }
                        CandleChart(b, span)
                        Text("— 5 日  — 20 日（月線）  — 60 日（季線）", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // 強弱與階段
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("強弱", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        val stage = Technicals.stage(c)
                        Text("走勢：${stage.label}（${stage.hint}）", fontSize = 14.sp)
                        val idx = index?.close
                        listOf(20, 60).forEach { d ->
                            val s = Technicals.change(c, d)
                            val i = idx?.let { Technicals.change(it, d) }
                            if (s != null) Text(
                                "$d 日漲跌 ${"%+.1f".format(s)}%" + (i?.let { "，大盤 ${"%+.1f".format(it)}% → " + if (s - it >= 3) "強於大盤" else if (s - it <= -3) "弱於大盤" else "與大盤相當" } ?: ""),
                                fontSize = 14.sp, color = if (i != null && s - i >= 3) StockUp else if (i != null && s - i <= -3) StockDown else Color.Unspecified,
                            )
                        }
                    }
                }
                // 持股紀律
                if (held.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val shares = held.sumOf { it.shares }
                        val avg = held.sumOf { it.avgCost * it.shares } / shares
                        val first = held.flatMap { it.lots }.minOfOrNull { it.epochDay }
                        val peak = first?.let { b.peakSince(it) } ?: last
                        val style = settings.styleOf(symbol)
                        val ad = Discipline.advise(style, avg, last, peak, settings.investPlan.rule())
                        Text("我的持股（${style.label}）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("${"%.0f".format(shares)} 股 · 均價 ${"%.2f".format(avg)} · 損益 ${"%+.1f".format((last / avg - 1) * 100)}% · 買進後最高 ${"%.2f".format(peak)}", fontSize = 14.sp)
                        Text("${ad.action}：${ad.note}", fontSize = 14.sp, color = if (ad.triggered) MaterialTheme.colorScheme.error else Color.Unspecified)
                    }
                }
            }
            // 公司業務
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("這家公司做什麼", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    business?.let { Text(it, fontSize = 14.sp) }
                        ?: if (GroqAi.enabled) OutlinedButton(onClick = {
                            scope.launch {
                                val d = GroqAi.describeCompany(symbol, name.orEmpty())
                                if (!d.isNullOrBlank()) {
                                    business = d
                                    MoneyApp.news.update { it.copy(business = it.business + (symbol to d)) }
                                }
                            }
                        }) { Text("請 AI 說明") } else Text("開啟 AI 後可以查看", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 相關新聞
            if (news.isNotEmpty()) Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("相關新聞（資料集）", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    news.forEach { NewsLine(it) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.navigate("trade/0?market=TW&symbol=$symbol") }) { Text("記一筆交易") }
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
fun NewsLine(n: NewsItem) {
    val c = when {
        n.sentiment > 0 -> StockUp
        n.sentiment < 0 -> StockDown
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(Modifier.padding(vertical = 2.dp)) {
        Text((if (n.sentiment > 0) "▲ " else if (n.sentiment < 0) "▼ " else "• ") + n.title, fontSize = 13.sp, color = c)
        Text(
            listOfNotNull(
                n.source.takeIf { it.isNotBlank() },
                n.theme.takeIf { it.isNotBlank() },
                java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(n.epochSec * 1000)),
            ).joinToString(" · ") + (n.note.takeIf { it.isNotBlank() }?.let { "　$it" } ?: ""),
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 日 K 線：紅漲綠跌（台股慣例），疊 5／20／60 日均線，下方成交量。 */
@Composable
private fun CandleChart(b: DailyBars, span: Int) {
    val up = StockUp
    val down = StockDown
    val grid = MaterialTheme.colorScheme.outlineVariant
    val ma = listOf(5 to Color(0xFFFFA000), 20 to Color(0xFF7E57C2), 60 to Color(0xFF26A69A))
    Canvas(Modifier.fillMaxWidth().height(260.dp).padding(top = 8.dp)) {
        val n = minOf(span, b.close.size)
        val from = b.close.size - n
        val priceH = size.height * 0.75f
        val volTop = size.height * 0.80f
        val hi = (from until b.close.size).maxOf { b.high[it] }
        val lo = (from until b.close.size).minOf { b.low[it] }
        val range = (hi - lo).takeIf { it > 0 } ?: 1.0
        val cw = size.width / n
        fun y(v: Double) = ((hi - v) / range * priceH).toFloat()
        for (k in 0..3) drawLine(grid, Offset(0f, priceH * k / 3), Offset(size.width, priceH * k / 3), 1f)
        val maxVol = (from until b.close.size).maxOf { b.volume.getOrNull(it) ?: 0.0 }.takeIf { it > 0 } ?: 1.0
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
        ma.forEach { (len, col) ->
            val p = Path()
            var started = false
            for (i in 0 until n) {
                val v = Technicals.sma(b.close, len, from + i + 1) ?: continue
                val pt = Offset(i * cw + cw / 2, y(v).coerceIn(0f, priceH))
                if (!started) { p.moveTo(pt.x, pt.y); started = true } else p.lineTo(pt.x, pt.y)
            }
            drawPath(p, col, style = Stroke(width = 2f))
        }
    }
}
