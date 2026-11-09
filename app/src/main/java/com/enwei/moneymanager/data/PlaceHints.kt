package com.enwei.moneymanager.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
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
    private val geoPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("merchant_geo3", Context.MODE_PRIVATE) }
    fun merchantGeo(): Map<String, Pair<Double, Double>?> = geoPrefs.all.mapNotNull { (k, v) ->
        val s = v as? String ?: return@mapNotNull null
        k to s.split(',').takeIf { it.size == 2 }?.let { (a, b) -> a.toDoubleOrNull()?.let { la -> b.toDoubleOrNull()?.let { lo -> la to lo } } }
    }.toMap()

    /** 自動找時有好幾個結果、要使用者自己選的店 */
    private val ambPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("merchant_amb", Context.MODE_PRIVATE) }
    fun ambiguous(): Set<String> = ambPrefs.all.keys
    fun clearAmbiguous(name: String) = ambPrefs.edit().remove(name).apply()

    /** 使用者標成「不是實體店」的店名（線上購物、簽證、面交…）：不放地圖、不列在店家位置 */
    private val exPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("merchant_excluded", Context.MODE_PRIVATE) }
    fun excluded(): Set<String> = exPrefs.all.keys
    fun setExcluded(name: String, on: Boolean) = exPrefs.edit().apply { if (on) putBoolean(name, true) else remove(name) }.apply()

    fun setMerchantGeo(name: String, ll: Pair<Double, Double>?) = geoPrefs.edit().putString(name, ll?.let { "${it.first},${it.second}" } ?: "").apply()
    fun clearMerchantGeo(name: String) { geoPrefs.edit().remove(name).apply(); ambPrefs.edit().remove(name).apply() }
    /** 只留目前交易裡還有的店名 */
    fun pruneMerchantGeo(keep: Set<String>) {
        val e = geoPrefs.edit(); geoPrefs.all.keys.filter { it !in keep }.forEach { e.remove(it) }; e.apply()
        val f = pinPrefs.edit(); pinPrefs.all.keys.filter { it !in keep }.forEach { f.remove(it) }; f.apply()
    }

    /** 使用者手動指定的店家位置（店名 → 座標）：優先於自動找到的與記帳時的定位 */
    private val pinPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("merchant_pin", Context.MODE_PRIVATE) }
    fun merchantPins(): Map<String, Pair<Double, Double>> {
        val all = pinPrefs.all.mapNotNull { (k, v) ->
            val p = (v as? String)?.split(',')?.takeIf { it.size >= 2 } ?: return@mapNotNull null
            val la = p[0].toDoubleOrNull() ?: return@mapNotNull null
            val lo = p[1].toDoubleOrNull() ?: return@mapNotNull null
            k to (la to lo)
        }.toMap()
        // 舊版讀地圖連結的錯誤：好幾家店被設成一模一樣的座標（地圖預設中心）→ 清掉這些
        val dup = all.values.groupingBy { "%.5f,%.5f".format(it.first, it.second) }.eachCount().filter { it.value >= 2 }.keys
        if (dup.isEmpty()) return all
        val bad = all.filter { "%.5f,%.5f".format(it.value.first, it.value.second) in dup }.keys
        pinPrefs.edit().also { e -> bad.forEach { e.remove(it) } }.apply()
        return all - bad
    }
    /** [via]：位置怎麼來的（搜尋、地圖、連結、定位），清單上顯示用 */
    fun setMerchantPin(name: String, lat: Double, lon: Double, via: String = "地圖") {
        pinPrefs.edit().putString(name, "$lat,$lon,$via").apply(); ambPrefs.edit().remove(name).apply()
    }
    fun pinSources(): Map<String, String> = pinPrefs.all.mapNotNull { (k, v) -> (v as? String)?.split(',')?.getOrNull(2)?.let { k to it } }.toMap()
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
        val near = anchors.take(2)
        names.forEachIndexed { i, n ->
            onProgress(i + 1, names.size)
            if (geoPrefs.contains(n) || ambPrefs.contains(n)) return@forEachIndexed
            // 有 Google／Mapbox 金鑰用它們找；沒有才用 OpenStreetMap（Overpass，常活動區域 2 公里內）
            val hit = kotlinx.coroutines.withTimeoutOrNull(15_000) {
                if (com.enwei.moneymanager.net.PlaceSearch.online) {
                    val c = near.firstOrNull()
                    val rel = com.enwei.moneymanager.net.PlaceSearch.search(n, c, 8)
                        .filter { com.enwei.moneymanager.net.Mapbox.relevant(n, it.name) }
                        .sortedBy { it.meters ?: 0f }
                    when {
                        rel.isEmpty() -> c?.let { (la, lo) -> snapToStore(n, la, lo, 2000) }
                        // 只有一個、或最近的一個就在常活動區域 3 公里內：直接用
                        rel.size == 1 || (rel.first().meters ?: Float.MAX_VALUE) <= 3000f -> rel.first().let { it.lat to it.lon }
                        // 好幾個、都離得遠（例如連鎖店、外縣市同名店）：留給使用者選
                        else -> { ambPrefs.edit().putBoolean(n, true).apply(); null }
                    }
                } else {
                    near.mapNotNull { (la, lo) -> snapToStore(n, la, lo, 2000)?.let { h -> h to dist(la, lo, h.first, h.second) } }
                        .minByOrNull { it.second }?.first
                }
            }
            if (hit == null && ambPrefs.contains(n)) return@forEachIndexed
            geoPrefs.edit().putString(n, hit?.let { "${it.first},${it.second}" } ?: "").apply()
            if (hit != null) found++
            // Overpass 是免費的公共服務：每家店之間停一下，不要一次打太快
            if (!com.enwei.moneymanager.net.PlaceSearch.online) kotlinx.coroutines.delay(1000)
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
            val els = overpassPost(q) ?: return@runCatching emptyList()
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
    /** 手機內建的地理編碼（Google Play 服務提供，不用金鑰）。 */
    private fun geocoder(): android.location.Geocoder? =
        if (android.location.Geocoder.isPresent()) android.location.Geocoder(com.enwei.moneymanager.MoneyApp.instance, java.util.Locale.TAIWAN) else null

    /** 地理編碼（有時間上限；Android 13 以上用非同步介面，舊版在背景執行緒等最多 6 秒）。 */
    @Suppress("DEPRECATION")
    private suspend fun geoByName(q: String, s: Double, w: Double, n: Double, e: Double): List<android.location.Address> {
        val g = geocoder() ?: return emptyList()
        return kotlinx.coroutines.withTimeoutOrNull(6_000) {
            if (android.os.Build.VERSION.SDK_INT >= 33) kotlinx.coroutines.suspendCancellableCoroutine { c ->
                runCatching {
                    g.getFromLocationName(q, 6, s, w, n, e, object : android.location.Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) { if (c.isActive) c.resumeWith(Result.success(addresses.toList())) }
                        override fun onError(errorMessage: String?) { if (c.isActive) c.resumeWith(Result.success(emptyList())) }
                    })
                }.onFailure { if (c.isActive) c.resumeWith(Result.success(emptyList())) }
            } else kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async { runCatching { g.getFromLocationName(q, 6, s, w, n, e).orEmpty() }.getOrDefault(emptyList()) }.await()
        }.orEmpty()
    }

    @Suppress("DEPRECATION")
    private suspend fun geoByPoint(lat: Double, lon: Double, max: Int): List<android.location.Address> {
        val g = geocoder() ?: return emptyList()
        return kotlinx.coroutines.withTimeoutOrNull(6_000) {
            if (android.os.Build.VERSION.SDK_INT >= 33) kotlinx.coroutines.suspendCancellableCoroutine { c ->
                runCatching {
                    g.getFromLocation(lat, lon, max, object : android.location.Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<android.location.Address>) { if (c.isActive) c.resumeWith(Result.success(addresses.toList())) }
                        override fun onError(errorMessage: String?) { if (c.isActive) c.resumeWith(Result.success(emptyList())) }
                    })
                }.onFailure { if (c.isActive) c.resumeWith(Result.success(emptyList())) }
            } else kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async { runCatching { g.getFromLocation(lat, lon, max).orEmpty() }.getOrDefault(emptyList()) }.await()
        }.orEmpty()
    }

    private val areaCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    /** 座標所在的行政區（例如「永和區」），店名加上它搜尋比較準。 */
    private suspend fun areaName(lat: Double, lon: Double): String? {
        val k = "%.2f,%.2f".format(lat, lon)
        areaCache[k]?.let { return it.ifEmpty { null } }
        val a = geoByPoint(lat, lon, 1).firstOrNull()
        val name = a?.locality ?: a?.subAdminArea ?: a?.subLocality
        areaCache[k] = name.orEmpty()
        return name
    }

    /**
     * 用店名在 [lat],[lon] 附近 [radius] 公尺內找（Google 的資料，透過手機內建服務）。
     * 結果的名稱要對得上店名，避免只對到路名或行政區中心。
     */
    /** 比對用的關鍵字：連鎖店帶分店時用分店名（「7-ELEVEN 安福門市」→「安福」），否則用店名開頭 */
    private fun matchKey(name: String): String {
        val parts = name.replace(Regex("[（(].*?[)）]"), "").trim().split(' ', '　').filter { it.isNotBlank() }
        val branch = parts.drop(1).lastOrNull()?.replace(Regex("(門市|分店|店)$"), "")?.takeIf { it.length >= 2 }
        return (branch ?: parts.firstOrNull().orEmpty()).take(4).lowercase()
    }

    suspend fun geocoderNear(name: String, lat: Double, lon: Double, radius: Int = 3000): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        if (geocoder() == null) return@withContext null
        val key = name.replace(Regex("[（(].*?[)）]"), "").trim().ifBlank { return@withContext null }
        val core = matchKey(name)
        val d = radius / 111_000.0
        val dl = d / kotlin.math.cos(Math.toRadians(lat))
        val area = areaName(lat, lon)
        for (q in listOfNotNull(area?.let { "$key $it" }, key)) {
            val res = geoByName(q, lat - d, lon - dl, lat + d, lon + dl)
            val hit = res.filter { it.hasLatitude() && it.hasLongitude() }.filter { a ->
                val text = listOfNotNull(a.featureName, a.premises, a.getAddressLine(0)).joinToString(" ").lowercase()
                text.contains(core)
            }.map { Triple(it.latitude, it.longitude, dist(lat, lon, it.latitude, it.longitude)) }
                .filter { it.third <= radius }.minByOrNull { it.third }
            if (hit != null) return@withContext hit.first to hit.second
        }
        null
    }

    /** 這個位置的店名（Google 反查，常常只回地址；看起來是店名才採用）。 */
    suspend fun geocoderHere(loc: Location): List<String> = withContext(Dispatchers.IO) {
        val res = geoByPoint(loc.latitude, loc.longitude, 8)
        res.mapNotNull { a -> (a.premises ?: a.featureName)?.trim() }
            .filter { n -> n.length >= 2 && !n.any { it.isDigit() } && !Regex("(路|街|段|巷|弄|號|區|里|市|縣|道)$").containsMatchIn(n) }
            .distinct()
    }

    /** 最近一次查附近店家的錯誤（測試頁顯示用） */
    @Volatile var lastNearbyError: String = ""

    /** Overpass 查詢：主站忙時換鏡像站（用 POST，查詢字串不會太長被擋）。 */
    private suspend fun overpassPost(q: String): kotlinx.serialization.json.JsonArray? = withContext(Dispatchers.IO) {
        val hosts = listOf("https://overpass-api.de/api/interpreter", "https://overpass.kumi.systems/api/interpreter", "https://overpass.private.coffee/api/interpreter")
        val client = com.enwei.moneymanager.net.Http.client.newBuilder().callTimeout(8, java.util.concurrent.TimeUnit.SECONDS).build()
        val errs = mutableListOf<String>()
        for (h in hosts) {
            val r = runCatching {
                val req = okhttp3.Request.Builder().url(h).header("User-Agent", UA)
                    .post(okhttp3.FormBody.Builder().add("data", q).build()).build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) error("HTTP ${resp.code}")
                    val body = resp.body?.string().orEmpty()
                    (kotlinx.serialization.json.Json.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject)?.get("elements") as? kotlinx.serialization.json.JsonArray
                }
            }
            r.getOrNull()?.let { lastNearbyError = ""; return@withContext it }
            errs += "${h.substringAfter("//").substringBefore('/')}：${r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName } ?: "沒有資料"}"
        }
        lastNearbyError = errs.joinToString("；")
        null
    }

    /** 已知位置的店（手動指定、用店名找到的）在 [radius] 公尺內的，由近到遠。 */
    fun knownNear(loc: Location, names: Set<String>, radius: Float = 200f): List<String> {
        val all = merchantGeo().mapNotNull { (k, v) -> v?.let { k to it } }.toMap() + merchantPins()
        return all.filter { it.key in names }
            .map { (k, v) -> k to dist(loc.latitude, loc.longitude, v.first, v.second) }
            .filter { it.second <= radius + loc.accuracy.coerceAtMost(60f) }
            .sortedBy { it.second }.map { it.first }.take(6)
    }

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
        if (com.enwei.moneymanager.net.PlaceSearch.online)
            com.enwei.moneymanager.net.PlaceSearch.nearby(loc.latitude, loc.longitude).firstOrNull()
                ?.takeIf { (it.meters ?: 999f) <= 60f + loc.accuracy.coerceAtMost(60f) }?.let { Place(it.name, null) }
        else overpass(loc)
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
        val r = 80
        val q = "[out:json][timeout:12];(" +
            "node(around:$r,${loc.latitude},${loc.longitude})[name][shop];" +
            "node(around:$r,${loc.latitude},${loc.longitude})[name][amenity];" +
            "way(around:$r,${loc.latitude},${loc.longitude})[name][shop];" +
            "way(around:$r,${loc.latitude},${loc.longitude})[name][amenity];" +
            ");out center 30;"
        val els = overpassPost(q) ?: return@runCatching null
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
    /** OpenStreetMap（Overpass）用名稱在附近找店，列出名稱與位置（由近到遠），給「搜尋店家」用 */
    suspend fun overpassSearch(name: String, lat: Double, lon: Double, radius: Int = 3000): List<com.enwei.moneymanager.net.Mapbox.Place> = withContext(Dispatchers.IO) {
        runCatching {
            val key = matchKey(name).ifBlank { return@runCatching emptyList() }
            val esc = Regex.escape(key).replace("\\Q", "").replace("\\E", "").replace("\"", "")
            val q = "[out:json][timeout:12];(" +
                "node(around:$radius,$lat,$lon)[\"name\"~\"$esc\",i];" +
                "way(around:$radius,$lat,$lon)[\"name\"~\"$esc\",i];" +
                ");out center 20;"
            val els = overpassPost(q) ?: return@runCatching emptyList()
            els.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun num(k: String) = (o[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                    ?: ((o["center"] as? kotlinx.serialization.json.JsonObject)?.get(k) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
                val tags = o["tags"] as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                fun tag(k: String) = (tags[k] as? kotlinx.serialization.json.JsonPrimitive)?.content
                val nm = (tag("name:zh") ?: tag("name") ?: return@mapNotNull null) + (tag("branch")?.let { " $it" } ?: "")
                val la = num("lat") ?: return@mapNotNull null
                val lo = num("lon") ?: return@mapNotNull null
                val addr = listOfNotNull(tag("addr:street"), tag("addr:housenumber")).joinToString("")
                com.enwei.moneymanager.net.Mapbox.Place(nm, addr, la, lo, dist(lat, lon, la, lo))
            }.sortedBy { it.meters }.distinctBy { it.name to "%.4f".format(it.lat) }.take(10)
        }.getOrDefault(emptyList())
    }

    suspend fun snapToStore(name: String, lat: Double, lon: Double, radius: Int = 300): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        runCatching {
            // 店名去掉分店、括號等，只用主要名稱比對（例如「萊爾富 永和店」→「萊爾富」）
            val key = matchKey(name).ifBlank { return@runCatching null }
            val esc = Regex.escape(key).replace("\\Q", "").replace("\\E", "").replace("\"", "")
            val q = "[out:json][timeout:12];(" +
                "node(around:$radius,$lat,$lon)[\"name\"~\"$esc\",i];" +
                "way(around:$radius,$lat,$lon)[\"name\"~\"$esc\",i];" +
                ");out center 20;"
            val els = overpassPost(q) ?: return@runCatching null
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
            val core = matchKey(name)
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
    /** 最近一次讀連結讀到什麼（讀不到時顯示給使用者看） */
    @Volatile var lastLinkDebug: String = ""

    /**
     * 讀 Google 地圖連結的座標。不同店讀出一模一樣的座標（通常是 Google 預設的地圖中心）就當作讀不到，
     * 不會再讓每家店都跑到同一個點。
     */
    suspend fun coordsFromMapsLink(text: String, near: Pair<Double, Double>? = null, forName: String = ""): Pair<Double, Double>? {
        lastLinkDebug = ""
        val ll = linkCoords(text, near) ?: return null
        val k = "%.4f,%.4f".format(ll.first, ll.second)
        val prev = linkPrefs.getString(k, null)
        if (prev != null && forName.isNotBlank() && prev != forName) {
            lastLinkDebug = "讀到的位置和「$prev」一模一樣，可能是地圖預設位置，不採用"
            return null
        }
        if (forName.isNotBlank()) linkPrefs.edit().putString(k, forName).apply()
        return ll
    }
    private val linkPrefs by lazy { com.enwei.moneymanager.MoneyApp.instance.getSharedPreferences("link_coords", Context.MODE_PRIVATE) }

    private suspend fun linkCoords(text: String, near: Pair<Double, Double>? = null): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val url = Regex("https?://[^\\s]+").find(text)?.value?.trimEnd('.', ',', '。', '）', ')') ?: return@withContext null
        if (!url.contains("goo.gl") && !url.contains("google.") && !url.contains("g.co")) return@withContext null
        fun pair(m: MatchResult?, swap: Boolean = false) = m?.let {
            val a = it.groupValues[1].toDoubleOrNull(); val b = it.groupValues[2].toDoubleOrNull()
            if (a == null || b == null) null else if (swap) b to a else a to b
        }
        fun parse(s: String): Pair<Double, Double>? {
            val dec = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
            return pair(Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)").find(dec))
                ?: pair(Regex("@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)").find(dec))
                ?: pair(Regex("[?&](?:q|ll|query|daddr)=(-?\\d{1,2}\\.\\d{4,}),\\s*(-?\\d{1,3}\\.\\d{4,})").find(dec))
        }
        // 頁面內容只信「!3d…!4d…」（地點本身的座標）；其他數字常是預設的地圖中心，會讓每家店都跑到同一個點
        fun placeOnly(s: String): Pair<Double, Double>? {
            val dec = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
            return pair(Regex("!3d(-?\\d+\\.\\d+)!4d(-?\\d+\\.\\d+)").find(dec))
        }
        parse(url)?.let { return@withContext it }
        // 短網址：一步一步跟著轉址（每一站的網址都看），手機 App 分享的連結常轉到「maps?q=店名, 地址&ftid=…」，沒有座標
        val client = com.enwei.moneymanager.net.Http.client.newBuilder().followRedirects(false).followSslRedirects(false)
            .callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
        val hops = mutableListOf(url)
        var body = ""
        runCatching {
            var cur = url
            repeat(6) {
                val req = okhttp3.Request.Builder().url(cur)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124 Safari/537.36")
                    .header("Accept-Language", "zh-TW,zh;q=0.9").build()
                val next = client.newCall(req).execute().use { r ->
                    val loc = r.header("Location")
                    if (r.isRedirect && loc != null) r.request.url.resolve(loc)?.toString() else { body = r.body?.string()?.take(400_000).orEmpty(); null }
                } ?: return@runCatching
                hops += next; cur = next
                parse(next)?.let { return@withContext it }
            }
        }
        placeOnly(body)?.let { return@withContext it }
        lastLinkDebug = hops.last().take(300)
        // 從網址取出「店名, 地址」：先用地址找（最準），再用店名在附近找
        fun dec(x: String) = runCatching { java.net.URLDecoder.decode(x, "UTF-8") }.getOrDefault(x)
        val q = hops.asReversed().firstNotNullOfOrNull { h ->
            Regex("[?&](?:q|query|daddr)=([^&]+)").find(h)?.groupValues?.get(1)?.let(::dec)
                ?: Regex("/maps/place/([^/?]+)").find(h)?.groupValues?.get(1)?.let(::dec)?.replace('+', ' ')
        }?.trim().orEmpty()
        val ogTitle = Regex("<meta[^>]+(?:property|itemprop)=\"(?:og:title|name)\"[^>]+content=\"([^\"]+)\"").find(body)?.groupValues?.get(1)
            ?: Regex("<meta[^>]+content=\"([^\"]+)\"[^>]+(?:property|itemprop)=\"(?:og:title|name)\"").find(body)?.groupValues?.get(1)
        val text0 = q.ifBlank { ogTitle.orEmpty().replace(" · ", ", ") }
        if (text0.isBlank()) return@withContext null
        val parts = text0.split(Regex(",\\s*|，|\\s·\\s"), limit = 2)
        val name = parts.first().trim()
        val addr = parts.getOrNull(1)?.trim()?.takeIf { a -> a.any { it.isDigit() } || Regex("[市縣區鄉鎮路街]").containsMatchIn(a) }
        lastLinkDebug = "店名：$name" + (addr?.let { "\n地址：$it" } ?: "\n（連結裡沒有地址）")
        if (addr != null) geocodeAddress(addr)?.let { return@withContext it }
        val center = near ?: (25.0136 to 121.5414)
        geocoderNear(name, center.first, center.second, 30_000)
    }

    /** Nominatim（OpenStreetMap 的官方搜尋）在 [lat],[lon] 附近 [radius] 公尺的框內找店名 */
    suspend fun nominatimNear(name: String, lat: Double, lon: Double, radius: Int): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        runCatching {
            val d = radius / 111_000.0; val dl = d / kotlin.math.cos(Math.toRadians(lat))
            val key = name.replace(Regex("[（(].*?[)）]"), "").trim()
            val u = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&bounded=1&accept-language=zh-TW" +
                "&viewbox=${lon - dl},${lat + d},${lon + dl},${lat - d}&q=" + java.net.URLEncoder.encode(key, "UTF-8")
            val arr = com.enwei.moneymanager.net.Http.getJson(u, mapOf("User-Agent" to UA)) as? kotlinx.serialization.json.JsonArray ?: return@runCatching null
            val core = matchKey(name)
            arr.mapNotNull { e ->
                val o = e as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val nm = ((o["name"] ?: o["display_name"]) as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                if (!nm.lowercase().contains(core)) return@mapNotNull null
                val la = (o["lat"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                val lo = (o["lon"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
                Triple(la, lo, dist(lat, lon, la, lo))
            }.minByOrNull { it.third }?.let { it.first to it.second }
        }.getOrNull()
    }

    /** 地址 → 座標：手機內建的 Google 地理編碼（台灣範圍內），找不到再用 OpenStreetMap 的 Nominatim。 */
    suspend fun geocodeAddress(addr: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val a = addr.replace(Regex("^\\d{3,6}"), "").trim()
        if (com.enwei.moneymanager.net.GooglePlaces.enabled) com.enwei.moneymanager.net.GooglePlaces.search(a, null, 1).firstOrNull()?.let { return@withContext it.lat to it.lon }
        if (com.enwei.moneymanager.net.Mapbox.enabled) com.enwei.moneymanager.net.Mapbox.search(a, null, 1, poi = false).firstOrNull()?.let { return@withContext it.lat to it.lon }
        geoByName(a, 21.8, 119.3, 25.4, 122.1).firstOrNull { it.hasLatitude() }?.let { return@withContext it.latitude to it.longitude }
        runCatching {
            val u = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=tw&accept-language=zh-TW&q=" + java.net.URLEncoder.encode(a, "UTF-8")
            val arr = com.enwei.moneymanager.net.Http.getJson(u, mapOf("User-Agent" to UA)) as? kotlinx.serialization.json.JsonArray
            val o = arr?.firstOrNull() as? kotlinx.serialization.json.JsonObject ?: return@runCatching null
            val la = (o["lat"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@runCatching null
            val lo = (o["lon"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@runCatching null
            la to lo
        }.getOrNull()
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
