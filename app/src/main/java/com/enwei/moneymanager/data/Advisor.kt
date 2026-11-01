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
        map[key]?.takeIf { System.currentTimeMillis() - it.first < ttl && it.second != null }?.let { return it.second }
        val first = runCatching { InvestData.bars(symbol, range, interval = interval) }.getOrNull()
        // 讀不到或天數太少（判斷大盤階段至少要 65 天）：換更長的期間再試一次
        val longer = if (interval == "1d" && (first == null || first.close.size < 65) && range != "2y")
            runCatching { InvestData.bars(symbol, "2y", interval = interval) }.getOrNull() else null
        val b = if (longer != null && longer.close.size > (first?.close?.size ?: 0)) longer else first
        // 失敗時不把空結果存起來（之前會讓「資料不足」持續 30 分鐘），沿用上一次讀到的資料
        if (b == null) return map[key]?.second
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
    val hourJobs = if (fetch && plan.disciplineOn && plan.guardOn) grouped.keys.associateWith { sym -> async { BarCache.get(sym, "1mo", "60m") } } else emptyMap()
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
            price?.takeIf { plan.disciplineOn }?.let { p -> Discipline.advise(style, avg, p, peak ?: p, plan.rule()) },
            gi.takeIf { plan.disciplineOn && plan.guardOn },
            gi?.takeIf { plan.disciplineOn && plan.guardOn }?.let { com.enwei.moneymanager.core.ProfitGuard.advise(it, plan.guardRule()) },
            gs?.principal == null,
            bars?.close?.let { com.enwei.moneymanager.core.ProfitGuard.dailyTrend(it) },
        )
    }.sortedByDescending { it.value }
    // 記下移動停利的最高價（下次從這裡繼續往上移）
    if (plan.disciplineOn && plan.guardOn) saveGuardPeaks(holdings.mapNotNull { h -> h.guard?.let { h.symbol to it.peak } }.toMap())
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

/**
 * 把 AI 文字裡的資料集新聞編號（例如「[19, 35]」）換成這段內容自己的編號「[1][2]」，
 * 對應 [ids] 的順序（新聞 id 依第一次出現加進去），畫面上點編號就能開那則新聞。
 */
fun renumberCites(text: String, imp: List<NewsItem>, ids: MutableList<String>): String =
    text.replace(citeRe) { m ->
        Regex("\\d{1,3}").findAll(m.value).mapNotNull { n -> imp.getOrNull(n.value.toInt())?.id }.toList().distinct()
            .joinToString("") { id -> if (id !in ids) ids += id; "[${ids.indexOf(id) + 1}]" }
    }

/** 目前持股依保本紀律算出的建議（記賣出時提示用）；沒持有時為 null。 */
suspend fun Repository.guardFor(symbol: String, price: Double): com.enwei.moneymanager.core.GuardAdvice? {
    val plan = settings.value.investPlan
    val held = holdingsByBroker.first().values.flatten().filter { it.market == Market.TW && it.symbol.equals(symbol, true) }
    val shares = held.sumOf { it.shares }
    if (!plan.guardOn || shares <= 0.0001 || price <= 0) return null
    val gs = plan.guards[symbol.uppercase()] ?: plan.guards[symbol]
    val principal = gs?.principal ?: autoPrincipal(trades.first().filter { it.market == Market.TW && it.symbol.equals(symbol, true) })
    return com.enwei.moneymanager.core.ProfitGuard.advise(com.enwei.moneymanager.core.GuardInput(shares, price, principal, gs?.peak, gs?.takeProfit), plan.guardRule())
}

/**
 * 記一筆賣出後調整保本紀律：只賣多賺的股數時保本金額不變；賣超過的部分是減碼，
 * 保本金額依留下的「成本股數」等比例調降。移動停利的最高價、手動停利價重新開始；全部賣光就清掉這檔的紀錄。
 * 要在存檔之前呼叫（用賣出前的持股計算）。
 */
