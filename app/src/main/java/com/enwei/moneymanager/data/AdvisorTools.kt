package com.enwei.moneymanager.data

import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.net.MarketScan
import com.enwei.moneymanager.net.TwData
import com.enwei.moneymanager.net.TwMarket
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 問 AI 的工具：AI 先規劃要查什麼，App 真的去查（全市場報價、估值、營收、法人、類股、個股、自己的支出），
 * 把結果整理成短短的文字表格交給回答的 AI。AI 只能根據這些實際數字回答，不會亂編股票清單。
 */
object AdvisorTools {
    /** 一次工具查詢的結果：給 AI 看的文字、可以做成卡片／圖表的代號、給使用者看的說明 */
    data class Result(val text: String, val symbols: List<String>, val label: String)

    private fun Map<String, String>.num(k: String): Double? = this[k]?.replace(",", "")?.trim()?.toDoubleOrNull()
    private fun Map<String, String>.lim(def: Int = 12) = (num("limit")?.toInt() ?: def).coerceIn(3, 20)
    private fun f1(v: Double?) = v?.let { "%.1f".format(it) } ?: "—"
    private fun f2(v: Double) = if (v >= 100) "%.1f".format(v) else "%.2f".format(v)
    private fun isEtf(r: MarketScan.Row) = "ETF" in r.industry || r.code.startsWith("00")

    /** AI 沒規劃到（或規劃失敗）時的保底：從問題的字面直接判斷要查什麼。 */
    fun fallbackPlan(q: String): List<GroqAi.ToolCall> = buildList {
        val priceMax = when {
            Regex("個位數|10\\s*元以下|十元以下|銅板").containsMatchIn(q) -> "10"
            Regex("(\\d{2,4})\\s*元以下").find(q) != null -> Regex("(\\d{2,4})\\s*元以下").find(q)!!.groupValues[1]
            Regex("百元以下|100\\s*元以下|低價股").containsMatchIn(q) -> "100"
            else -> null
        }
        if (priceMax != null) add(GroqAi.ToolCall("screen", mapOf("price_max" to priceMax, "sort" to "value", "etf" to "no", "limit" to "15")))
        if (Regex("殖利率|存股|高股息|本益比").containsMatchIn(q)) add(GroqAi.ToolCall("valuation", mapOf("yield_min" to "5", "sort" to "yield", "limit" to "15")))
        if (Regex("營收.*(成長|創新高|年增)|成長股").containsMatchIn(q)) add(GroqAi.ToolCall("revenue", mapOf("yoy_min" to "20", "sort" to "yoy", "limit" to "15")))
        if (Regex("外資|投信|法人").containsMatchIn(q)) add(GroqAi.ToolCall("institutional", mapOf("days" to "5",
            "who" to if ("投信" in q) "trust" else if ("外資" in q) "foreign" else "total", "side" to if (Regex("賣超|賣").containsMatchIn(q)) "sell" else "buy")))
        if (Regex("類股|產業|族群|哪個產業").containsMatchIn(q)) add(GroqAi.ToolCall("sectors", emptyMap()))
        if (Regex("漲停|漲最多|強勢股").containsMatchIn(q)) add(GroqAi.ToolCall("screen", mapOf("sort" to "pct", "etf" to "no", "limit" to "15")))
        if (Regex("跌停|跌最多|弱勢股").containsMatchIn(q)) add(GroqAi.ToolCall("screen", mapOf("sort" to "-pct", "etf" to "no", "limit" to "15")))
        if (Regex("我.*(花|支出|開銷)|這個月花").containsMatchIn(q)) add(GroqAi.ToolCall("spending", mapOf("month" to if ("上個月" in q) "1" else "0")))
    }

    suspend fun run(c: GroqAi.ToolCall, repo: Repository): Result? = runCatching {
        when (c.tool) {
            "screen" -> screen(c.args)
            "valuation" -> valuation(c.args)
            "revenue" -> revenue(c.args)
            "institutional" -> institutional(c.args)
            "sectors" -> sectors()
            "stock" -> stock(c.args["symbol"].orEmpty())
            "spending" -> spending(repo, c.args.num("month")?.toInt() ?: 0)
            else -> null
        }
    }.getOrNull()

    // 全市場報價：同一次問答會用好幾次，快取 2 分鐘
    private var boardCache: Pair<Long, List<MarketScan.Row>>? = null
    private suspend fun board(): List<MarketScan.Row> {
        boardCache?.let { (t, rows) -> if (System.currentTimeMillis() - t < 120_000 && rows.isNotEmpty()) return rows }
        return MarketScan.board().also { boardCache = System.currentTimeMillis() to it }
    }

