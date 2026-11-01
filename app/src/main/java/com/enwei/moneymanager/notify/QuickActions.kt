package com.enwei.moneymanager.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.R
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.MainActivity
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.launch

/**
 * 通知記帳後的「已記帳」通知：直接在通知上一鍵改分類（最常用的兩個）或輸入說明，
 * 不必打開 App 也能標清楚這筆是什麼花費。點通知本身可開啟完整編輯。
 */
object QuickActions {
    private const val ACTION_CATEGORY = "com.enwei.moneymanager.SET_CATEGORY"
    private const val ACTION_NOTE = "com.enwei.moneymanager.ADD_NOTE"
    private const val EXTRA_TXN = "txn"
    private const val EXTRA_CAT = "cat"
    const val KEY_NOTE = "note"

    private fun notifId(txnId: Long) = (txnId % 100_000).toInt() + 20_000

    /**
     * 每一筆自動記帳都發一則通知，內容和帳理裡記下的一樣：日期、金額、分類、帳戶、內容（與說明）。
     * 支出、收入可直接在通知上改分類或輸入說明；改完會用同一則通知更新內容。
     */
    suspend fun notifyRecorded(context: Context, txnId: Long?, message: String, pending: Boolean, silent: Boolean = false) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        val repo = MoneyApp.repo
        val t = txnId?.let { repo.getTxn(it) }
        val open = PendingIntent.getActivity(
            context, (txnId ?: 0L).toInt(),
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ROUTE, if (t != null) "txn/${t.id}" else if (pending) "pending" else "home"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val b = NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(silent)
        if (t == null) {
            // 股票成交等不是一般交易的紀錄：只顯示訊息
            b.setContentTitle(tr(if (pending) "已記下，待確認" else "已自動記帳")).setContentText(message)
            nm.notify(notifId(txnId ?: System.currentTimeMillis()), b.build())
            return
        }
        val cats = repo.db.categories().all().associateBy { it.id }
        val accs = repo.db.accounts().all().associateBy { it.id }
        val cat = t.categoryId?.let { id -> cats[id]?.let { c -> listOfNotNull(c.parentId?.let { cats[it]?.name }, c.name).joinToString("／") } }
        val account = when (t.type) {
            TxnType.TRANSFER -> "${accs[t.accountId]?.name ?: "?"} → ${accs[t.toAccountId]?.name ?: "?"}"
            else -> accs[t.accountId]?.name ?: tr("未指定")
        }
        val dt = java.time.Instant.ofEpochMilli(t.date).atZone(java.time.ZoneId.systemDefault())
        val date = "%d/%d/%d（%s）%02d:%02d".format(dt.year, dt.monthValue, dt.dayOfMonth,
            com.enwei.moneymanager.core.Recurrence.weekdayName(dt.dayOfWeek), dt.hour, dt.minute)
        val amount = Money.withSymbol(t.amount, t.currency)
        val lines = listOfNotNull(
            tr("日期") + "：" + date,
            tr("金額") + "：" + amount,
            if (t.type != TxnType.TRANSFER) tr("分類") + "：" + (cat?.let(::tr) ?: tr("未分類")) else null,
            tr("帳戶") + "：" + account,
            t.merchant?.takeIf { it.isNotBlank() }?.let { tr("內容") + "：" + it },
            t.note?.takeIf { it.isNotBlank() }?.let { tr("說明") + "：" + it },
        )
        b.setContentTitle("${tr(t.type.label)} $amount · " + tr(if (pending) "待確認" else "已自動記帳"))
            .setContentText(listOfNotNull(t.merchant?.takeIf { it.isNotBlank() }, cat?.let(::tr), account).joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setWhen(t.date).setShowWhen(true)
        if (t.type == TxnType.TRANSFER) { nm.notify(notifId(t.id), b.build()); return }
        // 輸入說明（通知上直接回覆）
        val noteIntent = PendingIntent.getBroadcast(
            context, notifId(t.id) * 10 + 9,
            Intent(context, TxnActionReceiver::class.java).setAction(ACTION_NOTE).putExtra(EXTRA_TXN, t.id),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        b.addAction(
            NotificationCompat.Action.Builder(0, tr("輸入說明"), noteIntent)
                .addRemoteInput(RemoteInput.Builder(KEY_NOTE).setLabel(tr("這筆是什麼？")).build())
                .build(),
        )
        repo.frequentCategories(t.type, 2, t.categoryId).forEachIndexed { i, c ->
            val pi = PendingIntent.getBroadcast(
                context, notifId(t.id) * 10 + i,
                Intent(context, TxnActionReceiver::class.java).setAction(ACTION_CATEGORY).putExtra(EXTRA_TXN, t.id).putExtra(EXTRA_CAT, c.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, tr(c.name), pi)
        }
        nm.notify(notifId(t.id), b.build())
    }

    class TxnActionReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(EXTRA_TXN, 0)
            if (id <= 0) return
            val pending = goAsync()
            MoneyApp.instance.appScope.launch {
                try {
                    when (intent.action) {
                        ACTION_CATEGORY -> MoneyApp.repo.quickUpdateTxn(id, categoryId = intent.getLongExtra(EXTRA_CAT, 0).takeIf { it > 0 })
                        ACTION_NOTE -> {
                            val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_NOTE)?.toString()
                            MoneyApp.repo.quickUpdateTxn(id, note = text)
                        }
                    }
                    // 用同一則通知更新成新的分類／說明（不另外發一則「已更新」）
                    val t = MoneyApp.repo.getTxn(id)
                    notifyRecorded(context, id, "", t?.status == com.enwei.moneymanager.data.TxnStatus.PENDING, silent = true)
                } finally { pending.finish() }
            }
        }
    }
}
