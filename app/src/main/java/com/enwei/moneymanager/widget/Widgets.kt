package com.enwei.moneymanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.widget.RemoteViews
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.R
import com.enwei.moneymanager.core.Money
import com.enwei.moneymanager.core.Periods
import com.enwei.moneymanager.data.AccountType
import com.enwei.moneymanager.data.TxnStatus
import com.enwei.moneymanager.data.TxnType
import com.enwei.moneymanager.data.toLocalDate
import com.enwei.moneymanager.ui.MainActivity
import com.enwei.moneymanager.ui.tr
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.reflect.KClass

/** 所有桌面小工具共用：深淺色、透明度、開啟 App 指定頁面、資料變動時全部更新。 */
object Widgets {
    private val all: List<KClass<out AppWidgetProvider>> = listOf(
        QuickWidget::class, TodayWidget::class, StocksWidget::class, AccountsWidget::class, RecentWidget::class,
    )

    private suspend fun build(cls: KClass<out AppWidgetProvider>, c: Context): RemoteViews? = when (cls) {
        QuickWidget::class -> QuickWidget.build(c)
        TodayWidget::class -> TodayWidget.build(c)
        StocksWidget::class -> StocksWidget.build(c)
        AccountsWidget::class -> AccountsWidget.build(c)
        RecentWidget::class -> RecentWidget.build(c)
        else -> null
    }

    /** 資料變動後更新所有已放到桌面的小工具。 */
    fun refreshAll(context: Context) {
        SummaryWidget.refresh(context)
        val m = AppWidgetManager.getInstance(context)
        all.forEach { cls ->
            val ids = m.getAppWidgetIds(ComponentName(context, cls.java))
            if (ids.isNotEmpty()) MoneyApp.instance.appScope.launch {
                ids.forEach { id -> runCatching { build(cls, context)?.let { m.updateAppWidget(id, fit(context, m, id, cls, it)) } } }
            }
        }
    }

    fun open(context: Context, code: Int, route: String): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun dark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** 背景、主要文字與次要文字顏色。 */
    fun style(context: Context, v: RemoteViews, primaryIds: List<Int>, secondaryIds: List<Int>, tintIcon: Boolean = true) {
        val d = dark(context)
        // 標題列的小圖示跟著次要文字顏色（深色模式變淺）
        if (tintIcon) v.setInt(R.id.w_icon, "setColorFilter", if (d) 0xFFAAAAAA.toInt() else 0xFF8A8A8A.toInt())
        v.setInt(R.id.widget_bg, "setColorFilter", if (d) 0xFF26272B.toInt() else 0xFFFFFFFF.toInt())
        v.setInt(R.id.widget_bg, "setImageAlpha", 255)
        primaryIds.forEach { v.setTextColor(it, if (d) 0xFFEEEEEE.toInt() else 0xFF333333.toInt()) }
        secondaryIds.forEach { v.setTextColor(it, if (d) 0xFFAAAAAA.toInt() else 0xFF8A8A8A.toInt()) }
    }

    /**
     * 依小工具實際大小調整：字級跟著大小等比例放大縮小（有上下限），太矮時收起次要的那一行、清單只放得下的列數。
     * 原本字級固定：縮小時字被截掉，放大時只是留白。
     */
    class Fit(val baseW: Float, val baseH: Float, val sizes: Map<Int, Float>, val hideShort: List<Int> = emptyList(), val shortH: Float = 0f,
              val listHeader: Float = 0f, val rowH: Float = 0f)

    private fun specOf(cls: KClass<out AppWidgetProvider>): Fit? = when (cls) {
        QuickWidget::class -> Fit(70f, 70f, mapOf(R.id.w_title to 12f), hideShort = listOf(R.id.w_title), shortH = 56f)
        TodayWidget::class -> Fit(150f, 80f, mapOf(R.id.w_title to 12f, R.id.w_value to 22f, R.id.w_sub to 11f),
            hideShort = listOf(R.id.w_sub, R.id.w_progress), shortH = 64f)
        StocksWidget::class -> Fit(150f, 80f, mapOf(R.id.w_title to 12f, R.id.w_value to 20f, R.id.w_sub to 12f),
            hideShort = listOf(R.id.w_sub), shortH = 64f)
        AccountsWidget::class -> Fit(165f, 160f, mapOf(R.id.w_title to 12f, R.id.w_value to 20f) + (rowName + rowAmount).associateWith { 13f },
            listHeader = 70f, rowH = 22f)
        RecentWidget::class -> Fit(230f, 200f, mapOf(R.id.w_title to 12f, R.id.w_sub to 11f) + (rowName + rowAmount).associateWith { 13f },
            listHeader = 56f, rowH = 24f)
        else -> null
    }

