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

    /** 使用者在桌面縮放小工具：依新的大小重畫 */
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
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
                it.status == TxnStatus.CONFIRMED && !d.isBefore(start) && !d.isAfter(end) && it.accountId?.toString() !in s.statsExcluded
            }
            val expense = txns.filter { it.type == TxnType.EXPENSE }
            val expenseTotal = expense.sumOf { fx.convert(it.amount, it.currency) }
            val incomeTotal = txns.filter { it.type == TxnType.INCOME }.sumOf { fx.convert(it.amount, it.currency) }
            val categories = repo.categories.first().associateBy { it.id }
            // 子分類併入所屬大分類
            val byCat = expense.groupBy { t -> categories[t.categoryId]?.let { it.parentId ?: it.id } }
                .map { (id, l) -> (categories[id]?.name ?: "未分類") to l.sumOf { fx.convert(it.amount, it.currency) } }
                .sortedByDescending { it.second }
            val top = byCat.take(4) + listOfNotNull(byCat.drop(4).takeIf { it.isNotEmpty() }?.let { rest -> "其他" to rest.sumOf { it.second } })
            // 圖例：前三名排成一行，圓點顏色和圓餅一樣（不會被小工具的高度截掉）
            fun legend(n: Int): CharSequence = if (top.isEmpty()) com.enwei.moneymanager.ui.tr("本月還沒有支出") else android.text.SpannableStringBuilder().apply {
                top.take(n).forEachIndexed { i, (name, v) ->
                    if (i > 0) append("  ")
                    val st = length
                    append("●")
                    setSpan(android.text.style.ForegroundColorSpan(palette[i % palette.size]), st, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // 圓點、名稱、百分比之間用不換行空白：換行只會發生在兩項之間，不會把「日用品」和「17%」拆到兩行
                    append("\u00A0${com.enwei.moneymanager.ui.tr(name).replace(' ', '\u00A0')}\u00A0${if (expenseTotal > 0) v * 100 / expenseTotal else 0}%")
                }
            }
            val cash = repo.accounts.first().firstOrNull { it.type == AccountType.CASH && !it.archived }

            fun make(layout: Int, twoLines: Boolean, legendN: Int) = RemoteViews(context.packageName, layout).apply {
                setTextViewText(R.id.widget_title, com.enwei.moneymanager.ui.tr("${month.monthValue} 月支出"))
                setTextViewText(R.id.widget_expense, Money.withSymbol(expenseTotal, s.baseCurrency))
                // 文字欄不夠寬時收入、結餘各一行，不截掉
                setTextViewText(R.id.widget_income, com.enwei.moneymanager.ui.tr("收入") + " ${Money.withSymbol(incomeTotal, s.baseCurrency)}" + (if (twoLines) "\n" else "　") +
                    com.enwei.moneymanager.ui.tr("結餘") + " ${Money.full(incomeTotal - expenseTotal)}")
                setTextViewText(R.id.widget_legend, legend(legendN))
                setImageViewBitmap(R.id.widget_pie, pie(top.map { it.second }))
                setOnClickPendingIntent(R.id.widget_stats, open(context, 10, "stats"))
                setOnClickPendingIntent(R.id.widget_cash, open(context, 11, "txn/0?type=EXPENSE&account=${cash?.id ?: 0}"))
                setOnClickPendingIntent(R.id.widget_income_btn, open(context, 12, "txn/0?type=INCOME"))
                setOnClickPendingIntent(R.id.widget_pending, open(context, 13, "txn/0?type=TRANSFER"))
                setTextViewText(R.id.widget_cash, com.enwei.moneymanager.ui.tr("－ 現金支出"))
                setTextViewText(R.id.widget_income_btn, com.enwei.moneymanager.ui.tr("＋ 收入"))
                setTextViewText(R.id.widget_pending, com.enwei.moneymanager.ui.tr("⇄ 轉帳"))
                val dark = Widgets.dark(context)
                setInt(R.id.widget_bg, "setColorFilter", if (dark) 0xFF26272B.toInt() else 0xFFFFFFFF.toInt())
                setInt(R.id.widget_bg, "setImageAlpha", 255)
                setTextColor(R.id.widget_legend, if (dark) 0xFFDDDDDD.toInt() else 0xFF444444.toInt())
                setTextColor(R.id.widget_title, if (dark) 0xFFAAAAAA.toInt() else 0xFF8A8A8A.toInt())
                val line = if (dark) 0x33FFFFFF else 0x22000000
                listOf(R.id.widget_div_top, R.id.widget_div_1, R.id.widget_div_2).forEach { setInt(it, "setBackgroundColor", line) }
            }
            // 依每個小工具的實際大小選版面：接近正方或直的用直式（圓餅在上、文字在下），橫的用橫式；字級跟著大小調整
            ids.forEach { id ->
                val size = Widgets.sizeOf(manager, id)
                val w = size?.first ?: 260f; val h = size?.second ?: 150f
                val tall = h >= 200f && h >= w * 0.7f
                // 橫式的文字欄窄：圖例只放得下的項數（不出現「…」）
                val twoLines = !tall && w < 250f
                val v = make(if (tall) R.layout.widget_summary_tall else R.layout.widget_summary, twoLines = twoLines,
                    legendN = if (tall) 4 else if (w < 300f) 2 else 3)
                // 橫式：上半部（圓餅＋文字）可用的高度＝總高 − 邊距與下方按鈕列（約 76dp），再留一成餘裕（各家桌面回報的大小不一定準）
                val avail = (h - 76f) * 0.9f
                // 四行文字（標題、金額、收入結餘、圖例）在 1 倍字級約 85dp 高；放不下就先拿掉圖例（三行約 61dp），再縮字
                val k0 = (if (tall) minOf(w / 220f, h / 260f) else w / 260f).coerceIn(0.8f, 1.6f)
                val lines = if (twoLines) 16f else 0f
                val showLegend = tall || (h >= 130f && avail / (85f + lines) >= 0.9f)
                val k = if (tall) k0 else minOf(k0, avail / ((if (showLegend) 85f else 61f) + lines)).coerceIn(0.7f, 1.6f)
                mapOf(R.id.widget_title to 12f, R.id.widget_expense to 22f, R.id.widget_income to 12f, R.id.widget_legend to 12f,
                    R.id.widget_cash to 14f, R.id.widget_income_btn to 14f, R.id.widget_pending to 14f)
                    .forEach { (vid, sp) -> v.setTextViewTextSize(vid, android.util.TypedValue.COMPLEX_UNIT_SP, sp * k) }
                if (!tall) {
                    if (w < 220f) v.setViewVisibility(R.id.widget_pie, android.view.View.GONE)
                    if (!showLegend) v.setViewVisibility(R.id.widget_legend, android.view.View.GONE)
                    // 橫式：圓餅跟著放大，但不超過可用高度（Android 12 以上才能調整大小）
                    if (android.os.Build.VERSION.SDK_INT >= 31) {
                        val d = minOf(84f * k0, avail).coerceAtLeast(48f)
                        v.setViewLayoutWidth(R.id.widget_pie, d, android.util.TypedValue.COMPLEX_UNIT_DIP)
                        v.setViewLayoutHeight(R.id.widget_pie, d, android.util.TypedValue.COMPLEX_UNIT_DIP)
                    }
                }
                manager.updateAppWidget(id, v)
            }
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
            // 中間挖空成甜甜圈（透明，適用任何背景）
            paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR)
            c.drawCircle(size / 2f, size / 2f, size * 0.26f, paint)
            return bmp
        }
    }
}
