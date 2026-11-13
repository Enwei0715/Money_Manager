package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

class PeriodsTest {
    @Test fun naturalMonth() {
        assertEquals(LocalDate.of(2026, 2, 1) to LocalDate.of(2026, 2, 28), Periods.month(YearMonth.of(2026, 2), 1))
    }

    @Test fun customStartDay() {
        assertEquals(LocalDate.of(2026, 9, 25) to LocalDate.of(2026, 10, 24), Periods.month(YearMonth.of(2026, 10), 25))
        assertEquals(YearMonth.of(2026, 10), Periods.monthOf(LocalDate.of(2026, 10, 24), 25))
        assertEquals(YearMonth.of(2026, 11), Periods.monthOf(LocalDate.of(2026, 10, 25), 25))
    }

    @Test fun week() {
        assertEquals(LocalDate.of(2026, 10, 26), Periods.week(LocalDate.of(2026, 10, 28), DayOfWeek.MONDAY).first)
        assertEquals(LocalDate.of(2026, 10, 25), Periods.week(LocalDate.of(2026, 10, 28), DayOfWeek.SUNDAY).first)
    }

    @Test fun fullFormat() {
        // 台幣沒有輔幣：一律整數（四捨五入）；外幣保留兩位小數
        assertEquals("1,000", Money.full(100000))
        assertEquals("-2,574", Money.full(-257400))
        assertEquals("$ 40", Money.withSymbol(4000))
        assertEquals("$ 143", Money.withSymbol(14307))
        assertEquals("$ 1,861", Money.withSymbol(186050))
        assertEquals("US$ 12.50", Money.withSymbol(1250, "USD"))
        assertEquals("1,000.00", Money.full(100000, "USD"))
    }
}
