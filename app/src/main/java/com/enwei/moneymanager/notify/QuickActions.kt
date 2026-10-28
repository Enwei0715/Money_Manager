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

    suspend fun notifyRecorded(context: Context, txnId: Long?, message: String, pending: Boolean) {
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
            .setContentTitle(tr(if (pending) "已記下，待確認" else "已自動記帳"))
            .setContentIntent(open)
            .setAutoCancel(true)
        if (t == null || t.type == TxnType.TRANSFER) {
            b.setContentText(message)
            nm.notify(notifId(txnId ?: System.currentTimeMillis()), b.build())
            return
        }
        val cats = repo.frequentCategories(t.type, 2, t.categoryId)
        val current = t.categoryId?.let { id -> MoneyApp.repo.db.categories().all().find { it.id == id }?.name }
        b.setContentText("${Money.withSymbol(t.amount, t.currency)} ${t.merchant.orEmpty()} · ${tr("分類：")}${current?.let(::tr) ?: tr("未分類")}")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${Money.withSymbol(t.amount, t.currency)} ${t.merchant.orEmpty()}\n${tr("分類：")}${current?.let(::tr) ?: tr("未分類")}\n" +
                    tr("可直接點下方改分類或輸入說明"),
            ))
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
        cats.forEachIndexed { i, c ->
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
                    // 更新通知內容（顯示新的分類／說明）
                    val t = MoneyApp.repo.getTxn(id)
                    val cat = t?.categoryId?.let { c -> MoneyApp.repo.db.categories().all().find { it.id == c }?.name }
                    val nm = context.getSystemService(NotificationManager::class.java)
                    nm.notify(
                        notifId(id),
                        NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                            .setSmallIcon(R.drawable.ic_stat_ledger)
                            .setContentTitle(tr("已更新"))
                            .setContentText(
                                listOfNotNull(t?.let { Money.withSymbol(it.amount, it.currency) }, t?.merchant, cat?.let { tr("分類：") + tr(it) }, t?.note)
                                    .joinToString(" · "),
                            )
                            .setTimeoutAfter(4000)
                            .setAutoCancel(true)
                            .build(),
                    )
                } finally { pending.finish() }
            }
        }
    }
}
