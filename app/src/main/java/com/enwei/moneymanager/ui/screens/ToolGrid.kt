package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.enwei.moneymanager.ui.components.Text
import kotlin.math.roundToInt

/** 工具格的位置與大小（以格為單位）。 */
data class Placement(val row: Int, val col: Int, val w: Int, val h: Int)

const val GRID_COLS = 3

/** 大小字串「寬x高」→ (寬, 高)。 */
fun parseSize(s: String?): Pair<Int, Int>? = s?.split('x')?.takeIf { it.size == 2 }?.let {
    val w = it[0].toIntOrNull() ?: return null
    val h = it[1].toIntOrNull() ?: return null
    w.coerceIn(1, GRID_COLS) to h.coerceIn(1, 2)
}

/** 依順序把每一格放到第一個放得下的位置（由上到下、由左到右）。 */
fun packTiles(ids: List<String>, size: (String) -> Pair<Int, Int>): Map<String, Placement> {
    val used = ArrayList<BooleanArray>()
    fun free(r: Int, c: Int, w: Int, h: Int): Boolean {
        if (c + w > GRID_COLS) return false
        for (rr in r until r + h) for (cc in c until c + w) if (used.getOrNull(rr)?.get(cc) == true) return false
        return true
    }
    val out = LinkedHashMap<String, Placement>()
    ids.forEach { id ->
        val (w, h) = size(id)
        var r = 0
        while (true) {
            val c = (0..GRID_COLS - w).firstOrNull { free(r, it, w, h) }
            if (c != null) {
                while (used.size < r + h) used += BooleanArray(GRID_COLS)
                for (rr in r until r + h) for (cc in c until c + w) used[rr][cc] = true
                out[id] = Placement(r, c, w, h)
                break
            }
            r++
        }
    }
    return out
}

