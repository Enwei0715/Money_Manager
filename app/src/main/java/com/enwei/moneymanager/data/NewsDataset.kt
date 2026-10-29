package com.enwei.moneymanager.data

import android.content.Context
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.net.InvestData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** 一則新聞與 AI 的標註（相關台股代號、題材、多空、重要性）。 */
@Serializable
data class NewsItem(
    val id: String,
    val title: String,
    /** RSS 來源直接提供的正文或摘要；不會抓取連結網頁。 */
    val content: String = "",
    /** 正文、摘要或標題，指出來源實際提供的內容範圍。 */
    val contentLevel: String = "title",
    val source: String = "",
    val epochSec: Long = 0,
    val query: String = "",
    val tagged: Boolean = false,
    val symbols: List<String> = emptyList(),
    val theme: String = "",
    /** -2（大利空）～ +2（大利多） */
    val sentiment: Int = 0,
    /** 1～5，5 為對台股影響最大 */
    val importance: Int = 1,
    /** 重要新聞的一句話重點 */
    val note: String = "",
    /** 新聞連結 */
    val url: String = "",
    /** 送去標註但 AI 沒有回覆的次數；未回覆時保留待處理狀態。 */
    val tries: Int = 0,
    /** 完全相同的來源內容合併後的篇數。 */
    val duplicateCount: Int = 1,
)

/** 新聞重點：同一事件只算一次，並保存涵蓋的新聞 id 以核對是否漏讀。 */
@Serializable
data class DigestPoint(
    val text: String,
    val symbols: List<String> = emptyList(),
    val sentiment: Int = 0,
    val count: Int = 1,
    val sourceIds: List<String> = emptyList(),
)

/** 某一天、某個題材的新聞重點（整理了 [count] 則標題）。 */
@Serializable
data class ThemeDigest(
    val day: String,
    val theme: String,
    val count: Int,
    val points: List<DigestPoint>,
    val updatedAt: Long = 0,
    val contentHash: String = "",
)

/** 每日 AI 選股的一檔。 */
@Serializable
data class StockPick(
    val symbol: String,
    val name: String = "",
    /** 長線／短線 */
    val style: String = "",
    /** 這家公司做什麼 */
    val business: String = "",
    /** 強勢／中性／弱勢（AI 判斷，會再用價格資料核對） */
    val strength: String = "",
    val reason: String = "",
    val entry: String = "",
    val stop: String = "",
    /** 建議投入金額（元） */
    val amount: Long? = null,
    val price: Double? = null,
)

/** 重點新聞：新聞 id 與 AI 說明為什麼重要。 */
@Serializable
data class KeyNews(val id: String, val why: String = "")

/** 每日 AI 投資報告（讀完資料集後產生）。 */
@Serializable
data class DailyReport(
    val createdAt: Long = 0,
    val day: Long = 0,
    val newsCount: Int = 0,
    val trend: String = "",
    val stage: String = "",
    val themes: List<String> = emptyList(),
    val summary: String = "",
    val picks: List<StockPick> = emptyList(),
    val holdings: List<AdvisorHolding> = emptyList(),
    val keyNews: List<KeyNews> = emptyList(),
    val allocation: String = "",
    val checklist: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    /** 報告文字裡 [1][2]… 對應的新聞 id */
    val cites: List<String> = emptyList(),
)

/** 投資助理對話的一則訊息；AI 的回覆可附帶股票卡片與新聞。 */
@Serializable
data class ChatMsg(
    val fromUser: Boolean,
    val text: String,
    val time: Long = System.currentTimeMillis(),
    val cards: List<StockPick> = emptyList(),
    val newsIds: List<String> = emptyList(),
)

@Serializable
data class NewsStore(
    val items: List<NewsItem> = emptyList(),
    val lastCollect: Long = 0,
    val report: DailyReport? = null,
    val chat: List<ChatMsg> = emptyList(),
    /** 代號 → 公司在做什麼（AI 說明，快取） */
    val business: Map<String, String> = emptyMap(),
    /** 新聞重點庫：「日期|題材」→ 重點（所有新聞都整理進來） */
    val digests: Map<String, ThemeDigest> = emptyMap(),
    /** 過去的每日報告（新到舊，不含目前這份） */
    val history: List<DailyReport> = emptyList(),
    /** 報告保留天數；0 表示不自動刪除 */
    val historyKeepDays: Int = 30,
)

