package com.enwei.moneymanager.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** 依「每月起始日」與「每週起始日」設定計算期間。 */
object Periods {
    /** 例：起始日 25 時，「10 月」是 9/25 ~ 10/24。起始日 1 則為自然月。 */
    fun month(ym: YearMonth, startDay: Int): Pair<LocalDate, LocalDate> {
        if (startDay <= 1) return ym.atDay(1) to ym.atEndOfMonth()
        val start = Recurrence.dayInMonth(ym.minusMonths(1), startDay)
        val end = Recurrence.dayInMonth(ym, startDay).minusDays(1)
        return start to end
    }

    /** 某日期屬於哪個「帳務月」。 */
    fun monthOf(date: LocalDate, startDay: Int): YearMonth {
        if (startDay <= 1) return YearMonth.from(date)
        val ym = YearMonth.from(date)
        return if (date.isBefore(Recurrence.dayInMonth(ym, startDay))) ym else ym.plusMonths(1)
    }

    fun week(date: LocalDate, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> {
        val s = date.with(TemporalAdjusters.previousOrSame(weekStart))
        return s to s.plusDays(6)
    }
}
