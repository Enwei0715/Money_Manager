package com.enwei.moneymanager.work

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.ReviewSpan
import com.enwei.moneymanager.data.aiInput
import com.enwei.moneymanager.data.spendingReview
import com.enwei.moneymanager.net.GroqAi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.TimeUnit

data class AiWorkStatus(
    val running: Boolean,
    val step: String,
    val startedAt: Long,
    val done: Int?,
    val total: Int?,
    val error: String? = null,
)

object AiBackgroundWork {
    private val executionMutex = Mutex()
    private const val KIND = "kind"
    private const val REVIEW = "review"
    private const val UNKNOWN = "unknown"
    private const val DUPLICATES = "duplicates"
    private const val AUTO_TIDY = "auto-tidy"
    private const val PROGRESS_STEP = "step"
    private const val PROGRESS_STARTED = "started"
    private const val PROGRESS_DONE = "done"
    private const val PROGRESS_TOTAL = "total"

    fun reviewName(key: String) = "ai-review-$key"
    const val UNKNOWN_NAME = "ai-unknown-sort"
    const val DUPLICATES_NAME = "ai-duplicate-review"
    const val AUTO_TIDY_NAME = "ai-auto-tidy"

    /** Keep long-running AI workers from making overlapping provider requests. */
    suspend fun <T> withAiExecution(block: suspend () -> T): T {
        executionMutex.lock()
        return try {
            block()
        } finally {
            executionMutex.unlock()
        }
    }

    fun review(context: Context, span: ReviewSpan, offset: Int, key: String) {
        enqueue(
            context,
            reviewName(key),
            workDataOf(KIND to REVIEW, "span" to span.name, "offset" to offset, "periodKey" to key, "userInitiated" to true),
        )
    }

    fun unknown(context: Context) {
        enqueue(context, UNKNOWN_NAME, workDataOf(KIND to UNKNOWN, "userInitiated" to true))
    }

    fun duplicates(context: Context) {
        enqueue(context, DUPLICATES_NAME, workDataOf(KIND to DUPLICATES, "userInitiated" to true))
    }

    fun autoTidy(context: Context, userInitiated: Boolean = true) {
        if (!GroqAi.enabled || !MoneyApp.repo.settings.value.aiAutoTidy) return
        enqueue(context, AUTO_TIDY_NAME, workDataOf(KIND to AUTO_TIDY, "userInitiated" to userInitiated))
    }

    fun scheduleAutoTidy(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoTidyTriggerWorker>()
            .setInitialDelay(3, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("ai-auto-tidy-trigger", ExistingWorkPolicy.REPLACE, request)
    }

    fun status(infos: List<WorkInfo>?, uniqueName: String): AiWorkStatus? {
        val info = infos
            ?.firstOrNull { !it.state.isFinished }
            ?: infos?.lastOrNull()
            ?: return null
        if (info.state.isFinished && info.state != WorkInfo.State.FAILED) return null
        if (uniqueName == "news" && info.state == WorkInfo.State.ENQUEUED) return null
        val data = info.progress
        val startedAt = data.getLong(PROGRESS_STARTED, 0)
        return AiWorkStatus(
            running = info.state == WorkInfo.State.RUNNING || (info.state == WorkInfo.State.ENQUEUED && uniqueName != "news"),
            step = data.getString(PROGRESS_STEP) ?: if (info.state == WorkInfo.State.ENQUEUED) "等待網路或背景排程…" else "準備中…",
            startedAt = startedAt,
            done = data.getInt(PROGRESS_DONE, -1).takeIf { it >= 0 },
            total = data.getInt(PROGRESS_TOTAL, -1).takeIf { it > 0 },
            error = info.outputData.getString("error")?.takeIf { info.state == WorkInfo.State.FAILED }
                ?: if (info.state == WorkInfo.State.FAILED) "背景工作失敗，請重試。" else null,
        )
    }

    private fun enqueue(
        context: Context,
        uniqueName: String,
        input: Data,
    ) {
        val request = OneTimeWorkRequestBuilder<AiBackgroundWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
    }

    internal fun progress(step: String, startedAt: Long, done: Int? = null, total: Int? = null) = workDataOf(
        PROGRESS_STEP to step,
        PROGRESS_STARTED to startedAt,
        PROGRESS_DONE to (done ?: -1),
        PROGRESS_TOTAL to (total ?: -1),
    )
}

class AutoTidyTriggerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AiBackgroundWork.autoTidy(applicationContext, userInitiated = false)
        return Result.success()
    }
}

class AiBackgroundWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val jobTitle: String
        get() = when (inputData.getString("kind")) {
            "review" -> "AI 收支檢討"
            "unknown" -> "AI 分類整理"
            "duplicates" -> "AI 重複交易判斷"
            else -> "AI 自動整理"
        }

    private val notificationId: Int
        get() = 810_000 + (id.hashCode() and Int.MAX_VALUE) % 100_000

    override suspend fun doWork(): Result {
        val kind = inputData.getString("kind") ?: return Result.failure(workDataOf("error" to "缺少 AI 工作類型。"))
        val userInitiated = inputData.getBoolean("userInitiated", false)
        setProgress(AiBackgroundWork.progress("等待其他 AI 工作完成…", 0))
        return AiBackgroundWork.withAiExecution {
            val startedAt = System.currentTimeMillis()
            var currentStep = "準備中…"
            try {
                if (!GroqAi.enabled) throw IllegalStateException("AI 已停用或尚未設定可用金鑰。")
                setProgress(AiBackgroundWork.progress(currentStep, startedAt))
                if (userInitiated) setForeground(foregroundInfo(currentStep, null, null))
                val repo = MoneyApp.repo
                suspend fun update(step: String, done: Int? = null, total: Int? = null) {
                    currentStep = step
                    val data = AiBackgroundWork.progress(step, startedAt, done, total)
                    setProgress(data)
                    if (userInitiated) setForeground(foregroundInfo(step, done, total))
                }
                when (kind) {
                "review" -> {
                    val span = inputData.getString("span")?.let { ReviewSpan.valueOf(it) }
                        ?: return@withAiExecution Result.failure(workDataOf("error" to "缺少檢討期間。"))
                    val offset = inputData.getInt("offset", 0)
                    val periodKey = inputData.getString("periodKey").orEmpty()
                    update("整理收支資料…")
                    val data = repo.spendingReview(span, offset)
                    if (data.key != periodKey) return@withAiExecution Result.failure(workDataOf("error" to "檢討期間已變更，請重新產生。"))
                    val settings = repo.settings.value
                    val (text, transactions) = data.aiInput(
                        settings.baseCurrency,
                        settings.txnNecessity,
                        GroqAi.bigContext(GroqAi.AiTask.REVIEW),
                    )
                    update("AI 分析 ${transactions.size} 筆交易…")
                    val review = GroqAi.review(text, settings.baseCurrency, transactions.map { it.id })
                        ?: throw IllegalStateException(GroqAi.lastError ?: "AI 沒有回應。")
                    repo.settings.update {
                        it.copy(reviews = (it.reviews + (periodKey to review)).entries.toList().takeLast(40).associate { e -> e.key to e.value })
                    }
                    complete("收支檢討已完成", startedAt)
                }
                "unknown" -> {
                    val total = repo.unknownCount()
                    if (total == 0) {
                        complete("沒有需要整理的未分類交易", startedAt)
                    } else {
                        update("整理不明交易…", 0, total)
                        val count = repo.aiSortUnknown { done, all ->
                            update("AI 分類交易 $done / $all…", done, all)
                        }
                        if (count == 0 && GroqAi.lastError != null) throw IllegalStateException(GroqAi.lastError!!)
                        complete("已新增 $count 筆 AI 分類建議", startedAt)
                    }
                }
                "duplicates" -> {
                    val pairs = repo.findDuplicates()
                    if (pairs.isEmpty()) {
                        complete("沒有待判斷的重複交易", startedAt)
                    } else {
                        update("AI 判斷可能重複的交易…", 0, pairs.size)
                        val results = repo.aiJudgeDuplicates(pairs) { done, total ->
                            update("AI 判斷交易 $done / $total 組…", done, total)
                        }
                        if (results.isEmpty() && GroqAi.lastError != null) throw IllegalStateException(GroqAi.lastError!!)
                        complete("已判斷 ${results.size} 組可能重複交易", startedAt)
                    }
                }
                "auto-tidy" -> {
                    update("自動整理新進交易…")
                    val count = repo.aiAutoTidy { done, total ->
                        update("AI 自動整理 $done / $total 組…", done, total)
                    }
                    if (count == 0 && GroqAi.lastError != null) throw IllegalStateException(GroqAi.lastError!!)
                    complete("自動整理完成", startedAt)
                }
                else -> return@withAiExecution Result.failure(workDataOf("error" to "未知的 AI 工作類型。"))
                }
                Result.success(workDataOf("message" to currentStep))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.message ?: e.javaClass.simpleName
                postCompletion("AI 工作失敗", error)
                Result.failure(workDataOf("error" to error))
            }
        }
    }

    private suspend fun complete(message: String, startedAt: Long) {
        setProgress(AiBackgroundWork.progress(message, startedAt, 1, 1))
        postCompletion(jobTitle, message)
    }

    private fun foregroundInfo(step: String, done: Int?, total: Int?): ForegroundInfo =
        ForegroundInfo(notificationId, notification(jobTitle, step, done, total, ongoing = true))

    private fun notification(title: String, message: String, done: Int?, total: Int?, ongoing: Boolean) =
        androidx.core.app.NotificationCompat.Builder(applicationContext, MoneyApp.CHANNEL_RECORDS)
            .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
            .setContentTitle(title)
            .setContentText(if (done != null && total != null) "$message · $done / $total" else message)
            .setProgress(total ?: 0, done ?: 0, total == null || total <= 0)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                android.app.PendingIntent.getActivity(
                    applicationContext,
                    notificationId,
                    Intent(applicationContext, com.enwei.moneymanager.ui.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun postCompletion(title: String, message: String) {
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(
            notificationId + 100_000,
            notification(title, message, null, null, ongoing = false),
        )
    }
}
