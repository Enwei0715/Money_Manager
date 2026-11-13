package com.enwei.moneymanager.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/** 金額一律以「最小單位 ×100」的 Long 儲存，避免浮點誤差。 */
object Money {
    /** 金額上限：一千億（避免輸入過長數字時 Long 溢位變成負數或亂數） */
    val MAX_MINOR: Long = 100_000_000_000L * 100

    /** 轉成最小單位；超過上限時回傳 null（使用者輸入用，避免溢位變成負數或亂數） */
    fun toMinorOrNull(value: BigDecimal): Long? {
        val scaled = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2)
        if (scaled.abs() > BigDecimal.valueOf(MAX_MINOR)) return null
        return scaled.toLong()
    }

    /** 轉成最小單位（程式內部計算用）：超過上限時夾在上限，不會溢位 */
    fun toMinor(value: BigDecimal): Long = toMinorOrNull(value) ?: if (value.signum() < 0) -MAX_MINOR else MAX_MINOR

    fun toMinor(value: Double): Long = if (value.isFinite()) toMinor(BigDecimal.valueOf(value)) else 0L

    fun parseMinor(text: String): Long? {
        val cleaned = text.replace(",", "").replace("，", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()?.let { toMinorOrNull(it) }
    }

    fun fromMinor(minor: Long): BigDecimal = BigDecimal.valueOf(minor, 2)

    fun toDouble(minor: Long): Double = minor / 100.0

    private val noDecimals = DecimalFormat("#,##0")
    private val twoDecimals = DecimalFormat("#,##0.00")

    /** 整數金額不顯示小數點；有零頭時顯示兩位。 */
    fun format(minor: Long, currency: String? = null): String {
        val abs = Math.abs(minor)
        // 台幣等沒有輔幣的幣別一律整數；其他幣別整數時不顯示小數、有零頭時顯示兩位
        val body = if (decimalsOf(currency ?: displayBase) == 0) noDecimals.format(BigDecimal.valueOf(abs, 2).setScale(0, RoundingMode.HALF_UP))
            else if (abs % 100 == 0L) noDecimals.format(abs / 100) else twoDecimals.format(abs / 100.0)
        val sign = if (minor < 0) "-" else ""
        // 全 App 一種寫法：負號在最前、符號與數字間一個空格（-$ 1,234）
        val symbol = currency?.let { symbolOf(it).trim() + " " } ?: ""
        return "$sign$symbol$body"
    }

    /** 沒有輔幣的幣別：台幣、日圓、韓元一律顯示整數（四捨五入），其他幣別顯示兩位小數 */
    private val zeroDecimal = setOf("TWD", "JPY", "KRW")

    /** 不指定幣別時用的幣別（App 設定的基準幣別） */
    @Volatile var displayBase: String = "TWD"

    fun decimalsOf(currency: String): Int = if (currency.uppercase() in zeroDecimal) 0 else 2

    private fun body(absMinor: Long, currency: String): String =
        if (decimalsOf(currency) == 0) noDecimals.format(BigDecimal.valueOf(absMinor, 2).setScale(0, RoundingMode.HALF_UP))
        else twoDecimals.format(absMinor / 100.0)

    /** 依幣別顯示：台幣 "1,000"、美元 "1,000.00"（沒有符號）。 */
    fun full(minor: Long, currency: String = displayBase): String {
        val sign = if (minor < 0) "-" else ""
        return sign + body(Math.abs(minor), currency)
    }

    /** 帶幣別符號，例如 "$ 1,000"、"US$ 12.50"。 */
    fun withSymbol(minor: Long, currency: String = displayBase): String {
        val sign = if (minor < 0) "-" else ""
        return "$sign${symbolOf(currency).trim()} ${body(Math.abs(minor), currency)}"
    }

    /** 以「元」為單位的小數金額（股利、成交金額等）：依幣別決定小數位並加上符號 */
    fun ofUnits(value: Double, currency: String = displayBase): String = withSymbol(toMinor(value), currency)

    fun symbolOf(currency: String): String = when (currency.uppercase()) {
        "TWD" -> "$"
        "USD" -> "US$"
        "JPY" -> "¥"
        "EUR" -> "€"
        "GBP" -> "£"
        "HKD" -> "HK$"
        "CNY" -> "CN¥"
        "KRW" -> "₩"
        else -> "${currency.uppercase()} "
    }
}

/**
 * 匯率換算。rates 為「1 單位基準幣可換多少該幣別」（與 open.er-api.com 相同語意）。
 */
class FxConverter(private val base: String, private val rates: Map<String, Double>) {
    fun convert(minor: Long, from: String, to: String = base): Long {
        if (from.equals(to, ignoreCase = true)) return minor
        val fromRate = rateOf(from) ?: return minor
        val toRate = rateOf(to) ?: return minor
        val v = minor / fromRate * toRate
        // 換成新台幣、日圓這類平常不用小數的幣別時取整數（元），不出現 1,234.57 這種零頭
        return if (to.uppercase() in WHOLE) Math.round(v / 100.0) * 100 else Math.round(v)
    }

    private companion object {
        val WHOLE = setOf("TWD", "JPY", "KRW", "VND", "IDR", "HUF", "CLP", "ISK")
    }

    fun hasRate(currency: String) = rateOf(currency) != null

    private fun rateOf(currency: String): Double? =
        if (currency.equals(base, ignoreCase = true)) 1.0 else rates[currency.uppercase()]?.takeIf { it > 0 }
}
