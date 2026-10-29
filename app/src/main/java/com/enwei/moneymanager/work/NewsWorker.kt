package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.net.GroqAi
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 投資助理的背景工作：每 3 小時蒐集新聞並請 AI 標註，累積成資料集；
 * 每天早上 7 點後、資料集夠完整時產生當天的投資報告並通知。
 */
class NewsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = MoneyApp.repo
        val s = repo.settings.value
        if (!s.advisorDaily || !GroqAi.enabled) return Result.success()
        val ds = MoneyApp.news
        val startedAt = System.currentTimeMillis()
        var currentStep = "準備蒐集新聞…"
        fun update(step: String, done: Int? = null, total: Int? = null) {
            currentStep = step
            val progress = AiBackgroundWork.progress(step, startedAt, done, total)
            setProgressAsync(progress)
        }
        try {
            update(currentStep)
            repo.refreshNews(
                ds,
                onStep = { step -> update(step) },
                onBatch = { i, n -> update("AI 標註新聞 ${i + 1}/$n 批…", i + 1, n) },
                onDigest = { i, n -> update("整理新聞重點 ${i + 1}/$n…", i + 1, n) },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("NewsWorker", "News refresh failed (${e.javaClass.simpleName}); retrying")
            val msg = "新聞資料集更新失敗：${com.enwei.moneymanager.net.InvestData.lastHeadlineError ?: "網路或資料儲存錯誤"}，將稍後重試。"
            update(msg)
            return Result.retry()
        }
        val st = ds.load()
        val today = LocalDate.now().toEpochDay()
        if (LocalTime.now().hour >= 7 && st.report?.day != today && ds.recent(st, 3).size >= 100) {
            ReportJob.start(applicationContext, userInitiated = false)
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<NewsWorker>(3, TimeUnit.HOURS)
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
