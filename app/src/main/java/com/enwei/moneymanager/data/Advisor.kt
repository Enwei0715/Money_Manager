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
    /** 保本＋移動停利：計算用的輸入（盤中提醒換上即時價重算）與建議 */
    val guardInput: com.enwei.moneymanager.core.GuardInput? = null,
    val guard: com.enwei.moneymanager.core.GuardAdvice? = null,
    /** 保本金額是不是自動算的（這一輪買進的總成本） */
    val autoPrincipal: Boolean = true,
    /** 日 K 均線（5、10、20、72、100、200）的位置 */
    val trend: com.enwei.moneymanager.core.ProfitGuard.MaTrend? = null,
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

fun InvestPlanSettings.guardRule() = com.enwei.moneymanager.core.GuardRule(guardTrailPct, baseShares)

/**
 * 自動的保本金額：這一輪（上次賣光之後）所有買進的成本加手續費。
 * 賣出多賺的股數不會減少保本金額——成本要一直留在場內；減碼賣一半後可以在畫面上改成新的金額。
 */
fun autoPrincipal(trades: List<StockTradeEntity>): Double {
    var held = 0.0
    var sum = 0.0
    trades.sortedWith(compareBy<StockTradeEntity> { it.date }.thenBy { it.id }).forEach { t ->
        when (t.side) {
            com.enwei.moneymanager.core.StockSide.BUY -> { held += t.shares; sum += t.shares * t.price + t.fee }
            com.enwei.moneymanager.core.StockSide.STOCK_DIVIDEND -> held += t.shares
            com.enwei.moneymanager.core.StockSide.SELL -> { held -= t.shares; if (held <= 0.0001) { held = 0.0; sum = 0.0 } }
            else -> {}
        }
    }
    return sum
}

/** 日線快取（30 分鐘），畫面重組時不重抓。 */
object BarCache {
    val map = ConcurrentHashMap<String, Pair<Long, DailyBars?>>()
    /** 日線快取 30 分鐘；60 分 K 盤中變化快，快取 10 分鐘 */
    suspend fun get(symbol: String, range: String, interval: String = "1d"): DailyBars? {
        val key = "$symbol:$range:$interval"
        val ttl = if (interval == "1d") 30 * 60_000 else 10 * 60_000
        map[key]?.takeIf { System.currentTimeMillis() - it.first < ttl }?.let { return it.second }
        val b = runCatching { InvestData.bars(symbol, range, interval = interval) }.getOrNull()
        map[key] = System.currentTimeMillis() to b
        return b
    }
}

suspend fun Repository.advisorSnapshot(fetch: Boolean = true): AdvisorSnapshot = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { coroutineScope {
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
    // 保本＋移動停利要看 60 分 K 的 20MA、50MA
    val hourJobs = if (fetch && plan.guardOn) grouped.keys.associateWith { sym -> async { BarCache.get(sym, "1mo", "60m") } } else emptyMap()
    val allTrades = trades.first().filter { it.market == Market.TW }
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
        val gs = plan.guards[sym]
        val hour = hourJobs[sym]?.await()?.close
        val gi = price?.let { p ->
            com.enwei.moneymanager.core.GuardInput(
                shares, p, gs?.principal ?: autoPrincipal(allTrades.filter { it.symbol.equals(sym, true) }),
                gs?.peak, gs?.takeProfit, gs?.reduceAt,
                hour?.let { Technicals.sma(it, 20) }, hour?.let { Technicals.sma(it, 50) },
            )
        }
        HoldingPlan(
            sym, list.firstNotNullOfOrNull { it.name } ?: bars?.name, shares, avg, price,
            conv.convert(Money.toMinor(list.sumOf { it.marketValue }), list.first().currency), style, first, peak,
            price?.let { p -> Discipline.advise(style, avg, p, peak ?: p, plan.rule()) },
            gi, gi?.let { com.enwei.moneymanager.core.ProfitGuard.advise(it, plan.guardRule()) }, gs?.principal == null,
            bars?.close?.let { com.enwei.moneymanager.core.ProfitGuard.dailyTrend(it) },
        )
    }.sortedByDescending { it.value }
    // 記下移動停利的最高價（下次從這裡繼續往上移）
    if (plan.guardOn) saveGuardPeaks(holdings.mapNotNull { h -> h.guard?.let { h.symbol to it.peak } }.toMap())
    val longValue = holdings.filter { it.style == TradeStyle.LONG }.sumOf { it.value }
    val shortValue = holdings.filter { it.style == TradeStyle.SHORT }.sumOf { it.value }
    val index = indexJob?.await()
    val sox = soxJob?.await()
    AdvisorSnapshot(
        Allocation.of(cash, plan.reserve, longValue, shortValue, plan.longRatio), holdings,
        index, index?.close?.let(Technicals::stage) ?: MarketStage.UNKNOWN,
        sox, sox?.close?.let(Technicals::stage) ?: MarketStage.UNKNOWN,
    )
}}

