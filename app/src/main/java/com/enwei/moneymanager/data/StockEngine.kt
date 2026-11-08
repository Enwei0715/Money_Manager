package com.enwei.moneymanager.data

import com.enwei.moneymanager.net.MarketScan
import com.enwei.moneymanager.net.TwMarket
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 每日報告的選股引擎（全部在手機上計算，不花 AI 額度）：
 * 大盤狀態 → 全市場初篩 → 五大面向打分（趨勢動能、法人籌碼、基本面、消息面、風險）→ 時光機模擬。
 * AI 只拿到整理好的候選表，負責判讀與撰寫。
 */
object StockEngine {
    /** 大盤狀態：多頭／盤整／空頭，以及給 AI 的一句描述。 */
    data class Regime(val label: String, val text: String)

    /** 時光機：用過去一年的日報酬重播未來 [days] 個交易日 [paths] 次。 */
    data class Sim(val target: Int, val stop: Int, val none: Int, val expR: Double, val entry: Double, val stopPrice: Double, val targetPrice: Double)

    /** 一檔候選股的所有數字與分數（0–100）。 */
    data class Candidate(
        val code: String, val name: String, val industry: String,
        val close: Double, val ret5: Double, val ret20: Double, val ret60: Double?, val rs20: Double?,
        val aboveMa20: Boolean, val aboveMa60: Boolean, val offHigh: Double, val volRatio: Double?,
        val foreign5: Long, val trust5: Long, val foreignStreak: Int, val trustStreak: Int, val instPctVol: Double?,
        val revYoy: Double?, val revMom: Double?, val revHigh: Boolean, val epsLast4: Double?, val epsYoy: Double?,
        val pe: Double?, val yield: Double?, val newsCount: Int, val newsSent: Double, val newsTitle: String?,
        val vol: Double, val sTrend: Int, val sChip: Int, val sFund: Int, val sNews: Int, val sRisk: Int, val score: Int,
        val sim: Sim?, val holding: Boolean,
    ) {
        /** 卡片上的依據標籤（最重要的 4 個）。 */
        fun tags(): List<String> = buildList {
            if (foreignStreak >= 3) add("外資連買 $foreignStreak 天") else if (foreignStreak <= -3) add("外資連賣 ${-foreignStreak} 天")
            if (trustStreak >= 3) add("投信連買 $trustStreak 天")
            if (revHigh) add("營收創 12 個月新高")
            revYoy?.let { if (it >= 20) add("營收年增 %+.0f%%".format(it)) else if (it <= -15) add("營收年減 %.0f%%".format(it)) }
            epsYoy?.let { if (abs(it) >= 15) add("EPS 年增 %+.0f%%".format(it)) }
            rs20?.let { if (it >= 5) add("強於大盤 %+.0f%%".format(it)) else if (it <= -5) add("弱於大盤 %+.0f%%".format(it)) }
            if (aboveMa20 && aboveMa60) add("站上月季線") else if (!aboveMa20 && !aboveMa60) add("跌破月季線")
            volRatio?.let { if (it >= 1.5) add("量增 %.1f 倍".format(it)) }
            if (offHigh > -3) add("近波段高點")
        }.take(4)

        /** 給 AI 的一行（只放數字，省 token）。 */
        fun row(): String = listOf(
            "$code $name", industry.ifBlank { "?" }, "分$score(趨$sTrend 籌$sChip 基$sFund 消$sNews 險$sRisk)",
            "20日${"%+.0f".format(ret20)}%" + (rs20?.let { " RS${"%+.0f".format(it)}" } ?: ""),
            (if (aboveMa20) "↑月" else "↓月") + (if (aboveMa60) "↑季" else "↓季") + " 距高${"%.0f".format(offHigh)}%",
            "外5日${fmtLots(foreign5)}/連${foreignStreak} 投5日${fmtLots(trust5)}/連${trustStreak}",
            "營收YoY${revYoy?.let { "%+.0f".format(it) } ?: "?"}%" + (if (revHigh) "新高" else ""),
            "EPS近4季${epsLast4?.let { "%.1f".format(it) } ?: "?"} YoY${epsYoy?.let { "%+.0f".format(it) } ?: "?"}%",
            "PE${pe?.let { "%.0f".format(it) } ?: "?"} 殖${yield?.let { "%.1f".format(it) } ?: "?"}%",
            "新聞${newsCount}則${if (newsSent > 0.2) "偏多" else if (newsSent < -0.2) "偏空" else "中性"}" + (newsTitle?.let { "「${it.take(22)}」" } ?: ""),
            sim?.let { "時光機 目標${it.target}% 停損${it.stop}% 期望${"%+.2f".format(it.expR)}R" } ?: "",
        ).filter { it.isNotBlank() }.joinToString("｜")
    }

