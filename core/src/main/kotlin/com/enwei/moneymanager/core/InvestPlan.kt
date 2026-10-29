package com.enwei.moneymanager.core

import kotlin.math.max
import kotlin.math.min

/** 均線、RSI 與走勢階段（以日收盤價計算）。 */
object Technicals {
    fun sma(v: List<Double>, n: Int, endExclusive: Int = v.size): Double? =
        if (endExclusive < n || n <= 0) null else v.subList(endExclusive - n, endExclusive).average()

    /** Wilder RSI。 */
    fun rsi(v: List<Double>, n: Int = 14): Double? {
        if (v.size <= n) return null
        var gain = 0.0
        var loss = 0.0
        for (i in 1..n) {
            val d = v[i] - v[i - 1]
            if (d > 0) gain += d else loss -= d
        }
        gain /= n; loss /= n
        for (i in n + 1 until v.size) {
            val d = v[i] - v[i - 1]
            gain = (gain * (n - 1) + max(d, 0.0)) / n
            loss = (loss * (n - 1) + max(-d, 0.0)) / n
        }
        return if (loss == 0.0) 100.0 else 100 - 100 / (1 + gain / loss)
    }

    /** 收盤價相對 [daysAgo] 天前的漲跌幅（%）。 */
    fun change(v: List<Double>, daysAgo: Int): Double? =
        if (v.size <= daysAgo) null else (v.last() / v[v.size - 1 - daysAgo] - 1) * 100

    /**
     * 走勢階段：價格、月線（20 日）、季線（60 日）的排列與方向，
     * 搭配離季線的距離與 RSI 判斷初升／主升／末升、初跌／主跌／末跌或盤整。
     */
    fun stage(close: List<Double>): MarketStage {
        if (close.size < 65) return MarketStage.UNKNOWN
        val p = close.last()
        val ma20 = sma(close, 20)!!
        val ma60 = sma(close, 60)!!
        val ma20Before = sma(close, 20, close.size - 5)!!
        val dist60 = (p / ma60 - 1) * 100
        val r = rsi(close) ?: 50.0
        // 近 20 天內月線是否剛穿越季線（剛轉多或剛轉空）
        fun crossedRecently(up: Boolean): Boolean = (close.size - 20 until close.size - 1).any { i ->
            val a20 = sma(close, 20, i) ?: return@any false
            val a60 = sma(close, 60, i) ?: return@any false
            if (up) a20 <= a60 else a20 >= a60
        }
        val up = p > ma20 && ma20 > ma60 && ma20 > ma20Before
        val down = p < ma20 && ma20 < ma60 && ma20 < ma20Before
        return when {
            up && (dist60 > 15 || r > 75) -> MarketStage.LATE_UP
            up && crossedRecently(true) -> MarketStage.EARLY_UP
            up -> MarketStage.MAIN_UP
            down && (dist60 < -15 || r < 25) -> MarketStage.LATE_DOWN
            down && crossedRecently(false) -> MarketStage.EARLY_DOWN
            down -> MarketStage.MAIN_DOWN
            p >= ma60 -> MarketStage.RANGE_HIGH
            else -> MarketStage.RANGE_LOW
        }
    }
}

enum class MarketStage(val label: String, val hint: String) {
    EARLY_UP("初升段", "剛站上均線，量能放大可分批布局"),
    MAIN_UP("主升段", "多頭排列，順勢持有、移動停利"),
    LATE_UP("末升段（過熱）", "離季線太遠，不追高，持股分批停利"),
    RANGE_HIGH("高檔整理", "方向未明，降低短線部位、等待突破"),
    RANGE_LOW("低檔整理／築底", "觀察是否站回季線，小量試單"),
    EARLY_DOWN("初跌段", "跌破均線，短線先減碼、嚴守停損"),
    MAIN_DOWN("主跌段", "空頭排列，保留現金，不攤平"),
    LATE_DOWN("末跌段（超跌）", "可能出現反彈，只用短線資金試單"),
    UNKNOWN("資料不足", "需要至少 65 天的價格"),
}

/** 股性：長線（不理短期波動）或短線（嚴守停損）。 */
enum class TradeStyle(val label: String) { LONG("長線"), SHORT("短線") }

/** 紀律參數（百分比）。 */
data class DisciplineRule(
    val shortStopPct: Double = 8.0,
    val shortTrailPct: Double = 8.0,
    val longTrailPct: Double = 15.0,
    val takeProfitPct: Double = 15.0,
)

