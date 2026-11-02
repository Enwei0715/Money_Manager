package com.enwei.moneymanager.net

import android.content.Context
import com.enwei.moneymanager.MoneyApp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Request

/**
 * NVIDIA NIM（build.nvidia.com）的模型：免費、不限每日次數（每分鐘約 40 次）。
 * 模型清單每天從 https://integrate.api.nvidia.com/v1/models 抓一次，只保留下面偏好清單裡還在提供的模型；
 * 抓不到時直接用偏好清單（下架的模型會在 404 時自動跳過一天）。
 */
object NvidiaModels {
    /**
     * 每類工作的偏好（依序）：用關鍵字比對實際提供的模型 id，NVIDIA 改版號或下架時自動對到還在的版本。
     * 簡短判讀：快、JSON 穩定；大量資料：長上下文；推理整合：大模型。
     */
    val FAST = listOf("nemotron-3.5-lightning", "llama-3.3-70b-instruct", "deepseek-v4", "qwen3-next-80b-a3b-instruct", "llama-3.1-70b-instruct", "gemma-3-27b-it", "nemotron-3.5-bolt")
    val LONG = listOf("deepseek-v4", "qwen3-next-80b-a3b-instruct", "nemotron-3.5-lightning", "mistral-large", "llama-4-maverick", "kimi-k2", "llama-3.3-70b-instruct")
    val HEAVY = listOf("deepseek-v4", "mistral-large", "kimi-k2", "deepseek-v3", "qwen3-235b", "gpt-oss-120b", "llama-3.1-70b-instruct", "llama-3.3-70b-instruct")

    /** 還沒抓到清單時用的 id（確定長期存在的） */
    private val FALLBACK = mapOf(
        "deepseek-v4" to "deepseek-ai/deepseek-v4.1-flash",
        "mistral-large" to "mistralai/mistral-large-4",
        "nemotron-3.5-lightning" to "nvidia/nemotron-3.5-lightning-30b-a3b",
        "nemotron-3.5-bolt" to "nvidia/nemotron-3.5-bolt-e4b",
        "llama-3.3-70b-instruct" to "meta/llama-3.3-70b-instruct",
        "llama-3.1-70b-instruct" to "meta/llama-3.1-70b-instruct",
        "llama-3.1-405b" to "meta/llama-3.1-405b-instruct",
        "deepseek-v3" to "deepseek-ai/deepseek-v3.1",
        "kimi-k2" to "moonshotai/kimi-k2-instruct",
        "qwen3-235b" to "qwen/qwen3-235b-a22b",
        "gpt-oss-120b" to "openai/gpt-oss-120b",
    )

    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("nvidia", Context.MODE_PRIVATE) }

    /** 上次抓到的模型 id（空集合＝還沒抓到，全部當作可用） */
    fun available(): Set<String> = prefs.getStringSet("ids", emptySet()).orEmpty()

    /** 拒絕或不存在的模型：7 天內不再挑 */
    fun ban(id: String) = prefs.edit().putLong("ban:$id", System.currentTimeMillis() + 7 * 86_400_000L).apply()
    fun banned(id: String) = prefs.getLong("ban:$id", 0) > System.currentTimeMillis()

    fun refresh(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - prefs.getLong("at", 0) < 24 * 3600_000L) return
        val ids = runCatching {
            val key = MoneyApp.repo.settings.value.nvidiaKey.trim()
            val req = Request.Builder().url("https://integrate.api.nvidia.com/v1/models")
                .apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }.build()
            Http.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                (Http.json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["data"] as? JsonArray)
                    ?.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }?.toSet()
            }
        }.getOrNull()
        if (!ids.isNullOrEmpty()) prefs.edit().putStringSet("ids", ids).putLong("at", System.currentTimeMillis()).apply()
    }

    /** 依工作類型挑 [n] 個（[preferred] 為使用者指定的優先模型）；排除視覺、嵌入、安全審查等非對話模型 */
    fun pick(prefs: List<String>, n: Int, preferred: String = ""): List<String> {
        val av = available().filter { id ->
            listOf("embed", "vision", "-vl", "guard", "reward", "rerank", "parse", "retriever", "safety", "clip", "ocr", "coder", "flux", "image", "embedding").none { id.contains(it, true) }
        }
        val found = prefs.mapNotNull { k ->
            if (av.isEmpty()) FALLBACK[k]?.takeIf { !banned(it) }
            // 同一個關鍵字有多個版本時取最新（字串排序最後的，例如 deepseek-v3.1 > deepseek-v3）
            else av.filter { it.contains(k, true) && !banned(it) }.maxOrNull()
        }
        return (listOfNotNull(preferred.trim().takeIf { it.isNotBlank() }) + found).distinct().take(n)
    }
}