/** 依保留天數刪掉太舊的報告。 */
fun NewsStore.prunedHistory(): NewsStore {
    if (historyKeepDays <= 0) return this
    val cut = System.currentTimeMillis() - historyKeepDays * 86_400_000L
    return copy(history = history.filter { it.createdAt >= cut })
}

/** 題材或個股在資料集中的統計。 */
data class TopicStat(val key: String, val count: Int, val avgSentiment: Double, val important: Int)

/**
 * 新聞資料集：背景定期從 Google 新聞 RSS 抓市場與個人持股相關內容，請 AI 逐則標註，
 * 累積近 14 天的資料再統整成題材與個股熱度，給每日選股與對話使用。存在 App 的私有檔案。
 */
class NewsDataset(context: Context) {
    private val file = File(context.filesDir, "news_dataset.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    @Volatile private var cache: NewsStore? = null
    private val _flow = kotlinx.coroutines.flow.MutableStateFlow<NewsStore?>(null)
    /** 資料集的最新內容：背景蒐集、標註、產生報告時畫面會即時更新 */
    val flow: kotlinx.coroutines.flow.StateFlow<NewsStore?> = _flow

    suspend fun load(): NewsStore = cache ?: withContext(Dispatchers.IO) {
        val store = if (file.exists()) {
            runCatching { json.decodeFromString(NewsStore.serializer(), file.readText()) }
                .getOrElse { throw IllegalStateException("新聞資料集讀取失敗，原始檔案已保留", it) }
        } else NewsStore()
        store.also { cache = it; _flow.value = it }
    }

    private suspend fun save(s: NewsStore) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(NewsStore.serializer(), s))
        try {
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        cache = s
        _flow.value = s
    }

    suspend fun update(f: (NewsStore) -> NewsStore) = lock.withLock { save(f(load())) }

    companion object {
        const val KEEP_DAYS = 14
        const val MAX_ITEMS = 12000
    }

    /** 以寬泛市場詞與個人持股／觀察標的蒐集新聞，不使用固定產業題材清單。 */
    suspend fun collect(extraQueries: List<String>): Int {
        val queries = (listOf("台灣 股市", "台灣 財經", "全球 市場 經濟") + extraQueries)
            .map(String::trim).filter(String::isNotBlank).distinct()
        val heads = InvestData.headlines(queries, perQuery = 100, max = 1200)
        InvestData.lastHeadlineError?.let {
            throw IllegalStateException(it)
        }
        val now = System.currentTimeMillis() / 1000
        var added = 0
        update { st ->
            fun mergeCopies(copies: List<NewsItem>, duplicateCount: Int): NewsItem {
                val best = copies.maxWithOrNull(compareBy<NewsItem> { it.content.length }.thenBy { it.epochSec })!!
                val annotation = copies.firstOrNull { it.tagged }
                return best.copy(
                    id = copies.first().id,
                    tagged = annotation != null,
                    symbols = annotation?.symbols ?: best.symbols,
                    theme = annotation?.theme ?: best.theme,
                    sentiment = annotation?.sentiment ?: best.sentiment,
                    importance = annotation?.importance ?: best.importance,
                    note = annotation?.note?.takeIf(String::isNotBlank) ?: best.note,
                    tries = copies.minOf { it.tries },
                    duplicateCount = duplicateCount.coerceAtLeast(1),
                )
            }
            val existing = st.items.groupBy { contentFingerprint(it.title, it.content) }.values
                .map { copies -> mergeCopies(copies, copies.sumOf { it.duplicateCount.coerceAtLeast(1) }) }
                .toMutableList()
            val fresh = heads.map { h ->
                NewsItem(
                    id = idOf(h.title, h.content),
                    title = h.title,
                    content = h.content,
                    contentLevel = h.contentLevel,
                    source = h.source.take(80),
                    epochSec = h.epochSec.takeIf { it > 0 } ?: now,
                    url = h.url,
                    duplicateCount = h.duplicateCount.coerceAtLeast(1),
                )
            }
            fresh.forEach { incoming ->
                val exact = contentFingerprint(incoming.title, incoming.content)
                val titleOnly = titleFingerprint(incoming.title)
                val index = existing.indexOfFirst { current ->
                    contentFingerprint(current.title, current.content) == exact ||
                        (current.content.isBlank() || incoming.content.isBlank()) && titleFingerprint(current.title) == titleOnly
                }
                if (index < 0) {
                    existing += incoming
                    added++
                } else {
                    val current = existing[index]
                    val contentImproved = incoming.content.length > current.content.length
                    val metadataChanged = incoming.title != current.title && incoming.epochSec >= current.epochSec
                    val best = if (contentImproved || metadataChanged ||
                        incoming.epochSec > current.epochSec && incoming.content.length == current.content.length
                    ) incoming else current
                    val annotation = if (contentImproved || metadataChanged) null else listOf(current, incoming).firstOrNull { it.tagged }
                    existing[index] = best.copy(
                        id = current.id,
                        tagged = annotation != null,
                        symbols = annotation?.symbols ?: best.symbols,
                        theme = annotation?.theme ?: best.theme,
                        sentiment = annotation?.sentiment ?: best.sentiment,
                        importance = annotation?.importance ?: best.importance,
                        note = annotation?.note?.takeIf(String::isNotBlank) ?: best.note,
                        tries = if (contentImproved || metadataChanged) 0 else minOf(current.tries, incoming.tries),
                        duplicateCount = maxOf(current.duplicateCount, incoming.duplicateCount).coerceAtLeast(1),
                    )
                }
            }
            val keep = existing.filter { now - it.epochSec <= KEEP_DAYS * 86_400L }
                .sortedByDescending { it.epochSec }.take(MAX_ITEMS)
            st.copy(items = keep, lastCollect = if (InvestData.lastHeadlineError == null) System.currentTimeMillis() else st.lastCollect)
        }
        return added
    }

