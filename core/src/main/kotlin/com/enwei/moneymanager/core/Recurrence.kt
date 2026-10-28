package com.enwei.moneymanager.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

object Recurrence {
    /** 該月的指定日；超過月底則取月底（例：31 號在 2 月 → 2/28 或 2/29）。 */
    fun dayInMonth(ym: YearMonth, day: Int): LocalDate = ym.atDay(minOf(day, ym.lengthOfMonth()))

    /**
     * 回傳嚴格晚於 [after] 的下一次發生日。
     * @param anchor 規則起始日，WEEKLY 取其星期、MONTHLY 取 [dayOfMonth]（null 則取 anchor 的日）、YEARLY 取其月日。
     */
    fun next(
        frequency: Frequency,
        interval: Int,
        anchor: LocalDate,
        after: LocalDate,
        dayOfMonth: Int? = null,
    ): LocalDate {
        val step = interval.coerceAtLeast(1)
        if (anchor.isAfter(after)) {
            return when (frequency) {
                Frequency.MONTHLY -> dayInMonth(YearMonth.from(anchor), dayOfMonth ?: anchor.dayOfMonth)
                    .let { if (it.isBefore(anchor)) dayInMonth(YearMonth.from(anchor).plusMonths(step.toLong()), dayOfMonth ?: anchor.dayOfMonth) else it }
                else -> anchor
            }
        }
        return when (frequency) {
            Frequency.DAILY -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(anchor, after)
                anchor.plusDays((days / step + 1) * step)
            }
            Frequency.WEEKLY -> {
                val weeks = java.time.temporal.ChronoUnit.WEEKS.between(anchor, after)
                var d = anchor.plusWeeks(weeks / step * step)
                while (!d.isAfter(after)) d = d.plusWeeks(step.toLong())
                d
            }
            Frequency.MONTHLY -> {
                val dom = dayOfMonth ?: anchor.dayOfMonth
                val start = YearMonth.from(anchor)
                val months = java.time.temporal.ChronoUnit.MONTHS.between(start, YearMonth.from(after))
                var ym = start.plusMonths(months / step * step)
                var d = dayInMonth(ym, dom)
                while (!d.isAfter(after)) {
                    ym = ym.plusMonths(step.toLong())
                    d = dayInMonth(ym, dom)
                }
                d
            }
            Frequency.YEARLY -> {
                var year = anchor.year + ((after.year - anchor.year) / step) * step
                var d = dayInMonth(YearMonth.of(year, anchor.month), anchor.dayOfMonth)
                while (!d.isAfter(after)) {
                    year += step
                    d = dayInMonth(YearMonth.of(year, anchor.month), anchor.dayOfMonth)
                }
                d
            }
        }
    }

    /** 列出 (from, to] 期間內所有發生日，用於補上離線期間漏掉的週期交易。 */
    fun occurrences(
        frequency: Frequency, interval: Int, anchor: LocalDate, from: LocalDate, to: LocalDate, dayOfMonth: Int? = null,
    ): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var cur = from
        while (true) {
            val n = next(frequency, interval, anchor, cur, dayOfMonth)
            if (n.isAfter(to)) break
            out += n
            cur = n
            if (out.size > 1000) break
        }
        return out
    }

    fun weekdayName(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "一"; DayOfWeek.TUESDAY -> "二"; DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"; DayOfWeek.FRIDAY -> "五"; DayOfWeek.SATURDAY -> "六"; DayOfWeek.SUNDAY -> "日"
    }
}

/** 信用卡帳單週期：結帳日與繳款日。 */
/**
 * 結帳日遇週末的調整方式。
 * NONE：不調整；SAT_SUN_TO_MON：遇週六、日順延到週一；FRI_SAT_TO_SUN：遇週五、六延到週日。
 */
enum class StatementShift(val label: String) {
    NONE("不調整"), SAT_SUN_TO_MON("遇週六、日順延至週一"), FRI_SAT_TO_SUN("遇週五、六延至週日");

