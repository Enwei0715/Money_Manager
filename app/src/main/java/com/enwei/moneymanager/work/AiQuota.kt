package com.enwei.moneymanager.work

import android.content.Context
import com.enwei.moneymanager.MoneyApp
import java.util.concurrent.ConcurrentHashMap

/**
 * 每個模型自己的額度：每分鐘請求數（RPM）、每分鐘 token 數（TPM）、每日請求數（RPD）。
 * 呼叫前先算這個模型還有沒有空間：有就送；滿了就等一下或換別的模型；當天用完就跳過到重置時間。
 * 上限先用免費方案的已知數字，被 429 拒絕時從回覆裡讀出真正的上限並記住。
 */
object AiQuota {
    /** [tpd]：每日 token 上限（Groq 的 gpt-oss 免費方案 200,000） */
    data class Limits(val rpm: Int, val tpm: Int, val rpd: Int, val tpd: Int = Int.MAX_VALUE)

    /** 免費方案的預設上限（保守估計；實際上限會從 429 回覆學到）。 */
    fun defaults(model: String, gemini: Boolean): Limits {
        val m = model.lowercase()
        return if (gemini) when {
            m.contains("2.0-flash-lite") -> Limits(30, 1_000_000, 200)
            m.contains("2.0-flash") -> Limits(15, 1_000_000, 200)
            m.contains("2.5-flash-lite") -> Limits(15, 250_000, 1000)
            m.contains("flash-lite") -> Limits(15, 250_000, 500)
            m.contains("2.5-flash") -> Limits(10, 250_000, 250)
            m.contains("flash") -> Limits(5, 250_000, 20)
            else -> Limits(5, 250_000, 20)
        } else when {
            // OpenRouter 免費模型：每分鐘 20 次；每日上限是帳戶共用，被拒時整批暫停到 UTC 午夜
            m.endsWith(":free") -> Limits(20, 1_000_000, 1000)
            m.contains("qwen3-32b") -> Limits(60, 6_000, 1_000, 500_000)
            m.contains("llama-4-scout") -> Limits(30, 30_000, 1_000, 500_000)
            m.contains("kimi-k2") -> Limits(60, 10_000, 1_000, 300_000)
            m.contains("8b") -> Limits(30, 6_000, 14_400, 500_000)
            m.contains("70b") -> Limits(30, 12_000, 1_000, 100_000)
            m.contains("gpt-oss") -> Limits(30, 8_000, 1_000, 200_000)
            else -> Limits(30, 6_000, 1_000, 200_000)
        }
    }

    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("ai_quota", Context.MODE_PRIVATE) }
    private val minute = ConcurrentHashMap<String, ArrayDeque<Pair<Long, Int>>>()

    private fun learned(model: String): Limits? = prefs.getString("lim:$model", null)?.split(',')?.mapNotNull { it.toIntOrNull() }
        ?.takeIf { it.size >= 3 }?.let { Limits(it[0], it[1], it[2], it.getOrNull(3) ?: Int.MAX_VALUE) }

    fun limits(model: String, gemini: Boolean): Limits = learned(model) ?: defaults(model, gemini)

    /** 每日額度重置：Gemini 在美西午夜、Groq 用 UTC 日期。 */
    private fun dayKey(gemini: Boolean): String =
        java.time.LocalDate.now(java.time.ZoneId.of(if (gemini) "America/Los_Angeles" else "UTC")).toString()

    fun usedToday(model: String, gemini: Boolean): Int = prefs.getInt("day:${dayKey(gemini)}:$model", 0)
    fun tokensToday(model: String, gemini: Boolean): Int = prefs.getInt("tok:${dayKey(gemini)}:$model", 0)
    private fun exhausted(model: String, gemini: Boolean) = prefs.getBoolean("full:${dayKey(gemini)}:$model", false)

    /**
     * 這次呼叫要等多久（毫秒）才不會超過每分鐘上限；當天已用完回傳 null（換別的模型）。
     * [tokens] 是這次預估的輸入加輸出 token 數。留 10% 安全空間。
     */
    fun waitMs(model: String, gemini: Boolean, tokens: Int): Long? {
        val lim = limits(model, gemini)
        if (exhausted(model, gemini) || usedToday(model, gemini) >= lim.rpd) return null
        // 每日 token 上限快用完（留 5%）：今天先不用這個模型
        if (lim.tpd != Int.MAX_VALUE && tokensToday(model, gemini) + tokens > lim.tpd * 0.95) return null
        val now = System.currentTimeMillis()
        val q = minute.getOrPut(model) { ArrayDeque() }
        synchronized(q) {
            while (q.isNotEmpty() && now - q.first().first > 60_000) q.removeFirst()
            val rpm = (lim.rpm * 0.9).toInt().coerceAtLeast(1)
            val tpm = (lim.tpm * 0.9).toInt()
            var wait = 0L
            if (q.size >= rpm) wait = maxOf(wait, q.elementAt(q.size - rpm).first + 60_000 - now)
            // token：最舊的幾筆過了一分鐘後空出來的量夠不夠
            var sum = q.sumOf { it.second }
            if (sum + tokens > tpm) {
                for ((t, n) in q) {
                    sum -= n
                    if (sum + tokens <= tpm) { wait = maxOf(wait, t + 60_000 - now); break }
                }
                if (sum + tokens > tpm) wait = maxOf(wait, 60_000)   // 單次就超過上限：等滿一分鐘
            }
            return wait.coerceAtLeast(0)
        }
    }

    /** 送出前先登記（佔住這一分鐘的額度與今天的次數）。 */
    fun record(model: String, gemini: Boolean, tokens: Int) {
        val q = minute.getOrPut(model) { ArrayDeque() }
        synchronized(q) { q.addLast(System.currentTimeMillis() to tokens) }
        val k = "day:${dayKey(gemini)}:$model"
        val t = "tok:${dayKey(gemini)}:$model"
        prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).putInt(t, prefs.getInt(t, 0) + tokens).apply()
    }

    /**
     * 從 429 回覆學真正的上限：Groq 寫「tokens per minute (TPM): Limit 6000」；
     * Gemini 寫 quotaId（…PerDay… / …PerMinute… / …InputTokens…）與 quotaValue。每日上限用完就標記到重置。
     */
    fun learnFrom429(model: String, gemini: Boolean, body: String) {
        var l = limits(model, gemini)
        var dailyFull = false
        if (gemini) {
            Regex("\"quotaId\"\\s*:\\s*\"([^\"]+)\"[\\s\\S]*?\"quotaValue\"\\s*:\\s*\"(\\d+)\"").findAll(body).forEach { m ->
                val id = m.groupValues[1]; val v = m.groupValues[2].toIntOrNull() ?: return@forEach
                when {
                    id.contains("PerDay") -> { l = l.copy(rpd = v); dailyFull = true }
                    id.contains("Token") -> l = l.copy(tpm = v)
                    id.contains("PerMinute") -> l = l.copy(rpm = v)
                }
            }
        } else {
            fun lim(key: String) = Regex("$key\\)?:?\\s*Limit\\s*(\\d+)", RegexOption.IGNORE_CASE).find(body)?.groupValues?.get(1)?.toIntOrNull()
            lim("\\(TPM")?.let { l = l.copy(tpm = it) }
            lim("\\(RPM")?.let { l = l.copy(rpm = it) }
            lim("\\(RPD")?.let { l = l.copy(rpd = it); dailyFull = true }
            // 每日 token 上限：記住上限，並把今天的用量記成已滿（Groq 是滾動 24 小時，冷卻時間到會再試）
            lim("\\(TPD")?.let { tpd ->
                l = l.copy(tpd = tpd)
                val used = Regex("Used\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull() ?: tpd
                prefs.edit().putInt("tok:${dayKey(false)}:$model", used).apply()
            }
        }
        prefs.edit().putString("lim:$model", "${l.rpm},${l.tpm},${l.rpd},${l.tpd}").apply()
        if (dailyFull) prefs.edit().putBoolean("full:${dayKey(gemini)}:$model", true).apply()
    }

    /** 今天各模型的用量（有用過或已用完的），給排程資訊顯示。 */
    fun todaySummary(models: List<Pair<String, Boolean>>): List<String> = models.distinct().mapNotNull { (m, g) ->
        val used = usedToday(m, g)
        val full = exhausted(m, g)
        if (used == 0 && !full) null
        else {
            val lim = limits(m, g)
            "$m：今天 $used/${lim.rpd} 次" + (if (lim.tpd != Int.MAX_VALUE) "、約 ${tokensToday(m, g) / 1000}k/${lim.tpd / 1000}k token" else "") +
                if (full) "（已用完，${if (g) "台灣下午 4 點（美西午夜）" else "24 小時內陸續"}恢復）" else ""
        }
    }
}
