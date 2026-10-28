package com.enwei.moneymanager.core

import kotlin.math.floor

/**
 * 一筆買進的庫存（券商「台股庫存明細」的一列）。賣出以先進先出扣除。
 * [cost] 為剩餘股數的價金＋手續費＋稅；[dividends] 為這批股票已領的現金股利（除息）。
 */
data class Lot(
    val tradeId: Long,
    val epochDay: Long,
    val shares: Double,
    val price: Double,
    val cost: Double,
    val dividends: Double,
) {
    /** 券商的「原幣成本」：價金＋手續費－已領股利 */
    val netCost: Double get() = cost - dividends

    /** 現在全部賣出的手續費＋交易稅（依這一批的股數與金額計算，同券商逐筆試算） */
    fun sellCost(market: Market, symbol: String, quote: Double, fees: FeeSettings): Double {
        val (f, t) = StockCalc.estimate(market, symbol, StockSide.SELL, shares, quote, fees)
        return f + t
    }

    /** 預估損益＝市值－賣出手續費與交易稅－原幣成本 */
    fun pnl(market: Market, symbol: String, quote: Double, fees: FeeSettings): Double =
        shares * quote - sellCost(market, symbol, quote, fees) - netCost
}

data class LotBook(val lots: List<Lot>, val realizedPnl: Double) {
    val shares: Double get() = lots.sumOf { it.shares }
    val cost: Double get() = lots.sumOf { it.cost }
    val dividends: Double get() = lots.sumOf { it.dividends }
    val netCost: Double get() = lots.sumOf { it.netCost }
}

object LotCalc {
    private const val EPS = 1e-9

    /**
     * 先進先出建立庫存。
     * - 買進：新增一批。
     * - 賣出：從最早的一批開始扣；已實現損益＝賣出淨額－扣掉部分的原幣成本（含該部分已領股利）。
     * - 現金股利：有指定 lotId 就記到那一批；否則依各批股數比例分配；沒有持股時直接算已實現。
     * - 股票股利：新增一批 0 成本的股數。
     */
    fun build(trades: List<TradeRecord>): LotBook {
        val lots = ArrayList<Lot>()
        var realized = 0.0
        // 同一天先處理買進，再處理股利與賣出
        val order = trades.sortedWith(compareBy<TradeRecord>({ it.epochDay }, { if (it.side == StockSide.BUY) 0 else 1 }, { it.id }))
        order.forEach { t ->
            when (t.side) {
                StockSide.BUY -> lots += Lot(t.id, t.epochDay, t.shares, t.price, t.shares * t.price + t.fee + t.tax, 0.0)
                StockSide.STOCK_DIVIDEND -> if (t.shares > EPS) lots += Lot(t.id, t.epochDay, t.shares, 0.0, 0.0, 0.0)
                StockSide.CASH_DIVIDEND -> {
                    val target = t.lotId?.let { id -> lots.indexOfFirst { it.tradeId == id && it.shares > EPS } }?.takeIf { it >= 0 }
                    val total = lots.sumOf { it.shares }
                    when {
                        target != null -> lots[target] = lots[target].let { it.copy(dividends = it.dividends + t.cashAmount) }
                        total > EPS -> lots.indices.forEach { i ->
                            val l = lots[i]
                            lots[i] = l.copy(dividends = l.dividends + t.cashAmount * l.shares / total)
                        }
                        else -> realized += t.cashAmount
                    }
                }
                StockSide.SELL -> {
                    var left = t.shares
                    val proceedsPerShare = if (t.shares > EPS) (t.shares * t.price - t.fee - t.tax) / t.shares else 0.0
                    val it = lots.listIterator()
                    while (left > EPS && it.hasNext()) {
                        val l = it.next()
                        if (l.shares <= EPS) continue
                        val q = minOf(left, l.shares)
                        val ratio = q / l.shares
                        val costOut = l.cost * ratio
                        val divOut = l.dividends * ratio
                        realized += q * proceedsPerShare - (costOut - divOut)
                        val rest = l.copy(shares = l.shares - q, cost = l.cost - costOut, dividends = l.dividends - divOut)
                        if (rest.shares <= EPS) it.remove() else it.set(rest)
                        left -= q
                    }
                    // 賣超過庫存（資料不完整）：多出的部分以成交淨額計入已實現
                    if (left > EPS) realized += left * proceedsPerShare
                }
            }
        }
        return LotBook(lots.filter { it.shares > EPS }, realized)
    }
}

/** 一次除息事件：除息日與每股現金股利。 */
data class DividendEvent(val exEpochDay: Long, val perShare: Double)

object DividendCalc {
    /**
     * 依券商「除息試算」分配一次除息到各批庫存：
     * 只計算除息日前一天仍持有的批次；股利總額＝總股數×每股股利（元以下捨去），
     * 各批＝股數×每股股利（四捨五入），最後一批用總額減去前面各批（尾差歸最後一批）。
     * 回傳 (買進交易 id, 金額)。
     */
    fun allocate(trades: List<TradeRecord>, event: DividendEvent): List<Pair<Long, Double>> {
        val held = LotCalc.build(trades.filter { it.epochDay < event.exEpochDay && it.side != StockSide.CASH_DIVIDEND })
            .lots.filter { it.shares > 0 }.sortedWith(compareBy({ it.epochDay }, { it.tradeId }))
        if (held.isEmpty() || event.perShare <= 0) return emptyList()
        val total = floor(held.sumOf { it.shares } * event.perShare + 1e-6)
        val out = ArrayList<Pair<Long, Double>>()
        var used = 0.0
        held.forEachIndexed { i, l ->
            val amt = if (i == held.lastIndex) (total - used).coerceAtLeast(0.0)
            else kotlin.math.round(l.shares * event.perShare).coerceAtMost(total - used)
            used += amt
            out += l.tradeId to amt
        }
        return out.filter { it.second > 0 }
    }
}

/** 元以下無條件捨去（券商帳單顯示用）。 */
fun floorYuan(v: Double): Double = floor(v + 1e-9)
