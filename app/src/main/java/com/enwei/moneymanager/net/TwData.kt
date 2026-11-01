package com.enwei.moneymanager.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 台股公開資料：股票名稱清單（用名稱查代號）與每月營收。
 * 名稱清單存在手機裡 7 天；營收在 App 執行期間快取 12 小時。
 */
object TwData {
    @Serializable
    data class StockInfo(val code: String, val name: String, val industry: String = "")

    /** 一個月的營收（元）；[yoy]、[mom] 為年增率、月增率（%）。 */
    data class MonthRevenue(val year: Int, val month: Int, val revenue: Double, val yoy: Double?, val mom: Double?)

    private val lock = Mutex()
    @Volatile private var stocks: List<StockInfo>? = null
    private val revenueCache = ConcurrentHashMap<String, Pair<Long, List<MonthRevenue>>>()

    /** 上市櫃股票清單（代號、名稱、產業）。先用 FinMind，失敗時改用證交所與櫃買中心的開放資料。 */
    suspend fun stockList(context: Context): List<StockInfo> = stocks ?: lock.withLock {
        stocks ?: withContext(Dispatchers.IO) {
            val file = File(context.filesDir, "tw_stocks.json")
            val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < 7L * 86_400_000
            val cached = if (file.exists()) runCatching {
                Http.json.decodeFromString(ListSerializer(StockInfo.serializer()), file.readText())
            }.getOrNull() else null
            val list = if (fresh && !cached.isNullOrEmpty()) cached else fetchList() ?: cached.orEmpty()
            if (list.isNotEmpty() && list !== cached) runCatching { file.writeText(Http.json.encodeToString(ListSerializer(StockInfo.serializer()), list)) }
            list.also { if (it.isNotEmpty()) stocks = it }
        }
    }

    private suspend fun fetchList(): List<StockInfo>? {
        runCatching {
            val root = Http.getJson("https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockInfo")
            val data = root.obj("data") as? JsonArray ?: error("no data")
            data.mapNotNull { e ->
                val code = e.str("stock_id")?.trim() ?: return@mapNotNull null
                val name = e.str("stock_name")?.trim() ?: return@mapNotNull null
                if (!code.matches(Regex("[0-9]{4,6}[A-Z]?"))) null else StockInfo(code, name, e.str("industry_category").orEmpty())
            }.distinctBy { it.code }.takeIf { it.size > 500 }
        }.getOrNull()?.let { return it }
        // 備援：證交所（上市）＋櫃買中心（上櫃）
        val out = ArrayList<StockInfo>()
        runCatching {
            (Http.getJson("https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL") as? JsonArray)?.forEach { e ->
                val c = e.str("Code") ?: return@forEach; val n = e.str("Name") ?: return@forEach
                out += StockInfo(c.trim(), n.trim())
            }
        }
        runCatching {
            (Http.getJson("https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes") as? JsonArray)?.forEach { e ->
                val c = e.str("SecuritiesCompanyCode") ?: return@forEach; val n = e.str("CompanyName") ?: return@forEach
                out += StockInfo(c.trim(), n.trim())
            }
        }
        return out.filter { it.code.matches(Regex("[0-9]{4,6}[A-Z]?")) }.distinctBy { it.code }.takeIf { it.isNotEmpty() }
    }

    /** 用代號或名稱（部分文字也可以）查股票；代號完全相同、名稱開頭相同的排前面。 */
    suspend fun search(context: Context, query: String, limit: Int = 30): List<StockInfo> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val list = stockList(context)
        return list.filter { it.code.startsWith(q, true) || it.name.contains(q, true) }
            .sortedWith(compareBy<StockInfo>({ it.code != q && it.name != q }, { !it.code.startsWith(q) && !it.name.startsWith(q) }, { it.code.length }, { it.code }))
            .take(limit)
    }

    /** 已載入的股票清單（沒有時為空）。 */
    fun cached(): List<StockInfo> = stocks.orEmpty()

    /** 代號 → 名稱（清單已載入時才有）。 */
    fun nameOf(code: String): String? = stocks?.firstOrNull { it.code == code }?.name

    /** 近 [months] 個月的月營收（舊到新），含年增率與月增率。 */
    suspend fun monthRevenue(code: String, months: Int = 25): List<MonthRevenue> {
        revenueCache[code]?.takeIf { System.currentTimeMillis() - it.first < 12 * 3600_000L }?.let { return it.second }
        val start = java.time.LocalDate.now().minusMonths(months.toLong() + 13).withDayOfMonth(1)
        val raw = runCatching {
            val root = Http.getJson("https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockMonthRevenue&data_id=$code&start_date=$start")
            (root.obj("data") as? JsonArray)?.mapNotNull { e ->
                val y = e.num("revenue_year")?.toInt() ?: return@mapNotNull null
                val m = e.num("revenue_month")?.toInt() ?: return@mapNotNull null
                val r = e.num("revenue")?.takeIf { it > 0 } ?: return@mapNotNull null
                Triple(y, m, r)
            }.orEmpty()
        }.getOrDefault(emptyList()).distinctBy { it.first * 100 + it.second }.sortedBy { it.first * 100 + it.second }
        if (raw.isEmpty()) return revenueCache[code]?.second.orEmpty()
        val byKey = raw.associate { (it.first * 100 + it.second) to it.third }
        val out = raw.mapIndexed { i, (y, m, r) ->
            val ly = byKey[(y - 1) * 100 + m]
            val prev = raw.getOrNull(i - 1)?.third
            MonthRevenue(y, m, r, ly?.let { (r / it - 1) * 100 }, prev?.let { (r / it - 1) * 100 })
        }.takeLast(months)
        revenueCache[code] = System.currentTimeMillis() to out
        return out
    }
}