/** 一檔持股的紀律建議：動態停損價、下一個分批停利價與建議動作。 */
data class StopAdvice(val stop: Double, val nextTakeProfit: Double?, val action: String, val note: String, val triggered: Boolean)

object Discipline {
    /**
     * 動態停損與分批停利，避免「一次全賣、隔天賣飛」：
     * - 短線：停損 = max(成本 × (1 − 停損%), 買進後最高價 × (1 − 移動停損%))；獲利達停利% 先賣 1/3，其餘以移動停損保護。
     * - 長線：只用較寬的移動停損（最高價 × (1 − 長線移動%)）且只在有獲利時啟用；觸發時減碼 1/3 而不是全賣。
     */
    fun advise(style: TradeStyle, avgCost: Double, price: Double, peak: Double, rule: DisciplineRule = DisciplineRule()): StopAdvice {
        val hi = max(peak, price)
        val gain = (price / avgCost - 1) * 100
        val peakGain = (hi / avgCost - 1) * 100
        return when (style) {
            TradeStyle.SHORT -> {
                val hard = avgCost * (1 - rule.shortStopPct / 100)
                // 漲過一段後才把停損往上移（至少保本）
                val trail = if (peakGain >= rule.shortTrailPct) max(hi * (1 - rule.shortTrailPct / 100), avgCost) else hard
                val stop = max(hard, trail)
                val tp = avgCost * (1 + rule.takeProfitPct / 100)
                when {
                    price <= stop && price < avgCost -> StopAdvice(stop, null, "停損出場", "跌破停損 ${fmt(stop)}，短線不攤平、不凹單", true)
                    price <= stop -> StopAdvice(stop, null, "移動停利出場", "從高點 ${fmt(hi)} 回落，跌破 ${fmt(stop)}，獲利先入袋", true)
                    gain >= rule.takeProfitPct -> StopAdvice(stop, tp * (1 + rule.takeProfitPct / 100), "分批停利 1/3",
                        "已賺 ${fmt(gain)}%，先賣 1/3，其餘守 ${fmt(stop)}，不要一次全賣", false)
                    else -> StopAdvice(stop, tp, "續抱", "停損 ${fmt(stop)}，到 ${fmt(tp)} 再分批停利", false)
                }
            }
            TradeStyle.LONG -> {
                val trail = hi * (1 - rule.longTrailPct / 100)
                val active = peakGain >= rule.longTrailPct
                val tp = avgCost * (1 + rule.takeProfitPct * 2 / 100)
                when {
                    active && price <= trail -> StopAdvice(trail, null, "減碼 1/3", "從高點回落超過 ${fmt(rule.longTrailPct)}%，先減碼 1/3，不必全部賣掉", true)
                    gain >= rule.takeProfitPct * 2 -> StopAdvice(if (active) trail else 0.0, null, "可分批停利 1/3",
                        "已賺 ${fmt(gain)}%，可先賣 1/3 換成現金，其餘長抱", false)
                    else -> StopAdvice(if (active) trail else 0.0, tp, "長抱", if (active) "高點回落 ${fmt(rule.longTrailPct)}%（${fmt(trail)}）再減碼" else "長線不理短期波動", false)
                }
            }
        }
    }

    private fun fmt(v: Double) = if (v >= 100) "%.0f".format(v) else "%.2f".format(v).trimEnd('0').trimEnd('.')
}

/** 資金分配：保留生活費後，可投資的錢依比例分成長線與短線。金額為最小單位。 */
data class Allocation(
    val cash: Long,
    val reserve: Long,
    val longValue: Long,
    val shortValue: Long,
    val longTarget: Long,
    val shortTarget: Long,
) {
    /** 扣掉生活費後還能動用的現金 */
    val spareCash: Long get() = max(0, cash - reserve)
    val longRoom: Long get() = max(0, min(longTarget - longValue, spareCash))
    val shortRoom: Long get() = max(0, min(shortTarget - shortValue, spareCash - longRoom))
    val investable: Long get() = longTarget + shortTarget
    /** 生活費不夠保留時的缺口 */
    val reserveShort: Long get() = max(0, reserve - cash)
    val shortOver: Long get() = max(0, shortValue - shortTarget)

    companion object {
        fun of(cash: Long, reserve: Long, longValue: Long, shortValue: Long, longRatio: Double): Allocation {
            val investable = max(0, cash - reserve) + longValue + shortValue
            val longTarget = Math.round(investable * longRatio.coerceIn(0.0, 1.0))
            return Allocation(cash, reserve, longValue, shortValue, longTarget, investable - longTarget)
        }
    }
}
