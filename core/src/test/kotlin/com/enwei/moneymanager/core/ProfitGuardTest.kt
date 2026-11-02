package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfitGuardTest {
    @Test fun excessSharesFromUserExample() {
        // 成本 60 萬、100 股；漲到 1 股 6,500 → 市值 65 萬，多 5 萬 ≈ 7 股
        val a = ProfitGuard.advise(GuardInput(shares = 100.0, price = 6500.0, principal = 600_000.0))
        assertEquals(7.0, a.excessShares, 0.0)
        assertEquals(GuardAction.TRAILING, a.action)
        assertEquals(6500.0 * 0.97, a.takeProfit!!, 0.01)
    }

    @Test fun trailingStopMovesUpAndTriggers() {
        val up = ProfitGuard.advise(GuardInput(100.0, 7000.0, 600_000.0, peak = 6500.0))
        assertEquals(7000.0, up.peak!!, 0.0)
        val tp = up.takeProfit!!
        val down = ProfitGuard.advise(GuardInput(100.0, tp - 10, 600_000.0, peak = up.peak))
        assertEquals(GuardAction.SELL_EXCESS, down.action)
        assertEquals(down.excessShares, down.sellShares, 0.0)
    }

    @Test fun manualTakeProfitHigherWins() {
        val a = ProfitGuard.advise(GuardInput(100.0, 6500.0, 600_000.0, manualTakeProfit = 6450.0))
        assertEquals(6450.0, a.takeProfit!!, 0.0)
    }

    @Test fun noExcessHolds() {
        val a = ProfitGuard.advise(GuardInput(100.0, 5900.0, 600_000.0, peak = 7000.0))
        assertEquals(GuardAction.HOLD, a.action)
        assertNull(a.peak)
    }

    @Test fun hourlyMaBreaks() {
        // 跌破超過容許幅度（預設 1%）才算
        val one = ProfitGuard.advise(GuardInput(10.0, 100.0, 1200.0, hourMa20 = 103.0, hourMa50 = 95.0))
        assertEquals(GuardAction.REDUCE_HALF, one.action)
        assertEquals(5.0, one.sellShares, 0.0)
        val both = ProfitGuard.advise(GuardInput(10.0, 100.0, 1200.0, hourMa20 = 103.0, hourMa50 = 104.0), GuardRule(baseShares = 2.0))
        assertEquals(GuardAction.TO_BASE, both.action)
        assertEquals(8.0, both.sellShares, 0.0)
    }

    @Test fun smallDipWithinToleranceHolds() {
        // 只比 20MA 低 1%（在容許範圍內）：不賣
        val dip = ProfitGuard.advise(GuardInput(10.0, 100.0, 1200.0, hourMa20 = 101.0, hourMa50 = 95.0))
        assertEquals(GuardAction.HOLD, dip.action)
        // 容許幅度設 0：一跌破就減碼，賣出比例依設定
        val strict = ProfitGuard.advise(GuardInput(10.0, 100.0, 1200.0, hourMa20 = 101.0, hourMa50 = 95.0), GuardRule(tolerancePct = 0.0, reducePct = 30.0))
        assertEquals(GuardAction.REDUCE_HALF, strict.action)
        assertEquals(3.0, strict.sellShares, 0.0)
    }

    @Test fun reduceTrigger() {
        val a = ProfitGuard.advise(GuardInput(10.0, 4690.0, 40_000.0, reduceAt = 4700.0))
        assertEquals(GuardAction.REDUCE_TRIGGER, a.action)
        assertEquals(5.0, a.sellShares, 0.0)
    }

    @Test fun dailyTrend() {
        val close = (1..250).map { it.toDouble() }
        val t = ProfitGuard.dailyTrend(close)
        assertEquals(6, t.above.size)
    }
}
