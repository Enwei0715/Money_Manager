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

    /** 呼叫模型並取得 JSON 物件回覆；失敗回傳 null。 */
    private suspend fun chatJson(system: String, user: String, maxTokens: Int = 200): JsonObject? = withContext(Dispatchers.IO) {
        val s = MoneyApp.repo.settings.value
        if (s.groqKey.isBlank()) return@withContext null
        val body = buildJsonObject {
            put("model", s.aiModel.ifBlank { "llama-3.1-8b-instant" })
            put("temperature", 0)
            put("max_tokens", maxTokens)
            put("response_format", buildJsonObject { put("type", "json_object") })
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                add(buildJsonObject { put("role", "user"); put("content", user) })
            })
        }.toString()
        val req = Request.Builder().url(URL)
            .header("Authorization", "Bearer ${s.groqKey.trim()}")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            Http.client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    lastError = when (resp.code) {
                        401 -> "API 金鑰無效（401）"
                        429 -> "超過 Groq 的使用額度（429），稍後會自動恢復"
                        else -> "HTTP ${resp.code}：${text.take(120)}"
                    }
                    return@use null
                }
                val content = Http.json.parseToJsonElement(text).jsonObject["choices"]
                    ?.let { (it as? kotlinx.serialization.json.JsonArray)?.firstOrNull() }
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.let { (it as? JsonPrimitive)?.contentOrNull } ?: return@use null
                lastError = null
                Http.json.parseToJsonElement(content.substring(content.indexOf('{').coerceAtLeast(0))).jsonObject
            }
        }.getOrElse { lastError = it.message ?: it.javaClass.simpleName; null }
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
        val r = chatJson(system, user, 60 + 50 * items.size) ?: return emptyMap()
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