    private fun fmtLots(shares: Long): String = (shares / 1000).let { if (it >= 0) "+${it}張" else "${it}張" }

    /** 大盤狀態：加權指數相對季線、年線，與全市場上漲家數比例。 */
    fun regime(index: List<Double>, upRatio: Double?): Regime {
        if (index.size < 60) return Regime("不明", "加權指數資料不足")
        val last = index.last()
        val ma60 = index.takeLast(60).average()
        val ma240 = if (index.size >= 240) index.takeLast(240).average() else null
        val r20 = MarketScan.ret(index, 20) ?: 0.0
        val label = when {
            last > ma60 && (ma240 == null || last > ma240) && r20 > 0 -> "多頭"
            last < ma60 && ma240 != null && last < ma240 -> "空頭"
            else -> "盤整"
        }
        return Regime(label, "加權 ${"%.0f".format(last)}，季線 ${"%.0f".format(ma60)}" + (ma240?.let { "、年線 ${"%.0f".format(it)}" } ?: "") +
            "，20 日 ${"%+.1f".format(r20)}%" + (upRatio?.let { "，今日上漲家數 ${"%.0f".format(it * 100)}%" } ?: "") + "→ $label")
    }

    /** 0–100 的分數（把數值線性對應到區間，超出就截斷）。 */
    private fun scale(v: Double?, lo: Double, hi: Double): Double = if (v == null) 50.0 else ((v - lo) / (hi - lo) * 100).coerceIn(0.0, 100.0)

    /** 連續買超（正）或賣超（負）的天數。 */
    private fun streak(xs: List<Long>): Int {
        if (xs.isEmpty()) return 0
        val sign = xs.last().compareTo(0)
        if (sign == 0) return 0
        var n = 0
        for (x in xs.asReversed()) { if (x.compareTo(0) == sign) n++ else break }
        return n * sign
    }

