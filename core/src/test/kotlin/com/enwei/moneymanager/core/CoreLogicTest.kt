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

    @Test fun invoiceCsv() {
        val csv = """
            表頭=M|載具自訂名稱|載具號碼|發票日期|商店統編|商店店名|發票號碼|總金額|發票狀態|
            明細=D|發票號碼|小計|品項名稱|
            M|手機條碼|/ABC1234|20261020|22555003|統一超商股份有限公司|AB12345678|85|開立|
            D|AB12345678|45|鮮奶|
            D|AB12345678|40|御飯糰|
            M|手機條碼|/ABC1234|20261021|12345678|某商店|CD87654321|100|作廢|
        """.trimIndent()
        val list = InvoiceCsvParser.parse(csv)
        assertEquals(1, list.size)
        assertEquals(8500L, list[0].amountMinor)
        assertEquals(2, list[0].items.size)
        assertEquals(LocalDate.of(2026, 10, 20), list[0].date)
        assertEquals(LocalDate.of(2024, 10, 28), InvoiceCsvParser.parseDate("113/10/28"))
    }

    @Test fun twStockFeesAndPositions() {
        val s = FeeSettings(discount = 0.6, minFee = 20.0)
        assertEquals(20.0, StockCalc.twFee(10_000.0, s), 0.0)
        assertEquals(855.0, StockCalc.twFee(1_000_000.0, s), 0.0)
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
