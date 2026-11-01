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
object ChatJob {
    data class State(val question: String, val startedAt: Long, val running: Boolean = true, val error: String? = null)

    val state = MutableStateFlow<State?>(null)

    /** 對話畫面是否正在顯示（顯示中就不另外通知） */
    @Volatile var visible = false

    /** 對話清單的捲動位置與看過的最後一則訊息時間（這次開 App 期間記住；-1 表示還沒進過對話） */
    var scrollIndex = 0
    var scrollOffset = 0
    var seenLast = -1L

    /** 進行中的回覆（App 的 coroutine，不經過 WorkManager 排程，送出就立刻開始） */
    @Volatile private var current: kotlinx.coroutines.Job? = null

    fun ask(context: Context, question: String) {
        if (state.value?.running == true || question.isBlank()) return
        val app = context.applicationContext
        state.value = State(question, System.currentTimeMillis())
        // 直接執行：沒有共用鎖，背景新聞分析在跑也不會讓對話排隊
        current = MoneyApp.instance.appScope.launch { run(app, question) }
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
            MoneyApp.repo.askAdvisor(MoneyApp.news, question)
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
        state.value = State(question, start, running = false)
        if (!visible) notifyReply(context, r.text)
    }

    private fun notifyReply(context: Context, text: String) {
        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        val open = android.app.PendingIntent.getActivity(
            context, 9340,
            android.content.Intent(context, com.enwei.moneymanager.ui.MainActivity::class.java)
                .putExtra(com.enwei.moneymanager.ui.MainActivity.EXTRA_ROUTE, "advisor"),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            9340,
            androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_ALERTS)
                .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
                .setContentTitle(tr("股票助理回覆了"))
                .setContentText(text.take(80))
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(text.take(400)))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

}

/** 舊版排進 WorkManager 的對話工作：保留類別讓系統能結束它，不再做事（對話已改為直接執行）。 */
class ChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()
}
