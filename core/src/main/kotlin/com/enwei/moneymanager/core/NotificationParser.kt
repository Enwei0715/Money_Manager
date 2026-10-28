package com.enwei.moneymanager.core

enum class ParsedType { EXPENSE, INCOME }

data class ParsedTransaction(
    val type: ParsedType,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val cardLast4: String?,
)

data class ParsedStockFill(
    val side: StockSide,
    val symbol: String,
    val name: String?,
    val shares: Double,
    val price: Double,
)

/**
 * 解析銀行、信用卡、電子支付、券商推播通知。
 * 規則採關鍵字＋正規表示式，不依賴特定 App，因此新的銀行通知多半也能辨識。
 */
object NotificationParser {

    private val ignoreKeywords = listOf(
        "驗證碼", "OTP", "密碼", "一次性", "認證碼", "交易失敗", "未成功", "失敗", "取消交易", "已取消",
        "登入", "優惠", "活動", "抽獎", "限時", "加碼",
    )

    private val incomeKeywords = listOf(
        "入帳", "轉入", "存入", "收款", "退款", "退貨", "薪資", "匯入", "收到", "已收", "入金", "返還", "回饋入帳",
    )

    private val expenseKeywords = listOf(
        "消費", "刷卡", "付款", "扣款", "支付", "轉出", "提款", "繳費", "支出", "購買", "交易成功", "已付", "簽帳", "交易金額",
    )

    // 金額前的幣別標示 → ISO 幣別
    private val currencyTokens: List<Pair<String, String>> = listOf(
        "NT$" to "TWD", "NTD" to "TWD", "TWD" to "TWD", "新臺幣" to "TWD", "新台幣" to "TWD", "台幣" to "TWD", "臺幣" to "TWD",
        "US$" to "USD", "USD" to "USD", "美金" to "USD", "美元" to "USD",
        "JPY" to "JPY", "日圓" to "JPY", "日幣" to "JPY", "日元" to "JPY",
        "EUR" to "EUR", "€" to "EUR", "歐元" to "EUR",
        "HK$" to "HKD", "HKD" to "HKD", "港幣" to "HKD",
        "CNY" to "CNY", "RMB" to "CNY", "人民幣" to "CNY",
        "KRW" to "KRW", "韓元" to "KRW", "GBP" to "GBP", "£" to "GBP", "英鎊" to "GBP",
        "SGD" to "SGD", "AUD" to "AUD", "THB" to "THB",
        "¥" to "JPY", "$" to "TWD",
    )

    private val number = "([0-9][0-9,]*(?:\\.[0-9]+)?)"
    private val prefixedAmount: Regex = Regex(
        "(" + currencyTokens.joinToString("|") { Regex.escape(it.first) } + ")\\s*:?\\s*" + number,
        RegexOption.IGNORE_CASE,
    )
    private val suffixedAmount = Regex("$number\\s*(元|圓|円)")
    private val notAmountContext = listOf("餘額", "額度", "累積", "累計", "回饋", "紅利", "點數", "可用")

    private val cardLast4Regex = Regex("(?:末四碼|末4碼|尾號|末碼|後四碼|卡號末四碼)\\s*[:：]?\\s*\\*{0,4}(\\d{4})")

    private val merchantPatterns = listOf(
        Regex("(?:於|在)\\s*([^，,。\\s]{2,30}?)\\s*(?:消費|刷卡|付款|支付|交易|扣款)"),
        Regex("(?:商店|特店|店家|商家|消費地點|收款方|付款對象)\\s*(?:名稱)?\\s*[:：]\\s*([^，,。\\n]{2,30})"),
        Regex("(?:付款給|支付給|轉帳給|付給)\\s*([^，,。\\s]{2,30})"),
    )
    private val dateLike = Regex("^[0-9/:\\-.\\s年月日時分上下午AMPamp]+$")

    fun shouldIgnore(text: String): Boolean = ignoreKeywords.any { text.contains(it, ignoreCase = true) }

