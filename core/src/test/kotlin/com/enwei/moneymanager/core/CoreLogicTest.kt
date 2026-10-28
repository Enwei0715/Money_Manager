package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoreLogicTest {
    @Test fun monthlyClampsToMonthEnd() {
        val anchor = LocalDate.of(2026, 1, 31)
        assertEquals(LocalDate.of(2026, 2, 28), Recurrence.next(Frequency.MONTHLY, 1, anchor, LocalDate.of(2026, 1, 31), 31))
        assertEquals(LocalDate.of(2026, 3, 31), Recurrence.next(Frequency.MONTHLY, 1, anchor, LocalDate.of(2026, 2, 28), 31))
    }

    @Test fun monthlyFutureAnchor() {
        val anchor = LocalDate.of(2026, 11, 5)
        assertEquals(anchor, Recurrence.next(Frequency.MONTHLY, 1, anchor, LocalDate.of(2026, 10, 28), 5))
    }

    @Test fun occurrencesCatchUp() {
        val list = Recurrence.occurrences(Frequency.MONTHLY, 1, LocalDate.of(2026, 1, 10), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 10), 10)
        assertEquals(listOf(3, 4, 5, 6), list.map { it.monthValue })
    }

    @Test fun weeklyAndYearly() {
        val anchor = LocalDate.of(2026, 10, 5) // 週一
        assertEquals(LocalDate.of(2026, 10, 19), Recurrence.next(Frequency.WEEKLY, 2, anchor, LocalDate.of(2026, 10, 5)))
        assertEquals(LocalDate.of(2028, 2, 29), Recurrence.next(Frequency.YEARLY, 1, LocalDate.of(2024, 2, 29), LocalDate.of(2027, 3, 1)))
    }

    @Test fun cardCycle() {
        val c = CardCycle(statementDay = 5, dueDay = 20)
        assertEquals(LocalDate.of(2026, 10, 5), c.statementDateFor(LocalDate.of(2026, 10, 20)))
        val c2 = CardCycle(statementDay = 25, dueDay = 10)
        assertEquals(LocalDate.of(2026, 9, 25), c2.statementDateFor(LocalDate.of(2026, 10, 10)))
        assertEquals(LocalDate.of(2026, 11, 10), c2.nextDueDate(LocalDate.of(2026, 10, 28)))
    }

    @Test fun autopayDate() {
        val c = CardCycle(statementDay = 5, dueDay = 20)
        val due = LocalDate.of(2026, 10, 20)
        assertEquals(due, c.autopayDateFor(due, null))
        assertEquals(LocalDate.of(2026, 10, 18), c.autopayDateFor(due, 18))
        val c2 = CardCycle(statementDay = 15, dueDay = 3)
        assertEquals(LocalDate.of(2026, 10, 28), c2.autopayDateFor(LocalDate.of(2026, 11, 3), 28))
    }

    @Test fun twStockFeesAndPositions() {
        val s = FeeSettings(discount = 0.6, minFee = 20.0)
        assertEquals(20.0, StockCalc.twFee(10_000.0, s), 0.0)
        assertEquals(855.0, StockCalc.twFee(1_000_000.0, s), 0.0)
        // 永豐零股實例：5 股 × 3,745 = 18,725 → 手續費 26（無折扣、零股最低 1 元）
        val full = FeeSettings(discount = 1.0, minFee = 20.0, oddLotMinFee = 1.0)
        assertEquals(26.0, StockCalc.estimate(Market.TW, "3661", StockSide.BUY, 5.0, 3745.0, full).first, 0.0)
        assertEquals(30.0, StockCalc.estimate(Market.TW, "3661", StockSide.BUY, 5.0, 4280.0, full).first, 0.0)
        assertEquals(1.0, StockCalc.estimate(Market.TW, "2884", StockSide.BUY, 1.0, 30.0, full).first, 0.0)
        assertEquals(20.0, StockCalc.estimate(Market.TW, "2884", StockSide.BUY, 1000.0, 10.0, full).first, 0.0)
        // 永豐 2 折月退：實收 102，2 折後 floor(72,375 × 0.1425% × 0.2) = 20，退 82
        assertEquals(82.0, StockCalc.rebate(102.0, 72375.0, 0.2, 1.0), 0.0)
        assertEquals(0.0, StockCalc.rebate(102.0, 72375.0, 1.0, 1.0), 0.0)
        assertEquals(3000.0, StockCalc.twTax("2330", 1_000_000.0), 0.0)
        assertEquals(1000.0, StockCalc.twTax("0050", 1_000_000.0), 0.0)

        val trades = listOf(
            TradeRecord("2330", StockSide.BUY, 1000.0, 500.0, 0.0, 0.0, 1),
            TradeRecord("2330", StockSide.BUY, 1000.0, 600.0, 0.0, 0.0, 2),
            TradeRecord("2330", StockSide.SELL, 500.0, 700.0, 0.0, 0.0, 3),
        )
        val p = StockCalc.positions(trades)["2330"]!!
        assertEquals(1500.0, p.shares, 0.0)
        assertEquals(550.0, p.avgCost, 1e-9)
        assertEquals(75_000.0, p.realizedPnl, 1e-9)
    }

    @Test fun fxAndMoney() {
        val fx = FxConverter("TWD", mapOf("USD" to 0.03125))
        assertEquals(320000L, fx.convert(10000, "USD"))
        assertEquals("$1,280", Money.format(128000, "TWD"))
        assertEquals("-US$15.49", Money.format(-1549, "USD"))
        assertEquals(23, VersionUtil.codeFromTag("v1.0.23"))
    }

    @Test fun categoryGuess() {
        assertEquals("便利商店", CategoryGuesser.guess("7-ELEVEN 台北門市"))
        assertEquals("自訂", CategoryGuesser.guess("全聯福利中心", mapOf("全聯" to "自訂")))
        assertTrue(CategoryGuesser.guess("不知名店家") == null)
    }
}