/**
 * 工具格：三欄網格。編輯時顯示實際的格線；拖動方框移動位置、拖右下角的把手改大小（貼齊格線），
 * 點一下可以從大小清單挑（1×1、2×1、3×1、1×2、2×2），左上角 ✕ 移除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolGrid(
    tools: List<ToolItem>, sizes: Map<String, Pair<Int, Int>>, summary: Map<String, String>, editing: Boolean,
    onOpen: (ToolItem) -> Unit, onLongPress: () -> Unit,
    onReorder: (List<String>) -> Unit, onResize: (String, Int, Int) -> Unit, onRemove: (String) -> Unit,
) {
    val ids = tools.map { it.id }
    fun sizeOf(id: String) = sizes[id] ?: (1 to 1)
    val place = packTiles(ids, ::sizeOf)
    var selected by remember { mutableStateOf<String?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    var resizeId by remember { mutableStateOf<String?>(null) }
    var resize by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val gap = 8.dp
    val rowH = 96.dp
    val rows = (place.values.maxOfOrNull { it.row + it.h } ?: 0) + if (editing) 1 else 0
    BoxWithConstraints(Modifier.fillMaxWidth().height(rowH * rows + gap * (rows - 1).coerceAtLeast(0))) {
        val cellW = (maxWidth - gap * (GRID_COLS - 1)) / GRID_COLS
        val cellPx = with(density) { cellW.toPx() }
        val rowPx = with(density) { rowH.toPx() }
        val gapPx = with(density) { gap.toPx() }
        val line = MaterialTheme.colorScheme.outlineVariant
        // 編輯時的實際格線
        if (editing) Canvas(Modifier.fillMaxSize()) {
            for (r in 0 until rows) for (c in 0 until GRID_COLS) {
                drawRoundRect(
                    line, Offset(c * (cellPx + gapPx), r * (rowPx + gapPx)), Size(cellPx, rowPx), CornerRadius(28f, 28f),
                    style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
        }
        tools.forEach { t ->
            val p = place[t.id] ?: return@forEach
            val isDrag = dragId == t.id
            val isResize = resizeId == t.id
            // 改大小時即時預覽（貼齊格數）
            val w = if (isResize) ((p.w * (cellPx + gapPx) + resize.x) / (cellPx + gapPx)).roundToInt().coerceIn(1, GRID_COLS) else p.w
            val h = if (isResize) ((p.h * (rowPx + gapPx) + resize.y) / (rowPx + gapPx)).roundToInt().coerceIn(1, 2) else p.h
            val x = p.col * (cellPx + gapPx) + if (isDrag) drag.x else 0f
            val y = p.row * (rowPx + gapPx) + if (isDrag) drag.y else 0f
            Box(
                Modifier.offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                    .size(cellW * w + gap * (w - 1), rowH * h + gap * (h - 1))
                    .zIndex(if (isDrag || isResize) 2f else 1f)
                    .scale(if (isDrag) 1.05f else 1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                    border = if (editing) BorderStroke(if (selected == t.id) 2.dp else 1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (selected == t.id) 1f else 0.5f)) else null,
                    shadowElevation = if (isDrag) 8.dp else 0.dp,
                    modifier = Modifier.fillMaxSize()
                        .then(
                            if (editing) Modifier.pointerInput(t.id, ids, place) {
                                // 拖動整格：放開時依位置決定插入的順序
                                detectDragGestures(
                                    onDragStart = { dragId = t.id; drag = Offset.Zero; selected = t.id },
                                    onDrag = { ch, d -> ch.consume(); drag += d },
                                    onDragEnd = {
                                        val cx = p.col * (cellPx + gapPx) + drag.x + cellPx / 2
                                        val cy = p.row * (rowPx + gapPx) + drag.y + rowPx / 2
                                        val tc = (cx / (cellPx + gapPx)).toInt().coerceIn(0, GRID_COLS - 1)
                                        val tr = (cy / (rowPx + gapPx)).toInt().coerceAtLeast(0)
                                        val others = ids - t.id
                                        val before = others.count { o -> place[o]?.let { it.row < tr || it.row == tr && it.col < tc } == true }
                                        onReorder(others.toMutableList().apply { add(before.coerceIn(0, size), t.id) })
                                        dragId = null; drag = Offset.Zero
                                    },
                                    onDragCancel = { dragId = null; drag = Offset.Zero },
                                )
                            } else Modifier,
                        )
                        .combinedClickable(
                            onClick = { if (editing) selected = if (selected == t.id) null else t.id else onOpen(t) },
                            onLongClick = { if (!editing) onLongPress() },
                        ),
                ) { TileContent(t, w, h, summary[t.id]) }
                if (editing) {
                    // 左上角移除
                    Box(
                        Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp).size(22.dp)
                            .background(MaterialTheme.colorScheme.error, CircleShape).clickable { onRemove(t.id) },
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", fontSize = 11.sp, color = MaterialTheme.colorScheme.onError) }
                    // 右下角把手：拖動改大小
                    Box(
                        Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(24.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .pointerInput(t.id, p) {
                                detectDragGestures(
                                    onDragStart = { resizeId = t.id; resize = Offset.Zero; selected = t.id },
                                    onDrag = { ch, d -> ch.consume(); resize += d },
                                    onDragEnd = {
                                        val nw = ((p.w * (cellPx + gapPx) + resize.x) / (cellPx + gapPx)).roundToInt().coerceIn(1, GRID_COLS)
                                        val nh = ((p.h * (rowPx + gapPx) + resize.y) / (rowPx + gapPx)).roundToInt().coerceIn(1, 2)
                                        onResize(t.id, nw, nh)
                                        resizeId = null; resize = Offset.Zero
                                    },
                                    onDragCancel = { resizeId = null; resize = Offset.Zero },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text("⤡", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
    }
    // 點選一格後：挑大小
    val sel = selected?.takeIf { editing && it in ids }
    if (sel != null) {
        val (cw, ch) = sizeOf(sel)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("「${tools.first { it.id == sel }.label}」的大小", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to 1, 2 to 1, 3 to 1, 1 to 2, 2 to 2).forEach { (w, h) ->
                    FilterChip(cw == w && ch == h, { onResize(sel, w, h) }, label = { Text("$w×$h") })
                }
            }
        }
    }
    if (editing) Text("拖動方框移動位置，拖右下角的圓點改大小，點一下可以挑大小，✕ 移除。", fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
}

/** 一格的內容：依大小顯示圖示、名稱與摘要。 */
@Composable
private fun TileContent(t: ToolItem, w: Int, h: Int, summary: String?) {
    val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        when {
            w == 1 && h == 1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(t.icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Text(t.label, fontSize = 13.sp, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
            h == 1 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(t.icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f, fill = false)) {
                    Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (!summary.isNullOrBlank()) Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(t.icon, null, tint = tint, modifier = Modifier.size(if (w >= 2) 40.dp else 32.dp))
                Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (!summary.isNullOrBlank()) Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