suspend fun Repository.adjustGuardAfterSell(symbol: String, sold: Double, price: Double) {
    val plan = settings.value.investPlan
    if (!plan.guardOn || sold <= 0 || price <= 0) return
    val key = plan.guards.keys.firstOrNull { it.equals(symbol, true) } ?: symbol
    val held = holdingsByBroker.first().values.flatten().filter { it.market == Market.TW && it.symbol.equals(symbol, true) }
    val shares = held.sumOf { it.shares }
    if (shares <= 0.0001) return
    if (sold >= shares - 0.0001) {
        settings.update { st -> st.copy(investPlan = st.investPlan.copy(guards = st.investPlan.guards - key)) }
        return
    }
    val gs = plan.guards[key] ?: GuardState()
    val principal = gs.principal ?: autoPrincipal(trades.first().filter { it.market == Market.TW && it.symbol.equals(symbol, true) })
    val excess = kotlin.math.floor(maxOf(0.0, shares * price - principal) / price)
    val reduce = (sold - excess).coerceAtLeast(0.0)
    val costShares = (shares - excess).coerceAtLeast(0.0)
    val ng = if (reduce > 0 && costShares > 0) gs.copy(principal = principal * ((costShares - reduce).coerceAtLeast(0.0) / costShares), reduceAt = null)
    else gs
    settings.update { st -> st.copy(investPlan = st.investPlan.copy(guards = st.investPlan.guards + (key to ng.copy(peak = null, takeProfit = null)))) }
}

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
        appendLine("資金計畫：現金 ${m(a.cash)}，預留金額 ${m(a.reserve)}，可投資 ${m(a.investable)}（長線 ${"%.0f".format(s.investPlan.longRatio * 100)}%）；" +
            "長線已投入 ${m(a.longValue)}／目標 ${m(a.longTarget)}，可再投入 ${m(a.longRoom)}；短線已投入 ${m(a.shortValue)}／目標 ${m(a.shortTarget)}，可再投入 ${m(a.shortRoom)}")
        appendLine("持股（代號 名稱｜股性｜股數｜均價｜現價｜損益%｜規則建議）：" + if (snap.holdings.isEmpty()) "無" else "")
        snap.holdings.forEach { h ->
            appendLine(
                "- ${h.symbol} ${h.name.orEmpty()}｜${h.style.label}｜${"%.0f".format(h.shares)}｜${"%.2f".format(h.avgCost)}｜${h.price ?: "?"}｜" +
                    "${h.gainPct?.let { "%+.1f".format(it) } ?: "?"}｜" + (h.advice?.let { "${it.action}，停損 ${"%.2f".format(it.stop)}" } ?: "?"),
            )
        }
        if (s.watchlist.isNotEmpty()) appendLine("觀察清單（使用者想留意、還沒買的）：" + s.watchlist.joinToString("、"))
        if (s.investPlan.disciplineOn && s.investPlan.guardOn) {
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
        if (s.investPlan.rules.isNotEmpty()) {
            appendLine("使用者設定的投資原則：" + s.investPlan.rules.joinToString("；"))
        }
    }
}

