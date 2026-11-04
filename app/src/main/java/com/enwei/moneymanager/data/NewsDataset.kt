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
    /** 整理方式版本：2＝依題材 block 整理（舊的逐日小組整理會自動重新整理一次） */
    val v: Int = 1,
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
    /** 產業（依上市櫃產業分類） */
    val industry: String = "",
    /** App 用量價與營收算出的依據標籤（20 日漲幅、相對大盤、均線、量比、營收） */
    val basis: List<String> = emptyList(),
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
    /** 回答的依據：搜尋到幾則相關新聞、讀了幾篇全文 */
    val basis: String = "",
)

/** 一段過去的對話（按「新對話」時把目前的對話收進這裡，可以再打開接著聊）。 */
@Serializable
data class ChatThread(val id: Long, val title: String, val msgs: List<ChatMsg>, val updated: Long = System.currentTimeMillis())

@Serializable
data class NewsStore(
    val items: List<NewsItem> = emptyList(),
    val lastCollect: Long = 0,
    val report: DailyReport? = null,
    val chat: List<ChatMsg> = emptyList(),
    /** 過去的對話（最新的在前，最多 30 段） */
    val chatThreads: List<ChatThread> = emptyList(),
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
    /** grid：所有 block 重點彙整的市場大局（條列）與整理時間 */
    val overview: List<String> = emptyList(),
    val overviewAt: Long = 0,
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
    /** 市場大局（所有 block 重點的彙整） */
    val overview: List<String> = emptyList(),
    val overviewAt: Long = 0,
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
    private var savedThreads: List<ChatThread>? = null
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
            val threads = readOr(File(dir, "chats.json"), kotlinx.serialization.builtins.ListSerializer(ChatThread.serializer()), emptyList())
            savedThreads = threads
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
                chatThreads = threads,
                business = g.business,
                digests = blocks.mapNotNull { b -> b.article?.let { b.key to it } }.toMap(),
                history = g.history,
                historyKeepDays = g.historyKeepDays,
                themeCanon = g.themeCanon,
                overview = g.overview, overviewAt = g.overviewAt,
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
        if (savedThreads != s.chatThreads) writeAtomic(File(dir, "chats.json"),
            json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatThread.serializer()), s.chatThreads))
        if (savedChat != s.chat) writeAtomic(File(dir, "chat.json"), json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ChatMsg.serializer()), s.chat))
        val prevInfo = savedGrid?.blocks.orEmpty().associateBy { it.key }
        val grid = NewsGrid(
            lastCollect = s.lastCollect, report = s.report, history = s.history, business = s.business,
            historyKeepDays = s.historyKeepDays, inbox = inbox.size, themeCanon = s.themeCanon,
            overview = s.overview, overviewAt = s.overviewAt,
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
        savedThreads = s.chatThreads
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
            savedBlocks = emptyMap(); savedInbox = null; savedChat = null; savedThreads = null; savedGrid = null
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
        // 股票名稱清單：標題裡寫了公司名稱（或金控簡稱）也連到代號
        runCatching { com.enwei.moneymanager.net.TwData.stockList(com.enwei.moneymanager.MoneyApp.instance) }
        // 已標註的新聞補上標題裡提到、但 AI 沒標到的代號（只改有變動的）
        if (com.enwei.moneymanager.net.TwData.cached().isNotEmpty()) update { st ->
            var changed = false
            val items = st.items.map { n ->
                if (!n.tagged) return@map n
                val add = com.enwei.moneymanager.net.TwData.codesIn(n.title, 4).filter { it !in n.symbols }
                if (add.isEmpty()) n else { changed = true; n.copy(symbols = n.symbols + add) }
            }
            if (changed) st.copy(items = items) else st
        }
        // 依模型把每批限制在可完整送出的字符數；未回覆的新聞維持待辦，稍後重試。
        val big = GroqAi.bigContext(GroqAi.AiTask.NEWS_TAG)
        val maxChars = if (big) 18_000 else 2_500
        val maxBatches = if (big) 40 else 15
        val pendingCount = load().items.count { !it.tagged }
        val total = pendingCount.coerceAtMost(maxBatches)
        for (i in 0 until maxBatches) {
            // 每一批之前檢查時間預算與網路：停下時已完成的批次都已寫入，下一輪接續
            if (stop()) return done
            // thread：可讀大量資料時每次 20 則（標題＋內文頭尾各 200 字），否則依字數限制
            val todo = load().items.filter { !it.tagged }.sortedByDescending { it.tries }
            val batch = if (big) takeNewsBatch(todo.take(20), 12_000) else takeNewsBatch(todo, maxChars)
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
                    // 部分成功也保留，沒標到的下一輪再標
                    GroqAi.tagNews(part, known)?.let { retried.putAll(it) }
                }
                if (retried.size > (tags?.size ?: 0)) tags = retried
            }
            val completeTags = tags
            if (completeTags == null) {
                update { st -> st.copy(items = st.items.map { if (it.id in ids) it.copy(tries = it.tries + 1) else it }) }
                return done
            }
            update { st ->
                st.copy(items = st.items.map { n ->
                    completeTags[n.id]?.let { t -> n.copy(tagged = true, symbols = (t.symbols + com.enwei.moneymanager.net.TwData.codesIn(n.title, 4)).distinct(), theme = t.theme, sentiment = t.sentiment, importance = t.importance, note = t.note) }
                        ?: if (n.id in ids) n.copy(tries = n.tries + 1) else n
                })
            }
            done += completeTags.size
            // 這批只標到一部分也繼續下一批（沒標到的保留待辦）；一則都沒標到才停
            if (completeTags.isEmpty()) return done
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
                needsDigest(old, l)
            }.entries.sortedByDescending { it.value.size }
        val big = GroqAi.bigContext(GroqAi.AiTask.DIGEST)
        val maxChars = if (big) 18_000 else 2_500
        var calls = 0
        var done = 0
        fun store(k: Pair<String, String>, list: List<NewsItem>, points: List<DigestPoint>, s: NewsStore) =
            s.digests + ("${k.first}|${k.second}" to ThemeDigest(k.first, k.second, list.size, points, System.currentTimeMillis(), groupContentHash(list), v = 2))
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
        // block：同一個題材近 3 天的新聞放同一塊（較少見的題材合成一塊），一次給 AI 全局整理；
        // 只處理含有待整理區塊的題材，結果依新聞 id 寫回各「日期|題材」區塊（畫面與報告照舊讀取）
        val large = groups.filter { it.value.size >= 3 }
        if (large.isEmpty()) return done
        val needThemes = large.map { it.key.second }.toSet()
        val byTheme = recent(st, 3).filter { it.tagged && it.theme in needThemes }.groupBy { it.theme }
        val rare = byTheme.filterValues { it.size < 8 }
        val blocks = ArrayList<Pair<String, List<NewsItem>>>()
        byTheme.filterValues { it.size >= 8 }.entries.sortedByDescending { it.value.size }.forEach { (t, l) ->
            chunkNews(l.sortedByDescending { it.epochSec }.take(400), if (big) 18_000 else 2_500).forEach { blocks += t to it }
        }
        if (rare.isNotEmpty()) chunkNews(rare.values.flatten().sortedBy { it.theme }, if (big) 18_000 else 2_500).forEach { blocks += "其他題材" to it }
        val groupOf = recent(st, 3).filter { it.tagged }.groupBy { dayOf(it) to it.theme }
        var failures = 0
        for ((bi, b) in blocks.withIndex()) {
            if (stop()) return done
            if (failures >= 3) return done
            if (calls >= maxCalls) return done
            val (label, items) = b
            onProgress(bi, blocks.size)
            calls++
            var pts = GroqAi.digestBlock(label, items, ::dayOf)
            // 大模型都失敗或漏掉一半以上：拆成小段，讓可讀量小的模型也能接手
            if ((pts == null || coverage(pts, items) < 0.5) && big && items.size > 8) {
                val got = ArrayList<DigestPoint>()
                var ok = true
                for (part in chunkNews(items, 2_500)) {
                    if (stop() || calls >= maxCalls) { ok = false; break }
                    calls++
                    val r = GroqAi.digestBlock(label, part, ::dayOf)
                    if (r == null) { ok = false; break }
                    got += repair(r, part)
                    delay(3_000)
                }
                pts = if (ok) got else pts
            }
            if (pts == null) { failures++; continue }
            failures = 0
            val fixed = repair(pts, items)
            // 寫回：每個「日期|題材」區塊取屬於它的新聞的重點
            val touched = items.map { dayOf(it) to it.theme }.toSet()
            update { s ->
                var m = s.digests
                touched.forEach { k ->
                    val list = groupOf[k] ?: return@forEach
                    val ids = list.map { it.id }.toSet()
                    val own = fixed.mapNotNull { p -> p.sourceIds.filter { it in ids }.takeIf { it.isNotEmpty() }?.let { p.copy(sourceIds = it, count = it.size) } }
                    val missing = list.filter { n -> own.none { n.id in it.sourceIds } }
                        .map { DigestPoint(it.note.ifBlank { it.title }.take(80), it.symbols, it.sentiment, it.duplicateCount, listOf(it.id)) }
                    m = store(k, list, mergePoints(own + missing), s.copy(digests = m))
                }
                s.copy(digests = keepRecent(m))
            }
            done += touched.size
            if (bi < blocks.lastIndex) delay(if (big) 3_000 else 8_000)
        }
        return done
    }

    /**
     * grid：把所有題材的重點彙整成市場大局（一次呼叫）。重點有更新、或上次整理超過 3 小時才重做。
     */
    suspend fun overview(force: Boolean = false): Boolean {
        val st = load()
        val newest = st.digests.values.maxOfOrNull { it.updatedAt } ?: return false
        if (!force && st.overview.isNotEmpty() && st.overviewAt >= newest && System.currentTimeMillis() - st.overviewAt < 3 * 3600_000L) return false
        val cutoff = java.time.LocalDate.now().minusDays(2).toString()
        val text = buildString {
            st.digests.values.filter { it.day >= cutoff }.groupBy { it.theme }
                .entries.sortedByDescending { e -> e.value.sumOf { it.count } }.take(40).forEach { (theme, days) ->
                    appendLine("【$theme】${days.sumOf { it.count }} 則")
                    days.sortedByDescending { it.day }.forEach { d ->
                        d.points.sortedByDescending { it.count }.take(6).forEach { p ->
                            appendLine("- ${d.day.takeLast(5)} ${p.text}" + (if (p.symbols.isNotEmpty()) "｜${p.symbols.joinToString(",")}" else "") + "｜${p.sentiment}｜${p.count}則")
                        }
                    }
                }
        }.take(60_000)
        val o = GroqAi.gridOverview(text) ?: return false
        update { it.copy(overview = o, overviewAt = System.currentTimeMillis()) }
        return true
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

    /**
     * 這個區塊需不需要（重新）整理重點：沒整理過就要；整理過的，新增的新聞夠多（至少 3 則或 30%）
     * 且距上次整理超過 6 小時才重整，避免每多一則就整塊重做、很快用完 AI 每日額度。
     */
    fun needsDigest(old: ThemeDigest?, list: List<NewsItem>): Boolean =
        old == null || old.v < 2 || (System.currentTimeMillis() - old.updatedAt > 6 * 3600_000L && list.size >= old.count + maxOf(3, old.count * 3 / 10))

    fun coverage(st: NewsStore): Coverage {
        val since = recentSince(3)
        val current = st.items.filter { it.epochSec >= since }
        val zone = java.time.ZoneId.of("Asia/Taipei")
        val groups = current.filter { it.tagged }.groupBy {
            val day = java.time.Instant.ofEpochSecond(it.epochSec).atZone(zone).toLocalDate().toString()
            "$day|${it.theme}"
        }
        // 整理過、且不需要重整（見 needsDigest）的區塊都算完成；不再要求每一則都逐字對上，否則每多一則就整塊重做，很耗 AI 額度
        val digested = groups.entries.sumOf { (key, items) -> if (needsDigest(st.digests[key], items)) 0 else items.size }
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

    /** AI 歸類到的新聞比例（重複歸類只算一次） */
    private fun coverage(points: List<DigestPoint>, items: List<NewsItem>): Double {
        val ids = items.map { it.id }.toSet()
        return points.flatMap { it.sourceIds }.toSet().count { it in ids }.toDouble() / ids.size.coerceAtLeast(1)
    }

    /**
     * 修正 AI 的歸類：同一則被放進多個重點時只留第一個、拿掉不在這批的編號與空的重點，
     * 漏掉的新聞用它的摘要或標題自成一點，確保每一則都在重點裡、整理一定有進展。
     */
    private fun repair(points: List<DigestPoint>, items: List<NewsItem>): List<DigestPoint> {
        val ids = items.map { it.id }.toSet()
        val seen = HashSet<String>()
        val fixed = points.mapNotNull { p ->
            val src = p.sourceIds.filter { it in ids && seen.add(it) }
            if (src.isEmpty()) null else p.copy(sourceIds = src, count = src.size)
        }
        val missing = items.filter { it.id !in seen }
            .map { DigestPoint(it.note.ifBlank { it.title }.take(80), it.symbols, it.sentiment, it.duplicateCount, listOf(it.id)) }
        return fixed + missing
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


/** 對話標題：第一個問題的前 18 個字。 */
fun chatTitle(msgs: List<ChatMsg>): String =
    msgs.firstOrNull { it.fromUser }?.text?.replace("\n", " ")?.trim()?.let { if (it.length > 18) it.take(18) + "…" else it } ?: "對話"

/** 開新對話：目前的對話（有內容時）收進過去的對話。 */
fun NewsStore.newChat(): NewsStore =
    if (chat.isEmpty()) this
    else copy(chat = emptyList(), chatThreads = (listOf(ChatThread(System.currentTimeMillis(), chatTitle(chat), chat)) + chatThreads).take(30))

/** 打開過去的一段對話：目前的對話先收起來，再把選的那段變成目前的對話。 */
fun NewsStore.openChat(id: Long): NewsStore {
    val t = chatThreads.firstOrNull { it.id == id } ?: return this
    val rest = chatThreads.filter { it.id != id }
    val parked = if (chat.isEmpty()) rest else listOf(ChatThread(System.currentTimeMillis(), chatTitle(chat), chat)) + rest
    return copy(chat = t.msgs, chatThreads = parked.take(30))
}
