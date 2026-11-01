package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 開啟特殊權限前的說明與同意（Google Play 的規定：使用通知存取、無障礙服務前要清楚說明蒐集什麼、做什麼用，並取得同意）。
 * [kind] 0 通知存取、1 錢包畫面讀取（無障礙服務）。
 */
@Composable
fun AccessDisclosure(kind: Int, onAgree: () -> Unit, onDismiss: () -> Unit) {
    val (title, body) = if (kind == 0) "允許讀取通知" to listOf(
        "帳理會讀取你在「通知記帳」中勾選的銀行、信用卡與支付 App 的通知內容（金額、商家、時間），自動建立交易紀錄。",
        "其他 App 的通知不會被讀取或保存。",
        "通知內容保存在這支手機上；只有你啟用 AI 功能時，規則看不懂的通知文字會傳給你設定的 AI 服務（Groq 或 Gemini）判讀，不會傳給其他人。",
        "你可以隨時在系統設定關閉這項權限。",
    ) else "允許讀取錢包 App 畫面（無障礙服務）" to listOf(
        "帳理會使用 Android 無障礙服務，在你打開「錢包畫面讀取」中勾選的錢包 App（例如交易紀錄頁）時，讀取畫面上的文字並整理成交易紀錄。",
        "系統只會把你勾選的 App 的畫面交給帳理；其他 App 一律不讀取。帳理不會操作畫面、點擊或輸入任何內容。",
        "讀到的內容保存在這支手機上；只有你啟用 AI 功能時，規則看不懂的畫面文字會傳給你設定的 AI 服務（Groq 或 Gemini）整理。",
        "你可以隨時在系統的無障礙設定關閉這項服務。",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                body.forEach { Text(it, fontSize = 14.sp) }
            }
        },
        confirmButton = { TextButton(onClick = { onDismiss(); onAgree() }) { Text("同意並前往設定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("不同意") } },
    )
}