/** 資料集摘要：題材熱度、個股統計（附最新一則標題）、重點新聞（編號 → 新聞 id）。 */
private fun datasetDigest(ds: NewsDataset, st: NewsStore, themes: Int, symbols: Int, important: Int, full: Boolean = false): Pair<String, List<NewsItem>> {
    val recent = ds.recent(st, 3)
    val coverage = ds.coverage(st)
    val recentAll = st.items.filter { it.epochSec >= System.currentTimeMillis() / 1000 - 3 * 86_400L }
    val imp = ds.important(st, 2, important)
    val text = buildString {
        appendLine("新聞資料集：近 3 天共 ${coverage.recent} 則，待標註 ${coverage.pending} 則，已整理進重點庫 ${coverage.digested} 則；資料集共收 ${st.items.size} 則")
        appendLine("近 3 天來源內容範圍：取得正文或摘要 ${recentAll.count { it.contentLevel != "title" }} 則，只有標題 ${recentAll.count { it.contentLevel == "title" }} 則")
        com.enwei.moneymanager.net.InvestData.lastHeadlineError?.let { appendLine("新聞搜尋完整性警告：$it") }
        appendLine("題材熱度（則數｜平均多空 -2~2｜重大則數）：")
        ds.themes(st).let { if (full) it else it.take(themes) }.forEach { appendLine("- ${it.key}｜${it.count}｜${"%+.1f".format(it.avgSentiment)}｜${it.important}") }
        appendLine("被提到最多的個股（代號｜則數｜多空｜最新標題）：")
        ds.symbols(st).let { if (full) it else it.take(symbols) }.forEach { t ->
            val heads = recent.filter { t.key in it.symbols }.take(if (full) 3 else 1).joinToString(" / ") { it.title.take(if (full) 40 else 24) }
            appendLine("- ${t.key}｜${t.count}｜${"%+.1f".format(t.avgSentiment)}｜$heads")
        }
        // 新聞重點庫：所有新聞依題材整理後的重點（同一事件只寫一次）
        val cutoff = LocalDate.now().minusDays(2).toString()
        val byTheme = st.digests.values.filter { it.day >= cutoff }.groupBy { it.theme }
            .entries.sortedByDescending { e -> e.value.sumOf { it.count } }
        if (byTheme.isNotEmpty()) {
            appendLine("新聞重點庫（依題材；每點後為相關代號、多空 -2~2、幾則新聞提到）：")
            (if (full) byTheme else byTheme.take(8)).forEach { (theme, days) ->
                appendLine("【$theme】")
                (if (full) days else days.sortedByDescending { it.day }.take(1)).forEach { d ->
                    (if (full) d.points else d.points.take(3)).forEach { p ->
                        appendLine("- ${d.day.substring(5)} ${p.text}" + (if (p.symbols.isNotEmpty()) "｜${p.symbols.joinToString(",")}" else "") + "｜${p.sentiment}｜${p.count}則")
                    }
                }
            }
        }
        appendLine("重點新聞：")
        imp.forEachIndexed { i, n -> appendLine("[$i] ${n.title.take(45)}" + (n.note.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "")) }
        // 讀得下大量資料時（Gemini）：附上近 3 天所有新聞標題，依題材分組
        if (full) {
            appendLine("近 3 天所有新聞標題、來源內容範圍與合併篇數（依題材；括號為多空 -2~2）：")
            recent.groupBy { it.theme.ifBlank { "其他" } }.entries.sortedByDescending { it.value.size }.forEach { (theme, list) ->
                appendLine("【$theme】")
                list.forEach { n ->
                    appendLine("- [${n.contentLevel}] ${n.title}（${n.sentiment}；合併 ${n.duplicateCount} 篇）" +
                        (if (n.symbols.isNotEmpty()) " ${n.symbols.joinToString(",")}" else ""))
                }
            }
        }
    }
    return text to imp
}

/**
 * 依可用的字數挑選資料集摘要的詳細程度：先試完整版，放不下就逐步減少題材、個股與重點新聞，
 * 都放不下時再截短（並註明已精簡）。
 */
private fun fitDigest(ds: NewsDataset, st: NewsStore, room: Int, big: Boolean): Pair<String, List<NewsItem>> {
    val levels = listOf(Triple(30, 50, 40), Triple(20, 30, 25), Triple(12, 18, 15), Triple(8, 12, 10), Triple(5, 8, 6), Triple(3, 5, 4))
    if (big) datasetDigest(ds, st, 30, 50, 40, full = true).let { if (it.first.length <= room) return it }
    for ((t, s, i) in levels) {
        val r = datasetDigest(ds, st, themes = t, symbols = s, important = i)
        if (r.first.length + 30 <= room) return ("（資料量較大，已精簡為前 $t 個題材、$s 檔個股、$i 則重點新聞）\n" + r.first) to r.second
    }
    val r = datasetDigest(ds, st, themes = 3, symbols = 5, important = 4)
    return r.first.take((room - 20).coerceAtLeast(0)) to r.second
}

/**
 * 依使用者的問題搜尋資料集裡的新聞（BM25 排序）：問題切成代號、公司名稱、題材與中文兩字詞，
 * 在標題、重點與內文裡計分；代號、公司與題材命中另外加權，越新、越重要的新聞略為加分。
 * 先找近 7 天，結果太少時放寬到資料集保存的 14 天。
 */
