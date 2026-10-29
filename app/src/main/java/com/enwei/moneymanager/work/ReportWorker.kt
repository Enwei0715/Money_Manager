package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.DailyReport
import com.enwei.moneymanager.data.buildDailyReport
import com.enwei.moneymanager.data.refreshNews
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 產生每日投資報告：在背景執行（離開畫面或 App 也會繼續），
 * 依實際完成的步驟回報進度；不顯示無法可靠推算的剩餘時間。
 */
object ReportJob {
    data class State(
        val step: String, val startedAt: Long, val running: Boolean = true, val error: String? = null,
        /** 只更新新聞資料集（不產生報告） */
        val refreshOnly: Boolean = false,
        val done: Int? = null,
        val total: Int? = null,
    )

    val state = MutableStateFlow<State?>(null)

    /** 產生報告（[refreshOnly] 為只更新新聞資料集）。 */
    fun start(context: Context, refreshOnly: Boolean = false, userInitiated: Boolean = true) {
        state.value = State("等待網路或背景排程…", System.currentTimeMillis(), refreshOnly = refreshOnly)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "report-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReportWorker>()
                .setInputData(
                    androidx.work.workDataOf(
                        "refreshOnly" to refreshOnly,
                        "requestedAt" to System.currentTimeMillis(),
                        "userInitiated" to userInitiated,
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    /** 執行一次並回報進度；背景每日報告也走這裡。 */
    suspend fun execute(
        context: Context,
        notifyProgress: Boolean,
        refreshOnly: Boolean = false,
        onProgress: (State) -> Unit = {},
    ): DailyReport? {
        val repo = MoneyApp.repo
        val ds = MoneyApp.news
        val start = System.currentTimeMillis()
        fun update(step: String, done: Int? = null, total: Int? = null) {
            val now = System.currentTimeMillis()
            val next = State(step, start, refreshOnly = refreshOnly, done = done, total = total)
            state.value = next
            onProgress(next)
            if (notifyProgress) progressNotice(context, next, now - start)
        }
        return try {
            var st = ds.load()
            val coverage = ds.coverage(st)
            val needCollect = refreshOnly ||
                System.currentTimeMillis() - st.lastCollect > 3 * 3600_000L ||
                ds.recent(st, 3).size < 300 ||
                coverage.pending > 0 ||
                coverage.digested < coverage.recent
            if (needCollect) {
                update("蒐集新聞…")
                repo.refreshNews(
                    ds,
                    onStep = { step -> update(step) },
                    onBatch = { i, n -> update("AI 標註新聞 ${i + 1}/$n 批…", i + 1, n) },
                    onDigest = { i, n -> update("整理新聞重點 ${i + 1}/$n…", i + 1, n) },
                )
                st = ds.load()
            }
            if (refreshOnly) {
                state.value = State("資料集已更新：共 ${st.items.size} 則", start, running = false, refreshOnly = true)
                if (notifyProgress) context.getSystemService(android.app.NotificationManager::class.java).cancel(NOTICE_ID)
                return null
            }
            var reportStep = ""
            val r = repo.buildDailyReport(ds) { step ->
                reportStep = step
                update(step)
            }
            if (r == null) {
                val msg = if (reportStep.startsWith("近 3 天新聞") || reportStep.endsWith("暫不產生報告。")) {
                    reportStep
                } else "產生失敗：${GroqAi.lastError ?: "沒有回應"}"
                state.value = State(msg, start, running = false, error = msg)
                doneNotice(context, tr("投資報告產生失敗"), msg)
            } else {
                state.value = State("完成", start, running = false)
                doneNotice(context, tr("今日投資報告已更新"), r.picks.joinToString("、") { "${it.symbol} ${it.name}" }.ifBlank { r.trend })
            }
            r
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val msg = "${if (refreshOnly) "更新失敗" else "產生失敗"}：${e.message ?: e.javaClass.simpleName}"
            state.value = State(msg, start, running = false, error = msg)
            doneNotice(context, tr(if (refreshOnly) "新聞資料集更新失敗" else "投資報告產生失敗"), msg)
            null
        }
    }

    private const val NOTICE_ID = 9321
    private const val COMPLETION_NOTICE_ID = 9323

    private fun progressNotice(context: Context, state: State, elapsed: Long) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(NOTICE_ID, progressNotification(context, state, elapsed))
    }

    fun foregroundInfo(context: Context, state: State) =
        ForegroundInfo(NOTICE_ID, progressNotification(context, state, elapsed = 0))

    fun workProgress(state: State) = AiBackgroundWork.progress(state.step, state.startedAt, state.done, state.total)

    fun notificationId() = NOTICE_ID

    private fun progressNotification(context: Context, state: State, elapsed: Long) =
        androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
            .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
            .setContentTitle(tr(if (state.refreshOnly) "更新新聞中" else "產生投資報告中"))
            .setContentText(
                tr(state.step) +
                    (state.done?.let { done -> state.total?.let { total -> " · $done / $total" } ?: "" } ?: "") +
                    (if (elapsed > 0) " · 已執行 ${formatDuration(elapsed)}" else ""),
            )
            .setProgress(state.total ?: 0, state.done ?: 0, state.total == null || state.total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(context))
            .build()

    private fun doneNotice(context: Context, title: String, text: String) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(
            COMPLETION_NOTICE_ID,
            androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openIntent(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    private fun openIntent(context: Context) = android.app.PendingIntent.getActivity(
        context, 9322,
        android.content.Intent(context, com.enwei.moneymanager.ui.MainActivity::class.java)
            .putExtra(com.enwei.moneymanager.ui.MainActivity.EXTRA_ROUTE, "advisor"),
        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(1)
        return if (s >= 60) "${s / 60} 分 ${s % 60} 秒" else "$s 秒"
    }
}

class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val initial = ReportJob.state.value
            ?: ReportJob.State("準備中…", System.currentTimeMillis(), refreshOnly = inputData.getBoolean("refreshOnly", false))
        val running = initial.copy(step = "準備中…", startedAt = System.currentTimeMillis())
        val userInitiated = inputData.getBoolean("userInitiated", false)
        ReportJob.state.value = running
        setProgress(ReportJob.workProgress(running))
        if (userInitiated) setForeground(ReportJob.foregroundInfo(applicationContext, running))
        try {
            ReportJob.execute(
                applicationContext,
                notifyProgress = true,
                refreshOnly = inputData.getBoolean("refreshOnly", false),
                onProgress = { state ->
                    setProgressAsync(ReportJob.workProgress(state))
                    if (userInitiated) setForegroundAsync(ReportJob.foregroundInfo(applicationContext, state))
                },
            )
        } catch (e: CancellationException) {
            ReportJob.state.value?.let {
                ReportJob.state.value = it.copy(step = "背景工作已停止", running = false, error = "背景工作已停止。")
            }
            throw e
        }
        val error = ReportJob.state.value?.error
        return if (error == null) Result.success() else Result.failure(androidx.work.workDataOf("error" to error))
    }
}