    private suspend fun screen(a: Map<String, String>): Result? {
        val rows = board().filter { r ->
            r.close > 0 &&
                (a.num("price_min")?.let { r.close >= it } ?: true) && (a.num("price_max")?.let { r.close <= it } ?: true) &&
                (a.num("pct_min")?.let { r.pct >= it } ?: true) && (a.num("pct_max")?.let { r.pct <= it } ?: true) &&
                (a["industry"]?.takeIf { it.isNotBlank() }?.let { k -> r.industry.contains(k) || MarketScan.shortIndustry(r.industry).contains(k) } ?: true) &&
                when (a["etf"]) { "yes" -> isEtf(r); "no" -> !isEtf(r); else -> true }
        }
        val sorted = when (a["sort"]) {
            "pct" -> rows.sortedByDescending { it.pct }; "-pct" -> rows.sortedBy { it.pct }
            "price" -> rows.sortedByDescending { it.close }; "-price" -> rows.sortedBy { it.close }
            else -> rows.sortedByDescending { it.value }
        }.take(a.lim(15))
        val cond = listOfNotNull(a["price_min"]?.let { "股價 ≥ $it" }, a["price_max"]?.let { "股價 ≤ $it" }, a["pct_min"]?.let { "漲跌 ≥ $it%" },
            a["pct_max"]?.let { "漲跌 ≤ $it%" }, a["industry"]?.takeIf { it.isNotBlank() }, if (a["etf"] == "yes") "只看 ETF" else if (a["etf"] == "no") "排除 ETF" else null).joinToString("、")
        val text = "【全市場篩選】條件：${cond.ifBlank { "無" }}；符合 ${rows.size} 檔，列出前 ${sorted.size} 檔（代號 名稱｜產業｜收盤｜漲跌%｜成交值億）：\n" +
            sorted.joinToString("\n") { "${it.code} ${it.name}｜${MarketScan.shortIndustry(it.industry)}｜${f2(it.close)}｜${"%+.2f".format(it.pct)}｜${"%.1f".format(it.value / 1e8)}" }
        return Result(text, sorted.map { it.code }, "篩選全市場 ${rows.size} 檔")
    }

    private suspend fun valuation(a: Map<String, String>): Result? {
        val v = TwMarket.valuation()
        val names = board().associateBy { it.code }
        val rows = v.entries.filter { (code, x) ->
            names[code]?.let { !isEtf(it) } == true &&
                (a.num("pe_max")?.let { x.pe != null && x.pe <= it } ?: true) && (a.num("pe_min")?.let { x.pe != null && x.pe >= it } ?: true) &&
                (a.num("yield_min")?.let { (x.yield ?: 0.0) >= it } ?: true) && (a.num("pb_max")?.let { x.pb != null && x.pb <= it } ?: true)
        }
        val sorted = when (a["sort"]) {
            "pe" -> rows.sortedBy { it.value.pe ?: 999.0 }; "pb" -> rows.sortedBy { it.value.pb ?: 999.0 }
            else -> rows.sortedByDescending { it.value.yield ?: 0.0 }
        }.take(a.lim(15))
        val text = "【估值篩選】符合 ${rows.size} 檔（代號 名稱｜收盤｜本益比｜殖利率%｜股價淨值比）：\n" + sorted.joinToString("\n") { (c, x) ->
            "$c ${names[c]?.name ?: TwData.nameOf(c).orEmpty()}｜${names[c]?.close?.let { f2(it) } ?: "—"}｜${f1(x.pe)}｜${f1(x.yield)}｜${f1(x.pb)}"
        }
        return Result(text, sorted.map { it.key }, "依本益比、殖利率篩選 ${rows.size} 檔")
    }

    private suspend fun revenue(a: Map<String, String>): Result? {
        val rv = TwMarket.revenueAll()
        val names = board().associateBy { it.code }
        val rows = rv.entries.filter { (c, r) ->
            names[c] != null && (a.num("yoy_min")?.let { (r.yoy ?: -999.0) >= it } ?: true) && (a.num("mom_min")?.let { (r.mom ?: -999.0) >= it } ?: true)
        }
        val sorted = (if (a["sort"] == "mom") rows.sortedByDescending { it.value.mom ?: -999.0 } else rows.sortedByDescending { it.value.yoy ?: -999.0 })
            // 營收基期太小（年增上千 %）的排到後面，避免全是雞蛋水餃股
            .sortedBy { if ((it.value.yoy ?: 0.0) > 500) 1 else 0 }.take(a.lim(15))
        val ym = sorted.firstOrNull()?.value?.let { "${it.year}/${it.month}" }.orEmpty()
        val text = "【營收篩選】$ym 營收，符合 ${rows.size} 檔（代號 名稱｜收盤｜年增%｜月增%）：\n" + sorted.joinToString("\n") { (c, r) ->
            "$c ${names[c]?.name.orEmpty()}｜${names[c]?.close?.let { f2(it) } ?: "—"}｜${f1(r.yoy)}｜${f1(r.mom)}"
        }
        return Result(text, sorted.map { it.key }, "依營收篩選 ${rows.size} 檔")
    }