private fun relatedNews(ds: NewsDataset, st: NewsStore, q: String, holdings: Set<String>, limit: Int): List<NewsItem> {
    val codes = HashSet<String>()
    Regex("[0-9]{4,6}[A-Z]?").findAll(q).forEach { codes += it.value }
    val names = HashSet<String>()
    com.enwei.moneymanager.net.TwData.cached().forEach { s -> if (s.name.length >= 2 && q.contains(s.name)) { codes += s.code; names += s.name } }
    if (listOf("持股", "我的股票", "手上", "庫存").any { q.contains(it) }) codes += holdings
    val stop = setOf("今天", "什麼", "怎麼", "可以", "現在", "適合", "我的", "應該", "是否", "如何", "哪些", "一下", "目前", "要不", "不要", "還是", "這個", "那個", "股票", "建議", "請問", "最近", "新聞", "消息", "分析", "看法", "一個", "有沒", "沒有", "會不", "不會")
    val cjk = q.filter { it in '一'..'鿿' }
    val grams = (0 until (cjk.length - 1).coerceAtLeast(0)).map { cjk.substring(it, it + 2) }.filter { it !in stop }.toSet()
    val words = Regex("[A-Za-z]{2,}").findAll(q).map { it.value.lowercase() }.toSet()
    if (codes.isEmpty() && grams.isEmpty() && words.isEmpty()) return emptyList()
    fun search(days: Int): List<Pair<NewsItem, Double>> {
        val docs = ds.recent(st, days)
        if (docs.isEmpty()) return emptyList()
        val texts = docs.map { (it.title + " " + it.note + " " + it.content.take(600)).lowercase() }
        val avg = texts.map { it.length }.average().coerceAtLeast(1.0)
        val terms = grams + words
        val df = terms.associateWith { t -> texts.count { it.contains(t) } }
        val n = docs.size.toDouble()
        val now = System.currentTimeMillis() / 1000
        return docs.mapIndexed { i, d ->
            val text = texts[i]
            var score = 0.0
            for (t in terms) {
                val f = Regex(Regex.escape(t)).findAll(text).count().toDouble()
                if (f == 0.0) continue
                val idf = kotlin.math.ln(1 + (n - df.getValue(t) + 0.5) / (df.getValue(t) + 0.5))
                score += idf * f * 2.2 / (f + 1.2 * (0.25 + 0.75 * text.length / avg))
                if (d.title.lowercase().contains(t)) score += idf * 0.5
            }
            if (d.symbols.any { it in codes }) score += 6
            if (names.any { d.title.contains(it) }) score += 4
            if (d.theme.isNotBlank() && (q.contains(d.theme) || grams.any { d.theme.contains(it) })) score += 2.5
            if (score > 0) score += d.importance * 0.3 + (if (now - d.epochSec < 86_400) 0.8 else 0.0)
            d to score
        }.filter { it.second > 1.0 }.sortedByDescending { it.second }
    }
    val hits = search(7).let { if (it.size < 3) search(14) else it }
    // 同一事件的多則報導只留最相關的一則
    val seen = HashSet<String>()
    return hits.filter { seen.add(it.first.title.filter { c -> c.isLetterOrDigit() }.take(14)) }.take(limit).map { it.first }
}

/** 背景蒐集與標註新聞（每天數次）；回傳新增則數。 */
suspend fun Repository.refreshNews(
    ds: NewsDataset, onStep: (String) -> Unit = {}, onBatch: (Int, Int) -> Unit = { _, _ -> }, onDigest: (Int, Int) -> Unit = { _, _ -> },
    /** 時間預算與網路檢查：用完就在批次之間停下，下一輪接續 */
    budget: com.enwei.moneymanager.work.WorkBudget? = null,
): Int {
    val holdings = holdingsByBroker.first().values.flatten().filter { it.market == Market.TW && it.shares > 0.0001 }
    val watch = ds.load().report?.picks.orEmpty().map { "${it.symbol} ${it.name}" }
    // 持股、觀察清單、報告推薦的個股都另外查新聞
    val watched = settings.value.watchlist
    val extra = (holdings.map { "${it.symbol} ${it.name.orEmpty()}".trim() } + watched + watch).distinct().take(25)
    onStep("蒐集新聞…")
    val added = ds.collect(extra)
    com.enwei.moneymanager.net.InvestData.lastHeadlineError?.let(onStep)
    if (com.enwei.moneymanager.net.GroqAi.enabled) {
        val stop = { budget?.shouldStop() == true }
        // thread：逐批讀取（標註）新聞，讀完的新聞歸入「日期|題材」區塊
        ds.tag(stop) { i, n -> onBatch(i, n); onStep("AI 標註新聞 ${i + 1}/$n 批…") }
        // 題材合併：各批取的名稱常不一樣，請 AI 對應到標準題材，避免同一件事分成很多題材
        onStep("AI 合併題材分類…")
        runCatching { ds.consolidateThemes(stop) }
        // block：把每個區塊的全部新聞整理成重點文章，區塊就完整了
        ds.digest(stop = stop) { i, n -> onDigest(i, n); onStep("整理新聞重點 ${i + 1}/$n…") }
        budget?.stopReason?.let { onStep("暫停：$it") }
    }
    return added
}

