package com.enwei.moneymanager.core

import java.math.BigDecimal
import java.math.RoundingMode

/** 記帳鍵盤用的四則運算（先乘除後加減），例如 "120+35×2"。 */
object Calculator {
    private val ops = setOf('+', '-', '×', '÷')

    fun isOperator(c: Char) = c in ops

    /** 依輸入的按鍵更新算式，避免連續運算子或多個小數點。 */
    fun append(expr: String, key: Char): String {
        val last = expr.lastOrNull()
        return when {
            key in ops -> when {
                expr.isEmpty() -> if (key == '-') "-" else expr
                last != null && last in ops -> expr.dropLast(1) + key
                last == '.' -> expr.dropLast(1) + key
                else -> expr + key
            }
            key == '.' -> {
                val current = expr.takeLastWhile { it !in ops }
                when {
                    current.contains('.') -> expr
                    current.isEmpty() -> expr + "0."
                    else -> expr + key
                }
            }
            key.isDigit() -> {
                val current = expr.takeLastWhile { it !in ops }
                val decimals = current.substringAfter('.', "")
                when {
                    current.contains('.') && decimals.length >= 2 -> expr
                    current == "0" -> expr.dropLast(1) + key
                    else -> expr + key
                }
            }
            else -> expr
        }
    }

    fun hasOperator(expr: String) = expr.drop(1).any { it in ops }

    /** 計算結果；算式不完整時回傳 null。 */
    fun evaluate(expr: String): BigDecimal? {
        val e = expr.replace(",", "").trimEnd { it in ops || it == '.' }
        if (e.isEmpty() || e == "-") return null
        val numbers = mutableListOf<BigDecimal>()
        val operators = mutableListOf<Char>()
        var i = 0
        val sb = StringBuilder()
        while (i < e.length) {
            val c = e[i]
            if (c in ops && !(c == '-' && sb.isEmpty() && (i == 0 || e[i - 1] in ops))) {
                numbers += sb.toString().toBigDecimalOrNull() ?: return null
                sb.clear()
                operators += c
            } else sb.append(c)
            i++
        }
        numbers += sb.toString().toBigDecimalOrNull() ?: return null

        // 先處理乘除
        val n2 = mutableListOf(numbers[0])
        val o2 = mutableListOf<Char>()
        operators.forEachIndexed { idx, op ->
            val rhs = numbers[idx + 1]
            when (op) {
                '×' -> n2[n2.lastIndex] = n2.last().multiply(rhs)
                '÷' -> {
                    if (rhs.signum() == 0) return null
                    n2[n2.lastIndex] = n2.last().divide(rhs, 6, RoundingMode.HALF_UP)
                }
                else -> { o2 += op; n2 += rhs }
            }
        }
        var result = n2[0]
        o2.forEachIndexed { idx, op -> result = if (op == '+') result.add(n2[idx + 1]) else result.subtract(n2[idx + 1]) }
        return result.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    }

    fun format(value: BigDecimal): String = value.stripTrailingZeros().toPlainString()
}
