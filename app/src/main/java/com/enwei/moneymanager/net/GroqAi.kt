package com.enwei.moneymanager.net

import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.ParsedKind
import com.enwei.moneymanager.core.ParsedTransaction
import com.enwei.moneymanager.core.ParsedType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Groq（OpenAI 相容 API）的 AI 輔助：只在規則判斷不出來時才呼叫，失敗或超過額度時安靜地退回規則結果。
 * 只會送出「已啟用的銀行／支付 App」的通知文字或商家名稱，以及使用者的分類名稱；不送帳戶、餘額。
 */
object GroqAi {
    private const val URL = "https://api.groq.com/openai/v1/chat/completions"

    /** 最近一次錯誤（設定頁顯示用）。 */
    @Volatile var lastError: String? = null
        private set

    /** 同一個商家只問一次（App 執行期間）。 */
    private val nameCache = ConcurrentHashMap<String, Pair<String?, Long?>>()

    val enabled: Boolean
        get() = MoneyApp.repo.settings.value.let { it.aiEnabled && (it.groqKey.isNotBlank() || it.geminiKey.isNotBlank()) }

    enum class Provider(val label: String) { GROQ("Groq"), GEMINI("Gemini") }

    /**
     * App 裡所有用到 AI 的工作與分配；大量輸入優先使用較大上下文模型。[lite] 為用較輕量的模型即可。
     */
    enum class AiTask(val label: String, val desc: String, val lite: Boolean, val userText: Boolean = true) {
        NOTIFY("通知判讀", "規則看不懂的銀行／支付通知", true, userText = false),
        NAMING("商家命名與分類", "代碼商家換成好讀名稱、挑分類", true),
        TEACH("教它辨識", "先幫你判斷通知的類型與帳戶", true),
        DUPLICATE("重複交易判斷", "通知、錢包、手動重複記的同一筆", true),
        COMPANY("公司說明", "股票資訊頁的公司業務", true),
        WALLET("錢包畫面整理", "版面規則看不懂的交易紀錄畫面", true, userText = false),
        NEWS_TAG("新聞標註", "每則新聞的相關個股、題材、多空、重要性", true),
        DIGEST("新聞重點整理", "把每天每個題材的所有新聞整理成重點", true),
        UNKNOWN("整理不明花費", "依習慣推測不明交易的分類", false),
        REVIEW("收支檢討", "逐筆讀整期交易、分析與提問", false),
        REPORT("每日投資報告", "讀整個新聞資料集產生報告", false),
        CHAT("問 AI（股票）", "對話時讀資料集與你的狀況", false),
    }

    /** 預設模型。llama-3.1-8b-instant 已於 2026/8/16 停止提供給免費與開發者方案。 */
    const val DEFAULT_MODEL = "openai/gpt-oss-20b"
    /** 較難的推估（整理「不明」花費）用的大模型；失敗時退回 [DEFAULT_MODEL] */
    const val HARD_MODEL = "openai/gpt-oss-120b"
    /** 已停止提供的模型，自動改用預設模型 */
    val RETIRED_MODELS = setOf("llama-3.1-8b-instant", "llama3-8b-8192", "llama-3.3-70b-versatile")

    private enum class Fail { NONE, MODEL_GONE, NO_JSON_MODE }

    /** 呼叫模型並取得 JSON 物件回覆；失敗回傳 null。模型下架時自動改用預設模型、不支援 JSON 模式時改用一般模式重試。 */
    /** 一個可用的模型（哪一家、哪個模型、是否為推理較深的大模型）。 */
    data class Route(val provider: Provider, val model: String, val big: Boolean)

    /** 需要推理、整合大量資料的工作 */
    private val HEAVY = setOf(AiTask.UNKNOWN, AiTask.REVIEW, AiTask.REPORT, AiTask.CHAT)

    /**
     * 自動選模型：依工作的難度與這次要送的資料量排出候選順序，前一個失敗或被限流就換下一個。
     * - 簡單又短（通知、命名、重複判斷…）：Groq 20b（最快）→ Gemini Lite
     * - 簡單但很長（新聞標註、錢包畫面、新聞重點）：Gemini Lite → Gemini Flash → Groq（只處理符合預算的分段）
     * - 複雜但放得進 Groq：Groq 120b（推理）→ Gemini Flash → Gemini Lite
     * - 複雜又很長（報告、檢討、對話）：Gemini Flash → Gemini Lite → Groq（超出預算則拒絕，不截斷）
     * 額度用完的模型會暫停一陣子，直接跳過。
     */
    fun routes(task: AiTask, inputLen: Int): List<Route> {
        val s = MoneyApp.repo.settings.value
        val g20 = Route(Provider.GROQ, s.aiModel.trim().ifBlank { DEFAULT_MODEL }.let { if (it in RETIRED_MODELS) DEFAULT_MODEL else it }, false)
        val g120 = Route(Provider.GROQ, s.aiModelHard.trim().ifBlank { HARD_MODEL }, true)
        val lite = Route(Provider.GEMINI, s.geminiLiteModel.trim().ifBlank { "gemini-flash-lite-latest" }, false)
        val flash = Route(Provider.GEMINI, s.geminiModel.trim().ifBlank { "gemini-flash-latest" }, true)
        val heavy = task in HEAVY
        val fitsGroq = inputLen <= 5000
        val order = when {
            !heavy && fitsGroq -> listOf(g20, lite, g120)
            !heavy -> listOf(lite, flash, g20)
            fitsGroq -> listOf(g120, flash, lite, g20)
            else -> listOf(flash, lite, g120)
        }
        return order.filter { if (it.provider == Provider.GROQ) s.groqKey.isNotBlank() else s.geminiKey.isNotBlank() }
    }

    /** 這項工作會先送到 Gemini（可以一次送大量資料），用來決定要送完整資料還是精簡版。 */
    fun bigContext(task: AiTask): Boolean {
        val now = System.currentTimeMillis()
        return routes(task, Int.MAX_VALUE).firstOrNull { com.enwei.moneymanager.work.AiHealth.cooldownUntil(it.model) <= now }?.provider == Provider.GEMINI
    }

    private suspend fun chatJson(
        system: String, user: String, maxTokens: Int = 200, hard: Boolean = false,
        /** 回覆（含推理）的 token 上限；免費方案每分鐘上限約 8,000，輸入加回覆不能超過 */
        cap: Int = 6000, effort: String = "medium",
        task: AiTask = AiTask.NAMING,
    ): JsonObject? = withContext(Dispatchers.IO) {
        val s = MoneyApp.repo.settings.value
        // 回覆語言跟隨 App 介面語言（JSON 的鍵、清單裡指定的選項值、代號與商家原名不翻）
        val system = if (com.enwei.moneymanager.ui.I18n.english && task.userText)
            system + " Write every human-readable text value in English (keep JSON keys, values chosen from given lists, stock codes and original names unchanged)."
        else system
        val all = routes(task, system.length + user.length)
        if (all.isEmpty()) return@withContext null
        val now = System.currentTimeMillis()
        // 剛被限流或沒回應的模型先跳過；全部都在暫停就照原順序再試
        val ready = all.filter { com.enwei.moneymanager.work.AiHealth.cooldownUntil(it.model) <= now }.ifEmpty { all }
        for (r in ready) {
            lastError = null
            retryAfterMs = null
            // 同一家服務的呼叫之間留間隔（對話除外，送出就立刻問）
            if (task != AiTask.CHAT) com.enwei.moneymanager.work.AiHealth.pace(r.provider.name, if (r.provider == Provider.GEMINI) 4_000 else 1_500)
            val out = when (r.provider) {
                Provider.GEMINI -> callGemini(s.geminiKey.trim(), r.model, system, user,
                    // Gemini 的思考也算在輸出長度裡：大模型多留空間
                    maxOf(2048, maxTokens * 4).let { if (r.big) maxOf(it, 12_000) else it }, timeoutSec = if (r.big) 90 else 45)
                // 不加全域鎖：鎖不能重複取得，外層若已持有就會永遠卡住；限流交給下面的暫停機制與各工作的批次間隔
                Provider.GROQ -> groqJson(s, system, user, maxTokens, r.big || hard, cap, if (r.big) effort else "low", forceModel = r.model)
            }
            if (out != null) return@withContext out
            // 額度用完或沒回應：這個模型先暫停，之後的呼叫直接用下一個
            val err = lastError.orEmpty()
            // 伺服器有說多久後再試（Retry-After）就照它，否則用預設時間；再加隨機間隔，暫停時間會保存到 App 重開後
            if ("429" in err || "額度" in err) com.enwei.moneymanager.work.AiHealth.cooldown(r.model,
                retryAfterMs?.coerceIn(5_000L, 3600_000L) ?: if (r.provider == Provider.GEMINI) 15 * 60_000L else 2 * 60_000L)
            else if ("逾時" in err || "timeout" in err.lowercase() || "503" in err || "404" in err) com.enwei.moneymanager.work.AiHealth.cooldown(r.model,
                retryAfterMs?.coerceIn(5_000L, 3600_000L) ?: 5 * 60_000L)
        }
        null
    }