    /** 小工具目前的大小（dp）：直向時寬取 minWidth、高取 maxHeight */
    fun sizeOf(m: AppWidgetManager, id: Int): Pair<Float, Float>? {
        val o = m.getAppWidgetOptions(id) ?: return null
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0)
        return if (w > 0 && h > 0) w.toFloat() to h.toFloat() else null
    }

    fun fit(context: Context, m: AppWidgetManager, id: Int, cls: KClass<out AppWidgetProvider>, v: RemoteViews): RemoteViews {
        val spec = specOf(cls) ?: return v
        val (w, h) = sizeOf(m, id) ?: return v
        val k = minOf(w / spec.baseW, h / spec.baseH).coerceIn(0.78f, 1.45f)
        spec.sizes.forEach { (vid, sp) -> v.setTextViewTextSize(vid, android.util.TypedValue.COMPLEX_UNIT_SP, sp * k) }
        if (spec.shortH > 0 && h < spec.shortH) spec.hideShort.forEach { v.setViewVisibility(it, android.view.View.GONE) }
        // 清單：只顯示放得下的列數（原本固定 5 列，矮的時候最後幾列被截一半）
        if (spec.rowH > 0) {
            val fitRows = ((h - spec.listHeader * k) / (spec.rowH * k)).toInt().coerceIn(1, rows.size)
            rows.forEachIndexed { i, r -> if (i >= fitRows) v.setViewVisibility(r, android.view.View.GONE) }
        }
        return v
    }

    const val EXPENSE = 0xFFF0605A.toInt()
    const val INCOME = 0xFF3C82E6.toInt()
    const val UP = 0xFFE53935.toInt()
    const val DOWN = 0xFF2E9E4F.toInt()

    val rowName = listOf(R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4, R.id.w_n5)
    val rowAmount = listOf(R.id.w_a1, R.id.w_a2, R.id.w_a3, R.id.w_a4, R.id.w_a5)
    val rows = listOf(R.id.w_row1, R.id.w_row2, R.id.w_row3, R.id.w_row4, R.id.w_row5)

    suspend fun monthTxns(): Triple<List<com.enwei.moneymanager.data.TxnEntity>, LocalDate, LocalDate> {
        val s = MoneyApp.repo.settings.value
        val (start, end) = Periods.month(Periods.monthOf(LocalDate.now(), s.monthStartDay), s.monthStartDay)
        val txns = MoneyApp.repo.allTxns().filter {
            val d = it.date.toLocalDate()
            it.status == TxnStatus.CONFIRMED && !d.isBefore(start) && !d.isAfter(end) && it.accountId?.toString() !in s.statsExcluded
        }
        return Triple(txns, start, end)
    }
}

/** 共用：onUpdate 時在背景產生畫面。 */
abstract class BaseWidget : AppWidgetProvider() {
    abstract suspend fun render(context: Context): RemoteViews
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        MoneyApp.instance.appScope.launch {
            try { ids.forEach { id -> runCatching { manager.updateAppWidget(id, Widgets.fit(context, manager, id, this@BaseWidget::class, render(context))) } } }
            finally { pending.finish() }
        }
    }

    /** 使用者在桌面縮放小工具：依新的大小重畫 */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }
}

/** 1×1：一鍵記帳。 */
class QuickWidget : BaseWidget() {
    override suspend fun render(context: Context) = build(context)
    companion object {
        suspend fun build(context: Context): RemoteViews = RemoteViews(context.packageName, R.layout.widget_quick).apply {
            setTextViewText(R.id.w_title, tr("記一筆"))
            Widgets.style(context, this, emptyList(), listOf(R.id.w_title), tintIcon = false)
            setOnClickPendingIntent(R.id.w_root, Widgets.open(context, 101, "txn/0?type=EXPENSE"))
        }
    }
}

