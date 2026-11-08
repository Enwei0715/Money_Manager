package com.enwei.moneymanager.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import kotlin.coroutines.resume

/**
 * 依位置推測店家：讀到付款通知時記下手機當下的位置，之後在同一個地點（80 公尺內）付款、
 * 通知又沒寫店家時，用過去在這裡記過的店家與分類（含使用者改過的）來補；沒有紀錄時查 OpenStreetMap 附近的店。
 * 位置只存在手機裡（不放進備份）。
 */
object PlaceHints {
    /** 一筆交易記帳當下的位置（店家與分類從交易本身讀，使用者改過也會跟著變）。 */
    @Serializable
    data class Sample(val txnId: Long, val lat: Double, val lon: Double, val at: Long)

    data class Guess(val merchant: String, val categoryId: Long?, val fromHistory: Boolean, val kind: String? = null)

    /** 附近的店：名稱與 OpenStreetMap 類型（restaurant、convenience…，推測分類用）。 */
    data class Place(val name: String, val kind: String?)

    private const val RADIUS_M = 80f
    private const val MAX_SAMPLES = 3000
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "place_samples.json")

    @Volatile private var cache: List<Sample>? = null

    /** 上一次讀通知時推測的結果或沒推測的原因（地點測試顯示用）。 */
    private val prefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("place_hints", Context.MODE_PRIVATE) }
    /** 上一次讀通知時推測的結果或沒推測的原因（存起來，App 重開後地點測試仍看得到）。 */
    val lastResult: String get() = runCatching { prefs.getString("last", "") }.getOrNull().orEmpty()
    fun note(msg: String) {
        val line = java.text.SimpleDateFormat("M/d HH:mm", java.util.Locale.TAIWAN).format(java.util.Date()) + "　" + msg
        runCatching { prefs.edit().putString("last", line).apply() }
    }

    /** 已記下的位置筆數。 */
    fun sampleCount(ctx: Context): Int = load(ctx).size

    private fun load(ctx: Context): List<Sample> = cache ?: runCatching {
        json.decodeFromString(ListSerializer(Sample.serializer()), file(ctx).readText())
    }.getOrDefault(emptyList()).also { cache = it }

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 背景（App 沒開著時讀到通知）也能取得位置。 */
    fun hasBackground(ctx: Context): Boolean = android.os.Build.VERSION.SDK_INT < 29 ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * 手機目前的位置：15 分鐘內的最後位置先用；太舊就依序向 fused、網路、GPS 要一次新的定位（共約 12 秒）；
     * 都拿不到時退回 45 分鐘內的最後位置。
     */
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val providers = listOf("fused", LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.allProviders.contains(it) && (it == LocationManager.PASSIVE_PROVIDER || lm.isProviderEnabled(it)) }.getOrDefault(false) }
        val known = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }.filter { it.accuracy <= 300f }
        fun best(maxAge: Long) = known.filter { now - it.time <= maxAge }.minByOrNull { it.accuracy + (now - it.time) / 10_000f }
        best(15 * 60_000L)?.let { return it }
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            for ((i, provider) in providers.filter { it != LocationManager.PASSIVE_PROVIDER }.withIndex()) {
                val loc = kotlinx.coroutines.withTimeoutOrNull(if (i == 0) 6_000L else 4_000L) {
                    kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                        val cancel = android.os.CancellationSignal()
                        cont.invokeOnCancellation { cancel.cancel() }
                        runCatching {
                            lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { l -> if (cont.isActive) cont.resume(l) }
                        }.onFailure { if (cont.isActive) cont.resume(null) }
                    }
                }
                if (loc != null && loc.accuracy <= 300f) return loc
            }
        }
        return best(45 * 60_000L)
    }

    /** 記下這筆交易的位置。 */
    suspend fun record(ctx: Context, txnId: Long, loc: Location) = withContext(Dispatchers.IO) {
        synchronized(this@PlaceHints) {
            val list = (load(ctx).filter { it.txnId != txnId } + Sample(txnId, loc.latitude, loc.longitude, System.currentTimeMillis())).takeLast(MAX_SAMPLES)
            cache = list
            runCatching { file(ctx).writeText(json.encodeToString(ListSerializer(Sample.serializer()), list)) }
        }
    }

    private fun dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(aLat, aLon, bLat, bLon, r)
        return r[0]
    }

    /** 過去在這附近記過的店家：次數最多的（同樣多時取最近的）。 */
    fun fromHistory(ctx: Context, loc: Location, txns: List<TxnEntity>): Guess? {
        val byId = txns.associateBy { it.id }
        val near = load(ctx).mapNotNull { s ->
            val t = byId[s.txnId] ?: return@mapNotNull null
            val m = t.merchant?.trim()?.takeIf { it.length >= 2 && !com.enwei.moneymanager.core.CategoryGuesser.isGenericMerchant(it) } ?: return@mapNotNull null
            val d = dist(loc.latitude, loc.longitude, s.lat, s.lon)
            if (d > RADIUS_M + loc.accuracy.coerceAtMost(120f)) null else Triple(t, m, d)
        }
        if (near.isEmpty()) return null
        val best = near.groupBy { it.second }.maxWithOrNull(compareBy<Map.Entry<String, List<Triple<TxnEntity, String, Float>>>> { it.value.size }
            .thenByDescending { e -> e.value.minOf { it.third } }) ?: return null
        val cat = best.value.mapNotNull { it.first.categoryId }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        return Guess(best.key, cat, true)
    }

    private const val UA = "Ledgerly/2.1 (Android personal finance app)"

    /**
     * 沒有紀錄時查附近的店：先用 Nominatim 反查這個座標上的店家（快），查不到再用 Overpass 找 50 公尺內最近的
     * 店家、餐廳（較慢，最多等 15 秒）。查不到回傳 null。
     */
    suspend fun nearbyPlace(loc: Location): Place? = withContext(Dispatchers.IO) {
        reverse(loc) ?: overpass(loc)
    }

    private val spendKeys = setOf("shop", "amenity", "tourism", "leisure", "craft")
    private val notSpend = setOf("parking", "parking_space", "atm", "toilets", "bench", "bicycle_parking", "waste_basket", "post_box",
        "bank", "school", "university", "place_of_worship", "police", "townhall", "shelter", "drinking_water", "vending_machine", "motorcycle_parking")

    private suspend fun reverse(loc: Location): Place? = runCatching {
        val url = "https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=18&namedetails=1&accept-language=zh-TW&lat=${loc.latitude}&lon=${loc.longitude}"
        val o = com.enwei.moneymanager.net.Http.getJson(url, mapOf("User-Agent" to UA)) as? kotlinx.serialization.json.JsonObject ?: return@runCatching null
        fun str(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
        val cat = str("category") ?: return@runCatching null
        val type = str("type")
        if (cat !in spendKeys || type in notSpend) return@runCatching null
        val name = str("name")?.takeIf { it.isNotBlank() } ?: return@runCatching null
        val lat = str("lat")?.toDoubleOrNull() ?: return@runCatching null
        val lon = str("lon")?.toDoubleOrNull() ?: return@runCatching null
        if (dist(loc.latitude, loc.longitude, lat, lon) > 60f + loc.accuracy.coerceAtMost(60f)) null else Place(name, type)
    }.getOrNull()

    private suspend fun overpass(loc: Location): Place? = runCatching {
        val r = 50
        val q = "[out:json][timeout:12];(" +
            "node(around:$r,${loc.latitude},${loc.longitude})[name][shop];" +
            "node(around:$r,${loc.latitude},${loc.longitude})[name][amenity];" +
            "way(around:$r,${loc.latitude},${loc.longitude})[name][shop];" +
            "way(around:$r,${loc.latitude},${loc.longitude})[name][amenity];" +
            ");out center 30;"
        val body = com.enwei.moneymanager.net.Http.getJson("https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(q, "UTF-8"), mapOf("User-Agent" to UA))
        val els = (body as? kotlinx.serialization.json.JsonObject)?.get("elements") as? kotlinx.serialization.json.JsonArray ?: return@runCatching null
        els.mapNotNull { e ->
            val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun num(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                ?: ((o["center"] as? kotlinx.serialization.json.JsonObject)?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
            val tags = o["tags"] as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            fun tag(k: String) = (tags[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
            val name = tag("name:zh") ?: tag("name") ?: return@mapNotNull null
            val kind = tag("amenity") ?: tag("shop")
            if (kind in notSpend) return@mapNotNull null
            val lat = num("lat") ?: return@mapNotNull null
            val lon = num("lon") ?: return@mapNotNull null
            Triple(name, kind, dist(loc.latitude, loc.longitude, lat, lon))
        }.minByOrNull { it.third }?.takeIf { it.third <= r + loc.accuracy.coerceAtMost(40f) }?.let { Place(it.first, it.second) }
    }.getOrNull()

    /** OpenStreetMap 類型 → 分類名稱關鍵字（在使用者的分類裡找名稱含這些字的）。 */
    fun categoryWords(kind: String?): List<String> = when (kind) {
        "restaurant", "fast_food", "cafe", "food_court", "bakery", "ice_cream", "bar", "pub", "biergarten", "deli", "confectionery", "beverages", "tea", "coffee" ->
            listOf("飲食", "餐", "食")
        "convenience", "supermarket", "department_store", "mall", "variety_store", "general" -> listOf("日用", "購物", "超市", "生活")
        "clothes", "shoes", "electronics", "mobile_phone", "books", "gift", "sports", "cosmetics", "jewelry", "furniture", "toys", "stationery" -> listOf("購物", "服飾", "3C")
        "fuel", "charging_station", "car_wash", "car_repair", "bicycle_rental" -> listOf("交通", "加油", "汽機車")
        "pharmacy", "chemist", "clinic", "doctors", "dentist", "hospital", "optician" -> listOf("醫療", "藥", "保健")
        "cinema", "theatre", "karaoke", "amusement_arcade", "fitness_centre", "sports_centre" -> listOf("娛樂", "休閒", "運動")
        "hairdresser", "beauty" -> listOf("美容", "美髮", "個人")
        else -> emptyList()
    }
}
