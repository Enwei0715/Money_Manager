package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class CalculatorTest {
    private fun eq(expected: String, expr: String) =
        assertEquals(0, BigDecimal(expected).compareTo(Calculator.evaluate(expr)))

    private fun type(keys: String) = keys.fold("") { acc, c -> Calculator.append(acc, c) }

    @Test fun precedence() {
        eq("190", "120+35×2")
        eq("25", "100÷4")
        eq("33.33", "100÷3")
        eq("-5", "-10+5")
    }

    @Test fun typingRules() {
        assertEquals("12+", type("12+×+"))
        assertEquals("1.5", type("1..5"))
        assertEquals("1.25", type("1.257"))
        assertEquals("5", type("05"))
        assertEquals("0.", type("."))
    }

    @Test fun incomplete() {
        eq("12", "12+")
        assertNull(Calculator.evaluate(""))
        assertNull(Calculator.evaluate("5÷0"))
    }
}
