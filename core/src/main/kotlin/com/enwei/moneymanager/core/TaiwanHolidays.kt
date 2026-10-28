package com.enwei.moneymanager.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 台灣國定假日（行政院人事行政總處「政府行政機關辦公日曆表」）。
 * holidays：平日放假（含補假）；workdays：週末補行上班。銀行營業日同政府辦公日；2025 年勞動節金融機構亦休假。
 * 內建 2025～2027 年；之後的年份由 App 連網自動下載補上（[update]），沒有資料的年份只排除週末。
 */
data class YearCalendar(val holidays: Map<String, String>, val workdays: Set<String> = emptySet())

object TaiwanHolidays {
    private val builtIn: Map<Int, YearCalendar> = mapOf(
        2025 to YearCalendar(
            holidays = mapOf("0101" to "開國紀念日", "0127" to "小年夜", "0128" to "農曆除夕", "0129" to "春節", "0130" to "春節", "0131" to "春節", "0228" to "和平紀念日", "0403" to "補假", "0404" to "兒童節及民族掃墓節", "0501" to "勞動節（金融機構休假）", "0530" to "補假", "0929" to "補假", "1006" to "中秋節", "1010" to "國慶日", "1024" to "補假", "1225" to "行憲紀念日"),
            workdays = setOf("0208"),
        ),
        2026 to YearCalendar(
            holidays = mapOf("0101" to "開國紀念日", "0216" to "農曆除夕", "0217" to "春節", "0218" to "春節", "0219" to "春節", "0220" to "補假", "0227" to "補假", "0403" to "補假", "0406" to "補假", "0501" to "勞動節", "0619" to "端午節", "0925" to "中秋節", "0928" to "孔子誕辰紀念日/教師節", "1009" to "補假", "1026" to "補假", "1225" to "行憲紀念日"),
            workdays = setOf(),
        ),
        2027 to YearCalendar(
            holidays = mapOf("0101" to "開國紀念日", "0204" to "小年夜", "0205" to "農曆除夕", "0208" to "春節", "0209" to "補假", "0210" to "補假", "0301" to "補假", "0405" to "清明節", "0406" to "補假", "0430" to "補假", "0609" to "端午節", "0915" to "中秋節", "0928" to "孔子誕辰紀念日/教師節", "1011" to "補假", "1025" to "臺灣光復暨金門古寧頭大捷紀念日", "1224" to "補假", "1231" to "補假"),
            workdays = setOf(),
        ),
    )

    @Volatile private var downloaded: Map<Int, YearCalendar> = emptyMap()

    /** 加入下載的年份（不覆蓋內建資料）。 */
    fun update(years: Map<Int, YearCalendar>) { downloaded = downloaded + years.filterKeys { it !in builtIn } }

    fun knownYears(): Set<Int> = builtIn.keys + downloaded.keys

    private fun cal(year: Int) = builtIn[year] ?: downloaded[year]
    private fun key(d: LocalDate) = "%02d%02d".format(d.monthValue, d.dayOfMonth)

    /** 假日名稱（平日放假）；週末回傳 null。 */
    fun holidayName(d: LocalDate): String? = cal(d.year)?.holidays?.get(key(d))

    /** 銀行營業日：非週末（補班日除外）且非國定假日。 */
    fun isBusinessDay(d: LocalDate): Boolean {
        val c = cal(d.year)
        if (c != null && key(d) in c.workdays) return true
        if (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) return false
        return c?.holidays?.containsKey(key(d)) != true
    }

    /** 當天或之後的第一個營業日。 */
    fun nextBusinessDay(d: LocalDate): LocalDate {
        var x = d
        repeat(40) { if (isBusinessDay(x)) return x; x = x.plusDays(1) }
        return x
    }
}
