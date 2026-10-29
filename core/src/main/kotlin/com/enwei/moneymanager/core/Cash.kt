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

    /** 一種付款方式：給出去的鈔票硬幣與找回來的。 */
    data class PayOption(val give: Map<Int, Int>, val back: Map<Int, Int>) {
        val pieces: Int get() = pieces(give) + pieces(back)
    }

    /** 金額用最少張數組成（不用 2000、200 元）。 */
    fun compose(amount: Long): Map<Int, Int> = change(amount)

    /**
     * 常見的付款與找零方式：剛好付、付一張（枚）剛好超過的鈔票硬幣再找零、
     * 大鈔加上零頭讓找零變整數（例如 132 元付 500＋32 找 400）。
     * [have] 不為空時，皮夾付得出來的排前面，並加上「從皮夾挑最省」的組合。最多回傳 [limit] 種。
     */
    fun payOptions(price: Long, have: Map<Int, Int> = emptyMap(), limit: Int = 4): List<PayOption> {
        if (price <= 0) return emptyList()
        val out = ArrayList<PayOption>()
        fun add(give: Map<Int, Int>) {
            val g = give.filterValues { it > 0 }
            val paid = total(g)
            if (paid < price) return
            val o = PayOption(g, change(paid - price))
            if (out.none { it == o }) out += o
        }
        if (have.isNotEmpty()) suggestPay(price, have)?.let { add(it) }
        add(compose(price))
        // 付一張（枚）剛好超過的，和再大一級的
        val singles = DENOMS.filter { it != 2000 && it != 200 && it >= price }.sorted()
        singles.take(2).forEach { add(mapOf(it to 1)) }
        // 大鈔＋零頭：讓找零是整數
        for (unit in listOf(100, 10)) {
            val r = price % unit
            if (r == 0L || price < unit) continue
            val base = price - r
            for (n in listOf(100, 500, 1000).filter { it > unit }) {
                val k = (base + n - 1) / n
                if (k * n > base && k <= 3) add(mapOf(n to k.toInt()) + compose(r))
            }
        }
        // 金額比最大面額大時：用千元鈔湊整再找零
        if (singles.isEmpty()) add(mapOf(1000 to ((price + 999) / 1000).toInt()))
        fun fits(g: Map<Int, Int>) = have.isEmpty() || g.all { (d, n) -> (have[d] ?: 0) >= n }
        return out.sortedWith(compareBy<PayOption>({ !fits(it.give) }, { it.back.isNotEmpty() }, { it.pieces })).take(limit)
    }
}
