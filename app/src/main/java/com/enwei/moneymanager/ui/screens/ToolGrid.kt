package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.components.coachTarget
import androidx.compose.ui.draw.drawBehind
import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
fun packTiles(ids: List<String>, size: (String) -> Pair<Int, Int>): Map<String, Placement> = placeTiles(ids, size, emptyMap())

/**
 * 有存位置的格子固定放在原位（拖到哪就在哪，不會往左上靠）；
 * 沒有位置、或原位已被佔用／超出邊界的，才放到第一個空位。
 */
fun placeTiles(ids: List<String>, size: (String) -> Pair<Int, Int>, pos: Map<String, Pair<Int, Int>>): Map<String, Placement> {
    val used = ArrayList<BooleanArray>()
    fun free(r: Int, c: Int, w: Int, h: Int): Boolean {
        if (r < 0 || c < 0 || c + w > GRID_COLS) return false
        for (rr in r until r + h) for (cc in c until c + w) if (used.getOrNull(rr)?.get(cc) == true) return false
        return true
    }
    fun take(r: Int, c: Int, w: Int, h: Int) {
        while (used.size < r + h) used += BooleanArray(GRID_COLS)
        for (rr in r until r + h) for (cc in c until c + w) used[rr][cc] = true
    }
    val out = LinkedHashMap<String, Placement>()
    ids.forEach { id ->
        val (w, h) = size(id)
        val p = pos[id] ?: return@forEach
        if (p.first < 60 && free(p.first, p.second, w, h)) { take(p.first, p.second, w, h); out[id] = Placement(p.first, p.second, w, h) }
    }
    ids.filter { it !in out }.forEach { id ->
        val (w, h) = size(id)
        var r = 0
        while (true) {
            val c = (0..GRID_COLS - w).firstOrNull { free(r, it, w, h) }
            if (c != null) { take(r, c, w, h); out[id] = Placement(r, c, w, h); break }
            r++
        }
    }
    return ids.mapNotNull { id -> out[id]?.let { id to it } }.toMap(LinkedHashMap())
}

/**
 * 把 [id] 放到 (row, col)（大小 w×h）：其他格子維持原位，被壓到的格子挪到最近的空位。
 * 回傳新的位置表。
 */
fun moveTile(place: Map<String, Placement>, id: String, row: Int, col: Int, w: Int, h: Int): Map<String, Pair<Int, Int>> {
    val c = col.coerceIn(0, GRID_COLS - w)
    val r = row.coerceAtLeast(0)
    fun overlaps(p: Placement) = p.row < r + h && r < p.row + p.h && p.col < c + w && c < p.col + p.w
    val out = LinkedHashMap<String, Pair<Int, Int>>()
    out[id] = r to c
    place.forEach { (o, p) -> if (o != id && !overlaps(p)) out[o] = p.row to p.col }
    return out
}

