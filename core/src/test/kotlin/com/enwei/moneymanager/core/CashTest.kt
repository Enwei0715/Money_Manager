package com.enwei.moneymanager.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CashTest {
    @Test fun changeIsMinimal() {
        assertEquals(mapOf(500 to 1, 100 to 2, 50 to 1, 10 to 2, 5 to 1, 1 to 3), Cash.change(778))
    }

    @Test fun payExactWhenPossible() {
        val have = mapOf(1000 to 1, 100 to 3, 10 to 5, 1 to 5)
        val p = Cash.suggestPay(132, have)!!
        assertEquals(132L, Cash.total(p))
    }

    @Test fun payWithSmallestOverpay() {
        val have = mapOf(1000 to 1, 500 to 1, 100 to 1)
        val p = Cash.suggestPay(350, have)!!
        assertEquals(mapOf(500 to 1), p)
    }

    @Test fun notEnough() {
        assertNull(Cash.suggestPay(2000, mapOf(1000 to 1)))
    }

    @Test fun applyUpdatesWallet() {
        val after = Cash.apply(mapOf(1000 to 1, 10 to 2), mapOf(1000 to 1), Cash.change(1000 - 132))
        assertEquals(20L + 868, Cash.total(after))
    }
}
