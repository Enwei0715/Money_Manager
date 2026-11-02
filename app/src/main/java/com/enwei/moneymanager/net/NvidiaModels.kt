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
    /** 簡短的判讀、分類、命名（快、JSON 穩定） */
    val FAST = listOf(
        "meta/llama-3.3-70b-instruct",
        "qwen/qwen3-next-80b-a3b-instruct",
        "mistralai/mistral-small-3.1-24b-instruct-2503",
        "google/gemma-3-27b-it",
        "meta/llama-4-maverick-17b-128e-instruct",
    )

    /** 大量資料但工作簡單（新聞標註、重點整理、錢包畫面）：長上下文 */
    val LONG = listOf(
        "qwen/qwen3-next-80b-a3b-instruct",
        "meta/llama-4-maverick-17b-128e-instruct",
        "deepseek-ai/deepseek-v3.1",
        "moonshotai/kimi-k2-instruct",
        "meta/llama-3.3-70b-instruct",
    )

    /** 需要推理與整合（報告、檢討、對話、不明花費） */
    val HEAVY = listOf(
        "deepseek-ai/deepseek-v3.1",
        "moonshotai/kimi-k2-instruct",
        "qwen/qwen3-235b-a22b",
        "openai/gpt-oss-120b",
        "nvidia/llama-3.3-nemotron-super-49b-v1.5",
        "meta/llama-3.3-70b-instruct",
    )

    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("nvidia", Context.MODE_PRIVATE) }

    /** 上次抓到的模型 id（空集合＝還沒抓到，全部當作可用） */
    fun available(): Set<String> = prefs.getStringSet("ids", emptySet()).orEmpty()

    fun refresh(force: Boolean = false) {
        if (!force && System.currentTimeMillis() - prefs.getLong("at", 0) < 24 * 3600_000L) return
        val ids = runCatching {
            Http.client.newCall(Request.Builder().url("https://integrate.api.nvidia.com/v1/models").build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                (Http.json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["data"] as? JsonArray)
                    ?.mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull }?.toSet()
            }
        }.getOrNull()
        if (!ids.isNullOrEmpty()) prefs.edit().putStringSet("ids", ids).putLong("at", System.currentTimeMillis()).apply()
    }

    /** 依工作類型挑 [n] 個（[preferred] 為使用者指定的優先模型） */
    fun pick(list: List<String>, n: Int, preferred: String = ""): List<String> {
        val av = available()
        val ok = list.filter { av.isEmpty() || it in av }.ifEmpty { list }
        return (listOfNotNull(preferred.trim().takeIf { it.isNotBlank() }) + ok).distinct().take(n)
    }
}