    private suspend fun institutional(a: Map<String, String>): Result? {
        val days = (a.num("days")?.toInt() ?: 5).coerceIn(1, 10)
        val inst = TwMarket.institutional(days)
        val names = board().associateBy { it.code }
        fun amt(l: List<TwMarket.Inst>) = when (a["who"]) { "foreign" -> l.sumOf { it.foreign }; "trust" -> l.sumOf { it.trust }; else -> l.sumOf { it.total } }
        val sell = a["side"] == "sell"
        val sorted = inst.entries.filter { names[it.key] != null }.map { it.key to amt(it.value.takeLast(days)) }
            .sortedBy { if (sell) it.second else -it.second }.take(a.lim(15))
        val who = when (a["who"]) { "foreign" -> "外資"; "trust" -> "投信"; else -> "三大法人" }
        val text = "【$who 近 $days 日${if (sell) "賣超" else "買超"}排行】（代號 名稱｜收盤｜張數）：\n" + sorted.joinToString("\n") { (c, v) ->
            "$c ${names[c]?.name.orEmpty()}｜${names[c]?.close?.let { f2(it) } ?: "—"}｜${"%+,d".format(v / 1000)}"
        }
        return Result(text, sorted.map { it.first }, "$who 近 $days 日${if (sell) "賣超" else "買超"}排行")
    }

    private suspend fun sectors(): Result? {
        val s = MarketScan.sectors(board()).sortedByDescending { it.pct }
        val text = "【今日類股】（產業｜家數｜平均漲跌%｜上漲比例｜成交值億）：\n" + s.joinToString("\n") {
            "${it.name}｜${it.n}｜${"%+.2f".format(it.pct)}｜${"%.0f".format(it.upRatio * 100)}%｜${"%.0f".format(it.value / 1e8)}"
        }
        return Result(text, emptyList(), "今日 ${s.size} 個類股排行")
    }

    private suspend fun stock(q: String): Result? {
        val code = Regex("[0-9]{4,6}[A-Z]?").find(q)?.value ?: TwData.cached().firstOrNull { it.name.length >= 2 && q.contains(it.name) }?.code ?: return null
        val row = board().firstOrNull { it.code == code }
        val v = TwMarket.valuation()[code]
        val r = TwMarket.revenueAll()[code]
        val inst = TwMarket.institutional(5)[code].orEmpty()
        val s = MarketScan.strength(listOf(code), null)[code]
        val text = "【個股 $code ${row?.name ?: TwData.nameOf(code).orEmpty()}】產業 ${row?.industry.orEmpty()}；收盤 ${row?.close?.let { f2(it) } ?: "—"}（今日 ${row?.pct?.let { "%+.2f".format(it) } ?: "—"}%）；" +
            "本益比 ${f1(v?.pe)}、殖利率 ${f1(v?.yield)}%、股價淨值比 ${f1(v?.pb)}；" +
            (r?.let { "${it.year}/${it.month} 營收年增 ${f1(it.yoy)}%、月增 ${f1(it.mom)}%；" } ?: "") +
            (if (inst.isNotEmpty()) "法人近 5 日 外資 ${"%+,d".format(inst.sumOf { it.foreign } / 1000)} 張、投信 ${"%+,d".format(inst.sumOf { it.trust } / 1000)} 張；" else "") +
            (s?.let { "近 5 日 ${"%+.1f".format(it.ret5)}%、20 日 ${"%+.1f".format(it.ret20)}%，${if (it.aboveMa20) "站上" else "跌破"}月線、${if (it.aboveMa60) "站上" else "跌破"}季線，距半年高點 ${"%.1f".format(it.offHigh)}%" } ?: "")
        return Result(text, listOf(code), "查詢 $code 的完整資料")
    }

    private suspend fun spending(repo: Repository, back: Int): Result? {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now().withDayOfMonth(1).minusMonths(back.toLong())
        val end = start.plusMonths(1)
        val fx = repo.fx.first()
        val cats = repo.categories.first().associateBy { it.id }
        val txns = repo.allTxns().filter {
            val d = Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate()
            it.type == TxnType.EXPENSE && it.status == TxnStatus.CONFIRMED && !d.isBefore(start) && d.isBefore(end)
        }
        val total = txns.sumOf { fx.convert(it.amount, it.currency) }
        val by = txns.groupBy { t -> cats[t.categoryId]?.let { it.parentId ?: it.id } }
            .map { (id, l) -> (cats[id]?.name ?: "未分類") to l.sumOf { fx.convert(it.amount, it.currency) } }.sortedByDescending { it.second }
        val text = "【${start.monthValue} 月支出】共 ${Money.full(total)} 元、${txns.size} 筆；依分類：" + by.joinToString("、") { (n, v) -> "$n ${Money.full(v)}" }
        return Result(text, emptyList(), "讀取 ${start.monthValue} 月的記帳")
    }
}
