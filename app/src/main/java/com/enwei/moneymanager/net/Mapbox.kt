package com.enwei.moneymanager.net

import android.content.Context
import com.enwei.moneymanager.MoneyApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URLEncoder

/**
 * Mapbox（使用者自己的金鑰）：店家搜尋（Search Box API）與底圖圖磚。
 * 金鑰只存在這支手機（獨立的設定檔，不進備份）。沒有金鑰時 App 照舊用 OpenStreetMap。
 */
object Mapbox {
    private val prefs by lazy { MoneyApp.instance.getSharedPreferences("mapbox", Context.MODE_PRIVATE) }

    var token: String
        get() = prefs.getString("token", "").orEmpty()
        set(v) { prefs.edit().putString("token", v.trim()).apply() }

    /** 公開金鑰（pk. 開頭）才能用 */
    val enabled: Boolean get() = token.startsWith("pk.")

    /** 一個地點：名稱、地址、座標、離搜尋中心的距離（公尺） */
    data class Place(val name: String, val address: String, val lat: Double, val lon: Double, val meters: Float? = null)

    /** 最近一次的錯誤（設定頁測試、找不到時顯示） */
    @Volatile var lastError: String = ""

    private fun str(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.content
    private fun num(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.content?.toDoubleOrNull()

    private fun dist(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Float =
        FloatArray(1).also { android.location.Location.distanceBetween(aLat, aLon, bLat, bLon, it) }[0]

    private fun parse(root: kotlinx.serialization.json.JsonElement, near: Pair<Double, Double>?): List<Place> {
        val feats = (root as? JsonObject)?.get("features") as? JsonArray ?: return emptyList()
        return feats.mapNotNull { f ->
            val o = f as? JsonObject ?: return@mapNotNull null
            val p = o["properties"] as? JsonObject ?: return@mapNotNull null
            val c = p["coordinates"] as? JsonObject
            val g = ((o["geometry"] as? JsonObject)?.get("coordinates") as? JsonArray)
            val lat = num(c, "latitude") ?: (g?.getOrNull(1) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val lon = num(c, "longitude") ?: (g?.getOrNull(0) as? JsonPrimitive)?.content?.toDoubleOrNull() ?: return@mapNotNull null
            val name = str(p, "name")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val addr = str(p, "full_address") ?: str(p, "place_formatted") ?: str(p, "address") ?: ""
            Place(name, addr, lat, lon, near?.let { dist(it.first, it.second, lat, lon) })
        }
    }

    /** 最近一次呼叫是否成功連上（金鑰有效） */
    @Volatile var lastOk: Boolean = false

    private suspend fun get(url: String): kotlinx.serialization.json.JsonElement? = withContext(Dispatchers.IO) {
        runCatching { Http.getJson(url, quick = true) }
            .onFailure { lastError = (it.message ?: it.javaClass.simpleName).substringBefore(": http").let { m -> if (m.contains("401")) "金鑰無效（HTTP 401）" else m } }
            .onSuccess { lastError = ""; lastOk = true }
            .onFailure { lastOk = false }
            .getOrNull()
    }

    /** 查過的結果先記著（同樣的關鍵字、約 500 公尺內的中心 30 分鐘內不重查），避免太多 API 呼叫 */
    private val cache = object : android.util.LruCache<String, Pair<Long, List<Place>>>(200) {}
    private fun cached(k: String): List<Place>? = cache.get(k)?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000L }?.second

    /** 用名稱搜尋店家（以 [near] 為中心，台灣境內），由近到遠；[poi]＝只找店家（false 時也找地址） */
    suspend fun search(q: String, near: Pair<Double, Double>?, limit: Int = 8, poi: Boolean = true): List<Place> {
        if (!enabled || q.isBlank()) return emptyList()
        val k = "s|${q.trim().lowercase()}|${near?.let { "%.3f,%.3f".format(it.first, it.second) }}|$limit|$poi"
        cached(k)?.let { return it }
        fun url(lang: Boolean) = "https://api.mapbox.com/search/searchbox/v1/forward?q=" + URLEncoder.encode(q.trim(), "UTF-8") +
            (if (lang) "&language=zh" else "") + "&country=TW&limit=$limit" + (if (poi) "&types=poi" else "") +
            (near?.let { "&proximity=${it.second},${it.first}" } ?: "") + "&access_token=" + token
        // 只要店家（POI）；中文查不到再不指定語言查一次
        var res = get(url(lang = true))?.let { parse(it, near) }.orEmpty()
        if (res.isEmpty() && lastOk) res = get(url(lang = false))?.let { parse(it, near) }.orEmpty()
        // 只留名稱對得上、離中心 15 公里內的（Mapbox 會回傳很遠、只有一個字相同的地名，不能用）
        if (poi) res = res.filter { (it.meters ?: 0f) <= 15_000f && relevant(q, it.name) }
        return res.sortedBy { it.meters ?: 0f }.also { if (lastOk) cache.put(k, System.currentTimeMillis() to it) }
    }

    /** 名稱是否對得上：查詢的主要字詞（連鎖店用分店名）要出現在結果名稱裡 */
    fun relevant(q: String, name: String): Boolean {
        fun norm(s: String) = s.lowercase().replace(Regex("[\\s\\-－·・()（）]"), "")
        val n = norm(name)
        val parts = q.replace(Regex("[（(].*?[)）]"), "").trim().split(' ', '　').filter { it.isNotBlank() }
        val branch = parts.drop(1).lastOrNull()?.replace(Regex("(門市|分店|店)$"), "")?.let(::norm)?.takeIf { it.length >= 2 }
        if (branch != null) return n.contains(branch)
        val core = norm(parts.firstOrNull().orEmpty())
        if (core.length < 2) return n.contains(core) && core.isNotEmpty()
        // 至少兩個連續字相同
        return (0 until core.length - 1).any { n.contains(core.substring(it, it + 2)) }
    }

    /** 這個位置附近的店（由近到遠） */
    suspend fun nearby(lat: Double, lon: Double, limit: Int = 10): List<Place> {
        if (!enabled) return emptyList()
        val k = "n|%.3f,%.3f|$limit".format(lat, lon)
        cached(k)?.let { return it }
        val url = "https://api.mapbox.com/search/searchbox/v1/reverse?longitude=$lon&latitude=$lat&types=poi&language=zh&limit=$limit&access_token=$token"
        val root = get(url) ?: return emptyList()
        return parse(root, lat to lon).sortedBy { it.meters ?: 0f }.also { cache.put(k, System.currentTimeMillis() to it) }
    }

    /** 底圖圖磚（Mapbox Streets，512 像素） */
    fun tileUrl(z: Int, x: Int, y: Int) = "https://api.mapbox.com/styles/v1/mapbox/streets-v12/tiles/256/$z/$x/$y@2x?access_token=$token"

    /** 測試金鑰：成功回傳 null，失敗回傳原因 */
    suspend fun test(): String? {
        if (!token.startsWith("pk.")) return "金鑰應該是 pk. 開頭的公開金鑰"
        // 金鑰有效＝Mapbox 回應成功（有沒有搜到店不影響）
        cache.evictAll()
        search("台北車站", 25.0478 to 121.5170, 1, poi = false)
        return if (lastOk) null else lastError.ifBlank { "連不上 Mapbox" }
    }
}
