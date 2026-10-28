package com.enwei.moneymanager.core

/**
 * 從銀行／錢包 App 畫面的文字（截圖或照片經 OCR）找出「餘額」候選。
 * 依關鍵字優先順序排列：可用餘額 > 帳戶餘額 > 餘額 > 總額…；數字需像金額（有千分位、小數或貨幣符號），
 * 排除帳號、卡號、日期與時間。
 */
object BalanceExtractor {
    data class Candidate(val label: String, val amountMinor: Long, val rank: Int)

    private val keywords = listOf(
        "可用餘額", "可動用餘額", "帳戶餘額", "活存餘額", "存款餘額", "目前餘額", "錢包餘額", "餘額", "結餘",
        "總資產", "資產總額", "存款總額", "總額", "available balance", "current balance", "balance",
    )

    private val money = Regex("(?<![\\d.,/:*-])(-?)\\s*(?:NT\\$|NTD|TWD|\\$|＄)?\\s*(-?\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|-?\\d+\\.\\d{1,2}|-?\\d{1,9})(?![\\d,/:*-])")

    /** 一段文字裡看起來像金額的數字（最小單位）。 */
    fun amounts(line: String): List<Long> {
        val t = NotificationParser.clean(line)
        // 日期、時間、帳號先拿掉
        val stripped = t.replace(Regex("\\d{2,4}[/-]\\d{1,2}[/-]\\d{1,2}"), " ")
            .replace(Regex("\\d{1,2}:\\d{2}(:\\d{2})?"), " ")
            .replace(Regex("[\\d*-]{8,}"), " ")
        return money.findAll(stripped).mapNotNull { m ->
            val raw = m.groupValues[2]
            val neg = m.groupValues[1] == "-" || raw.startsWith("-")
            val v = raw.removePrefix("-").replace(",", "").toDoubleOrNull() ?: return@mapNotNull null
            // 沒有千分位、小數也沒有貨幣符號的短數字（例如 2、15）多半不是餘額
            val looksMoney = ',' in raw || '.' in raw || m.value.contains('$') || m.value.contains("TWD") || v >= 100
            if (!looksMoney) null else Math.round(v * 100) * (if (neg) -1 else 1)
        }.toList()
    }

    /** 由上到下的文字行 → 候選餘額（最可能的在前）。 */
    fun candidates(lines: List<String>): List<Candidate> {
        val out = ArrayList<Candidate>()
        lines.forEachIndexed { i, line ->
            val low = NotificationParser.clean(line).lowercase()
            val k = keywords.indexOfFirst { low.contains(it) }
            if (k < 0) return@forEachIndexed
            val label = keywords[k]
            // 同一行關鍵字後面的數字；沒有就看下面兩行
            val after = low.substring(low.indexOf(label) + label.length)
            val nums = amounts(after).ifEmpty { lines.drop(i + 1).take(2).firstNotNullOfOrNull { amounts(it).firstOrNull()?.let(::listOf) }.orEmpty() }
            nums.firstOrNull()?.let { out += Candidate(label, it, k) }
        }
        return out.sortedBy { it.rank }.distinctBy { it.amountMinor }
    }
}
