package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.net.DailyBars
import com.enwei.moneymanager.net.InvestData
import com.enwei.moneymanager.ui.StockDown
import com.enwei.moneymanager.ui.StockUp
import java.util.concurrent.ConcurrentHashMap

/** 小 K 線的資料快取（這次開 App 期間），同一檔不重複下載。 */
private object MiniBarsCache {
    val map = ConcurrentHashMap<String, Pair<Long, DailyBars>>()
}

/**
 * 股票卡片上的小 K 線：近 [days] 個交易日，紅漲綠跌（台股慣例），右上角顯示期間漲跌幅。
 * 讀不到資料時不佔空間。
 */
@Composable
fun MiniKLine(symbol: String, days: Int = 40, modifier: Modifier = Modifier) {
    val bars by produceState<DailyBars?>(MiniBarsCache.map[symbol]?.second, symbol) {
        val cached = MiniBarsCache.map[symbol]
        if (cached != null && System.currentTimeMillis() - cached.first < 30 * 60_000L) { value = cached.second; return@produceState }
        val b = runCatching { InvestData.bars(symbol, range = "3mo") }.getOrNull()
        if (b != null && b.close.isNotEmpty()) { MiniBarsCache.map[symbol] = System.currentTimeMillis() to b; value = b }
    }
    val b = bars ?: return
    val n = minOf(days, b.close.size)
    if (n < 2) return
    val from = b.close.size - n
    val first = b.close[from]
    val last = b.close.last()
    val pct = if (first > 0) (last - first) / first * 100 else 0.0
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("近 $n 個交易日", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                "%.2f".format(last) + "  " + (if (pct >= 0) "▲" else "▼") + "%.1f%%".format(kotlin.math.abs(pct)),
                fontSize = 11.sp, color = if (pct >= 0) StockUp else StockDown,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(84.dp).padding(top = 2.dp)) {
            val hi = (from until b.close.size).maxOf { b.high.getOrElse(it) { b.close[it] } }
            val lo = (from until b.close.size).minOf { b.low.getOrElse(it) { b.close[it] } }
            val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            fun y(v: Double) = ((hi - v) / span * size.height).toFloat()
            val w = size.width / n
            // 期間起點的價格虛線，看得出現在比起點高還低
            drawLine(grid, Offset(0f, y(first)), Offset(size.width, y(first)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            for (k in 0 until n) {
                val i = from + k
                val o = b.open.getOrElse(i) { b.close[i] }
                val c = b.close[i]
                val h = b.high.getOrElse(i) { maxOf(o, c) }
                val l = b.low.getOrElse(i) { minOf(o, c) }
                val color = if (c >= o) StockUp else StockDown
                val x = w * k + w / 2
                drawLine(color, Offset(x, y(h)), Offset(x, y(l)), 1.5f)
                val top = y(maxOf(o, c))
                val bh = (y(minOf(o, c)) - top).coerceAtLeast(1.5f)
                drawRect(color, Offset(x - w * 0.35f, top), Size(w * 0.7f, bh))
            }
        }
    }
}

/**
 * 橫條比較：每列是一個項目的本期與上期（上期用淡色細條），[highlight] 的項目加粗並用重點色。
 * 金額以 [fmt] 顯示。
 */
@Composable
fun CompareBars(
    rows: List<Triple<String, Long, Long>>, highlight: Set<String>, fmt: (Long) -> String,
    color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (name, now, prev) ->
            val hot = name in highlight
            val c = if (hot) MaterialTheme.colorScheme.error else color
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text((if (hot) "● " else "") + name, fontSize = 12.sp, fontWeight = if (hot) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    val diff = now - prev
                    Text(fmt(now), fontSize = 12.sp)
                    if (prev > 0) Text(
                        "  " + (if (diff >= 0) "+" else "−") + "%.0f%%".format(kotlin.math.abs(diff) * 100.0 / prev),
                        fontSize = 11.sp, color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(Modifier.fillMaxWidth().height(8.dp).background(track, RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(now.toFloat() / max).height(8.dp).background(c, RoundedCornerShape(4.dp)))
                }
                if (prev > 0) Box(Modifier.fillMaxWidth(prev.toFloat() / max).height(3.dp).padding(top = 1.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(14.dp).height(6.dp).background(color, RoundedCornerShape(3.dp)))
            Text(" 本期　", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(Modifier.width(14.dp).height(3.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(2.dp)))
            Text(" 上期", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 每日支出長條圖，虛線為每日平均；最高的一天標出金額。 */
@Composable
fun DailyBarsChart(values: List<Long>, fmt: (Long) -> String, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier, weekly: Boolean = false) {
    if (values.isEmpty() || values.all { it == 0L }) return
    val max = values.max().coerceAtLeast(1)
    val avg = values.sum() / values.size
    val peak = values.indexOf(values.max())
    val grid = MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier.fillMaxWidth()) {
        Row {
            val u = if (weekly) "週" else "天"
            Text(if (weekly) "每週支出" else "每日支出", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("平均 ${fmt(avg)}／$u · 最高第 ${peak + 1} $u ${fmt(values[peak])}", fontSize = 11.sp, color = grid)
        }
        Canvas(Modifier.fillMaxWidth().height(80.dp).padding(top = 4.dp)) {
            val w = size.width / values.size
            values.forEachIndexed { i, v ->
                val h = v.toFloat() / max * size.height
                drawRect(if (i == peak) color else color.copy(alpha = 0.55f), Offset(w * i + w * 0.15f, size.height - h), Size(w * 0.7f, h))
            }
            val ay = size.height - avg.toFloat() / max * size.height
            drawLine(grid, Offset(0f, ay), Offset(size.width, ay), 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        }
    }
}

/** 從快取或網路取得近 3 個月日線。 */
private suspend fun cachedBars(symbol: String): DailyBars? {
    MiniBarsCache.map[symbol]?.let { if (System.currentTimeMillis() - it.first < 30 * 60_000L) return it.second }
    val b = runCatching { InvestData.bars(symbol, range = "3mo") }.getOrNull()?.takeIf { it.close.isNotEmpty() } ?: return null
    MiniBarsCache.map[symbol] = System.currentTimeMillis() to b
    return b
}

private val SeriesColors = listOf(Color(0xFF3C82E6), Color(0xFFF0605A), Color(0xFF2E9E57), Color(0xFFFFA000), Color(0xFF7E57C2))

/** 多檔個股走勢比較：近 [days] 個交易日，以第一天為 0% 的漲跌幅折線。 */
@Composable
fun CompareChart(symbols: List<String>, days: Int = 60, modifier: Modifier = Modifier) {
    val data by produceState<List<Pair<String, List<Double>>>?>(null, symbols) {
        value = symbols.mapNotNull { s ->
            val b = cachedBars(s) ?: return@mapNotNull null
            val c = b.close.takeLast(days)
            val base = c.firstOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            "$s ${b.name.orEmpty()}".trim() to c.map { (it - base) / base * 100 }
        }
    }
    val series = data ?: return
    if (series.isEmpty()) return
    val grid = MaterialTheme.colorScheme.outlineVariant
    Column(modifier.fillMaxWidth()) {
        Text("走勢比較（近 $days 個交易日，%）", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(vertical = 4.dp)) {
            val all = series.flatMap { it.second }
            val hi = maxOf(all.max(), 0.0)
            val lo = minOf(all.min(), 0.0)
            val span = (hi - lo).takeIf { it > 0 } ?: 1.0
            fun y(v: Double) = ((hi - v) / span * size.height).toFloat()
            drawLine(grid, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            series.forEachIndexed { si, (_, v) ->
                val step = size.width / (v.size - 1).coerceAtLeast(1)
                for (i in 1 until v.size) drawLine(SeriesColors[si % SeriesColors.size], Offset(step * (i - 1), y(v[i - 1])), Offset(step * i, y(v[i])), 3f)
            }
        }
        series.forEachIndexed { si, (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(12.dp).height(3.dp).background(SeriesColors[si % SeriesColors.size]))
                Text(" $name", fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val last = v.last()
                Text((if (last >= 0) "+" else "") + "%.1f%%".format(last), fontSize = 11.sp, color = if (last >= 0) StockUp else StockDown)
            }
        }
    }
}

/**
 * 正負長條：每列一個項目，數值為正往右（[pos] 色）、為負往左（[neg] 色），[label] 為右側顯示文字。
 * 用在題材多空熱度、持股損益。
 */
@Composable
fun SignedBars(title: String, rows: List<Triple<String, Double, String>>, pos: Color = StockUp, neg: Color = StockDown, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val max = rows.maxOf { kotlin.math.abs(it.second) }.takeIf { it > 0 } ?: 1.0
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        rows.forEach { (name, v, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(88.dp))
                Row(Modifier.weight(1f).height(10.dp).background(track, RoundedCornerShape(3.dp))) {
                    val f = (kotlin.math.abs(v) / max).toFloat().coerceIn(0.02f, 1f)
                    // 左半邊放負值、右半邊放正值
                    Box(Modifier.weight(1f).height(10.dp)) {
                        if (v < 0) Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(f).height(10.dp).background(neg, RoundedCornerShape(3.dp)))
                    }
                    Box(Modifier.weight(1f).height(10.dp)) {
                        if (v >= 0) Box(Modifier.fillMaxWidth(f).height(10.dp).background(pos, RoundedCornerShape(3.dp)))
                    }
                }
                Text(" $label", fontSize = 11.sp, color = if (v >= 0) pos else neg, modifier = Modifier.width(72.dp), maxLines = 1)
            }
        }
    }
}

/** 橫條清單：每列名稱、長條、右側文字（金額、次數等），最大值為滿格。 */
@Composable
fun LabeledBars(title: String, rows: List<Triple<String, Long, String>>, color: Color = MaterialTheme.colorScheme.primary, modifier: Modifier = Modifier) {
    if (rows.isEmpty() || rows.all { it.second <= 0 }) return
    val max = rows.maxOf { it.second }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        rows.forEach { (name, v, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(76.dp))
                Box(Modifier.weight(1f).height(10.dp).background(track, RoundedCornerShape(3.dp))) {
                    if (v > 0) Box(Modifier.fillMaxWidth((v.toFloat() / max).coerceIn(0.02f, 1f)).height(10.dp).background(color, RoundedCornerShape(3.dp)))
                }
                Text(" $label", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.width(96.dp))
            }
        }
    }
}
