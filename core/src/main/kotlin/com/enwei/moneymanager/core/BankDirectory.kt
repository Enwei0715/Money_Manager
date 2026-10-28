package com.enwei.moneymanager.core

import java.text.Normalizer

/**
 * 台灣銀行／電子支付的別名與代碼，用來把通知來源（App 名稱、Email 寄件者）
 * 和使用者自己的帳戶名稱對起來，例如「國泰世華」「CUBE」與帳戶「國泰簽帳」。
 */
object BankDirectory {
    data class Bank(val key: String, val code: String?, val aliases: List<String>)

    val banks: List<Bank> = listOf(
        Bank("台銀", "004", listOf("臺灣銀行", "台灣銀行", "台銀", "臺銀", "bank of taiwan")),
        Bank("土銀", "005", listOf("土地銀行", "土銀")),
        Bank("合庫", "006", listOf("合作金庫", "合庫")),
        Bank("第一", "007", listOf("第一銀行", "第一銀", "一銀", "first bank", "ileo")),
        Bank("華南", "008", listOf("華南", "hncb")),
        Bank("彰銀", "009", listOf("彰化銀行", "彰銀")),
        Bank("上海", "011", listOf("上海商銀", "上海銀行", "scsb")),
        Bank("富邦", "012", listOf("富邦", "fubon")),
        Bank("國泰", "013", listOf("國泰", "cathay", "cube")),
        Bank("兆豐", "017", listOf("兆豐", "mega")),
        Bank("王道", "048", listOf("王道", "o-bank", "obank")),
        Bank("臺企", "050", listOf("臺灣企銀", "台灣企銀", "臺企", "台企")),
        Bank("渣打", "052", listOf("渣打", "standard chartered")),
        Bank("滙豐", "081", listOf("滙豐", "匯豐", "hsbc")),
        Bank("新光", "103", listOf("新光銀行", "臺灣新光", "新光")),
        Bank("郵局", "700", listOf("中華郵政", "郵局", "郵政")),
        Bank("聯邦", "803", listOf("聯邦", "union bank")),
        Bank("遠東", "805", listOf("遠東商銀", "遠銀", "bankee")),
        Bank("元大", "806", listOf("元大", "yuanta")),
        Bank("永豐", "807", listOf("永豐", "sinopac", "dawho", "大戶")),
        Bank("玉山", "808", listOf("玉山", "esun", "e.sun")),
        Bank("凱基", "809", listOf("凱基", "kgi")),
        Bank("星展", "810", listOf("星展", "dbs")),
        Bank("台新", "812", listOf("台新", "richart", "taishin")),
        Bank("安泰", "816", listOf("安泰")),
        Bank("中信", "822", listOf("中國信託", "中信", "ctbc")),
        Bank("將來", "823", listOf("將來銀行", "next bank")),
        Bank("LINE Bank", "824", listOf("line bank", "連線銀行", "linebank")),
        Bank("樂天", "826", listOf("樂天", "rakuten")),
        Bank("街口", "396", listOf("街口", "jko")),
        Bank("悠遊付", null, listOf("悠遊付", "easy wallet")),
        Bank("全支付", null, listOf("全支付", "pxpay")),
        Bank("一卡通", null, listOf("一卡通", "ipass")),
    )

    private fun norm(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase()

    /** 文字（App 名稱、寄件者、帳戶名稱）屬於哪家銀行。 */
    fun of(text: String?): Bank? {
        if (text.isNullOrBlank()) return null
        val n = norm(text)
        return banks.firstOrNull { b -> b.aliases.any { n.contains(norm(it)) } }
    }

    fun byCode(code: String): Bank? = banks.firstOrNull { it.code == code }

    /** 帳戶名稱是否屬於這家銀行。 */
    fun matches(bank: Bank, accountName: String): Boolean = norm(accountName).let { n -> bank.aliases.any { n.contains(norm(it)) } }

    /** 轉帳通知裡的轉入銀行，例如「轉入銀行 807永豐銀行」「轉入行：822」。 */
    fun transferDestination(text: String?): Bank? {
        if (text.isNullOrBlank()) return null
        val m = Regex("轉入(?:銀行|行庫|行)[^\\d\\p{IsHan}]*(\\d{3})?\\s*([\\p{IsHan}A-Za-z ]{2,12})?").find(text) ?: return null
        m.groupValues[1].takeIf { it.isNotEmpty() }?.let { code -> byCode(code)?.let { return it } }
        return of(m.groupValues[2])
    }
}
