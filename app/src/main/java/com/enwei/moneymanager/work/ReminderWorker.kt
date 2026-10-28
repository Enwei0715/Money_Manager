package com.enwei.moneymanager.work

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.R
import com.enwei.moneymanager.ui.MainActivity
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** 每日記帳提醒。 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!MoneyApp.repo.settings.value.reminderEnabled) return Result.success()
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        val intent = PendingIntent.getActivity(
            applicationContext, 1,
            Intent(applicationContext, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "txn/0"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            9001,
            NotificationCompat.Builder(applicationContext, MoneyApp.CHANNEL_RECORDS)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("今天記帳了嗎？")
                .setContentText("點這裡記下今天的花費")
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build(),
        )
        return Result.success()
    }

    companion object {
        private const val NAME = "daily-reminder"

        fun schedule(context: Context) {
            val s = MoneyApp.repo.settings.value
            val wm = WorkManager.getInstance(context)
            if (!s.reminderEnabled) { wm.cancelUniqueWork(NAME); return }
            val now = LocalDateTime.now()
            var target = now.toLocalDate().atTime(LocalTime.of(s.reminderMinutes / 60, s.reminderMinutes % 60))
            if (!target.isAfter(now)) target = target.plusDays(1)
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(Duration.between(now, target).toMinutes(), TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
