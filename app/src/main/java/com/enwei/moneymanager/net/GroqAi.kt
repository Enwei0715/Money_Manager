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
        get() = MoneyApp.repo.settings.value.let { it.aiEnabled && it.groqKey.isNotBlank() }

    /** 預設模型。llama-3.1-8b-instant 已於 2026/8/16 停止提供給免費與開發者方案。 */
    const val DEFAULT_MODEL = "openai/gpt-oss-20b"
    /** 較難的推估（整理「不明」花費）用的大模型；失敗時退回 [DEFAULT_MODEL] */
    const val HARD_MODEL = "openai/gpt-oss-120b"
    /** 看得懂圖片的模型（截圖對帳用） */
    const val VISION_MODEL = "qwen/qwen3.8-27b"
    /** 已停止提供的模型，自動改用預設模型 */
    val RETIRED_MODELS = setOf("llama-3.1-8b-instant", "llama3-8b-8192", "llama-3.3-70b-versatile")

    private enum class Fail { NONE, MODEL_GONE, NO_JSON_MODE }

    /** 呼叫模型並取得 JSON 物件回覆；失敗回傳 null。模型下架時自動改用預設模型、不支援 JSON 模式時改用一般模式重試。 */
    private suspend fun chatJson(system: String, user: String, maxTokens: Int = 200, hard: Boolean = false): JsonObject? = withContext(Dispatchers.IO) {
        val s = MoneyApp.repo.settings.value
        if (s.groqKey.isBlank()) return@withContext null
        // 難題先用大模型（推理強度中等）；大模型不能用或超過額度時改用一般模型
        if (hard) {
            val big = s.aiModelHard.trim().ifBlank { HARD_MODEL }
            val (r, _) = call(s.groqKey.trim(), big, true, system, user, maxTokens, effort = "medium")
            if (r != null) return@withContext r
        }
        var model = s.aiModel.trim().ifBlank { DEFAULT_MODEL }.let { if (it in RETIRED_MODELS) DEFAULT_MODEL else it }
        var jsonMode = true
        repeat(3) {
            val (result, fail) = call(s.groqKey.trim(), model, jsonMode, system, user, maxTokens)
            when (fail) {
                Fail.NONE -> return@withContext result
                Fail.MODEL_GONE -> if (model != DEFAULT_MODEL) {
                    model = DEFAULT_MODEL
                    MoneyApp.repo.settings.update { it.copy(aiModel = DEFAULT_MODEL) }
                } else return@withContext null
                Fail.NO_JSON_MODE -> if (jsonMode) jsonMode = false else return@withContext null
            }
        }
        null
    }

    private fun call(
        key: String, model: String, jsonMode: Boolean, system: String, user: String, maxTokens: Int, effort: String = "low",
        imageDataUrl: String? = null,
    ): Pair<JsonObject?, Fail> {
        // gpt-oss 等推理模型的思考過程也算在 max_tokens 裡，要多留空間並把推理強度調低
        val reasoning = model.startsWith("openai/gpt-oss") || model.contains("qwen3") || model.contains("deepseek")
        val body = buildJsonObject {
            put("model", model)
            put("temperature", if (reasoning) 0.2 else 0.0)
            put("max_tokens", if (reasoning) maxOf(if (effort == "low") 1024 else 4096, maxTokens * 4) else maxTokens)
            if (reasoning && model.startsWith("openai/gpt-oss")) put("reasoning_effort", effort)
            if (jsonMode) put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject {
                    put("role", "user")
                    if (imageDataUrl == null) put("content", user)
                    else put("content", buildJsonArray {
                        add(buildJsonObject { put("type", "text"); put("text", user) })
                        add(buildJsonObject { put("type", "image_url"); put("image_url", buildJsonObject { put("url", imageDataUrl) }) })
                    })
                })
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
                recordUsage((root["usage"] as? JsonObject)?.get("total_tokens")?.let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0)
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
        }.getOrElse { lastError = it.message ?: it.javaClass.simpleName; null to Fail.NONE }
    }

    /** 今天的用量（請求數、tokens），每天重新計算；Groq 免費方案每個模型約每天 1,000 次請求、20 萬 tokens。 */
    private fun recordUsage(tokens: Long) {
        val today = java.time.LocalDate.now().toEpochDay()
        MoneyApp.repo.settings.update { s ->
            if (s.aiUsageDay != today) s.copy(aiUsageDay = today, aiUsageRequests = 1, aiUsageTokens = tokens)
            else s.copy(aiUsageRequests = s.aiUsageRequests + 1, aiUsageTokens = s.aiUsageTokens + tokens)
        }
    }

    /**
     * 用視覺模型直接看截圖找餘額。只接受圖片文字（手機 OCR）裡真的出現過的金額；OCR 什麼都沒讀到時才直接採用（仍需使用者確認）。
     * 模型不能用時回傳 null，改用文字判斷。
     */
    suspend fun readBalanceFromImage(context: android.content.Context, uri: android.net.Uri, accountName: String, ocrAmounts: Set<Long>): Long? =
        withContext(Dispatchers.IO) {
            val s = MoneyApp.repo.settings.value
            if (!enabled) return@withContext null
            val dataUrl = runCatching { imageDataUrl(context, uri) }.getOrNull() ?: return@withContext null
            val system = "你是記帳 App 的助理。看這張銀行或錢包 App 的畫面，找出帳戶「$accountName」目前的餘額（優先：可用餘額、帳戶餘額）。" +
                "交易金額、額度、帳號、日期都不是餘額。只回 JSON：{\"balance\": number|null}"
            val model = s.aiModelVision.trim().ifBlank { VISION_MODEL }
            var (r, fail) = call(s.groqKey.trim(), model, true, system, "找出餘額", 300, imageDataUrl = dataUrl)
            if (r == null && fail == Fail.NO_JSON_MODE) r = call(s.groqKey.trim(), model, false, system, "找出餘額", 300, imageDataUrl = dataUrl).first
            val v = r?.str("balance")?.replace(",", "")?.toDoubleOrNull() ?: return@withContext null
            val minor = Math.round(v * 100)
            minor.takeIf { ocrAmounts.isEmpty() || it in ocrAmounts || -it in ocrAmounts }
        }

    /** 圖片縮到長邊 1280、JPEG 壓縮後轉成 data URL（Groq 限制 4MB）。 */
    private fun imageDataUrl(context: android.content.Context, uri: android.net.Uri): String? {
        val bmp = context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) } ?: return null
        val scale = minOf(1f, 1280f / maxOf(bmp.width, bmp.height))
        val scaled = if (scale < 1f) android.graphics.Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
        val out = java.io.ByteArrayOutputStream()
        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        return "data:image/jpeg;base64," + android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
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

    /**
     * 從銀行 App 畫面的文字（OCR）找出這個帳戶目前的餘額。只接受畫面上真的出現過的金額，避免 AI 自己編數字。
     */
    suspend fun pickBalance(accountName: String, lines: List<String>): Long? {
        if (!enabled || lines.isEmpty()) return null
        val seen = lines.flatMap { com.enwei.moneymanager.core.BalanceExtractor.amounts(it) }.toSet()
        if (seen.isEmpty()) return null
        val system = "你是記帳 App 的助理。以下是銀行或錢包 App 畫面的文字（由上到下）。找出帳戶「$accountName」目前的餘額" +
            "（優先：可用餘額、帳戶餘額）。不是餘額的數字（交易金額、額度、帳號、日期）不要選。只回 JSON：{\"balance\": number|null}。"
        val r = chatJson(system, lines.joinToString("\n").take(1500), 40) ?: return null
        val v = r.str("balance")?.replace(",", "")?.toDoubleOrNull() ?: return null
        return Math.round(v * 100).takeIf { it in seen || -it in seen }
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
        val r = chatJson(system, user, 60 + 50 * items.size, hard = true) ?: return emptyMap()
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
        val full = listOfNotNull(title, text).joinToString("\n").take(800)
        if (full.none { it.isDigit() }) return null
        val system = "你是台灣記帳 App 的通知判讀器。判斷這則銀行或支付 App 的通知是否代表一筆已完成的金錢進出" +
            "（驗證碼、登入、行銷、優惠、帳單提醒、未完成的交易都不是）。只回 JSON：" +
            "{\"is_transaction\": boolean, \"type\": \"expense\"|\"income\"|\"transfer_out\"|\"transfer_in\", " +
            "\"amount\": number, \"currency\": \"TWD\"等三碼代碼, \"merchant\": string|null, \"datetime\": \"yyyy-MM-dd HH:mm\"|null}。" +
            "轉出到別的帳戶用 transfer_out，從別的帳戶轉入用 transfer_in。今天是 ${now.toLocalDate()}。"
        val r = chatJson(system, full) ?: return null
        if (r.str("is_transaction") != "true") return null
        val amount = r.str("amount")?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 && it < 1e9 } ?: return null
        val (type, kind) = when (r.str("type")) {
            "income" -> ParsedType.INCOME to ParsedKind.NORMAL
            "transfer_out" -> ParsedType.EXPENSE to ParsedKind.TRANSFER_OUT
            "transfer_in" -> ParsedType.INCOME to ParsedKind.TRANSFER_IN
            else -> ParsedType.EXPENSE to ParsedKind.NORMAL
        }
        val at = r.str("datetime")?.let { runCatching { LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull() }
            ?.takeIf { !it.isAfter(now.plusMinutes(5)) && it.isAfter(now.minusDays(7)) }
        return ParsedTransaction(
            type = type, amountMinor = Math.round(amount * 100), currency = r.str("currency")?.uppercase()?.take(3) ?: "TWD",
            merchant = r.str("merchant")?.take(40), cardLast4 = null, kind = kind, occurredAt = at,
        )
    }
}
