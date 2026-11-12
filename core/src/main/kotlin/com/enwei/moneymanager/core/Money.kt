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
        val body = if (abs % 100 == 0L) noDecimals.format(abs / 100) else twoDecimals.format(abs / 100.0)
        val sign = if (minor < 0) "-" else ""
        val symbol = currency?.let { symbolOf(it) } ?: ""
        return "$sign$symbol$body"
    }

    /** 一律顯示兩位小數，例如 "1,000.00"、"-2,574.00"。 */
    fun full(minor: Long): String {
        val sign = if (minor < 0) "-" else ""
        return sign + twoDecimals.format(Math.abs(minor) / 100.0)
    }

    /** 帶幣別符號，例如 "$ 1,000.00"。 */
    fun withSymbol(minor: Long, currency: String = "TWD"): String {
        val sign = if (minor < 0) "-" else ""
        return "${symbolOf(currency).trim()} $sign${twoDecimals.format(Math.abs(minor) / 100.0)}"
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
