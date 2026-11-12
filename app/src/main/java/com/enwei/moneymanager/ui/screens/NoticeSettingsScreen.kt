package com.enwei.moneymanager.ui.screens

import com.enwei.moneymanager.ui.AppText

import android.app.NotificationManager
import androidx.compose.material.icons.filled.MoreVert
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.notify.AppNotices
import com.enwei.moneymanager.notify.NoticeKind
import com.enwei.moneymanager.ui.components.BackTopBar
import com.enwei.moneymanager.ui.components.Text
import com.enwei.moneymanager.ui.tr

/**
 * 通知設定：依「記帳／投資／系統」分組列出 App 會發的每一種通知，
 * 每種可個別開關、試發一則看樣子、打開系統頁調整聲音與震動。上層功能關閉的種類不顯示。
 */
@Composable
fun NoticeSettingsScreen(nav: NavController) {
    val ctx = LocalContext.current
    val s by MoneyApp.repo.settings.flow.collectAsStateWithLifecycle()
    // 從系統設定回來時重新讀取（系統總開關、頻道狀態）
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val nm = remember { ctx.getSystemService(NotificationManager::class.java) }
    val systemOn = remember(tick) { nm.areNotificationsEnabled() }
    fun channelBlocked(k: NoticeKind) = nm.getNotificationChannel(k.channel)?.importance == NotificationManager.IMPORTANCE_NONE

    Scaffold(topBar = { BackTopBar("通知", { nav.popBackStack() }) }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!systemOn) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("系統已關閉帳理的通知", fontSize = AppText.secondary, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }) { Text("開啟") }
                }
            }
            AppNotices.groups.forEach { (gid, glabel) ->
                val kinds = NoticeKind.entries.filter { it.group == gid && it.available(s) }
                if (kinds.isEmpty()) return@forEach
                Text(glabel, fontSize = AppText.secondary, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp))
                com.enwei.moneymanager.ui.components.AppCard(Modifier.fillMaxWidth()) {
                    Column {
                        kinds.forEachIndexed { i, k ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val on = k.enabled(s)
                            val blocked = remember(tick) { channelBlocked(k) }
                            fun toggle(v: Boolean) = MoneyApp.repo.settings.update { st ->
                                val off = if (v) st.noticeOff - k.name else st.noticeOff + k.name
                                // 股價提醒：關掉時連背景檢查也停
                                if (k == NoticeKind.STOCK) st.copy(noticeOff = off - k.name, stockAlertsOn = v) else st.copy(noticeOff = off)
                            }
                            // 一行：名稱（系統關閉時下方紅字）＋更多（試發、聲音與震動）＋開關
                            var menu by remember { mutableStateOf(false) }
                            Row(Modifier.fillMaxWidth().clickable { toggle(!on) }.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(k.label, fontSize = AppText.section)
                                    if (blocked) Text("系統已關閉這一類", fontSize = AppText.caption, color = MaterialTheme.colorScheme.error)
                                }
                                androidx.compose.foundation.layout.Box {
                                    androidx.compose.material3.IconButton(onClick = { menu = true }) {
                                        androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.MoreVert, "更多",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    androidx.compose.material3.DropdownMenu(menu, { menu = false }) {
                                        if (on && systemOn && !blocked) androidx.compose.material3.DropdownMenuItem(text = { Text("試發一則") },
                                            onClick = { menu = false; sample(ctx, k) })
                                        androidx.compose.material3.DropdownMenuItem(text = { Text("聲音與震動") }, onClick = {
                                            menu = false
                                            ctx.startActivity(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, k.channel))
                                        })
                                    }
                                }
                                Switch(on, { toggle(it) })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 試發一則範例，看實際的版型；點了打開的畫面和真的通知一樣。 */
private fun sample(ctx: android.content.Context, k: NoticeKind) {
    val id = 9900 + k.ordinal
    when (k) {
        NoticeKind.RECORDED -> AppNotices.post(ctx, k, id, tr("支出") + " $ 140・" + tr("範例店家"), tr("餐飲") + " · LINE Pay", "txns",
            listOf(tr("分類") + "：" + tr("餐飲"), tr("帳戶") + "：LINE Pay", tr("預算") + "：" + tr("總預算") + " " + tr("已用") + " 8%"))
        NoticeKind.BUDGET -> AppNotices.post(ctx, k, id, tr("餐飲 已用 80% 預算"), "$ 4,000 / $ 5,000・" + tr("剩") + " $ 1,000", "review")
        NoticeKind.BILLS -> AppNotices.post(ctx, k, id, tr("借貸提醒"), tr("範例：3 天後要還款"), "accounts")
        NoticeKind.STOCK -> AppNotices.post(ctx, k, id, tr("範例：2330 跌破停利價"), tr("點選看 K 線與紀律"), "invest")
        NoticeKind.REPORT -> AppNotices.post(ctx, k, id, tr("今日投資報告已更新"), tr("點選看完整報告"), "advisor:0")
        NoticeKind.PROGRESS -> AppNotices.post(ctx, k, id, tr("產生投資報告中"), tr("範例進度"), "advisor:0")
        NoticeKind.UPDATE -> AppNotices.post(ctx, k, id, tr("有新版本"), tr("點選前往更新"), "settings")
        NoticeKind.BACKUP -> AppNotices.post(ctx, k, id, tr("自動備份失敗"), tr("備份資料夾無法存取，請重新選擇"), "backup")
    }
}