    /**
     * 算一檔的五大面向分數。[closes]、[volumes] 為近半年日線；[inst] 近 10 日三大法人；
     * [epsQ] 近 8 季 EPS；[revHist] 近 13 個月營收。
     */
    fun candidate(
        code: String, name: String, industry: String, closes: List<Double>, volumes: List<Double>, indexR20: Double?,
        inst: List<TwMarket.Inst>, rev: TwMarket.Revenue?, revHist: List<com.enwei.moneymanager.net.TwData.MonthRevenue>, epsQ: List<Double>,
        valuation: TwMarket.Valuation?, newsCount: Int, newsSent: Double, newsTitle: String?, holding: Boolean, regime: String,
    ): Candidate? {
        if (closes.size < 40) return null
        val last = closes.last()
        val ret5 = MarketScan.ret(closes, 5) ?: 0.0
        val ret20 = MarketScan.ret(closes, 20) ?: return null
        val ret60 = MarketScan.ret(closes, 60)
        val ma20 = closes.takeLast(20).average()
        val ma60 = closes.takeLast(min(60, closes.size)).average()
        val hi = closes.takeLast(min(120, closes.size)).max()
        val offHigh = (last / hi - 1) * 100
        val v5 = volumes.takeLast(5).filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
        val v20 = volumes.takeLast(20).filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()
        val volRatio = if (v5 != null && v20 != null && v20 > 0) v5 / v20 else null
        val rs20 = indexR20?.let { ret20 - it }
        // 日報酬波動（年化）
        val rets = closes.zipWithNext { a, b -> ln(b / a) }.takeLast(60)
        val mean = rets.average()
        val vol = sqrt(rets.sumOf { (it - mean) * (it - mean) } / max(1, rets.size - 1)) * sqrt(250.0) * 100

        val f5 = inst.takeLast(5).sumOf { it.foreign }
        val t5 = inst.takeLast(5).sumOf { it.trust }
        val fStreak = streak(inst.map { it.foreign })
        val tStreak = streak(inst.map { it.trust })
        val instPct = if (v5 != null && v5 > 0) inst.takeLast(5).sumOf { it.total }.toDouble() / (v5 * 5) * 100 else null

        val revHigh = revHist.size >= 13 && revHist.last().revenue >= revHist.takeLast(13).dropLast(1).maxOf { it.revenue }
        val last4 = if (epsQ.size >= 4) epsQ.takeLast(4).sum() else null
        val prev4 = if (epsQ.size >= 8) epsQ.takeLast(8).take(4).sum() else null
        val epsYoy = if (last4 != null && prev4 != null && abs(prev4) > 0.05) (last4 - prev4) / abs(prev4) * 100 else null

        val sTrend = (scale(ret20, -15.0, 25.0) * 0.3 + scale(rs20, -10.0, 15.0) * 0.3 + (if (last > ma20) 60.0 else 20.0) * 0.1 +
            (if (last > ma60) 70.0 else 20.0) * 0.15 + scale(offHigh, -25.0, 0.0) * 0.15).toInt()
        val sChip = (scale(instPct, -15.0, 15.0) * 0.4 + scale(fStreak.toDouble(), -5.0, 5.0) * 0.3 + scale(tStreak.toDouble(), -3.0, 5.0) * 0.3).toInt()
        val sFund = (scale(rev?.yoy, -20.0, 40.0) * 0.35 + (if (revHigh) 85.0 else 45.0) * 0.15 + scale(epsYoy, -30.0, 50.0) * 0.3 +
            scale(valuation?.pe?.let { -it }, -40.0, -8.0) * 0.2).toInt()
        val sNews = (scale(newsSent, -1.0, 1.0) * 0.6 + scale(newsCount.toDouble(), 0.0, 8.0) * 0.4).toInt()
        val sRisk = (100 - scale(vol, 20.0, 70.0)).toInt()
        // 權重依大盤狀態調整：空頭時風險與基本面比重提高
        val w = when (regime) {
            "多頭" -> listOf(0.30, 0.25, 0.25, 0.10, 0.10)
            "空頭" -> listOf(0.20, 0.20, 0.30, 0.10, 0.20)
            else -> listOf(0.25, 0.25, 0.25, 0.10, 0.15)
        }
        val score = (sTrend * w[0] + sChip * w[1] + sFund * w[2] + sNews * w[3] + sRisk * w[4]).toInt().coerceIn(0, 100)
        return Candidate(code, name, industry, last, ret5, ret20, ret60, rs20, last > ma20, last > ma60, offHigh, volRatio,
            f5, t5, fStreak, tStreak, instPct, rev?.yoy, rev?.mom, revHigh, last4, epsYoy, valuation?.pe, valuation?.yield,
            newsCount, newsSent, newsTitle, vol, sTrend, sChip, sFund, sNews, sRisk, score, null, holding)
    }

    /**
     * 時光機（蒙地卡羅）：以近一年日報酬做區塊重抽（5 日一塊，保留連漲連跌的特性），
     * 從今天收盤進場，重播接下來 [days] 個交易日 [paths] 次，看先碰到目標價、先碰到停損，或都沒碰到。
     * 停損 = 1.5 倍日波動 × √5（4%～10%），目標 = 2 倍停損（2R）。
     */
    fun simulate(closes: List<Double>, days: Int = 20, paths: Int = 3000, seed: Long = 7L): Sim? {
        if (closes.size < 60) return null
        val rets = closes.takeLast(251).zipWithNext { a, b -> b / a - 1 }
        if (rets.size < 50) return null
        val entry = closes.last()
        val sd = sqrt(rets.sumOf { it * it } / rets.size)
        val k = (sd * 1.5 * sqrt(5.0)).coerceIn(0.04, 0.10)
        val stop = entry * (1 - k)
        val target = entry * (1 + 2 * k)
        val rnd = java.util.Random(seed + closes.size + (entry * 100).toLong())
        var hitT = 0; var hitS = 0; var sumR = 0.0
        val block = 5
        repeat(paths) {
            var p = entry
            var d = 0
            var result = 0 // 1 目標、-1 停損
            while (d < days && result == 0) {
                val start = rnd.nextInt(rets.size - block)
                var j = 0
                while (j < block && d < days && result == 0) {
                    p *= 1 + rets[start + j]
                    if (p <= stop) result = -1 else if (p >= target) result = 1
                    j++; d++
                }
            }
            when (result) {
                1 -> { hitT++; sumR += 2.0 }
                -1 -> { hitS++; sumR -= 1.0 }
                else -> sumR += (p / entry - 1) / k
            }
        }
        val t = hitT * 100 / paths
        val s = hitS * 100 / paths
        return Sim(t, s, 100 - t - s, sumR / paths, entry, stop, target)
    }
}
