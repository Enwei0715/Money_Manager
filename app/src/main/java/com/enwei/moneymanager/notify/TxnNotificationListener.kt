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
import com.enwei.moneymanager.ui.MainActivity
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.launch

/** 監聽銀行、信用卡、電子支付、券商 App 的推播並自動記帳。 */
class TxnNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return
        handle(this, sbn.packageName, title, text, sbn.postTime)
    }

    /** 通知列上目前的一則通知（用於測試辨識）。 */
    data class ActiveNote(val pkg: String, val title: String?, val text: String?, val postedAt: Long)

    companion object {
        @Volatile private var instance: TxnNotificationListener? = null

        /** 各 App 最近一次成功從通知記帳的時間（通知監聽與無障礙兩個來源共用）。 */
        private val lastRecorded = java.util.concurrent.ConcurrentHashMap<String, Long>()

        /**
         * 處理一則通知（通知監聽或無障礙服務收到的）。內容被 Android 15 隱藏時，
         * 等幾秒看另一個來源有沒有讀到；都沒有就跳提醒，點了直接開新增畫面。
         */
        fun handle(context: Context, pkg: String, title: String?, text: String?, postedAt: Long) {
            val app = MoneyApp.instance
            val repo = app.repo
            if (repo.isRedacted(text)) {
                val s = repo.settings.value
                if (pkg !in s.notifPackages && !s.notifAccountMap.containsKey(pkg)) return
                app.appScope.launch {
                    kotlinx.coroutines.delay(8000)
                    if ((lastRecorded[pkg] ?: 0L) >= postedAt - 60_000) return@launch
                    notifyHidden(context, pkg, title)
                }
                return
            }
            app.appScope.launch {
                val result = runCatching { repo.ingestNotification(pkg, title, text, postedAt) }.getOrNull() ?: return@launch
                lastRecorded[pkg] = System.currentTimeMillis()
                val pending = !repo.settings.value.notifAutoConfirm && result.kind == "RECORDED"
                QuickActions.notifyRecorded(context, result.txnId.takeIf { result.kind != "STOCK" }, tr(result.message), pending)
            }
        }

        private fun notifyHidden(context: Context, pkg: String, title: String?) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            val label = runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrNull() ?: title ?: pkg
            val account = MoneyApp.repo.settings.value.notifAccountMap[pkg] ?: 0L
            val open = PendingIntent.getActivity(
                context, pkg.hashCode(),
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "txn/0?type=EXPENSE&account=$account"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                9200 + (pkg.hashCode() and 0xff),
                NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                    .setSmallIcon(R.drawable.ic_stat_ledger)
                    .setContentTitle(tr("$label 有一則通知被系統隱藏"))
                    .setContentText(tr("點此記帳（帳戶已帶入，只要填金額）"))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }

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

        private fun notifyRecorded(context: Context, message: String, kind: String) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            val pending = MoneyApp.repo.settings.value.notifAutoConfirm.not() && kind == "RECORDED"
            val intent = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, if (pending) "pending" else "home"),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                .setSmallIcon(R.drawable.ic_stat_ledger)
                .setContentTitle(if (pending) "已記下，待確認" else "已自動記帳")
                .setContentText(message)
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
        }
    }
}
