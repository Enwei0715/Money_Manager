package com.enwei.moneymanager.net

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/** 日 K 線（收盤、最高）與日期（epochDay），由舊到新。 */
data class DailyBars(
    val days: List<Long>, val close: List<Double>, val high: List<Double>, val name: String?,
    val open: List<Double> = close, val low: List<Double> = close, val volume: List<Double> = emptyList(),
) {
    /** [fromDay] 之後（含）的最高價 */
    fun peakSince(fromDay: Long): Double? = days.indices.filter { days[it] >= fromDay }.maxOfOrNull { high[it] }
}

/** 一則新聞標題。 */
data class Headline(val title: String, val source: String, val epochSec: Long, val url: String = "")

object InvestData {
    /** Yahoo Finance 日線；台股依序試 .TW、.TWO。 */
    suspend fun bars(symbol: String, range: String = "1y", tw: Boolean = true): DailyBars? {
        val candidates = if (symbol.startsWith("^") || !tw) listOf(symbol) else listOf("$symbol.TW", "$symbol.TWO")
        for (ys in candidates) {
            val root = runCatching {
                Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(ys, "UTF-8")}?range=$range&interval=1d")
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

    /**
     * Google 新聞（繁體中文）RSS 的標題：近兩天、依查詢字串分別抓，去除重複。
     * 只取標題與來源，不讀內文。
     */
    suspend fun headlines(queries: List<String>, perQuery: Int = 12, max: Int = 45): List<Headline> = coroutineScope {
        // 一次最多 8 個查詢同時進行，避免被新聞服務限流
        val lists = queries.chunked(8).flatMap { group ->
            group.map { q ->
                async {
                    runCatching {
                        val url = "https://news.google.com/rss/search?q=${URLEncoder.encode("$q when:2d", "UTF-8")}&hl=zh-TW&gl=TW&ceid=TW:zh-Hant"
                        parseRss(Http.getText(url)).take(perQuery)
                    }.getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        val seen = HashSet<String>()
        // 各查詢輪流取，避免某一類新聞占滿
        val out = ArrayList<Headline>()
        var i = 0
        while (out.size < max && lists.any { it.size > i }) {
            lists.forEach { l -> l.getOrNull(i)?.let { h -> if (out.size < max && seen.add(h.title.replace(" ", "").take(30))) out += h } }
            i++
        }
        out
    }

    private val itemRe = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
    private val titleRe = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
    private val sourceRe = Regex("<source[^>]*>(.*?)</source>", RegexOption.DOT_MATCHES_ALL)
    private val dateRe = Regex("<pubDate>(.*?)</pubDate>")
    private val linkRe = Regex("<link>(.*?)</link>", RegexOption.DOT_MATCHES_ALL)

    fun parseRss(xml: String): List<Headline> = itemRe.findAll(xml).mapNotNull { m ->
        val body = m.groupValues[1]
        val raw = titleRe.find(body)?.groupValues?.get(1)?.let(::unescape) ?: return@mapNotNull null
        val source = sourceRe.find(body)?.groupValues?.get(1)?.let(::unescape).orEmpty()
        // 標題結尾的「 - 來源」拿掉
        val title = if (source.isNotBlank()) raw.removeSuffix(" - $source").trim() else raw.substringBeforeLast(" - ").trim()
        val sec = dateRe.find(body)?.groupValues?.get(1)?.let {
            runCatching { java.time.ZonedDateTime.parse(it, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond() }.getOrNull()
        } ?: 0L
        Headline(title, source, sec, linkRe.find(body)?.groupValues?.get(1)?.let(::unescape).orEmpty())
    }.sortedByDescending { it.epochSec }.toList()

    private fun unescape(s: String) = s.replace("<![CDATA[", "").replace("]]>", "")
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").trim()
}
