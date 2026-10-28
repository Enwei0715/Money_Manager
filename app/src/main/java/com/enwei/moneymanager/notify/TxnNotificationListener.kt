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
import kotlinx.coroutines.launch

/** 監聽銀行、信用卡、電子支付、券商 App 的推播並自動記帳。 */
class TxnNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        if (title.isNullOrBlank() && text.isNullOrBlank()) return

        val app = application as MoneyApp
        app.appScope.launch {
            val result = runCatching { app.repo.ingestNotification(sbn.packageName, title, text, sbn.postTime) }.getOrNull() ?: return@launch
            notifyRecorded(this@TxnNotificationListener, result.message, result.kind)
        }
    }

    companion object {
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(if (pending) "已記下，待確認" else "已自動記帳")
                .setContentText(message)
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
            nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), n)
        }
    }
}
