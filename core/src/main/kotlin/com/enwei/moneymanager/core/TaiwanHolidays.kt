package com.enwei.moneymanager.core

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 台灣國定假日（行政院人事行政總處「政府行政機關辦公日曆表」）。
 * holidays：平日放假（含補假）；workdays：週末補行上班。銀行營業日同政府辦公日；2025 年勞動節金融機構亦休假。
 * 內建 2025～2027 年；App 連網時會定期下載最新的辦公日曆（[update]，以下載資料為準），
 * 並另外記錄颱風等天然災害的停止上班日（[setExtra]）。沒有資料的年份只排除週末。
 */
data class YearCalendar(
    val holidays: Map<String, String>,
    val workdays: Set<String> = emptySet(),
    /** 落在週末的節日、補行上班等只用來標示的名稱（不影響營業日計算） */
    val names: Map<String, String> = emptyMap(),
)

object TaiwanHolidays {
    private val builtIn: Map<Int, YearCalendar> = mapOf(
        2025 to YearCalendar(
            holidays = mapOf("0101" to "開國紀念日", "0127" to "小年夜", "0128" to "農曆除夕", "0129" to "春節", "0130" to "春節", "0131" to "春節", "0228" to "和平紀念日", "0403" to "補假", "0404" to "兒童節及民族掃墓節", "0501" to "勞動節（金融機構休假）", "0530" to "補假", "0929" to "補假", "1006" to "中秋節", "1010" to "國慶日", "1024" to "補假", "1225" to "行憲紀念日"),
            workdays = setOf("0208"),
            names = mapOf("0208" to "補行上班", "0531" to "端午節", "0928" to "孔子誕辰紀念日", "1025" to "臺灣光復暨金門古寧頭大捷紀念日"),
        ),
        2026 to YearCalendar(
            holidays = mapOf("0101" to "開國紀念日", "0216" to "農曆除夕", "0217" to "春節", "0218" to "春節", "0219" to "春節", "0220" to "補假", "0227" to "補假", "0403" to "補假", "0406" to "補假", "0501" to "勞動節", "0619" to "端午節", "0925" to "中秋節", "0928" to "孔子誕辰紀念日/教師節", "1009" to "補假", "1026" to "補假", "1225" to "行憲紀念日"),
            workdays = setOf(),
            names = mapOf("0215" to "小年夜", "0228" to "和平紀念日", "0404" to "兒童節", "0405" to "清明節", "1010" to "國慶日", "1025" to "臺灣光復暨金門古寧頭大捷紀念日", "1128" to "地方公職人員選舉"),
        ),
        2027 to YearCalendar(
            holidays = mapOf("0101" to "開國紀念日", "0204" to "小年夜", "0205" to "農曆除夕", "0208" to "春節", "0209" to "補假", "0210" to "補假", "0301" to "補假", "0405" to "清明節", "0406" to "補假", "0430" to "補假", "0609" to "端午節", "0915" to "中秋節", "0928" to "孔子誕辰紀念日/教師節", "1011" to "補假", "1025" to "臺灣光復暨金門古寧頭大捷紀念日", "1224" to "補假", "1231" to "補假"),
            workdays = setOf(),
            names = mapOf("0206" to "春節", "0207" to "春節", "0228" to "和平紀念日", "0404" to "兒童節", "0501" to "勞動節", "1010" to "國慶日", "1225" to "行憲紀念日"),
        ),
    )

    @Volatile private var downloaded: Map<Int, YearCalendar> = emptyMap()

    /** 颱風假等臨時停止上班日（日期 → 名稱）。 */
    @Volatile private var extra: Map<LocalDate, String> = emptyMap()

    /** 加入下載的年份（覆蓋內建資料，政府若調整放假以最新公告為準）。 */
    fun update(years: Map<Int, YearCalendar>) {
        // 政府辦公日曆不含「只有金融機構休假」的日子，保留內建的這類假日
        downloaded = downloaded + years.mapValues { (y, c) ->
            val b = builtIn[y]
            val bankOnly = b?.holidays.orEmpty().filterValues { "金融機構" in it }
            // 選舉等下載資料沒有的標示名稱也保留
            c.copy(holidays = c.holidays + bankOnly, names = b?.names.orEmpty() + c.names)
        }
    }

