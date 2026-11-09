package com.enwei.moneymanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.data.geminiKeys
import com.enwei.moneymanager.data.groqKeys
import com.enwei.moneymanager.data.hasAiKey
import com.enwei.moneymanager.net.GooglePlaces
import com.enwei.moneymanager.net.LocalAi
import com.enwei.moneymanager.net.Mapbox
import com.enwei.moneymanager.net.ShizukuHelper
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/**
 * 智慧功能：每項功能目前用的是「基本」（不用任何設定）還是「加強」（有金鑰或權限），以及怎麼升級。
 * 不設定也能完整使用；設定了只是更準、更自動。
 */
@Composable
fun SmartScreen(nav: NavController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repo.settings.flow.collectAsStateWithLifecycle()
    // 從別的 App（設定、Shizuku）回來時重新讀狀態
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    val prefs = remember { ctx.getSharedPreferences("smart", android.content.Context.MODE_PRIVATE) }
    var shizukuMsg by remember { mutableStateOf<String?>(null) }
    fun open(url: String) = runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }

    @Suppress("UNUSED_VARIABLE") val t = tick
    val listenerOn = com.enwei.moneymanager.notify.TxnNotificationListener.isEnabled(ctx)
    val needSensitive = android.os.Build.VERSION.SDK_INT >= 35
    val sensitiveOk = prefs.getBoolean("sensitive_ok", false)
    val aiCloud = settings.groqKeys.isNotEmpty() || settings.geminiKeys.isNotEmpty()
    val locOn = com.enwei.moneymanager.data.PlaceHints.hasPermission(ctx)

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("智慧功能", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(8.dp))

            // ① 通知記帳
            Feature(
                "通知自動記帳", listenerOn,
                if (!listenerOn) "未開啟" else if (needSensitive && !sensitiveOk) "基本：部分銀行通知可能被隱藏" else "完整",
            ) {
                if (!listenerOn) Action("開啟通知存取") {
                    runCatching { ctx.startActivity(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                }
                if (listenerOn && needSensitive && !sensitiveOk) {
                    Hint("Android 15 起，部分通知內容會被系統隱藏。用 Shizuku（不用電腦）可以一鍵開啟。")
                    when {
                        !ShizukuHelper.running -> {
                            Step("1. 安裝 Shizuku", "安裝") { open(ShizukuHelper.PLAY_URL) }
                            Step("2. 打開 Shizuku，用「無線偵錯」啟動（照它的說明配對一次）", null) {}
                            Step("3. 回到這裡按「一鍵開啟」", null) {}
                        }
                        else -> Action("一鍵開啟讀取敏感通知") {
                            scope.launch {
                                shizukuMsg = "處理中…"
                                if (!ShizukuHelper.granted && !ShizukuHelper.requestPermission()) { shizukuMsg = "需要在 Shizuku 允許帳理"; return@launch }
                                val err = ShizukuHelper.allowSensitiveNotifications()
                                if (err == null) { prefs.edit().putBoolean("sensitive_ok", true).apply(); tick++; shizukuMsg = "已開啟" } else shizukuMsg = err
                            }
                        }
                    }
                    shizukuMsg?.let { Hint(it) }
                }
            }

            // ② AI
            Feature(
                "AI 判讀與分析", settings.aiEnabled && settings.hasAiKey,
                when {
                    !settings.aiEnabled || !settings.hasAiKey -> "基本：用規則辨識通知與分類"
                    LocalAi.enabled && aiCloud -> "加強：自架伺服器＋雲端備援"
                    LocalAi.enabled -> "加強：自架伺服器（免費）"
                    else -> "加強：雲端 AI"
                },
            ) {
                if (!settings.hasAiKey) Hint("沒有 AI 也能記帳；有 AI 時看不懂的通知、店名整理與股票報告會更聰明。")
                Action(if (settings.hasAiKey) "AI 設定" else "設定 AI（雲端金鑰或自架伺服器）") { nav.navigate("ai") }
            }

            // ③ 店家搜尋
            Feature(
                "店家搜尋", GooglePlaces.enabled || Mapbox.enabled,
                when { GooglePlaces.enabled -> "加強：Google Places"; Mapbox.enabled -> "加強：Mapbox"; else -> "基本：自己記過的店＋OpenStreetMap" },
            ) {
                if (!GooglePlaces.enabled) Hint("記過一次位置的店之後都會自動推薦；設定 Google Places 後，新的店也找得到。")
                Action(if (GooglePlaces.enabled) "店家位置" else "設定 Google Places") { nav.navigate("merchants") }
            }

            // ④ 地圖
            Feature("支出地圖底圖", Mapbox.enabled, if (Mapbox.enabled) "加強：Mapbox" else "基本：OpenStreetMap") {
                if (!Mapbox.enabled) Action("設定 Mapbox") { nav.navigate("merchants") }
            }

            // ⑤ 定位
            Feature("記帳定位", locOn, if (locOn) "已開啟" else "未開啟：不會推薦附近的店") {
                if (!locOn) Action("開啟位置權限") {
                    runCatching {
                        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", ctx.packageName, null)))
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 一項功能：名稱、目前等級（加強的用主題色），下面是升級的動作 */
@Composable
private fun Feature(title: String, strong: Boolean, level: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = AppText.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(level, fontSize = AppText.caption, maxLines = 1,
                color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Hint(text: String) = Text(text, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) { Text(label) }
}

@Composable
private fun Step(text: String, button: String?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = AppText.secondary, modifier = Modifier.weight(1f))
        if (button != null) TextButton(onClick = onClick) { Text(button) }
    }
}