/** 以永豐「台股庫存明細」實際三筆 3661 世芯-KY 驗證（現價 3,800、手續費無折扣、零股最低 1 元）。 */
class LotCalcTest {
    private val fees = FeeSettings(discount = 1.0, minFee = 20.0, oddLotMinFee = 1.0)
    private val trades = listOf(
        TradeRecord("3661", StockSide.BUY, 15.0, 4825.0, 102.0, 0.0, 20586, id = 1),   // 2026/05/13
        TradeRecord("3661", StockSide.BUY, 5.0, 4280.0, 30.0, 0.0, 20640, id = 2),     // 2026/07/06
        TradeRecord("3661", StockSide.BUY, 5.0, 3745.0, 26.0, 0.0, 20649, id = 3),     // 2026/07/15
        TradeRecord("3661", StockSide.CASH_DIVIDEND, 0.0, 0.0, 0.0, 0.0, 20680, cashAmount = 489.0, id = 4, lotId = 1),
        TradeRecord("3661", StockSide.CASH_DIVIDEND, 0.0, 0.0, 0.0, 0.0, 20680, cashAmount = 163.0, id = 5, lotId = 2),
        TradeRecord("3661", StockSide.CASH_DIVIDEND, 0.0, 0.0, 0.0, 0.0, 20680, cashAmount = 162.0, id = 6, lotId = 3),
    )

    @Test fun matchesBroker() {
        val book = LotCalc.build(trades)
        assertEquals(3, book.lots.size)
        val byId = book.lots.associateBy { it.tradeId }
        // 原幣成本
        assertEquals(71988.0, byId.getValue(1).netCost, 1e-6)
        assertEquals(21267.0, byId.getValue(2).netCost, 1e-6)
        assertEquals(18589.0, byId.getValue(3).netCost, 1e-6)
        // 預估損益
        assertEquals(-15240.0, byId.getValue(1).pnl(Market.TW, "3661", 3800.0, fees), 1e-6)
        assertEquals(-2351.0, byId.getValue(2).pnl(Market.TW, "3661", 3800.0, fees), 1e-6)
        assertEquals(327.0, byId.getValue(3).pnl(Market.TW, "3661", 3800.0, fees), 1e-6)
        assertEquals(25.0, book.shares, 0.0)
        assertEquals(0.0, book.realizedPnl, 1e-9)
    }

