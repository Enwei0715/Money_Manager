package com.enwei.moneymanager.data

import com.enwei.moneymanager.core.Frequency
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Periods
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 檢討的期間長度。 */
enum class ReviewSpan(val label: String, val months: Int) { MONTH("月", 1), QUARTER("季", 3), YEAR("年", 12) }

/** 一個大分類在本期與上期的金額。 */
data class ReviewCat(val id: Long?, val name: String, val amount: Long, val prev: Long, val share: Double) {
    val change: Long get() = amount - prev
}

/** 收支檢討的資料（金額皆換算為基準幣別的最小單位）。 */
data class ReviewData(
    val span: ReviewSpan,
    /** 期間代號，例如 M2026-10、Q2026-4、Y2026（AI 檢討的快取鍵） */
    val key: String,
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val income: Long,
    val expense: Long,
    val prevIncome: Long,
    val prevExpense: Long,
    /** 前三期平均支出 */
    val avgExpense: Long,
    val days: Int,
    val categories: List<ReviewCat>,
    val top: List<TxnEntity>,
    /** 週期設定裡的固定支出（換算成每月） */
    val fixedMonthly: Long,
    val unknownCount: Int,
    val unknownAmount: Long,
    /** 超出預算的分類（名稱、支出、預算） */
    val overBudget: List<Triple<String, Long, Long>>,
    val weekendShare: Double,
    val txnCount: Int,
) {
    val net: Long get() = income - expense
    val savingsRate: Double get() = if (income > 0) net * 100.0 / income else 0.0
    val dailyAvg: Long get() = if (days > 0) expense / days else 0
    val expenseChangePct: Double? get() = if (prevExpense > 0) (expense - prevExpense) * 100.0 / prevExpense else null

    /** 規則整理的重點觀察（不需要 AI）。 */
    fun observations(base: String): List<String> {
        fun m(v: Long) = Money.withSymbol(v, base)
        val out = ArrayList<String>()
        if (income > 0) out += "儲蓄率 ${"%.0f".format(savingsRate)}%" + when {
            savingsRate >= 30 -> "，存得不錯"
            savingsRate >= 10 -> "，還有空間"
            savingsRate >= 0 -> "，偏低"
            else -> "，支出超過收入 ${m(-net)}"
        }
        expenseChangePct?.let { p ->
            if (kotlin.math.abs(p) >= 10) out += "支出比上${span.label}${if (p > 0) "多" else "少"} ${m(kotlin.math.abs(expense - prevExpense))}（${"%+.0f".format(p)}%）"
        }
        categories.filter { it.prev > 0 && it.change > 0 && it.change * 100 / it.prev >= 20 && it.change >= 50_000 }
            .sortedByDescending { it.change }.take(2)
            .forEach { out += "${it.name}增加 ${m(it.change)}（${"%+.0f".format(it.change * 100.0 / it.prev)}%）" }
        categories.firstOrNull()?.let { out += "最大支出是${it.name}，占 ${"%.0f".format(it.share)}%" }
        if (fixedMonthly > 0) out += "固定支出（訂閱、房租等）每月 ${m(fixedMonthly)}" +
            if (expense > 0) "，約占支出 ${"%.0f".format(fixedMonthly * span.months * 100.0 / expense)}%" else ""
        overBudget.forEach { (n, spent, b) -> out += "$n 超出預算 ${m(spent - b)}（預算 ${m(b)}）" }
        if (unknownCount > 0) out += "有 $unknownCount 筆「不明」或未分類，共 ${m(unknownAmount)}，分類後檢討會更準"
        if (weekendShare >= 45) out += "週末花了 ${"%.0f".format(weekendShare)}% 的錢"
        return out
    }

    /** 給 AI 的摘要（不含商家以外的個資）。 */
    fun summary(base: String): String {
        fun m(v: Long) = Money.format(v, base)
        return buildString {
            appendLine("期間：$title（$start ~ $end，共 $days 天，$txnCount 筆）")
            appendLine("收入 ${m(income)}，支出 ${m(expense)}，結餘 ${m(net)}，儲蓄率 ${"%.1f".format(savingsRate)}%")
            appendLine("上${span.label}：收入 ${m(prevIncome)}，支出 ${m(prevExpense)}；前三${span.label}平均支出 ${m(avgExpense)}")
            appendLine("每日平均支出 ${m(dailyAvg)}；週末支出占 ${"%.0f".format(weekendShare)}%")
            appendLine("固定支出每月 ${m(fixedMonthly)}；不明或未分類 $unknownCount 筆共 ${m(unknownAmount)}")
            appendLine("支出分類（本期／上期／占比）：")
            categories.take(10).forEach { appendLine("- ${it.name}：${m(it.amount)}／${m(it.prev)}／${"%.0f".format(it.share)}%") }
            if (overBudget.isNotEmpty()) appendLine("超出預算：" + overBudget.joinToString("、") { "${it.first} ${m(it.second)}（預算 ${m(it.third)}）" })
            appendLine("最大幾筆：" + top.take(5).joinToString("、") { "${it.merchant ?: "未命名"} ${m(it.amount)}" })
        }
    }
}

/** AI 收支檢討（固定格式）。 */
@Serializable
data class AiReview(
    val score: Int = 0,
    val headline: String = "",
    val good: List<String> = emptyList(),
    val improve: List<String> = emptyList(),
    val actions: List<ReviewAction> = emptyList(),
    val expenseCap: Long? = null,
    val savingsTarget: Double? = null,
    val goals: List<String> = emptyList(),
    val createdAt: Long = 0,
)

@Serializable
data class ReviewAction(val action: String, val saving: Long? = null)

