package com.enwei.moneymanager.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 從錢包 App「交易紀錄」畫面讀到的一筆交易。 */
data class ScreenRecord(
    val time: LocalDateTime,
    /** 畫面只有日期沒有時間 */
    val dateOnly: Boolean,
    val merchant: String?,
    val amountMinor: Long,
    val isIncome: Boolean,
    /** 畫面上的類型文字，例如「支付」「儲值」「嗶乘車」 */
    val typeLabel: String?,
) {
    val isTopUp: Boolean get() = isIncome && typeLabel != null && (typeLabel.contains("儲值") || typeLabel.contains("加值"))
}

/**
 * 解析街口、悠遊付等錢包的交易紀錄畫面（無障礙服務依畫面順序取得的文字）。
 * 以「帶正負號的金額」為每筆紀錄的錨點，往前找商家、往前後找日期，因此不依賴固定版面。
 */
object ScreenRecordParser {
    private val amountRegex = Regex("^([+\\-−－])\\s*(?:NT)?\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)$")
    private val fullDate = Regex("^(\\d{4})[/.-](\\d{1,2})[/.-](\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2}))?$")
    private val monthHeader = Regex("(\\d{4})\\s*年\\s*(\\d{1,2})\\s*月")
    private val dayOnly = Regex("^(\\d{1,2})\\s*日$")
    private val typeWords = listOf("支付", "付款", "儲值", "加值", "嗶乘車", "乘車", "轉帳", "退款", "收款", "回饋", "提領", "轉出", "轉入", "消費")
    private val noise = setOf(">", "›", "看詳情", "全部", "支出", "收入", "大眾運輸", "交易紀錄", "所有交易")

    private sealed interface Tok
    private data class Amt(val minor: Long, val income: Boolean) : Tok
    private data class Dt(val time: LocalDateTime, val dateOnly: Boolean) : Tok
    private data class Type(val text: String) : Tok
    private data class Txt(val text: String) : Tok
    private object Skip : Tok

    fun parse(texts: List<String>, today: LocalDate = LocalDate.now()): List<ScreenRecord> {
        var month: Pair<Int, Int>? = null
        val toks = texts.map { it.trim() }.filter { it.isNotEmpty() }.map { t ->
            monthHeader.find(t)?.takeIf { t.length <= 12 }?.let { m ->
                month = m.groupValues[1].toInt() to m.groupValues[2].toInt()
                return@map Skip
            }
            amountRegex.matchEntire(t)?.let { m ->
                val v = Money.parseMinor(m.groupValues[2]) ?: return@map Skip
                return@map if (v == 0L) Skip else Amt(v, m.groupValues[1] == "+")
            }
            fullDate.matchEntire(t)?.let { m ->
                val g = m.groupValues
                val d = runCatching { LocalDate.of(g[1].toInt(), g[2].toInt(), g[3].toInt()) }.getOrNull() ?: return@map Skip
                return@map if (g[4].isNotEmpty()) Dt(d.atTime(g[4].toInt(), g[5].toInt()), false) else Dt(d.atTime(LocalTime.NOON), true)
            }
            dayOnly.matchEntire(t)?.let { m ->
                val (y, mo) = month ?: (today.year to today.monthValue)
                val d = runCatching { LocalDate.of(y, mo, m.groupValues[1].toInt()) }.getOrNull() ?: return@map Skip
                return@map Dt(d.atTime(LocalTime.NOON), true)
            }
            when {
                t in noise || t.startsWith("$") || t.contains("~") -> Skip
                typeWords.any { t == it || (t.length <= 4 && t.contains(it)) } -> Type(t)
                t.length >= 2 && t.any { !it.isDigit() } -> Txt(t)
                else -> Skip
            }
        }

        val amountIdx = toks.indices.filter { toks[it] is Amt }
        if (amountIdx.isEmpty()) return emptyList()
        // 版面判斷：日期在商家前（悠遊付：19日 → 商家）或商家後（街口：商家 → 類型 → 日期）
        val firstMerchant = (amountIdx[0] - 1 downTo 0).firstOrNull { toks[it] is Txt }
        val dateFirst = firstMerchant != null &&
            ((firstMerchant - 1) downTo 0).takeWhile { toks[it] !is Txt }.any { toks[it] is Dt }

        return amountIdx.mapIndexedNotNull { n, i ->
            val prev = amountIdx.getOrNull(n - 1) ?: -1
            val next = amountIdx.getOrNull(n + 1) ?: toks.size
            val amt = toks[i] as Amt
            // 商家：金額之前最近的一般文字；沒有的話往後找
            val m = ((i - 1) downTo (prev + 1)).firstOrNull { toks[it] is Txt }
                ?: ((i + 1) until next).firstOrNull { toks[it] is Txt }
            val anchor = m ?: i
            val before = { ((anchor - 1) downTo (prev + 1)).firstNotNullOfOrNull { toks[it] as? Dt } }
            val after = {
                ((anchor + 1) until next).takeWhile { it == i || toks[it] !is Txt }.firstNotNullOfOrNull { toks[it] as? Dt }
            }
            val dt = (if (dateFirst) before() ?: after() else after() ?: before()) ?: return@mapIndexedNotNull null
            val type = ((prev + 1) until next).firstNotNullOfOrNull { (toks[it] as? Type)?.text }
            ScreenRecord(dt.time, dt.dateOnly, m?.let { (toks[it] as Txt).text }, amt.minor, amt.income, type)
        }
    }
}