/** 2×1：今日支出、本月支出與結餘。 */
class TodayWidget : BaseWidget() {
    override suspend fun render(context: Context) = build(context)
    companion object {
        suspend fun build(context: Context): RemoteViews {
            val repo = MoneyApp.repo
            val fx = repo.fx.first()
            val base = repo.settings.value.baseCurrency
            val (month, _, _) = Widgets.monthTxns()
            val today = LocalDate.now()
            val todayExp = month.filter { it.type == TxnType.EXPENSE && it.date.toLocalDate() == today }.sumOf { fx.convert(it.amount, it.currency) }
            val exp = month.filter { it.type == TxnType.EXPENSE }.sumOf { fx.convert(it.amount, it.currency) }
            val inc = month.filter { it.type == TxnType.INCOME }.sumOf { fx.convert(it.amount, it.currency) }
            return RemoteViews(context.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.w_title, tr("今日支出"))
                setTextViewText(R.id.w_value, Money.withSymbol(todayExp, base))
                // 有總預算時：下面顯示預算用了多少與進度條
                val budget = repo.settings.value.budgets[0L]
                if (budget != null && budget > 0) {
                    val pct = (exp * 100 / budget).toInt()
                    setTextViewText(R.id.w_sub, "${tr("本月支出")} ${Money.withSymbol(exp, base)} · ${tr("預算")} $pct%")
                    setProgressBar(R.id.w_progress, 100, pct.coerceIn(0, 100), false)
                    setViewVisibility(R.id.w_progress, android.view.View.VISIBLE)
                } else {
                    setTextViewText(R.id.w_sub, "${tr("本月支出")} ${Money.withSymbol(exp, base)} · ${tr("結餘")} ${Money.full(inc - exp)}")
                    setViewVisibility(R.id.w_progress, android.view.View.GONE)
                }
                Widgets.style(context, this, emptyList(), listOf(R.id.w_title, R.id.w_sub))
                setTextColor(R.id.w_value, Widgets.EXPENSE)
                setOnClickPendingIntent(R.id.w_root, Widgets.open(context, 102, "txns"))
            }
        }
    }
}

/** 2×1：股票市值、預估損益、今日漲跌。 */
class StocksWidget : BaseWidget() {
    override suspend fun render(context: Context) = build(context)
    companion object {
        suspend fun build(context: Context): RemoteViews {
            val repo = MoneyApp.repo
            // 設定裡關掉股票功能：小工具只顯示已關閉，點了開設定
            if (!repo.settings.value.featureStocks) return RemoteViews(context.packageName, R.layout.widget_stocks).apply {
                setTextViewText(R.id.w_title, tr("股票"))
                setTextViewText(R.id.w_value, "—")
                setTextViewText(R.id.w_sub, tr("股票功能已關閉（設定 → 功能開關）"))
                Widgets.style(context, this, listOf(R.id.w_value), listOf(R.id.w_title, R.id.w_sub))
                setOnClickPendingIntent(R.id.w_root, Widgets.open(context, 103, "config"))
            }
            val fx = repo.fx.first()
            val base = repo.settings.value.baseCurrency
            val open = repo.holdings.first().filter { it.shares > 0.0001 }
            val value = open.sumOf { fx.convert(Money.toMinor(it.marketValue), it.currency) }
            val pnl = open.sumOf { fx.convert(Money.toMinor(it.unrealized), it.currency) }
            val day = open.sumOf { fx.convert(Money.toMinor(it.dayChange), it.currency) }
            val cost = open.sumOf { fx.convert(Money.toMinor(it.netCost), it.currency) }
            val pct = if (cost > 0) pnl * 100.0 / cost else 0.0
            return RemoteViews(context.packageName, R.layout.widget_stocks).apply {
                setTextViewText(R.id.w_title, "${tr("股票")} · ${open.size} ${tr("檔")}")
                setTextViewText(R.id.w_value, Money.withSymbol(value, base))
                setTextViewText(R.id.w_sub, "${tr("預估損益")} ${if (pnl > 0) "+" else ""}${Money.full(pnl)} (${"%+.2f".format(pct)}%) · ${tr("今日")} ${if (day > 0) "+" else ""}${Money.full(day)}")
                Widgets.style(context, this, listOf(R.id.w_value), listOf(R.id.w_title))
                setTextColor(R.id.w_sub, if (pnl >= 0) Widgets.UP else Widgets.DOWN)
                setOnClickPendingIntent(R.id.w_root, Widgets.open(context, 103, "invest"))
            }
        }
    }
}