    fun apply(d: LocalDate): LocalDate = when (this) {
        NONE -> d
        SAT_SUN_TO_MON -> when (d.dayOfWeek) {
            java.time.DayOfWeek.SATURDAY -> d.plusDays(2); java.time.DayOfWeek.SUNDAY -> d.plusDays(1); else -> d
        }
        FRI_SAT_TO_SUN -> when (d.dayOfWeek) {
            java.time.DayOfWeek.FRIDAY -> d.plusDays(2); java.time.DayOfWeek.SATURDAY -> d.plusDays(1); else -> d
        }
    }
}

data class CardCycle(
    val statementDay: Int,
    val dueDay: Int,
    val shift: StatementShift = StatementShift.NONE,
    /** 繳款截止日＝實際結帳日＋幾天（依銀行規則）；null 時用固定的每月繳款日 [dueDay] */
    val dueOffsetDays: Int? = null,
) {
    /** 某個月的實際結帳日（已套用週末調整）；statementDay 為 -1 表示月底。 */
    fun statementIn(ym: YearMonth): LocalDate =
        shift.apply(if (statementDay < 0) ym.atEndOfMonth() else Recurrence.dayInMonth(ym, statementDay))

    /** 某次結帳的繳款截止日。 */
    fun dueFor(statement: LocalDate): LocalDate = dueOffsetDays?.let { statement.plusDays(it.toLong()) } ?: run {
        val ym = YearMonth.from(statement)
        val same = Recurrence.dayInMonth(ym, dueDay)
        if (same.isAfter(statement)) same else Recurrence.dayInMonth(ym.plusMonths(1), dueDay)
    }

    /** 今天（含）以前最近一次的結帳日。 */
    fun lastStatementOnOrBefore(today: LocalDate): LocalDate {
        val ym = YearMonth.from(today)
        val thisMonth = statementIn(ym)
        return if (thisMonth.isAfter(today)) statementIn(ym.minusMonths(1)) else thisMonth
    }

    /** 對應某繳款日的結帳日（繳款日在結帳日之後；若繳款日數字較小則屬於上個月的帳單）。 */
    fun statementDateFor(dueDate: LocalDate): LocalDate {
        if (dueOffsetDays != null) {
            val ym = YearMonth.from(dueDate)
            return (-3..1).map { statementIn(ym.plusMonths(it.toLong())) }.filter { !dueFor(it).isAfter(dueDate) }.maxOrNull()
                ?: statementIn(ym.minusMonths(1))
        }
        val dueYm = YearMonth.from(dueDate)
        val sameMonth = Recurrence.dayInMonth(dueYm, statementDay)
        return if (dueDay > statementDay && sameMonth.isBefore(dueDate)) statementIn(dueYm)
        else statementIn(dueYm.minusMonths(1))
    }

    /** 帳單期間 (上期結帳日, 本期結帳日]。 */
    fun periodEndingAt(statementDate: LocalDate): Pair<LocalDate, LocalDate> {
        val prev = statementIn(YearMonth.from(statementDate).minusMonths(1))
        return prev to statementDate
    }

    /** [after] 之後（不含）的下一個繳款截止日。 */
    fun nextDueDate(after: LocalDate): LocalDate {
        if (dueOffsetDays == null) return Recurrence.next(Frequency.MONTHLY, 1, after.withDayOfMonth(1), after, dueDay)
        val ym = YearMonth.from(after)
        return (-2..2).map { dueFor(statementIn(ym.plusMonths(it.toLong()))) }.filter { it.isAfter(after) }.min()
    }

    /**
     * 實際自動扣繳日：同一期帳單、指定日（[autopayDay]，空白則等於繳款日）。
     * 若指定日落在繳款日之後超過 10 天，視為前一個月（例如繳款日 3 號、扣繳日 28 號）。
     */
    fun autopayDateFor(dueDate: LocalDate, autopayDay: Int?): LocalDate {
        if (autopayDay == null) return dueDate
        val candidate = Recurrence.dayInMonth(YearMonth.from(dueDate), autopayDay)
        return if (candidate.isAfter(dueDate.plusDays(10))) Recurrence.dayInMonth(YearMonth.from(dueDate).minusMonths(1), autopayDay) else candidate
    }

    fun nextStatementDate(after: LocalDate): LocalDate {
        val ym = YearMonth.from(after)
        val c = statementIn(ym)
        return if (c.isAfter(after)) c else statementIn(ym.plusMonths(1))
    }
}
