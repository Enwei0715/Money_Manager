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
    /** 送去標註但 AI 沒有回覆的次數（兩次後放棄，避免一直重送） */
    val tries: Int = 0,
)

/** 新聞重點：同一事件只算一次。 */
@Serializable
data class DigestPoint(val text: String, val symbols: List<String> = emptyList(), val sentiment: Int = 0, val count: Int = 1)

/** 某一天、某個題材的新聞重點（整理了 [count] 則標題）。 */
@Serializable
data class ThemeDigest(val day: String, val theme: String, val count: Int, val points: List<DigestPoint>, val updatedAt: Long = 0)

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
 * 新聞資料集：背景定期從 Google 新聞抓上百則台股相關標題，請 AI 逐則標註，
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
        runCatching { json.decodeFromString(NewsStore.serializer(), file.readText()) }.getOrDefault(NewsStore()).also { cache = it; _flow.value = it }
    }

    private suspend fun save(s: NewsStore) = withContext(Dispatchers.IO) {
        cache = s
        _flow.value = s
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(NewsStore.serializer(), s))
        tmp.renameTo(file)
    }

    suspend fun update(f: (NewsStore) -> NewsStore) = lock.withLock { save(f(load())) }

    companion object {
        /** 大盤、資金、總經與主要產業題材 */
        val BASE_QUERIES = listOf(
            "台股 大盤", "台股 加權指數", "台股 外資 買賣超", "台股 投信", "台股 自營商", "台股 融資 融券",
            "台股 法說會", "台股 月營收", "台股 財報 EPS", "台股 ETF 配息", "台股 除權息",
            "美股 道瓊 那斯達克", "費城半導體", "聯準會 利率", "新台幣 匯率", "美國 CPI 通膨",
            "台積電", "AI 伺服器", "CoWoS 先進封裝", "IC 設計", "記憶體 DRAM", "PCB 載板", "散熱 液冷",
            "矽光子 光通訊", "網通 低軌衛星", "機器人", "重電 電網", "電動車 供應鏈", "航運 貨櫃",
            "金融股", "生技 新藥", "營建 房市", "鋼鐵 原物料", "觀光 餐飲 內需",
            "台股 盤後", "台股 盤前", "台股 漲停", "台股 跌停", "台股 主力", "台股 當沖", "台股 處置股",
            "櫃買 中小型股", "台股 外資 目標價", "台股 法人 調升", "台股 庫藏股", "台股 增資", "台股 合併 收購",
            "日股 韓股 亞股", "中國 經濟 刺激", "美元 美債 殖利率", "油價 原油", "黃金 價格", "地緣政治 關稅",
            "輝達 NVIDIA", "蘋果 供應鏈", "伺服器 ODM", "被動元件", "面板", "太陽能 儲能", "風電", "軍工 無人機",
            "電信", "食品 民生", "紡織 製鞋", "汽車 零組件", "高股息 ETF", "債券 ETF",
            "鉅亨 台股", "經濟日報 台股", "工商時報 台股", "MoneyDJ 台股", "Yahoo股市 台股", "財訊 台股", "商周 台股",
            "台股 法說會 展望", "台股 毛利率", "台股 接單", "台股 出貨", "台股 擴產", "台股 新廠", "台股 訂單",
            "半導體 設備", "晶圓代工", "成熟製程", "矽晶圓", "IC 封測", "ASIC", "HBM", "GPU", "邊緣 AI", "AI PC 手機",
            "車用電子", "工業電腦", "低軌衛星 供應鏈", "5G 6G", "光學鏡頭", "連接器", "電源供應器", "機殼 滑軌",
            "營建 股", "銀行 金控 獲利", "壽險", "航空", "鋼鐵 報價", "塑化", "水泥", "造紙", "百貨 零售", "電商",
            "生技 臨床", "醫材", "CDMO", "台股 ETF 成分股", "台股 季報", "台股 年報 股利", "台股 現金增資", "台股 除息 填息",
        )
        const val KEEP_DAYS = 14
        const val MAX_ITEMS = 12000
    }

    /** 抓新聞：基本題材＋持股與觀察中的代號，去除重複，保留近 [KEEP_DAYS] 天。回傳新增則數。 */
    suspend fun collect(extraQueries: List<String>): Int {
        // Google 新聞每個查詢最多約 100 則：查詢數 × 100，去重後可達數千則
        val heads = InvestData.headlines(BASE_QUERIES + extraQueries, perQuery = 100, max = 6000)
        val now = System.currentTimeMillis() / 1000
        var added = 0
        update { st ->
            val have = st.items.associateBy { it.id }
            val fresh = heads.mapNotNull { h ->
                val id = idOf(h.title)
                if (id in have) null else NewsItem(id, h.title.take(120), h.source.take(20), h.epochSec.takeIf { it > 0 } ?: now, "", url = h.url)
            }
            added = fresh.size
            val keep = (st.items + fresh).filter { now - it.epochSec <= KEEP_DAYS * 86_400L }
                .sortedByDescending { it.epochSec }.take(MAX_ITEMS)
            st.copy(items = keep, lastCollect = System.currentTimeMillis())
        }
        return added
    }

    /**
     * 請 AI 標註還沒標過的新聞。每批送出後核對回覆：沒被標到的（AI 漏掉、或輸入被截短）
     * 會留在待標註清單，下一批優先重送；同一則送了 3 次仍沒結果，才歸到「其他」，
     * 確保每一則最後都有題材、會被算進統計與重點庫。回傳標註則數。
     */
    suspend fun tag(onProgress: (Int, Int) -> Unit = { _, _ -> }): Int {
        var done = 0
        // Gemini 一次可以標 150 則、請求之間只要稍等；Groq 每分鐘 token 少，一次 40 則、等久一點
        val big = GroqAi.bigContext(GroqAi.AiTask.NEWS_TAG)
        val size = if (big) 150 else 40
        val maxBatches = if (big) 40 else 15
        val total = ((load().items.count { !it.tagged } + size - 1) / size).coerceAtMost(maxBatches)
        for (i in 0 until maxBatches) {
            // 每一批都重新取待標註清單：漏標的（tries 較多）排前面優先重送
            val batch = load().items.filter { !it.tagged }.sortedByDescending { it.tries }.take(size)
            if (batch.isEmpty()) break
            onProgress(i, maxOf(total, i + 1))
            val tags = GroqAi.tagNews(batch.map { it.id to it.title }) ?: return done
            val ids = batch.map { it.id }.toHashSet()
            update { st ->
                st.copy(items = st.items.map { n ->
                    tags[n.id]?.let { t -> n.copy(tagged = true, symbols = t.symbols, theme = t.theme, sentiment = t.sentiment, importance = t.importance, note = t.note) }
                        ?: if (n.id in ids) (if (n.tries >= 2) n.copy(tagged = true, theme = "其他", tries = n.tries + 1) else n.copy(tries = n.tries + 1)) else n
                })
            }
            done += tags.size
            delay(if (big) 6_000 else 15_000)
        }
        return done
    }

    /**
     * 新聞重點庫：近 3 天每天、每個題材的「全部」新聞整理成不重複的重點。
     * - 一組新聞太多時分段送（Gemini 每段 250 則、Groq 60 則），每段的重點再合併，不會因為輸入上限漏讀。
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
                old == null || l.size >= old.count + maxOf(if (old.count < 3) 1 else 5, old.count / 5)
            }.entries.sortedByDescending { it.value.size }
        val big = GroqAi.bigContext(GroqAi.AiTask.DIGEST)
        val chunk = if (big) 250 else 60
        var calls = 0
        var done = 0
        for ((i, e) in groups.withIndex()) {
            val (k, list) = e
            val sorted = list.sortedByDescending { it.importance }
            val points: List<DigestPoint> = if (list.size < 3) {
                sorted.map { DigestPoint(it.note.ifBlank { it.title }.take(80), it.symbols, it.sentiment) }
            } else {
                val parts = sorted.chunked(chunk)
                if (calls + parts.size > maxCalls) continue
                onProgress(i, groups.size)
                val got = ArrayList<DigestPoint>()
                for ((pi, part) in parts.withIndex()) {
                    got += GroqAi.digestTheme(k.second, k.first, part.map { it.title }) ?: return done
                    calls++
                    if (pi < parts.lastIndex) delay(if (big) 5_000 else 12_000)
                }
                mergePoints(got)
            }
            update { s -> s.copy(digests = (s.digests + ("${k.first}|${k.second}" to ThemeDigest(k.first, k.second, list.size, points, System.currentTimeMillis())))
                .filterValues { d -> runCatching { java.time.LocalDate.parse(d.day) }.getOrNull()?.isAfter(java.time.LocalDate.now().minusDays(KEEP_DAYS.toLong())) == true }) }
            done++
            if (list.size >= 3 && i < groups.lastIndex) delay(if (big) 5_000 else 12_000)
        }
        return done
    }

    /** 分段整理出的重點合併：同一件事（開頭相同、或代號相同且內容相近）只留一點、則數相加。 */
    private fun mergePoints(points: List<DigestPoint>): List<DigestPoint> {
        val out = ArrayList<DigestPoint>()
        fun norm(t: String) = t.replace(Regex("[\\s，,。、：:「」]"), "").take(10)
        for (p in points.sortedByDescending { it.count }) {
            val j = out.indexOfFirst { o -> norm(o.text) == norm(p.text) || (o.symbols.isNotEmpty() && o.symbols == p.symbols && o.sentiment == p.sentiment) }
            if (j >= 0) out[j] = out[j].copy(count = out[j].count + p.count) else out += p
        }
        return out.sortedByDescending { it.count }.take(12)
    }

    /** 整理進度：近 3 天已標註、待標註、AI 漏標後歸「其他」、已進重點庫的則數。 */
    data class Coverage(val recent: Int, val pending: Int, val fallback: Int, val digested: Int)

    fun coverage(st: NewsStore): Coverage {
        val r = recent(st, 3)
        val keys = st.digests.values.associate { "${it.day}|${it.theme}" to it.count }
        val zone = java.time.ZoneId.of("Asia/Taipei")
        val digested = r.filter { it.theme.isNotBlank() }
            .groupBy { java.time.Instant.ofEpochSecond(it.epochSec).atZone(zone).toLocalDate().toString() + "|" + it.theme }
            .entries.sumOf { (k, l) -> minOf(l.size, keys[k] ?: 0) }
        return Coverage(r.size, st.items.count { !it.tagged }, st.items.count { it.tries >= 3 }, digested)
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

    private fun idOf(title: String) = Integer.toHexString(title.replace(Regex("\\s"), "").take(40).hashCode())
}
