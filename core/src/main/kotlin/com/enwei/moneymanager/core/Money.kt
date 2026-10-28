package com.enwei.moneymanager.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat

/** 金額一律以「最小單位 ×100」的 Long 儲存，避免浮點誤差。 */
object Money {
    fun toMinor(value: BigDecimal): Long = value.setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()

    fun toMinor(value: Double): Long = toMinor(BigDecimal.valueOf(value))

    fun parseMinor(text: String): Long? {
        val cleaned = text.replace(",", "").replace("，", "").trim()
        if (cleaned.isEmpty()) return null
        return cleaned.toBigDecimalOrNull()?.let(::toMinor)
    }

    fun fromMinor(minor: Long): BigDecimal = BigDecimal.valueOf(minor, 2)

    fun toDouble(minor: Long): Double = minor / 100.0

    private val noDecimals = DecimalFormat("#,##0")
    private val twoDecimals = DecimalFormat("#,##0.00")

    /** 整數金額不顯示小數點；有零頭時顯示兩位。 */
    fun format(minor: Long, currency: String? = null): String {
        val abs = Math.abs(minor)
        val body = if (abs % 100 == 0L) noDecimals.format(abs / 100) else twoDecimals.format(abs / 100.0)
        val sign = if (minor < 0) "-" else ""
        val symbol = currency?.let { symbolOf(it) } ?: ""
        return "$sign$symbol$body"
    }

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
        return Math.round(minor / fromRate * toRate)
    }

    fun hasRate(currency: String) = rateOf(currency) != null

    private fun rateOf(currency: String): Double? =
        if (currency.equals(base, ignoreCase = true)) 1.0 else rates[currency.uppercase()]?.takeIf { it > 0 }
}
