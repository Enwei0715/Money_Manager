package com.enwei.moneymanager.net

import android.content.Context
import com.enwei.moneymanager.MoneyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * 自架 AI 伺服器（OpenAI 相容的 /v1/chat/completions），例如家裡電腦上的 Ollama、LM Studio。
 * 不用金鑰、不用錢；網址與模型只存在這支手機。
 */
object LocalAi {
    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("local_ai", Context.MODE_PRIVATE) }

    /** 伺服器網址，例如 http://192.168.1.10:11434 */
    var url: String
        get() = prefs.getString("url", "").orEmpty()
        set(v) { prefs.edit().putString("url", v.trim().trimEnd('/')).apply() }
    var model: String
        get() = prefs.getString("model", "").orEmpty()
        set(v) { prefs.edit().putString("model", v.trim()).apply() }

    val enabled: Boolean get() = url.startsWith("http") && model.isNotBlank()

    @Volatile var lastError: String = ""

    private val client by lazy {
        Http.client.newBuilder().connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(240, java.util.concurrent.TimeUnit.SECONDS).build()
    }
    private fun base() = if (url.endsWith("/v1")) url else "$url/v1"

    /** 送出一次對話，回傳 JSON 物件（解析不出來回傳 null） */
    suspend fun chat(system: String, user: String, maxTokens: Int, parse: (String) -> JsonObject?): JsonObject? = withContext(Dispatchers.IO) {
        if (!enabled) return@withContext null
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0.1)
            put("max_tokens", maxOf(512, maxTokens * 4))
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        runCatching {
            val req = okhttp3.Request.Builder().url(base() + "/chat/completions")
                .post(body.toRequestBody("application/json".toMediaType())).build()
            client.newCall(req).execute().use { r ->
                val t = r.body?.string().orEmpty()
                if (!r.isSuccessful) error("HTTP ${r.code}：${t.take(120)}")
                val o = Http.json.parseToJsonElement(t) as JsonObject
                val content = (((o["choices"] as JsonArray)[0] as JsonObject)["message"] as JsonObject)["content"] as JsonPrimitive
                parse(content.content) ?: error("回覆不是 JSON")
            }
        }.onSuccess { lastError = "" }
            .onFailure { lastError = it.message ?: it.javaClass.simpleName }
            .getOrNull()
    }

    /** 伺服器上的模型清單（讓使用者挑） */
    suspend fun models(): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(okhttp3.Request.Builder().url(base() + "/models").build()).execute().use { r ->
                if (!r.isSuccessful) error("HTTP ${r.code}")
                val o = Http.json.parseToJsonElement(r.body?.string().orEmpty()) as JsonObject
                (o["data"] as JsonArray).mapNotNull { ((it as? JsonObject)?.get("id") as? JsonPrimitive)?.content }
            }
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrDefault(emptyList())
    }

    /** 測試：成功回傳 null，失敗回傳原因 */
    suspend fun test(parse: (String) -> JsonObject?): String? {
        if (!url.startsWith("http")) return "網址要是 http:// 或 https:// 開頭"
        if (model.isBlank()) return "請選一個模型"
        return if (chat("Reply with JSON only.", "Return {\"ok\": true} as JSON.", 20, parse) != null) null else lastError.ifBlank { "沒有回應" }
    }
}
