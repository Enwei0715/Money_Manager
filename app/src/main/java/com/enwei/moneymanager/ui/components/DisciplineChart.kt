package com.enwei.moneymanager.ui.components

import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
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
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
        Text("$symbol $name · 紀律檢查", fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold)
        val labelBg = MaterialTheme.colorScheme.surface
        Canvas(Modifier.fillMaxWidth().height(170.dp)) {
            val lo = (from until b.close.size).minOf { minOf(b.low[it], ma20[it] ?: b.low[it], ma60[it] ?: b.low[it]) }
            val hi = (from until b.close.size).maxOf { maxOf(b.high[it], ma20[it] ?: b.high[it], ma60[it] ?: b.high[it]) }
            // 上下多留空間給買賣圓章與說明，不會壓在 K 棒上
            val pad = (hi - lo) * 0.22
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
            // 和個股頁一樣：小圓章「買／賣」，只有最新一個寫出理由
            // 文字以 11sp 為基準（跟著螢幕密度與系統字級），圓章與標籤一起等比放大
            val k = 11.sp.toPx() / 22f
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = com.enwei.moneymanager.ui.AppTypeface.current; textSize = 22f * k }
            val badge = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface = com.enwei.moneymanager.ui.AppTypeface.current;
                textSize = 20f * k; color = android.graphics.Color.WHITE; textAlign = android.graphics.Paint.Align.CENTER
            }
            val shownSig = signals.filter { it.index >= from }
            val latest = shownSig.maxByOrNull { it.index }?.index
            shownSig.forEach { sg ->
                val i = sg.index - from
                val cx = i * cw + cw / 2
                val col = if (sg.buy) StockUp else StockDown
                val r = 12f * k
                val cy = if (sg.buy) (y(b.low[sg.index]) + r + 3f).coerceAtMost(size.height - r) else (y(b.high[sg.index]) - r - 3f).coerceAtLeast(r)
                drawCircle(col, radius = r, center = Offset(cx, cy))
                drawContext.canvas.nativeCanvas.drawText(com.enwei.moneymanager.ui.tr(if (sg.buy) "買" else "賣"), cx, cy + 7f * k, badge)
                if (sg.index == latest) {
                    val label = com.enwei.moneymanager.ui.tr(sg.tag.ifBlank { if (sg.buy) "買" else "賣" })
                    paint.color = android.graphics.Color.argb(255, (col.red * 255).toInt(), (col.green * 255).toInt(), (col.blue * 255).toInt())
                    val tw = paint.measureText(label)
                    val tx = (cx - tw / 2).coerceIn(4f, size.width - tw - 4f)
                    val ty = if (sg.buy) (cy + r + 24f * k).coerceAtMost(size.height - 4f) else (cy - r - 8f * k).coerceAtLeast(24f * k)
                    // 說明底下墊底色與外框，不會和 K 棒、均線疊在一起看不清楚
                    drawRoundRect(labelBg.copy(alpha = 0.94f), Offset(tx - 5f * k, ty - 21f * k), Size(tw + 10f * k, 28f * k), androidx.compose.ui.geometry.CornerRadius(6f, 6f))
                    drawRoundRect(col, Offset(tx - 5f * k, ty - 21f * k), Size(tw + 10f * k, 28f * k), androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
                    drawContext.canvas.nativeCanvas.drawText(label, tx, ty, paint)
                }
            }
        }
        Text("━ 月線　━ 季線　●買／●賣＝紀律買賣點（點選看個股）", fontSize = AppText.badge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        st?.let { s ->
            Text(s.verdict, fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold,
                color = when (s.bias) { 1 -> StockUp; -1 -> StockDown; else -> MaterialTheme.colorScheme.onSurface })
            // 依據改成小方塊（和個股頁一樣），不再一條一條長列
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                s.chips.forEach { (t, ok) ->
                    val col = when (ok) { true -> StockUp; false -> StockDown; null -> MaterialTheme.colorScheme.onSurfaceVariant }
                    Text(t, fontSize = AppText.label, color = col,
                        modifier = Modifier.background(col.copy(alpha = 0.1f), androidx.compose.foundation.shape.RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            signals.lastOrNull()?.let { sg ->
                Text("最近：${d(sg.index)} ${if (sg.buy) "買" else "賣"}・${sg.reason}", fontSize = AppText.label, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, color = if (sg.buy) StockUp else StockDown)
            }
        }
    }
}