    @Test fun fifoSell() {
        val book = LotCalc.build(
            trades + TradeRecord("3661", StockSide.SELL, 20.0, 4000.0, 114.0, 240.0, 20700, id = 7),
        )
        // 先賣掉 5/13 的 15 股與 7/06 的 5 股，剩 7/15 那批
        assertEquals(listOf(3L), book.lots.map { it.tradeId })
        val proceeds = 20 * 4000.0 - 114 - 240
        assertEquals(proceeds - (71988.0 + 21267.0), book.realizedPnl, 1e-6)
    }

    @Test fun autoDividendMatchesBroker() {
        val buys = trades.filter { it.side == StockSide.BUY }
        // 永豐除息試算：489、163、162（總額 814）
        val alloc = DividendCalc.allocate(buys, DividendEvent(20680, 32.5996)).toMap()
        assertEquals(489.0, alloc.getValue(1), 0.0)
        assertEquals(163.0, alloc.getValue(2), 0.0)
        assertEquals(162.0, alloc.getValue(3), 0.0)
        // 除息日之後才買的那批不分配
        val late = DividendCalc.allocate(buys, DividendEvent(20645, 32.5996)).toMap()
        assertEquals(setOf(1L, 2L), late.keys)
    }

    @Test fun stockDividend() {
        val buys = trades.filter { it.side == StockSide.BUY }
        // 每股配 0.05 股：15 股→0（0.75 捨去）、5 股→0；1000 股→50
        assertEquals(emptyList<Pair<Long, Double>>(), DividendCalc.allocateStock(buys, DividendEvent(20680, 0.0, 0.05)))
        val big = listOf(TradeRecord("2884", StockSide.BUY, 1000.0, 30.0, 20.0, 0.0, 1, id = 9))
        assertEquals(listOf(9L to 50.0), DividendCalc.allocateStock(big, DividendEvent(10, 0.0, 0.05)))
        // 配股後成為一批 0 成本的新庫存
        val book = LotCalc.build(big + TradeRecord("2884", StockSide.STOCK_DIVIDEND, 50.0, 0.0, 0.0, 0.0, 10, id = 10, lotId = 9))
        assertEquals(1050.0, book.shares, 0.0)
        assertEquals(30020.0, book.cost, 1e-9)
    }

    @Test fun fillsExplainFee() {
        // 5/13 買 15 股 @4,825：一次成交 103，分 10 股＋5 股兩次成交各自捨去 = 68 + 34 = 102（帳單金額）
        val fees = FeeSettings(discount = 1.0, minFee = 20.0, oddLotMinFee = 1.0)
        assertEquals(103.0, StockCalc.twFee(72375.0, fees, oddLot = true), 0.0)
        assertEquals(102.0, StockCalc.twFee(48250.0, fees, true) + StockCalc.twFee(24125.0, fees, true), 0.0)
    }

    @Test fun proportionalDividend() {
        val book = LotCalc.build(
            listOf(
                TradeRecord("2330", StockSide.BUY, 1000.0, 500.0, 0.0, 0.0, 1, id = 1),
                TradeRecord("2330", StockSide.BUY, 3000.0, 600.0, 0.0, 0.0, 2, id = 2),
                TradeRecord("2330", StockSide.CASH_DIVIDEND, 0.0, 0.0, 0.0, 0.0, 3, cashAmount = 400.0, id = 3),
            ),
        )
        assertEquals(100.0, book.lots[0].dividends, 1e-9)
        assertEquals(300.0, book.lots[1].dividends, 1e-9)
    }
}

