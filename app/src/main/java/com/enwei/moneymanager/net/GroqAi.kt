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
    private suspend fun chatJson(
        system: String, user: String, maxTokens: Int = 200, hard: Boolean = false,
        /** 回覆（含推理）的 token 上限；免費方案每分鐘上限約 8,000，輸入加回覆不能超過 */
        cap: Int = 6000, effort: String = "medium",
    ): JsonObject? = withContext(Dispatchers.IO) {
        val s = MoneyApp.repo.settings.value
        if (s.groqKey.isBlank()) return@withContext null
        // 難題先用大模型（推理強度中等）；大模型不能用或超過額度時改用一般模型
        if (hard) {
            val big = s.aiModelHard.trim().ifBlank { HARD_MODEL }
            val (r, _) = call(s.groqKey.trim(), big, true, system, user, maxTokens, effort = effort, cap = cap)
            if (r != null) return@withContext r
        }
        var model = s.aiModel.trim().ifBlank { DEFAULT_MODEL }.let { if (it in RETIRED_MODELS) DEFAULT_MODEL else it }
        var jsonMode = true
        repeat(3) {
            val (result, fail) = call(s.groqKey.trim(), model, jsonMode, system, user, maxTokens, cap = cap)
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
        imageDataUrl: String? = null, cap: Int = 6000,
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

    /** 判斷每組（A、B）是不是同一筆交易，同一筆時給較精確的名稱與分類。[items] 為 (組別代號, 說明)。 */
    suspend fun judgeDuplicates(items: List<Pair<String, String>>, categories: List<Pair<Long, String>>): Map<String, com.enwei.moneymanager.data.DupJudgement> {
        if (!enabled || items.isEmpty()) return emptyMap()
        val system = "你是台灣記帳 App 的助理。每組 A、B 是金額相同、時間相近的兩筆紀錄，可能是同一筆消費被通知和錢包畫面（或手動）各記了一次，" +
            "也可能真的是兩筆（例如同一天買兩張一樣的票）。判斷是否同一筆；若是，從兩筆的名稱與原文整理出最精確、好讀的交易名稱（例如店名），並從分類清單挑一個。" +
            "理由 15 字以內。只回 JSON：{\"results\": [{\"id\": string, \"same\": boolean, \"name\": string|null, \"category_id\": number|null, \"reason\": string}]}"
        val user = "分類清單（id: 名稱）：\n" + categories.joinToString("\n") { "${it.first}: ${it.second}" } +
            "\n\n" + items.joinToString("\n") { "[${it.first}] ${it.second}" }
        val r = chatJson(system, user, 80 + 60 * items.size) ?: return emptyMap()
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

    /** 「教它辨識」的 AI 建議：[mode] 0 支出、1 收入、2 轉出、3 轉入；帳戶從 [accounts]（id 與說明）挑。 */
    data class TeachHint(val mode: Int, val accountId: Long?, val toAccountId: Long?, val reason: String)

    suspend fun suggestTeach(title: String?, text: String?, accounts: List<Pair<Long, String>>): TeachHint? {
        if (!enabled) return null
        val full = listOfNotNull(title, text).joinToString("\n").take(800)
        val system = "你是台灣記帳 App 的助理。使用者要教 App 辨識這種格式的通知。判斷它是支出(expense)、收入(income)、" +
            "轉出到自己另一個帳戶或別人(transfer_out)、從別的帳戶轉入(transfer_in)；從帳戶清單挑出這則通知所屬的帳戶（依銀行名稱、卡號末四碼、App 名稱）；" +
            "轉帳時若看得出另一端是清單裡的哪個帳戶也一併挑出。理由 20 字以內。只回 JSON：" +
            "{\"type\": \"expense\"|\"income\"|\"transfer_out\"|\"transfer_in\", \"account_id\": number|null, \"other_account_id\": number|null, \"reason\": string}"
        val user = "帳戶清單（id: 名稱｜類型｜說明）：\n" + accounts.joinToString("\n") { "${it.first}: ${it.second}" } + "\n\n通知：\n$full"
        val r = chatJson(system, user, 160) ?: return null
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
        val body = lines.joinToString("\n").take(2500)
        if (body.count { it.isDigit() } < 4) return emptyList()
        val system = "你是台灣記帳 App 的助理。以下是電子錢包 App「交易紀錄」畫面依順序讀到的文字。整理出畫面上每一筆已完成的交易；" +
            "沒有交易紀錄（例如首頁、設定頁、廣告）就回空陣列。儲值、加值算 income 並把 label 設為「儲值」。年份沒寫時依今天推斷（今天是 $today），不可晚於今天。" +
            "只回 JSON：{\"records\": [{\"date\": \"yyyy-MM-dd\", \"time\": \"HH:mm\"|null, \"merchant\": string|null, \"amount\": number, \"income\": boolean, \"label\": string|null}]}"
        val r = chatJson(system, body, 900) ?: return emptyList()
        val arr = r["records"] as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val date = o.str("date")?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                ?.takeIf { !it.isAfter(today) && it.isAfter(today.minusYears(1)) } ?: return@mapNotNull null
            val time = o.str("time")?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
            val amount = o.str("amount")?.replace(",", "")?.toDoubleOrNull()?.let { kotlin.math.abs(it) }?.takeIf { it > 0 && it < 1e7 } ?: return@mapNotNull null
            com.enwei.moneymanager.core.ScreenRecord(
                time = date.atTime(time ?: java.time.LocalTime.MIDNIGHT), dateOnly = time == null,
                merchant = o.str("merchant")?.take(40), amountMinor = Math.round(amount * 100), isIncome = o.str("income") == "true",
                typeLabel = o.str("label")?.take(10),
            )
        }
    }

    /**
     * 收支檢討：讀總覽、商家彙總、習慣與逐筆重點交易，回覆固定格式（總評、多角度分析、做得好、需要改善、
     * 針對特定交易的疑問、行動建議、下期目標）。[txnIds] 為重點交易編號對應的交易 id。
     */
    suspend fun review(input: String, base: String, txnIds: List<Long>): com.enwei.moneymanager.data.AiReview? {
        if (!enabled) return null
        val system = "你是台灣的個人理財教練，口吻直接、具體、友善，不說教。請真正逐筆讀使用者的交易，判斷哪些消費是必要的、哪些可能是衝動或習慣性花費，" +
            "從多個角度分析（必要性、消費習慣與頻率、時間（深夜／週末）、分類趨勢與上期／去年比較、固定支出），所有結論都要引用資料裡的數字或交易，不可編造。" +
            "對 3～5 筆（或一組）看起來不必要、異常或重複的交易提出具體疑問，用交易編號；使用者已標記過的交易要尊重他的回答，不要再問同一筆。金額單位為 $base 元。" +
            "只回 JSON：{\"score\": 0～100 整數, \"headline\": 30 字內總評, " +
            "\"analysis\": [{\"title\": 角度名稱, \"text\": 60 字內分析}]（4～5 項）, \"good\": [1～3 點], \"improve\": [2～3 點，含數字], " +
            "\"questions\": [{\"t\": [交易編號], \"q\": 對這筆的疑問（30 字內）, \"why\": 為什麼這樣問（25 字內）}], " +
            "\"actions\": [{\"action\": 25 字內具體行動, \"saving\": 每月可省金額整數或 null}]（3 點）, " +
            "\"expense_cap\": 下期支出上限整數, \"savings_target\": 下期儲蓄率數字, \"goals\": [1～3 項]}"
        val r = chatJson(system, input.take(3800), 1100, hard = true, cap = 4000, effort = "low") ?: return null
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
        )
    }

    /**
     * 投資分析：依大盤數據、新聞標題、持股與使用者的紀律／資金計畫，回傳固定格式的建議。
     * 用進階模型（推理較深）；推薦標的只能來自新聞或大型權值股，之後會再查報價確認代號。
     */
    suspend fun advise(user: String): com.enwei.moneymanager.data.AdvisorReport? {
        if (!enabled) return null
        val system = "你是台灣股市的投資教練兼分析師，任務是幫使用者遵守他自己的交易紀律，而不是鼓勵頻繁交易。" +
            "使用者過去常犯的錯：1. 想實現獲利就整檔賣光，隔天繼續漲而賣飛；2. 短線看到新聞就衝動買；3. 賣完一有資金就立刻買別檔；4. 資金控管差。" +
            "請依提供的指數數據判斷大盤趨勢與所處階段；從新聞歸納目前市場資金追逐的需求類型與題材；" +
            "逐檔給持股建議，動作只能是「續抱」「長抱」「分批停利 1/3」「減碼 1/3」「停損出場」「觀察」之一，要參考提供的規則停損價，長線部位不可因短期波動建議全賣；" +
            "推薦 2 到 4 檔觀察標的，只能是新聞中出現的台股或台灣大型權值股／ETF，附四到六碼代號，標明長線或短線、進場條件與停損條件，不保證獲利；" +
            "依資金計畫說明本週長線、短線可動用多少；列出交易前檢查清單，並依使用者的錯誤提醒。內容要具體、引用數字，不可編造新聞沒有的消息。" +
            "只回 JSON：{\"trend\": 大盤趨勢一句話, \"stage\": 階段, \"demand\": [市場需求類型或題材 2-4 項], \"summary\": 整體看法 80 字內, " +
            "\"holdings\": [{\"symbol\": 代號, \"action\": 動作, \"stop\": 停損價數字或 null, \"reason\": 30 字內}], " +
            "\"ideas\": [{\"symbol\": 代號, \"name\": 名稱, \"style\": \"長線\"或\"短線\", \"reason\": 40 字內, \"entry\": 進場條件, \"stop\": 停損條件}], " +
            "\"allocation\": 資金建議 80 字內, \"checklist\": [交易前檢查 3-5 項], \"warnings\": [依紀律的提醒 1-3 項]}"
        val r = chatJson(system, user.take(4500), 1000, hard = true) ?: return null
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

    val THEMES = listOf(
        "大盤資金", "總經利率", "美股國際", "半導體", "AI伺服器", "IC設計", "記憶體", "先進封裝", "PCB載板", "散熱", "光通訊",
        "網通衛星", "機器人", "電動車", "重電綠能", "航運", "金融", "生技", "營建", "傳產原物料", "內需觀光", "ETF存股", "其他",
    )

    /** 逐則標註新聞：相關台股代號、題材、多空與重要性。[items] 為 (id, 標題)。 */
    suspend fun tagNews(items: List<Pair<String, String>>): Map<String, NewsTag>? {
        if (!enabled || items.isEmpty()) return emptyMap()
        val system = "你是台股新聞標註員。逐則判斷：相關的台股代號（4～6 碼，只填你確定的，例如台積電 2330；不確定就空陣列）、" +
            "題材（只能從這些挑一個：${THEMES.joinToString("、")}）、對相關台股的多空 m（-2 大利空、-1 利空、0 中性、1 利多、2 大利多）、" +
            "重要性 p（1～5，5 是會影響大盤或主流題材的重大消息，一般個股新聞 2～3，業配與無關新聞 1）；p 為 4 以上時用 n 寫 20 字內重點。" +
            "只回 JSON：{\"r\": [{\"i\": 編號, \"s\": [代號], \"t\": 題材, \"m\": 數字, \"p\": 數字, \"n\": 字串或空}]}"
        val user = items.mapIndexed { i, it -> "$i. ${it.second.take(70)}" }.joinToString("\n")
        val r = chatJson(system, user, 1100) ?: return null
        val arr = r["r"] as? kotlinx.serialization.json.JsonArray ?: return null
        val code = Regex("^[0-9]{4,6}[A-Z]?$")
        return arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            val idx = o.str("i")?.toDoubleOrNull()?.toInt() ?: return@mapNotNull null
            val id = items.getOrNull(idx)?.first ?: return@mapNotNull null
            val syms = (o["s"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { s -> code.matches(s) } }.orEmpty()
            id to NewsTag(
                syms.distinct().take(5), o.str("t")?.takeIf { it in THEMES } ?: "其他",
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

    const val ADVISOR_ROLE = "你是台灣股市的投資教練兼分析師，任務是幫使用者遵守他自己的交易紀律與資金計畫，而不是鼓勵頻繁交易。" +
        "使用者過去常犯的錯：1. 想實現獲利就整檔賣光，隔天繼續漲而賣飛；2. 短線看到新聞就衝動買；3. 賣完一有資金就立刻買別檔；4. 資金控管差。" +
        "所有建議都要符合他的資金計畫（短線可動用金額不可超過計畫）與紀律，長線部位不可因短期波動建議全賣，獲利了結一律建議分批。" +
        "只能根據提供的資料集、價格數據與新聞說話，不可編造；推薦的股票必須在資料集的個股統計或新聞中出現，附正確的台股代號；不保證獲利。"

    /** 每日投資報告：讀整理過的新聞資料集（題材熱度、個股統計、重點新聞）＋大盤與持股，用進階模型產生。 */
    suspend fun dailyReport(user: String): com.enwei.moneymanager.data.DailyReport? {
        if (!enabled) return null
        val system = ADVISOR_ROLE + "請產生今天的投資報告：大盤趨勢與階段、目前市場資金追逐的題材（依資料集熱度與多空）、整體看法；" +
            "今天可以考慮的 2～4 檔股票（標明長線或短線、這家公司在做什麼、目前強勢／中性／弱勢、理由、進場條件、停損條件、依資金計畫建議投入金額）；" +
            "逐檔持股動作（只能是「續抱」「長抱」「分批停利 1/3」「減碼 1/3」「停損出場」「觀察」，參考提供的規則停損價）；" +
            "挑 3～6 則使用者一定要看的重點新聞（用新聞編號）並說明為什麼重要；資金分配建議；交易前檢查清單；依他過去錯誤的提醒。" +
            "只回 JSON：{\"trend\": 一句話, \"stage\": 階段, \"themes\": [題材 2～4 項], \"summary\": 100 字內, " +
            "\"picks\": [{\"symbol\": 代號, \"name\": 名稱, \"style\": \"長線\"或\"短線\", \"business\": 公司做什麼 30 字內, \"strength\": \"強勢\"或\"中性\"或\"弱勢\", " +
            "\"reason\": 50 字內, \"entry\": 進場條件, \"stop\": 停損條件, \"amount\": 建議投入元}], " +
            "\"holdings\": [{\"symbol\": 代號, \"action\": 動作, \"stop\": 數字或 null, \"reason\": 30 字內}], " +
            "\"news\": [{\"n\": 新聞編號, \"why\": 30 字內}], \"allocation\": 80 字內, \"checklist\": [3～5 項], \"warnings\": [1～3 項]}"
        val r = chatJson(system, user.take(3500), 1200, hard = true, cap = 3800, effort = "low") ?: return null
        fun list(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.take(120) }.orEmpty()
        fun objs(k: String) = (r[k] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val sym = Regex("[0-9]{4,6}[A-Z]?")
        return com.enwei.moneymanager.data.DailyReport(
            trend = r.str("trend")?.take(120).orEmpty(), stage = r.str("stage")?.take(20).orEmpty(), themes = list("themes"),
            summary = r.str("summary")?.take(240).orEmpty(),
            picks = objs("picks").mapNotNull { parsePick(it, sym) }.take(4),
            holdings = objs("holdings").mapNotNull { o ->
                val code = o.str("symbol")?.let { sym.find(it)?.value } ?: return@mapNotNull null
                com.enwei.moneymanager.data.AdvisorHolding(code, o.str("action")?.take(12).orEmpty(), o.str("stop")?.toDoubleOrNull(), o.str("reason")?.take(80).orEmpty())
            },
            // 新聞編號先放在 id，呼叫端換成真正的新聞 id
            keyNews = objs("news").mapNotNull { o -> o.str("n")?.toDoubleOrNull()?.toInt()?.let { com.enwei.moneymanager.data.KeyNews(it.toString(), o.str("why")?.take(60).orEmpty()) } },
            allocation = r.str("allocation")?.take(200).orEmpty(), checklist = list("checklist"), warnings = list("warnings"),
        )
    }

    /** 投資助理對話：依資料集與使用者狀況回答，需要時附股票卡片與新聞編號。 */
    suspend fun advisorChat(context: String, history: List<Pair<Boolean, String>>, question: String): com.enwei.moneymanager.data.ChatMsg? {
        if (!enabled) return null
        val system = ADVISOR_ROLE + "以下是資料與使用者狀況，請回答他的問題，口吻簡潔直接，300 字內。提到具體股票時放進 cards；引用新聞時放新聞編號。" +
            "只回 JSON：{\"reply\": 回答, \"cards\": [{\"symbol\": 代號, \"name\": 名稱, \"style\": \"長線\"或\"短線\"或空, \"business\": 公司做什麼, \"strength\": 強勢/中性/弱勢, \"reason\": 40 字內}], \"news\": [編號]}\n\n" +
            context.take(2500)
        val convo = history.takeLast(6).joinToString("\n") { (u, t) -> (if (u) "使用者：" else "助理：") + t.take(300) }
        val r = chatJson(system, (if (convo.isNotBlank()) "先前對話：\n$convo\n\n" else "") + "問題：$question", 900, hard = true, cap = 3000, effort = "low") ?: return null
        val sym = Regex("[0-9]{4,6}[A-Z]?")
        val cards = (r["cards"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonObject)?.let { o -> parsePick(o, sym) } }.orEmpty().take(5)
        val news = (r["news"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.toInt()?.toString() }.orEmpty()
        return com.enwei.moneymanager.data.ChatMsg(false, r.str("reply")?.take(1200) ?: return null, cards = cards, newsIds = news)
    }

    /** 一句話說明這家公司在做什麼（股票資訊頁用，結果會快取）。 */
    suspend fun describeCompany(symbol: String, name: String): String? {
        if (!enabled) return null
        val r = chatJson(
            "你是台股資料助理。用繁體中文 40 字內說明這家台灣上市櫃公司的主要業務、產品與所屬產業；不知道就回空字串。只回 JSON：{\"d\": 說明}",
            "$symbol $name", 200,
        ) ?: return null
        return r.str("d")?.take(80)
    }
}
