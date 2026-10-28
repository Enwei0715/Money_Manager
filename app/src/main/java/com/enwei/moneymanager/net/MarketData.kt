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

object FxService {
    /** 即時匯率（open.er-api.com，免金鑰，每日更新多次）。 */
    suspend fun fetch(base: String): List<FxRateEntity> {
        val root = Http.getJson("https://open.er-api.com/v6/latest/${base.uppercase()}")
        if (root.str("result") != "success") throw IllegalStateException("匯率服務回應錯誤：${root.str("error-type")}")
        val rates = root.obj("rates") as? JsonObject ?: return emptyList()
        val now = System.currentTimeMillis()
        return rates.mapNotNull { (cur, v) -> (v as? JsonPrimitive)?.doubleOrNull?.let { FxRateEntity(cur, it, now) } }
    }

    val common = listOf("USD", "JPY", "EUR", "CNY", "HKD", "KRW", "GBP", "AUD", "SGD", "THB", "CAD", "CHF", "VND", "MYR")
}

object QuoteService {
    suspend fun fetch(items: List<Pair<Market, String>>): List<QuoteEntity> {
        val out = mutableListOf<QuoteEntity>()
        val tw = items.filter { it.first == Market.TW }.map { it.second.uppercase() }.distinct()
        val us = items.filter { it.first == Market.US }.map { it.second.uppercase() }.distinct()
        if (tw.isNotEmpty()) {
            val got = runCatching { fetchTwse(tw) }.getOrDefault(emptyList())
            out += got
            val missing = tw - got.map { it.key.removePrefix("TW:") }.toSet()
            missing.forEach { sym ->
                (runCatching { fetchYahoo("$sym.TW", Market.TW, sym) }.getOrNull()
                    ?: runCatching { fetchYahoo("$sym.TWO", Market.TW, sym) }.getOrNull())?.let(out::add)
            }
        }
        us.forEach { sym -> runCatching { fetchYahoo(sym, Market.US, sym) }.getOrNull()?.let(out::add) }
        return out
    }

    /** 證交所即時報價（上市 tse、上櫃 otc 同時查詢）。 */
    private suspend fun fetchTwse(symbols: List<String>): List<QuoteEntity> {
        val channels = symbols.flatMap { listOf("tse_$it.tw", "otc_$it.tw") }.joinToString("|")
        val url = "https://mis.twse.com.tw/stock/api/getStockInfo.jsp?ex_ch=${URLEncoder.encode(channels, "UTF-8")}&json=1&delay=0"
        val root = Http.getJson(url, mapOf("Referer" to "https://mis.twse.com.tw/stock/index.jsp"))
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

    private suspend fun fetchYahoo(yahooSymbol: String, market: Market, symbol: String): QuoteEntity? {
        val root = Http.getJson("https://query1.finance.yahoo.com/v8/finance/chart/${URLEncoder.encode(yahooSymbol, "UTF-8")}?range=5d&interval=1d")
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
