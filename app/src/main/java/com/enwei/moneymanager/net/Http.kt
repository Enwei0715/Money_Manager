package com.enwei.moneymanager.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true; isLenient = true }

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"

    /** 報價用：整個請求最多 6 秒，來源卡住時很快改用備援來源。 */
    val fast: OkHttpClient by lazy {
        client.newBuilder().callTimeout(6, TimeUnit.SECONDS).connectTimeout(4, TimeUnit.SECONDS).cookieJar(MemoryCookies).build()
    }

    /** 證交所即時報價需要先取得網站的 session cookie；只存在記憶體。 */
    private object MemoryCookies : okhttp3.CookieJar {
        private val store = java.util.concurrent.ConcurrentHashMap<String, List<okhttp3.Cookie>>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            store[url.host] = (store[url.host].orEmpty().filter { old -> cookies.none { it.name == old.name } } + cookies)
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
            store[url.host].orEmpty().filter { it.expiresAt > System.currentTimeMillis() }
    }

    /** 只取網頁（為了拿 cookie），內容丟掉。 */
    suspend fun touch(url: String) = withContext(Dispatchers.IO) {
        runCatching { fast.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().close() }
    }

    suspend fun getJson(url: String, headers: Map<String, String> = emptyMap(), quick: Boolean = false): JsonElement = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        (if (quick) fast else client).newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $url")
            json.parseToJsonElement(resp.body!!.string())
        }
    }

    suspend fun getText(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}: $url")
            resp.body!!.string()
        }
    }
}

fun JsonElement?.obj(key: String): JsonElement? = (this as? JsonObject)?.get(key)
fun JsonElement?.str(key: String): String? = (obj(key) as? JsonPrimitive)?.contentOrNull
fun JsonElement?.num(key: String): Double? = (obj(key) as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