    /** 已知、但公告頁已撤下而抓不到的臺北市停止上班日（使用者回報）。 */
    val KNOWN_TYPHOON: Map<LocalDate, String> = mapOf(LocalDate.of(2026, 10, 23) to "颱風假")

    /** 標為「上班」表示使用者取消了這天的停班（例如已知清單有誤）。 */
    const val WORKING = "上班"

    fun setExtra(days: Map<LocalDate, String>) { extra = (KNOWN_TYPHOON + days).filterValues { it != WORKING } }

    fun knownYears(): Set<Int> = builtIn.keys + downloaded.keys

    private fun cal(year: Int) = downloaded[year] ?: builtIn[year]
    private fun key(d: LocalDate) = "%02d%02d".format(d.monthValue, d.dayOfMonth)

    /** 假日名稱（平日放假，含颱風假）；週末回傳 null。 */
    fun holidayName(d: LocalDate): String? = extra[d] ?: cal(d.year)?.holidays?.get(key(d))

    /** 日曆要顯示的名稱：放假（含颱風假、週末的節日）或補行上班。 */
    fun label(d: LocalDate): String? = holidayName(d) ?: cal(d.year)?.names?.get(key(d))

    /** 日曆格子裡的短名稱（完整名稱太長會被截掉）。 */
    fun shortLabel(d: LocalDate): String? = label(d)?.let { shortName(it) }

    fun shortName(n: String): String = when {
        "光復" in n -> "光復節"
        "教師節" in n || "孔子" in n -> "教師節"
        "兒童節" in n && "掃墓" in n -> "兒童節"
        "勞動節" in n -> "勞動節"
        "開國紀念" in n -> "元旦"
        "和平紀念" in n -> "228"
        "行憲" in n -> "行憲紀念"
        "選舉" in n || "投票" in n -> "選舉日"
        "補行上班" in n -> "補班"
        "除夕" in n -> "除夕"
        else -> n
    }

    /** 銀行營業日：非週末（補班日除外）且非國定假日。 */
    fun isBusinessDay(d: LocalDate): Boolean {
        val c = cal(d.year)
        if (d in extra) return false
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

/**
 * 解析行政院人事行政總處「天然災害停止上班及上課情形」頁面（https://www.dgpa.gov.tw/typh/daily/nds.html）。
 * 回傳指定縣市各日期是否「全天停止上班」：true = 停班（金融機構同步停止營業），false = 照常上班。
 * 只有半天（上午／下午／晚上）停班的不算，因為銀行當天仍會營業。
 */
object TyphoonNotice {
    private val updated = Regex("更新時間[：:]\\s*(\\d{4})/(\\d{1,2})/(\\d{1,2})")
    private val monthDay = Regex("(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*日")
    private val partial = Regex("上午|下午|中午|晚上|夜間|\\d+\\s*時")

    fun parse(html: String, today: LocalDate, city: String = "臺北市"): Map<LocalDate, Boolean> {
        val base = updated.find(html)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        } ?: today
        val row = html.split(Regex("<tr", RegexOption.IGNORE_CASE))
            .map { it.replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ") }
            .firstOrNull { it.contains(city) && it.contains("上班") } ?: return emptyMap()
        val out = LinkedHashMap<LocalDate, Boolean>()
        row.substringAfter(city).split('。').forEach { sentence ->
            if (!sentence.contains("上班")) return@forEach
            val day = monthDay.find(sentence)?.let { m ->
                runCatching { LocalDate.of(base.year, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
            } ?: when {
                sentence.contains("明天") || sentence.contains("明日") -> base.plusDays(1)
                sentence.contains("今天") || sentence.contains("今日") -> base
                else -> return@forEach
            }
            val stop = sentence.contains("停止上班")
            out[day] = stop && !partial.containsMatchIn(sentence.substringBefore("停止上班"))
        }
        return out
    }
}
