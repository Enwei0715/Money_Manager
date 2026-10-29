package com.enwei.moneymanager.core

/**
 * 現金用紙鈔與硬幣記帳：皮夾裡每種面額有幾張／幾枚，付款時選付出的鈔票硬幣，自動算找零。
 * 金額單位為元（新台幣沒有角）。
 */
object Cash {
    /** 新台幣面額（大到小）；100 元以上是紙鈔 */
    val DENOMS = listOf(2000, 1000, 500, 200, 100, 50, 10, 5, 1)

    fun isBill(d: Int) = d >= 100

    fun total(counts: Map<Int, Int>): Long = counts.entries.sumOf { (d, c) -> d.toLong() * c }

    fun pieces(counts: Map<Int, Int>): Int = counts.values.sum()

    /** 找零的鈔票硬幣（張數最少；2000、200 元鈔少見，找零不用）。 */
    fun change(amount: Long): Map<Int, Int> {
        var left = amount
        val out = LinkedHashMap<Int, Int>()
        for (d in DENOMS.filter { it != 2000 && it != 200 }) {
            val n = (left / d).toInt()
            if (n > 0) { out[d] = n; left -= n.toLong() * d }
        }
        return out
    }

    /**
     * 從皮夾裡挑出要付的鈔票硬幣：付出的總額 ≥ [price] 且盡量小（找零少），同額時張數最少。
     * 錢不夠時回傳 null。
     */
    fun suggestPay(price: Long, have: Map<Int, Int>): Map<Int, Int>? {
        if (price <= 0) return emptyMap()
        val totalHave = total(have)
        if (totalHave < price) return null
        // 金額太大時用簡單做法：由大面額往下付，最後一張補足
        if (price > 200_000) return greedyPay(price, have)
        val limit = (price + DENOMS.max()).coerceAtMost(totalHave).toInt()
        // 有界背包：best[a] = 湊出 a 元的最少張數；from 記錄最後一張用哪個面額
        val inf = Int.MAX_VALUE
        val best = IntArray(limit + 1) { inf }
        val used = Array(limit + 1) { IntArray(DENOMS.size) }
        best[0] = 0
        DENOMS.forEachIndexed { di, d ->
            val cnt = have[d] ?: 0
            repeat(cnt) {
                for (a in limit downTo d) {
                    val prev = best[a - d]
                    if (prev != inf && prev + 1 < best[a]) {
                        best[a] = prev + 1
                        used[a] = used[a - d].copyOf().also { u -> u[di]++ }
                    }
                }
            }
        }
        // 用到的張數不能超過持有數
        for (a in price.toInt()..limit) {
            if (best[a] == inf) continue
            val u = used[a]
            if (DENOMS.indices.all { u[it] <= (have[DENOMS[it]] ?: 0) }) {
                return DENOMS.indices.filter { u[it] > 0 }.associate { DENOMS[it] to u[it] }
            }
        }
        return greedyPay(price, have)
    }

    private fun greedyPay(price: Long, have: Map<Int, Int>): Map<Int, Int>? {
        var left = price
        val out = LinkedHashMap<Int, Int>()
        for (d in DENOMS) {
            val n = minOf((left / d).toInt(), have[d] ?: 0)
            if (n > 0) { out[d] = n; left -= n.toLong() * d }
        }
        if (left > 0) {
            // 補一張比剩下金額大的最小面額
            val d = DENOMS.reversed().firstOrNull { it >= left && (have[it] ?: 0) > (out[it] ?: 0) } ?: return null
            out[d] = (out[d] ?: 0) + 1
        }
        return out
    }

    /** 付款後皮夾的內容：減掉付出的、加上找回的。 */
    fun apply(have: Map<Int, Int>, paid: Map<Int, Int>, received: Map<Int, Int>): Map<Int, Int> =
        DENOMS.associateWith { d -> (have[d] ?: 0) - (paid[d] ?: 0) + (received[d] ?: 0) }.filterValues { it != 0 }
}
