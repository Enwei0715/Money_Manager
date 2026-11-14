package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text

/**
 * 隱私權政策：正式條文放在 App 內（assets/legal），不連到外部網頁。
 * 條文格式：「# 」標題、「@ 」生效日期、「## 」條次、「- 」列點，其餘為段落。
 */
@Composable
fun PrivacyScreen(nav: NavController) {
    val ctx = LocalContext.current
    val english = com.enwei.moneymanager.ui.I18n.english
    val lines = remember(english) {
        runCatching {
            ctx.assets.open(if (english) "legal/privacy_en.txt" else "legal/privacy_zh.txt").bufferedReader().readLines()
        }.getOrDefault(emptyList())
    }
    val title = lines.firstOrNull { it.startsWith("# ") }?.removePrefix("# ") ?: "隱私權政策"
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BackTopBar("隱私權政策", { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, fontSize = AppText.title, fontWeight = FontWeight.SemiBold)
            lines.forEach { l ->
                when {
                    l.isBlank() || l.startsWith("# ") -> Unit
                    l.startsWith("@ ") -> Text(l.removePrefix("@ "), fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    l.startsWith("## ") -> Text(l.removePrefix("## "), fontSize = AppText.body, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 12.dp))
                    l.startsWith("- ") -> Row(Modifier.padding(start = 4.dp)) {
                        Text("・", fontSize = AppText.secondary)
                        Text(l.removePrefix("- "), fontSize = AppText.secondary)
                    }
                    else -> Text(l, fontSize = AppText.secondary)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 回報問題：先在 App 內寫問題、選要不要附上裝置資訊與閃退紀錄（看得到內容），
 * 再選傳送方式。開發者沒有伺服器，回報是透過你選的 App（Email、LINE…）或 GitHub 傳出去。
 */
@Composable
fun ReportScreen(nav: NavController) {
    val ctx = LocalContext.current
    val kinds = listOf("錯誤或閃退", "資料不正確", "建議", "其他")
    var kind by rememberSaveable { mutableStateOf(0) }
    var desc by rememberSaveable { mutableStateOf("") }
    var attach by rememberSaveable { mutableStateOf(true) }
    val diag = remember { problemDiagnostics(ctx) }
    fun body() = buildString {
        appendLine("【${com.enwei.moneymanager.ui.tr(kinds[kind])}】")
        appendLine(desc.trim())
        if (attach) { appendLine(); appendLine("——"); append(diag) }
    }
    val subject = "帳理問題回報 ${BuildConfig.VERSION_NAME}"
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        BackTopBar("回報問題", { nav.popBackStack() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("類型", fontSize = AppText.secondary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEachIndexed { i, k ->
                    val on = i == kind
                    Text(k, fontSize = AppText.secondary, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .border(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                            .clickable { kind = i }.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
            OutlinedTextField(desc, { desc = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                label = { Text("發生了什麼事？在哪個畫面、做了什麼") })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("附上裝置資訊與閃退紀錄", fontSize = AppText.body)
                    Text("不含任何帳務資料", fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(attach, { attach = it })
            }
            if (attach) Text(diag, fontSize = AppText.label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 8,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)).padding(10.dp))
            Spacer(Modifier.height(4.dp))
            // 傳送方式：GitHub（公開，開發者直接收到）或用手機上的 App 傳送
            Button(enabled = desc.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                val url = "https://github.com/${BuildConfig.UPDATE_REPO}/issues/new?title=" + android.net.Uri.encode(subject) +
                    "&body=" + android.net.Uri.encode(body().take(6000))
                runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            }) { Text("在 GitHub 回報（公開）") }
            OutlinedButton(enabled = desc.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
                    putExtra(android.content.Intent.EXTRA_TEXT, body())
                }
                runCatching { ctx.startActivity(android.content.Intent.createChooser(send, com.enwei.moneymanager.ui.tr("選擇傳送方式"))) }
            }) { Text("用其他 App 傳送（Email、LINE…）") }
        }
    }
}

/** 閃退後的提示視窗用：直接把裝置資訊與閃退紀錄交給分享選單（那個視窗已說明是閃退紀錄）。 */
fun shareProblemReport(ctx: android.content.Context) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, "帳理問題回報 ${BuildConfig.VERSION_NAME}")
        putExtra(android.content.Intent.EXTRA_TEXT, "問題描述：\n\n——\n" + problemDiagnostics(ctx))
    }
    runCatching {
        ctx.startActivity(android.content.Intent.createChooser(send, com.enwei.moneymanager.ui.tr("選擇傳送方式")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