/**
 * 每日投資報告：先確保資料集夠完整（不夠就先蒐集、標註），再把整理過的題材、個股與重點新聞
 * 加上大盤、資金計畫、持股與紀律交給進階模型。推薦的代號會再查報價確認。
 */
suspend fun Repository.buildDailyReport(ds: NewsDataset, onStep: (String) -> Unit): DailyReport? {
    var st = ds.load()
    if (System.currentTimeMillis() - st.lastCollect > 3 * 3600_000L) {
        refreshNews(ds, onStep)
        st = ds.load()
    }
    val coverage = ds.coverage(st)
    if (coverage.pending > 0 || coverage.digested < coverage.recent) {
        onStep("近 3 天新聞仍有 ${coverage.pending} 則待標註、${coverage.recent - coverage.digested} 則未納入完整重點整理，暫不產生可能遺漏資料的報告；稍後會繼續處理。")
        return null
    }
    com.enwei.moneymanager.net.InvestData.lastHeadlineError?.let {
        onStep("$it，新聞蒐集完整性未確認，暫不產生報告。")
        return null
    }
    onStep("讀取大盤與持股走勢…")
    val snap = advisorSnapshot(fetch = true)
    val big = com.enwei.moneymanager.net.GroqAi.bigContext(com.enwei.moneymanager.net.GroqAi.AiTask.REPORT)
    val (digest, imp) = if (big) datasetDigest(ds, st, themes = 30, symbols = 60, important = 60, full = true)
    else datasetDigest(ds, st, themes = 10, symbols = 18, important = 15)
    onStep("讀取熱門個股的月營收…")
    val revText = runCatching {
        val syms = ds.symbols(st).take(if (big) 20 else 10).map { it.key }
        kotlinx.coroutines.coroutineScope {
            syms.map { sym -> async { sym to com.enwei.moneymanager.net.TwData.monthRevenue(sym, 15) } }.map { it.await() }
        }.filter { it.second.isNotEmpty() }.joinToString("\n", prefix = "熱門個股月營收（代號｜最新月份｜年增率｜近三月平均年增）：\n") { (sym, l) ->
            val last = l.last()
            val avg3 = l.takeLast(3).mapNotNull { it.yoy }.takeIf { it.isNotEmpty() }?.average()
            "- $sym｜${last.year}/${last.month}｜${last.yoy?.let { "%+.1f%%".format(it) } ?: "?"}｜${avg3?.let { "%+.1f%%".format(it) } ?: "?"}"
        }
    }.getOrDefault("")
    onStep("AI 分析資料集（${ds.recent(st, 3).size} 則新聞）…")
    val r = com.enwei.moneymanager.net.GroqAi.dailyReport(situation(snap) + "\n" + digest + "\n" + revText) ?: return null
    onStep("確認推薦的代號與價格…")
    val quotes = runCatching { com.enwei.moneymanager.net.QuoteService.fetch(r.picks.map { Market.TW to it.symbol }) }
        .getOrDefault(emptyList()).associateBy { it.key }
    val picks = r.picks.map { p ->
        val q = quotes[quoteKey(Market.TW, p.symbol)]
        p.copy(price = q?.price, name = p.name.ifBlank { q?.name.orEmpty() })
    }
    val keyNews = r.keyNews.mapNotNull { k -> k.id.toIntOrNull()?.let { imp.getOrNull(it) }?.let { KeyNews(it.id, k.why) } }
    // 報告文字裡的新聞編號換成報告自己的 [1][2]…，重點新聞也編進同一組編號
    val ids = ArrayList<String>()
    fun rn(t: String) = renumberCites(t, imp, ids)
    val trend = rn(r.trend); val summary = rn(r.summary); val allocation = rn(r.allocation)
    val picks2 = picks.map { it.copy(reason = rn(it.reason)) }
    val holdings2 = r.holdings.map { it.copy(reason = rn(it.reason)) }
    keyNews.forEach { if (it.id !in ids) ids += it.id }
    val report = r.copy(trend = trend, summary = summary, allocation = allocation, picks = picks2, holdings = holdings2, cites = ids, keyNews = keyNews, createdAt = System.currentTimeMillis(), day = LocalDate.now().toEpochDay(), newsCount = ds.recent(st, 3).size)
    ds.update { it.copy(report = report, history = (listOfNotNull(it.report) + it.history).take(400), business = it.business + picks2.filter { p -> p.business.isNotBlank() }.associate { p -> p.symbol to p.business }).prunedHistory() }
    return report
}

