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
 * OpenRouter 的免費模型（id 以「:free」結尾）。免費模型會隨時增減，所以每天從
 * https://openrouter.ai/api/v1/models 抓一次清單存起來；抓不到時用內建的備用清單。
 * 依上下文長度分成「大」（≥ 64k，可讀報告、檢討這類大量資料）與一般。
 */
object OpenRouterModels {
    data class FreeModel(val id: String, val context: Int)

    /** 抓不到清單時的備用（都是 OpenRouter 常見的免費模型；下架的會在 404 時自動跳過） */
    private val FALLBACK = listOf(
        FreeModel("deepseek/deepseek-chat-v3-0324:free", 163_840),
        FreeModel("meta-llama/llama-3.3-70b-instruct:free", 131_072),
        FreeModel("qwen/qwen3-235b-a22b:free", 131_072),
        FreeModel("google/gemma-3-27b-it:free", 96_000),
        FreeModel("mistralai/mistral-small-3.2-24b-instruct:free", 96_000),
    )

    /** 回 JSON 比較穩定的模型家族排前面 */
    private val PREFER = listOf("deepseek", "qwen", "llama-3.3", "gemini", "gemma-3", "mistral", "llama-4", "glm", "kimi")

    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("openrouter", Context.MODE_PRIVATE) }

    fun cached(): List<FreeModel> = prefs.getString("free", null)?.split('\n')?.mapNotNull { l ->
        l.split('|').takeIf { it.size == 2 }?.let { FreeModel(it[0], it[1].toIntOrNull() ?: 0) }
    }?.takeIf { it.isNotEmpty() } ?: FALLBACK

    fun lastFetch(): Long = prefs.getLong("at", 0)

    /** 一天最多抓一次；[force] 時立即重抓。回傳免費模型數。 */
    fun refresh(force: Boolean = false): Int {
        if (!force && System.currentTimeMillis() - lastFetch() < 24 * 3600_000L) return cached().size
        val list = runCatching {
            Http.client.newCall(Request.Builder().url("https://openrouter.ai/api/v1/models").build()).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val data = Http.json.parseToJsonElement(r.body?.string().orEmpty()).jsonObject["data"] as? JsonArray ?: return@use null
                data.mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    val id = (o["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                    if (!id.endsWith(":free")) return@mapNotNull null
                    // 只用文字輸入、文字輸出的模型
                    val arch = o["architecture"] as? JsonObject
                    val outMods = (arch?.get("output_modalities") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    if (outMods != null && "text" !in outMods) return@mapNotNull null
                    FreeModel(id, (o["context_length"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt() ?: 0)
                }
            }
        }.getOrNull()
        if (!list.isNullOrEmpty()) prefs.edit().putString("free", list.joinToString("\n") { "${it.id}|${it.context}" })
            .putLong("at", System.currentTimeMillis()).apply()
        return cached().size
    }

    private fun rank(m: FreeModel): Int = PREFER.indexOfFirst { m.id.contains(it, true) }.let { if (it < 0) PREFER.size else it }

    /** 挑幾個模型：[big] 時只要上下文 ≥ 64k 的；依偏好家族、再依上下文長度排序。 */
    fun pick(big: Boolean, n: Int, preferred: String = ""): List<String> {
        val all = cached().filter { !big || it.context >= 64_000 }
            .sortedWith(compareBy<FreeModel> { rank(it) }.thenByDescending { it.context })
            .map { it.id }
        val first = preferred.trim().takeIf { it.isNotBlank() }
        return (listOfNotNull(first) + all).distinct().take(n)
    }
}
