package com.enwei.moneymanager.data

import com.enwei.moneymanager.core.Allocation
import com.enwei.moneymanager.core.Discipline
import com.enwei.moneymanager.core.DisciplineRule
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.MarketStage
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.StopAdvice
import com.enwei.moneymanager.core.Technicals
import com.enwei.moneymanager.core.TradeStyle
import com.enwei.moneymanager.net.DailyBars
import com.enwei.moneymanager.net.InvestData
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/** 一檔台股持股（同代號跨券商合併）與它的紀律建議。 */
data class HoldingPlan(
    val symbol: String,
    val name: String?,
    val shares: Double,
    val avgCost: Double,
    val price: Double?,
    val value: Long,
    val style: TradeStyle,
    val firstBuy: Long?,
    val peak: Double?,
    val advice: StopAdvice?,
) {
    val gainPct: Double? get() = price?.let { (it / avgCost - 1) * 100 }
}

/** 投資助理的現況：資金分配、持股紀律與大盤。 */
data class AdvisorSnapshot(
    val alloc: Allocation,
    val holdings: List<HoldingPlan>,
    val index: DailyBars?,
    val indexStage: MarketStage,
    val sox: DailyBars?,
    val soxStage: MarketStage,
)

fun AppSettings.styleOf(symbol: String): TradeStyle =
    stockStyle[symbol]?.let { s -> TradeStyle.entries.find { it.name == s } } ?: if (symbol.startsWith("00")) TradeStyle.LONG else TradeStyle.SHORT

fun InvestPlanSettings.rule() = DisciplineRule(shortStopPct, shortTrailPct, longTrailPct, takeProfitPct)

/** 日線快取（30 分鐘），畫面重組時不重抓。 */
private object BarCache {
    val map = ConcurrentHashMap<String, Pair<Long, DailyBars?>>()
    suspend fun get(symbol: String, range: String): DailyBars? {
        val key = "$symbol:$range"
        map[key]?.takeIf { System.currentTimeMillis() - it.first < 30 * 60_000 }?.let { return it.second }
        val b = runCatching { InvestData.bars(symbol, range) }.getOrNull()
        map[key] = System.currentTimeMillis() to b
        return b
    }
}

suspend fun Repository.advisorSnapshot(fetch: Boolean = true): AdvisorSnapshot = coroutineScope {
    val s = settings.value
    val conv = fx.first()
    val plan = s.investPlan
    // 可動用的現金：銀行、現金、錢包、證券戶現金，扣掉信用卡欠款
    val bal = balances.first().filter { !it.account.archived && it.account.includeInNetWorth }
    val cash = bal.filter {
        it.account.type in setOf(AccountType.BANK, AccountType.CASH, AccountType.EWALLET, AccountType.TRANSIT_CARD, AccountType.BROKERAGE)
    }.sumOf { conv.convert(it.balance, it.account.currency) } +
        bal.filter { it.account.type == AccountType.CREDIT_CARD }.sumOf { minOf(0, conv.convert(it.balance, it.account.currency)) }

    val raw = holdingsByBroker.first().values.flatten().filter { it.market == Market.TW && it.shares > 0.0001 }
    val grouped = raw.groupBy { it.symbol }
    val barsJobs = if (fetch) grouped.keys.associateWith { sym -> async { BarCache.get(sym, "1y") } } else emptyMap()
    val indexJob = if (fetch) async { BarCache.get("^TWII", "1y") } else null
    val soxJob = if (fetch) async { BarCache.get("^SOX", "1y") } else null

    val holdings = grouped.map { (sym, list) ->
        val shares = list.sumOf { it.shares }
        val cost = list.sumOf { it.avgCost * it.shares }
        val avg = if (shares > 0) cost / shares else 0.0
        val price = list.firstNotNullOfOrNull { it.price }
        val first = list.flatMap { it.lots }.minOfOrNull { it.epochDay }
        val bars = barsJobs[sym]?.await()
        val peak = first?.let { bars?.peakSince(it) } ?: bars?.high?.takeLast(60)?.maxOrNull()
        val style = s.styleOf(sym)
        HoldingPlan(
            sym, list.firstNotNullOfOrNull { it.name } ?: bars?.name, shares, avg, price,
            conv.convert(Money.toMinor(list.sumOf { it.marketValue }), list.first().currency), style, first, peak,
            price?.let { p -> Discipline.advise(style, avg, p, peak ?: p, plan.rule()) },
        )
    }.sortedByDescending { it.value }
    val longValue = holdings.filter { it.style == TradeStyle.LONG }.sumOf { it.value }
    val shortValue = holdings.filter { it.style == TradeStyle.SHORT }.sumOf { it.value }
    val index = indexJob?.await()
    val sox = soxJob?.await()
    AdvisorSnapshot(
        Allocation.of(cash, plan.reserve, longValue, shortValue, plan.longRatio), holdings,
        index, index?.close?.let(Technicals::stage) ?: MarketStage.UNKNOWN,
        sox, sox?.close?.let(Technicals::stage) ?: MarketStage.UNKNOWN,
    )
}

