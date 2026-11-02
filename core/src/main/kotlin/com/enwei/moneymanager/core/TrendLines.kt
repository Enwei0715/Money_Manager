package com.enwei.moneymanager.core

import kotlin.math.abs
import kotlin.math.max

/**
 * 趨勢線：找出波段高點、低點，各用最近幾個點畫出壓力線與支撐線，判斷型態（通道、楔形、三角收斂…）。
 * 線型太亂（大部分收盤價不在兩條線之間）時回傳 null，畫面改用統計扇形。
 */
object TrendLines {
    data class Pivot(val index: Int, val price: Double, val high: Boolean)

    /** 直線：price = a + b × index（index 為整段資料的位置） */
    data class Line(val a: Double, val b: Double) {
        fun at(i: Double) = a + b * i
    }

    data class Result(
        val upper: Line,
        val lower: Line,
        val pivots: List<Pivot>,
        /** 型態名稱，例如「上升通道」 */
        val pattern: String,
        /** 型態的意義，例如「預示下跌風險」 */
        val hint: String,
        /** 1 偏多、-1 偏空、0 方向未明 */
        val bias: Int,
        /** 最新收盤已突破壓力線或跌破支撐線 */
        val breakout: String?,
        /** 兩條線從哪一根開始畫 */
        val start: Int,
        /** 收盤價落在兩線之間的比例 */
        val fit: Double,
    )

    /** 波段高低點：前後 [k] 根內的最高（最低）價。 */
    fun pivots(high: List<Double>, low: List<Double>, from: Int, k: Int = 4): List<Pivot> {
        val out = ArrayList<Pivot>()
        for (i in maxOf(from, k) until high.size - k) {
            val win = (i - k)..(i + k)
            if (win.all { high[it] <= high[i] } && win.count { high[it] == high[i] } == 1) out += Pivot(i, high[i], true)
            if (win.all { low[it] >= low[i] } && win.count { low[it] == low[i] } == 1) out += Pivot(i, low[i], false)
        }
        return out
    }

    private fun fit(points: List<Pivot>): Line {
        if (points.size == 1) return Line(points[0].price, 0.0)
        val xm = points.map { it.index.toDouble() }.average()
        val ym = points.map { it.price }.average()
        val sxx = points.sumOf { (it.index - xm) * (it.index - xm) }
        val b = if (sxx == 0.0) 0.0 else points.sumOf { (it.index - xm) * (it.price - ym) } / sxx
        return Line(ym - b * xm, b)
    }

    fun find(high: List<Double>, low: List<Double>, close: List<Double>, lookback: Int = 120): Result? {
        val n = close.size
        if (n < 40) return null
        val from = maxOf(0, n - lookback)
        val ps = pivots(high, low, from)
        val highs = ps.filter { it.high }.takeLast(3)
        val lows = ps.filter { !it.high }.takeLast(3)
        if (highs.size < 2 || lows.size < 2) return null
        var upper = fit(highs)
        var lower = fit(lows)
        val start = minOf(highs.first().index, lows.first().index)
        // 讓線貼著波段點：壓力線至少碰到最高的偏離點、支撐線至少碰到最低的偏離點
        upper = upper.copy(a = upper.a + highs.maxOf { it.price - upper.at(it.index.toDouble()) })
        lower = lower.copy(a = lower.a + lows.minOf { it.price - lower.at(it.index.toDouble()) })
        val last = close.last()
        val endU = upper.at((n - 1).toDouble()); val endL = lower.at((n - 1).toDouble())
        if (endU <= endL) return null
        // 收盤價在兩線之間（容許 2%）的比例
        val tol = 0.02
        val inside = (start until n).count { i -> close[i] <= upper.at(i.toDouble()) * (1 + tol) && close[i] >= lower.at(i.toDouble()) * (1 - tol) }
        val fitRatio = inside.toDouble() / (n - start)
        if (fitRatio < 0.8 || n - start < 15) return null
        // 每根 K 棒的斜率（%）
        val su = upper.b / last * 100
        val sl = lower.b / last * 100
        val flat = 0.04
        val parallel = abs(su - sl) <= max(abs(su), abs(sl)) * 0.4 + 0.02
        val (pattern, hint, bias) = when {
            parallel && abs(su) < flat && abs(sl) < flat -> Triple("箱型整理", "在壓力與支撐之間來回，等突破方向", 0)
            parallel && su > 0 && sl > 0 -> Triple("上升通道", "買低賣高：靠近支撐線偏買、靠近壓力線偏賣", 1)
            parallel && su < 0 && sl < 0 -> Triple("下降通道", "反彈到壓力線容易再跌，站上壓力線才轉強", -1)
            su < -flat / 2 && sl > flat / 2 -> Triple("三角收斂", "高點越來越低、低點越來越高，突破方向決定下一段", 0)
            su > 0 && sl > 0 && sl > su -> Triple("上升楔形", "漲勢趨緩，預示下跌風險", -1)
            su < 0 && sl < 0 && su < sl -> Triple("下降楔形", "跌勢趨緩，預示反轉上漲", 1)
            su > sl -> Triple("擴散型態", "高點越高、低點越低，波動放大", 0)
            else -> Triple("收斂整理", "波動縮小，等待突破", 0)
        }
        val breakout = when {
            last > endU * 1.01 -> "已向上突破壓力線"
            last < endL * 0.99 -> "已跌破支撐線"
            else -> null
        }
        return Result(upper, lower, highs + lows, pattern, hint, bias, breakout, start, fitRatio)
    }
}