/**
 * 工具格：三欄網格。編輯時顯示實際的格線；拖動方框移動位置、拖右下角的把手改大小（貼齊格線），
 * 點一下可以從大小清單挑（1×1、2×1、3×1、1×2、2×2），左上角 ✕ 移除。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ToolGrid(
    tools: List<ToolItem>, sizes: Map<String, Pair<Int, Int>>, positions: Map<String, Pair<Int, Int>>,
    summary: Map<String, String>, editing: Boolean,
    onOpen: (ToolItem) -> Unit, onLongPress: () -> Unit,
    /** 新的位置表（拖動或改大小後）；[onResize] 同時給新的大小 */
    onMove: (Map<String, Pair<Int, Int>>) -> Unit, onResize: (String, Int, Int, Map<String, Pair<Int, Int>>) -> Unit, onRemove: (String) -> Unit,
) {
    val ids = tools.map { it.id }
    fun sizeOf(id: String) = sizes[id] ?: (1 to 1)
    val place = placeTiles(ids, ::sizeOf, positions)
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
        val gridRadius = with(androidx.compose.ui.platform.LocalDensity.current) { com.enwei.moneymanager.ui.AppearancePrefs.cardRadius.toPx() }
        if (editing) Canvas(Modifier.fillMaxSize()) {
            for (r in 0 until rows) for (c in 0 until GRID_COLS) {
                // 編輯時的格線：很淡的實心底，不用虛線（看起來比較整齊）
                drawRoundRect(
                    line.copy(alpha = 0.18f), Offset(c * (cellPx + gapPx), r * (rowPx + gapPx)), Size(cellPx, rowPx), CornerRadius(gridRadius, gridRadius),
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
                    .coachTarget("tool.${t.id}")
                    .zIndex(if (isDrag || isResize) 2f else 1f)
                    .scale(if (isDrag) 1.05f else 1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), color = MaterialTheme.colorScheme.surface,
                    border = if (editing) BorderStroke(if (selected == t.id) 1.5.dp else 1.dp,
                        if (selected == t.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)) else null,
                    shadowElevation = if (isDrag) 8.dp else 0.dp,
                    modifier = Modifier.fillMaxSize()
                        .then(
                            if (editing) Modifier.pointerInput(t.id, ids, place) {
                                // 長按後才拖動整格：一般上下滑動用來捲動頁面（工具放滿時才捲得到下方）
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragId = t.id; drag = Offset.Zero; selected = t.id },
                                    onDrag = { ch, d -> ch.consume(); drag += d },
                                    onDragEnd = {
                                        // 放開的位置：以方框左上角貼齊最近的格子，固定在那裡
                                        val tc = ((p.col * (cellPx + gapPx) + drag.x) / (cellPx + gapPx)).roundToInt()
                                        val tr = ((p.row * (rowPx + gapPx) + drag.y) / (rowPx + gapPx)).roundToInt()
                                        onMove(moveTile(place, t.id, tr, tc, p.w, p.h))
                                        dragId = null; drag = Offset.Zero
                                    },
                                    onDragCancel = { dragId = null; drag = Offset.Zero },
                                )
                            } else Modifier,
                        )
                        .combinedClickable(
                            onClick = { if (editing) selected = if (selected == t.id) null else t.id else onOpen(t) },
                            // 編輯中不偵測長按：否則長按會被點擊處理吃掉，拖曳手勢收不到（長按後無法移動）
                            onLongClick = if (editing) null else ({ onLongPress() }),
                        ),
                ) { TileContent(t, w, h, summary[t.id]) }
                if (editing) {
                    // 右上角移除：格子裡面的小圓，細線「－」，不壓到外框
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            .clickable { onRemove(t.id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val c = MaterialTheme.colorScheme.onSurfaceVariant
                        Canvas(Modifier.size(10.dp)) { drawLine(c, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
                    }
                    // 右下角把手：拖動改大小
                    // 右下角把手：格子內側一個細線直角，拖動改大小（觸控範圍比看到的大）
                    val handle = MaterialTheme.colorScheme.primary
                    // 只在選取的那一格顯示：其他格的角落不會攔下上下滑動（工具放滿時才捲得動）
                    if (selected == t.id) Box(
                        Modifier.align(Alignment.BottomEnd).size(32.dp)
                            .drawBehind {
                                val m = 8.dp.toPx(); val l = 10.dp.toPx(); val w = 1.6.dp.toPx()
                                val ex = size.width - m; val ey = size.height - m
                                drawLine(handle, Offset(ex - l, ey), Offset(ex, ey), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                                drawLine(handle, Offset(ex, ey - l), Offset(ex, ey), w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            }
                            .pointerInput(t.id, p) {
                                detectDragGestures(
                                    onDragStart = { resizeId = t.id; resize = Offset.Zero; selected = t.id },
                                    onDrag = { ch, d -> ch.consume(); resize += d },
                                    onDragEnd = {
                                        val nw = ((p.w * (cellPx + gapPx) + resize.x) / (cellPx + gapPx)).roundToInt().coerceIn(1, GRID_COLS)
                                        val nh = ((p.h * (rowPx + gapPx) + resize.y) / (rowPx + gapPx)).roundToInt().coerceIn(1, 2)
                                        onResize(t.id, nw, nh, moveTile(place, t.id, p.row, p.col, nw, nh))
                                        resizeId = null; resize = Offset.Zero
                                    },
                                    onDragCancel = { resizeId = null; resize = Offset.Zero },
                                )
                            },
                    )
                }
            }
        }
    }
    // 點選一格後：挑大小
    val sel = selected?.takeIf { editing && it in ids }
    if (sel != null) {
        val (cw, ch) = sizeOf(sel)
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("「${tools.firstOrNull { it.id == sel }?.label.orEmpty()}」的大小", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(1 to 1, 2 to 1, 3 to 1, 1 to 2, 2 to 2).forEach { (w, h) ->
                    FilterChip(cw == w && ch == h, { place[sel]?.let { p -> onResize(sel, w, h, moveTile(place, sel, p.row, p.col, w, h)) } }, label = { Text("$w×$h") })
                }
            }
        }
    }
}

/** 一格的內容：依大小顯示圖示、名稱與摘要。 */
@Composable
private fun TileContent(t: ToolItem, w: Int, h: Int, summary: String?) {
    // 圖示用主題色，格子看起來有重點
    val tint = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment = Alignment.Center) {
        when {
            w == 1 && h == 1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(t.icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Text(t.label, fontSize = AppText.secondary, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
            h == 1 -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Icon(t.icon, null, tint = tint, modifier = Modifier.size(28.dp))
                Column(Modifier.padding(start = 10.dp).weight(1f, fill = false)) {
                    Text(t.label, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (!summary.isNullOrBlank()) Text(summary, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(t.icon, null, tint = tint, modifier = Modifier.size(if (w >= 2) 40.dp else 32.dp))
                Text(t.label, fontSize = AppText.section, fontWeight = FontWeight.SemiBold, maxLines = 1)
                if (!summary.isNullOrBlank()) Text(summary, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
