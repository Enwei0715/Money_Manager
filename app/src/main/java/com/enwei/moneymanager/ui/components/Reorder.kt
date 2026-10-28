package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

/**
 * 可拖曳排序的清單（放在可捲動的 Column 裡使用）。按住右側把手上下拖曳，放開後呼叫 [onReorder]。
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    row: @Composable (item: T, handle: Modifier) -> Unit,
) {
    val list = remember(items) { items.toMutableStateList() }
    val heights = remember { mutableStateMapOf<Any, Int>() }
    var dragging by remember { mutableStateOf<Any?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val commit by rememberUpdatedState(onReorder)

    Column {
        list.forEach { item ->
            val k = key(item)
            val isDragging = dragging == k
            val handle = Modifier.pointerInput(k, list) {
                detectDragGestures(
                    onDragStart = { dragging = k; offset = 0f },
                    onDragEnd = { dragging = null; offset = 0f; commit(list.toList()) },
                    onDragCancel = { dragging = null; offset = 0f },
                    onDrag = { change, d ->
                        change.consume()
                        offset += d.y
                        val i = list.indexOfFirst { key(it) == k }
                        if (i < 0) return@detectDragGestures
                        if (offset > 0 && i < list.lastIndex) {
                            val h = heights[key(list[i + 1])] ?: return@detectDragGestures
                            if (offset > h / 2f) { list.add(i + 1, list.removeAt(i)); offset -= h }
                        } else if (offset < 0 && i > 0) {
                            val h = heights[key(list[i - 1])] ?: return@detectDragGestures
                            if (-offset > h / 2f) { list.add(i - 1, list.removeAt(i)); offset += h }
                        }
                    },
                )
            }
            Box(
                Modifier.onSizeChanged { heights[k] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) offset else 0f; shadowElevation = if (isDragging) 8f else 0f },
            ) { row(item, handle) }
        }
    }
}

/** 管理清單的一列：刪除、名稱與摘要、編輯、拖曳把手（與分類／帳戶群組設定共用）。 */
@Composable
fun ManageRow(
    title: String,
    subtitle: String? = null,
    handle: Modifier,
    dimmed: Boolean = false,
    onDelete: (() -> Unit)?,
    onEdit: () -> Unit,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                onDelete != null -> Box(
                    Modifier.size(40.dp).clickable(onClick = onDelete), contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(22.dp).background(MaterialTheme.colorScheme.error, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Remove, "刪除", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                leading != null -> Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { leading() }
                else -> Spacer(Modifier.width(40.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                if (!subtitle.isNullOrBlank()) Text(
                    subtitle, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "編輯", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DragHandle, "拖曳排序", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