    fun parse(title: String?, text: String?): ParsedTransaction? {
        val full = listOfNotNull(title, text).joinToString(" ").replace('\n', ' ')
        if (full.isBlank() || shouldIgnore(full)) return null

        val hasIncome = incomeKeywords.any { full.contains(it) }
        val hasExpense = expenseKeywords.any { full.contains(it) }
        val type = when {
            // 退款、退貨即使含「消費」字樣仍屬收入
            full.contains("退款") || full.contains("退貨") -> ParsedType.INCOME
            hasExpense -> ParsedType.EXPENSE
            hasIncome -> ParsedType.INCOME
            else -> return null
        }

        val (amount, currency) = findAmount(full) ?: return null
        if (amount <= 0) return null

        return ParsedTransaction(
            type = type,
            amountMinor = amount,
            currency = currency,
            merchant = findMerchant(full),
            cardLast4 = cardLast4Regex.find(full)?.groupValues?.get(1),
        )
    }

    internal fun findAmount(text: String): Pair<Long, String>? {
        data class Candidate(val index: Int, val minor: Long, val currency: String)

        val candidates = mutableListOf<Candidate>()
        prefixedAmount.findAll(text).forEach { m ->
            val token = m.groupValues[1]
            val currency = currencyTokens.first { it.first.equals(token, ignoreCase = true) }.second
            Money.parseMinor(m.groupValues[2])?.let { candidates += Candidate(m.range.first, it, currency) }
        }
        suffixedAmount.findAll(text).forEach { m ->
            val currency = if (m.groupValues[2] == "円") "JPY" else "TWD"
            Money.parseMinor(m.groupValues[1])?.let { candidates += Candidate(m.range.first, it, currency) }
        }
        return candidates
            .sortedBy { it.index }
            .firstOrNull { c ->
                val before = text.substring(maxOf(0, c.index - 6), c.index)
                notAmountContext.none { before.contains(it) }
            }
            ?.let { it.minor to it.currency }
    }

    internal fun findMerchant(text: String): String? {
        for (p in merchantPatterns) {
            for (m in p.findAll(text)) {
                val candidate = m.groupValues[1].trim().trim('「', '」', '【', '】', '(', ')', '（', '）')
                    .replace(Regex("^\\d{1,4}/\\d{1,2}(?:/\\d{1,2})?(?:\\s*\\d{1,2}:\\d{2}(?::\\d{2})?)?\\s*"), "") // 去掉開頭的日期時間
                    .trim()
                if (candidate.length >= 2 && !dateLike.matches(candidate) &&
                    !candidate.startsWith("NT") && !candidate.contains("$")
                ) {
                    return candidate
                }
            }
        }
        return null
    }

    private val sideRegex = Regex("(買進|賣出|現股買進|現股賣出|買入|賣|買)")
    private val symbolRegex = Regex("(?<![0-9])([0-9]{4,6}[A-Z]?)(?![0-9])\\s*([\\u4e00-\\u9fa5A-Za-z0-9\\-]{0,12})")
    private val sharesRegex = Regex("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(張|股)")
    private val priceRegex = Regex("(?:成交價|成交均價|均價|價格|單價|@)\\s*[:：]?\\s*(?:NT\\$|\\$)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")

    /** 券商「成交回報」通知 → 股票交易。 */
    fun parseStockFill(title: String?, text: String?): ParsedStockFill? {
        val full = listOfNotNull(title, text).joinToString(" ").replace('\n', ' ')
        if (!full.contains("成交")) return null
        if (full.contains("未成交") || full.contains("委託失敗") || full.contains("刪單")) return null
        val sideToken = sideRegex.find(full)?.value ?: return null
        val side = if (sideToken.contains("賣")) StockSide.SELL else StockSide.BUY

        val sharesMatch = sharesRegex.find(full) ?: return null
        val qty = sharesMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val shares = if (sharesMatch.groupValues[2] == "張") qty * 1000 else qty

        val price = priceRegex.find(full)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null

        // 從文字中找股票代號，排除數量與價格本身
        val textNoQty = full.replace(sharesMatch.value, " ")
        val symMatch = symbolRegex.findAll(textNoQty).firstOrNull { m ->
            val v = m.groupValues[1]
            v.toDoubleOrNull() != price && !(m.range.first > 0 && textNoQty[m.range.first - 1] == '/')
        } ?: return null
        val name = symMatch.groupValues[2].takeIf { it.isNotBlank() && it.any { ch -> ch in '一'..'龥' } }

        return ParsedStockFill(side, symMatch.groupValues[1], name, shares, price)
    }
}
