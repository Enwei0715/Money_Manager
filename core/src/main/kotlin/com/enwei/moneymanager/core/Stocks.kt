package com.enwei.moneymanager.core

import kotlin.math.floor
import kotlin.math.max

enum class StockSide { BUY, SELL, CASH_DIVIDEND, STOCK_DIVIDEND }

enum class Market { TW, US }

data class TradeRecord(
    val symbol: String,
    val side: StockSide,
    val shares: Double,
    val price: Double,
    val fee: Double,
    val tax: Double,
    val epochDay: Long,
    /** 現金股利總額（side = CASH_DIVIDEND 時使用） */
    val cashAmount: Double = 0.0,
)

data class Position(
    val symbol: String,
    val shares: Double,
    /** 含手續費的總成本 */
    val cost: Double,
    val realizedPnl: Double,
    val dividends: Double,
) {
    val avgCost: Double get() = if (shares > 0) cost / shares else 0.0
    fun marketValue(price: Double) = shares * price
    fun unrealizedPnl(price: Double) = marketValue(price) - cost
}

data class FeeSettings(
    /** 手續費折數，例如 0.6 代表六折 */
    val discount: Double = 1.0,
    val minFee: Double = 20.0,
    val usFeePerTrade: Double = 0.0,
)

object StockCalc {
    const val TW_FEE_RATE = 0.001425

    fun isTwEtf(symbol: String) = symbol.startsWith("00")

    fun twFee(amount: Double, settings: FeeSettings): Double {
        if (amount <= 0) return 0.0
        return max(settings.minFee, floor(amount * TW_FEE_RATE * settings.discount))
    }

    fun twTax(symbol: String, amount: Double, dayTrade: Boolean = false): Double {
        val rate = when {
            isTwEtf(symbol) -> 0.001
            dayTrade -> 0.0015
            else -> 0.003
        }
        return floor(amount * rate)
    }

    fun estimate(market: Market, symbol: String, side: StockSide, shares: Double, price: Double, settings: FeeSettings): Pair<Double, Double> {
        val amount = shares * price
        return when (market) {
            Market.TW -> twFee(amount, settings) to (if (side == StockSide.SELL) twTax(symbol, amount) else 0.0)
            Market.US -> settings.usFeePerTrade to 0.0
        }
    }

    /** 平均成本法計算持股與已實現損益。 */
    fun positions(trades: List<TradeRecord>): Map<String, Position> {
        val result = LinkedHashMap<String, Position>()
        trades.sortedBy { it.epochDay }.forEach { t ->
            val p = result[t.symbol] ?: Position(t.symbol, 0.0, 0.0, 0.0, 0.0)
            result[t.symbol] = when (t.side) {
                StockSide.BUY -> p.copy(shares = p.shares + t.shares, cost = p.cost + t.shares * t.price + t.fee + t.tax)
                StockSide.SELL -> {
                    val sold = minOf(t.shares, p.shares)
                    val costOut = p.avgCost * sold
                    val proceeds = t.shares * t.price - t.fee - t.tax
                    p.copy(shares = p.shares - sold, cost = p.cost - costOut, realizedPnl = p.realizedPnl + proceeds - costOut)
                }
                StockSide.CASH_DIVIDEND -> p.copy(dividends = p.dividends + t.cashAmount)
                StockSide.STOCK_DIVIDEND -> p.copy(shares = p.shares + t.shares)
            }
        }
        return result
    }

    /** 交易對交割帳戶的現金影響（正數流入、負數流出）。 */
    fun cashFlow(t: TradeRecord): Double = when (t.side) {
        StockSide.BUY -> -(t.shares * t.price + t.fee + t.tax)
        StockSide.SELL -> t.shares * t.price - t.fee - t.tax
        StockSide.CASH_DIVIDEND -> t.cashAmount
        StockSide.STOCK_DIVIDEND -> 0.0
    }
}
