package com.enwei.moneymanager.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.MoneyApp

/** 可收合區塊的展開狀態：存起來，換分頁、離開畫面或重開 App 都維持上次的樣子。 */
object FoldPrefs {
    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("folds", Context.MODE_PRIVATE) }
    fun get(key: String, default: Boolean) = prefs.getBoolean(key, default)
    fun set(key: String, open: Boolean) = prefs.edit().putBoolean(key, open).apply()
}

/** 一個會記住的展開狀態。 */
class Fold(private val key: String, private val state: MutableState<Boolean>) {
    var open: Boolean
        get() = state.value
        set(v) { state.value = v; FoldPrefs.set(key, v) }
    fun toggle() { open = !open }
}

/** [key] 是這個區塊的名稱（同一個區塊在各處用同一個 key）；[default] 為第一次看到時是否展開。 */
@Composable
fun rememberFold(key: String, default: Boolean): Fold {
    val state = remember(key) { mutableStateOf(FoldPrefs.get(key, default)) }
    return remember(key) { Fold(key, state) }
}

/**
 * 明顯的展開／收合按鈕：有底色的小膠囊，寫「展開」「收合」加箭頭（[count] 為收起時顯示的數量）。
 * 點擊交給外層整列處理。
 */
@Composable
fun FoldToggle(open: Boolean, count: Int? = null, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(Modifier.padding(start = 10.dp, end = 6.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (open) "收合" else "展開" + (count?.let { " $it" } ?: ""),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Icon(
                if (open) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp),
            )
        }
    }
}