/** 期間的範圍、標題與快取鍵：[offset] 0 為目前這期，-1 為上一期。 */
fun reviewPeriod(span: ReviewSpan, offset: Int, startDay: Int, today: LocalDate = LocalDate.now()): Triple<Pair<LocalDate, LocalDate>, String, String> {
    val cur = Periods.monthOf(today, startDay)
    return when (span) {
        ReviewSpan.MONTH -> {
            val ym = cur.plusMonths(offset.toLong())
            Triple(Periods.month(ym, startDay), "${ym.year} 年 ${ym.monthValue} 月", "M$ym")
        }
        ReviewSpan.QUARTER -> {
            val q0 = YearMonth.of(cur.year, ((cur.monthValue - 1) / 3) * 3 + 1).plusMonths(offset * 3L)
            val q = (q0.monthValue - 1) / 3 + 1
            Triple(Periods.month(q0, startDay).first to Periods.month(q0.plusMonths(2), startDay).second, "${q0.year} 年第 $q 季", "Q${q0.year}-$q")
        }
        ReviewSpan.YEAR -> {
            val y = cur.year + offset
            Triple(Periods.month(YearMonth.of(y, 1), startDay).first to Periods.month(YearMonth.of(y, 12), startDay).second, "$y 年", "Y$y")
        }
    }
}

suspend fun Repository.spendingReview(span: ReviewSpan, offset: Int): ReviewData {
    val s = settings.value
    val conv = this.fx.first()
    val zone = ZoneId.systemDefault()
    val (range, title, key) = reviewPeriod(span, offset, s.monthStartDay)
    val txns = db.txns().all().filter { it.status == TxnStatus.CONFIRMED && it.type != TxnType.TRANSFER }
    val cats = db.categories().all().associateBy { it.id }
    fun day(t: TxnEntity) = java.time.Instant.ofEpochMilli(t.date).atZone(zone).toLocalDate()
    fun inRange(t: TxnEntity, r: Pair<LocalDate, LocalDate>) = day(t).let { !it.isBefore(r.first) && !it.isAfter(r.second) }
    fun amt(t: TxnEntity) = conv.convert(t.amount, t.currency)
    fun rootOf(id: Long?): CategoryEntity? = id?.let { cats[it] }?.let { c -> c.parentId?.let { cats[it] } ?: c }

    val cur = txns.filter { inRange(it, range) }
    val prevRange = reviewPeriod(span, offset - 1, s.monthStartDay).first
    val prev = txns.filter { inRange(it, prevRange) }
    val earlier = (1..3).map { reviewPeriod(span, offset - it, s.monthStartDay).first }
    val avg = earlier.map { r -> txns.filter { it.type == TxnType.EXPENSE && inRange(it, r) }.sumOf(::amt) }.let { l -> if (l.any { it > 0 }) l.sum() / l.count { it > 0 } else 0 }

    val exp = cur.filter { it.type == TxnType.EXPENSE }
    val expense = exp.sumOf(::amt)
    val prevExp = prev.filter { it.type == TxnType.EXPENSE }
    val prevBy = prevExp.groupBy { rootOf(it.categoryId)?.id }.mapValues { (_, l) -> l.sumOf(::amt) }
    val categories = exp.groupBy { rootOf(it.categoryId)?.id }.map { (id, l) ->
        val v = l.sumOf(::amt)
        ReviewCat(id, id?.let { cats[it]?.name } ?: "未分類", v, prevBy[id] ?: 0, if (expense > 0) v * 100.0 / expense else 0.0)
    }.sortedByDescending { it.amount }

    // 預算是每月金額：季、年依月數放大
    val budgets = s.budgets.mapNotNull { (id, b) ->
        val spent = exp.filter { rootOf(it.categoryId)?.id == id || it.categoryId == id }.sumOf(::amt)
        val cap = b * span.months
        if (b > 0 && spent > cap) Triple(cats[id]?.name ?: "?", spent, cap) else null
    }
    val fixed = db.recurring().all().filter { it.enabled && it.type == TxnType.EXPENSE }.sumOf { r ->
        val v = conv.convert(r.amount, r.currency)
        when (r.frequency) {
            Frequency.MONTHLY -> v / r.interval.coerceAtLeast(1)
            Frequency.YEARLY -> v / 12 / r.interval.coerceAtLeast(1)
            Frequency.WEEKLY -> v * 52 / 12 / r.interval.coerceAtLeast(1)
            Frequency.DAILY -> v * 30 / r.interval.coerceAtLeast(1)
        }
    }
    val unknown = exp.filter { it.categoryId == null || cats[it.categoryId]?.name == "不明" }
    val weekend = exp.filter { day(it).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) }.sumOf(::amt)
    // 進行中的這一期只算到今天
    val lastDay = minOf(range.second, LocalDate.now())
    return ReviewData(
        span, key, title, range.first, range.second,
        income = cur.filter { it.type == TxnType.INCOME }.sumOf(::amt), expense = expense,
        prevIncome = prev.filter { it.type == TxnType.INCOME }.sumOf(::amt), prevExpense = prevExp.sumOf(::amt), avgExpense = avg,
        days = (ChronoUnit.DAYS.between(range.first, lastDay) + 1).toInt().coerceAtLeast(1),
        categories = categories, top = exp.sortedByDescending(::amt).take(8), fixedMonthly = fixed,
        unknownCount = unknown.size, unknownAmount = unknown.sumOf(::amt), overBudget = budgets,
        weekendShare = if (expense > 0) weekend * 100.0 / expense else 0.0, txnCount = cur.size,
    )
}
