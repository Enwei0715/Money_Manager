package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.TradeSignals
import com.enwei.moneymanager.core.marketOf
import com.enwei.moneymanager.data.signalParams
import com.enwei.moneymanager.net.DailyBars
import com.enwei.moneymanager.net.InvestData
import com.enwei.moneymanager.ui.StockDown
import com.enwei.moneymanager.ui.StockUp

/**
 * 對話用的紀律 K 線：近 60 根日 K、月線與季線，框出依紀律參數算出的買賣點並標上理由，
 * 下面列出目前的依據（站上／跌破月線、季線、停損價…）與依紀律的結論。
 */
@Composable
fun DisciplineChart(symbol: String, modifier: Modifier = Modifier) {
    val settings by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
    val holdings by MoneyApp.repo.holdingsByBroker.collectAsStateWithLifecycle(emptyMap())
    val market = marketOf(symbol)
    val bars by produceState<DailyBars?>(null, symbol) {
        value = runCatching { InvestData.bars(symbol, "1y", tw = market == com.enwei.moneymanager.core.Market.TW) }.getOrNull()?.let { b ->
            runCatching { com.enwei.moneymanager.net.QuoteService.live(market, symbol) }.getOrNull()?.let { b.withLive(it) } ?: b
        }
    }
    val b = bars ?: return
    if (b.close.size < 70) return
    val p = settings.signalParams(symbol)
    val signals = TradeSignals.scan(b.open, b.high, b.low, b.close, b.volume, p)
    val held = holdings.values.flatten().filter { it.symbol == symbol && it.shares > 0.0001 }
    val avg = held.takeIf { it.isNotEmpty() }?.let { hs -> hs.sumOf { it.avgCost * it.shares } / hs.sumOf { it.shares } }
    fun d(i: Int) = java.time.LocalDate.ofEpochDay(b.days[i]).let { "${it.monthValue}/${it.dayOfMonth}" }
    val st = TradeSignals.status(b.high, b.close, p, avg, signals) { d(it) }
    val name = held.firstNotNullOfOrNull { it.name } ?: com.enwei.moneymanager.net.TwData.nameOf(symbol) ?: b.name.orEmpty()
    val n = 60
    val from = b.close.size - n
    val ma20 = b.close.indices.map { com.enwei.moneymanager.core.Technicals.sma(b.close, 20, it + 1) }
    val ma60 = b.close.indices.map { com.enwei.moneymanager.core.Technicals.sma(b.close, 60, it + 1) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text("$symbol $name · 紀律檢查", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val lo = (from until b.close.size).minOf { minOf(b.low[it], ma20[it] ?: b.low[it], ma60[it] ?: b.low[it]) }
            val hi = (from until b.close.size).maxOf { maxOf(b.high[it], ma20[it] ?: b.high[it], ma60[it] ?: b.high[it]) }
            val pad = (hi - lo) * 0.12
            val top = hi + pad; val bottom = lo - pad
            val r = (top - bottom).takeIf { it > 0 } ?: 1.0
            val cw = size.width / n
            fun y(v: Double) = ((top - v) / r * size.height).toFloat()
            for (i in 0 until n) {
                val j = from + i
                val col = if (b.close[j] >= b.open[j]) StockUp else StockDown
                val cx = i * cw + cw / 2
                drawLine(col, Offset(cx, y(b.high[j])), Offset(cx, y(b.low[j])), 1f)
                val t = y(maxOf(b.open[j], b.close[j])); val bt = y(minOf(b.open[j], b.close[j]))
                drawRect(col, Offset(cx - cw * 0.35f, t), Size(cw * 0.7f, maxOf(1f, bt - t)))
            }
            listOf(ma20 to Color(0xFFFFA000), ma60 to Color(0xFF7E57C2)).forEach { (m, col) ->
                for (i in 1 until n) {
                    val a = m[from + i - 1] ?: continue; val c = m[from + i] ?: continue
                    drawLine(col, Offset((i - 1) * cw + cw / 2, y(a)), Offset(i * cw + cw / 2, y(c)), 2f)
                }
            }
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; isFakeBoldText = true }
            signals.filter { it.index >= from }.forEach { sg ->
                val i = sg.index - from
                val cx = i * cw + cw / 2
                val col = if (sg.buy) StockUp else StockDown
                val half = maxOf(cw * 0.8f, 7f)
                val tp = y(b.high[sg.index]) - 4f; val bt = y(b.low[sg.index]) + 4f
                drawRoundRect(col, Offset(cx - half, tp), Size(half * 2, bt - tp), CornerRadius(half, half), style = Stroke(2.5f))
                val label = (if (sg.buy) "買" else "賣") + (sg.tag.takeIf { it.isNotBlank() }?.let { "·$it" } ?: "")
                paint.color = android.graphics.Color.argb(255, (col.red * 255).toInt(), (col.green * 255).toInt(), (col.blue * 255).toInt())
                val tw = paint.measureText(label)
                drawContext.canvas.nativeCanvas.drawText(label, (cx - tw / 2).coerceIn(0f, size.width - tw),
                    if (sg.buy) (bt + 24f).coerceAtMost(size.height - 2f) else (tp - 6f).coerceAtLeast(22f), paint)
            }
        }
        Text("━ 月線（20 日）　━ 季線（60 日）　框起來的 K 棒＝紀律買賣點", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        st?.let { s ->
            Text(s.verdict, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = when (s.bias) { 1 -> StockUp; -1 -> StockDown; else -> MaterialTheme.colorScheme.onSurface })
            s.checks.forEach { (t, ok) ->
                Text((when (ok) { true -> "✓ "; false -> "✗ "; null -> "・" }) + t, fontSize = 12.sp,
                    color = when (ok) { true -> StockUp; false -> StockDown; null -> MaterialTheme.colorScheme.onSurfaceVariant })
            }
        }
    }
}
