package com.enwei.moneymanager.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 桌面小工具：本月支出、分類圓餅圖、快速記現金支出。 */
class SummaryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        MoneyApp.instance.appScope.launch {
            try { render(context, manager, ids) } finally { pending.finish() }
        }
    }

    companion object {
        private val palette = intArrayOf(
            0xFFF0605A.toInt(), 0xFFF59E42.toInt(), 0xFFF5C842.toInt(), 0xFF8BC34A.toInt(), 0xFF26A69A.toInt(), 0xFF90A4AE.toInt(),
        )

        /** 資料變動後呼叫，更新所有已放到桌面的小工具。 */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SummaryWidget::class.java))
            if (ids.isEmpty()) return
            MoneyApp.instance.appScope.launch { runCatching { render(context, manager, ids) } }
        }

        private fun open(context: Context, code: Int, route: String): PendingIntent = PendingIntent.getActivity(
            context, code,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        private suspend fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val repo = MoneyApp.repo
            val s = repo.settings.value
            val fx = repo.fx.first()
            val month = Periods.monthOf(LocalDate.now(), s.monthStartDay)
            val (start, end) = Periods.month(month, s.monthStartDay)
            val txns = repo.allTxns().filter {
                val d = it.date.toLocalDate()
                it.status == TxnStatus.CONFIRMED && !d.isBefore(start) && !d.isAfter(end)
            }
            val expense = txns.filter { it.type == TxnType.EXPENSE }
            val expenseTotal = expense.sumOf { fx.convert(it.amount, it.currency) }
            val incomeTotal = txns.filter { it.type == TxnType.INCOME }.sumOf { fx.convert(it.amount, it.currency) }
            val categories = repo.categories.first().associateBy { it.id }
            val byCat = expense.groupBy { it.categoryId }
                .map { (id, l) -> (categories[id]?.name ?: "未分類") to l.sumOf { fx.convert(it.amount, it.currency) } }
                .sortedByDescending { it.second }
            val top = byCat.take(4) + listOfNotNull(byCat.drop(4).takeIf { it.isNotEmpty() }?.let { rest -> "其他" to rest.sumOf { it.second } })
            val legend = top.joinToString("\n") { (name, v) ->
                "● $name ${if (expenseTotal > 0) v * 100 / expenseTotal else 0}%"
            }.ifEmpty { "本月還沒有支出" }
            val cash = repo.accounts.first().firstOrNull { it.type == AccountType.CASH && !it.archived }

            val views = RemoteViews(context.packageName, R.layout.widget_summary).apply {
                setTextViewText(R.id.widget_title, "${month.monthValue} 月支出")
                setTextViewText(R.id.widget_expense, Money.withSymbol(expenseTotal, s.baseCurrency))
                setTextViewText(R.id.widget_income, "收入 ${Money.withSymbol(incomeTotal, s.baseCurrency)}　結餘 ${Money.full(incomeTotal - expenseTotal)}")
                setTextViewText(R.id.widget_legend, legend)
                setImageViewBitmap(R.id.widget_pie, pie(top.map { it.second }))
                setOnClickPendingIntent(R.id.widget_stats, open(context, 10, "stats"))
                setOnClickPendingIntent(R.id.widget_cash, open(context, 11, "txn/0?type=EXPENSE&account=${cash?.id ?: 0}"))
                setOnClickPendingIntent(R.id.widget_income_btn, open(context, 12, "txn/0?type=INCOME"))
                setOnClickPendingIntent(R.id.widget_pending, open(context, 13, "bookmarks"))
            }
            manager.updateAppWidget(ids, views)
        }

        private fun pie(values: List<Long>): Bitmap {
            val size = 240
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = RectF(6f, 6f, size - 6f, size - 6f)
            val total = values.sum()
            if (total <= 0) {
                paint.color = 0xFFEEEEEE.toInt()
                c.drawOval(rect, paint)
            } else {
                var start = -90f
                values.forEachIndexed { i, v ->
                    val sweep = v.toFloat() / total * 360f
                    paint.color = palette[i % palette.size]
                    c.drawArc(rect, start, sweep, true, paint)
                    start += sweep
                }
            }
            // 中間挖空成甜甜圈
            paint.color = 0xFFFFFFFF.toInt()
            c.drawCircle(size / 2f, size / 2f, size * 0.26f, paint)
            return bmp
        }
    }
}