/**
 * 依紀律參數在 K 線上找出買點、賣點（模擬：買進後才會有停損、停利、移動停損的賣點）。
 * - 買：站回季線、突破前 20 日高點且量增、多頭時回測月線不破
 * - 賣：短線停損、移動停損（從買進後最高價回落）、分批停利、跌破季線
 */
object TradeSignals {
    /** [tag] 是畫在 K 線上的短標籤，[reason] 是完整理由。 */
    data class Signal(val index: Int, val buy: Boolean, val reason: String, val tag: String = "")

    data class Params(
        val long: Boolean = false,
        val stopPct: Double = 8.0,
        val trailPct: Double = 8.0,
        val takeProfitPct: Double = 15.0,
    )

    private fun f(v: Double) = if (v >= 100) "%.0f".format(v) else "%.1f".format(v)

    fun scan(open: List<Double>, high: List<Double>, low: List<Double>, close: List<Double>, volume: List<Double>, p: Params): List<Signal> {
        val n = close.size
        if (n < 70) return emptyList()
        val ma20 = close.indices.map { Technicals.sma(close, 20, it + 1) }
        val ma60 = close.indices.map { Technicals.sma(close, 60, it + 1) }
        val out = ArrayList<Signal>()
        var entry: Double? = null
        var peak = 0.0
        var tookProfit = false
        var lastSignal = -99
        for (i in 61 until n) {
            val c = close[i]; val m20 = ma20[i] ?: continue; val m60 = ma60[i] ?: continue
            val p20 = ma20[i - 1] ?: continue; val p60 = ma60[i - 1] ?: continue
            val m20Ago = ma20[i - 5] ?: p20
            val avgVol = (i - 20 until i).map { volume.getOrNull(it) ?: 0.0 }.average()
            val vol = volume.getOrNull(i) ?: 0.0
            val upTrend = m20 > m20Ago && c > m60
            if (entry == null) {
                // 只在趨勢向上時買：月線上彎、收盤在季線之上
                val sig = when {
                    close[i - 1] <= p60 && c > m60 && m20 > m20Ago && c > m20 ->
                        Signal(i, true, "站回季線 ${f(m60)}，且在上彎的月線 ${f(m20)} 之上", "站回季線")
                    upTrend && m20 >= m60 && c > (i - 20 until i).maxOf { high[it] } && avgVol > 0 && vol > avgVol * 1.5 ->
                        Signal(i, true, "多頭中突破前 20 日高點，量增 ${"%.1f".format(vol / avgVol)} 倍", "突破")
                    !p.long && upTrend && m20 > m60 && low[i] <= m20 * 1.01 && c > m20 && close[i - 1] > p20 ->
                        Signal(i, true, "多頭回測月線 ${f(m20)} 不破", "回測月線")
                    else -> null
                }
                if (sig != null && i - lastSignal >= 5) {
                    out += sig; entry = c; peak = c; tookProfit = false; lastSignal = i
                }
            } else {
                peak = max(peak, high[i])
                val e = entry!!
                val tp = if (p.long) p.takeProfitPct * 2 else p.takeProfitPct
                val trailStop = peak * (1 - p.trailPct / 100)
                val sig = when {
                    !p.long && c < e * (1 - p.stopPct / 100) ->
                        Signal(i, false, "停損：跌破買進價 ${f(e)} 的 −${"%.0f".format(p.stopPct)}%（${f(e * (1 - p.stopPct / 100))}）", "停損")
                    peak > e && c < trailStop && (!p.long || c > e) ->
                        Signal(i, false, "移動停損：從高點 ${f(peak)} 回落超過 ${"%.0f".format(p.trailPct)}%（${f(trailStop)}）", "移動停損")
                    !tookProfit && c >= e * (1 + tp / 100) ->
                        Signal(i, false, "分批停利：獲利達 +${"%.0f".format(tp)}%（先賣 1/3）", "停利")
                    !p.long && close[i - 1] >= p60 && c < m60 -> Signal(i, false, "跌破季線 ${f(m60)}", "破季線")
                    else -> null
                }
                if (sig != null && i - lastSignal >= 2) {
                    out += sig; lastSignal = i
                    if (sig.tag == "停利") tookProfit = true else entry = null
                }
            }
        }
        return out
    }

