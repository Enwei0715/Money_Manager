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
        /** 預估完成時間（毫秒）；依這一階段每批實際花的時間推算，算不出來時為 null */
        val etaAt: Long? = null,
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
        // 同一階段（例如「AI 標註新聞」）的開始時間，用來推算剩餘時間
        var phase = ""
        var phaseStart = start
        fun update(step: String, done: Int? = null, total: Int? = null) {
            val now = System.currentTimeMillis()
            val key = step.substringBefore(' ')
            if (key != phase) { phase = key; phaseStart = now }
            val eta = if (done != null && total != null && done > 1 && total > done)
                now + (now - phaseStart) / (done - 1) * (total - done + 1) else null
            val next = State(step, start, refreshOnly = refreshOnly, done = done, total = total, etaAt = eta)
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
            if (notifyProgress) cancelProgress(context)
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

    fun progressNotice(context: Context, state: State, elapsed: Long) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(NOTICE_ID, progressNotification(context, state, elapsed))
    }

    /** 收起進度通知。 */
    fun cancelProgress(context: Context) = context.getSystemService(android.app.NotificationManager::class.java).cancel(NOTICE_ID)

    fun foregroundInfo(context: Context, state: State) =
        ForegroundInfo(NOTICE_ID, progressNotification(context, state, elapsed = 0))

    fun workProgress(state: State) = AiBackgroundWork.progress(state.step, state.startedAt, state.done, state.total)

    fun notificationId() = NOTICE_ID

    private fun progressNotification(context: Context, state: State, elapsed: Long) =
        androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_PROGRESS)
            .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
            .setContentTitle(tr(if (state.refreshOnly) "更新新聞中" else "產生投資報告中"))
            .setContentText(
                tr(state.step) +
                    (state.done?.let { done -> state.total?.let { total -> " · $done / $total" } ?: "" } ?: "") +
                    (state.etaAt?.let { " · 約剩 ${formatDuration(it - System.currentTimeMillis())}" }
                        ?: if (elapsed > 0) " · 已執行 ${formatDuration(elapsed)}" else ""),
            )
            .setProgress(state.total ?: 0, state.done ?: 0, state.total == null || state.total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent(context))
            .build()

    private fun doneNotice(context: Context, title: String, text: String) {
        // 先收起進行中的進度通知（不是前景服務時它不會自動消失）
        cancelProgress(context)
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(
            COMPLETION_NOTICE_ID,
            androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_ALERTS)
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
        val refreshOnly = inputData.getBoolean("refreshOnly", false)
        val userInitiated = inputData.getBoolean("userInitiated", false)
        val queued = ReportJob.State("準備中…", System.currentTimeMillis(), refreshOnly = refreshOnly)
        ReportJob.state.value = queued
        setProgress(ReportJob.workProgress(queued))
        try {
            run {
                val running = queued.copy(step = "準備中…")
                ReportJob.state.value = running
                setProgress(ReportJob.workProgress(running))
                // 前景服務只在 App 顯示在畫面上時啟動：Android 12 以上從背景啟動會被系統拒絕（之前閃退的原因之一）；
                // 不是前景服務時仍會顯示一般的進度通知
                if (userInitiated && appVisible()) runCatching { setForeground(ReportJob.foregroundInfo(applicationContext, running)) }
                ReportJob.execute(
                    applicationContext,
                    notifyProgress = true,
                    refreshOnly = refreshOnly,
                    onProgress = { state ->
                        // 進度通知用同一個編號更新（前景服務的通知也會一起更新），不必每次重新設定前景服務
                        setProgressAsync(ReportJob.workProgress(state))
                    },
                )
            }
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

/** App 目前是否顯示在畫面上（可以啟動前景服務的狀態）。 */
internal fun appVisible(): Boolean {
    val info = android.app.ActivityManager.RunningAppProcessInfo()
    android.app.ActivityManager.getMyMemoryState(info)
    return info.importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
}