private val citeRe = Regex("\\s*[\\[［【]\\s*\\d{1,3}(\\s*[,，、]\\s*\\d{1,3})*\\s*[\\]］】]")

/** AI 文字裡引用的新聞編號（例如「[19, 35]」）。 */
fun citeNumbers(text: String): List<Int> = citeRe.findAll(text).flatMap { m -> Regex("\\d{1,3}").findAll(m.value).map { it.value.toInt() } }.toList()

/** 拿掉 AI 文字裡的新聞編號引用。 */
fun stripCites(text: String): String = text.replace(citeRe, "")

/** 更新各檔移動停利的最高價（有變才寫入）。 */
fun Repository.saveGuardPeaks(peaks: Map<String, Double?>) {
    val cur = settings.value.investPlan.guards
    if (peaks.all { (k, v) -> cur[k]?.peak == v }) return
    settings.update { st ->
        val g = st.investPlan.guards.toMutableMap()
        peaks.forEach { (k, v) -> g[k] = (g[k] ?: GuardState()).copy(peak = v) }
        st.copy(investPlan = st.investPlan.copy(guards = g))
    }
}

private fun describe(name: String, b: DailyBars?, stage: MarketStage): String {
    if (b == null) return "$name：讀取失敗"
    val c = b.close
    fun f(v: Double?) = v?.let { "%.0f".format(it) } ?: "?"
    fun p(v: Double?) = v?.let { "%+.1f%%".format(it) } ?: "?"
    return "$name 收 ${f(c.last())}，5 日 ${p(Technicals.change(c, 5))}、20 日 ${p(Technicals.change(c, 20))}、60 日 ${p(Technicals.change(c, 60))}；" +
        "月線 ${f(Technicals.sma(c, 20))}、季線 ${f(Technicals.sma(c, 60))}、RSI ${f(Technicals.rsi(c))}；規則判斷：${stage.label}"
}

/** 給 AI 的使用者狀況：大盤、資金計畫、持股與紀律（每日報告與對話共用）。 */
private fun Repository.situation(snap: AdvisorSnapshot): String {
    val s = settings.value
    val a = snap.alloc
    fun m(v: Long) = Money.format(v, "TWD")
    return buildString {
        appendLine("今天：${LocalDate.now()}")
        appendLine(describe("加權指數", snap.index, snap.indexStage))
        appendLine(describe("費城半導體", snap.sox, snap.soxStage))
        appendLine("資金計畫：現金 ${m(a.cash)}，保留生活費 ${m(a.reserve)}，可投資 ${m(a.investable)}（長線 ${"%.0f".format(s.investPlan.longRatio * 100)}%）；" +
            "長線已投入 ${m(a.longValue)}／目標 ${m(a.longTarget)}，可再投入 ${m(a.longRoom)}；短線已投入 ${m(a.shortValue)}／目標 ${m(a.shortTarget)}，可再投入 ${m(a.shortRoom)}")
        appendLine("持股（代號 名稱｜股性｜股數｜均價｜現價｜損益%｜規則建議）：" + if (snap.holdings.isEmpty()) "無" else "")
        snap.holdings.forEach { h ->
            appendLine(
                "- ${h.symbol} ${h.name.orEmpty()}｜${h.style.label}｜${"%.0f".format(h.shares)}｜${"%.2f".format(h.avgCost)}｜${h.price ?: "?"}｜" +
                    "${h.gainPct?.let { "%+.1f".format(it) } ?: "?"}｜" + (h.advice?.let { "${it.action}，停損 ${"%.2f".format(it.stop)}" } ?: "?"),
            )
        }
        if (s.investPlan.guardOn) {
            appendLine("保本＋移動停利（App 依紀律算好的，請以此為準給建議）：")
            snap.holdings.forEach { h ->
                val g = h.guard ?: return@forEach
                val gi = h.guardInput ?: return@forEach
                appendLine(
                    "- ${h.symbol}：保本 ${"%,.0f".format(gi.principal)}、市值 ${"%,.0f".format(gi.shares * gi.price)}、多賺 ${g.excessShares.toLong()} 股、" +
                        "停利價 ${g.takeProfit?.let { "%.2f".format(it) } ?: "無"}、60分K 20MA ${gi.hourMa20?.let { "%.2f".format(it) } ?: "?"}／50MA ${gi.hourMa50?.let { "%.2f".format(it) } ?: "?"}、" +
                        "日均線：${h.trend?.summary ?: "?"}；建議：${g.action.label}（${g.note}）",
                )
            }
        }
        appendLine("紀律：" + s.investPlan.rules.joinToString("；"))
    }
}