    /** 目前狀態的依據（一條一條，true＝偏多、false＝偏空、null＝中性）與依紀律的結論。 */
    data class Status(val checks: List<Pair<String, Boolean?>>, val verdict: String, val bias: Int)

    fun status(high: List<Double>, close: List<Double>, p: Params, avgCost: Double?, signals: List<Signal>, dayLabel: (Int) -> String): Status? {
        val n = close.size
        if (n < 61) return null
        val c = close.last()
        val m20 = Technicals.sma(close, 20) ?: return null
        val m60 = Technicals.sma(close, 60) ?: return null
        val m20Ago = Technicals.sma(close, 20, n - 5) ?: m20
        val checks = ArrayList<Pair<String, Boolean?>>()
        checks += (if (c >= m20) "收盤 ${f(c)} 站上月線（20 日線）${f(m20)}" else "收盤 ${f(c)} 跌破月線（20 日線）${f(m20)}") to (c >= m20)
        checks += (if (c >= m60) "站上季線（60 日線）${f(m60)}" else "跌破季線（60 日線）${f(m60)}") to (c >= m60)
        checks += (if (m20 >= m20Ago) "月線上彎" else "月線下彎") to (m20 >= m20Ago)
        checks += (if (m20 >= m60) "多頭排列（月線在季線之上）" else "空頭排列（月線在季線之下）") to (m20 >= m60)
        val peak = high.takeLast(60).max()
        val trailStop = peak * (1 - p.trailPct / 100)
        checks += "近 60 日高點 ${f(peak)}，移動停損價 ${f(trailStop)}（${if (c >= trailStop) "未跌破" else "已跌破"}）" to (c >= trailStop)
        var verdict: String
        var bias: Int
        if (avgCost != null && avgCost > 0) {
            val gain = (c / avgCost - 1) * 100
            val stop = avgCost * (1 - p.stopPct / 100)
            val tp = avgCost * (1 + (if (p.long) p.takeProfitPct * 2 else p.takeProfitPct) / 100)
            checks += "成本 ${f(avgCost)}，報酬 ${"%+.1f".format(gain)}%" to (gain >= 0)
            if (!p.long) checks += "停損價 ${f(stop)}（${if (c >= stop) "未跌破" else "已跌破"}）" to (c >= stop)
            checks += "停利價 ${f(tp)}（${if (c >= tp) "已達到" else "未達到"}）" to null
            when {
                !p.long && c < stop -> { verdict = "依紀律：跌破停損價，停損出場"; bias = -1 }
                c < trailStop && (p.long.not() || c > avgCost) -> { verdict = "依紀律：跌破移動停損價，減碼或出場"; bias = -1 }
                c >= tp -> { verdict = "依紀律：達停利價，先賣 1/3，其餘用移動停損保護"; bias = 0 }
                !p.long && c < m60 -> { verdict = "依紀律：短線跌破季線，先減碼"; bias = -1 }
                c >= m20 && m20 >= m20Ago -> { verdict = "依紀律：站上上彎的月線，續抱"; bias = 1 }
                else -> { verdict = "依紀律：還沒觸發停損，續抱觀察，等站回月線"; bias = 0 }
            }
        } else {
            when {
                c >= m20 && m20 >= m20Ago && m20 >= m60 -> { verdict = "依紀律：多頭排列，可等回測月線不破再分批買"; bias = 1 }
                c < m60 && m20 < m20Ago -> { verdict = "依紀律：趨勢向下，先不買"; bias = -1 }
                else -> { verdict = "依紀律：方向未明，等站回季線或突破"; bias = 0 }
            }
        }
        signals.lastOrNull()?.takeIf { n - 1 - it.index <= 15 }?.let { sg ->
            checks += "最近紀律訊號：${dayLabel(sg.index)} ${if (sg.buy) "買" else "賣"}，${sg.reason}" to sg.buy
        }
        return Status(checks, verdict, bias)
    }
}
