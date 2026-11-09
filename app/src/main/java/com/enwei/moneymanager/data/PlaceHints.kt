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

    /** 所有記下的付款位置（支出地圖用）。 */
    fun samples(ctx: Context): List<Sample> = load(ctx)

    /** 用店名找到的位置（店名 → 緯度、經度；查不到存 null，之後不再查）。 */
    private val geoPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("merchant_geo2", Context.MODE_PRIVATE) }
    fun merchantGeo(): Map<String, Pair<Double, Double>?> = geoPrefs.all.mapNotNull { (k, v) ->
        val s = v as? String ?: return@mapNotNull null
        k to s.split(',').takeIf { it.size == 2 }?.let { (a, b) -> a.toDoubleOrNull()?.let { la -> b.toDoubleOrNull()?.let { lo -> la to lo } } }
    }.toMap()

    fun setMerchantGeo(name: String, ll: Pair<Double, Double>?) = geoPrefs.edit().putString(name, ll?.let { "${it.first},${it.second}" } ?: "").apply()
    fun clearMerchantGeo(name: String) = geoPrefs.edit().remove(name).apply()
    /** 只留目前交易裡還有的店名 */
    fun pruneMerchantGeo(keep: Set<String>) {
        val e = geoPrefs.edit(); geoPrefs.all.keys.filter { it !in keep }.forEach { e.remove(it) }; e.apply()
        val f = pinPrefs.edit(); pinPrefs.all.keys.filter { it !in keep }.forEach { f.remove(it) }; f.apply()
    }

    /** 使用者手動指定的店家位置（店名 → 座標）：優先於自動找到的與記帳時的定位 */
    private val pinPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("merchant_pin", Context.MODE_PRIVATE) }
    fun merchantPins(): Map<String, Pair<Double, Double>> = pinPrefs.all.mapNotNull { (k, v) ->
        val p = (v as? String)?.split(',')?.takeIf { it.size == 2 } ?: return@mapNotNull null
        val la = p[0].toDoubleOrNull() ?: return@mapNotNull null
        val lo = p[1].toDoubleOrNull() ?: return@mapNotNull null
        k to (la to lo)
    }.toMap()
    fun setMerchantPin(name: String, lat: Double, lon: Double) = pinPrefs.edit().putString(name, "$lat,$lon").apply()
    fun clearMerchantPin(name: String) = pinPrefs.edit().remove(name).apply()

    /** 使用者在地圖上手動修正的位置：key＝「店名|原本座標」，只改那一個點 */
    private val fixPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("spot_fix", Context.MODE_PRIVATE) }
    fun spotFixes(): Map<String, Pair<Double, Double>> = fixPrefs.all.mapNotNull { (k, v) ->
        val p = (v as? String)?.split(',')?.takeIf { it.size == 2 } ?: return@mapNotNull null
        val la = p[0].toDoubleOrNull() ?: return@mapNotNull null
        val lo = p[1].toDoubleOrNull() ?: return@mapNotNull null
        k to (la to lo)
    }.toMap()
    fun setSpotFix(key: String, lat: Double, lon: Double) = fixPrefs.edit().putString(key, "$lat,$lon").apply()
    fun clearSpotFix(key: String) = fixPrefs.edit().remove(key).apply()

    /**
     * 常活動的區域：把記過的付款位置以約 1 公里的格子分群，取次數最多的幾群的中心（例如學校、住家附近）。
     * 用店名找位置時只在這些區域附近找，不會跑到十幾公里外的同名店。
     */
    fun anchors(ctx: Context, max: Int = 4): List<Pair<Double, Double>> =
        load(ctx).groupBy { (Math.round(it.lat * 100)) to (Math.round(it.lon * 100)) }.values
            .sortedByDescending { it.size }.take(max)
            .map { g -> g.map { it.lat }.average() to g.map { it.lon }.average() }

    /**
     * 用店名找位置：只在常活動區域（[anchors]）附近 2 公里內找；先找 OpenStreetMap 名稱相符的店，
     * 再用 Photon 模糊搜尋；多個結果取離常活動區域最近的。找不到就記下來不再重查。回傳這次找到幾家。
     */
    suspend fun geocodeMerchants(names: List<String>, anchors: List<Pair<Double, Double>>, onProgress: (Int, Int) -> Unit = { _, _ -> }): Int = withContext(Dispatchers.IO) {
        var found = 0
        names.forEachIndexed { i, n ->
            onProgress(i, names.size)
            if (geoPrefs.contains(n)) return@forEachIndexed
            val hits = anchors.flatMap { (la, lo) ->
                listOfNotNull(snapToStore(n, la, lo, 2000), photonNear(n, la, lo, 2000)).map { h -> h to dist(la, lo, h.first, h.second) }
            }
            val hit = hits.minByOrNull { it.second }?.first
            geoPrefs.edit().putString(n, hit?.let { "${it.first},${it.second}" } ?: "").apply()
            if (hit != null) found++
            kotlinx.coroutines.delay(1100)
        }
        onProgress(names.size, names.size)
        found
    }

    /** 附近的店（OpenStreetMap，[radius] 公尺內，由近到遠）：記帳時推薦店名用。 */
    suspend fun nearbyStores(loc: Location, radius: Int = 150): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val q = "[out:json][timeout:10];(" +
                "node(around:$radius,${loc.latitude},${loc.longitude})[name][shop];" +
                "node(around:$radius,${loc.latitude},${loc.longitude})[name][amenity];" +
                "way(around:$radius,${loc.latitude},${loc.longitude})[name][shop];" +
                "way(around:$radius,${loc.latitude},${loc.longitude})[name][amenity];" +
                ");out center 40;"
            val body = com.enwei.moneymanager.net.Http.getJson("https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(q, "UTF-8"), mapOf("User-Agent" to UA))
            val els = (body as? kotlinx.serialization.json.JsonObject)?.get("elements") as? kotlinx.serialization.json.JsonArray ?: return@runCatching emptyList()
            els.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun num(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                    ?: ((o["center"] as? kotlinx.serialization.json.JsonObject)?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                val tags = o["tags"] as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun tag(k: String) = (tags[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val name = tag("name:zh") ?: tag("name") ?: return@mapNotNull null
                if ((tag("amenity") ?: tag("shop")) in notSpend) return@mapNotNull null
                val la = num("lat") ?: return@mapNotNull null
                val lo = num("lon") ?: return@mapNotNull null
                // 連鎖店把分店名一起帶上（例如「萊爾富 永和貞寧店」）
                val branch = tag("branch")?.let { " $it" }.orEmpty()
                (name + branch) to dist(loc.latitude, loc.longitude, la, lo)
            }.sortedBy { it.second }.map { it.first }.distinct().take(8)
        }.getOrDefault(emptyList())
    }

    /** 這附近記過的店（[radius] 公尺內，次數多的在前）：記帳時推薦店名用。 */
    fun historyNear(ctx: Context, loc: Location, txns: List<TxnEntity>, radius: Float = 150f): List<String> {
        val byId = txns.associateBy { it.id }
        return load(ctx).mapNotNull { s ->
            val t = byId[s.txnId] ?: return@mapNotNull null
            val m = t.merchant?.trim()?.takeIf { it.length >= 2 } ?: return@mapNotNull null
            if (dist(loc.latitude, loc.longitude, s.lat, s.lon) > radius + loc.accuracy.coerceAtMost(60f)) null else m
        }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(6)
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

    /**
     * 對準店家：在 [lat],[lon] 附近 [radius] 公尺內找名稱相符的店（OpenStreetMap），回傳最近那間的座標。
     * 連鎖店（萊爾富、7-11…）也是找離付款位置最近的那間分店。找不到回傳 null。
     */
    suspend fun snapToStore(name: String, lat: Double, lon: Double, radius: Int = 300): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        runCatching {
            // 店名去掉分店、括號等，只用主要名稱比對（例如「萊爾富 永和店」→「萊爾富」）
            val key = name.replace(Regex("[（(].*?[)）]"), "").split(' ', '　', '-', '－').firstOrNull { it.length >= 2 }?.take(12) ?: return@runCatching null
            val esc = Regex.escape(key).replace("\\Q", "").replace("\\E", "").replace("\"", "")
            val q = "[out:json][timeout:12];(" +
                "node(around:$radius,$lat,$lon)[\"name\"~\"$esc\",i];" +
                "way(around:$radius,$lat,$lon)[\"name\"~\"$esc\",i];" +
                ");out center 20;"
            val body = com.enwei.moneymanager.net.Http.getJson("https://overpass-api.de/api/interpreter?data=" + java.net.URLEncoder.encode(q, "UTF-8"), mapOf("User-Agent" to UA))
            val els = (body as? kotlinx.serialization.json.JsonObject)?.get("elements") as? kotlinx.serialization.json.JsonArray ?: return@runCatching null
            els.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun num(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                    ?: ((o["center"] as? kotlinx.serialization.json.JsonObject)?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                val la = num("lat") ?: return@mapNotNull null
                val lo = num("lon") ?: return@mapNotNull null
                Triple(la, lo, dist(lat, lon, la, lo))
            }.minByOrNull { it.third }?.let { it.first to it.second }
        }.getOrNull()
    }

    /**
     * Photon（OpenStreetMap 資料的模糊搜尋，不用金鑰）：名字差一點也找得到（例如「天佑」對到「天佑小吃」）。
     * 以 [lat],[lon] 為中心找，回傳 [radius] 公尺內最近、名字相符的那個。
     */
    suspend fun photonNear(name: String, lat: Double, lon: Double, radius: Int = 600): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        runCatching {
            val key = name.replace(Regex("[（(].*?[)）]"), "").trim().take(20).ifBlank { return@runCatching null }
            val url = "https://photon.komoot.io/api/?q=" + java.net.URLEncoder.encode(key, "UTF-8") + "&lat=$lat&lon=$lon&limit=8&location_bias_scale=0.5"
            val body = com.enwei.moneymanager.net.Http.getJson(url, mapOf("User-Agent" to UA)) as? kotlinx.serialization.json.JsonObject ?: return@runCatching null
            val feats = body["features"] as? kotlinx.serialization.json.JsonArray ?: return@runCatching null
            val core = key.split(' ', '　').first().take(6)
            feats.mapNotNull { f ->
                val o = f as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val coords = ((o["geometry"] as? kotlinx.serialization.json.JsonObject)?.get("coordinates") as? kotlinx.serialization.json.JsonArray) ?: return@mapNotNull null
                val lo = (coords.getOrNull(0) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val la = (coords.getOrNull(1) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val nm = ((o["properties"] as? kotlinx.serialization.json.JsonObject)?.get("name") as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                // 名字要對得上（互相包含），避免對到旁邊不相干的店
                if (nm.isBlank() || !(nm.contains(core, true) || core.contains(nm.take(4), true))) return@mapNotNull null
                Triple(la, lo, dist(lat, lon, la, lo))
            }.filter { it.third <= radius }.minByOrNull { it.third }?.let { it.first to it.second }
        }.getOrNull()
    }

    /** 已經試過對準的點（不管有沒有找到），避免每次打開地圖都重查 */
    private val snapPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("spot_snap", Context.MODE_PRIVATE) }
    fun snapTried(key: String) = snapPrefs.contains(key)
    fun markSnapTried(key: String) = snapPrefs.edit().putBoolean(key, true).apply()

    /**
     * 從 Google 地圖的分享連結讀出座標：短網址（maps.app.goo.gl）先跟著轉址拿到完整網址，
     * 再找「!3d緯度!4d經度」（地點本身）、「@緯度,經度」或「q=緯度,經度」。
     */
    suspend fun coordsFromMapsLink(text: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val url = Regex("https?://\\S+").find(text)?.value ?: return@withContext null
        if (!url.contains("goo.gl") && !url.contains("google.") && !url.contains("g.co")) return@withContext null
        val full = runCatching {
            val req = okhttp3.Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14)").build()
            com.enwei.moneymanager.net.Http.client.newCall(req).execute().use { r -> r.request.url.toString() + "\n" + (r.body?.string()?.take(200_000) ?: "") }
        }.getOrNull() ?: url
        fun pair(m: MatchResult?) = m?.let { it.groupValues[1].toDoubleOrNull()?.let { a -> it.groupValues[2].toDoubleOrNull()?.let { b -> a to b } } }
        val dec = runCatching { java.net.URLDecoder.decode(full, "UTF-8") }.getOrDefault(full)
        pair(Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)").find(dec))
            ?: pair(Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)").find(dec))
            ?: pair(Regex("[?&](?:q|ll|query|center)=(-?\\d+\\.\\d+),\\s*(-?\\d+\\.\\d+)").find(dec))
    }

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
