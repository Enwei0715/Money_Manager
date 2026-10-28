package com.enwei.moneymanager.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.enwei.moneymanager.R
import com.enwei.moneymanager.ui.MainActivity

/** 通知列常駐「快速記帳」：點一下直接開啟記帳畫面。 */
object QuickAdd {
    private const val CHANNEL = "quick_add"
    private const val ID = 7301

    private fun open(ctx: Context, code: Int, route: String) = PendingIntent.getActivity(
        ctx, code,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun sync(ctx: Context, enabled: Boolean) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!enabled) { nm.cancel(ID); return }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "快速記帳", NotificationManager.IMPORTANCE_MIN).apply {
                description = "通知列常駐的記帳捷徑"
                setShowBadge(false)
            },
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_ledger)
            .setContentTitle("記一筆")
            .setContentText("點一下新增支出")
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(open(ctx, 31, "txn/0?type=EXPENSE"))
            .addAction(0, "支出", open(ctx, 32, "txn/0?type=EXPENSE"))
            .addAction(0, "收入", open(ctx, 33, "txn/0?type=INCOME"))
            .addAction(0, "轉帳", open(ctx, 34, "txn/0?type=TRANSFER"))
            .build()
        runCatching { nm.notify(ID, n) }
    }
}
