package com.enwei.moneymanager.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 線條樣式的區塊：底色與分隔線一路畫到螢幕左右兩邊（像股票頁），內容仍對齊頁面的左右留白。
 * 只有幾乎滿版的區塊才延伸（左右各離邊 28dp 以內）；並排的小區塊維持原本寬度，避免互相蓋到。
 */
@Composable
fun Modifier.bleedBlock(bg: Color, top: Boolean = true, bottom: Boolean = true, lineInset: Dp = 0.dp): Modifier {
    val line = MaterialTheme.colorScheme.outlineVariant
    val max = with(LocalDensity.current) { 28.dp.toPx() }
    val ext = remember { mutableStateOf(0f to 0f) }
    // 並排的小區塊（不延伸）改畫四邊細框，看得出是一格
    val boxed = remember { mutableStateOf(false) }
    return this
        .onPlaced { c ->
            val rootW = c.findRootCoordinates().size.width.toFloat()
            val l = c.positionInRoot().x
            val r = rootW - l - c.size.width
            val wide = l in 0f..max && r in -1f..max
            ext.value = if (wide) l to r.coerceAtLeast(0f) else 0f to 0f
            boxed.value = !wide
        }
        .drawBehind {
            val (l, r) = ext.value
            val w = 0.6.dp.toPx()
            if (boxed.value && top && bottom) {
                drawRect(bg)
                drawRect(line, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 1.4f))
                return@drawBehind
            }
            drawRect(bg, Offset(-l, 0f), Size(size.width + l + r, size.height))
            if (top) drawLine(line, Offset(-l, 0f), Offset(size.width + r, 0f), w)
            if (bottom) drawLine(line, Offset(if (lineInset > 0.dp) lineInset.toPx() else -l, size.height - w), Offset(size.width + r, size.height - w), w)
        }
}
