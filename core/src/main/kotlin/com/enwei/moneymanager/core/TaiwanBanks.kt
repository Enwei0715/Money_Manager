package com.enwei.moneymanager.core

/**
 * 台灣發卡銀行資料：銀行代碼（金融機構代號）→ 繳款截止日規則。
 *
 * 結帳日是每位持卡人自己選定（或銀行指定）的，所以這裡只記錄各銀行固定的部分：
 * 繳款截止日＝結帳日後幾天，以及可選的結帳日。數值整理自公開資料（2024～2026 年各銀行說明與比較文章），
 * 各來源偶有 1 天差異；[verified] 為 false 表示資料不足、先用常見值，請以帳單為準。
 */
data class CardBank(
    val code: String,
    val name: String,
    /** 繳款截止日＝結帳日＋幾天 */
    val dueOffsetDays: Int,
    /** 可選的結帳日；空白表示未知或可任選 */
    val statementDays: List<Int> = emptyList(),
    /** 固定結帳日（例如合庫每月 1 日）；-1 表示月底 */
    val fixedStatementDay: Int? = null,
    val verified: Boolean = true,
    val note: String? = null,
)

object TaiwanBanks {
    val all: List<CardBank> = listOf(
        CardBank("004", "臺灣銀行", 15, verified = false),
        CardBank("005", "土地銀行", 14),
        CardBank("006", "合作金庫", 14, fixedStatementDay = 1, note = "結帳日每月 1 日、繳款截止日每月 15 日"),
        CardBank("007", "第一銀行", 15),
        CardBank("008", "華南銀行", 15),
        CardBank("009", "彰化銀行", 18, fixedStatementDay = -1, note = "結帳日為每月最後一天"),
        CardBank("011", "上海商銀", 15, verified = false),
        CardBank("012", "台北富邦", 16, statementDays = (2..26 step 2).toList(), note = "結帳日可選每月 2～26 日的偶數日"),
        CardBank("013", "國泰世華", 16, note = "繳款截止日＝名目結帳日＋16 天，遇六日順延至週一"),
        CardBank("017", "兆豐銀行", 15),
        CardBank("048", "王道銀行", 15, verified = false),
        CardBank("050", "臺灣企銀", 15, verified = false),
        CardBank("052", "渣打銀行", 21),
        CardBank("053", "台中銀行", 15, verified = false),
        CardBank("054", "京城銀行", 15, verified = false),
        CardBank("081", "滙豐（台灣）", 18),
        CardBank("103", "新光銀行", 16),
        CardBank("108", "陽信銀行", 15, verified = false),
        CardBank("803", "聯邦銀行", 15),
        CardBank("805", "遠東商銀", 15, verified = false),
        CardBank("806", "元大銀行", 15, verified = false),
        CardBank("807", "永豐銀行", 15),
        CardBank("808", "玉山銀行", 15),
        CardBank("809", "凱基銀行", 17),
        CardBank("810", "星展（台灣）", 18, note = "原花旗卡友同此規則；部分資料為 18～21 天"),
        CardBank("812", "台新銀行", 15, statementDays = listOf(2, 7, 12, 17, 20, 22, 27)),
        CardBank("816", "安泰銀行", 15, verified = false),
        CardBank("822", "中國信託", 18, statementDays = listOf(5, 7, 10, 12, 15, 17, 20, 22, 25, 27)),
        CardBank("823", "將來銀行", 15, verified = false),
        CardBank("824", "連線銀行（LINE Bank）", 15, verified = false),
        CardBank("826", "樂天國際", 15, verified = false),
    )

    private val byCode = all.associateBy { it.code }

    /** 以代碼（可含前導 0、可只打數字）或名稱關鍵字查詢。 */
    fun find(query: String): CardBank? {
        val q = query.trim()
        if (q.isEmpty()) return null
        q.filter { it.isDigit() }.takeIf { it.isNotEmpty() && it.length <= 3 }?.padStart(3, '0')?.let { byCode[it] }?.let { return it }
        val aliases = mapOf("國泰" to "013", "中信" to "822", "富邦" to "012", "永豐" to "807", "玉山" to "808", "台新" to "812", "星展" to "810",
            "花旗" to "810", "滙豐" to "081", "匯豐" to "081", "渣打" to "052", "聯邦" to "803", "凱基" to "809", "新光" to "103", "兆豐" to "017",
            "第一" to "007", "華南" to "008", "彰化" to "009", "彰銀" to "009", "合庫" to "006", "土銀" to "005", "台銀" to "004", "LINE" to "824",
            "樂天" to "826", "將來" to "823", "遠東" to "805", "元大" to "806", "王道" to "048", "上海" to "011", "台企" to "050")
        aliases.entries.firstOrNull { q.contains(it.key, ignoreCase = true) }?.let { return byCode[it.value] }
        return all.firstOrNull { q.contains(it.name.take(2)) }
    }
}
