package com.enwei.moneymanager.work

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.enwei.moneymanager.MoneyApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * AI 排程的健康狀態：
 * - 各模型被限流後的暫停時間（依伺服器的 Retry-After，加一點隨機間隔），App 重開後仍記得。
 * - 每家服務兩次呼叫之間的最短間隔，避免一次送太多被限流。
 * - 背景工作連續失敗 3 次就先暫停，畫面上顯示原因，使用者可手動恢復。
 * - 每一批之前檢查網路（可設定只用 Wi-Fi）。
 */
object AiHealth {
    data class State(
        /** 模型 → 暫停到的時間 */
        val cooldowns: Map<String, Long> = emptyMap(),
        val failures: Int = 0,
        val pausedUntil: Long = 0,
        val pauseReason: String? = null,
    ) {
        fun paused(now: Long = System.currentTimeMillis()) = pausedUntil > now
    }

    private const val PREFS = "ai_health"
    private const val MAX_FAILURES = 3
    private const val PAUSE_MS = 6 * 3600_000L

    private val prefs by lazy { MoneyApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val cool = ConcurrentHashMap<String, Long>()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> get() = _state.also { ensureLoaded() }

    @Volatile private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val now = System.currentTimeMillis()
            prefs.getStringSet("cooldowns", emptySet()).orEmpty().forEach { e ->
                val until = e.substringAfterLast('=').toLongOrNull() ?: return@forEach
                if (until > now) cool[e.substringBeforeLast('=')] = until
            }
            _state.value = State(
                cooldowns = HashMap(cool),
                failures = prefs.getInt("failures", 0),
                pausedUntil = prefs.getLong("pausedUntil", 0),
                pauseReason = prefs.getString("pauseReason", null),
            )
            loaded = true
        }
    }

    private fun publish(f: (State) -> State) {
        ensureLoaded()
        val now = System.currentTimeMillis()
        cool.entries.removeIf { it.value <= now }
        val next = f(_state.value).copy(cooldowns = HashMap(cool))
        _state.value = next
        prefs.edit()
            .putStringSet("cooldowns", cool.entries.map { "${it.key}=${it.value}" }.toSet())
            .putInt("failures", next.failures)
            .putLong("pausedUntil", next.pausedUntil)
            .putString("pauseReason", next.pauseReason)
            .apply()
    }

    // ---------------- 限流與暫停 ----------------

    fun cooldownUntil(model: String): Long {
        ensureLoaded()
        return cool[model] ?: 0
    }

    /** 模型暫停 [ms]（再加 0～20% 的隨機時間，多個工作不會同時重試）。 */
    fun cooldown(model: String, ms: Long) {
        val jitter = (ms * Math.random() * 0.2).toLong()
        val until = System.currentTimeMillis() + ms + jitter
        cool.merge(model, until) { a, b -> maxOf(a, b) }
        publish { it }
    }

    /** 所有可用模型都在暫停時，最早恢復的時間；不是全部暫停時為 null。 */
    fun allCoolingUntil(models: List<String>): Long? {
        if (models.isEmpty()) return null
        val now = System.currentTimeMillis()
        val until = models.map { cooldownUntil(it) }
        return if (until.all { it > now }) until.min() else null
    }

    private val nextCall = ConcurrentHashMap<String, AtomicLong>()

    /**
     * 同一家服務兩次呼叫至少相隔 [minGapMs]（加一點隨機），不使用鎖：
     * 每個呼叫先預約自己的時段再等，同時有很多工作也不會互相卡住。
     */
    suspend fun pace(provider: String, minGapMs: Long) {
        val slot = nextCall.getOrPut(provider) { AtomicLong(0) }
        val gap = minGapMs + (minGapMs * Math.random() * 0.25).toLong()
        var wait: Long
        while (true) {
            val now = System.currentTimeMillis()
            val cur = slot.get()
            val start = maxOf(now, cur)
            if (slot.compareAndSet(cur, start + gap)) { wait = start - now; break }
        }
        if (wait > 0) delay(wait)
    }

    /** 背景工作完成一輪：成功就清除失敗次數；連續失敗 [MAX_FAILURES] 次就暫停背景 AI。 */
    fun record(ok: Boolean, reason: String? = null) = publish { s ->
        if (ok) s.copy(failures = 0, pauseReason = if (s.paused()) s.pauseReason else null)
        else {
            val n = s.failures + 1
            if (n >= MAX_FAILURES) s.copy(
                failures = 0,
                pausedUntil = System.currentTimeMillis() + PAUSE_MS,
                pauseReason = "背景 AI 連續失敗 $MAX_FAILURES 次，先暫停 6 小時：${reason ?: "沒有回應"}",
            ) else s.copy(failures = n, pauseReason = reason)
        }
    }

    /** 背景 AI 是否暫停中（使用者手動操作不受影響）。 */
    fun backgroundPaused(): Boolean = state.value.paused()

    /** 使用者按「恢復」：清除暫停、失敗次數與模型的暫停時間。 */
    fun resume() {
        cool.clear()
        publish { State() }
    }

    // ---------------- 網路 ----------------

    /** 可以執行背景 AI 時回傳 null；否則回傳原因（沒有網路、設定只用 Wi-Fi 但目前是行動網路）。 */
    fun networkProblem(context: Context = MoneyApp.instance, background: Boolean = true): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "沒有網路連線"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "沒有網路連線"
        if (background && MoneyApp.repo.settings.value.aiWifiOnly &&
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        ) return "設定為只用 Wi-Fi，目前是行動網路"
        return null
    }
}

/**
 * 一次背景工作的時間預算：WorkManager 約 10 分鐘會停止背景工作，
 * 超過預算就在批次之間停下，已完成的批次都已寫入，下一輪從斷點接續。
 */
class WorkBudget(private val ms: Long = 8 * 60_000L, private val background: Boolean = true) {
    private val start = System.currentTimeMillis()
    /** 停下的原因（時間用完或網路不符）；null 表示可以繼續。 */
    var stopReason: String? = null
        private set

    fun shouldStop(): Boolean {
        if (stopReason != null) return true
        if (System.currentTimeMillis() - start > ms) stopReason = "這一輪的時間用完，稍後接續"
        else AiHealth.networkProblem(background = background)?.let { stopReason = it }
        return stopReason != null
    }
}
