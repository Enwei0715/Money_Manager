package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.buildDailyReport
import com.enwei.moneymanager.data.refreshNews
import com.enwei.moneymanager.net.GroqAi
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 投資助理的背景工作：每 6 小時蒐集新聞並請 AI 標註，累積成資料集；
 * 每天早上 7 點後、資料集夠完整時產生當天的投資報告並通知。
 */
class NewsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = MoneyApp.repo
        val s = repo.settings.value
        if (!s.advisorDaily || !GroqAi.enabled) return Result.success()
        val ds = MoneyApp.news
        runCatching { repo.refreshNews(ds) }
        val st = ds.load()
        val today = LocalDate.now().toEpochDay()
        if (LocalTime.now().hour >= 7 && st.report?.day != today && ds.recent(st, 3).size >= 100) {
            // 同一套流程（完成時會通知）
            if (ReportJob.state.value?.running != true) ReportJob.execute(applicationContext, notifyProgress = false)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NewsWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("news", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        private fun notifyReport(context: Context, picks: String, trend: String) {
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (!nm.areNotificationsEnabled()) return
            val open = android.app.PendingIntent.getActivity(
                context, 9320,
                android.content.Intent(context, com.enwei.moneymanager.ui.MainActivity::class.java)
                    .putExtra(com.enwei.moneymanager.ui.MainActivity.EXTRA_ROUTE, "advisor"),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                9320,
                androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                    .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
                    .setContentTitle(com.enwei.moneymanager.ui.tr("今日投資報告已更新"))
                    .setContentText(if (picks.isNotBlank()) picks else trend)
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText("$trend\n$picks"))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }
}
