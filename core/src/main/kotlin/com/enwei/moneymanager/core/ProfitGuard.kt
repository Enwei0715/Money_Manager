package com.enwei.moneymanager.core

import kotlin.math.floor
import kotlin.math.max

/**
 * 保本＋移動停利紀律（風險低、獲利低，但仍保有一定獲利）：
 * - 集保裡永遠留著「市值約等於原始成本」的股數，只有多賺出來的部分可以賣。
 * - 多賺的金額換算成股數；不見價就賣，而是設停利價（從多賺後的最高價回落 [GuardRule.trailPct]%），
 *   股價續漲時停利價跟著往上移，跌破才賣出多賺的股數。
 * - 想減碼時設一個比現價低的減碼價，觸發時賣一半。
 * - 下跌時看 60 分 K 的 20MA、50MA：跌破一條約賣一半，兩條都跌破減到只剩基本股。
 */
data class GuardRule(
    /** 移動停利：從高點回落幾 % 為停利價 */
    val trailPct: Double = 3.0,
    /** 兩條 60 分 K 均線都跌破時留下的基本股數 */
    val baseShares: Double = 1.0,
)

data class GuardInput(
    val shares: Double,
    val price: Double,
    /** 保本金額（原始成本） */
    val principal: Double,
    /** 出現多賺股數之後的最高價（上一次的狀態；沒有則為 null） */
    val peak: Double? = null,
    /** 使用者手動設的停利價 */
    val manualTakeProfit: Double? = null,
    /** 想減碼時設的觸發價（跌破賣一半） */
    val reduceAt: Double? = null,
    /** 60 分 K 的 20MA、50MA */
    val hourMa20: Double? = null,
    val hourMa50: Double? = null,
)

enum class GuardAction(val label: String) {
    HOLD("續抱，市值還沒超過成本"),
    TRAILING("移動停利中"),
    SELL_EXCESS("跌破停利價：賣出多賺的股數"),
    REDUCE_TRIGGER("跌破減碼價：賣一半"),
    REDUCE_HALF("跌破 60 分 K 一條均線：減碼約一半"),
    TO_BASE("跌破 60 分 K 兩條均線：減到只剩基本股"),
}

data class GuardAdvice(
    /** 多賺出來、可以停利的股數 */
    val excessShares: Double,
    /** 多賺的金額（市值 − 保本金額） */
    val excessValue: Double,
    /** 目前的停利價（沒有多賺股數時為 null） */
    val takeProfit: Double?,
    /** 更新後的最高價狀態（沒有多賺股數時歸零） */
    val peak: Double?,
    val action: GuardAction,
    /** 建議賣出的股數 */
    val sellShares: Double,
    val note: String,
) {
    val triggered: Boolean get() = sellShares > 0
}

object ProfitGuard {
    fun advise(i: GuardInput, rule: GuardRule = GuardRule()): GuardAdvice {
        val value = i.shares * i.price
        val excessValue = value - i.principal
        // 多賺的金額換算成股數（無條件捨去），賣完後留在場內的市值仍約等於成本
        val excess = if (excessValue > 0 && i.price > 0) floor(excessValue / i.price + 1e-9).coerceAtMost(i.shares) else 0.0
        val peak = if (excess >= 1) max(i.peak ?: i.price, i.price) else null
        val auto = peak?.let { it * (1 - rule.trailPct / 100) }
        val tp = if (excess >= 1) max(auto ?: 0.0, i.manualTakeProfit ?: 0.0).takeIf { it > 0 } else null

        val below = listOfNotNull(i.hourMa20, i.hourMa50).count { i.price < it }
        val sellable = (i.shares - rule.baseShares).coerceAtLeast(0.0)
        fun half() = floor(i.shares / 2).coerceAtMost(sellable)
        fun f(v: Double) = "%.2f".format(v)
        return when {
            below >= 2 && sellable > 0 -> GuardAdvice(excess, excessValue, tp, peak, GuardAction.TO_BASE, sellable,
                "現價 ${f(i.price)} 同時跌破 60 分 K 20MA ${f(i.hourMa20!!)}、50MA ${f(i.hourMa50!!)}，減到只剩 ${rule.baseShares.toLong()} 股")
            i.reduceAt != null && i.price < i.reduceAt && half() > 0 -> GuardAdvice(excess, excessValue, tp, peak, GuardAction.REDUCE_TRIGGER, half(),
                "現價 ${f(i.price)} 跌破你設的減碼價 ${f(i.reduceAt)}，賣一半")
            below == 1 && half() > 0 -> {
                val which = if (i.hourMa20 != null && i.price < i.hourMa20) "20MA ${f(i.hourMa20)}" else "50MA ${f(i.hourMa50 ?: 0.0)}"
                GuardAdvice(excess, excessValue, tp, peak, GuardAction.REDUCE_HALF, half(), "現價 ${f(i.price)} 跌破 60 分 K $which，考慮減碼約一半")
            }
            tp != null && i.price < tp -> GuardAdvice(excess, excessValue, tp, peak, GuardAction.SELL_EXCESS, excess,
                "跌破停利價 ${f(tp)}，賣出多賺的 ${excess.toLong()} 股，留下的市值約等於成本")
            excess >= 1 -> GuardAdvice(excess, excessValue, tp, peak, GuardAction.TRAILING, 0.0,
                "多賺 ${excess.toLong()} 股，停利價 ${f(tp ?: 0.0)}（最高 ${f(peak ?: i.price)} 回落 ${"%.0f".format(rule.trailPct)}%），續漲就跟著上移")
            else -> GuardAdvice(0.0, excessValue, null, null, GuardAction.HOLD, 0.0,
                if (excessValue > 0) "多賺 ${"%,.0f".format(excessValue)} 元，還不到一股（${f(i.price)}）" else "市值比成本少 ${"%,.0f".format(-excessValue)} 元，成本留在場內續抱")
        }
    }

    /** 日 K 均線（5、10、20、72、100、200 日）的位置，用來判斷整體趨勢。 */
    data class MaTrend(val values: Map<Int, Double>, val above: List<Int>, val below: List<Int>) {
        val summary: String
            get() = when {
                values.isEmpty() -> "資料不足"
                below.isEmpty() -> "站上全部日均線（多頭排列）"
                above.isEmpty() -> "跌破全部日均線（空頭排列）"
                else -> "站上 ${above.joinToString("、")} 日線，跌破 ${below.joinToString("、")} 日線"
            }
    }

    val DAILY_MAS = listOf(5, 10, 20, 72, 100, 200)

    fun dailyTrend(close: List<Double>): MaTrend {
        val p = close.lastOrNull() ?: return MaTrend(emptyMap(), emptyList(), emptyList())
        val v = DAILY_MAS.mapNotNull { n -> Technicals.sma(close, n)?.let { n to it } }.toMap()
        return MaTrend(v, v.filter { p >= it.value }.keys.toList(), v.filter { p < it.value }.keys.toList())
    }
}
