package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.askAdvisor
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 股票助理對話：送出後立刻在 App 的 coroutine 執行（離開對話畫面也會繼續），回覆存進對話紀錄；
 * 不在對話畫面時回覆完成會通知。
 */
/** 協程標記：在股票助理對話流程裡執行（背景工作看到對話進行中會讓路，對話自己的呼叫不用） */
object InChatKey : kotlin.coroutines.CoroutineContext.Key<InChat>
object InChat : kotlin.coroutines.AbstractCoroutineContextElement(InChatKey)

object ChatJob {
    data class State(val question: String, val startedAt: Long, val running: Boolean = true, val error: String? = null, val step: String = "")

    val state = MutableStateFlow<State?>(null)

    /** 對話畫面是否正在顯示（顯示中就不另外通知） */
    @Volatile var visible = false

    /** 對話清單的捲動位置與看過的最後一則訊息時間（這次開 App 期間記住；-1 表示還沒進過對話） */
    var scrollIndex = 0
    var scrollOffset = 0
    var seenLast = -1L

    /** 對話清單的捲動狀態放在這裡（不隨畫面消失）：切到其他分頁再回來，位置完全不變 */
    val listState = androidx.compose.foundation.lazy.LazyListState()
    /** 這次開 App 是否已從上次存的位置還原過、目前看到的最後一則訊息時間 */
    var restoredOnce = false
    var lastShown = 0L
    /** 對話引用的新聞（id → 新聞），切回對話時先用這份，不必等重新整理（避免訊息高度改變而跳動） */
    @Volatile var newsCache: Map<String, com.enwei.moneymanager.data.NewsItem> = emptyMap()

    /** 進行中的回覆（App 的 coroutine，不經過 WorkManager 排程，送出就立刻開始） */
    @Volatile private var current: kotlinx.coroutines.Job? = null

    fun ask(context: Context, question: String) {
        if (state.value?.running == true || question.isBlank()) return
        val app = context.applicationContext
        state.value = State(question, System.currentTimeMillis())
        // 直接執行：沒有共用鎖，背景新聞分析在跑也不會讓對話排隊
        // 標記「這是對話自己的工作」：對話流程裡的其他 AI 呼叫（確認代號等）不用讓路給自己
        current = MoneyApp.instance.appScope.launch(InChat) { run(app, question) }
    }

    /** 停止目前的回覆：取消執行中的工作，回覆就算之後回來也不會加進對話。 */
    fun stop(context: Context, question: String? = null) {
        current?.cancel()
        current = null
        val cur = state.value
        state.value = cur?.copy(running = false, error = "已停止這次回覆")
            ?: question?.let { State(it, System.currentTimeMillis(), running = false, error = "已停止這次回覆") }
    }

    suspend fun run(context: Context, question: String) {
        val start = state.value?.startedAt ?: System.currentTimeMillis()
        state.value = State(question, start)
        val r = try {
            MoneyApp.repo.askAdvisor(MoneyApp.news, question) { s -> state.value = state.value?.takeIf { it.running }?.copy(step = s) ?: state.value }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            state.value = State(question, start, running = false, error = "AI 執行失敗：${e.message ?: e.javaClass.simpleName}")
            null
        }
        if (r == null) {
            val error = state.value?.error ?: "AI 沒有回應：${GroqAi.lastError ?: "沒有回覆"}"
            // 失敗只在對話頁顯示（附「重試」），不另外發通知
            state.value = State(question, start, running = false, error = error)
            return
        }
        state.value = State(question, start, running = false, step = "已回覆" + r.basis.takeIf { it.isNotBlank() }?.let { "（$it）" }.orEmpty())
        if (!visible) notifyReply(context, r.text)
    }

    private fun notifyReply(context: Context, text: String) {
        val clean = com.enwei.moneymanager.data.stripCites(text).trim()
        com.enwei.moneymanager.notify.AppNotices.post(context, com.enwei.moneymanager.notify.NoticeKind.REPORT, 9340, tr("股票助理回覆了"),
            clean.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(80), "advisor:1",
            listOf(clean.take(400), tr("點選看完整回覆")))
    }

}

/** 舊版排進 WorkManager 的對話工作：保留類別讓系統能結束它，不再做事（對話已改為直接執行）。 */
class ChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
