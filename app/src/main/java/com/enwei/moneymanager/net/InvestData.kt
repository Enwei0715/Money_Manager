package com.enwei.moneymanager.net

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

/** 日 K 線（收盤、最高）與日期（epochDay），由舊到新。 */
data class DailyBars(
    val days: List<Long>, val close: List<Double>, val high: List<Double>, val name: String?,
    val open: List<Double> = close, val low: List<Double> = close, val volume: List<Double> = emptyList(),
) {
    /** 把盤中即時的今天 K 棒併進來：同一天就更新收盤、最高、最低、量；新的一天就加一根。 */
    fun withLive(l: QuoteService.LiveBar): DailyBars {
        if (close.isEmpty()) return this
        val lastDay = days.last()
        return when {
            l.day == lastDay -> {
                val i = close.lastIndex
                copy(
                    close = close.dropLast(1) + l.price,
                    high = high.dropLast(1) + maxOf(high[i], l.high ?: l.price, l.price),
                    low = low.dropLast(1) + minOf(low[i], l.low ?: l.price, l.price),
                    open = if (l.open != null) open.dropLast(1) + l.open else open,
                    volume = if (l.volume != null && volume.size == close.size) volume.dropLast(1) + l.volume else volume,
                )
            }
            l.day > lastDay -> copy(
                days = days + l.day, close = close + l.price, high = high + maxOf(l.high ?: l.price, l.price),
                low = low + minOf(l.low ?: l.price, l.price), open = open + (l.open ?: l.prev ?: l.price),
                volume = if (volume.size == close.size) volume + (l.volume ?: 0.0) else volume,
            )
            else -> this
        }
    }

    /** [fromDay] 之後（含）的最高價 */
    fun peakSince(fromDay: Long): Double? = days.indices.filter { days[it] >= fromDay }.maxOfOrNull { high[it] }
}

/** 新聞來源直接提供的標題、正文或摘要；不擷取連結網頁。 */
data class Headline(
    val title: String,
    val source: String,
    val epochSec: Long,
    val url: String = "",
    val content: String = "",
    val contentLevel: String = "title",
    val duplicateCount: Int = 1,
)

object InvestData {
    /** 對話與圖表用的指數代碼（Yahoo）→ 中文名稱 */
    val indexNames: Map<String, String> = linkedMapOf(
        "^TWII" to "加權指數", "^TWOII" to "櫃買指數", "^SOX" to "費城半導體", "^IXIC" to "那斯達克",
        "^DJI" to "道瓊", "^GSPC" to "S&P 500", "^N225" to "日經", "^VIX" to "VIX 恐慌指數",
    )

    /** 問題裡提到的指數（大盤、費半、美股…）。 */
    fun indexesIn(q: String): List<String> = buildList {
        if (listOf("大盤", "加權", "台股", "盤勢", "股市", "指數").any { q.contains(it) }) add("^TWII")
        if (listOf("櫃買", "上櫃", "OTC").any { q.contains(it, true) }) add("^TWOII")
        if (listOf("費半", "費城", "SOX", "半導體").any { q.contains(it, true) }) add("^SOX")
        if (listOf("那斯達克", "那指", "NASDAQ", "美股", "科技股").any { q.contains(it, true) }) add("^IXIC")
        if (listOf("道瓊", "DOW").any { q.contains(it, true) }) add("^DJI")
        if (listOf("標普", "S&P", "SP500").any { q.contains(it, true) }) add("^GSPC")
        if (listOf("日經", "日股").any { q.contains(it) }) add("^N225")
        if (listOf("恐慌", "VIX").any { q.contains(it, true) }) add("^VIX")
    }