    /**
     * 請 AI 標註還沒標過的新聞。每批送出後核對回覆：沒有回覆到的項目
     * 會留在待標註清單，下一批優先重送；報告完整性檢查會阻止未完成的資料被當成完整結果。回傳標註則數。
     */
    suspend fun tag(onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        var done = 0
        // 依模型把每批限制在可完整送出的字符數；未回覆的新聞維持待辦，稍後重試。
        val big = GroqAi.bigContext(GroqAi.AiTask.NEWS_TAG)
        val maxChars = if (big) 18_000 else 2_500
        val maxBatches = if (big) 40 else 15
        val pendingCount = load().items.count { !it.tagged }
        val total = pendingCount.coerceAtMost(maxBatches)
        for (i in 0 until maxBatches) {
            val batch = takeNewsBatch(load().items.filter { !it.tagged }.sortedByDescending { it.tries }, maxChars)
            if (batch.isEmpty()) break
            onProgress(i, maxOf(total, i + 1))
            val ids = batch.map { it.id }.toHashSet()
            var tags = GroqAi.tagNews(batch)
            if (big && tags?.keys?.containsAll(ids) != true) {
                val retried = LinkedHashMap<String, GroqAi.NewsTag>()
                val parts = chunkNews(batch, 2_500)
                for (part in parts) {
                    val result = GroqAi.tagNews(part)
                    if (result == null || !result.keys.containsAll(part.map { it.id })) {
                        retried.clear()
                        break
                    }
                    retried.putAll(result)
                }
                if (retried.keys.containsAll(ids)) tags = retried
            }
            val completeTags = tags
            if (completeTags == null) {
                update { st -> st.copy(items = st.items.map { if (it.id in ids) it.copy(tries = it.tries + 1) else it }) }
                return done
            }
            update { st ->
                st.copy(items = st.items.map { n ->
                    completeTags[n.id]?.let { t -> n.copy(tagged = true, symbols = t.symbols, theme = t.theme, sentiment = t.sentiment, importance = t.importance, note = t.note) }
                        ?: if (n.id in ids) n.copy(tries = n.tries + 1) else n
                })
            }
            done += completeTags.size
            if (completeTags.size < batch.size) return done
            delay(if (big) 6_000 else 15_000)
        }
        return done
    }

