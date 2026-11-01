package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
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
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.TimeUnit

data class AiWorkStatus(
    val running: Boolean,
    val step: String,
    val startedAt: Long,
    val done: Int?,
    val total: Int?,
    val error: String? = null,
)

/**
 * 輕量任務的進度狀態（由 ViewModel / UI 直接觀察 StateFlow，不透過 WorkManager / Notification）
 */
data class AiTaskProgress(
    val running: Boolean = false,
    val step: String = "",
    val done: Int? = null,
    val total: Int? = null,
    val error: String? = null,
)

object AiBackgroundWork {
    private const val PROGRESS_STEP = "step"
    private const val PROGRESS_STARTED = "started"
    private const val PROGRESS_DONE = "done"
    private const val PROGRESS_TOTAL = "total"

    // --- 輕量任務的 StateFlow，供 UI 層直接觀察與顯示即時 Loading ---
    val reviewProgress = MutableStateFlow(AiTaskProgress())
    val unknownProgress = MutableStateFlow(AiTaskProgress())
    val duplicatesProgress = MutableStateFlow(AiTaskProgress())
    val autoTidyProgress = MutableStateFlow(AiTaskProgress())

    /** 相容舊呼叫：直接執行（不再使用任何全域鎖）。 */
    suspend fun <T> withAiExecution(block: suspend () -> T): T = block()

    // ---------------- 輕量任務 suspend 函數 ----------------

    /**
     * AI 收支檢討（輕量任務）：由 UI/ViewModel Scope 直接以 launch 呼叫
     */
    suspend fun runReview(span: ReviewSpan, offset: Int, periodKey: String) {
        val flow = reviewProgress
        flow.value = AiTaskProgress(running = true, step = "整理收支資料…")
        try {
            if (!GroqAi.enabled) throw IllegalStateException("AI 已停用或尚未設定可用金鑰。")
            val repo = MoneyApp.repo
            val data = repo.spendingReview(span, offset)
            if (data.key != periodKey) throw IllegalStateException("檢討期間已變更，請重新產生。")
            val settings = repo.settings.value
            val (text, transactions) = data.aiInput(
                settings.baseCurrency,
                settings.txnNecessity,
                GroqAi.bigContext(GroqAi.AiTask.REVIEW),
            )
            flow.value = AiTaskProgress(running = true, step = "AI 分析 ${transactions.size} 筆交易…")
            val review = GroqAi.review(text, settings.baseCurrency, transactions.map { it.id })
                ?: throw IllegalStateException(GroqAi.lastError ?: "AI 沒有回應。")
            repo.settings.update {
                it.copy(reviews = (it.reviews + (periodKey to review)).entries.toList().takeLast(40).associate { e -> e.key to e.value })
            }
            flow.value = AiTaskProgress(running = false, step = "收支檢討已完成")
        } catch (e: CancellationException) {
            flow.value = AiTaskProgress(running = false)
            throw e
        } catch (e: Exception) {
            flow.value = AiTaskProgress(running = false, error = e.message ?: "AI 工作失敗")
        }
    }

    /**
     * AI 分類不明交易（輕量任務）：由 UI/ViewModel Scope 直接以 launch 呼叫
     */
    suspend fun runUnknownSort() {
        val flow = unknownProgress
        flow.value = AiTaskProgress(running = true, step = "整理不明交易…")
        try {
            if (!GroqAi.enabled) throw IllegalStateException("AI 已停用或尚未設定可用金鑰。")
            val repo = MoneyApp.repo
            val total = repo.unknownCount()
            if (total == 0) {
                flow.value = AiTaskProgress(running = false, step = "沒有需要整理的未分類交易")
                return
            }
            flow.value = AiTaskProgress(running = true, step = "整理不明交易…", done = 0, total = total)
            val count = repo.aiSortUnknown { done, all ->
                flow.value = AiTaskProgress(running = true, step = "AI 分類交易 $done / $all…", done = done, total = all)
            }
            if (count == 0 && GroqAi.lastError != null) throw IllegalStateException(GroqAi.lastError!!)
            flow.value = AiTaskProgress(running = false, step = "已新增 $count 筆 AI 分類建議")
        } catch (e: CancellationException) {
            flow.value = AiTaskProgress(running = false)
            throw e
        } catch (e: Exception) {
            flow.value = AiTaskProgress(running = false, error = e.message ?: "AI 工作失敗")
        }
    }