    /** 最近一次被限流時，伺服器建議等待的時間（Retry-After 標頭或 Gemini 的 retryDelay） */
    @Volatile private var retryAfterMs: Long? = null

    private fun parseRetryAfter(header: String?, body: String): Long? {
        header?.trim()?.toDoubleOrNull()?.let { return (it * 1000).toLong() }
        // Gemini：「"retryDelay": "31s"」；Groq 文字：「Please try again in 1m2.5s」
        Regex("\"retryDelay\"\\s*:\\s*\"(\\d+(?:\\.\\d+)?)s\"").find(body)?.let { return (it.groupValues[1].toDouble() * 1000).toLong() }
        Regex("try again in (?:(\\d+)m)?(\\d+(?:\\.\\d+)?)s").find(body)?.let {
            return ((it.groupValues[1].toLongOrNull() ?: 0) * 60_000 + it.groupValues[2].toDouble() * 1000).toLong()
        }
        return null
    }

    /** 所有可用模型都被限流時，最早恢復的時間（狀態列顯示用）。 */
    fun coolingUntil(): Long? {
        val models = AiTask.entries.flatMap { t -> routes(t, 0).map { it.model } }.distinct()
        return com.enwei.moneymanager.work.AiHealth.allCoolingUntil(models)
    }

    private fun groqJson(
        s: com.enwei.moneymanager.data.AppSettings, systemIn: String, userIn: String, maxTokens: Int, hard: Boolean, cap: Int, effort: String,
        forceModel: String? = null,
    ): JsonObject? {
        // Groq 每分鐘約 8,000 tokens：輸入超出預算時拒絕送出，不可靜默截斷資料。
        val reserve = minOf(cap, maxOf(if (hard) 4096 else 1024, maxTokens * 4))
        val budget = 7600 - reserve
        val userBudget = maxOf(400, budget - systemIn.length)
        if (systemIn.length > budget - 800 || userIn.length > userBudget) {
            lastError = "資料超過 Groq 可安全讀取範圍，未送出；請分批處理或啟用 Gemini"
            return null
        }
        val system = systemIn
        val user = userIn
        // 指定模型時只用那個（自動選模型會自己排下一個）；否則難題先用大模型，不行再用一般模型
        if (forceModel == null && hard) {
            val big = s.aiModelHard.trim().ifBlank { HARD_MODEL }
            val (r, _) = call(s.groqKey.trim(), big, true, system, user, maxTokens, effort = effort, cap = cap)
            if (r != null) return r
        }
        var model = forceModel ?: s.aiModel.trim().ifBlank { DEFAULT_MODEL }.let { if (it in RETIRED_MODELS) DEFAULT_MODEL else it }
        var jsonMode = true
        repeat(3) {
            val (result, fail) = call(s.groqKey.trim(), model, jsonMode, system, user, maxTokens, effort = effort, cap = cap)
            when (fail) {
                Fail.NONE -> return result
                Fail.MODEL_GONE -> if (forceModel == null && model != DEFAULT_MODEL) {
                    model = DEFAULT_MODEL
                    MoneyApp.repo.settings.update { it.copy(aiModel = DEFAULT_MODEL) }
                } else return null
                Fail.NO_JSON_MODE -> if (jsonMode) jsonMode = false else return null
            }
        }
        return null
    }

    /** AI 呼叫可能要讀大量資料，等久一點。 */
    private val aiClient by lazy { Http.client.newBuilder().readTimeout(150, java.util.concurrent.TimeUnit.SECONDS).build() }

