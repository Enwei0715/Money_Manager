package com.enwei.moneymanager.net

import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 台股全市場的公開資料（免金鑰）：三大法人買賣超、本益比／殖利率、最新月營收、季 EPS。
 * 每日報告的選股演算法用這些數字打分數，AI 只負責判讀與撰寫。
 * 過去日期的三大法人資料不會變，存成檔案後不再重抓。
 */
object TwMarket {
    /** 一檔股票一天的三大法人買賣超（股）。 */
    data class Inst(val day: Long, val foreign: Long, val trust: Long, val dealer: Long) {
        val total: Long get() = foreign + trust + dealer
    }
    data class Valuation(val pe: Double?, val yield: Double?, val pb: Double?)
    data class Revenue(val year: Int, val month: Int, val yoy: Double?, val mom: Double?)

    private fun dir(): File = File(com.enwei.moneymanager.MoneyApp.instance.cacheDir, "tw_market").apply { mkdirs() }
    private fun d(s: String?): Double? = s?.replace(",", "")?.replace("+", "")?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "--" }?.toDoubleOrNull()

    /** 民國日期「115/11/06」 */
    private fun roc(day: LocalDate) = "${day.year - 1911}/${"%02d".format(day.monthValue)}/${"%02d".format(day.dayOfMonth)}"

    /**
     * 近 [days] 個交易日的三大法人買賣超（上市＋上櫃），代號 → 依日期由舊到新。
     * 從昨天往回找（今天收盤後也會試今天），休市日跳過。
     */
    suspend fun institutional(days: Int = 10): Map<String, List<Inst>> {
        val out = HashMap<String, MutableList<Inst>>()
        var day = LocalDate.now()
        var got = 0
        var tries = 0
        while (got < days && tries < days * 2 + 6) {
            tries++
            if (day.dayOfWeek.value <= 5) {
                val rows = instDay(day)
                if (rows.isNotEmpty()) {
                    got++
                    rows.forEach { (code, inst) -> out.getOrPut(code) { ArrayList() } += inst }
                }
            }
            day = day.minusDays(1)
        }
        return out.mapValues { (_, l) -> l.sortedBy { it.day } }
    }

    /** 一天的三大法人（先看檔案快取；今天的資料不快取空結果，收盤後才有）。 */
    private suspend fun instDay(day: LocalDate): Map<String, Inst> {
        val f = File(dir(), "inst_${day}.tsv")
        if (f.exists()) return parseCache(f, day)
        val map = HashMap<String, Inst>()
        val ymd = day.format(DateTimeFormatter.BASIC_ISO_DATE)
        // 上市：證交所 T86
        runCatching {
            val o = Http.getJson("https://www.twse.com.tw/rwd/zh/fund/T86?date=$ymd&selectType=ALLBUT0999&response=json") as? JsonObject
            val fields = (o?.get("fields") as? JsonArray)?.map { (it as? JsonPrimitive)?.content.orEmpty() }.orEmpty()
            fun idx(vararg keys: String) = fields.indexOfFirst { f -> keys.all { f.contains(it) } }
            val iCode = idx("證券代號"); val iF = idx("外陸資買賣超股數", "不含"); val iT = idx("投信買賣超股數"); val iD = idx("自營商買賣超股數")
            if (iCode >= 0 && iF >= 0 && iT >= 0) (o?.get("data") as? JsonArray)?.forEach { r ->
                val row = (r as? JsonArray)?.map { (it as? JsonPrimitive)?.content } ?: return@forEach
                val c = row.getOrNull(iCode)?.trim() ?: return@forEach
                map[c] = Inst(day.toEpochDay(), d(row.getOrNull(iF))?.toLong() ?: 0, d(row.getOrNull(iT))?.toLong() ?: 0,
                    if (iD >= 0) d(row.getOrNull(iD))?.toLong() ?: 0 else 0)
            }
        }
        delay(400)
        // 上櫃：櫃買中心三大法人（欄位：代號、名稱、外資買進、賣出、買賣超…投信買賣超在第 13 欄，最後一欄為合計）
        runCatching {
            val o = Http.getJson("https://www.tpex.org.tw/web/stock/3insti/daily_trade/3itrade_hedge_result.php?l=zh-tw&o=json&se=EW&t=D&d=${roc(day)}") as? JsonObject
            val data = ((o?.get("tables") as? JsonArray)?.firstOrNull() as? JsonObject)?.get("data") as? JsonArray ?: o?.get("aaData") as? JsonArray
            data?.forEach { r ->
                val row = (r as? JsonArray)?.map { (it as? JsonPrimitive)?.content } ?: return@forEach
                if (row.size < 20) return@forEach
                val c = row[0]?.trim() ?: return@forEach
                val total = d(row.last())?.toLong() ?: 0
                val f0 = d(row[4])?.toLong() ?: 0
                val t0 = d(row[13])?.toLong() ?: 0
                map[c] = Inst(day.toEpochDay(), f0, t0, total - f0 - t0)
            }
        }
        // 過去的日期（或今天已有資料）寫入快取；休市日寫空檔，之後不再查
        if (map.isNotEmpty() || day.isBefore(LocalDate.now())) runCatching {
            f.writeText(map.entries.joinToString("\n") { (c, i) -> "$c\t${i.foreign}\t${i.trust}\t${i.dealer}" })
        }
        return map
    }

    private fun parseCache(f: File, day: LocalDate): Map<String, Inst> = runCatching {
        f.readLines().mapNotNull { l ->
            val p = l.split('\t')
            if (p.size < 4) null else p[0] to Inst(day.toEpochDay(), p[1].toLongOrNull() ?: 0, p[2].toLongOrNull() ?: 0, p[3].toLongOrNull() ?: 0)
        }.toMap()
    }.getOrDefault(emptyMap())

    /** 本益比、殖利率、股價淨值比（上市＋上櫃當日）。 */
    suspend fun valuation(): Map<String, Valuation> {
        val out = HashMap<String, Valuation>()
        runCatching {
            (Http.getJson("https://openapi.twse.com.tw/v1/exchangeReport/BWIBBU_ALL") as? JsonArray)?.forEach { e ->
                val c = e.str("Code")?.trim() ?: return@forEach
                out[c] = Valuation(d(e.str("PEratio"))?.takeIf { it > 0 }, d(e.str("DividendYield")), d(e.str("PBratio")))
            }
        }
        runCatching {
            (Http.getJson("https://www.tpex.org.tw/openapi/v1/tpex_mainboard_peratio_analysis") as? JsonArray)?.forEach { e ->
                val c = e.str("SecuritiesCompanyCode")?.trim() ?: return@forEach
                out[c] = Valuation(d(e.str("PriceEarningRatio"))?.takeIf { it > 0 }, d(e.str("DividendYield")), d(e.str("PriceBookRatio")))
            }
        }
        return out
    }

    /** 全部上市櫃公司最新一個月營收的年增、月增（%）。 */
    suspend fun revenueAll(): Map<String, Revenue> {
        val out = HashMap<String, Revenue>()
        for (url in listOf("https://openapi.twse.com.tw/v1/opendata/t187ap05_L", "https://www.tpex.org.tw/openapi/v1/mopsfin_t187ap05_O")) runCatching {
            (Http.getJson(url) as? JsonArray)?.forEach { e ->
                val c = e.str("公司代號")?.trim() ?: return@forEach
                val ym = e.str("資料年月")?.trim().orEmpty()
                val y = ym.dropLast(2).toIntOrNull()?.plus(1911) ?: return@forEach
                val m = ym.takeLast(2).toIntOrNull() ?: return@forEach
                out[c] = Revenue(y, m, d(e.str("營業收入-去年同月增減(%)")), d(e.str("營業收入-上月比較增減(%)")))
            }
        }
        return out
    }

    /** 一檔股票近 8 季 EPS（舊到新，FinMind 綜合損益表），快取 3 天。 */
    suspend fun epsHistory(code: String): List<Pair<String, Double>> {
        val f = File(dir(), "eps_$code.tsv")
        if (f.exists() && System.currentTimeMillis() - f.lastModified() < 3 * 86_400_000L) return runCatching {
            f.readLines().mapNotNull { l -> l.split('\t').takeIf { it.size == 2 }?.let { it[0] to (it[1].toDoubleOrNull() ?: return@mapNotNull null) } }
        }.getOrDefault(emptyList())
        val start = LocalDate.now().minusYears(2).minusMonths(3)
        val list = runCatching {
            val root = Http.getJson("https://api.finmindtrade.com/api/v4/data?dataset=TaiwanStockFinancialStatements&data_id=$code&start_date=$start")
            ((root as? JsonObject)?.get("data") as? JsonArray)?.mapNotNull { e ->
                if (e.str("type") != "EPS") return@mapNotNull null
                val date = e.str("date") ?: return@mapNotNull null
                val v = (e as? JsonObject)?.get("value")?.let { (it as? JsonPrimitive)?.content?.toDoubleOrNull() } ?: return@mapNotNull null
                date to v
            }.orEmpty().sortedBy { it.first }.takeLast(8)
        }.getOrDefault(emptyList())
        if (list.isNotEmpty()) runCatching { f.writeText(list.joinToString("\n") { "${it.first}\t${it.second}" }) }
        return list
    }
}