/** 資料集摘要：題材熱度、個股統計（附最新一則標題）、重點新聞（編號 → 新聞 id）。 */
private fun datasetDigest(ds: NewsDataset, st: NewsStore, themes: Int, symbols: Int, important: Int, full: Boolean = false): Pair<String, List<NewsItem>> {
    val recent = ds.recent(st, 3)
    val imp = ds.important(st, 2, important)
    val text = buildString {
        appendLine("新聞資料集：近 3 天已標註 ${recent.size} 則（共收 ${st.items.size} 則）")
        appendLine("題材熱度（則數｜平均多空 -2~2｜重大則數）：")
        ds.themes(st).take(themes).forEach { appendLine("- ${it.key}｜${it.count}｜${"%+.1f".format(it.avgSentiment)}｜${it.important}") }
        appendLine("被提到最多的個股（代號｜則數｜多空｜最新標題）：")
        ds.symbols(st).take(symbols).forEach { t ->
            val heads = recent.filter { t.key in it.symbols }.take(if (full) 3 else 1).joinToString(" / ") { it.title.take(if (full) 40 else 24) }
            appendLine("- ${t.key}｜${t.count}｜${"%+.1f".format(t.avgSentiment)}｜$heads")
        }
        // 新聞重點庫：所有新聞依題材整理後的重點（同一事件只寫一次）
        val byTheme = st.digests.values.groupBy { it.theme }.entries.sortedByDescending { e -> e.value.sumOf { it.count } }
        if (byTheme.isNotEmpty()) {
            appendLine("新聞重點庫（依題材；每點後為相關代號、多空 -2~2、幾則新聞提到）：")
            byTheme.take(if (full) 30 else 8).forEach { (theme, days) ->
                appendLine("【$theme】")
                days.sortedByDescending { it.day }.take(if (full) 3 else 1).forEach { d ->
                    d.points.take(if (full) 8 else 3).forEach { p ->
                        appendLine("- ${d.day.substring(5)} ${p.text}" + (if (p.symbols.isNotEmpty()) "｜${p.symbols.joinToString(",")}" else "") + "｜${p.sentiment}｜${p.count}則")
                    }
                }
            }
        }
        appendLine("重點新聞：")
        imp.forEachIndexed { i, n -> appendLine("[$i] ${n.title.take(45)}" + (n.note.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")) }
        // 讀得下大量資料時（Gemini）：附上近 3 天所有新聞標題，依題材分組
        if (full) {
            appendLine("近 3 天所有新聞（依題材；括號為多空 -2~2）：")
            recent.groupBy { it.theme.ifBlank { "其他" } }.entries.sortedByDescending { it.value.size }.forEach { (theme, list) ->
                appendLine("【$theme】")
                list.take(40).forEach { n -> appendLine("- ${n.title.take(60)}（${n.sentiment}）" + if (n.symbols.isNotEmpty()) " ${n.symbols.joinToString(",")}" else "") }
            }
        }
    }
    return text to imp
}

/** 背景蒐集與標註新聞（每天數次）；回傳新增則數。 */
suspend fun Repository.refreshNews(
    ds: NewsDataset, onStep: (String) -> Unit = {}, onBatch: (Int, Int) -> Unit = { _, _ -> }, onDigest: (Int, Int) -> Unit = { _, _ -> },
): Int {
    val holdings = holdingsByBroker.first().values.flatten().filter { it.market == Market.TW && it.shares > 0.0001 }
    val watch = ds.load().report?.picks.orEmpty().map { "${it.symbol} ${it.name}" }
    val extra = (holdings.map { "${it.symbol} ${it.name.orEmpty()}".trim() } + watch).distinct().take(15)
    onStep("蒐集新聞…")
    val added = ds.collect(extra)
    if (com.enwei.moneymanager.net.GroqAi.enabled) {
        ds.tag { i, n -> onBatch(i, n); onStep("AI 標註新聞 ${i + 1}/$n 批…") }
        // 把全部新聞整理成每天、每個題材的重點
        ds.digest { i, n -> onDigest(i, n); onStep("整理新聞重點 ${i + 1}/$n…") }
    }
    return added
}

/**
 * 每日投資報告：先確保資料集夠完整（不夠就先蒐集、標註），再把整理過的題材、個股與重點新聞
 * 加上大盤、資金計畫、持股與紀律交給進階模型。推薦的代號會再查報價確認。
 */
suspend fun Repository.buildDailyReport(ds: NewsDataset, onStep: (String) -> Unit): DailyReport? {
    var st = ds.load()
    if (System.currentTimeMillis() - st.lastCollect > 3 * 3600_000L || ds.recent(st, 3).size < 300) {
        refreshNews(ds, onStep)
        st = ds.load()
    }
    onStep("讀取大盤與持股走勢…")
    val snap = advisorSnapshot(fetch = true)
    val big = com.enwei.moneymanager.net.GroqAi.bigContext(com.enwei.moneymanager.net.GroqAi.AiTask.REPORT)
    val (digest, imp) = if (big) datasetDigest(ds, st, themes = 30, symbols = 60, important = 60, full = true)
    else datasetDigest(ds, st, themes = 10, symbols = 18, important = 15)
    onStep("AI 分析資料集（${ds.recent(st, 3).size} 則新聞）…")
    val r = com.enwei.moneymanager.net.GroqAi.dailyReport(situation(snap) + "\n" + digest) ?: return null
    onStep("確認推薦的代號與價格…")
    val quotes = runCatching { com.enwei.moneymanager.net.QuoteService.fetch(r.picks.map { Market.TW to it.symbol }) }
        .getOrDefault(emptyList()).associateBy { it.key }
    val picks = r.picks.map { p ->
        val q = quotes[quoteKey(Market.TW, p.symbol)]
        p.copy(price = q?.price, name = p.name.ifBlank { q?.name.orEmpty() })
    }
    val keyNews = r.keyNews.mapNotNull { k -> k.id.toIntOrNull()?.let { imp.getOrNull(it) }?.let { KeyNews(it.id, k.why) } }
    val report = r.copy(picks = picks, keyNews = keyNews, createdAt = System.currentTimeMillis(), day = LocalDate.now().toEpochDay(), newsCount = ds.recent(st, 3).size)
    ds.update { it.copy(report = report, history = (listOfNotNull(it.report) + it.history).take(400), business = it.business + picks.filter { p -> p.business.isNotBlank() }.associate { p -> p.symbol to p.business }).prunedHistory() }
    return report
}

/** 投資助理對話：帶入使用者狀況與資料集摘要，回覆可附股票卡片與新聞。 */
suspend fun Repository.askAdvisor(ds: NewsDataset, question: String): ChatMsg? {
    val st = ds.load()
    ds.update { it.copy(chat = (it.chat + ChatMsg(true, question)).takeLast(40)) }
    val snap = advisorSnapshot(fetch = true)
    val big = com.enwei.moneymanager.net.GroqAi.bigContext(com.enwei.moneymanager.net.GroqAi.AiTask.CHAT)
    val (digest, imp) = if (big) datasetDigest(ds, st, themes = 30, symbols = 50, important = 40, full = true)
    else datasetDigest(ds, st, themes = 8, symbols = 12, important = 10)
    val today = st.report?.let { r -> "今日報告：${r.trend}；推薦 " + r.picks.joinToString("、") { "${it.symbol}${it.name}（${it.style}）" } } ?: ""
    val history = st.chat.takeLast(6).map { it.fromUser to it.text }
    val reply = com.enwei.moneymanager.net.GroqAi.advisorChat(situation(snap) + "\n" + today + "\n" + digest, history, question) ?: return null
    // 回覆裡的 [19, 35] 是新聞編號：拿掉，改成附在回覆下方的新聞
    val cited = citeNumbers(reply.text)
    val fixed = reply.copy(
        text = stripCites(reply.text),
        newsIds = (reply.newsIds.mapNotNull { it.toIntOrNull() } + cited).distinct().mapNotNull { i -> imp.getOrNull(i)?.id },
    )
    ds.update { it.copy(chat = (it.chat + fixed).takeLast(40), business = it.business + fixed.cards.filter { c -> c.business.isNotBlank() }.associate { c -> c.symbol to c.business }) }
    return fixed
}
