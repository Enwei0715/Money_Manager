package com.enwei.moneymanager.core

import java.time.LocalDateTime

enum class ParsedType { EXPENSE, INCOME }

/** 通知的性質：一般收支、提款、電子支付儲值（扣款方／入帳方）、帳戶間轉帳。 */
enum class ParsedKind { NORMAL, WITHDRAWAL, TOPUP_OUT, TOPUP_IN, TRANSFER_OUT, TRANSFER_IN, CARD_PAYMENT }

data class ParsedTransaction(
    val type: ParsedType,
    val amountMinor: Long,
    val currency: String,
    val merchant: String?,
    val cardLast4: String?,
    val kind: ParsedKind = ParsedKind.NORMAL,
    /** 通知內文中的交易時間（用來去重與作為記帳時間） */
    val occurredAt: LocalDateTime? = null,
    /** 內文提到的電子支付名稱（例如「街口」），用來對應錢包帳戶 */
    val walletHint: String? = null,
)

data class ParsedStockFill(
    val side: StockSide,
    val symbol: String,
    val name: String?,
    val shares: Double,
    val price: Double,
    /** 通知裡寫的成交時間（沒有時為 null，改用通知時間） */
    val at: java.time.LocalDateTime? = null,
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
        "出帳", "扣帳",
    )

    // 英文介面的通知（例如 LINE 錢包：「LINE Pay Purchase NT$ 25 Payment complete. Merchant: …」），不分大小寫
    private val englishIgnore = listOf("failed", "declined", "cancelled", "canceled", "verification code", "one-time password")
    private val englishIncome = listOf("refund", "received", "cashback credited", "deposit")
    private val englishExpense = listOf("purchase", "payment complete", "payment completed", "paid", "spent", "charged", "payment of")
    private val englishMerchant = Regex("(?i)(?:merchant|store|shop)(?:\\s*name)?\\s*[:：]\\s*([^\\n,，。]{2,40})")

    private val withdrawalKeywords = listOf("提款", "領出", "提領", "ATM", "領現")
    private val topupKeywords = listOf("儲值", "加值", "電支交易", "電子支付交易")
    private val debitKeywords = listOf("扣款", "出帳", "扣帳", "支出", "電支交易", "電子支付交易", "轉出", "付款")
    private val transferOutKeywords = listOf("轉出", "匯出")
    private val transferInKeywords = listOf("轉入", "匯入")

    /** 電子支付名稱 → 用於比對帳戶名稱的別名 */
    val walletAliases: Map<String, List<String>> = linkedMapOf(
        "街口" to listOf("街口", "JKO"),
        "悠遊付" to listOf("悠遊付", "悠遊卡", "Easy Wallet", "EasyWallet", "悠遊"),
        "一卡通" to listOf("一卡通", "iPASS", "iPass"),
        "全支付" to listOf("全支付", "PX Pay"),
        "全盈" to listOf("全盈"),
        "LINE Pay" to listOf("LINE Pay", "LINEPAY", "LINE Pay Money", "一卡通Money"),
        "台灣Pay" to listOf("台灣Pay", "台灣 Pay"),
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

    private val cardLast4Regex = Regex("(?:末四碼|末4碼|尾號|末碼|後四碼|卡號末四碼|末五碼|末5碼)\\s*[:：]?\\s*\\*{0,4}(\\d{4,5})")

    private val merchantPatterns = listOf(
        // 國泰：「於 2026/10/26 16:22 在全聯福利中心 - 中正南海 刷卡」，店名可含空白
        Regex("在\\s*([^，,。！!\\n]{2,40}?)\\s*(?:刷卡|消費|付款)"),
        Regex("(?:於|在)\\s*([^，,。\\s]{2,30}?)\\s*(?:消費|刷卡|付款|支付|交易|扣款)"),
        Regex("(?:商店|特店|店家|商家|消費地點|收款方|付款對象)\\s*(?:名稱)?\\s*[:：]\\s*([^，,。\\n]{2,30})"),
        Regex("(?:付款給|支付給|轉帳給|付給)\\s*([^，,。\\s]{2,30})"),
    )
    private val dateLike = Regex("^[0-9/:\\-.\\s年月日時分上下午AMPamp]+$")

    fun shouldIgnore(text: String): Boolean =
        ignoreKeywords.any { text.contains(it, ignoreCase = true) } || englishIgnore.any { text.contains(it, ignoreCase = true) }

    /**
     * 從其他 App 複製或推播的文字常混有外觀相同、編碼不同的字：康熙部首（⾦、⽇）、相容漢字、
     * 全形數字，以及「∶」「︰」等冒號。先換成一般字元，關鍵字與數字才比對得到。
     */
    fun clean(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            val c = ch.code
            when {
                c in 0x2F00..0x2FDF || c in 0x2E80..0x2EFF || c in 0xF900..0xFAFF || c in 0xFF10..0xFF19 ->
                    sb.append(java.text.Normalizer.normalize(ch.toString(), java.text.Normalizer.Form.NFKC))
                ch == '∶' || ch == '︰' || ch == '﹕' -> sb.append('：')
                ch == '\u00A0' || ch == '\u3000' || ch == '\u200B' || ch == '\uFEFF' -> sb.append(if (ch == '\u200B' || ch == '\uFEFF') "" else " ")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun parse(title: String?, text: String?, now: LocalDateTime = LocalDateTime.now()): ParsedTransaction? {
        val full = clean(listOfNotNull(title, text).joinToString(" ").replace('\n', ' '))
        if (full.isBlank() || shouldIgnore(full)) return null

        val hasIncome = incomeKeywords.any { full.contains(it) } || englishIncome.any { full.contains(it, ignoreCase = true) }
        val hasExpense = expenseKeywords.any { full.contains(it) } || englishExpense.any { full.contains(it, ignoreCase = true) }
        val has = { list: List<String> -> list.any { full.contains(it, ignoreCase = true) } }
        val (type, kind) = when {
            // 退款、退貨即使含「消費」字樣仍屬收入
            full.contains("退款") || full.contains("退貨") || full.contains("refund", ignoreCase = true) -> ParsedType.INCOME to ParsedKind.NORMAL
            // 信用卡款自動扣繳／繳款（銀行帳戶扣款，不是刷卡消費）
            (full.contains("卡款") || full.contains("卡費") || full.contains("信用卡")) &&
                (full.contains("扣繳") || full.contains("繳款") || full.contains("繳費")) && !full.contains("刷卡") ->
                ParsedType.EXPENSE to ParsedKind.CARD_PAYMENT
            has(withdrawalKeywords) && !full.contains("存入") -> ParsedType.EXPENSE to ParsedKind.WITHDRAWAL
            has(topupKeywords) -> if (has(debitKeywords)) ParsedType.EXPENSE to ParsedKind.TOPUP_OUT else ParsedType.INCOME to ParsedKind.TOPUP_IN
            has(transferOutKeywords) -> ParsedType.EXPENSE to ParsedKind.TRANSFER_OUT
            has(transferInKeywords) -> ParsedType.INCOME to ParsedKind.TRANSFER_IN
            hasExpense -> ParsedType.EXPENSE to ParsedKind.NORMAL
            hasIncome -> ParsedType.INCOME to ParsedKind.NORMAL
            else -> return null
        }

        val (amount, currency) = findAmount(full) ?: return null
        if (amount <= 0) return null

        val wallet = walletAliases.entries.firstOrNull { (_, keys) -> keys.any { full.contains(it, ignoreCase = true) } }?.key
        return ParsedTransaction(
            type = type,
            amountMinor = amount,
            currency = currency,
            merchant = if (kind == ParsedKind.NORMAL) {
                englishMerchant.find(listOfNotNull(title, text).joinToString("\n"))?.groupValues?.get(1)?.trim()?.trimEnd('.')
                    ?: findMerchant(full)
            } else null,
            cardLast4 = cardLast4Regex.find(full)?.groupValues?.get(1)?.takeLast(4),
            kind = kind,
            occurredAt = findDateTime(full, now),
            walletHint = wallet,
        )
    }

    /**
     * 使用者「教過」的通知來源：不靠關鍵字，只要找得到金額就依指定的收支類型建立交易。
     */
    fun parseLearned(
        title: String?, text: String?, type: ParsedType, now: LocalDateTime = LocalDateTime.now(), kind: ParsedKind = ParsedKind.NORMAL,
    ): ParsedTransaction? {
        val full = clean(listOfNotNull(title, text).joinToString(" ").replace('\n', ' '))
        if (full.isBlank() || shouldIgnore(full)) return null
        val (amount, currency) = findAmount(full) ?: return null
        if (amount <= 0) return null
        return ParsedTransaction(
            type = type, amountMinor = amount, currency = currency,
            merchant = englishMerchant.find(listOfNotNull(title, text).joinToString("\n"))?.groupValues?.get(1)?.trim()?.trimEnd('.')
                ?: findMerchant(full),
            cardLast4 = cardLast4Regex.find(full)?.groupValues?.get(1)?.takeLast(4),
            kind = kind, occurredAt = findDateTime(full, now),
        )
    }

    private val fullDateTime = Regex("(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?")
    private val rocCompact = Regex("(?<!\\d)(1\\d{2})(\\d{2})(\\d{2})[_\\s](\\d{1,2}):(\\d{2})")
    private val shortDateTime = Regex("(?<![\\d/])(\\d{1,2})/(\\d{1,2})\\s*(\\d{1,2}):(\\d{2})")

    /** 內文中的交易時間；只有月日時，年份取最接近 [now] 的那一年。 */
    fun findDateTime(text: String, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? = runCatching {
        fullDateTime.find(text)?.let { m ->
            val g = m.groupValues
            return@runCatching LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
        }
        rocCompact.find(text)?.let { m ->
            val g = m.groupValues
            return@runCatching LocalDateTime.of(g[1].toInt() + 1911, g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt())
        }
        shortDateTime.find(text)?.let { m ->
            val g = m.groupValues
            var dt = LocalDateTime.of(now.year, g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt())
            if (dt.isAfter(now.plusDays(1))) dt = dt.minusYears(1)
            return@runCatching dt
        }
        null
    }.getOrNull()

    fun findAmount(text: String): Pair<Long, String>? {
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
    private val bracketRegex = Regex("[【\\[［]\\s*([0-9]{4,6}[A-Z]?)\\s*([\\u4e00-\\u9fa5A-Za-z\\-]{0,12})\\s*[】\\]］]")
    private val sharesRegex = Regex("([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*(張|股)")
    /** 「成交數:1股」的單位字被系統字型或截圖辨識成別的字時，仍可依欄位名稱讀出股數 */
    private val sharesLabelRegex = Regex("(?:成交數量|成交股數|成交數|成交量)\\s*[:：]?\\s*([0-9][0-9,]*)\\s*(張)?")
    private val fillTimeRegex = Regex("(20[0-9]{2})[/-]([0-9]{1,2})[/-]([0-9]{1,2})\\s*([0-9]{1,2}):([0-9]{2})(?::([0-9]{2}))?")
    private val priceRegex = Regex("(?:成交價|成交均價|均價|價格|單價|@)\\s*[:：]?\\s*(?:NT\\$|\\$)?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)")

    /** 券商「成交回報」通知 → 股票交易。 */
    fun parseStockFill(title: String?, text: String?): ParsedStockFill? {
        val full = clean(listOfNotNull(title, text).joinToString(" ").replace('\n', ' '))
        if (!full.contains("成交")) return null
        if (full.contains("未成交") || full.contains("委託失敗") || full.contains("刪單")) return null
        val sideToken = sideRegex.find(full)?.value ?: return null
        val side = if (sideToken.contains("賣")) StockSide.SELL else StockSide.BUY

        val sharesMatch = sharesLabelRegex.find(full) ?: sharesRegex.find(full) ?: return null
        val qty = sharesMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val shares = if (sharesMatch.groupValues[2] == "張") qty * 1000 else qty
        // 成交時間：取第一個完整的日期時間（例如「已於 2026/10/28 13:01:17 成交」）
        val at = fillTimeRegex.findAll(full).firstNotNullOfOrNull { m ->
            val g = m.groupValues
            runCatching {
                java.time.LocalDateTime.of(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
            }.getOrNull()
        }

        val price = priceRegex.find(full)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: return null

        // 股票代號：優先取括號內的「【6515 穎崴】」；否則找獨立的 4～6 碼數字，
        // 排除日期時間（2026/10/28、13:01）、帳號（9A9L-0609**2）與數量、價格本身
        bracketRegex.find(full)?.let { m ->
            val name = m.groupValues[2].trim().takeIf { it.any { ch -> ch in '一'..'龥' } }
            return ParsedStockFill(side, m.groupValues[1], name, shares, price, at)
        }
        val textNoQty = full.replace(sharesMatch.value, " ")
        val symMatch = symbolRegex.findAll(textNoQty).firstOrNull { m ->
            val v = m.groupValues[1]
            val before = textNoQty.getOrNull(m.range.first - 1)
            val after = textNoQty.getOrNull(m.range.first + v.length)
            v.toDoubleOrNull() != price && before !in listOf('/', '-', ':', '*', '.') && after !in listOf('/', '-', ':', '*', '.')
        } ?: return null
        val name = symMatch.groupValues[2].takeIf { it.isNotBlank() && it.any { ch -> ch in '一'..'龥' } }

        return ParsedStockFill(side, symMatch.groupValues[1], name, shares, price, at)
    }
}
