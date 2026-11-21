package com.enwei.moneymanager.notify

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.R
import com.enwei.moneymanager.data.Repository
import com.enwei.moneymanager.ui.MainActivity
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 監聽銀行、信用卡、電子支付、券商 App 的推播並自動記帳。 */
class TxnNotificationListener : NotificationListenerService() {
    private val pendingLock = Any()
    private val pendingNotes = LinkedHashMap<String, ActiveNote>()
    private var flushJob: Job? = null
    private var firstPendingAt = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        // 服務被系統暫停期間進來的通知收不到：重新連上時補讀通知列上還在的（近 24 小時、還沒有紀錄的）
        val app = application as MoneyApp
        if (!app.repo.settings.value.featureNotif) return
        app.appScope.launch {
            delay(2000)
            val since = System.currentTimeMillis() - 24 * 3600_000L
            val list = runCatching { activeNotifications }.getOrNull().orEmpty()
                .filter { it.packageName != packageName && it.postTime >= since }
            val log = app.repo.db.notificationLog()
            list.forEach { sbn ->
                val (title, text) = textOf(sbn) ?: return@forEach
                if (log.countSame(sbn.packageName, title, text, sbn.postTime - 60_000) == 0) enqueue(sbn.packageName, sbn.key, title, text, sbn.postTime)
            }
        }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
        // 被系統中斷時要求重新連線，否則之後的通知都收不到
        runCatching { requestRebind(ComponentName(this, TxnNotificationListener::class.java)) }
    }

    override fun onDestroy() {
        synchronized(pendingLock) {
            flushJob?.cancel()
            flushJob = null
            pendingNotes.clear()
        }
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** 標題與內文（長內文、多行內文都讀，沒有時用跑馬燈文字） */
    private fun textOf(sbn: StatusBarNotification): Pair<String?, String?>? {
        val n = sbn.notification ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.takeIf { it.isNotBlank() }
            ?: lines?.takeIf { it.isNotBlank() } ?: n.tickerText?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null
        return title to text
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        // 設定裡關掉「通知自動記帳」：不讀任何通知
        if (!com.enwei.moneymanager.MoneyApp.repo.settings.value.featureNotif) return
        val (title, text) = textOf(sbn) ?: return
        // App 更新同一則通知（同一個通知編號、內容沒變）：已經處理過，略過。編號不同的是另一則，照常處理
        val sig = "$title\u0000$text"
        synchronized(recentKeys) {
            if (recentKeys[sbn.key] == sig) return
            recentKeys[sbn.key] = sig
            if (recentKeys.size > 300) recentKeys.remove(recentKeys.keys.first())
        }
        enqueue(sbn.packageName, sbn.key, title, text, sbn.postTime)
    }
    private val recentKeys = LinkedHashMap<String, String>()

    private fun enqueue(pkg: String, key: String, title: String?, text: String?, postTime: Long) {
        val app = application as MoneyApp
        synchronized(pendingLock) {
            pendingNotes["$pkg:$key:$postTime"] = ActiveNote(pkg, title, text, postTime)
            // 等 0.9 秒收齊同一筆交易的多則通知；但通知一直進來時，最多等 3 秒或 20 則就先處理，不會一直延後
            if (firstPendingAt == 0L) firstPendingAt = System.currentTimeMillis()
            val waited = System.currentTimeMillis() - firstPendingAt
            val wait = if (pendingNotes.size >= 20 || waited >= 3000) 0L else 900L
            // 只取消「還在等」的那次；已經開始處理的批次不能取消（原本新通知一進來就把處理到一半的批次取消，那幾則就不見了）
            flushJob?.cancel()
            flushJob = app.appScope.launch {
                delay(wait)
                val batch = synchronized(pendingLock) {
                    pendingNotes.values.toList().also {
                        pendingNotes.clear()
                        flushJob = null
                        firstPendingAt = 0L
                    }
                }
                if (batch.isEmpty()) return@launch
                app.appScope.launch { process(app, batch) }
            }
        }
    }

    private suspend fun process(app: MoneyApp, batch: List<ActiveNote>) {
        val results = try {
            app.repo.ingestNotificationBatch(batch.map { Repository.NotificationInput(it.pkg, it.title, it.text, it.postedAt) })
        } catch (e: Exception) {
            android.util.Log.e("TxnNotificationListener", "Failed to process notification batch", e)
            // 整批失敗也要留下紀錄（通知記帳頁可點選重試），不能整批消失
            runCatching {
                batch.forEach { n -> app.repo.db.notificationLog().insert(com.enwei.moneymanager.data.NotificationLogEntity(
                    packageName = n.pkg, title = n.title, text = n.text, postedAt = n.postedAt, result = "ERROR")) }
            }
            return
        }
        results.filterNotNull().forEach { result ->
            if (result.kind == "RECORDED") app.repo.scheduleAutoTidy()
            // 有網路且有把握的直接記帳；沒網路或不確定的在「待確認」
            val pending = result.kind == "RECORDED" && result.pending
            QuickActions.notifyRecorded(
                this@TxnNotificationListener, result.txnId.takeIf { result.kind != "STOCK" }, tr(result.message), pending,
            )
        }
    }

    /** 通知列上目前的一則通知（用於測試辨識）。 */
    data class ActiveNote(val pkg: String, val title: String?, val text: String?, val postedAt: Long)

    companion object {
        @Volatile private var instance: TxnNotificationListener? = null

        /** 目前通知列上的通知（不含本 App 與群組摘要）；服務未連線時回傳 null。 */
        fun activeNotes(): List<ActiveNote>? {
            val svc = instance ?: return null
            val list = runCatching { svc.activeNotifications }.getOrNull() ?: return null
            return list.filter { it.packageName != svc.packageName && (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
                .mapNotNull { sbn ->
                    val ex = sbn.notification.extras
                    val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString()
                    if (title.isNullOrBlank() && text.isNullOrBlank()) null else ActiveNote(sbn.packageName, title, text, sbn.postTime)
                }
                .sortedByDescending { it.postedAt }
        }

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, TxnNotificationListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun openSettings(context: Context) {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