    /** 即時行情一行文字：主要指數與美元匯率的最新價與漲跌（Yahoo，盤中即時、收盤後為收盤價）。 */
    suspend fun liveMarketLine(): String = kotlinx.coroutines.coroutineScope {
        val list = listOf("^TWII", "^TWOII", "^SOX", "^IXIC", "^GSPC", "^VIX", "TWD=X")
        list.map { sym ->
            async {
                runCatching {
                    val root = Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(sym, "UTF-8")}?range=1d&interval=1m", quick = true)
                    val meta = (root.obj("chart").obj("result") as? JsonArray)?.firstOrNull().obj("meta")
                    val p = meta.num("regularMarketPrice") ?: return@runCatching null
                    val prev = meta.num("regularMarketPreviousClose") ?: meta.num("chartPreviousClose") ?: meta.num("previousClose")
                    val name = if (sym == "TWD=X") "美元兌台幣" else indexNames[sym] ?: sym
                    "$name ${"%,.2f".format(p)}" + (prev?.takeIf { it > 0 }?.let { " (%+.2f%%)".format((p / it - 1) * 100) } ?: "")
                }.getOrNull()
            }
        }.mapNotNull { it.await() }.let { if (it.isEmpty()) "" else "即時行情（App 剛查到，可直接引用數字）：" + it.joinToString("；") }
    }

    @Volatile
    var lastHeadlineError: String? = null
        private set

    /** Yahoo Finance K 線（預設日線，[interval] 可用 "60m"）；台股依序試 .TW、.TWO。 */
    suspend fun bars(symbol: String, range: String = "1y", tw: Boolean = true, interval: String = "1d"): DailyBars? {
        val candidates = if (symbol.startsWith("^") || !tw) listOf(symbol) else listOf("$symbol.TW", "$symbol.TWO")
        for (ys in candidates) {
            val root = runCatching {
                Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(ys, "UTF-8")}?range=$range&interval=$interval")
            }.getOrNull() ?: continue
            val result = ((root as? JsonObject)?.get("chart") as? JsonObject)?.get("result") as? JsonArray ?: continue
            val r = result.firstOrNull() as? JsonObject ?: continue
            val ts = r["timestamp"] as? JsonArray ?: continue
            val q = ((r["indicators"] as? JsonObject)?.get("quote") as? JsonArray)?.firstOrNull() as? JsonObject ?: continue
            val closes = q["close"] as? JsonArray ?: continue
            val highs = q["high"] as? JsonArray ?: closes
            val opens = q["open"] as? JsonArray ?: closes
            val lows = q["low"] as? JsonArray ?: closes
            val vols = q["volume"] as? JsonArray
            val zone = java.time.ZoneId.of("Asia/Taipei")
            val d = ArrayList<Long>(); val c = ArrayList<Double>(); val h = ArrayList<Double>()
            val o = ArrayList<Double>(); val l = ArrayList<Double>(); val v = ArrayList<Double>()
            ts.forEachIndexed { i, t ->
                val sec = (t as? JsonPrimitive)?.content?.toLongOrNull() ?: return@forEachIndexed
                val cv = num(closes.getOrNull(i)) ?: return@forEachIndexed
                d += java.time.Instant.ofEpochSecond(sec).atZone(zone).toLocalDate().toEpochDay()
                c += cv
                h += num(highs.getOrNull(i)) ?: cv
                o += num(opens.getOrNull(i)) ?: cv
                l += num(lows.getOrNull(i)) ?: cv
                v += num(vols?.getOrNull(i)) ?: 0.0
            }
            if (c.isEmpty()) continue
            val meta = r["meta"] as? JsonObject
            val name = (meta?.get("shortName") as? JsonPrimitive)?.content ?: (meta?.get("longName") as? JsonPrimitive)?.content
            return DailyBars(d, c, h, name, o, l, v)
        }
        return null
    }

    private fun num(e: JsonElement?): Double? = (e as? JsonPrimitive)?.content?.toDoubleOrNull()

    /** Google 新聞繁體中文 RSS：只讀 RSS 直接提供的欄位，不抓取文章頁。 */
    suspend fun headlines(queries: List<String>, perQuery: Int = 12, max: Int = 45): List<Headline> = coroutineScope {
        val failures = AtomicInteger()
        // 一次最多 8 個查詢同時進行，避免被新聞服務限流
        val lists = queries.chunked(8).flatMap { group ->
            group.map { q ->
                async {
                    try {
                        val url = "https://news.google.com/rss/search?q=${URLEncoder.encode("$q when:2d", "UTF-8")}&hl=zh-TW&gl=TW&ceid=TW:zh-Hant"
                        parseRss(Http.getText(url)).take(perQuery)
                    } catch (_: Exception) {
                        failures.incrementAndGet()
                        emptyList()
                    }
                }
            }.awaitAll()
        }
        lastHeadlineError = failures.get().takeIf { it > 0 }?.let { "新聞來源有 $it 個查詢失敗" }
        val unique = LinkedHashMap<String, Headline>()
        lists.flatten().forEach { headline ->
            val key = fingerprint(headline.title, headline.content)
            val previous = unique[key]
            unique[key] = if (previous == null) headline else {
                val best = if (headline.content.length > previous.content.length) headline else previous
                best.copy(
                    duplicateCount = previous.duplicateCount + headline.duplicateCount,
                    epochSec = maxOf(previous.epochSec, headline.epochSec),
                )
            }
        }
        unique.values.sortedByDescending { it.epochSec }.take(max)
    }

    private val itemRe = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private fun tag(name: String) = Regex("<$name\\b[^>]*>(.*?)</$name\\s*>", RegexOption.DOT_MATCHES_ALL)

    fun parseRss(xml: String): List<Headline> = itemRe.findAll(xml).mapNotNull { m ->
        val body = m.groupValues[1]
        val raw = tag("title").find(body)?.groupValues?.get(1)?.let(::plainText)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val source = tag("source").find(body)?.groupValues?.get(1)?.let(::plainText).orEmpty()
        // 標題結尾的「 - 來源」拿掉
        val title = if (source.isNotBlank()) raw.removeSuffix(" - $source").trim() else raw.substringBeforeLast(" - ").trim()
        val sec = tag("pubDate").find(body)?.groupValues?.get(1)?.let(::plainText)?.let {
            runCatching { java.time.ZonedDateTime.parse(it, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() }.getOrNull()
        } ?: 0L
        val encoded = tag("content:encoded").find(body)?.groupValues?.get(1)?.let(::plainText).orEmpty()
        val summary = tag("description").find(body)?.groupValues?.get(1)?.let(::plainText).orEmpty()
        val content = encoded.ifBlank { summary }
        val level = when {
            encoded.isNotBlank() -> "正文"
            summary.isNotBlank() -> "摘要"
            else -> "title"
        }
        Headline(
            title = title,
            source = source,
            epochSec = sec,
            url = tag("link").find(body)?.groupValues?.get(1)?.let(::plainText).orEmpty(),
            content = content,
            contentLevel = level,
        )
    }.sortedByDescending { it.epochSec }.toList()

    private fun plainText(raw: String): String {
        val cdata = raw.replace("<![CDATA[", "").replace("]]>", "")
        val html = android.text.Html.fromHtml(unescape(cdata), android.text.Html.FROM_HTML_MODE_LEGACY).toString()
        return html.replace(Regex("\\s+"), " ").trim()
    }

    private fun unescape(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'").replace("&#39;", "'").replace("&nbsp;", " ")
        .replace(Regex("&#(x[0-9a-fA-F]+|[0-9]+);")) { match ->
            val code = match.groupValues[1].removePrefix("x").let {
                it.toIntOrNull(if (match.groupValues[1].startsWith("x")) 16 else 10)
            }
            code?.let { Character.toString(it) } ?: match.value
        }

    private fun fingerprint(title: String, content: String): String {
        val normalized = (content.ifBlank { title }).lowercase().replace(Regex("\\s+"), " ").trim()
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
