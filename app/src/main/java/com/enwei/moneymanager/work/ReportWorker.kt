package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 產生每日投資報告：在背景執行（離開畫面或 App 也會繼續），
 * 依目前的步驟估算剩餘時間，畫面與通知列都看得到進度，完成時通知。
 */
object ReportJob {
    /** [etaAt] 為預估完成的時間點（毫秒）。 */
    data class State(val step: String, val startedAt: Long, val etaAt: Long, val running: Boolean = true, val error: String? = null)

    val state = MutableStateFlow<State?>(null)

    fun start(context: Context) {
        if (state.value?.running == true) return
        state.value = State("準備中…", System.currentTimeMillis(), System.currentTimeMillis() + 60_000)
        WorkManager.getInstance(context).enqueueUniqueWork(
            "report-now", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ReportWorker>().setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    private const val COLLECT_MS = 20_000L
    private const val SNAPSHOT_MS = 10_000L

    /** 執行一次並回報進度；背景每日報告也走這裡。 */
    suspend fun execute(context: Context, notifyProgress: Boolean): DailyReport? {
        val repo = MoneyApp.repo
        val ds = MoneyApp.news
        val start = System.currentTimeMillis()
        val big = GroqAi.bigContext(GroqAi.AiTask.NEWS_TAG)
        val perBatch = if (big) 22_000L else 30_000L
        val batchSize = if (big) 150 else 40
        val maxBatches = if (big) 20 else 12
        val aiMs = repo.settings.value.reportAiMs.coerceIn(15_000, 240_000)
        fun batchesFor(untagged: Int) = minOf(maxBatches, (untagged + batchSize - 1) / batchSize)
        fun update(step: String, remaining: Long) {
            val now = System.currentTimeMillis()
            state.value = State(step, start, now + remaining)
            if (notifyProgress) progressNotice(context, step, remaining, now - start, now + remaining - start)
        }
        return try {
            var st = ds.load()
            val needCollect = System.currentTimeMillis() - st.lastCollect > 3 * 3600_000L || ds.recent(st, 3).size < 300
            if (needCollect) {
                // 還不知道會抓到多少新的：先以 400 則估算，抓完再修正
                update("蒐集新聞…", COLLECT_MS + batchesFor(st.items.count { !it.tagged } + 400) * perBatch + SNAPSHOT_MS + aiMs)
                repo.refreshNews(ds, onStep = {}, onBatch = { i, n ->
                    update("AI 標註新聞 ${i + 1}/$n 批…", (n - i) * perBatch + SNAPSHOT_MS + aiMs)
                })
                st = ds.load()
            }
            val aiStart = System.currentTimeMillis()
            val r = repo.buildDailyReport(ds) { step ->
                when {
                    step.startsWith("讀取大盤") -> update(step, SNAPSHOT_MS + aiMs)
                    step.startsWith("AI 分析") -> update(step, aiMs)
                    step.startsWith("確認") -> update(step, 5_000)
                    else -> update(step, aiMs)
                }
            }
            val took = System.currentTimeMillis() - aiStart
            // 記住這次 AI 分析花的時間，下次估得更準
            repo.settings.update { it.copy(reportAiMs = (it.reportAiMs * 0.5 + took * 0.5).toLong()) }
            if (r == null) {
                val msg = "產生失敗：${GroqAi.lastError ?: "沒有回應"}"
                state.value = State(msg, start, System.currentTimeMillis(), running = false, error = msg)
                if (notifyProgress) doneNotice(context, tr("投資報告產生失敗"), msg)
            } else {
                state.value = State("完成", start, System.currentTimeMillis(), running = false)
                doneNotice(context, tr("今日投資報告已更新"), r.picks.joinToString("、") { "${it.symbol} ${it.name}" }.ifBlank { r.trend })
            }
            r
        } catch (e: Exception) {
            val msg = "產生失敗：${e.message ?: e.javaClass.simpleName}"
            state.value = State(msg, start, System.currentTimeMillis(), running = false, error = msg)
            null
        }
    }

    private const val NOTICE_ID = 9321

    private fun progressNotice(context: Context, step: String, remaining: Long, elapsed: Long, total: Long) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(
            NOTICE_ID,
            androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
                .setContentTitle(tr("產生投資報告中"))
                .setContentText(tr(step) + " · " + tr("約剩") + " " + formatDuration(remaining))
                .setProgress(100, ((elapsed * 100) / total.coerceAtLeast(1)).toInt().coerceIn(1, 95), false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openIntent(context))
                .build(),
        )
    }

    private fun doneNotice(context: Context, title: String, text: String) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(
            NOTICE_ID,
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
        ReportJob.execute(applicationContext, notifyProgress = true)
        return Result.success()
    }
}
