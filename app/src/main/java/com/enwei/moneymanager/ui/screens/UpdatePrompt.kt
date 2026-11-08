package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.net.ReleaseInfo
import com.enwei.moneymanager.net.Updater
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/** 上次檢查的時間：每次回到 App 都會檢查，但 10 分鐘內不重複 */
private var lastCheck = 0L

/**
 * 開啟或回到 App 時檢查 GitHub 上的新版；有新版且沒被略過就跳出視窗，
 * 可以直接下載安裝、稍後再說，或略過這一版（下一版出來會再提醒）。
 */
@Composable
fun UpdatePrompt() {
    // Google Play 版的更新由 Play 負責
    if (!com.enwei.moneymanager.BuildConfig.SELF_UPDATE) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var release by remember { mutableStateOf<ReleaseInfo?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    fun check() {
        val now = System.currentTimeMillis()
        if (release != null || checking || now - lastCheck < 10 * 60_000) return
        checking = true
        scope.launch {
            try {
                val s = repo.settings.value
                if (!s.notifyUpdates) return@launch
                val time = java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date())
                val res = runCatching { Updater.latest(s.githubToken) }
                val r = res.getOrNull()
                val log = when {
                    r == null -> "$time 檢查失敗：" + (res.exceptionOrNull()?.message ?: "找不到可下載的版本")
                    !Updater.isNewer(r) -> "$time 已是最新版本"
                    r.versionCode == s.updateSkipped -> "$time 有新版本 ${r.tag}（已略過）"
                    else -> "$time 有新版本 ${r.tag}"
                }
                repo.settings.update { it.copy(updateCheckLog = log, updateAvailable = if (r != null && Updater.isNewer(r)) r.versionCode else 0) }
                // 只有真的查到結果才算檢查過；失敗（沒網路等）下次回到 App 再試
                if (r != null) lastCheck = System.currentTimeMillis()
                if (r != null && Updater.isNewer(r) && r.versionCode != s.updateSkipped) release = r
            } finally {
                checking = false
            }
        }
    }
    // App 從背景回到前景（不一定會重新啟動）時也要檢查
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) check() }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val r = release ?: return
    // 更新說明是累積的：拆成「【標題】＋條列」的段落，最新的在最後；預設只顯示最新 3 段，其餘按「看全部」
    val sections = remember(r.notes) { parseNotes(r.notes) }
    var showAll by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (progress == null) release = null },
        icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("有新版本 ${r.tag}", fontSize = AppText.title)
                Text("目前 ${BuildConfig.VERSION_NAME}" + (r.sizeBytes.takeIf { it > 0 }?.let { " · 約 ${"%.1f".format(it / 1_048_576.0)} MB" } ?: ""),
                    fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val list = sections.asReversed().let { if (showAll) it else it.take(3) }
                Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    list.forEach { (title, lines) ->
                        androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = androidx.compose.foundation.shape.RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                if (title.isNotBlank()) Text(title, fontSize = AppText.body, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                lines.forEach { l -> Text("• " + l.removePrefix("・").removePrefix("-").trim(), fontSize = AppText.caption, lineHeight = 17.sp) }
                            }
                        }
                    }
                }
                if (sections.size > 3) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "只看最新" else "看全部更新說明（${sections.size} 段）", fontSize = AppText.caption) }
                progress?.let { p ->
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Text("下載中 ${"%.0f".format(p * 100)}%", fontSize = AppText.caption)
                }
                error?.let { Text(it, fontSize = AppText.secondary, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Button(enabled = progress == null, modifier = Modifier.fillMaxWidth(), onClick = {
                    error = null
                    if (!Updater.canInstall(ctx)) {
                        error = "請允許「安裝不明應用程式」後再按一次更新"
                        Updater.openInstallPermission(ctx)
                        return@Button
                    }
                    scope.launch {
                        progress = 0f
                        runCatching { Updater.download(ctx, r, repo.settings.value.githubToken) { p -> progress = p } }
                            .onSuccess { f -> progress = null; Updater.install(ctx, f) }
                            .onFailure { progress = null; error = "下載失敗：${it.message}" }
                    }
                }) { Text(if (progress != null) "下載中…" else "立即更新") }
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = progress == null, onClick = {
                        repo.settings.update { it.copy(updateSkipped = r.versionCode) }
                        release = null
                    }) { Text("略過這版", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    TextButton(enabled = progress == null, onClick = { release = null }) { Text("稍後") }
                }
            }
        },
    )
}

/** 更新說明拆段：以「【…】」開頭的行是段落標題，其餘非空行是條列。 */
internal fun parseNotes(notes: String): List<Pair<String, List<String>>> {
    val out = ArrayList<Pair<String, MutableList<String>>>()
    notes.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { l ->
        if (l.startsWith("【")) out += l.trim('【', '】') to mutableListOf()
        else if (out.isEmpty()) { if (!l.contains("更新說明")) out += "" to mutableListOf(l) }
        else out.last().second += l
    }
    return out.filter { it.second.isNotEmpty() || it.first.isNotBlank() }
}
