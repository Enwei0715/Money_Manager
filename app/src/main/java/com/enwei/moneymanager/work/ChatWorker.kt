package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.askAdvisor
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 股票助理對話在背景執行：離開畫面或 App 也會繼續，回覆存進對話紀錄；
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

    fun ask(context: Context, question: String) {
        if (state.value?.running == true || question.isBlank()) return
        state.value = State(question, System.currentTimeMillis())
        WorkManager.getInstance(context).enqueueUniqueWork(
            "advisor-chat", ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ChatWorker>()
                .setInputData(workDataOf("q" to question))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }

    suspend fun run(context: Context, question: String) {
        val start = state.value?.startedAt ?: System.currentTimeMillis()
        state.value = State(question, start)
        val r = runCatching { MoneyApp.repo.askAdvisor(MoneyApp.news, question) }.getOrNull()
        if (r == null) {
            state.value = State(question, start, running = false, error = "AI 沒有回應：${GroqAi.lastError ?: ""}")
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
            androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
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

class ChatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ChatJob.run(applicationContext, inputData.getString("q").orEmpty())
        return Result.success()
    }
}