    /**
     * 新聞重點庫：近 3 天每天、每個題材的「全部」新聞整理成不重複的重點。
     * - 依內容長度分段送，每段核對所有來源 id 均被納入，再合併重點。
     * - 只有 1～2 則的小組不必叫 AI，直接把標題當重點，所以每一則都會進到重點庫。
     * - 已整理過的只在新聞增加不少時才重整；先整理則數多的。回傳整理了幾組。
     */
    suspend fun digest(maxCalls: Int = 40, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val st = load()
        val zone = java.time.ZoneId.of("Asia/Taipei")
        fun dayOf(n: NewsItem) = java.time.Instant.ofEpochSecond(n.epochSec).atZone(zone).toLocalDate().toString()
        val groups = recent(st, 3).filter { it.theme.isNotBlank() }.groupBy { dayOf(it) to it.theme }
            .filter { (k, l) ->
                val old = st.digests["${k.first}|${k.second}"]
                old == null || old.contentHash != groupContentHash(l) ||
                    l.size >= old.count + maxOf(if (old.count < 3) 1 else 5, old.count / 5)
            }.entries.sortedByDescending { it.value.size }
        val big = GroqAi.bigContext(GroqAi.AiTask.DIGEST)
        val maxChars = if (big) 18_000 else 2_500
        var calls = 0
        var done = 0
        for ((i, e) in groups.withIndex()) {
            val (k, list) = e
            val sorted = list.sortedByDescending { it.importance }
            val points: List<DigestPoint> = if (list.size < 3) {
                sorted.map { DigestPoint(it.note.ifBlank { it.title }.take(80), it.symbols, it.sentiment, it.duplicateCount, listOf(it.id)) }
            } else {
                val parts = chunkNews(sorted, maxChars)
                if (calls + parts.size > maxCalls) continue
                onProgress(i, groups.size)
                val got = ArrayList<DigestPoint>()
                for ((pi, part) in parts.withIndex()) {
                    calls++
                    var result = GroqAi.digestTheme(k.second, k.first, part)
                    if (!covers(result, part) && big) {
                        val retryParts = chunkNews(part, 2_500)
                        if (calls + retryParts.size > maxCalls) return done
                        val retried = ArrayList<DigestPoint>()
                        for (retryPart in retryParts) {
                            val retryResult = GroqAi.digestTheme(k.second, k.first, retryPart)
                            if (!covers(retryResult, retryPart)) return done
                            retried += retryResult.orEmpty()
                            calls++
                        }
                        result = mergePoints(retried)
                    }
                    if (!covers(result, part)) return done
                    got += result.orEmpty()
                    if (pi < parts.lastIndex) delay(if (big) 5_000 else 12_000)
                }
                val merged = mergePoints(got)
                val covered = merged.flatMap { it.sourceIds }
                if (covered.toSet() != sorted.map { it.id }.toSet() || covered.size != sorted.size) return done
                merged
            }
            update { s -> s.copy(digests = (s.digests + ("${k.first}|${k.second}" to ThemeDigest(
                k.first, k.second, list.size, points, System.currentTimeMillis(), groupContentHash(list),
            )))
                .filterValues { d -> runCatching { java.time.LocalDate.parse(d.day) }.getOrNull()?.isAfter(java.time.LocalDate.now().minusDays(KEEP_DAYS.toLong())) == true }) }
            done++
            if (list.size >= 3 && i < groups.lastIndex) delay(if (big) 5_000 else 12_000)
        }
        return done
    }

    /** 分段整理出的重點合併：只有整理文字相同時才合併，避免同代號的不同事件被混成一點。 */
    private fun mergePoints(points: List<DigestPoint>): List<DigestPoint> {
        val out = ArrayList<DigestPoint>()
        fun norm(t: String) = t.lowercase().replace(Regex("[\\s，,。、：:「」]"), "")
        for (p in points.sortedByDescending { it.count }) {
            val j = out.indexOfFirst { o -> norm(o.text) == norm(p.text) }
            if (j >= 0) out[j] = out[j].copy(
                count = out[j].count + p.count,
                sourceIds = (out[j].sourceIds + p.sourceIds).distinct(),
            ) else out += p
        }
        return out.sortedByDescending { it.count }
    }

    /** 整理進度：近 3 天已標註、待標註、已進重點庫的則數。 */
    data class Coverage(val recent: Int, val pending: Int, val digested: Int)

