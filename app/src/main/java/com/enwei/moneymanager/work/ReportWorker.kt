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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
        /** 背景排程自動執行（不是使用者按的） */
        val background: Boolean = false,
        /** 這一輪停下來了，稍後會自動接續（狀態列保留一行並可按「繼續」） */
        val paused: Boolean = false,
    )

    /** App 裡直接執行的這一輪是否還活著（看門狗用：活著就不必接手）。 */
    fun activeHere(): Boolean = direct?.isActive == true

    val state = MutableStateFlow<State?>(null)

    @Volatile private var direct: kotlinx.coroutines.Job? = null

    /**
     * 使用者按「立即更新」或「產生報告」：直接在 App 裡執行，不經過 WorkManager 排隊
     * （排隊可能被系統延後很久，畫面只會顯示「等待網路或背景排程」）。離開畫面也會繼續；
     * App 被系統關掉時，背景排程之後會從已保存的區塊接續。
     */
    fun startNow(context: Context, refreshOnly: Boolean = false) {
        if (direct?.isActive == true || state.value?.running == true) return
        val app = context.applicationContext
        // 清掉之前卡在排隊中的同一件工作
        WorkManager.getInstance(app).cancelUniqueWork("report-now")
        if (AiHealth.networkProblem(app, background = false) != null) {
            state.value = State("沒有網路連線，無法更新", System.currentTimeMillis(), running = false, error = "沒有網路連線，連上網路後再按一次。", refreshOnly = refreshOnly)
            return
        }
        state.value = State("準備中…", System.currentTimeMillis(), refreshOnly = refreshOnly)
        direct = MoneyApp.instance.appScope.launch {
            execute(app, notifyProgress = true, refreshOnly = refreshOnly, budgetMs = 30 * 60_000L, userInitiated = true, watchdog = true)
        }
    }

    /** 產生報告（[refreshOnly] 為只更新新聞資料集）。 */
    fun start(context: Context, refreshOnly: Boolean = false, userInitiated: Boolean = true) {
        // 背景排程：還在排隊時不顯示「執行中」（真正開始執行時才更新狀態），畫面不會一直卡在「等待網路或背景排程」
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
        /** 這一輪可以執行多久；用完就在批次之間停下，稍後接續 */
        budgetMs: Long = 8 * 60_000L,
        userInitiated: Boolean = true,
        /**
         * App 裡直接執行時：一直往後延一個「看門狗」背景工作；App 被系統關掉（這一輪跟著消失）時，
         * 看門狗約 2 分鐘後由 WorkManager 啟動，從已保存的區塊接續，不會整個停掉。
         */
        watchdog: Boolean = false,
        onProgress: (State) -> Unit = {},
    ): DailyReport? {
        var lastWatch = 0L
        fun pushWatchdog() {
            if (!watchdog) return
            val now = System.currentTimeMillis()
            if (now - lastWatch < 45_000L) return
            lastWatch = now
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "report-watchdog", ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<ReportWorker>()
                        .setInputData(androidx.work.workDataOf("refreshOnly" to refreshOnly, "userInitiated" to true, "watchdog" to true))
                        .setInitialDelay(2, java.util.concurrent.TimeUnit.MINUTES)
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
                )
            }
        }
        fun stopWatchdog() { if (watchdog) runCatching { WorkManager.getInstance(context).cancelUniqueWork("report-watchdog") } }
        pushWatchdog()
        val budget = WorkBudget(budgetMs, background = !userInitiated)
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
            val next = State(step, start, refreshOnly = refreshOnly, done = done, total = total, etaAt = eta, background = !userInitiated)
            state.value = next
            pushWatchdog()
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
                    budget = budget,
                )
                st = ds.load()
            }
            budget.stopReason?.let { why ->
                // 時間用完或網路不符：已讀完的區塊都已寫入，稍後從斷點接續（不算失敗）
                val msg = "暫停：$why；已完成的部分已保存，稍後自動接續"
                state.value = State(msg, start, running = false, refreshOnly = refreshOnly, background = !userInitiated, paused = true)
                stopWatchdog()
                if (notifyProgress) cancelProgress(context)
                resumeOrLater(context, refreshOnly, userInitiated)
                return null
            }
            // 還沒整理完就停下（多半是 AI 額度用完，例如其他畫面也在用 AI）：不能說「已更新」，要標成暫停並接續
            val cov2 = ds.coverage(st)
            if (cov2.pending > 0 || cov2.digested < cov2.recent) {
                stopWatchdog()
                if (notifyProgress) cancelProgress(context)
                val until = GroqAi.coolingUntil()?.takeIf { it > System.currentTimeMillis() }
                val hm = until?.let { java.text.SimpleDateFormat("HH:mm", java.util.Locale.TAIWAN).format(java.util.Date(it)) }
                val progressed = cov2.digested > coverage.digested || cov2.pending < coverage.pending
                // 進度用全部資料計算（不是只算這一輪），並寫出 AI 回覆的真正原因
                val doneN = cov2.digested
                val msg = "已整理 $doneN/${cov2.recent} 則（待標註 ${cov2.pending}、待整理 ${cov2.recent - cov2.digested}）；" +
                    (if (hm != null) "AI 暫停到 $hm 後自動接續" else if (progressed) "接續中" else "AI 這一輪沒有進展") +
                    (GroqAi.lastError?.let { "。原因：$it" } ?: "")
                state.value = State(msg, start, running = false, refreshOnly = refreshOnly, background = !userInitiated, paused = until != null || progressed,
                    error = if (until == null && !progressed) msg else null)
                if (until != null) resumeAt(context, until + 5_000L, refreshOnly, userInitiated)
                else if (progressed) resumeOrLater(context, refreshOnly, userInitiated)
                return null
            }
            // 資料集完整了：只更新新聞時，若今天還沒有報告（早上 7 點後）就接著產生報告
            val today = java.time.LocalDate.now().toEpochDay()
            if (refreshOnly && (st.report?.day == today || java.time.LocalTime.now().hour < 7)) {
                stopWatchdog()
                state.value = State("資料集已更新：共 ${st.items.size} 則", start, running = false, refreshOnly = true)
                if (notifyProgress) context.getSystemService(android.app.NotificationManager::class.java).cancel(NOTICE_ID)
                return null
            }
            var reportStep = ""
            val r = repo.buildDailyReport(ds) { step ->
                reportStep = step
                update(step)
            }
            stopWatchdog()
            val notReady = reportStep.startsWith("近 3 天新聞")
            if (r == null && notReady) {
                // 不是失敗：新聞還在整理，報告要等資料完整才產生。這一輪有進展就稍後自動接續，不發「失敗」通知
                val after = ds.coverage(ds.load())
                val progressed = after.digested > coverage.digested || after.pending < coverage.pending
                val msg = "資料整理中：還有 ${after.pending} 則待標註、${after.recent - after.digested} 則待整理重點；" +
                    (if (progressed) "會自動繼續，完成後產生報告。" else "AI 這一輪沒有進展（${GroqAi.lastError ?: "可能是額度用完"}），稍後再試。")
                state.value = State(msg, start, running = false, background = !userInitiated, paused = progressed)
                if (notifyProgress) cancelProgress(context)
                stopWatchdog()
                if (progressed) resumeOrLater(context, refreshOnly, userInitiated)
                else if (!userInitiated) AiHealth.record(false, GroqAi.lastError ?: "新聞整理沒有進展")
            } else if (r == null) {
                val msg = if (reportStep.endsWith("暫不產生報告。")) reportStep else "產生失敗：${GroqAi.lastError ?: "沒有回應"}"
                state.value = State(msg, start, running = false, error = msg, background = !userInitiated)
                if (!userInitiated) AiHealth.record(false, msg)
                doneNotice(context, tr("投資報告產生失敗"), msg)
            } else {
                state.value = State("完成", start, running = false, background = !userInitiated)
                AiHealth.record(true)
                doneNotice(context, tr("今日投資報告已更新"),
                    listOf(r.stage, r.outlook).filter { it.isNotBlank() }.joinToString("・").ifBlank { r.trend.take(60) },
                    listOfNotNull(
                        listOf(r.stage, r.outlook.takeIf { it.isNotBlank() }?.let { tr("短期") + " " + it }).filterNotNull().filter { it.isNotBlank() }.joinToString("・").ifBlank { null },
                        r.picks.takeIf { it.isNotEmpty() }?.let { ps -> tr("今天可以考慮") + "：" + ps.take(5).joinToString("、") { "${it.symbol} ${it.name}" } },
                        r.holdings.takeIf { it.isNotEmpty() }?.let { hs -> tr("持股動作") + "：" + hs.take(4).joinToString("、") { "${it.symbol} ${it.action}" } },
                        tr("點一下看完整報告"),
                    ))
            }
            r
        } catch (e: CancellationException) {
            if (notifyProgress) cancelProgress(context)
            stopWatchdog()
            throw e
        } catch (e: Exception) {
            stopWatchdog()
            val msg = "${if (refreshOnly) "更新失敗" else "產生失敗"}：${e.message ?: e.javaClass.simpleName}"
            state.value = State(msg, start, running = false, error = msg, background = !userInitiated)
            if (!userInitiated) AiHealth.record(false, msg)
            doneNotice(context, tr(if (refreshOnly) "新聞資料集更新失敗" else "投資報告產生失敗"), msg)
            null
        }
    }

    /** AI 額度用完：等到恢復時間再接續（App 開著就在 App 裡接，否則排背景）。 */
    fun resumeAt(context: Context, at: Long, refreshOnly: Boolean, userInitiated: Boolean) {
        val wait = (at - System.currentTimeMillis()).coerceAtLeast(5_000L)
        // 背景排程一定排一個（App 被關掉也會接續）
        WorkManager.getInstance(context).enqueueUniqueWork(
            "report-continue", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReportWorker>()
                .setInputData(androidx.work.workDataOf("refreshOnly" to refreshOnly, "userInitiated" to userInitiated))
                .setInitialDelay(wait, java.util.concurrent.TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
        // App 開著時直接在 App 裡等時間到就接續
        MoneyApp.instance.appScope.launch {
            kotlinx.coroutines.delay(wait)
            if (appVisible() && state.value?.running != true) startNow(context, refreshOnly)
        }
    }

    /** 還沒做完：App 開在畫面上就馬上在 App 裡接著跑；否則排背景接續。 */
    fun resumeOrLater(context: Context, refreshOnly: Boolean, userInitiated: Boolean) {
        if (appVisible()) MoneyApp.instance.appScope.launch {
            // 等目前這一輪完全結束再開始下一輪
            kotlinx.coroutines.delay(1500)
            while (direct?.isActive == true) kotlinx.coroutines.delay(500)
            startNow(context, refreshOnly)
        } else continueLater(context, refreshOnly, userInitiated)
    }

    /** 這一輪時間用完：1 分鐘後接續（排在目前這輪之後）。 */
    fun continueLater(context: Context, refreshOnly: Boolean, userInitiated: Boolean) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "report-continue", ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<ReportWorker>()
                .setInputData(androidx.work.workDataOf("refreshOnly" to refreshOnly, "userInitiated" to userInitiated))
                .setInitialDelay(1, java.util.concurrent.TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
        )
    }

    private const val NOTICE_ID = 9321
    private const val COMPLETION_NOTICE_ID = 9323

    fun progressNotice(context: Context, state: State, elapsed: Long) {
        if (!com.enwei.moneymanager.notify.AppNotices.allowed(context, com.enwei.moneymanager.notify.NoticeKind.PROGRESS)) return
        context.getSystemService(android.app.NotificationManager::class.java).notify(NOTICE_ID, progressNotification(context, state, elapsed))
    }

    /** 收起進度通知。 */
    fun cancelProgress(context: Context) = context.getSystemService(android.app.NotificationManager::class.java).cancel(NOTICE_ID)


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
            .setSubText(tr("背景進度"))
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openIntent(context))
            .build()

    private fun doneNotice(context: Context, title: String, text: String, lines: List<String> = emptyList()) {
        // 先收起進行中的進度通知（不是前景服務時它不會自動消失）
        cancelProgress(context)
        com.enwei.moneymanager.notify.AppNotices.post(context, com.enwei.moneymanager.notify.NoticeKind.REPORT, COMPLETION_NOTICE_ID, title, text, "advisor:0", lines)
    }

    private fun openIntent(context: Context) = com.enwei.moneymanager.notify.AppNotices.openIntent(context, "advisor:0", 9322)

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(1)
        return if (s >= 60) "${s / 60} 分 ${s % 60} 秒" else "$s 秒"
    }
}

class ReportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val refreshOnly = inputData.getBoolean("refreshOnly", false)
        val userInitiated = inputData.getBoolean("userInitiated", false)
        // 背景自動執行：連續失敗暫停中、或網路不符（只用 Wi-Fi）時先不跑
        if (!userInitiated && (AiHealth.backgroundPaused() || AiHealth.networkProblem() != null)) return Result.success()
        // 看門狗：App 裡那一輪還在跑就不用接手；App 被關掉後才由這裡從斷點接續
        if (ReportJob.activeHere() || (inputData.getBoolean("watchdog", false) && ReportJob.state.value?.running == true)) return Result.success()
        val queued = ReportJob.State("準備中…", System.currentTimeMillis(), refreshOnly = refreshOnly)
        ReportJob.state.value = queued
        setProgress(ReportJob.workProgress(queued))
        try {
            run {
                val running = queued.copy(step = "準備中…")
                ReportJob.state.value = running
                setProgress(ReportJob.workProgress(running))
                // 不使用前景服務：WorkManager 啟動前景服務是在另一個系統元件裡執行，Android 14/15 的限制一出錯就會讓整個 App 閃退，
                // 這裡的 try/catch 攔不到。改用一般的進度通知，並把工作切成每輪 8 分鐘、時間到自動接續。
                ReportJob.execute(
                    applicationContext,
                    notifyProgress = true,
                    refreshOnly = refreshOnly,
                    // 背景工作約 10 分鐘會被系統停止：每輪 8 分鐘，超過就分段接續
                    budgetMs = 8 * 60_000L,
                    userInitiated = userInitiated,
                    onProgress = { state ->
                        // 進度通知由 execute 以同一個編號更新
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
