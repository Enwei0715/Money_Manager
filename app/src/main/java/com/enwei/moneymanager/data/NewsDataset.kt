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
import kotlinx.serialization.builtins.nullable
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
    /** 資金分配比例（畫成比例條） */
    val alloc: List<AllocSlice> = emptyList(),
    /** AI 標出的大盤關鍵點位（支撐、壓力…，加權指數點數） */
    val levels: List<KeyLevel> = emptyList(),
    /** 大盤短期展望：偏多／盤整／偏空 */
    val outlook: String = "",
)

@Serializable
data class KeyLevel(val label: String, val price: Double)

/** 資金分配的一塊：名稱與百分比。 */
@Serializable
data class AllocSlice(val name: String, val pct: Double)

/** 對話回覆裡的圖表：[kind] 種類，[items] 代號或題材名稱。 */
@Serializable
data class ChatChart(val kind: String, val items: List<String> = emptyList())

/** 投資助理對話的一則訊息；AI 的回覆可附帶股票卡片與新聞。 */
@Serializable
data class ChatMsg(
    val fromUser: Boolean,
    /** AI 回覆的結論（一兩句） */
    val text: String,
    /** AI 回覆的重點條列（依語氣設定的格式） */
    val points: List<String> = emptyList(),
    /** 回覆附的圖表（App 依實際資料畫）：compare 個股走勢比較、themes 題材熱度、holdings 持股損益 */
    val charts: List<ChatChart> = emptyList(),
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
    /** 合併後的標準題材（標註新聞時優先沿用） */
    val themeCanon: List<String> = emptyList(),
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
 * 資料集的檔案格式（grid / block / thread）：
 * - thread：一則新聞的讀取與標註（[NewsItem]）。還沒讀的放在 inbox.json。
 * - block：某一天、某個題材的所有新聞＋整理好的重點文章（[NewsBlock]，一個區塊一個檔案）。
 *   區塊裡每一則新聞都讀完、重點文章也涵蓋了全部新聞時，這個區塊才算完整。
 * - grid：整個資料集的索引（grid.json），列出每個區塊的狀態，加上報告、公司說明等。
 * 只重寫有變動的檔案，標註一批新聞只會寫到 inbox 與相關的幾個區塊，不必每次重寫整個資料集。
 * 所有檔案都是欄位名稱清楚的 JSON，人與 AI 都讀得懂。
 */
@Serializable
data class NewsBlock(
    /** 「日期|題材」 */
    val key: String,
    val day: String,
    val theme: String,
    /** reading：還有新聞沒整理進重點；complete：所有新聞都已讀完並整理 */
    val status: String = "reading",
    /** 這個區塊的新聞則數（thread 數） */
    val threads: Int = 0,
    /** 已整理進重點文章的則數 */
    val read: Int = 0,
    /** 這個區塊的重點文章 */
    val article: ThemeDigest? = null,
    val items: List<NewsItem> = emptyList(),
)

/** 區塊索引的一列。 */
@Serializable
data class BlockInfo(val key: String, val file: String, val status: String, val threads: Int, val read: Int, val updatedAt: Long = 0)

@Serializable
data class NewsGrid(
    val format: String = "grid/block/thread v1：blocks/ 每檔一個「日期|題材」區塊（items＝新聞、article＝重點文章）；inbox.json＝尚未讀取的新聞；chat.json＝對話",
    val version: Int = 1,
    val lastCollect: Long = 0,
    val report: DailyReport? = null,
    val history: List<DailyReport> = emptyList(),
    val business: Map<String, String> = emptyMap(),
    val historyKeepDays: Int = 30,
    /** 合併後的標準題材 */
    val themeCanon: List<String> = emptyList(),
    /** 未讀（待標註）的新聞則數 */
    val inbox: Int = 0,
    val blocks: List<BlockInfo> = emptyList(),
)

@Serializable
private data class Inbox(val note: String = "尚未讀取（標註）的新聞", val items: List<NewsItem> = emptyList())

/** 資料集的完整度：區塊完成數、總區塊數、待讀新聞。 */
data class GridProgress(val complete: Int, val blocks: Int, val unread: Int) {
    val done get() = blocks > 0 && complete == blocks && unread == 0
}

/** 新聞的日期（台北時間），區塊依這個日期分。 */
fun NewsItem.day(): String = java.time.Instant.ofEpochSecond(epochSec).atZone(java.time.ZoneId.of("Asia/Taipei")).toLocalDate().toString()

/**
 * 新聞資料集：背景定期從 Google 新聞 RSS 抓市場與個人持股相關內容，請 AI 逐則標註，
 * 累積近 14 天的資料再統整成題材與個股熱度，給每日選股與對話使用。存在 App 的私有檔案。
 */
class NewsDataset(context: Context) {
    private val dir = File(context.filesDir, "news")
    private val blockDir = File(dir, "blocks")
    /** 舊版的單一大檔（讀到時轉成區塊格式） */
    private val legacy = File(context.filesDir, "news_dataset.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val lock = Mutex()
    @Volatile private var cache: NewsStore? = null
    private val _flow = kotlinx.coroutines.flow.MutableStateFlow<NewsStore?>(null)
    /** 資料集的最新內容：背景蒐集、標註、產生報告時畫面會即時更新 */
    val flow: kotlinx.coroutines.flow.StateFlow<NewsStore?> = _flow

    /** 上次寫入時各檔案的內容（只重寫有變動的檔案） */
    private var savedBlocks: Map<String, NewsBlock> = emptyMap()
    private var savedInbox: List<NewsItem>? = null
    private var savedChat: List<ChatMsg>? = null
    private var savedGrid: NewsGrid? = null

    private val _grid = kotlinx.coroutines.flow.MutableStateFlow(GridProgress(0, 0, 0))
    /** 區塊完成度（狀態列顯示用） */
    val grid: kotlinx.coroutines.flow.StateFlow<GridProgress> = _grid

    // 已經讀進記憶體時直接回傳，不必等正在寫檔的背景工作（對話、畫面不會被新聞分析卡住）
    suspend fun load(): NewsStore = cache ?: lock.withLock { loadUnlocked() }

    /** 上次讀檔失敗的原因（畫面顯示提示用；讀檔失敗不再丟出例外，避免畫面閃退） */
    val loadError = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private fun <T> readOr(file: File, ser: kotlinx.serialization.KSerializer<T>, fallback: T): T {
        if (!file.exists()) return fallback
        return runCatching { json.decodeFromString(ser, file.readText()) }.getOrElse { e ->
            // 單一檔案壞掉只影響那個區塊：另存壞檔，其他區塊照常使用，缺的新聞下次背景更新會補回
            runCatching { file.renameTo(File(file.parentFile, file.name + ".bad-" + System.currentTimeMillis())) }
            loadError.value = "新聞資料集的「${file.name}」讀取失敗，已略過這個區塊（${e.javaClass.simpleName}）"
            fallback
        }
    }

    private suspend fun loadUnlocked(): NewsStore = cache ?: withContext(Dispatchers.IO) {
        val store = if (!File(dir, "grid.json").exists() && legacy.exists()) {
            // 舊版單一大檔：讀進來後改存成區塊格式
            val old = runCatching { json.decodeFromString(NewsStore.serializer(), legacy.readText()) }.getOrElse { e ->
                loadError.value = "新聞資料集讀取失敗，已重新建立（${e.javaClass.simpleName}）"
                NewsStore()
            }
            writeFiles(old)
            runCatching { legacy.renameTo(File(legacy.parentFile, legacy.name + ".migrated")) }
            old
        } else {
            val g = readOr(File(dir, "grid.json"), NewsGrid.serializer(), NewsGrid())
            val inbox = readOr(File(dir, "inbox.json"), Inbox.serializer(), Inbox()).items
            val chat = readOr(File(dir, "chat.json"), kotlinx.serialization.builtins.ListSerializer(ChatMsg.serializer()), emptyList())
            // 依實際的區塊檔案讀（不只看索引），寫到一半中斷時也不會漏掉已寫好的區塊
            val blocks = (blockDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
                .mapNotNull { f -> readOr(f, NewsBlock.serializer().nullable, null) }
            savedBlocks = blocks.associateBy { it.key }
            savedInbox = inbox
            savedChat = chat
            savedGrid = g
            NewsStore(
                items = (inbox + blocks.flatMap { it.items }).distinctBy { it.id }.sortedByDescending { it.epochSec },
                lastCollect = g.lastCollect,
                report = g.report,
                chat = chat,
                business = g.business,
                digests = blocks.mapNotNull { b -> b.article?.let { b.key to it } }.toMap(),
                history = g.history,
                historyKeepDays = g.historyKeepDays,
                themeCanon = g.themeCanon,
            )
        }
        store.also { cache = it; _flow.value = it; _grid.value = progressOf(it) }
    }

    /** 把資料集拆成區塊。 */
    private fun blocksOf(s: NewsStore): List<NewsBlock> {
        val groups = s.items.filter { it.tagged }.groupBy { "${it.day()}|${it.theme}" }
        val keys = groups.keys + s.digests.keys
        return keys.map { key ->
            val items = groups[key].orEmpty().sortedByDescending { it.epochSec }
            val article = s.digests[key]
            val read = if (article != null && articleCovers(article, items)) items.size else 0
            NewsBlock(
                key = key, day = key.substringBefore('|'), theme = key.substringAfter('|'),
                status = if (items.isNotEmpty() && read == items.size || items.isEmpty() && article != null) "complete" else "reading",
                threads = items.size, read = read, article = article, items = items,
            )
        }
    }

    private fun articleCovers(d: ThemeDigest, items: List<NewsItem>): Boolean {
        val ids = d.points.flatMap { it.sourceIds }
        return d.contentHash == groupContentHash(items) && ids.toSet() == items.map { it.id }.toSet() && ids.size == items.size
    }

    private fun progressOf(s: NewsStore): GridProgress {
        val since = recentSince(3)
        val recent = blocksOf(NewsStore(items = s.items.filter { it.epochSec >= since }, digests = emptyMap()))
            .map { b -> b.copy(article = s.digests[b.key]) }
        return GridProgress(
            complete = recent.count { b -> b.article != null && articleCovers(b.article, b.items) },
            blocks = recent.size,
            unread = s.items.count { !it.tagged && it.epochSec >= since },
        )
    }

    private fun fileOf(key: String): String {
        val h = java.security.MessageDigest.getInstance("SHA-1").digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(12)
        return key.substringBefore('|').filter { it.isDigit() || it == '-' }.ifBlank { "day" } + "_" + h + ".json"
    }

    private fun writeAtomic(file: File, text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        try {
            java.nio.file.Files.move(
                tmp.toPath(), file.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /** 只寫有變動的區塊、inbox、對話；索引最後寫（代表這次寫入完成）。 */
    private fun writeFiles(s: NewsStore) {
        val blocks = blocksOf(s)
        val now = System.currentTimeMillis()
        val byKey = blocks.associateBy { it.key }
        for (b in blocks) if (savedBlocks[b.key] != b) writeAtomic(File(blockDir, fileOf(b.key)), json.encodeToString(NewsBlock.serializer(), b))
        for (k in savedBlocks.keys - byKey.keys) File(blockDir, fileOf(k)).delete()
        val inbox = s.items.filter { !it.tagged }
        if (savedInbox != inbox) writeAtomic(File(dir, "inbox.json"), json.encodeToString(Inbox.serializer(), Inbox(items = inbox)))
        if (savedChat != s.chat) writeAtomic(File(dir, "chat.json"), json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatMsg.serializer()), s.chat))
        val prevInfo = savedGrid?.blocks.orEmpty().associateBy { it.key }
        val grid = NewsGrid(
            lastCollect = s.lastCollect, report = s.report, history = s.history, business = s.business,
            historyKeepDays = s.historyKeepDays, inbox = inbox.size, themeCanon = s.themeCanon,
            blocks = blocks.sortedWith(compareByDescending<NewsBlock> { it.day }.thenByDescending { it.threads }).map { b ->
                val info = BlockInfo(b.key, "blocks/" + fileOf(b.key), b.status, b.threads, b.read)
                val prev = prevInfo[b.key]
                info.copy(updatedAt = if (prev != null && prev.copy(updatedAt = 0) == info && savedBlocks[b.key] == b) prev.updatedAt else now)
            },
        )
        if (savedGrid != grid) writeAtomic(File(dir, "grid.json"), json.encodeToString(NewsGrid.serializer(), grid))
        savedBlocks = byKey
        savedInbox = inbox
        savedChat = s.chat
        savedGrid = grid
    }

    private suspend fun save(s: NewsStore) = withContext(Dispatchers.IO) {
        writeFiles(s)
        cache = s
        _flow.value = s
        _grid.value = progressOf(s)
    }

    suspend fun update(f: (NewsStore) -> NewsStore) = lock.withLock { save(f(loadUnlocked())) }

    suspend fun clear() {
        lock.withLock {
            withContext(Dispatchers.IO) {
                dir.deleteRecursively()
                java.nio.file.Files.deleteIfExists(File(legacy.parentFile, legacy.name + ".tmp").toPath())
                java.nio.file.Files.deleteIfExists(legacy.toPath())
            }
            savedBlocks = emptyMap(); savedInbox = null; savedChat = null; savedGrid = null
            cache = NewsStore()
            _flow.value = cache
            _grid.value = GridProgress(0, 0, 0)
        }
    }

    companion object {
        /** 市場、總經類（不是產業題材）的名稱 */
        val MACRO = Regex("個股消息|法人籌碼|財報營收|個股|法人|財報|營收|獲利|基本面|技術面|大盤|台股|股市|美股|陸股|港股|日股|指數|加權|經濟|總經|景氣|通膨|利率|升息|降息|央行|聯準會|匯率|新台幣|關稅|政策|選舉|外資|法人|重挫|大漲|大跌|收盤|盤勢|盤後|行情|市場")

        /**
         * 「近 [days] 天」的起點：台北時間今天往前 days-1 天的 0 點。用整天計算，
         * 同一天同一題材的區塊一定完整落在範圍內，不會隨時間經過被切掉一半而一直顯示「未整理完」。
         */
        fun recentSince(days: Int): Long {
            val zone = java.time.ZoneId.of("Asia/Taipei")
            return java.time.LocalDate.now(zone).minusDays((days - 1).coerceAtLeast(0).toLong()).atStartOfDay(zone).toEpochSecond()
        }
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
    suspend fun tag(stop: () -> Boolean = { false }, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        var done = 0
        // 依模型把每批限制在可完整送出的字符數；未回覆的新聞維持待辦，稍後重試。
        val big = GroqAi.bigContext(GroqAi.AiTask.NEWS_TAG)
        val maxChars = if (big) 18_000 else 2_500
        val maxBatches = if (big) 40 else 15
        val pendingCount = load().items.count { !it.tagged }
        val total = pendingCount.coerceAtMost(maxBatches)
        for (i in 0 until maxBatches) {
            // 每一批之前檢查時間預算與網路：停下時已完成的批次都已寫入，下一輪接續
            if (stop()) return done
            val batch = takeNewsBatch(load().items.filter { !it.tagged }.sortedByDescending { it.tries }, maxChars)
            if (batch.isEmpty()) break
            onProgress(i, maxOf(total, i + 1))
            val ids = batch.map { it.id }.toHashSet()
            // 已有的題材名稱給 AI 參考：意思相同就沿用，避免同一件事分成很多個題材
            val st0 = load()
            val known = (st0.themeCanon + st0.items.asSequence().filter { it.tagged && it.theme.isNotBlank() }.groupingBy { it.theme }.eachCount()
                .entries.sortedByDescending { it.value }.take(80).map { it.key }).distinct().take(80)
            var tags = GroqAi.tagNews(batch, known)
            if (big && tags?.keys?.containsAll(ids) != true) {
                val retried = LinkedHashMap<String, GroqAi.NewsTag>()
                val parts = chunkNews(batch, 2_500)
                for (part in parts) {
                    val result = GroqAi.tagNews(part, known)
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
    suspend fun digest(maxCalls: Int = 40, stop: () -> Boolean = { false }, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        val st = load()
        val zone = java.time.ZoneId.of("Asia/Taipei")
        fun dayOf(n: NewsItem) = java.time.Instant.ofEpochSecond(n.epochSec).atZone(zone).toLocalDate().toString()
        // 包含題材空白的舊資料，否則那些區塊永遠不會完整
        val groups = recent(st, 3).groupBy { dayOf(it) to it.theme }
            .filter { (k, l) ->
                val old = st.digests["${k.first}|${k.second}"]
                old == null || old.contentHash != groupContentHash(l) ||
                    l.size >= old.count + maxOf(if (old.count < 3) 1 else 5, old.count / 5)
            }.entries.sortedByDescending { it.value.size }
        val big = GroqAi.bigContext(GroqAi.AiTask.DIGEST)
        val maxChars = if (big) 18_000 else 2_500
        var calls = 0
        var done = 0
        fun store(k: Pair<String, String>, list: List<NewsItem>, points: List<DigestPoint>, s: NewsStore) =
            s.digests + ("${k.first}|${k.second}" to ThemeDigest(k.first, k.second, list.size, points, System.currentTimeMillis(), groupContentHash(list)))
        fun keepRecent(m: Map<String, ThemeDigest>) = m.filterValues { d ->
            runCatching { java.time.LocalDate.parse(d.day) }.getOrNull()?.isAfter(java.time.LocalDate.now().minusDays(KEEP_DAYS.toLong())) == true
        }
        // 只有 1～2 則的區塊不必叫 AI（標題就是重點）：一次全部寫入，區塊馬上完整
        val small = groups.filter { it.value.size < 3 }
        if (small.isNotEmpty()) {
            update { s ->
                var m = s.digests
                small.forEach { (k, list) ->
                    val pts = list.sortedByDescending { it.importance }
                        .map { DigestPoint(it.note.ifBlank { it.title }.take(80), it.symbols, it.sentiment, it.duplicateCount, listOf(it.id)) }
                    m = store(k, list, pts, s.copy(digests = m))
                }
                s.copy(digests = keepRecent(m))
            }
            done += small.size
        }
        val large = groups.filter { it.value.size >= 3 }
        var failures = 0
        for ((i, e) in large.withIndex()) {
            if (stop()) return done
            // 連續 3 組都失敗（多半是額度用完）才停，單一組失敗跳過、下次再試
            if (failures >= 3) return done
            val (k, list) = e
            val sorted = list.sortedByDescending { it.importance }
            val parts = chunkNews(sorted, maxChars)
            if (calls + parts.size > maxCalls) continue
            onProgress(i, large.size)
            val got = ArrayList<DigestPoint>()
            var ok = true
            for ((pi, part) in parts.withIndex()) {
                calls++
                var result = GroqAi.digestTheme(k.second, k.first, part)
                if (!covers(result, part) && big) {
                    val retryParts = chunkNews(part, 2_500)
                    if (calls + retryParts.size > maxCalls) { ok = false; break }
                    val retried = ArrayList<DigestPoint>()
                    for (retryPart in retryParts) {
                        val retryResult = GroqAi.digestTheme(k.second, k.first, retryPart)
                        calls++
                        if (!covers(retryResult, retryPart)) { ok = false; break }
                        retried += retryResult.orEmpty()
                    }
                    if (!ok) break
                    result = mergePoints(retried)
                }
                if (!covers(result, part)) { ok = false; break }
                got += result.orEmpty()
                if (pi < parts.lastIndex) delay(if (big) 5_000 else 12_000)
            }
            val merged = if (ok) mergePoints(got) else emptyList()
            val covered = merged.flatMap { it.sourceIds }
            if (!ok || covered.toSet() != sorted.map { it.id }.toSet() || covered.size != sorted.size) { failures++; continue }
            failures = 0
            update { s -> s.copy(digests = keepRecent(store(k, list, merged, s))) }
            done++
            if (i < large.lastIndex) delay(if (big) 5_000 else 12_000)
        }
        return done
    }

    /**
     * 合併題材：近 7 天的題材超過 40 個時，請 AI 把同義、說法不同的題材對應到標準題材，
     * 改寫新聞的題材並刪掉被合併掉的舊重點（下一步重新整理）。回傳合併掉的題材數。
     */
    suspend fun consolidateThemes(stop: () -> Boolean = { false }): Int {
        val st = load()
        val since = recentSince(7)
        val counts = st.items.filter { it.tagged && it.epochSec >= since && it.theme.isNotBlank() }.groupingBy { it.theme }.eachCount()
        if (stop()) return 0
        val canonical = st.themeCanon
        val todo = counts.entries.filter { it.key !in canonical }.sortedByDescending { it.value }.map { it.key to it.value }
        // 還沒有標準題材時：題材超過 20 個才合併；之後每次有新的題材名稱都對應到標準題材
        if (todo.isEmpty() || canonical.isEmpty() && counts.size <= 20) return 0
        val map = GroqAi.mergeThemes(todo, canonical) ?: return 0
        val changed = map.filter { (k, v) -> k != v }
        update { s ->
            val items = s.items.map { n -> map[n.theme]?.let { t -> if (t != n.theme) n.copy(theme = t) else n } ?: n }
            val alive = items.filter { it.tagged }.map { "${it.day()}|${it.theme}" }.toSet()
            s.copy(
                items = items,
                // 舊題材的重點已不對應任何新聞：刪掉，合併後的題材會重新整理
                digests = s.digests.filterKeys { it in alive },
                themeCanon = (s.themeCanon + map.values).distinct().take(80),
            )
        }
        return changed.size
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
        val since = recentSince(3)
        val current = st.items.filter { it.epochSec >= since }
        val zone = java.time.ZoneId.of("Asia/Taipei")
        val groups = current.filter { it.tagged }.groupBy {
            val day = java.time.Instant.ofEpochSecond(it.epochSec).atZone(zone).toLocalDate().toString()
            "$day|${it.theme}"
        }
        val digested = groups.entries.sumOf { (key, items) ->
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

    /**
     * 可投資的題材（產業／概念股）：排除大盤漲跌、總經、政策這類市場新聞，且多數新聞要有對應的個股。
     * 回傳題材統計與最常一起出現的個股代號。
     */
    fun investableThemes(st: NewsStore, days: Int = 3): List<Pair<TopicStat, List<String>>> =
        recent(st, days).filter { it.theme.isNotBlank() && !MACRO.containsMatchIn(it.theme) }.groupBy { it.theme }
            .filter { (_, l) -> l.count { it.symbols.isNotEmpty() } * 10 >= l.size * 4 }
            .map { (k, l) ->
                TopicStat(k, l.size, l.map { it.sentiment }.average(), l.count { it.importance >= 4 }) to
                    l.flatMap { it.symbols }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(4).map { it.key }
            }.sortedByDescending { it.first.count }

    /** 近 [days] 天被提到的台股代號統計。 */
    fun symbols(st: NewsStore, days: Int = 3): List<TopicStat> = recent(st, days).flatMap { n -> n.symbols.map { it to n } }
        .groupBy({ it.first }, { it.second }).map { (k, l) -> TopicStat(k, l.size, l.map { it.sentiment }.average(), l.count { it.importance >= 4 }) }
        .sortedByDescending { it.count * 2 + it.important * 3 }

    fun important(st: NewsStore, days: Int = 2, limit: Int = 30): List<NewsItem> = recent(st, days)
        .filter { it.importance >= 4 }.sortedWith(compareByDescending<NewsItem> { it.importance }.thenByDescending { it.epochSec }).take(limit)

    fun recent(st: NewsStore, days: Int): List<NewsItem> {
        val since = recentSince(days)
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
