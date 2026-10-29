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
