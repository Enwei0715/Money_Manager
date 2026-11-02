package com.enwei.moneymanager.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.enwei.moneymanager.MoneyApp

/**
 * 每個頁面上次停在哪個分頁：存起來，離開再回來、甚至重開 App 都回到上次的分頁
 * （例如帳戶 → 資金紀律 → 持股，下次打開帳戶還是在持股）。
 */
object TabMemory {
    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("tabs", Context.MODE_PRIVATE) }
    fun get(key: String, default: Int) = prefs.getInt(key, default)
    fun set(key: String, v: Int) = prefs.edit().putInt(key, v).apply()
}

private class SavedTab(private val key: String, private val state: MutableState<Int>) : MutableState<Int> by state {
    override var value: Int
        get() = state.value
        set(v) { state.value = v; TabMemory.set(key, v) }
}

/** 記住分頁位置的狀態（用法和 `remember { mutableStateOf(0) }` 一樣）。 */
@Composable
fun rememberTab(key: String, default: Int = 0): MutableState<Int> =
    remember(key) { SavedTab(key, mutableStateOf(TabMemory.get(key, default))) }
