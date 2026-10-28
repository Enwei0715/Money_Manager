package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.core.Money
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private fun niceStep(range: Double): Double {
    if (range <= 0) return 1.0
    val raw = range / 4
    val mag = 10.0.pow(floor(log10(raw)))
    val n = raw / mag
    val step = when { n <= 1 -> 1.0; n <= 2 -> 2.0; n <= 2.5 -> 2.5; n <= 5 -> 5.0; else -> 10.0 }
    return step * mag
}

private fun axisLabel(v: Double): String = when {
    Math.abs(v) >= 1_000_000 -> "${trim(v / 1_000_000)}m"
    Math.abs(v) >= 1_000 -> "${trim(v / 1_000)}k"
    else -> trim(v)
}

private fun trim(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v)

private val AxisWidth = 44f

/** 折線圖（例如餘額走勢），下方顯示各點標籤與數值。 */
@Composable
fun LineChart(values: List<Long>, labels: List<String>, color: Color, modifier: Modifier = Modifier, autoMin: Boolean = true) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val plotBg = MaterialTheme.colorScheme.background
    val vs = values.map { it / 100.0 }
    var lo = if (autoMin) (vs.minOrNull() ?: 0.0) else minOf(0.0, vs.minOrNull() ?: 0.0)
    var hi = vs.maxOrNull() ?: 1.0
    if (hi == lo) { hi += 1; lo -= if (autoMin) 1 else 0 }
    val step = niceStep(hi - lo)
    lo = floor(lo / step) * step
    hi = ceil(hi / step) * step
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(200.dp).padding(top = 8.dp, end = 8.dp)) {
            val left = AxisWidth * density
            val w = size.width - left
            val h = size.height
            drawRect(plotBg, Offset(left, 0f), Size(w, h))
            var g = lo
            while (g <= hi + 1e-9) {
                val y = h - ((g - lo) / (hi - lo) * h).toFloat()
                drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 2f)
                val t = measurer.measure(axisLabel(g), TextStyle(fontSize = 10.sp, color = textColor))
                drawText(t, topLeft = Offset(left - t.size.width - 6f, y - t.size.height / 2))
                g += step
            }
            if (vs.isEmpty()) return@Canvas
            val dx = w / vs.size
            val pts = vs.mapIndexed { i, v -> Offset(left + dx * (i + 0.5f), h - ((v - lo) / (hi - lo) * h).toFloat()) }
            pts.zipWithNext().forEach { (a, b) -> drawLine(color, a, b, strokeWidth = 5f) }
            pts.forEach { drawCircle(color, 9f, it) }
        }
        Row(Modifier.fillMaxWidth().padding(start = AxisWidth.dp)) {
            labels.forEachIndexed { i, l ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, fontSize = 11.sp, color = textColor)
                    Text(Money.full(values.getOrElse(i) { 0 }), fontSize = 9.sp, maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** 收入／支出並排長條圖。 */
@Composable
fun DualBarChart(first: List<Long>, second: List<Long>, labels: List<String>, firstColor: Color, secondColor: Color, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val grid = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val plotBg = MaterialTheme.colorScheme.background
    val maxV = (first + second).maxOrNull()?.div(100.0)?.coerceAtLeast(1.0) ?: 1.0
    val step = niceStep(maxV)
    val hi = ceil(maxV / step) * step
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp, end = 8.dp)) {
            val left = AxisWidth * density
            val w = size.width - left
            val h = size.height
            drawRect(plotBg, Offset(left, 0f), Size(w, h))
            var g = 0.0
            while (g <= hi + 1e-9) {
                val y = h - (g / hi * h).toFloat()
                drawLine(grid, Offset(left, y), Offset(size.width, y), strokeWidth = 2f)
                val t = measurer.measure(axisLabel(g), TextStyle(fontSize = 10.sp, color = textColor))
                drawText(t, topLeft = Offset(left - t.size.width - 6f, y - t.size.height / 2))
                g += step
            }
            val dx = w / labels.size.coerceAtLeast(1)
            val bw = dx * 0.2f
            labels.indices.forEach { i ->
                val cx = left + dx * (i + 0.5f)
                val h1 = (first.getOrElse(i) { 0 } / 100.0 / hi * h).toFloat()
                val h2 = (second.getOrElse(i) { 0 } / 100.0 / hi * h).toFloat()
                drawRect(firstColor.copy(alpha = 0.55f), Offset(cx - bw - 4f, h - h1), Size(bw, h1))
                drawRect(secondColor.copy(alpha = 0.45f), Offset(cx + 4f, h - h2), Size(bw, h2))
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = AxisWidth.dp)) {
            labels.forEachIndexed { i, l ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, fontSize = 11.sp, color = textColor)
                    Text(Money.full(first.getOrElse(i) { 0 }), fontSize = 9.sp, color = firstColor, maxLines = 1)
                    Text(Money.full(second.getOrElse(i) { 0 }), fontSize = 9.sp, color = secondColor, maxLines = 1)
                }
            }
        }
    }
}
