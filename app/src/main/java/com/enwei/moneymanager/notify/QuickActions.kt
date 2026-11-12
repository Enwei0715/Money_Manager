package com.enwei.moneymanager.notify

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.budgetUse
import com.enwei.moneymanager.R
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.ui.MainActivity
import com.enwei.moneymanager.ui.tr
import com.enwei.moneymanager.notify.AppNotices
import com.enwei.moneymanager.notify.NoticeKind
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
        if (!AppNotices.allowed(context, NoticeKind.RECORDED)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val repo = MoneyApp.repo
        val t = txnId?.let { repo.getTxn(it) }
        val nid = notifId(txnId ?: System.currentTimeMillis())
        if (t == null) {
            // 股票成交等不是一般交易的紀錄：只顯示訊息
            val b = AppNotices.builder(context, NoticeKind.RECORDED, nid, tr(if (pending) "已記下，待確認" else "已自動記帳"), message,
                if (pending) "pending" else "txns").setOnlyAlertOnce(silent)
            nm.notify(nid, b.build())
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
        val date = "%d/%d（%s）%02d:%02d".format(dt.monthValue, dt.dayOfMonth,
            com.enwei.moneymanager.core.Recurrence.weekdayName(dt.dayOfWeek), dt.hour, dt.minute)
        val amount = Money.withSymbol(t.amount, t.currency)
        val what = t.merchant?.takeIf { it.isNotBlank() }
        val budget = runCatching { repo.budgetUse(t.categoryId, t.date) }.getOrDefault(emptyList()).firstOrNull()
        // 版型：標題「支出 $140・京都日式料理」，摘要「分類 · 帳戶」；展開後逐行列出，並附預算
        val lines = listOfNotNull(
            if (t.type != TxnType.TRANSFER) tr("分類") + "：" + (cat?.let(::tr) ?: tr("未分類")) else null,
            tr("帳戶") + "：$account",
            tr("時間") + "：$date",
            t.note?.takeIf { it.isNotBlank() }?.let { tr("說明") + "：$it" },
            budget?.let { u ->
                tr("預算") + "：" + u.name + " " + tr("已用") + " ${u.pct}%・" +
                    if (u.left >= 0) tr("剩") + " " + Money.withSymbol(u.left, repo.settings.value.baseCurrency)
                    else tr("超出") + " " + Money.withSymbol(-u.left, repo.settings.value.baseCurrency)
            },
            if (pending) tr("點一下確認或修改") else if (t.categoryId == null && t.type != TxnType.TRANSFER) tr("還沒有分類，可直接按下方按鈕") else null,
        )
        val b = AppNotices.builder(
            context, NoticeKind.RECORDED, notifId(t.id),
            (if (pending) tr("待確認") + "・" else "") + "${tr(t.type.label)} $amount" + (what?.let { "・$it" } ?: ""),
            listOfNotNull(if (t.type != TxnType.TRANSFER) (cat?.let(::tr) ?: tr("未分類")) else null, account).joinToString(" · "),
            "txn/${t.id}", lines, t.date,
        ).setOnlyAlertOnce(silent)
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
        // 「記錯了」：直接打開這筆交易修改；通知最多三個按鈕，分類快捷保留一個
        if (!pending) b.addAction(0, tr("記錯了"), AppNotices.openIntent(context, "txn/${t.id}", notifId(t.id) * 10 + 7))
        repo.frequentCategories(t.type, if (pending) 2 else 1, t.categoryId).forEachIndexed { i, c ->
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
