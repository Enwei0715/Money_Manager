package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.refreshNews
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
        // 連續失敗後暫停中：等使用者按「恢復」或暫停時間過了再跑
        if (AiHealth.backgroundPaused()) return Result.success()
        if (AiHealth.networkProblem() != null) return Result.success()
        // 使用者正在手動更新或產生報告：不重複執行
        if (ReportJob.state.value?.running == true) return Result.success()
        val ds = MoneyApp.news
        val budget = WorkBudget()
        var startedAt = 0L
        var phase = ""
        var phaseStart = 0L
        fun update(step: String, done: Int? = null, total: Int? = null) {
            val progress = AiBackgroundWork.progress(step, startedAt, done, total)
            setProgressAsync(progress)
            // 通知列與 App 的 AI 狀態列顯示進度與預估剩餘時間（一般通知，不啟動前景服務，不會因背景限制而閃退）
            val now = System.currentTimeMillis()
            val key = step.substringBefore(' ')
            if (key != phase) { phase = key; phaseStart = now }
            val eta = if (done != null && total != null && done > 1 && total > done) now + (now - phaseStart) / (done - 1) * (total - done + 1) else null
            val st = ReportJob.State(step, startedAt, refreshOnly = true, done = done, total = total, etaAt = eta, background = true)
            ReportJob.state.value = st
            ReportJob.progressNotice(applicationContext, st, now - startedAt)
        }
        val before = ds.coverage(ds.load())
        try {
            startedAt = System.currentTimeMillis()
            update("準備蒐集新聞…")
            repo.refreshNews(
                ds,
                onStep = { step -> update(step) },
                onBatch = { i, n -> update("AI 標註新聞 ${i + 1}/$n 批…", i + 1, n) },
                onDigest = { i, n -> update("整理新聞重點 ${i + 1}/$n…", i + 1, n) },
                budget = budget,
            )
        } catch (e: CancellationException) {
            ReportJob.cancelProgress(applicationContext)
            finish("背景工作已停止")
            throw e
        } catch (e: Exception) {
            android.util.Log.w("NewsWorker", "News refresh failed (${e.javaClass.simpleName}); retrying")
            val reason = com.enwei.moneymanager.net.InvestData.lastHeadlineError ?: e.message ?: "網路或資料儲存錯誤"
            setProgressAsync(AiBackgroundWork.progress("新聞資料集更新失敗：$reason，將稍後重試。", startedAt))
            ReportJob.cancelProgress(applicationContext)
            AiHealth.record(false, reason)
            finish("新聞更新失敗：$reason")
            return if (AiHealth.backgroundPaused()) Result.success() else Result.retry()
        }
        ReportJob.cancelProgress(applicationContext)
        val st = ds.load()
        val cov = ds.coverage(st)
        val stopped = budget.stopReason
        when {
            // 時間用完：已完成的區塊都已寫入，排一輪接續剩下的新聞
            stopped != null -> { continueSoon(applicationContext); finish("暫停：$stopped") }
            // 還沒整理完：這一輪有進展就接著跑（不算失敗）；完全沒進展才算失敗（多半是 AI 額度或回應問題）
            cov.pending > 0 || cov.digested < cov.recent -> {
                val progressed = cov.digested > before.digested || cov.pending < before.pending
                if (progressed) { AiHealth.record(true); continueSoon(applicationContext) }
                else AiHealth.record(false, GroqAi.lastError ?: "新聞整理沒有進展")
                finish(if (progressed) null else "還有 ${cov.pending} 則待讀、${cov.recent - cov.digested} 則待整理：${GroqAi.lastError ?: "稍後繼續"}")
            }
            else -> { AiHealth.record(true); finish(null) }
        }
        val today = LocalDate.now().toEpochDay()
        if (stopped == null && LocalTime.now().hour >= 7 && st.report?.day != today && ds.recent(st, 3).size >= 100) {
            ReportJob.start(applicationContext, userInitiated = false)
        }
        return Result.success()
    }

    /** 背景更新結束：狀態列改為結束狀態（[error] 為 null 表示完成）。 */
    private fun finish(error: String?) {
        val cur = ReportJob.state.value ?: return
        if (cur.background) ReportJob.state.value = cur.copy(
            step = error ?: "新聞資料集已更新", running = false, error = error, done = null, total = null, etaAt = null,
        )
    }

    companion object {
        private fun constraints(context: Context) = Constraints.Builder()
            .setRequiredNetworkType(if (MoneyApp.repo.settings.value.aiWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true).build()

        /** [replace] 為設定改變（例如只用 Wi-Fi）時更新排程條件。 */
        fun schedule(context: Context, replace: Boolean = false) {
            val req = PeriodicWorkRequestBuilder<NewsWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraints(context))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "news", if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP, req,
            )
        }

        /** 這一輪時間用完：1 分鐘後接續（排在目前這輪之後，不會同時執行兩輪）。 */
        fun continueSoon(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "news-continue", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<NewsWorker>()
                    .setInitialDelay(1, TimeUnit.MINUTES)
                    .setConstraints(constraints(context))
                    .build(),
            )
        }
    }
}
