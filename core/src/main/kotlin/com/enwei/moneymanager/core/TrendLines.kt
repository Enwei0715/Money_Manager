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
    data class Signal(val index: Int, val buy: Boolean, val reason: String)

    data class Params(
        val long: Boolean = false,
        val stopPct: Double = 8.0,
        val trailPct: Double = 8.0,
        val takeProfitPct: Double = 15.0,
    )

    fun scan(open: List<Double>, high: List<Double>, low: List<Double>, close: List<Double>, volume: List<Double>, p: Params): List<Signal> {
        val n = close.size
        if (n < 70) return emptyList()
        val ma20 = close.indices.map { Technicals.sma(close, 20, it + 1) }
        val ma60 = close.indices.map { Technicals.sma(close, 60, it + 1) }
        val out = ArrayList<Signal>()
        var entry: Double? = null
        var peak = 0.0
        var tookProfit = false
        var lastBuy = -99
        for (i in 61 until n) {
            val c = close[i]; val m20 = ma20[i] ?: continue; val m60 = ma60[i] ?: continue
            val p20 = ma20[i - 1] ?: continue; val p60 = ma60[i - 1] ?: continue
            val avgVol = (i - 20 until i).map { volume.getOrNull(it) ?: 0.0 }.average()
            val vol = volume.getOrNull(i) ?: 0.0
            if (entry == null) {
                val reason = when {
                    close[i - 1] <= p60 && c > m60 && m20 >= p20 -> "站回季線 ${"%.1f".format(m60)}，月線向上"
                    c > (i - 20 until i).maxOf { high[it] } && avgVol > 0 && vol > avgVol * 1.5 ->
                        "突破前 20 日高點，量增 ${"%.1f".format(vol / avgVol)} 倍"
                    !p.long && m20 > m60 && m20 > p20 && low[i] <= m20 * 1.01 && c > m20 && close[i - 1] > p20 -> "多頭回測月線不破"
                    else -> null
                }
                if (reason != null && i - lastBuy >= 5) {
                    out += Signal(i, true, reason); entry = c; peak = c; tookProfit = false; lastBuy = i
                }
            } else {
                peak = max(peak, high[i])
                val e = entry!!
                val tp = if (p.long) p.takeProfitPct * 2 else p.takeProfitPct
                val trailStop = peak * (1 - p.trailPct / 100)
                val reason = when {
                    !p.long && c < e * (1 - p.stopPct / 100) -> "停損：跌破買進價 ${"%.1f".format(e)} 的 −${"%.0f".format(p.stopPct)}%"
                    peak > e && c < trailStop && (!p.long || c > e) -> "移動停損：從高點 ${"%.1f".format(peak)} 回落 ${"%.0f".format(p.trailPct)}%"
                    !tookProfit && c >= e * (1 + tp / 100) -> "分批停利：獲利達 +${"%.0f".format(tp)}%（先賣 1/3）"
                    !p.long && close[i - 1] >= p60 && c < m60 -> "跌破季線 ${"%.1f".format(m60)}"
                    else -> null
                }
                if (reason != null) {
                    out += Signal(i, false, reason)
                    if (reason.startsWith("分批停利")) tookProfit = true else entry = null
                }
            }
        }
        return out
    }
}