private fun describe(name: String, b: DailyBars?, stage: MarketStage): String {
    if (b == null) return "$name：讀取失敗"
    val c = b.close
    fun f(v: Double?) = v?.let { "%.0f".format(it) } ?: "?"
    fun p(v: Double?) = v?.let { "%+.1f%%".format(it) } ?: "?"
    return "$name 收 ${f(c.last())}，5 日 ${p(Technicals.change(c, 5))}、20 日 ${p(Technicals.change(c, 20))}、60 日 ${p(Technicals.change(c, 60))}；" +
        "月線 ${f(Technicals.sma(c, 20))}、季線 ${f(Technicals.sma(c, 60))}、RSI ${f(Technicals.rsi(c))}；規則判斷：${stage.label}"
}

/**
 * 讀取大盤、新聞並請 AI 依使用者的紀律與資金計畫做分析。[onStep] 回報目前進度。
 * AI 推薦的代號會再查一次報價，查不到的標示為未確認。
 */
suspend fun Repository.runAdvisor(onStep: (String) -> Unit): AdvisorReport? {
    val s = settings.value
    onStep("讀取大盤與持股走勢…")
    val snap = advisorSnapshot(fetch = true)
    onStep("讀取新聞…")
    val queries = listOf("台股 大盤", "台股 外資 買超", "美股 費半 科技股", "台股 產業 題材") +
        snap.holdings.take(5).map { "${it.symbol} ${it.name.orEmpty()}".trim() }
    val news = runCatching { InvestData.headlines(queries, perQuery = 8, max = 35) }.getOrDefault(emptyList())
    onStep("AI 分析中（讀了 ${news.size} 則新聞）…")
    val a = snap.alloc
    fun m(v: Long) = Money.format(v, "TWD")
    val user = buildString {
        appendLine("今天：${LocalDate.now()}")
        appendLine(describe("加權指數", snap.index, snap.indexStage))
        appendLine(describe("費城半導體", snap.sox, snap.soxStage))
        appendLine()
        appendLine("資金計畫：現金 ${m(a.cash)}，保留生活費 ${m(a.reserve)}，可投資 ${m(a.investable)}（長線 ${"%.0f".format(s.investPlan.longRatio * 100)}%）")
        appendLine("長線：目標 ${m(a.longTarget)}，已投入 ${m(a.longValue)}，可再投入 ${m(a.longRoom)}")
        appendLine("短線：目標 ${m(a.shortTarget)}，已投入 ${m(a.shortValue)}，可再投入 ${m(a.shortRoom)}" + if (a.shortOver > 0) "（超出 ${m(a.shortOver)}）" else "")
        appendLine()
        appendLine("持股（代號 名稱｜股性｜股數｜均價｜現價｜損益%｜買進後最高｜規則建議）：")
        snap.holdings.forEach { h ->
            appendLine(
                "- ${h.symbol} ${h.name.orEmpty()}｜${h.style.label}｜${"%.0f".format(h.shares)}｜${"%.2f".format(h.avgCost)}｜${h.price ?: "?"}｜" +
                    "${h.gainPct?.let { "%+.1f".format(it) } ?: "?"}｜${h.peak?.let { "%.2f".format(it) } ?: "?"}｜" +
                    (h.advice?.let { "${it.action}，停損 ${"%.2f".format(it.stop)}" } ?: "?"),
            )
        }
        if (snap.holdings.isEmpty()) appendLine("（目前沒有持股）")
        appendLine()
        appendLine("使用者的交易紀律：")
        s.investPlan.rules.forEach { appendLine("- $it") }
        appendLine()
        appendLine("近兩天新聞標題：")
        news.forEachIndexed { i, h -> appendLine("${i + 1}. ${h.title.take(40)}") }
    }
    val report = com.enwei.moneymanager.net.GroqAi.advise(user) ?: return null
    onStep("確認推薦的代號…")
    val ideaQuotes = runCatching {
        com.enwei.moneymanager.net.QuoteService.fetch(report.ideas.map { Market.TW to it.symbol })
    }.getOrDefault(emptyList()).associateBy { it.key }
    val verified = report.ideas.map { idea ->
        val q = ideaQuotes[com.enwei.moneymanager.data.quoteKey(Market.TW, idea.symbol)]
        idea.copy(price = q?.price, name = idea.name.ifBlank { q?.name.orEmpty() })
    }
    val final = report.copy(ideas = verified, news = news.map { "${it.title}（${it.source}）" }, createdAt = System.currentTimeMillis())
    settings.update { it.copy(advisorReport = final) }
    return final
}
