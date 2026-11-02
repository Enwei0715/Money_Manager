package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * 會跟著捲動收起的頂部區塊（分頁列、搜尋、篩選…）：內容往下捲時頂部滑走，讓內容用滿整個畫面；
 * 往上捲一點就立刻滑回來，不必捲到最上面才能切分頁或搜尋。
 */
@Composable
fun CollapsingTop(modifier: Modifier = Modifier, header: @Composable () -> Unit, content: @Composable () -> Unit) {
    var headerH by remember { mutableFloatStateOf(0f) }
    var offset by remember { mutableFloatStateOf(0f) }
    val conn = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val d = available.y
                if (d == 0f || headerH <= 0f) return Offset.Zero
                val next = (offset + d).coerceIn(-headerH, 0f)
                val used = next - offset
                offset = next
                return Offset(0f, used)
            }
        }
    }
    Layout(
        content = { Box { header() }; Box { content() } },
        modifier = modifier.nestedScroll(conn).clipToBounds(),
    ) { ms, c ->
        val hp = ms[0].measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
        headerH = hp.height.toFloat()
        if (offset < -headerH) offset = -headerH
        val shown = (hp.height + offset.roundToInt()).coerceIn(0, hp.height)
        val h = if (c.hasBoundedHeight) (c.maxHeight - shown).coerceAtLeast(0) else Constraints.Infinity
        val cp = ms[1].measure(c.copy(minHeight = if (c.hasBoundedHeight) h else 0, maxHeight = h))
        val total = if (c.hasBoundedHeight) c.maxHeight else shown + cp.height
        layout(c.maxWidth, total) {
            cp.place(0, shown)
            hp.place(0, shown - hp.height)
        }
    }
}

/** 底部分頁列是否因為往下捲而收起（往上捲或換頁時出現）。 */
object Chrome {
    var bottomHidden by mutableStateOf(false)
    /** 次頁的標題列（BackTopBar）是否因往下捲而收起 */
    var topHidden by mutableStateOf(false)

    /** 放在整個內容外層：只看捲動方向，不吃掉捲動量 */
    val watcher = object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (consumed.y < -6f) { bottomHidden = true; topHidden = true }
            else if (consumed.y > 6f) { bottomHidden = false; topHidden = false }
            return Offset.Zero
        }
    }
}
