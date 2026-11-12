package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 刪除後的「復原」提示：不先跳確認框，直接刪除並在畫面下方顯示 5 秒「已刪除・復原」。
 * 任何畫面都可以呼叫 [show]；提示放在 App 最外層，換頁後仍看得到。
 */
object Undo {
    data class Item(val message: String, val undo: suspend () -> Unit, val id: Long = System.nanoTime())

    var current by mutableStateOf<Item?>(null)
        private set

    fun show(message: String, undo: suspend () -> Unit) { current = Item(message, undo) }

    fun dismiss() { current = null }
}

@Composable
fun UndoBar(modifier: Modifier = Modifier) {
    val item = Undo.current ?: return
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.id) { delay(5_000); if (Undo.current?.id == item.id) Undo.dismiss() }
    Box(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Snackbar(action = {
            TextButton(onClick = { Undo.dismiss(); scope.launch { item.undo() } }) { Text("復原") }
        }) { Text(item.message) }
    }
}
