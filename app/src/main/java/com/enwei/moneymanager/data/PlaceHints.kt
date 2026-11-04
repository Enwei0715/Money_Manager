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

    data class Guess(val merchant: String, val categoryId: Long?, val fromHistory: Boolean)

    private const val RADIUS_M = 80f
    private const val MAX_SAMPLES = 3000
    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private fun file(ctx: Context) = File(ctx.filesDir, "place_samples.json")

    @Volatile private var cache: List<Sample>? = null

    private fun load(ctx: Context): List<Sample> = cache ?: runCatching {
        json.decodeFromString(ListSerializer(Sample.serializer()), file(ctx).readText())
    }.getOrDefault(emptyList()).also { cache = it }

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 背景（App 沒開著時讀到通知）也能取得位置。 */
    fun hasBackground(ctx: Context): Boolean = android.os.Build.VERSION.SDK_INT < 29 ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** 手機目前的位置：先用 15 分鐘內的最後位置，太舊就等一次新的定位（最多 6 秒）。 */
    suspend fun current(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val providers = listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) || it == LocationManager.PASSIVE_PROVIDER }
        val last = providers.mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .filter { now - it.time <= 15 * 60_000L && it.accuracy <= 300f }
            .minByOrNull { it.accuracy + (now - it.time) / 10_000f }
        if (last != null) return last
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        val provider = providers.firstOrNull { it != LocationManager.PASSIVE_PROVIDER } ?: return null
        return kotlinx.coroutines.withTimeoutOrNull(6_000) {
            kotlinx.coroutines.suspendCancellableCoroutine<Location?> { cont ->
                val cancel = android.os.CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, cancel, ctx.mainExecutor) { loc -> if (cont.isActive) cont.resume(loc) }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
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

    /** 沒有紀錄時：OpenStreetMap 上 40 公尺內最近的店家或餐廳（查不到回傳 null）。 */
    suspend fun nearbyPlace(loc: Location): String? = withContext(Dispatchers.IO) {
        val q = "[out:json][timeout:5];(node(around:40,${loc.latitude},${loc.longitude})[name][~\"^(shop|amenity)$\"~\".\"];" +
            "way(around:40,${loc.latitude},${loc.longitude})[name][~\"^(shop|amenity)$\"~\".\"];);out center 20;"
        runCatching {
            val root = com.enwei.moneymanager.net.Http.getJson("https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(q, "UTF-8"), quick = true)
            val els = (root as? kotlinx.serialization.json.JsonObject)?.get("elements") as? kotlinx.serialization.json.JsonArray ?: return@runCatching null
            els.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun num(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                    ?: ((o["center"] as? kotlinx.serialization.json.JsonObject)?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                val tags = o["tags"] as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val name = ((tags["name:zh"] ?: tags["name"]) as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@mapNotNull null
                // 停車場、ATM、廁所這類不是消費的地點不算
                val kind = (tags["amenity"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                if (kind in setOf("parking", "atm", "toilets", "bench", "bicycle_parking", "waste_basket", "post_box", "bank")) return@mapNotNull null
                val lat = num("lat") ?: return@mapNotNull null
                val lon = num("lon") ?: return@mapNotNull null
                name to dist(loc.latitude, loc.longitude, lat, lon)
            }.minByOrNull { it.second }?.takeIf { it.second <= 40f }?.first
        }.getOrNull()
    }
}
