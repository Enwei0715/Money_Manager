package com.enwei.moneymanager.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.Request
import java.util.Collections

/**
 * 讀取新聞網頁的正文（給股票助理對話用，只在相關新聞不多時才抓幾篇）。
 * Google 新聞的 RSS 連結會先換成原始新聞網址，再取出網頁裡的段落文字。
 * 結果在 App 執行期間快取（最多 120 篇），抓不到時回傳 null，對話改用標題與摘要。
 */
object Article {
    private val cache: MutableMap<String, String> = Collections.synchronizedMap(object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) = size > 120
    })

    /** 抓 [url] 的正文，最多 [max] 字；整個過程最多 [timeoutMs] 毫秒。 */
    suspend fun text(url: String, max: Int = 2000, timeoutMs: Long = 10_000): String? {
        if (!url.startsWith("http")) return null
        cache[url]?.let { return it.take(max).ifBlank { null } }
        val body = withTimeoutOrNull(timeoutMs) {
            runCatching { withContext(Dispatchers.IO) { fetch(url) } }.getOrNull()
        }
        cache[url] = body.orEmpty()
        return body?.take(max)?.ifBlank { null }
    }

    private fun get(url: String): Pair<String, String> {
        val req = Request.Builder().url(url).header("User-Agent", Http.USER_AGENT)
            .header("Accept-Language", "zh-TW,zh;q=0.9").build()
        Http.client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw java.io.IOException("HTTP ${resp.code}")
            return resp.request.url.toString() to resp.body!!.string()
        }
    }

    private fun fetch(url: String): String? {
        var target = url
        if (url.contains("news.google.com/")) target = decodeGoogle(url) ?: return null
        val (_, html) = get(target)
        return extract(html)
    }

    /** Google 新聞文章連結 → 原始網址：舊格式的編碼直接含網址；新格式需向 Google 新聞換取。 */
    private fun decodeGoogle(url: String): String? {
        val id = Regex("/articles/([A-Za-z0-9_-]+)").find(url)?.groupValues?.get(1) ?: return null
        runCatching {
            val raw = String(android.util.Base64.decode(id, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP), Charsets.ISO_8859_1)
            Regex("https?://[\\x21-\\x7e]+").find(raw)?.value?.takeIf { !it.contains("news.google.com") && it.length > 12 }?.let { return it }
        }
        val (_, page) = get("https://news.google.com/rss/articles/$id")
        val sg = Regex("data-n-a-sg=\"([^\"]+)\"").find(page)?.groupValues?.get(1) ?: return null
        val ts = Regex("data-n-a-ts=\"([^\"]+)\"").find(page)?.groupValues?.get(1) ?: return null
        val inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"$id\",$ts,\"$sg\"]"
        val freq = "[[[\"Fbv4je\",${kotlinx.serialization.json.JsonPrimitive(inner)},null,\"generic\"]]]"
        val req = Request.Builder().url("https://news.google.com/_/DotsSplashUi/data/batchexecute")
            .header("User-Agent", Http.USER_AGENT)
            .post(FormBody.Builder().add("f.req", freq).build()).build()
        val text = Http.client.newCall(req).execute().use { it.body?.string().orEmpty() }
        return Regex("garturlres\\\\\",\\\\\"(https?://[^\\\\\"]+)").find(text)?.groupValues?.get(1)
    }

    private val drop = Regex("(?is)<(script|style|noscript|nav|header|footer|aside|form|figure)\\b.*?</\\1\\s*>")
    private val para = Regex("(?is)<p\\b[^>]*>(.*?)</p\\s*>")
    private val tagRe = Regex("(?s)<[^>]+>")
    private val junk = listOf("版權", "著作權", "訂閱", "加入會員", "下載APP", "延伸閱讀", "相關新聞", "看更多", "請繼續往下閱讀", "免責", "記者／", "Copyright", "All rights reserved", "Facebook", "LINE")

    /** 從網頁取出正文：優先 article 區塊內的段落，留下 15 字以上、不像版權宣告或導覽的段落。 */
    fun extract(html: String): String? {
        val clean = html.replace(drop, " ")
        val scope = Regex("(?is)<article\\b.*?</article\\s*>").findAll(clean).maxByOrNull { it.value.length }?.value ?: clean
        fun paras(src: String) = para.findAll(src).map { unescape(it.groupValues[1].replace(tagRe, "")).trim() }
            .filter { p -> p.length >= 15 && junk.none { p.contains(it) } }.distinct().toList()
        val list = paras(scope).ifEmpty { paras(clean) }
        val text = list.joinToString("\n")
        if (text.length >= 80) return text
        // 沒有段落時用網頁描述
        return Regex("(?is)<meta[^>]+(?:property|name)=\"(?:og:description|description)\"[^>]+content=\"([^\"]+)\"").find(html)
            ?.groupValues?.get(1)?.let(::unescape)?.takeIf { it.length >= 30 }
    }

    private fun unescape(s: String) = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .replace(Regex("\\s+"), " ")
}
