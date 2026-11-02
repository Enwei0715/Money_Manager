package com.enwei.moneymanager.data

import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Periods
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/** 一項預算這個月的使用量（金額皆為基準幣別最小單位）；[categoryId] 為 0 表示總預算。 */
data class BudgetUse(val categoryId: Long, val name: String, val cap: Long, val used: Long) {
    val left: Long get() = cap - used
    val pct: Int get() = if (cap > 0) (used * 100 / cap).toInt() else 0
}

/**
 * 某筆交易所屬月份的預算使用量：這筆的大分類預算（有設定時）與總預算。
 * [excludeId] 為正在編輯的交易（不重複算）。
 */
suspend fun Repository.budgetUse(categoryId: Long?, at: Long = System.currentTimeMillis(), excludeId: Long? = null): List<BudgetUse> {
    val s = settings.value
    if (s.budgets.isEmpty()) return emptyList()
    val fx = fx.first()
    val cats = db.categories().all().associateBy { it.id }
    fun root(id: Long?): Long? = id?.let { cats[it]?.parentId ?: it }
    val day = Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()
    val range = Periods.month(Periods.monthOf(day, s.monthStartDay), s.monthStartDay)
    val txns = db.txns().all().filter { t ->
        t.type == TxnType.EXPENSE && t.id != excludeId &&
            Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate().let { !it.isBefore(range.first) && !it.isAfter(range.second) }
    }
    val out = ArrayList<BudgetUse>()
    root(categoryId)?.let { r ->
        s.budgets[r]?.let { cap -> out += BudgetUse(r, cats[r]?.name.orEmpty(), cap, txns.filter { root(it.categoryId) == r }.sumOf { fx.convert(it.amount, it.currency) }) }
    }
    s.budgets[0L]?.let { cap -> out += BudgetUse(0L, "總預算", cap, txns.sumOf { fx.convert(it.amount, it.currency) }) }
    return out
}

/** 前 [months] 個月（不含這個月）各大分類的平均月支出，給預算設定當建議值；key 0 為總支出。 */
suspend fun Repository.budgetSuggestions(months: Int = 3): Map<Long, Long> {
    val s = settings.value
    val fx = fx.first()
    val cats = db.categories().all().associateBy { it.id }
    val cur = Periods.monthOf(java.time.LocalDate.now(), s.monthStartDay)
    val from = Periods.month(cur.minusMonths(months.toLong()), s.monthStartDay).first
    val to = Periods.month(cur.minusMonths(1), s.monthStartDay).second
    val txns = db.txns().all().filter { t ->
        t.type == TxnType.EXPENSE && t.status == TxnStatus.CONFIRMED &&
            Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate().let { !it.isBefore(from) && !it.isAfter(to) }
    }
    if (txns.isEmpty()) return emptyMap()
    val byRoot = txns.groupBy { t -> t.categoryId?.let { cats[it]?.parentId ?: it } ?: -1L }
        .mapValues { (_, l) -> l.sumOf { fx.convert(it.amount, it.currency) } / months }
    // 取整到百元
    fun round(v: Long) = (v + 9_999) / 10_000 * 10_000
    return byRoot.filterKeys { it > 0 }.mapValues { round(it.value) } + (0L to round(txns.sumOf { fx.convert(it.amount, it.currency) } / months))
}

/** 這筆支出讓預算跨過 80% 或 100% 時通知（同一個月每個門檻只通知一次）。 */
suspend fun Repository.checkBudgetAlert(t: TxnEntity) {
    if (t.type != TxnType.EXPENSE || t.status != TxnStatus.CONFIRMED) return
    val uses = budgetUse(t.categoryId, t.date)
    if (uses.isEmpty()) return
    val fx = fx.first()
    val amt = fx.convert(t.amount, t.currency)
    val ym = Periods.monthOf(Instant.ofEpochMilli(t.date).atZone(ZoneId.systemDefault()).toLocalDate(), settings.value.monthStartDay)
    val ctx = com.enwei.moneymanager.MoneyApp.instance
    for (u in uses) {
        val before = u.used - amt
        for (th in listOf(100, 80)) {
            val line = u.cap * th / 100
            if (before < line && u.used >= line) {
                val key = "$ym:${u.categoryId}:$th"
                if (key in settings.value.budgetAlerted) break
                settings.update { it.copy(budgetAlerted = (it.budgetAlerted + key).toList().takeLast(200).toSet()) }
                val cur = settings.value.baseCurrency
                val title = if (th >= 100) tr("${u.name} 已超出預算") else tr("${u.name} 已用 $th% 預算")
                val text = "${Money.withSymbol(u.used, cur)} / ${Money.withSymbol(u.cap, cur)}・" +
                    if (u.left >= 0) tr("剩") + " ${Money.withSymbol(u.left, cur)}" else tr("超出") + " ${Money.withSymbol(-u.left, cur)}"
                val days = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(), Periods.month(ym, settings.value.monthStartDay).second) + 1
                com.enwei.moneymanager.notify.AppNotices.post(ctx, com.enwei.moneymanager.notify.NoticeKind.BUDGET, ("budget$key").hashCode(),
                    title, text, "review",
                    listOfNotNull(
                        tr("這個月") + "：$text",
                        if (u.left > 0 && days > 0) tr("每天約") + " ${Money.withSymbol(u.left / days, cur)}（$days " + tr("天") + "）" else null,
                        tr("點一下看這個月的收支分析"),
                    ))
                break
            }
        }
    }
}