class StatementShiftTest {
    @Test fun weekendShift() {
        // 2026/10/17 是週六
        val cycle = CardCycle(17, 2, StatementShift.FRI_SAT_TO_SUN)
        assertEquals(java.time.LocalDate.of(2026, 10, 18), cycle.statementIn(java.time.YearMonth.of(2026, 10)))
        // 2026/04/17 是週五 → 週日 4/19
        assertEquals(java.time.LocalDate.of(2026, 4, 19), cycle.statementIn(java.time.YearMonth.of(2026, 4)))
        // 週六順延到週一
        assertEquals(java.time.LocalDate.of(2026, 10, 19), CardCycle(17, 2, StatementShift.SAT_SUN_TO_MON).statementIn(java.time.YearMonth.of(2026, 10)))
        // 10/17 當天還沒到實際結帳日（10/18），最近一次結帳日是 9/17（週四）
        assertEquals(java.time.LocalDate.of(2026, 9, 17), cycle.lastStatementOnOrBefore(java.time.LocalDate.of(2026, 10, 17)))
        assertEquals(java.time.LocalDate.of(2026, 10, 18), cycle.lastStatementOnOrBefore(java.time.LocalDate.of(2026, 10, 18)))
        assertEquals(java.time.LocalDate.of(2026, 10, 17), CardCycle(17, 2).statementIn(java.time.YearMonth.of(2026, 10)))
    }
}

class TaiwanBanksTest {
    @Test fun lookupAndOffset() {
        assertEquals("國泰世華", TaiwanBanks.find("013")!!.name)
        assertEquals("國泰世華", TaiwanBanks.find("13")!!.name)
        assertEquals("永豐銀行", TaiwanBanks.find("永豐信用卡")!!.name)
        assertEquals("連線銀行（LINE Bank）", TaiwanBanks.find("824")!!.name)
        // 永豐：結帳日 16 日＋15 天 = 10/31（週六）→ 順延至 11/2（週一）
        val c = CardCycle(16, 1, dueOffsetDays = 15)
        val s = c.statementIn(java.time.YearMonth.of(2026, 10))
        assertEquals(java.time.LocalDate.of(2026, 11, 2), c.dueFor(s))
        assertEquals(java.time.LocalDate.of(2026, 11, 2), c.nextDueDate(java.time.LocalDate.of(2026, 10, 20)))
        assertEquals(s, c.statementDateFor(java.time.LocalDate.of(2026, 11, 2)))
        // 彰化：月底結帳＋18 天
        val ch = CardCycle(-1, 1, dueOffsetDays = 18)
        assertEquals(java.time.LocalDate.of(2026, 2, 28), ch.statementIn(java.time.YearMonth.of(2026, 2)))
        assertEquals(java.time.LocalDate.of(2026, 3, 18), ch.dueFor(ch.statementIn(java.time.YearMonth.of(2026, 2))))
        // 舊模式（固定繳款日）不變
        assertEquals(java.time.LocalDate.of(2026, 11, 2), CardCycle(17, 2).nextDueDate(java.time.LocalDate.of(2026, 10, 20)))
    }
}

class DueRuleTest {
    @Test fun dueFromNominalAndRollToMonday() {
        // 國泰：結帳日 17 日、結帳日＋16 天；結帳日遇週五六延至週日
        val c = CardCycle(17, 1, StatementShift.FRI_SAT_TO_SUN, 16)
        // 2026/10/17（週六）實際結帳 10/18，但截止日用名目 10/17＋16 = 11/2（週一）
        val st = c.statementIn(java.time.YearMonth.of(2026, 10))
        assertEquals(java.time.LocalDate.of(2026, 10, 18), st)
        assertEquals(java.time.LocalDate.of(2026, 11, 2), c.dueFor(st))
        // 2026/05/17 是週日 → 名目＋16 = 6/2（週二）
        assertEquals(java.time.LocalDate.of(2026, 6, 2), c.dueFor(c.statementIn(java.time.YearMonth.of(2026, 5))))
        // 截止日落在週六 → 順延週一：2026/08/17＋16 = 9/2（週三）不變；找一個週六：2026/09/17+16=10/3（週六）→ 10/5
        assertEquals(java.time.LocalDate.of(2026, 10, 5), c.dueFor(c.statementIn(java.time.YearMonth.of(2026, 9))))
        assertEquals(c.statementIn(java.time.YearMonth.of(2026, 9)), c.statementDateFor(java.time.LocalDate.of(2026, 10, 5)))
        assertEquals(java.time.LocalDate.of(2026, 10, 5), c.nextDueDate(java.time.LocalDate.of(2026, 9, 20)))
    }
}