    fun coverage(st: NewsStore): Coverage {
        val since = System.currentTimeMillis() / 1000 - 3 * 86_400L
        val current = st.items.filter { it.epochSec >= since }
        val zone = java.time.ZoneId.of("Asia/Taipei")
        val groups = current.filter { it.tagged }.groupBy {
            val day = java.time.Instant.ofEpochSecond(it.epochSec).atZone(zone).toLocalDate().toString()
            "$day|${it.theme}"
        }
        val digested = groups.sumOf { (key, items) ->
            val digest = st.digests[key] ?: return@sumOf 0
            val ids = digest.points.flatMap { it.sourceIds }
            if (digest.contentHash == groupContentHash(items) &&
                ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size
            ) items.size else 0
        }
        return Coverage(
            recent = current.size,
            pending = current.count { !it.tagged },
            digested = digested,
        )
    }

    /** 近 [days] 天的題材統計（依則數排序）。 */
    fun themes(st: NewsStore, days: Int = 3): List<TopicStat> = recent(st, days).filter { it.theme.isNotBlank() }
        .groupBy { it.theme }.map { (k, l) -> TopicStat(k, l.size, l.map { it.sentiment }.average(), l.count { it.importance >= 4 }) }
        .sortedByDescending { it.count }

    /** 近 [days] 天被提到的台股代號統計。 */
    fun symbols(st: NewsStore, days: Int = 3): List<TopicStat> = recent(st, days).flatMap { n -> n.symbols.map { it to n } }
        .groupBy({ it.first }, { it.second }).map { (k, l) -> TopicStat(k, l.size, l.map { it.sentiment }.average(), l.count { it.importance >= 4 }) }
        .sortedByDescending { it.count * 2 + it.important * 3 }

    fun important(st: NewsStore, days: Int = 2, limit: Int = 30): List<NewsItem> = recent(st, days)
        .filter { it.importance >= 4 }.sortedWith(compareByDescending<NewsItem> { it.importance }.thenByDescending { it.epochSec }).take(limit)

    fun recent(st: NewsStore, days: Int): List<NewsItem> {
        val since = System.currentTimeMillis() / 1000 - days * 86_400L
        return st.items.filter { it.tagged && it.epochSec >= since }
    }

    fun about(st: NewsStore, symbol: String, limit: Int = 20): List<NewsItem> =
        st.items.filter { symbol in it.symbols }.sortedByDescending { it.epochSec }.take(limit)

    private fun takeNewsBatch(items: List<NewsItem>, maxChars: Int): List<NewsItem> {
        val batch = ArrayList<NewsItem>()
        var chars = 0
        for (item in items) {
            val size = item.title.length + item.content.length + 40
            if (batch.isNotEmpty() && chars + size > maxChars) break
            batch += item
            chars += size
        }
        return batch
    }

    private fun chunkNews(items: List<NewsItem>, maxChars: Int): List<List<NewsItem>> {
        val chunks = ArrayList<List<NewsItem>>()
        var chunk = ArrayList<NewsItem>()
        var chars = 0
        for (item in items) {
            val size = item.title.length + item.content.length + 40
            if (chunk.isNotEmpty() && chars + size > maxChars) {
                chunks += chunk
                chunk = ArrayList()
                chars = 0
            }
            chunk += item
            chars += size
        }
        if (chunk.isNotEmpty()) chunks += chunk
        return chunks
    }

    private fun covers(points: List<DigestPoint>?, items: List<NewsItem>): Boolean {
        if (points == null) return false
        val covered = points.flatMap { it.sourceIds }
        val expected = items.map { it.id }.toSet()
        return covered.toSet() == expected && covered.size == expected.size
    }

    private fun normalizedContent(title: String, content: String): String =
        (content.ifBlank { title }).lowercase().replace(Regex("\\s+"), " ").trim()

    private fun contentFingerprint(title: String, content: String): String {
        val normalized = normalizedContent(title, content)
        val bytes = java.security.MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun titleFingerprint(title: String): String = contentFingerprint(title, "")

    private fun idOf(title: String, content: String): String = contentFingerprint(title, content).take(16)

    private fun groupContentHash(items: List<NewsItem>): String =
        contentFingerprint("", items.sortedBy { it.id }.joinToString("\n") {
            "${it.id}:${it.title}:${it.contentLevel}:${it.content}:${it.duplicateCount}"
        })
}
