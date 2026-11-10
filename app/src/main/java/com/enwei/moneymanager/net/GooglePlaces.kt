package com.enwei.moneymanager.net

import android.content.Context
import com.enwei.moneymanager.MoneyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Google Places API（New）：用使用者自己的金鑰搜尋店家（台灣小店資料最完整）。
 * 金鑰只存在這支手機（獨立的設定檔，不進備份）。只要名稱、地址、座標三個欄位，查過的結果記 30 分鐘，避免多餘的呼叫。
 */
object GooglePlaces {
    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("gplaces", Context.MODE_PRIVATE) }

    var key: String
        get() = prefs.getString("key", "").orEmpty()
        set(v) { prefs.edit().putString("key", v.trim()).apply() }

    val enabled: Boolean get() = key.startsWith("AIza")

    @Volatile var lastError: String = ""
    @Volatile var lastOk: Boolean = false

    private const val FIELDS = "places.displayName,places.formattedAddress,places.location"
    private val cache = object : android.util.LruCache<String, Pair<Long, List<Mapbox.Place>>>(200) {}
    private fun cached(k: String) = cache.get(k)?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000L }?.second

    private fun dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float =
        FloatArray(1).also { android.location.Location.distanceBetween(aLat, aLon, bLat, bLon, it) }[0]

    /** 用量：本月與今天打了幾次（每天最多 [DAILY_CAP] 次，超過就先不查，避免意外大量呼叫） */
    const val DAILY_CAP = 150
    private fun dayKey() = "d:" + java.time.LocalDate.now()
    private fun monthKey() = "m:" + java.time.YearMonth.now()
    val usedThisMonth: Int get() = prefs.getInt(monthKey(), 0)
    val usedToday: Int get() = prefs.getInt(dayKey(), 0)
    private fun count() { prefs.edit().putInt(dayKey(), usedToday + 1).putInt(monthKey(), usedThisMonth + 1).apply() }

    private suspend fun post(path: String, body: JsonObject, near: Pair<Double, Double>?): List<Mapbox.Place>? = withContext(Dispatchers.IO) {
        if (usedToday >= DAILY_CAP) { lastError = "今天已查 $DAILY_CAP 次，明天再查"; lastOk = false; return@withContext null }
        count()
        runCatching {
            val req = okhttp3.Request.Builder().url("https://places.googleapis.com/v1/$path")
                .header("X-Goog-Api-Key", key).header("X-Goog-FieldMask", FIELDS)
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            Http.fast.newBuilder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).execute().use { r ->
                val text = r.body?.string().orEmpty()
                if (!r.isSuccessful) {
                    val msg = runCatching {
                        ((Http.json.parseToJsonElement(text) as JsonObject)["error"] as JsonObject)["message"].let { (it as JsonPrimitive).content }
                    }.getOrNull()
                    error("HTTP ${r.code}" + (msg?.let { "：$it" } ?: ""))
                }
                val places = (Http.json.parseToJsonElement(text) as? JsonObject)?.get("places") as? JsonArray ?: return@use emptyList()
                places.mapNotNull { e ->
                    val o = e as? JsonObject ?: return@mapNotNull null
                    val name = ((o["displayName"] as? JsonObject)?.get("text") as? JsonPrimitive)?.content ?: return@mapNotNull null
                    val loc = o["location"] as? JsonObject ?: return@mapNotNull null
                    val lat = (loc["latitude"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val lon = (loc["longitude"] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                    val addr = (o["formattedAddress"] as? JsonPrimitive)?.content.orEmpty().replace(Regex("^\\d{3,6}"), "").removePrefix("台灣")
                    Mapbox.Place(name, addr, lat, lon, near?.let { dist(it.first, it.second, lat, lon) })
                }
            }
        }.onSuccess { lastOk = true; lastError = "" }
            .onFailure { lastOk = false; lastError = it.message ?: it.javaClass.simpleName }
            .getOrNull()
    }

    /** 用名稱找店（偏向 [near] 附近 5 公里），由近到遠 */
    suspend fun search(q: String, near: Pair<Double, Double>?, limit: Int = 8): List<Mapbox.Place> {
        if (!enabled || q.isBlank()) return emptyList()
        val k = "s|${q.trim().lowercase()}|${near?.let { "%.3f,%.3f".format(it.first, it.second) }}|$limit"
        cached(k)?.let { return it }
        val body = buildJsonObject {
            put("textQuery", q.trim()); put("languageCode", "zh-TW"); put("regionCode", "TW"); put("pageSize", limit)
            if (near != null) putJsonObject("locationBias") {
                putJsonObject("circle") {
                    putJsonObject("center") { put("latitude", near.first); put("longitude", near.second) }
                    put("radius", 5000.0)
                }
            }
        }
        val r = post("places:searchText", body, near) ?: return emptyList()
        return r.sortedBy { it.meters ?: 0f }.also { cache.put(k, System.currentTimeMillis() to it) }
    }

    /** 這個位置附近（[radius] 公尺內）的店，由近到遠 */
    suspend fun nearby(lat: Double, lon: Double, radius: Double = 120.0, limit: Int = 10): List<Mapbox.Place> {
        if (!enabled) return emptyList()
        val k = "n|%.4f,%.4f|$limit".format(lat, lon)
        cached(k)?.let { return it }
        val body = buildJsonObject {
            put("languageCode", "zh-TW"); put("regionCode", "TW"); put("maxResultCount", limit); put("rankPreference", "DISTANCE")
            putJsonObject("locationRestriction") {
                putJsonObject("circle") {
                    putJsonObject("center") { put("latitude", lat); put("longitude", lon) }
                    put("radius", radius)
                }
            }
        }
        val r = post("places:searchNearby", body, lat to lon) ?: return emptyList()
        return r.sortedBy { it.meters ?: 0f }.also { cache.put(k, System.currentTimeMillis() to it) }
    }

    /** 測試金鑰：成功回傳 null，失敗回傳原因 */
    suspend fun test(): String? {
        if (!enabled) return "金鑰應該是 AIza 開頭"
        cache.evictAll()
        search("台北車站", 25.0478 to 121.5170, 1)
        return if (lastOk) null else lastError.ifBlank { "連不上 Google" }
    }
}

/**
 * 店家搜尋：有 Google Places 金鑰用 Google，否則有 Mapbox 金鑰用 Mapbox，都沒有用 OpenStreetMap。
 */
object PlaceSearch {
    val source: String get() = when {
        GooglePlaces.enabled -> "Google"
        Mapbox.enabled -> "Mapbox"
        else -> "OpenStreetMap"
    }
    val online: Boolean get() = GooglePlaces.enabled || Mapbox.enabled
    val lastError: String get() = if (GooglePlaces.enabled) GooglePlaces.lastError else Mapbox.lastError

    suspend fun search(q: String, near: Pair<Double, Double>?, limit: Int = 8): List<Mapbox.Place> = when {
        GooglePlaces.enabled -> GooglePlaces.search(q, near, limit)
        Mapbox.enabled -> Mapbox.search(q, near, limit)
        // 沒有金鑰：用 OpenStreetMap（Overpass）在附近 3 公里內依名稱找，免費但資料較少、回應較慢
        else -> if (near != null && q.trim().length >= 2) com.enwei.moneymanager.data.PlaceHints.overpassSearch(q.trim(), near.first, near.second, 3000).take(limit) else emptyList()
    }

    suspend fun nearby(lat: Double, lon: Double): List<Mapbox.Place> = when {
        GooglePlaces.enabled -> GooglePlaces.nearby(lat, lon)
        Mapbox.enabled -> Mapbox.nearby(lat, lon)
        else -> emptyList()
    }
}
