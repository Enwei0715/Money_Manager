package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
    AlertDialog(
        onDismissRequest = { if (progress == null) release = null },
        title = { Text("有新版本 ${r.tag}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("目前版本 ${BuildConfig.VERSION_NAME}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.notes.isNotBlank()) Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) { Text(r.notes, fontSize = 13.sp) }
                progress?.let { p ->
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Text("下載中 ${"%.0f".format(p * 100)}%", fontSize = 12.sp)
                }
                error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = progress == null, onClick = {
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
            }) { Text("立即更新") }
        },
        dismissButton = {
            Column {
                TextButton(enabled = progress == null, onClick = { release = null }) { Text("稍後") }
                TextButton(enabled = progress == null, onClick = {
                    repo.settings.update { it.copy(updateSkipped = r.versionCode) }
                    release = null
                }) { Text("略過這版") }
            }
        },
    )
}
