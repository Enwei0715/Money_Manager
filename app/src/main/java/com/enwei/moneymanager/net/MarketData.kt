package com.enwei.moneymanager.net

import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.data.FxRateEntity
import com.enwei.moneymanager.data.QuoteEntity
import com.enwei.moneymanager.data.quoteKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object FxService {
    /** 即時匯率（open.er-api.com，免金鑰，每日更新多次）。 */
    suspend fun fetch(base: String): List<FxRateEntity> {
        val root = Http.getJson("https://open.er-api.com/v6/latest/${base.uppercase()}")
        if (root.str("result") != "success") throw IllegalStateException("匯率服務回應錯誤：${root.str("error-type")}")
        val rates = root.obj("rates") as? JsonObject ?: return emptyList()
        val now = System.currentTimeMillis()
        return rates.mapNotNull { (cur, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { FxRateEntity(cur, it, now) } }
    }

    /**
     * 即時匯率：以 Yahoo Finance 外匯報價（盤中即時）覆蓋指定幣別，其餘用每日匯率。
     * Yahoo 代碼 "USDTWD=X" 的價格是 1 USD 可換多少 TWD，換成「1 基準幣可換多少該幣別」需取倒數。
     */
    suspend fun fetchRealtime(base: String, currencies: Collection<String>): List<FxRateEntity> = coroutineScope {
        val now = System.currentTimeMillis()
        currencies.filter { !it.equals(base, true) }.distinct().map { cur ->
            async {
                runCatching {
                    val sym = URLEncoder.encode("${cur.uppercase()}${base.uppercase()}=X", "UTF-8")
                    val root = Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/$sym?range=1d&interval=1d")
                    val meta = (root.obj("chart").obj("result") as? JsonArray)?.firstOrNull().obj("meta")
                    meta.num("regularMarketPrice")?.takeIf { it > 0 }?.let { FxRateEntity(cur.uppercase(), 1.0 / it, now) }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    val common = listOf("USD", "JPY", "EUR", "CNY", "HKD", "KRW", "GBP", "AUD", "SGD", "THB", "CAD", "CHF", "VND", "MYR")
}

object QuoteService {
    suspend fun fetch(items: List<Pair<Market, String>>): List<QuoteEntity> = kotlinx.coroutines.coroutineScope {
        val tw = items.filter { it.first == Market.TW }.map { it.second.uppercase() }.distinct()
        val us = items.filter { it.first == Market.US }.map { it.second.uppercase() }.distinct()
        // 證交所與 Yahoo 同時查（各最多約 6 秒），證交所的價格合理就用證交所，否則用 Yahoo
        val twseJob = async { if (tw.isEmpty()) emptyList() else runCatching { fetchTwse(tw) }.getOrDefault(emptyList()) }
        val yahooTw = tw.map { sym ->
            sym to async {
                runCatching { fetchYahoo("$sym.TW", Market.TW, sym) }.getOrNull()
                    ?: runCatching { fetchYahoo("$sym.TWO", Market.TW, sym) }.getOrNull()
            }
        }
        val usJobs = us.map { sym -> async { runCatching { fetchYahoo(sym, Market.US, sym) }.getOrNull() } }
        val twse = twseJob.await().associateBy { it.key }
        val out = mutableListOf<QuoteEntity>()
        for ((sym, job) in yahooTw) {
            val raw = twse[quoteKey(Market.TW, sym)]
            val q = raw?.takeIf { sane(it) } ?: job.await()?.takeIf { sane(it) } ?: raw ?: job.await()
            if (q != null) out += q
        }
        out += usJobs.mapNotNull { it.await() }
        out
    }

    /** 台股單日漲跌幅上限 10%：超過太多（例如抓到錯的欄位或別檔的價格）就不採用。 */
    private fun sane(q: QuoteEntity): Boolean {
        val prev = q.previousClose ?: return q.price > 0
        return q.price > 0 && prev > 0 && kotlin.math.abs(q.price / prev - 1) <= 0.105
    }

    /** 證交所即時報價（上市 tse、上櫃 otc 同時查詢）。 */
    private suspend fun fetchTwse(symbols: List<String>): List<QuoteEntity> {
        val channels = symbols.flatMap { listOf("tse_$it.tw", "otc_$it.tw") }.joinToString("|")
        val url = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=${URLEncoder.encode(channels, "UTF-8")}&json=1&delay=0"
        val root = Http.getJson(url, mapOf("Referer" to "https://mis.twse.com.tw/stock/index.jsp"), quick = true)
        val arr = root.obj("msgArray") as? JsonArray ?: return emptyList()
        val now = System.currentTimeMillis()
        return arr.mapNotNull { e ->
            val code = e.str("c") ?: return@mapNotNull null
            val prev = e.str("y")?.toDoubleOrNull()
            // z 為最新成交價；盤中尚無成交時為 "-"，改用最佳買價或昨收
            val last = e.str("z")?.toDoubleOrNull()
                ?: e.str("b")?.split('_')?.firstOrNull()?.toDoubleOrNull()
                ?: prev ?: return@mapNotNull null
            QuoteEntity(quoteKey(Market.TW, code), last, prev, "TWD", e.str("n"), now)
        }
    }

    /**
     * 歷年除息（除息日、每股現金股利），來源 Yahoo Finance。台股先試上市 .TW，再試上櫃 .TWO。
     */
    suspend fun fetchDividends(market: Market, symbol: String): List<com.enwei.moneymanager.core.DividendEvent> {
        val candidates = if (market == Market.TW) listOf("$symbol.TW", "$symbol.TWO") else listOf(symbol)
        for (ys in candidates) {
            val root = runCatching {
                Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(ys, "UTF-8")}?range=10y&interval=1mo&events=div%2Csplits")
            }.getOrNull() ?: continue
            val result = (root.obj("chart").obj("result") as? JsonArray)?.firstOrNull() ?: continue
            val zone = java.time.ZoneId.of(if (market == Market.TW) "Asia/Taipei" else "America/New_York")
            fun day(ts: Long) = java.time.Instant.ofEpochSecond(ts).atZone(zone).toLocalDate().toEpochDay()
            // 現金股利
            val cash = (result.obj("events").obj("dividends") as? JsonObject)?.values?.mapNotNull { e ->
                val ts = e.num("date")?.toLong() ?: return@mapNotNull null
                val amt = e.num("amount") ?: return@mapNotNull null
                day(ts) to amt
            }.orEmpty()
            // 配股（Yahoo 以 split 表示，例如 1050:1000 → 每股配 0.05 股）
            val stock = (result.obj("events").obj("splits") as? JsonObject)?.values?.mapNotNull { e ->
                val ts = e.num("date")?.toLong() ?: return@mapNotNull null
                val num = e.num("numerator") ?: return@mapNotNull null
                val den = e.num("denominator")?.takeIf { it > 0 } ?: return@mapNotNull null
                (num / den - 1).takeIf { it > 1e-6 }?.let { day(ts) to it }
            }.orEmpty()
            val days = (cash.map { it.first } + stock.map { it.first }).toSortedSet()
            return days.map { d ->
                com.enwei.moneymanager.core.DividendEvent(d, cash.filter { it.first == d }.sumOf { it.second }, stock.filter { it.first == d }.sumOf { it.second })
            }
        }
        return emptyList()
    }

    /** 近兩年每月收盤價（月 → 收盤；當月為最新價），來源 Yahoo Finance 月線。用於證券帳戶的資產走勢。 */
    suspend fun fetchMonthlyCloses(market: Market, symbol: String): Map<java.time.YearMonth, Double> {
        val candidates = if (market == Market.TW) listOf("$symbol.TW", "$symbol.TWO") else listOf(symbol)
        for (ys in candidates) {
            val root = runCatching {
                Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(ys, "UTF-8")}?range=2y&interval=1mo")
            }.getOrNull() ?: continue
            val result = (root.obj("chart").obj("result") as? JsonArray)?.firstOrNull() ?: continue
            val ts = result.obj("timestamp") as? JsonArray ?: continue
            val close = ((result.obj("indicators").obj("quote") as? JsonArray)?.firstOrNull().obj("close") as? JsonArray) ?: continue
            val zone = java.time.ZoneId.of(if (market == Market.TW) "Asia/Taipei" else "America/New_York")
            val out = HashMap<java.time.YearMonth, Double>()
            ts.forEachIndexed { i, t ->
                val sec = (t as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull() ?: return@forEachIndexed
                val c = (close.getOrNull(i) as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull() ?: return@forEachIndexed
                out[java.time.YearMonth.from(java.time.Instant.ofEpochSecond(sec).atZone(zone))] = c
            }
            if (out.isNotEmpty()) return out
        }
        return emptyMap()
    }

    private suspend fun fetchYahoo(yahooSymbol: String, market: Market, symbol: String): QuoteEntity? {
        val root = Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(yahooSymbol, "UTF-8")}?range=5d&interval=1d", quick = true)
        val result = (root.obj("chart").obj("result") as? JsonArray)?.firstOrNull() ?: return null
        val meta = result.obj("meta")
        val price = meta.num("regularMarketPrice") ?: return null
        return QuoteEntity(
            key = quoteKey(market, symbol),
            price = price,
            previousClose = meta.num("previousClose") ?: meta.num("chartPreviousClose"),
            currency = meta.str("currency") ?: if (market == Market.TW) "TWD" else "USD",
            name = meta.str("shortName") ?: meta.str("longName"),
            updatedAt = System.currentTimeMillis(),
        )
    }
}