/**
 * 投資助理對話：先用搜尋（BM25）從新聞資料集找出和問題相關的新聞；相關新聞不多時，
 * 再抓這幾篇的網頁全文，讓 AI 根據具體內容回答，結論用股票卡片、圖表與新聞編號呈現。
 */
suspend fun Repository.askAdvisor(ds: NewsDataset, question: String, onStep: (String) -> Unit = {}): ChatMsg? {
    val st = ds.load()
    // 重試同一個問題時不重複加進對話
    ds.update { st -> if (st.chat.lastOrNull()?.let { it.fromUser && it.text == question } == true) st else st.copy(chat = (st.chat + ChatMsg(true, question)).takeLast(40)) }
    onStep("讀取持股與大盤…")
    val snap = advisorSnapshot(fetch = true)
    val big = com.enwei.moneymanager.net.GroqAi.bigContext(com.enwei.moneymanager.net.GroqAi.AiTask.CHAT)
    val today = st.report?.let { r -> "今日報告：${r.trend}；推薦 " + r.picks.joinToString("、") { "${it.symbol}${it.name}（${it.style}）" } } ?: ""
    runCatching { com.enwei.moneymanager.net.TwData.stockList(com.enwei.moneymanager.MoneyApp.instance) }
    val limit = com.enwei.moneymanager.net.GroqAi.chatContextLimit()
    val holdSyms = snap.holdings.map { it.symbol }.toSet()
    // 1. 搜尋：依代號、公司名稱、題材與關鍵詞找出相關新聞
    onStep("搜尋相關新聞…")
    val related0 = relatedNews(ds, st, question, holdSyms, if (big) 20 else 8)
    val reserved0 = situation(snap).length + today.length + 2
    // 2. 相關新聞不多（範圍明確）時，抓前幾篇的全文
    val focused = related0.isNotEmpty() && related0.size <= (if (big) 10 else 6)
    val readN = if (!focused) 0 else if (big) 5 else 2
    val relRoom = ((limit - reserved0) * (if (focused) 0.65 else 0.4)).toInt().coerceAtLeast(0)
    val perBody = if (readN == 0) 0 else ((relRoom - related0.size * 90) / readN).coerceIn(0, if (big) 3000 else 600)
    val bodies: Map<String, String> = if (readN > 0 && perBody >= 150) {
        onStep("讀取 ${minOf(readN, related0.size)} 篇新聞全文…")
        kotlinx.coroutines.coroutineScope {
            related0.take(readN).map { n ->
                async { n.id to (com.enwei.moneymanager.net.Article.text(n.url, perBody) ?: n.content.takeIf { n.contentLevel == "正文" && it.length > 80 }?.take(perBody)) }
            }.map { it.await() }
        }.mapNotNull { (id, t) -> t?.let { id to it } }.toMap()
    } else emptyMap()
    fun relLine(k: Int, n: NewsItem) = "[$k] ${n.title.take(60)}" + (n.note.takeIf { it.isNotBlank() }?.let { "（$it）" } ?: "") +
        (if (n.symbols.isNotEmpty()) "｜${n.symbols.joinToString(",")}" else "") + "｜多空 ${n.sentiment}" +
        (bodies[n.id]?.let { "\n  全文摘錄：$it" } ?: "")
    val relSize = related0.withIndex().sumOf { (k, n) -> relLine(k, n).length + 1 }
    // 3. 其餘空間放資料集摘要（太多時自動精簡）
    onStep(if (bodies.isNotEmpty()) "AI 依 ${bodies.size} 篇全文與 ${related0.size} 則相關新聞回答…" else "AI 整理回覆…")
    val (digest, imp) = fitDigest(ds, st, limit - reserved0 - relSize - 80, big)
    val related = related0.filter { r -> imp.none { it.id == r.id } }
    val relText = if (related.isEmpty()) "" else buildString {
        appendLine("與問題相關的新聞（App 搜尋資料集找出，依相關度排序；有「全文摘錄」的是已讀取的新聞內文；引用時用編號）：")
        related.forEachIndexed { k, n -> appendLine(relLine(imp.size + k, n)) }
    }
    val sources = imp + related
    val history = st.chat.takeLast(6).map { it.fromUser to it.text }
    val reply = com.enwei.moneymanager.net.GroqAi.advisorChat(situation(snap) + "\n" + today + "\n" + digest + "\n" + relText, history, question, focused = related.isNotEmpty()) ?: return null
    // 回覆裡的 [19, 35] 是資料集的新聞編號：換成 [1][2]，對應附在回覆下方的新聞
    val ids = ArrayList<String>()
    val text = renumberCites(reply.text, sources, ids)
    val points = reply.points.map { renumberCites(it, sources, ids) }
    reply.newsIds.mapNotNull { it.toIntOrNull() }.mapNotNull { sources.getOrNull(it)?.id }.forEach { if (it !in ids) ids += it }
    onStep("確認代號與報價…")
    // 卡片的代號查報價確認（查得到才算確認，並帶上現價與名稱）
    val quotes = runCatching { com.enwei.moneymanager.net.QuoteService.fetch(reply.cards.map { Market.TW to it.symbol }) }
        .getOrDefault(emptyList()).associateBy { it.key }
    val cards = reply.cards.map { p ->
        val q = quotes[quoteKey(Market.TW, p.symbol)]
        p.copy(price = q?.price, name = p.name.ifBlank { q?.name.orEmpty() })
    }
    // 問到營收或走勢比較時，自動附上圖表（AI 沒有附的話）：對象是卡片上的個股、問題裡的代號或持股
    val mentioned = (Regex("[0-9]{4,6}[A-Z]?").findAll(question).map { it.value }.toList() +
        com.enwei.moneymanager.net.TwData.cached().filter { it.name.length >= 2 && question.contains(it.name) }.map { it.code } +
        (if (listOf("持股", "我的股票", "庫存").any { question.contains(it) }) snap.holdings.map { it.symbol } else emptyList())).distinct()
    val targets = (cards.map { it.symbol } + mentioned).distinct().take(6)
    val charts = reply.charts.toMutableList()
    if (question.contains("營收") && charts.none { it.kind == "revenue" } && targets.isNotEmpty())
        charts += com.enwei.moneymanager.data.ChatChart("revenue", targets)
    if (listOf("走勢", "比較", "強弱").any { question.contains(it) } && charts.none { it.kind == "compare" } && targets.size >= 2)
        charts += com.enwei.moneymanager.data.ChatChart("compare", targets.take(5))
    // AI 沒有附卡片時，用相關新聞裡最常出現的個股做卡片，理由放新聞重點
    val cards2 = if (cards.isNotEmpty() || related0.isEmpty()) cards else {
        val top = related0.take(6).flatMap { n -> n.symbols.map { it to n } }.groupBy({ it.first }, { it.second })
            .entries.sortedByDescending { it.value.size }.take(3)
        val q2 = runCatching { com.enwei.moneymanager.net.QuoteService.fetch(top.map { Market.TW to it.key }) }.getOrDefault(emptyList()).associateBy { it.key }
        top.mapNotNull { (sym, ns) ->
            val q = q2[quoteKey(Market.TW, sym)] ?: return@mapNotNull null
            val n = ns.first()
            StockPick(sym, name = q.name.orEmpty().ifBlank { com.enwei.moneymanager.net.TwData.nameOf(sym).orEmpty() }, price = q.price,
                strength = when { n.sentiment > 0 -> "強勢"; n.sentiment < 0 -> "弱勢"; else -> "中性" }, reason = n.note.ifBlank { n.title }.take(60))
        }
    }
    // 讀過全文的新聞一定附在回覆下方
    related0.filter { it.id in bodies }.forEach { if (it.id !in ids) ids += it.id }
    val basis = if (related0.isEmpty()) "" else "依據：搜尋到 ${related0.size} 則相關新聞" + (if (bodies.isNotEmpty()) "，讀了 ${bodies.size} 篇全文" else "")
    val fixed = reply.copy(text = text, points = points, newsIds = ids, cards = cards2, charts = charts.take(3), basis = basis)
    // 使用者按了停止：不把回覆加進對話
    kotlinx.coroutines.yield()
    ds.update { it.copy(chat = (it.chat + fixed).takeLast(40), business = it.business + fixed.cards.filter { c -> c.business.isNotBlank() }.associate { c -> c.symbol to c.business }) }
    return fixed
}