    /** Google Gemini（generateContent，JSON 模式）。失敗回傳 null 並記下原因。 */
    private fun callGemini(key: String, model: String, system: String, user: String, maxOut: Int, timeoutSec: Long = 90): JsonObject? {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", system) }) }) })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", user) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("responseMimeType", "application/json")
                put("temperature", 0.2)
                put("maxOutputTokens", minOf(maxOut, 16384))
            })
        }.toString()
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key)
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            aiClient.newBuilder().callTimeout(timeoutSec, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    if (resp.code == 429 || resp.code == 503) retryAfterMs = parseRetryAfter(resp.header("Retry-After"), text)
                    lastError = when (resp.code) {
                        400 -> if (text.contains("API_KEY") || text.contains("API key")) "Gemini 金鑰無效" else "Gemini 請求錯誤：${text.take(140)}"
                        401, 403 -> "Gemini 金鑰無效或沒有權限（${resp.code}）"
                        404 -> "Gemini 模型「$model」無法使用（404）"
                        429 -> "超過 Gemini 的免費額度（429），改用 Groq"
                        else -> "Gemini HTTP ${resp.code}：${text.take(140)}"
                    }
                    return@use null
                }
                val root = Http.json.parseToJsonElement(text).jsonObject
                val parts = ((root["candidates"] as? kotlinx.serialization.json.JsonArray)?.firstOrNull() as? JsonObject)
                    ?.get("content")?.let { it as? JsonObject }?.get("parts") as? kotlinx.serialization.json.JsonArray
                val content = parts?.mapNotNull { p ->
                    val o = p as? JsonObject ?: return@mapNotNull null
                    if ((o["thought"] as? JsonPrimitive)?.contentOrNull == "true") null else (o["text"] as? JsonPrimitive)?.contentOrNull
                }?.joinToString("")
                if (content.isNullOrBlank()) { lastError = "Gemini 沒有回覆內容（可能被安全過濾或長度不足）"; return@use null }
                val a = content.indexOf('{'); val b = content.lastIndexOf('}')
                if (a < 0 || b <= a) { lastError = "Gemini 回覆不是 JSON"; return@use null }
                lastError = null
                Http.json.parseToJsonElement(content.substring(a, b + 1)).jsonObject
            }
        }.getOrElse {
            lastError = if (it is java.io.InterruptedIOException) "Gemini 逾時（$timeoutSec 秒沒有回應）" else "Gemini：" + (it.message ?: it.javaClass.simpleName)
            null
        }
    }

    /** 設定頁：分別測試兩家的連線。回傳 null 表示成功。 */
    suspend fun testProvider(p: Provider): String? = withContext(Dispatchers.IO) {
        val s = MoneyApp.repo.settings.value
        val r = when (p) {
            Provider.GROQ -> if (s.groqKey.isBlank()) return@withContext "還沒填 Groq 金鑰"
            else groqJson(s, "Reply with JSON only.", "Return {\"ok\": true} as JSON.", 20, false, 6000, "low")
            Provider.GEMINI -> if (s.geminiKey.isBlank()) return@withContext "還沒填 Gemini 金鑰"
            else callGemini(s.geminiKey.trim(), s.geminiLiteModel.trim().ifBlank { "gemini-flash-lite-latest" }, "Reply with JSON only.", "Return {\"ok\": true} as JSON.", 256)
        }
        if (r != null) null else lastError ?: "沒有回應"
    }

    private fun call(
        key: String, model: String, jsonMode: Boolean, system: String, user: String, maxTokens: Int, effort: String = "low",
        cap: Int = 6000,
    ): Pair<JsonObject?, Fail> {
        // gpt-oss 等推理模型的思考過程也算在 max_tokens 裡，要多留空間並把推理強度調低
        val reasoning = model.startsWith("openai/gpt-oss") || model.contains("qwen3") || model.contains("deepseek")
        val body = buildJsonObject {
            put("model", model)
            put("temperature", if (reasoning) 0.2 else 0.0)
            // 免費方案每分鐘 token 上限約 8,000（含輸入），回覆上限不超過 6,000
            put("max_tokens", minOf(cap, if (reasoning) maxOf(if (effort == "low") 1024 else 4096, maxTokens * 4) else maxTokens))
            if (reasoning && model.startsWith("openai/gpt-oss")) put("reasoning_effort", effort)
            if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val req = Request.Builder().url(URL)
            .header("Authorization", "Bearer $key")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    if (resp.code == 429 || resp.code == 503) retryAfterMs = parseRetryAfter(resp.header("Retry-After"), text)
                    lastError = when (resp.code) {
                        401 -> "API 金鑰無效（401）"
                        429 -> "超過 Groq 的使用額度（429），稍後會自動恢復"
                        404 -> "模型「$model」無法使用（404）"
                        else -> "HTTP ${resp.code}：${text.take(160)}"
                    }
                    val fail = when {
                        resp.code == 404 || text.contains("model_not_found") || text.contains("decommissioned") -> Fail.MODEL_GONE
                        resp.code == 400 && jsonMode && (text.contains("response_format") || text.contains("json")) -> Fail.NO_JSON_MODE
                        else -> null
                    }
                    return@use null to (fail ?: Fail.NONE)
                }
                val root = Http.json.parseToJsonElement(text).jsonObject
                val content = root["choices"]
                    ?.let { (it as? kotlinx.serialization.json.JsonArray)?.firstOrNull() }
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.let { (it as? JsonPrimitive)?.contentOrNull }
                if (content == null) { lastError = "模型沒有回覆內容（可能是回覆長度不足）"; return@use null to Fail.NONE }
                // 只取第一個 { 到最後一個 } 之間，容許模型在 JSON 前後多說幾句
                val a = content.indexOf('{'); val b = content.lastIndexOf('}')
                if (a < 0 || b <= a) { lastError = "模型回覆不是 JSON"; return@use null to Fail.NONE }
                lastError = null
                Http.json.parseToJsonElement(content.substring(a, b + 1)).jsonObject to Fail.NONE
            }
        }.getOrElse {
            lastError = if (it is java.io.InterruptedIOException) "Groq 逾時（沒有回應）" else "Groq：" + (it.message ?: it.javaClass.simpleName)
            null to Fail.NONE
        }
    }

    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    /** 設定頁的連線測試：回傳 null 表示成功，否則為錯誤訊息。 */
    suspend fun test(): String? {
        val r = chatJson("Reply with JSON only.", "Return {\"ok\": true} as JSON.", 20)
        return if (r != null) null else lastError ?: "沒有回應"
    }

    /**
     * 幫一筆消費取簡潔的商家名稱並挑分類。[categories] 為 (分類 id, 「大分類 / 子分類」)。
     * 回傳 (名稱, 分類 id)，任一項可能為 null。
     */
    suspend fun nameAndCategory(merchant: String, hour: Int, categories: List<Pair<Long, String>>): Pair<String?, Long?> {
        if (!enabled || merchant.isBlank()) return null to null
        nameCache[merchant + "@" + hour / 6]?.let { return it }
        val list = categories.joinToString("\n") { "${it.first}: ${it.second}" }
        val system = "你是台灣記帳 App 的助理。根據消費的商家名稱，給出簡短好讀的商家名稱（繁體中文或原品牌名，去掉分店、代碼、星號），" +
            "並從使用者的分類清單選一個最合適的分類 id；沒有合適的就填 null。只回 JSON：{\"name\": string, \"category_id\": number|null}。"
        val user = "商家：$merchant\n消費時間：${hour} 點\n分類清單（id: 名稱）：\n$list"
        val r = chatJson(system, user) ?: return null to null
        val name = r.str("name")?.take(30)
        val id = r.str("category_id")?.toDoubleOrNull()?.toLong()?.takeIf { id -> categories.any { it.first == id } }
        return (name to id).also { nameCache[merchant + "@" + hour / 6] = it }
    }

    /** 判斷每組（A、B）是不是同一筆交易，同一筆時給較精確的名稱與分類。[items] 為 (組別代號, 說明)。 */
    suspend fun judgeDuplicates(items: List<Pair<String, String>>, categories: List<Pair<Long, String>>): Map<String, com.enwei.moneymanager.data.DupJudgement> {
        if (!enabled || items.isEmpty()) return emptyMap()
        val system = "你是台灣記帳 App 的助理。每組 A、B 是金額相同、時間相近的兩筆紀錄，可能是同一筆消費被通知和錢包畫面（或手動）各記了一次，" +
            "也可能真的是兩筆（例如同一天買兩張一樣的票）。判斷是否同一筆；若是，從兩筆的名稱與原文整理出最精確、好讀的交易名稱（例如店名），並從分類清單挑一個。" +
            "理由 15 字以內。只回 JSON：{\"results\": [{\"id\": string, \"same\": boolean, \"name\": string|null, \"category_id\": number|null, \"reason\": string}]}"
        val user = "分類清單（id: 名稱）：\n" + categories.joinToString("\n") { "${it.first}: ${it.second}" } +
            "\n\n" + items.joinToString("\n") { "[${it.first}] ${it.second}" }
        val r = chatJson(system, user, 80 + 60 * items.size, task = AiTask.DUPLICATE) ?: return emptyMap()
        val arr = r["results"] as? kotlinx.serialization.json.JsonArray ?: return emptyMap()
        val valid = categories.map { it.first }.toSet()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            if (items.none { it.first == id }) return@mapNotNull null
            id to com.enwei.moneymanager.data.DupJudgement(
                same = o.str("same") == "true", name = o.str("name")?.take(30),
                categoryId = o.str("category_id")?.toDoubleOrNull()?.toLong()?.takeIf { it in valid }, reason = o.str("reason")?.take(40) ?: "",
            )
        }.toMap()
    }

    /** 一筆要請 AI 分類的交易：[desc] 為交易內容（日期時間、金額、帳戶、說明），[hints] 為類似情境過去常用的分類。 */
    data class Unsorted(val id: Long, val desc: String, val hints: String)

    /**
     * 幫一批「不明」交易挑分類（一次最多約 10 筆，節省請求次數）。
     * 回傳 交易 id → (分類 id, 理由)；判斷不出來的不回傳。只接受清單內的分類 id。
     */
    suspend fun suggestCategories(items: List<Unsorted>, categories: List<Pair<Long, String>>): Map<Long, Pair<Long, String>> {
        if (!enabled || items.isEmpty()) return emptyMap()
        val system = "你是台灣記帳 App 的助理，幫使用者把分類為「不明」的交易歸到最可能的分類。" +
            "依交易的時間、金額、帳戶、說明，以及「這個人在類似情境的習慣」推測；沒有把握就填 null，不要硬猜。" +
            "理由用繁體中文 15 字以內。只回 JSON：{\"results\": [{\"id\": number, \"category_id\": number|null, \"reason\": string}]}"
        val user = "分類清單（id: 名稱）：\n" + categories.joinToString("\n") { "${it.first}: ${it.second}" } +
            "\n\n交易：\n" + items.joinToString("\n") { "[${it.id}] ${it.desc}｜類似情境常用：${it.hints.ifBlank { "無" }}" }
        val r = chatJson(system, user, 60 + 50 * items.size, hard = true, task = AiTask.UNKNOWN) ?: return emptyMap()
        val arr = r["results"] as? kotlinx.serialization.json.JsonArray ?: return emptyMap()
        val valid = categories.map { it.first }.toSet()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val id = o.str("id")?.toDoubleOrNull()?.toLong() ?: return@mapNotNull null
            val cat = o.str("category_id")?.toDoubleOrNull()?.toLong()?.takeIf { it in valid } ?: return@mapNotNull null
            if (items.none { it.id == id }) return@mapNotNull null
            id to (cat to (o.str("reason")?.take(40) ?: ""))
        }.toMap()
    }

    /** 規則看不懂的銀行／支付通知：請 AI 判讀是否為交易與內容。 */
    suspend fun parseNotification(title: String?, text: String?, now: LocalDateTime = LocalDateTime.now()): ParsedTransaction? {
        if (!enabled) return null
        val full = listOfNotNull(title, text).joinToString("\n")
        if (full.none { it.isDigit() }) return null
        val system = "你是台灣記帳 App 的通知判讀器。判斷這則銀行或支付 App 的通知是否代表一筆已完成的金錢進出" +
            "（驗證碼、登入、行銷、優惠、帳單提醒、未完成的交易都不是）。只回 JSON：" +
            "{\"is_transaction\": boolean, \"type\": \"expense\"|\"income\"|\"transfer_out\"|\"transfer_in\", " +
            "\"amount\": number, \"currency\": \"TWD\"等三碼代碼, \"merchant\": string|null, \"datetime\": \"yyyy-MM-dd HH:mm\"|null, " +
            "\"sure\": boolean（金額、收支方向、商家或對象都清楚才填 true，有任何猜測填 false）}。" +
            "轉出到別的帳戶用 transfer_out，從別的帳戶轉入用 transfer_in。今天是 ${now.toLocalDate()}。"
        val r = chatJson(system, full, task = AiTask.NOTIFY) ?: return null
        if (r.str("is_transaction") != "true") return null
        val amount = r.str("amount")?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 && it < 1e9 } ?: return null
        val (type, kind) = when (r.str("type")) {
            "income" -> ParsedType.INCOME to ParsedKind.NORMAL
            "expense" -> ParsedType.EXPENSE to ParsedKind.NORMAL
            "transfer_out" -> ParsedType.EXPENSE to ParsedKind.TRANSFER_OUT
            "transfer_in" -> ParsedType.INCOME to ParsedKind.TRANSFER_IN
            else -> return null
        }
        val at = r.str("datetime")?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull() }
            ?.takeIf { !it.isAfter(now.plusMinutes(5)) && it.isAfter(now.minusDays(7)) }
        return ParsedTransaction(
            type = type, amountMinor = Math.round(amount * 100), currency = r.str("currency")?.uppercase()?.take(3) ?: "TWD",
            merchant = r.str("merchant")?.take(40), cardLast4 = null, kind = kind, occurredAt = at,
            confident = r.str("sure") == "true",
        )
    }

    data class NotificationBatchResult(
        val parsed: Map<Int, ParsedTransaction>,
        val handled: Set<Int>,
    )

    /** 同一短時間收到的多則交易候選一起判讀；索引讓每一筆結果仍可對回原通知。 */
    suspend fun parseNotificationBatch(
        notices: List<Triple<Int, String?, String?>>,
        now: LocalDateTime = LocalDateTime.now(),
    ): NotificationBatchResult {
        if (!enabled || notices.isEmpty()) return NotificationBatchResult(emptyMap(), emptySet())
        val output = LinkedHashMap<Int, ParsedTransaction>()
        val handled = LinkedHashSet<Int>()
        val groups = ArrayList<List<Triple<Int, String?, String?>>>()
        var group = ArrayList<Triple<Int, String?, String?>>()
        var length = 0
        notices.forEach { notice ->
            val textLength = (notice.second.orEmpty().length + notice.third.orEmpty().length)
            if (textLength > 1_800) return@forEach
            if (group.isNotEmpty() && (group.size >= 4 || length + textLength > 4_800)) {
                groups += group
                group = ArrayList()
                length = 0
            }
            group += notice
            length += textLength
        }
        if (group.isNotEmpty()) groups += group
        for (batch in groups) {
            val body = batch.joinToString("\n\n") { (index, title, text) ->
                "通知 $index：\n" + listOfNotNull(title, text).joinToString("\n")
            }
            val system = "你是台灣記帳 App 的通知判讀器。這是一組同時收到的通知，請逐則判斷是否為已完成的金錢交易。" +
                "利用彼此資訊辨別自己的帳戶轉帳：同金額、時間接近且一出一入時，兩則都標記為 transfer_out/transfer_in。" +
                "登入、驗證碼、廣告、帳單提醒、未完成交易不得列入。索引必須使用原通知編號，不可漏掉能判斷的交易。只回 JSON：" +
                "{\"items\":[{\"index\":number,\"is_transaction\":boolean,\"type\":\"expense\"|\"income\"|\"transfer_out\"|\"transfer_in\"," +
                "\"amount\":number,\"currency\":\"TWD\",\"merchant\":string|null,\"datetime\":\"yyyy-MM-dd HH:mm\"|null," +
                "\"sure\":boolean（金額、方向、商家或對象都清楚才 true，有任何猜測 false）}]}。今天是 ${now.toLocalDate()}。"
            val response = chatJson(system, body, maxTokens = 900, task = AiTask.NOTIFY) ?: continue
            val items = response["items"] as? kotlinx.serialization.json.JsonArray ?: continue
            items.forEach { element ->
                val item = element as? JsonObject ?: return@forEach
                val index = item.str("index")?.toDoubleOrNull()?.toInt() ?: return@forEach
                if (batch.none { it.first == index }) return@forEach
                when (item.str("is_transaction")) {
                    "false" -> { handled += index; return@forEach }
                    "true" -> Unit
                    else -> return@forEach
                }
                val amount = item.str("amount")?.replace(",", "")?.toDoubleOrNull()
                    ?.takeIf { it > 0 && it < 1e9 } ?: return@forEach
                val typeAndKind = when (item.str("type")) {
                    "income" -> ParsedType.INCOME to ParsedKind.NORMAL
                    "expense" -> ParsedType.EXPENSE to ParsedKind.NORMAL
                    "transfer_out" -> ParsedType.EXPENSE to ParsedKind.TRANSFER_OUT
                    "transfer_in" -> ParsedType.INCOME to ParsedKind.TRANSFER_IN
                    else -> return@forEach
                }
                val at = item.str("datetime")
                    ?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull() }
                    ?.takeIf { !it.isAfter(now.plusMinutes(5)) && it.isAfter(now.minusDays(7)) }
                output[index] = ParsedTransaction(
                    type = typeAndKind.first,
                    amountMinor = Math.round(amount * 100),
                    currency = item.str("currency")?.uppercase()?.take(3) ?: "TWD",
                    merchant = item.str("merchant")?.take(40),
                    cardLast4 = null,
                    kind = typeAndKind.second,
                    occurredAt = at,
                    confident = item.str("sure") == "true",
                )
                handled += index
            }
        }
        return NotificationBatchResult(output, handled)
    }

    /** 「教它辨識」的 AI 建議：[mode] 0 支出、1 收入、2 轉出、3 轉入；帳戶從 [accounts]（id 與說明）挑。 */
    data class TeachHint(val mode: Int, val accountId: Long?, val toAccountId: Long?, val reason: String)

    suspend fun suggestTeach(title: String?, text: String?, accounts: List<Pair<Long, String>>): TeachHint? {
        if (!enabled) return null
        val full = listOfNotNull(title, text).joinToString("\n")
        val system = "你是台灣記帳 App 的助理。使用者要教 App 辨識這種格式的通知。判斷它是支出(expense)、收入(income)、" +
            "轉出到自己另一個帳戶或別人(transfer_out)、從別的帳戶轉入(transfer_in)；從帳戶清單挑出這則通知所屬的帳戶（依銀行名稱、卡號末四碼、App 名稱）；" +
            "轉帳時若看得出另一端是清單裡的哪個帳戶也一併挑出。理由 20 字以內。只回 JSON：" +
            "{\"type\": \"expense\"|\"income\"|\"transfer_out\"|\"transfer_in\", \"account_id\": number|null, \"other_account_id\": number|null, \"reason\": string}"
        val user = "帳戶清單（id: 名稱｜類型｜說明）：\n" + accounts.joinToString("\n") { "${it.first}: ${it.second}" } + "\n\n通知：\n$full"
        val r = chatJson(system, user, 160, task = AiTask.TEACH) ?: return null
        val ids = accounts.map { it.first }.toSet()
        val mode = when (r.str("type")) { "income" -> 1; "transfer_out" -> 2; "transfer_in" -> 3; else -> 0 }
        return TeachHint(
            mode,
            r.str("account_id")?.toDoubleOrNull()?.toLong()?.takeIf { it in ids },
            r.str("other_account_id")?.toDoubleOrNull()?.toLong()?.takeIf { it in ids },
            r.str("reason")?.take(40) ?: "",
        )
    }

    /**
     * 錢包交易紀錄畫面的版面規則看不懂時，請 AI 從畫面文字整理出交易列表。
     * 只送出勾選錢包 App 畫面上的文字。
     */
    suspend fun parseScreen(lines: List<String>, today: java.time.LocalDate): List<com.enwei.moneymanager.core.ScreenRecord> {
        if (!enabled) return emptyList()
        lastError = null
        val chunks = ArrayList<String>()
        var chunk = StringBuilder()
        var totalLength = 0
        for (line in lines) {
            totalLength += line.length + 1
            if (totalLength > 20_000) {
                lastError = "錢包畫面內容過長，未送出 AI 判讀；請縮小交易紀錄範圍後重試"
                return emptyList()
            }
            if (line.length > 1_900) {
                lastError = "錢包畫面有單行內容超出安全判讀長度，未送出 AI 判讀"
                return emptyList()
            }
            if (chunk.isNotEmpty() && chunk.length + line.length + 1 > 1_900) {
                chunks += chunk.toString()
                chunk = StringBuilder()
            }
            if (chunk.isNotEmpty()) chunk.append('\n')
            chunk.append(line)
        }
        if (chunk.isNotEmpty()) chunks += chunk.toString()
        if (chunks.isEmpty()) return emptyList()
        val records = ArrayList<com.enwei.moneymanager.core.ScreenRecord>()
        for (body in chunks) {
            if (body.count { it.isDigit() } < 4) continue
            val parsed = parseScreenChunk(body, today) ?: return emptyList()
            records += parsed
        }
        return records
    }

    private suspend fun parseScreenChunk(body: String, today: java.time.LocalDate): List<com.enwei.moneymanager.core.ScreenRecord>? {
        val system = "你是台灣記帳 App 的助理。以下是電子錢包 App「交易紀錄」畫面依順序讀到的文字。整理出畫面上每一筆已完成的交易；" +
            "沒有交易紀錄（例如首頁、設定頁、廣告）就回空陣列。儲值、加值算 income 並把 label 設為「儲值」。年份沒寫時依今天推斷（今天是 $today），不可晚於今天。" +
            "只回 JSON：{\"records\": [{\"date\": \"yyyy-MM-dd\", \"time\": \"HH:mm\"|null, \"merchant\": string|null, \"amount\": number, \"income\": boolean, \"label\": string|null}]}"
        val r = chatJson(system, body, 900, task = AiTask.WALLET) ?: run {
            if (lastError.isNullOrBlank()) lastError = "AI 未能完成錢包紀錄判讀，未匯入任何部分結果"
            return null
        }
        val arr = r["records"] as? kotlinx.serialization.json.JsonArray ?: run {
            lastError = "AI 錢包判讀回覆格式不完整，未匯入部分結果"
            return null
        }
        val records = ArrayList<com.enwei.moneymanager.core.ScreenRecord>()
        for (element in arr) {
            val o = element as? JsonObject ?: run {
                lastError = "AI 錢包判讀含有無效紀錄，未匯入部分結果"
                return null
            }
            val date = o.str("date")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                ?.takeIf { !it.isAfter(today) && it.isAfter(today.minusYears(1)) } ?: run {
                lastError = "AI 錢包判讀的日期無效，未匯入部分結果"
                return null
            }
            val timeText = o.str("time")
            val time = timeText?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            if (timeText != null && time == null) {
                lastError = "AI 錢包判讀的時間無效，未匯入部分結果"
                return null
            }
            val amount = o.str("amount")?.replace(",", "")?.toDoubleOrNull()?.let { kotlin.math.abs(it) }?.takeIf { it > 0 && it < 1e7 } ?: run {
                lastError = "AI 錢包判讀的金額無效，未匯入部分結果"
                return null
            }
            val income = o.str("income")?.toBooleanStrictOrNull() ?: run {
                lastError = "AI 錢包判讀的收支方向無效，未匯入部分結果"
                return null
            }
            records += com.enwei.moneymanager.core.ScreenRecord(
                time = date.atTime(time ?: java.time.LocalTime.MIDNIGHT), dateOnly = time == null,
                merchant = o.str("merchant")?.take(40), amountMinor = Math.round(amount * 100), isIncome = income,
                typeLabel = o.str("label")?.take(10),
            )
        }
        return records
    }

    /**
     * 收支檢討：讀總覽、商家彙總、習慣與逐筆重點交易，回覆固定格式（總評、多角度分析、做得好、需要改善、
     * 針對特定交易的疑問、行動建議、下期目標）。[txnIds] 為重點交易編號對應的交易 id。
     */
    suspend fun review(input: String, base: String, txnIds: List<Long>): com.enwei.moneymanager.data.AiReview? {
        if (!enabled) return null
        // 語氣依「設定 → AI 語氣」：溫和教練、理性顧問或簡潔直接；資料規則與輸出格式三種都一樣
        val system = tone().review + "請逐筆讀使用者的交易，" +
            "從多個角度整理（消費習慣與頻率、時段（深夜／週末）、分類趨勢與上期／去年比較、固定支出），所有結論都要引用資料裡的數字或交易，不可編造。" +
            "每個建議都要具體可執行，並寫出大約能省多少，讓使用者自己決定。" +
            "不要反問使用者交易的用途，直接依資料給建議；使用者已標記過必要性的交易要尊重他的標記。金額單位為 $base 元。" +
            "App 會用圖表呈現分類結構與每日支出。每個發現都必須有依據：basis 直接引用資料裡的數字、分類或交易（例如「訂閱 6,896，上月 1,818」），" +
            "沒有資料支持的推測不要寫；不要寫空泛的口號（例如「平衡現金流」），建議要具體到分類、商家或金額。" +
            "只回 JSON：{\"score\": 0～100 整數, \"headline\": 20 字內總評（含一個關鍵數字）, " +
            "\"findings\": [{\"text\": 20 字內發現, \"basis\": 30 字內依據（資料裡的數字）, \"action\": 25 字內具體建議, \"saving\": 每月約可省金額整數或 null}]（2～4 點，依影響大小排序）, " +
            "\"caps\": [{\"cat\": 大分類名稱（用資料裡的分類名）, \"cap\": 下期上限整數}]（2～4 個最需要控制的分類，上限以該分類前幾期的實際金額為基準，最多少 20%）, " +
            "\"expense_cap\": 下期支出上限整數（以前三期平均支出為基準，最多比基準少 15%；這期還沒過完時不要用目前的金額推算，要合乎實際、做得到）, \"savings_target\": 下期儲蓄率數字, " +
            "\"focus\": [圖表要標示、最值得注意的 1～3 個分類名稱，用資料裡的分類名]}"
        val r = chatJson(system, input, 1500, hard = true, cap = 4000, effort = "low", task = AiTask.REVIEW) ?: return null
        fun list(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.take(100) }.orEmpty()
        fun objs(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val actions = objs("actions").mapNotNull { o ->
            val a = o.str("action") ?: return@mapNotNull null
            com.enwei.moneymanager.data.ReviewAction(a.take(60), o.str("saving")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }?.times(100))
        }
        val questions = objs("questions").mapNotNull { o ->
            val ids = (o["t"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim('#')?.toDoubleOrNull()?.toInt()?.let { i -> txnIds.getOrNull(i) } }
                ?: o.str("t")?.trim('#')?.toDoubleOrNull()?.toInt()?.let { i -> listOfNotNull(txnIds.getOrNull(i)) } ?: emptyList()
            val q = o.str("q") ?: return@mapNotNull null
            if (ids.isEmpty()) null else com.enwei.moneymanager.data.ReviewQuestion(ids.distinct().take(5), q.take(60), o.str("why")?.take(50).orEmpty())
        }
        return com.enwei.moneymanager.data.AiReview(
            score = r.str("score")?.toDoubleOrNull()?.toInt()?.coerceIn(0, 100) ?: 0,
            headline = r.str("headline")?.take(60).orEmpty(), good = list("good"), improve = list("improve"), actions = actions,
            expenseCap = r.str("expense_cap")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }?.times(100),
            savingsTarget = r.str("savings_target")?.toDoubleOrNull(), goals = list("goals"),
            createdAt = System.currentTimeMillis(),
            analysis = objs("analysis").mapNotNull { o -> o.str("title")?.let { t -> com.enwei.moneymanager.data.ReviewAngle(t.take(12), o.str("text")?.take(120).orEmpty()) } },
            questions = questions,
            focus = list("focus").take(3),
            caps = objs("caps").mapNotNull { o ->
                val c = o.str("cat")?.take(20) ?: return@mapNotNull null
                val v = o.str("cap")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 } ?: return@mapNotNull null
                com.enwei.moneymanager.data.CategoryCap(c, v * 100)
            }.take(4),
            findings = objs("findings").mapNotNull { o ->
                val t = o.str("text") ?: return@mapNotNull null
                com.enwei.moneymanager.data.ReviewFinding(
                    t.take(40), o.str("basis")?.take(60).orEmpty(), o.str("action")?.take(50).orEmpty(),
                    o.str("saving")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 }?.times(100),
                )
            }.take(4),
        )
    }

    /**
     * 投資分析：依大盤數據、新聞標題、持股與使用者的紀律／資金計畫，回傳固定格式的建議。
     * 用進階模型（推理較深）；推薦標的只能來自新聞或大型權值股，之後會再查報價確認代號。
     */
    suspend fun advise(user: String): com.enwei.moneymanager.data.AdvisorReport? {
        if (!enabled) return null
        val system = "你是台灣股市分析助理，依使用者提供的市場資料、持倉與自行設定的投資原則提供中性分析。" +
            "使用者沒有設定的資金限制、交易習慣或紀律不可自行假設；不要替使用者編造過往行為。" +
            "請依提供的指數數據判斷大盤趨勢與所處階段；從新聞歸納目前市場資金追逐的需求類型與題材；" +
            "逐檔給持股建議，動作只能是「續抱」「長抱」「分批停利 1/3」「減碼 1/3」「停損出場」「觀察」之一，要參考提供的規則停損價，長線部位不可因短期波動建議全賣；" +
            "推薦 2 到 4 檔觀察標的，只能是新聞中出現的台股或台灣大型權值股／ETF，附四到六碼代號，標明長線或短線、進場條件與停損條件，不保證獲利；" +
            "依資金計畫說明本週長線、短線可動用多少；列出交易前檢查清單，並依使用者的錯誤提醒。內容要具體、引用數字，不可編造新聞沒有的消息。" +
            "只回 JSON：{\"trend\": 大盤趨勢一句話, \"stage\": 階段, \"demand\": [市場需求類型或題材 2-4 項], \"summary\": 整體看法 80 字內, " +
            "\"holdings\": [{\"symbol\": 代號, \"action\": 動作, \"stop\": 停損價數字或 null, \"reason\": 30 字內}], " +
            "\"ideas\": [{\"symbol\": 代號, \"name\": 名稱, \"style\": \"長線\"或\"短線\", \"reason\": 40 字內, \"entry\": 進場條件, \"stop\": 停損條件}], " +
            "\"allocation\": 資金建議 80 字內, \"checklist\": [交易前檢查 3-5 項], \"warnings\": [依紀律的提醒 1-3 項]}"
        val r = chatJson(system, user, 1000, hard = true, task = AiTask.REPORT) ?: return null
        fun list(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.take(120) }.orEmpty()
        fun objs(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val sym = Regex("[0-9]{4,6}[A-Z]?")
        return com.enwei.moneymanager.data.AdvisorReport(
            trend = r.str("trend")?.take(120).orEmpty(), stage = r.str("stage")?.take(20).orEmpty(), demand = list("demand"),
            summary = r.str("summary")?.take(200).orEmpty(),
            holdings = objs("holdings").mapNotNull { o ->
                val code = o.str("symbol")?.let { sym.find(it)?.value } ?: return@mapNotNull null
                com.enwei.moneymanager.data.AdvisorHolding(code, o.str("action")?.take(12).orEmpty(), o.str("stop")?.toDoubleOrNull(), o.str("reason")?.take(80).orEmpty())
            },
            ideas = objs("ideas").mapNotNull { o ->
                val code = o.str("symbol")?.let { sym.find(it)?.value } ?: return@mapNotNull null
                com.enwei.moneymanager.data.AdvisorIdea(
                    code, o.str("name")?.take(20).orEmpty(), o.str("style")?.take(4).orEmpty(), o.str("reason")?.take(100).orEmpty(),
                    o.str("entry")?.take(80).orEmpty(), o.str("stop")?.take(80).orEmpty(),
                )
            }.take(4),
            allocation = r.str("allocation")?.take(200).orEmpty(), checklist = list("checklist"), warnings = list("warnings"),
        )
    }

    /** 新聞標註結果。 */
    data class NewsTag(val symbols: List<String>, val theme: String, val sentiment: Int, val importance: Int, val note: String)

    /** 逐則標註新聞；模型只可依新聞來源提供的標題／內文判斷。 */
    suspend fun tagNews(items: List<com.enwei.moneymanager.data.NewsItem>, known: List<String> = emptyList()): Map<String, NewsTag>? {
        if (!enabled || items.isEmpty()) return emptyMap()
        val system = "你是台股新聞標註員。逐則判斷：相關的台股代號（4～6 碼，只填你確定的，例如台積電 2330；不確定就空陣列）、" +
            "題材請依內容自行歸納成簡短名稱，不得套用固定題材清單；題材必須是產業或投資概念（例如某種產品、技術、供應鏈或產業），" +
            "大盤漲跌、外資買賣、總體經濟、利率匯率、政策這類市場新聞一律標成「大盤」或「總經」，不要把「台股重挫」這種事件當題材；對相關台股的多空 m（-2 大利空、-1 利空、0 中性、1 利多、2 大利多）、" +
            "重要性 p（1～5，5 是會影響大盤或主流題材的重大消息，一般個股新聞 2～3，業配與無關新聞 1）；p 為 4 以上時用 n 寫 20 字內重點。" +
            "內容標成「標題」時不得假設你已讀到正文；僅依輸入內容判斷。逐筆都要輸出，不能省略。" +
            "只回 JSON：{\"r\": [{\"i\": 編號, \"s\": [代號], \"t\": 題材, \"m\": 數字, \"p\": 數字, \"n\": 字串或空}]}" +
            (if (known.isNotEmpty()) "已經用過的題材名稱（不是固定清單）：${known.joinToString("、")}。新聞屬於其中某個題材時請用完全相同的名稱；" +
                "都不合適才自己歸納新的簡短名稱（6 字內），不要只因用字不同就另開題材。" else "")
        val user = items.mapIndexed { i, item ->
            buildString {
                append("$i. [${item.contentLevel}] ${item.title}")
                if (item.content.isNotBlank()) append("\n來源內容：${item.content}")
            }
        }.joinToString("\n\n")
        val r = chatJson(system, user, 25 * items.size + 200, task = AiTask.NEWS_TAG) ?: return null
        val arr = r["r"] as? kotlinx.serialization.json.JsonArray ?: return null
        val code = Regex("^[0-9]{4,6}[A-Z]?$")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val idx = o.str("i")?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            val id = items.getOrNull(idx)?.id ?: return@mapNotNull null
            val syms = (o["s"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> code.matches(s) } }.orEmpty()
            id to NewsTag(
                syms.distinct().take(5), o.str("t")?.take(40)?.ifBlank { "其他" } ?: "其他",
                o.str("m")?.toDoubleOrNull()?.toInt()?.coerceIn(-2, 2) ?: 0, o.str("p")?.toDoubleOrNull()?.toInt()?.coerceIn(1, 5) ?: 1,
                o.str("n")?.take(40).orEmpty(),
            )
        }.toMap()
    }

    private fun parsePick(o: JsonObject, sym: Regex): com.enwei.moneymanager.data.StockPick? {
        val code = o.str("symbol")?.let { sym.find(it)?.value } ?: return null
        return com.enwei.moneymanager.data.StockPick(
            code, o.str("name")?.take(20).orEmpty(), o.str("style")?.take(4).orEmpty(), o.str("business")?.take(80).orEmpty(),
            o.str("strength")?.take(6).orEmpty(), o.str("reason")?.take(120).orEmpty(), o.str("entry")?.take(80).orEmpty(),
            o.str("stop")?.take(80).orEmpty(), o.str("amount")?.toDoubleOrNull()?.toLong()?.takeIf { it > 0 },
        )
    }

    /**
     * AI 語氣（設定 → AI 語氣）：收支檢討、投資報告與股票助理共用。
     * [review] 放在收支檢討的開頭，[advisor] 接在投資助理的角色說明後面。
     */
    /** [chat] 為對話回覆的寫法：結論怎麼寫、重點條列放什麼。 */
    enum class AiTone(val label: String, val desc: String, val review: String, val advisor: String, val chat: String) {
        GENTLE(
            "溫和教練", "先肯定、提供替代方案，不質問",
            "你是溫和、具建設性的個人理財教練，目標是陪使用者一起把錢花得更安心，而不是評判他。" +
                "描述消費時只說事實與數字，不要使用「浪費」「衝動」「真的必要嗎」「為什麼要買」這類帶評價或質問的字眼。" +
                "先寫做得好的地方，再寫可以調整的地方；建議用「可以考慮…」「如果想…可以試試…」並提供替代方案。" +
                "想確認用途的交易用邀請的語氣（例如「想跟你確認這筆的用途，之後分析會更準」），不要反問必要性。",
            "語氣溫和、具建設性：用「可以觀察」「可以考慮」「如果想保守一點」提出建議並說明理由與風險，不用命令句、不催促交易。",
            "reply 先用一句同理或肯定使用者的狀況，再給結論；points 2～4 點，每點是「可以考慮的做法＋理由」，附數字（價位、比例、金額）。",
        ),
        ANALYST(
            "理性顧問", "中立的報告口吻，列出選項與取捨",
            "你是中立、重視數據的財務顧問，用研究報告的口吻寫，不帶情緒、不用反問句。" +
                "每一點採「現況 → 影響 → 選項」：先寫數字與變化，再說明對儲蓄率的影響，最後給兩個可選做法與各自的取捨。" +
                "想確認用途的交易以「待補充資訊」的語氣陳述需要補充什麼。",
            "語氣中立、條理分明：每個建議都附「依據、風險、觀察條件」，不帶情緒、不用命令句。",
            "reply 一句中立結論；points 2～4 點，每點寫成「選項：依據｜風險｜觀察條件」，數字要具體。",
        ),
        DIRECT(
            "簡潔直接", "短句直接講重點與數字",
            "你是直接、精簡的理財教練，用短句直接指出重點與數字，建議具體可執行；可以直白，但不羞辱、不說教。" +
                "想確認用途的交易直接寫出想知道什麼。",
            "語氣簡潔直接：用短句講重點、數字與條件，不寫客套話。",
            "reply 一句直接結論（20 字內）；points 2～3 點，只寫動作與數字，例如「2330 回測 980 再分批，停損 940」。",
        ),
    }

    fun tone(): AiTone = AiTone.entries.getOrElse(MoneyApp.repo.settings.value.aiTone) { AiTone.GENTLE }

    private fun advisorRole() = "你是條理清楚的台灣股市資料助理，根據提供的市場資料、使用者持倉與使用者自行設定的投資原則回答。" +
        tone().advisor + ADVISOR_RULES

    private const val ADVISOR_RULES = "" +
        "使用者沒有設定的資金限制、交易習慣或紀律不可自行假設；不要替使用者編造過往行為。" +
        "只能根據提供的資料集、價格數據與新聞說話，不可編造；推薦的股票必須在資料集的個股統計或新聞中出現，附正確的台股代號；不保證獲利。"

    /** 每日投資報告：讀整理過的新聞資料集（題材熱度、個股統計、重點新聞）＋大盤與持股，用進階模型產生。 */
    suspend fun dailyReport(user: String): com.enwei.moneymanager.data.DailyReport? {
        if (!enabled) return null
        val system = advisorRole() + "請產生今天的投資報告，條理分明、容易閱讀：先用一句話說明今天的市場氣氛，再寫大盤趨勢與階段、目前市場資金追逐的題材（依資料集熱度與多空）、整體看法；" +
            "今天可以考慮的 6～10 檔股票：只能從資料集「被提到最多的個股」與新聞重點裡挑（每檔近 3 天至少被 2 則新聞提到），優先偏多且有具體產業題材的，分散在不同題材，長線與短線都要有；" +
            "理由要引用新聞編號 [n] 或資料裡的數字；有提供月營收年增率時一併參考（營收衰退的要說明理由）" +
            "（標明長線或短線、這家公司在做什麼、目前強勢／中性／弱勢、理由、進場條件、停損條件、依資金計畫建議投入金額）；文字精簡，理由 30 字內；" +
            "逐檔持股動作（只能是「續抱」「長抱」「分批停利 1/3」「減碼 1/3」「停損出場」「觀察」，參考提供的規則停損價）；" +
            "挑 3～6 則使用者一定要看的重點新聞（用新聞編號）並說明為什麼重要；資金分配建議；交易前檢查清單；依他過去錯誤的提醒。" +
            "大盤部分依提供的加權指數均線與高低點，標出 2～4 個關鍵點位（支撐、壓力），並給短期展望。" +
            "只回 JSON：{\"trend\": 一句話, \"stage\": 階段, \"outlook\": \"偏多\"或\"盤整\"或\"偏空\", \"levels\": [{\"k\": \"支撐\"或\"壓力\", \"p\": 指數點位}], \"themes\": [資金追逐的產業題材 2～5 項（要有相關個股，不可以是大盤漲跌或總經事件）], \"summary\": 100 字內, " +
            "\"picks\": [{\"symbol\": 代號, \"name\": 名稱, \"style\": \"長線\"或\"短線\", \"business\": 公司做什麼 30 字內, \"strength\": \"強勢\"或\"中性\"或\"弱勢\", " +
            "\"reason\": 30 字內, \"entry\": 進場條件, \"stop\": 停損條件, \"amount\": 建議投入元}]（6～10 檔）, " +
            "\"holdings\": [{\"symbol\": 代號, \"action\": 動作, \"stop\": 數字或 null, \"reason\": 30 字內}], " +
            "\"news\": [{\"n\": 新聞編號, \"why\": 30 字內}], \"alloc\": [{\"k\": 類別（例如現金、長線、短線、債券）, \"p\": 百分比}]（合計 100）, \"allocation\": 40 字內, \"checklist\": [3～5 項], \"warnings\": [1～3 項]}"
        val r = chatJson(system, user, 3500, hard = true, cap = 6000, effort = "low", task = AiTask.REPORT) ?: return null
        fun list(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.take(120) }.orEmpty()
        fun objs(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val sym = Regex("[0-9]{4,6}[A-Z]?")
        return com.enwei.moneymanager.data.DailyReport(
            trend = r.str("trend")?.take(120).orEmpty(), stage = r.str("stage")?.take(20).orEmpty(), themes = list("themes"),
            summary = r.str("summary")?.take(240).orEmpty(),
            alloc = objs("alloc").mapNotNull { o ->
                val k = o.str("k")?.take(10) ?: return@mapNotNull null
                val p = o.str("p")?.trimEnd('%')?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                com.enwei.moneymanager.data.AllocSlice(k, p)
            }.take(6),
            picks = objs("picks").mapNotNull { parsePick(it, sym) }.distinctBy { it.symbol }.take(10),
            holdings = objs("holdings").mapNotNull { o ->
                val code = o.str("symbol")?.let { sym.find(it)?.value } ?: return@mapNotNull null
                com.enwei.moneymanager.data.AdvisorHolding(code, o.str("action")?.take(12).orEmpty(), o.str("stop")?.toDoubleOrNull(), o.str("reason")?.take(80).orEmpty())
            },
            // 新聞編號先放在 id，呼叫端換成真正的新聞 id
            keyNews = objs("news").mapNotNull { o -> o.str("n")?.toDoubleOrNull()?.toInt()?.let { com.enwei.moneymanager.data.KeyNews(it.toString(), o.str("why")?.take(60).orEmpty()) } },
            outlook = r.str("outlook")?.take(4).orEmpty(),
            levels = objs("levels").mapNotNull { o ->
                val k = o.str("k")?.take(6) ?: return@mapNotNull null
                val p = o.str("p")?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                com.enwei.moneymanager.data.KeyLevel(k, p)
            }.take(4),
            allocation = r.str("allocation")?.take(200).orEmpty(), checklist = list("checklist"), warnings = list("warnings"),
        )
    }

    /** 投資助理對話：依資料集與使用者狀況回答，需要時附股票卡片與新聞編號。 */
    suspend fun advisorChat(context: String, history: List<Pair<Boolean, String>>, question: String, focused: Boolean = false): com.enwei.moneymanager.data.ChatMsg? {
        if (!enabled) return null
        val contextLimit = chatContextLimit()
        if (context.length > contextLimit) {
            lastError = "AI 對話資料超過安全上下文上限（${context.length}/$contextLimit 字元），未截斷或送出；請稍後重試"
            return null
        }
        val system = advisorRole() + "以下是資料與使用者狀況，請回答他的問題。字要少、多用圖表：結論 reply 40 字內，重點 points 最多 3 點、每點 30 字內；" +
            tone().chat + "提到具體股票時放進 cards（App 會在卡片上畫近期 K 線，文字不必描述走勢）；" +
            "需要比較或呈現時加 charts（App 用實際資料畫）：{\"kind\": \"compare\", \"items\": [2～5 個代號]} 比較走勢、" +
            "{\"kind\": \"themes\", \"items\": [題材名稱]} 題材熱度與多空、{\"kind\": \"revenue\", \"items\": [2～6 個代號]} 月營收年增率比較、{\"kind\": \"holdings\", \"items\": []} 使用者持股損益；" +
            "引用新聞時在句子後面用 [編號] 標出，並把編號放進 news。" +
            (if (focused) "這題有 App 搜尋到的相關新聞（部分附全文摘錄）：結論與每個重點都要根據這些新聞的具體內容（事件、數字、日期、公司），不要寫泛泛的看法；" +
                "新聞提到的個股都做成 cards，reason 寫新聞裡的具體依據並附 [編號]；新聞沒有提到的就說資料不足，不要猜。" else "") +
            "只回 JSON：{\"reply\": 結論, \"points\": [重點], \"charts\": [圖表], \"cards\": [{\"symbol\": 代號, \"name\": 名稱, \"style\": \"長線\"或\"短線\"或空, \"business\": 公司做什麼, \"strength\": 強勢/中性/弱勢, \"reason\": 40 字內}], \"news\": [編號]}\n\n" +
            context
        val convo = history.takeLast(6).joinToString("\n") { (u, t) -> (if (u) "使用者：" else "助理：") + t.take(300) }
        val r = chatJson(system, (if (convo.isNotBlank()) "先前對話：\n$convo\n\n" else "") + "問題：$question", 1200, hard = true, cap = 3000, effort = "low", task = AiTask.CHAT) ?: return null
        val sym = Regex("[0-9]{4,6}[A-Z]?")
        val cards = (r["cards"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonObject)?.let { o -> parsePick(o, sym) } }.orEmpty().distinctBy { it.symbol }.take(8)
        val charts = (r["charts"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val kind = o.str("kind")?.takeIf { it in setOf("compare", "themes", "holdings", "revenue") } ?: return@mapNotNull null
            val items = (o["items"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.take(40) }.orEmpty()
            com.enwei.moneymanager.data.ChatChart(kind, if (kind == "compare" || kind == "revenue") items.mapNotNull { sym.find(it)?.value }.distinct().take(5) else items.take(10))
        }.orEmpty().take(3)
        val news = (r["news"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()?.toString() }.orEmpty()
        val points = (r["points"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.take(120)?.takeIf { s -> s.isNotEmpty() } }.orEmpty().take(5)
        return com.enwei.moneymanager.data.ChatMsg(false, r.str("reply")?.take(300) ?: return null, points = points.take(3), charts = charts, cards = cards, newsIds = news)
    }

    /** 對話可以帶的資料量（字元）：Gemini 可讀大量資料，Groq 每分鐘 token 有限。 */
    fun chatContextLimit(): Int = if (bigContext(AiTask.CHAT)) 80_000 else 2500

    /** 一句話說明這家公司在做什麼（股票資訊頁用，結果會快取）。 */
    suspend fun describeCompany(symbol: String, name: String): String? {
        if (!enabled) return null
        val r = chatJson(
            "你是台股資料助理。用繁體中文 40 字內說明這家台灣上市櫃公司的主要業務、產品與所屬產業；不知道就回空字串。只回 JSON：{\"d\": 說明}",
            "$symbol $name", 200, task = AiTask.COMPANY,
        ) ?: return null
        return r.str("d")?.take(80)
    }

    /**
     * 新聞重點整理：同一天、同一題材的所有標題整理成不重複的重點（同一事件只寫一次，附相關代號、多空與幾則新聞提到）。
     */
    /**
     * 合併題材：把標註時各批取的題材名稱（同一件事常有不同說法）對應到少數標準題材。
     * 回傳「原題材 → 標準題材」，沒有回覆時為 null。
     */
    suspend fun mergeThemes(themes: List<Pair<String, Int>>, canonical: List<String>): Map<String, String>? {
        if (!enabled || themes.isEmpty()) return emptyMap()
        val system = "你是台股新聞題材的分類員。把下列題材名稱（後面是則數）合併成不超過 40 個標準題材。規則：" +
            "產業、產品、技術、供應鏈類用簡短的產業名稱（例如 被動元件、AI 伺服器、記憶體、低軌衛星、散熱、重電），意思相同或只是說法不同的一律合併；" +
            "大盤漲跌、盤勢、指數一律為「大盤」；總體經濟、利率匯率、政策、關稅為「總經」；個股的一般消息（個股動態、討論、概覽、股價表現）為「個股消息」；" +
            "法人買賣超、外資動向為「法人籌碼」；財報、營收、獲利、基本面為「財報營收」。" +
            (if (canonical.isNotEmpty()) "已經使用的標準題材（優先沿用）：${canonical.joinToString("、")}。" else "") +
            "每個原題材都要對應到一個標準題材，不能遺漏。只回 JSON：{\"map\": {\"原題材\": \"標準題材\"}}"
        val out = LinkedHashMap<String, String>()
        // 題材很多時分段送（每段約 120 個）
        for (part in themes.chunked(120)) {
            val user = part.joinToString("\n") { (t, n) -> "$t（$n）" }
            val r = chatJson(system, user, 40 * part.size + 200, task = AiTask.DIGEST) ?: return out.takeIf { it.isNotEmpty() }
            val m = r["map"] as? JsonObject ?: return out.takeIf { it.isNotEmpty() }
            m.forEach { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.trim()?.take(12)?.takeIf { it.isNotBlank() }?.let { out[k.trim()] = it } }
        }
        return out
    }

    suspend fun digestTheme(theme: String, day: String, items: List<com.enwei.moneymanager.data.NewsItem>): List<com.enwei.moneymanager.data.DigestPoint>? {
        if (!enabled || items.isEmpty()) return emptyList()
        val system = "你是台股新聞編輯。以下是 $day「$theme」題材的新聞，每筆包含來源提供的內容範圍與內容。" +
            "把所有輸入新聞分成 3～12 個不重複事件重點；每筆新聞的編號必須且只能歸入一個重點，不能漏掉。" +
            "每點 40 字內、具體（公司、數字、事件），標出相關台股代號（確定的才填）、對相關個股的多空（-2～2）。" +
            "內容只有標題時不可假設讀到正文。JSON 中 i 是該重點包含的輸入編號陣列。" +
            "只回 JSON：{\"p\": [{\"t\": 重點, \"s\": [代號], \"m\": 數字, \"i\": [輸入編號]}]}"
        val user = items.mapIndexed { i, item ->
            buildString {
                append("$i. [${item.contentLevel}] ${item.title}")
                if (item.content.isNotBlank()) append("\n來源內容：${item.content}")
            }
        }.joinToString("\n\n")
        val r = chatJson(system, user, 900, task = AiTask.DIGEST) ?: return null
        val code = Regex("^[0-9]{4,6}[A-Z]?$")
        val points = (r["p"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val t = o.str("t") ?: return@mapNotNull null
            val indexes = (o["i"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.takeIf { index -> index in items.indices }
            }?.distinct().orEmpty()
            if (indexes.isEmpty()) return@mapNotNull null
            val sourceItems = indexes.map(items::get)
            com.enwei.moneymanager.data.DigestPoint(
                t.take(80),
                (o["s"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { c -> code.matches(c) } }.orEmpty().distinct().take(5),
                o.str("m")?.toDoubleOrNull()?.toInt()?.coerceIn(-2, 2) ?: 0,
                sourceItems.sumOf { it.duplicateCount.coerceAtLeast(1) },
                sourceItems.map { it.id },
            )
        }?.take(12)
        val covered = points.orEmpty().flatMap { it.sourceIds }
        return points?.takeIf {
            covered.toSet() == items.map { item -> item.id }.toSet() && covered.size == items.size
        }
    }
}