/** 3×2：淨資產與前 5 個帳戶餘額。 */
class AccountsWidget : BaseWidget() {
    override suspend fun render(context: Context) = build(context)
    companion object {
        suspend fun build(context: Context): RemoteViews {
            val repo = MoneyApp.repo
            val fx = repo.fx.first()
            val base = repo.settings.value.baseCurrency
            val list = repo.balances.first().filter { !it.account.archived && it.account.type != AccountType.BROKERAGE }
                .sortedWith(compareBy({ it.account.type }, { it.account.sortOrder }, { it.account.id }))
            val total = list.filter { it.account.includeInNetWorth }.sumOf { fx.convert(it.balance, it.account.currency) }
            val top = list.sortedByDescending { kotlin.math.abs(fx.convert(it.balance, it.account.currency)) }.take(5)
            return RemoteViews(context.packageName, R.layout.widget_accounts).apply {
                setTextViewText(R.id.w_title, tr("帳戶") + " · " + tr("合計"))
                setTextViewText(R.id.w_value, Money.withSymbol(total, base))
                Widgets.style(context, this, listOf(R.id.w_value) + Widgets.rowName, listOf(R.id.w_title))
                Widgets.rows.forEachIndexed { i, row ->
                    val b = top.getOrNull(i)
                    setViewVisibility(row, if (b == null) android.view.View.GONE else android.view.View.VISIBLE)
                    if (b != null) {
                        setTextViewText(Widgets.rowName[i], tr(b.account.name))
                        setTextViewText(Widgets.rowAmount[i], Money.withSymbol(b.balance, b.account.currency))
                        setTextColor(Widgets.rowAmount[i], if (b.balance >= 0) Widgets.INCOME else Widgets.EXPENSE)
                    }
                }
                setOnClickPendingIntent(R.id.w_root, Widgets.open(context, 104, "accounts"))
            }
        }
    }
}

/** 4×3：最近 5 筆交易與本月總預算進度。 */
class RecentWidget : BaseWidget() {
    override suspend fun render(context: Context) = build(context)
    companion object {
        suspend fun build(context: Context): RemoteViews {
            val repo = MoneyApp.repo
            val fx = repo.fx.first()
            val s = repo.settings.value
            val cats = repo.categories.first().associateBy { it.id }
            val accs = repo.accounts.first().associateBy { it.id }
            val recent = repo.allTxns().filter { it.status == TxnStatus.CONFIRMED && it.date <= System.currentTimeMillis() }
                .sortedByDescending { it.date }.take(5)
            val (month, _, _) = Widgets.monthTxns()
            val exp = month.filter { it.type == TxnType.EXPENSE }.sumOf { fx.convert(it.amount, it.currency) }
            val budget = s.budgets[0L] ?: 0L
            return RemoteViews(context.packageName, R.layout.widget_recent).apply {
                setTextViewText(R.id.w_title, tr("最近交易"))
                Widgets.style(context, this, Widgets.rowName, listOf(R.id.w_title, R.id.w_sub))
                Widgets.rows.forEachIndexed { i, row ->
                    val t = recent.getOrNull(i)
                    setViewVisibility(row, if (t == null) android.view.View.GONE else android.view.View.VISIBLE)
                    if (t != null) {
                        val d = t.date.toLocalDate()
                        val label = t.merchant?.takeIf { it.isNotBlank() }
                            ?: if (t.type == TxnType.TRANSFER) tr("轉帳") else t.categoryId?.let { cats[it]?.name }?.let(::tr) ?: tr("未分類")
                        setTextViewText(Widgets.rowName[i], "${d.monthValue}/${d.dayOfMonth}  $label · ${accs[t.accountId]?.name.orEmpty()}")
                        val sign = when (t.type) { TxnType.EXPENSE -> "-"; TxnType.INCOME -> "+"; else -> "" }
                        setTextViewText(Widgets.rowAmount[i], sign + Money.withSymbol(t.amount, t.currency))
                        setTextColor(Widgets.rowAmount[i], when (t.type) { TxnType.EXPENSE -> Widgets.EXPENSE; TxnType.INCOME -> Widgets.INCOME; else -> 0xFF8A8A8A.toInt() })
                    }
                }
                if (budget > 0) {
                    val pct = (exp * 100 / budget).toInt()
                    setTextViewText(R.id.w_sub, "${tr("總預算")} ${Money.withSymbol(exp, s.baseCurrency)} / ${Money.withSymbol(budget, s.baseCurrency)}（$pct%）")
                    setProgressBar(R.id.w_progress, 100, pct.coerceIn(0, 100), false)
                    setViewVisibility(R.id.w_progress, android.view.View.VISIBLE)
                } else {
                    setTextViewText(R.id.w_sub, "${tr("本月支出")} ${Money.withSymbol(exp, s.baseCurrency)}")
                    setViewVisibility(R.id.w_progress, android.view.View.GONE)
                }
                setOnClickPendingIntent(R.id.w_root, Widgets.open(context, 105, "txns"))
            }
        }
    }
}
