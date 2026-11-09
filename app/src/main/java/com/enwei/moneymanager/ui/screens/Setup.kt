package com.enwei.moneymanager.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.enwei.moneymanager.data.AppSettings
import com.enwei.moneymanager.data.hasAiKey
import com.enwei.moneymanager.net.GooglePlaces
import com.enwei.moneymanager.net.Mapbox
import com.enwei.moneymanager.net.ShizukuHelper
import com.enwei.moneymanager.ui.AppText
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import kotlinx.coroutines.launch

/**
 * 功能需要的「一次性設定」：選了某個功能，就先把它需要的權限、金鑰設好。
 * - 新手導覽：依勾選的功能列出要做的設定；
 * - 交易頁上方：已開啟的功能少了必要設定（例如通知存取被系統關掉）時提醒一行，點一下補設定；
 * - 設定 → 功能設定檢查：隨時看全部的狀態。
 */
object Setup {
    /**
     * 一項設定。[feature]：哪個功能需要（notif 通知記帳、map 定位與地圖、ai AI、backup 自動備份）；
     * [required]：沒設好功能就不能用（會在交易頁提醒）；選用的只讓功能更好。
     */
    data class Item(val id: String, val feature: String, val title: String, val why: String, val required: Boolean, val action: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("smart", Context.MODE_PRIVATE)

    fun items(): List<Item> = buildList {
        add(Item("notif.listener", "notif", "允許讀取通知", "看得到付款通知才能自動記帳", true, "開啟"))
        if (android.os.Build.VERSION.SDK_INT >= 33) add(Item("notif.post", "notif", "允許 App 發通知", "記下一筆、待確認時通知你", false, "開啟"))
        if (android.os.Build.VERSION.SDK_INT >= 35) add(Item("notif.sensitive", "notif", "讀取被隱藏的通知內容", "Android 15 起部分銀行通知內容會被隱藏", false, "設定"))
        add(Item("map.location", "map", "允許使用位置", "記下付款地點、推薦附近的店", true, "開啟"))
        if (android.os.Build.VERSION.SDK_INT >= 29) add(Item("map.background", "map", "位置設為「一律允許」", "App 沒開著時，通知記帳也能記下地點", false, "設定"))
        add(Item("map.places", "map", "店家搜尋金鑰（Google Places）", "找店最準；不設也能用自己記過的店", false, "設定"))
        add(Item("ai.key", "ai", "AI 金鑰或自架伺服器", "有 AI 才能判讀複雜通知、產生報告", true, "設定"))
        add(Item("backup.folder", "backup", "自動備份的資料夾", "資料夾權限不見時，自動備份會停止", true, "重新選擇"))
    }

    fun done(ctx: Context, s: AppSettings, id: String): Boolean = when (id) {
        "notif.listener" -> com.enwei.moneymanager.notify.TxnNotificationListener.isEnabled(ctx)
        "notif.post" -> android.os.Build.VERSION.SDK_INT < 33 ||
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        "notif.sensitive" -> prefs(ctx).getBoolean("sensitive_ok", false)
        "map.location" -> com.enwei.moneymanager.data.PlaceHints.hasPermission(ctx)
        "map.background" -> com.enwei.moneymanager.data.PlaceHints.hasBackground(ctx)
        "map.places" -> GooglePlaces.enabled || Mapbox.enabled
        "ai.key" -> s.hasAiKey
        "backup.folder" -> s.autoBackupUri.isBlank() ||
            ctx.contentResolver.persistedUriPermissions.any { it.uri.toString() == s.autoBackupUri && it.isWritePermission }
        else -> true
    }

    /** 目前開著的功能 */
    fun features(s: AppSettings): Set<String> = buildSet {
        if (s.featureNotif) add("notif"); if (s.locationHints) add("map"); if (s.aiEnabled) add("ai")
        if (s.autoBackupUri.isNotBlank()) add("backup")
    }

    /** 已開啟的功能裡，少了的必要設定（交易頁提醒用） */
    fun missing(ctx: Context, s: AppSettings): List<Item> = items().filter { it.required && it.feature in features(s) && !done(ctx, s, it.id) }

    val featureName = mapOf("notif" to "通知自動記帳", "map" to "定位與支出地圖", "ai" to "AI", "backup" to "自動備份")

    /** 執行一項設定的動作：系統設定頁直接打開；App 內的頁面交給 [open] */
    fun act(ctx: Context, id: String, open: (String) -> Unit) {
        fun start(i: android.content.Intent) = runCatching { ctx.startActivity(i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
        val appDetails = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", ctx.packageName, null))
        when (id) {
            "notif.listener" -> start(android.content.Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
            "notif.post" -> start(android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            "map.location", "map.background" -> start(appDetails)
            "map.places" -> open("merchants")
            "ai.key" -> open("ai")
            "backup.folder" -> open("backup")
        }
    }
}

/** 從其他 App（系統設定、Shizuku）回來時，[content] 會重新讀狀態 */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    return tick
}

/**
 * 設定清單：[features] 這些功能需要的設定，一項一行（已完成打勾、沒完成有按鈕）。
 * 新手導覽與「功能設定檢查」共用。[open]：打開 App 內的頁面。
 */
@Composable
fun SetupList(features: Set<String>, open: (String) -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    var shizukuOpen by remember { mutableStateOf(false) }
    var shizukuMsg by remember { mutableStateOf<String?>(null) }
    @Suppress("UNUSED_VARIABLE") val t = tick
    val groups = Setup.items().filter { it.feature in features }.groupBy { it.feature }
    Column {
        groups.forEach { (f, list) ->
            Text(Setup.featureName[f] ?: f, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            list.forEach { item ->
                val ok = Setup.done(ctx, s, item.id)
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(if (ok) "✓" else if (item.required) "●" else "○", fontSize = AppText.body,
                        color = if (ok) MaterialTheme.colorScheme.primary else if (item.required) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(26.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title + if (!item.required) "（選用）" else "", fontSize = AppText.body)
                        Text(item.why, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!ok) TextButton(onClick = {
                        if (item.id == "notif.sensitive") shizukuOpen = !shizukuOpen else Setup.act(ctx, item.id, open)
                    }) { Text(item.action) }
                }
                // 讀取被隱藏的通知：用 Shizuku（不用電腦）
                if (item.id == "notif.sensitive" && shizukuOpen && !ok) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                    .padding(start = 42.dp, end = 16.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (!ShizukuHelper.running) {
                        StepLine("1. 安裝 Shizuku", "安裝") {
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(ShizukuHelper.PLAY_URL))) }
                        }
                        StepLine("2. 打開 Shizuku，用「無線偵錯」啟動（照它的說明配對一次）", null) {}
                        StepLine("3. 回到這裡按「一鍵開啟」", null) {}
                    } else StepLine("Shizuku 已啟動", "一鍵開啟") {
                        scope.launch {
                            shizukuMsg = "處理中…"
                            if (!ShizukuHelper.granted && !ShizukuHelper.requestPermission()) { shizukuMsg = "需要在 Shizuku 允許帳理"; return@launch }
                            val err = ShizukuHelper.allowSensitiveNotifications()
                            if (err == null) { ctx.getSharedPreferences("smart", Context.MODE_PRIVATE).edit().putBoolean("sensitive_ok", true).apply(); shizukuMsg = "已開啟" }
                            else shizukuMsg = err
                        }
                    }
                    shizukuMsg?.let { Text(it, fontSize = AppText.caption, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StepLine(text: String, button: String?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, fontSize = AppText.secondary, modifier = Modifier.weight(1f))
        if (button != null) TextButton(onClick = onClick) { Text(button) }
    }
}

/** 設定 → 功能設定檢查：已開啟的功能需要的設定，全部列出來 */
@Composable
fun SetupScreen(nav: NavController) {
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar("功能設定檢查", { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            val f = Setup.features(s)
            if (f.isEmpty()) Text("目前沒有開啟需要設定的功能（通知記帳、定位、AI、自動備份）", fontSize = AppText.secondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            SetupList(f) { nav.navigate(it) }
            Spacer(Modifier.height(32.dp))
        }
    }
}

/** 交易頁上方：已開啟的功能少了必要設定時提醒一行（例如通知存取被系統關掉）；✕ 這次打開 App 期間不再顯示 */
object SetupBannerState { var dismissed by mutableStateOf(setOf<String>()) }

@Composable
fun SetupBanner(nav: NavController) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val s by repo.settings.flow.collectAsStateWithLifecycle()
    val tick = rememberResumeTick()
    @Suppress("UNUSED_VARIABLE") val t = tick
    val item = Setup.missing(ctx, s).firstOrNull { it.id !in SetupBannerState.dismissed } ?: return
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).clickable { Setup.act(ctx, item.id) { nav.navigate(it) } }
        .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${Setup.featureName[item.feature]}：需要${item.title}", fontSize = AppText.secondary, maxLines = 1, modifier = Modifier.weight(1f))
        TextButton(onClick = { Setup.act(ctx, item.id) { nav.navigate(it) } }) { Text(item.action) }
        Text("✕", color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { SetupBannerState.dismissed = SetupBannerState.dismissed + item.id }.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