    /**
     * AI 重複交易判斷（輕量任務）：由 UI/ViewModel Scope 直接以 launch 呼叫
     */
    suspend fun runDuplicatesCheck() {
        val flow = duplicatesProgress
        flow.value = AiTaskProgress(running = true, step = "搜尋可能重複的交易…")
        try {
            if (!GroqAi.enabled) throw IllegalStateException("AI 已停用或尚未設定可用金鑰。")
            val repo = MoneyApp.repo
            val pairs = repo.findDuplicates()
            if (pairs.isEmpty()) {
                flow.value = AiTaskProgress(running = false, step = "沒有待判斷的重複交易")
                return
            }
            flow.value = AiTaskProgress(running = true, step = "AI 判斷可能重複的交易…", done = 0, total = pairs.size)
            val results = repo.aiJudgeDuplicates(pairs) { done, total ->
                flow.value = AiTaskProgress(running = true, step = "AI 判斷交易 $done / $total 組…", done = done, total = total)
            }
            if (results.isEmpty() && GroqAi.lastError != null) throw IllegalStateException(GroqAi.lastError!!)
            flow.value = AiTaskProgress(running = false, step = "已判斷 ${results.size} 組可能重複交易")
        } catch (e: CancellationException) {
            flow.value = AiTaskProgress(running = false)
            throw e
        } catch (e: Exception) {
            flow.value = AiTaskProgress(running = false, error = e.message ?: "AI 工作失敗")
        }
    }

    /**
     * AI 自動整理（輕量任務）：由 UI/ViewModel Scope 或 AutoTidyTriggerWorker 呼叫
     */
    suspend fun runAutoTidy() {
        if (!GroqAi.enabled || !MoneyApp.repo.settings.value.aiAutoTidy) return
        val flow = autoTidyProgress
        flow.value = AiTaskProgress(running = true, step = "自動整理新進交易…")
        try {
            val repo = MoneyApp.repo
            val count = repo.aiAutoTidy { done, total ->
                flow.value = AiTaskProgress(running = true, step = "AI 自動整理 $done / $total 組…", done = done, total = total)
            }
            if (count == 0 && GroqAi.lastError != null) throw IllegalStateException(GroqAi.lastError!!)
            flow.value = AiTaskProgress(running = false, step = "自動整理完成")
        } catch (e: CancellationException) {
            flow.value = AiTaskProgress(running = false)
            throw e
        } catch (e: Exception) {
            flow.value = AiTaskProgress(running = false, error = e.message ?: "AI 工作失敗")
        }
    }

    fun scheduleAutoTidy(context: Context) {
        val request = OneTimeWorkRequestBuilder<AutoTidyTriggerWorker>()
            .setInitialDelay(3, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("ai-auto-tidy-trigger", ExistingWorkPolicy.REPLACE, request)
    }

    // --- WorkManager 狀態輔助（專供 NewsWorker 與 ReportWorker 重型任務） ---

    fun status(infos: List<WorkInfo>?, uniqueName: String): AiWorkStatus? {
        val info = infos
            ?.firstOrNull { !it.state.isFinished }
            ?: infos?.lastOrNull()
            ?: return null
        // 已結束的工作不再顯示：WorkManager 會一直保留最後一次的結果，舊的失敗紀錄會讓畫面一直顯示「背景工作失敗」；
        // 這一次執行的錯誤由 ReportJob.state 與通知顯示
        if (info.state.isFinished) return null
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

    internal fun progress(step: String, startedAt: Long, done: Int? = null, total: Int? = null) = workDataOf(
        PROGRESS_STEP to step,
        PROGRESS_STARTED to startedAt,
        PROGRESS_DONE to (done ?: -1),
        PROGRESS_TOTAL to (total ?: -1),
    )
}

class AutoTidyTriggerWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        AiBackgroundWork.runAutoTidy()
        return Result.success()
    }
}
