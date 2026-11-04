package com.enwei.moneymanager.net

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray

/**
 * 全市場量價掃描（給每日報告選股用）：上市櫃當日收盤 → 各產業強弱、各產業成交值大且上漲的個股；
 * 再查候選股近半年 K 線，算 5／20 日漲幅、相對大盤、均線位置與量比，選股才有量價依據、不只看新聞熱度。
 */
object MarketScan {
    data class Row(val code: String, val name: String, val industry: String, val close: Double, val pct: Double, val value: Double)
    data class Sector(val name: String, val n: Int, val pct: Double, val upRatio: Double, val value: Double)
    data class Strength(
        val code: String, val ret5: Double, val ret20: Double, val rs20: Double?,
        val aboveMa20: Boolean, val aboveMa60: Boolean, val volRatio: Double?, val offHigh: Double,
    )

    /** 產業名稱去掉「業」字，標籤比較短。 */
    fun shortIndustry(s: String): String = s.removeSuffix("業").removeSuffix("類").trim()

    private fun d(s: String?): Double? = s?.replace(",", "")?.replace("+", "")?.trim()?.toDoubleOrNull()

    /** 上市＋上櫃當日收盤（只取 4 碼普通股，ETF 與權證不算）。 */
    suspend fun board(): List<Row> {
        val ind = TwData.cached().associate { it.code to it.industry }
        val out = ArrayList<Row>()
        runCatching {
            (Http.getJson("https://openapi.twse.com.tw/v1/exchangeReport/STOCK_DAY_ALL") as? JsonArray)?.forEach { e ->
                val c = e.str("Code")?.trim() ?: return@forEach
                val close = d(e.str("ClosingPrice")) ?: return@forEach
                val ch = d(e.str("Change")) ?: 0.0
                out += Row(c, e.str("Name")?.trim().orEmpty(), ind[c].orEmpty(), close, if (close - ch > 0) ch / (close - ch) * 100 else 0.0, d(e.str("TradeValue")) ?: 0.0)
            }
        }
        runCatching {
            (Http.getJson("https://www.tpex.org.tw/openapi/v1/tpex_mainboard_daily_close_quotes") as? JsonArray)?.forEach { e ->
                val c = e.str("SecuritiesCompanyCode")?.trim() ?: return@forEach
                val close = d(e.str("Close")) ?: return@forEach
                val ch = d(e.str("Change")) ?: 0.0
                out += Row(c, e.str("CompanyName")?.trim().orEmpty(), ind[c].orEmpty(), close, if (close - ch > 0) ch / (close - ch) * 100 else 0.0, d(e.str("TransactionAmount")) ?: 0.0)
            }
        }
        return out.filter { it.code.matches(Regex("[1-9][0-9]{3}")) && it.close > 0 }.distinctBy { it.code }
    }

    /** 各產業當日強弱：成交值加權漲跌幅、上漲家數比例（家數太少的產業不算）。 */
    fun sectors(rows: List<Row>): List<Sector> = rows.filter { it.industry.isNotBlank() && "ETF" !in it.industry && "存託" !in it.industry }
        .groupBy { it.industry }.filter { it.value.size >= 4 }
        .map { (k, l) ->
            val v = l.sumOf { it.value }.coerceAtLeast(1.0)
            Sector(shortIndustry(k), l.size, l.sumOf { it.pct * it.value } / v, l.count { it.pct > 0 }.toDouble() / l.size, v)
        }.sortedByDescending { it.pct }

    /** 強勢候選：強勢產業（加上金融）各取成交值最大、當日上漲的幾檔。 */
    fun candidates(rows: List<Row>, sectors: List<Sector>, perSector: Int = 3, topSectors: Int = 8): List<Row> {
        val pick = (sectors.take(topSectors).map { it.name } + sectors.filter { it.name.contains("金融") }.map { it.name }).distinct()
        return pick.flatMap { s ->
            rows.filter { shortIndustry(it.industry) == s && it.pct > 0 }.sortedByDescending { it.value }.take(perSector)
        }
    }

    /** 候選股近半年 K 線算量價強度；[index] 為加權指數 20 日漲幅（算相對強弱）。 */
    suspend fun strength(codes: List<String>, index: Double?): Map<String, Strength> = coroutineScope {
        val gate = Semaphore(6)
        codes.distinct().map { c ->
            async {
                gate.withPermit {
                    val b = runCatching { InvestData.bars(c, "6mo") }.getOrNull() ?: return@withPermit null
                    of(c, b.close, b.volume, index)
                }
            }
        }.mapNotNull { it.await() }.associateBy { it.code }
    }

    fun ret(cl: List<Double>, n: Int): Double? = if (cl.size > n && cl[cl.size - 1 - n] > 0) (cl.last() / cl[cl.size - 1 - n] - 1) * 100 else null

    private fun of(code: String, cl: List<Double>, vol: List<Double>, index: Double?): Strength? {
        if (cl.size < 25) return null
        val last = cl.last()
        val ma20 = cl.takeLast(20).average()
        val ma60 = cl.takeLast(60).average()
        val r20 = ret(cl, 20) ?: return null
        val v5 = vol.takeLast(5).filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
        val v20 = vol.takeLast(20).filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
        val hi = cl.takeLast(60).maxOrNull() ?: last
        return Strength(
            code, ret(cl, 5) ?: 0.0, r20, index?.let { r20 - it }, last >= ma20, cl.size >= 60 && last >= ma60,
            if (v5 != null && v20 != null && v20 > 0) v5 / v20 else null, (last / hi - 1) * 100,
        )
    }

    /** 卡片上的依據標籤（數字為主，最多 4 個）。 */
    fun basis(s: Strength?, yoy: Double?): List<String> = buildList {
        if (s != null) {
            add("20日 %+.0f%%".format(s.ret20))
            s.rs20?.let { if (it >= 3) add("強於大盤 %+.0f%%".format(it)) else if (it <= -3) add("弱於大盤 %+.0f%%".format(it)) }
            if (s.aboveMa20 && s.aboveMa60) add("站上月季線") else if (!s.aboveMa20 && !s.aboveMa60) add("跌破月季線") else if (s.aboveMa20) add("站上月線")
            s.volRatio?.let { if (it >= 1.3) add("量增 %.1f 倍".format(it)) }
            if (s.offHigh > -3) add("近波段高點")
        }
        yoy?.let { add("營收年增 %+.0f%%".format(it)) }
    }.take(4)

    /** 報告提示用的一行描述。 */
    fun line(r: Row?, s: Strength, name: String, industry: String): String =
        "- ${s.code} $name｜${industry.ifBlank { "?" }}｜今日 ${r?.let { "%+.1f%%".format(it.pct) } ?: "?"}｜5日 ${"%+.1f%%".format(s.ret5)}｜20日 ${"%+.1f%%".format(s.ret20)}" +
            "｜相對大盤 ${s.rs20?.let { "%+.1f".format(it) } ?: "?"}｜${if (s.aboveMa20) "站上" else "低於"}月線、${if (s.aboveMa60) "站上" else "低於"}季線" +
            "｜量比 ${s.volRatio?.let { "%.1f".format(it) } ?: "?"}｜距 60 日高 ${"%.0f%%".format(s.offHigh)}"
}
