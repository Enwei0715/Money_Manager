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

    /** 小工具從桌面移除：一併刪掉它的設定 */
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { WidgetConfig.remove(context, it) } }

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
            val (mStart, mEnd) = Periods.month(month, s.monthStartDay)
            val allTx = repo.allTxns()
            val categories = repo.categories.first().associateBy { it.id }
            val cash = repo.accounts.first().firstOrNull { it.type == AccountType.CASH && !it.archived }
            val budget = s.budgets[0L]?.takeIf { it > 0 }
            ids.forEach { id ->
            // 每個小工具各自的設定（主要數字、期間、按鈕、透明度）
            val cfg = WidgetConfig.load(context, id)
            val today = LocalDate.now()
            val (start, end) = when (if (cfg.metric == 1) 0 else cfg.period) {
                1 -> today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)) to today
                2 -> today to today
                else -> mStart to mEnd
            }
            val periodLabel = when (if (cfg.metric == 1) 0 else cfg.period) { 1 -> "本週"; 2 -> "今天"; else -> "${month.monthValue} 月" }
            val txns = allTx.filter {
                val d = it.date.toLocalDate()
                it.status == TxnStatus.CONFIRMED && !d.isBefore(start) && !d.isAfter(end) && it.accountId?.toString() !in s.statsExcluded
            }
            val expense = txns.filter { it.type == TxnType.EXPENSE }
            val expenseTotal = expense.sumOf { fx.convert(it.amount, it.currency) }
            val incomeTotal = txns.filter { it.type == TxnType.INCOME }.sumOf { fx.convert(it.amount, it.currency) }
            // 子分類併入所屬大分類
            val byCat = expense.groupBy { t -> categories[t.categoryId]?.let { it.parentId ?: it.id } }
                .map { (cid, l) -> (categories[cid]?.name ?: "未分類") to l.sumOf { fx.convert(it.amount, it.currency) } }
                .sortedByDescending { it.second }
            val top = byCat.take(4) + listOfNotNull(byCat.drop(4).takeIf { it.isNotEmpty() }?.let { rest -> "其他" to rest.sumOf { it.second } })
            fun legend(n: Int): CharSequence = if (top.isEmpty()) com.enwei.moneymanager.ui.tr("還沒有支出") else android.text.SpannableStringBuilder().apply {
                top.take(n).forEachIndexed { i, (name, v) ->
                    // 直式：每行固定兩項（中文在任何字之間都可能換行，自己分行才不會把一項拆開）
                    if (i > 0) append(if (n >= 4 && i % 2 == 0) "\n" else "  ")
                    val st = length
                    append("●")
                    setSpan(android.text.style.ForegroundColorSpan(palette[i % palette.size]), st, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    append("\u00A0${com.enwei.moneymanager.ui.tr(name).replace(' ', '\u00A0')}\u00A0${if (expenseTotal > 0) v * 100 / expenseTotal else 0}%")
                }
            }
            // 圓餅圖只畫一次：好幾種大小的版面共用同一張圖（每個版面各畫一張會超過小工具的資料上限）
            val pieBmp = pie(top.map { it.second })
            val showBudget = cfg.metric == 1 && budget != null
            val btnIds = listOf(R.id.widget_cash, R.id.widget_income_btn, R.id.widget_pending)
            fun make(layout: Int, twoLines: Boolean, legendN: Int) = RemoteViews(context.packageName, layout).apply {
                if (showBudget) {
                    val left = budget!! - expenseTotal
                    setTextViewText(R.id.widget_title, com.enwei.moneymanager.ui.tr("${month.monthValue} 月預算剩餘"))
                    setTextViewText(R.id.widget_expense, Money.withSymbol(left, s.baseCurrency))
                    setTextColor(R.id.widget_expense, if (left >= 0) 0xFF3C82E6.toInt() else 0xFFF0605A.toInt())
                    setTextViewText(R.id.widget_income, com.enwei.moneymanager.ui.tr("已花") + " ${Money.withSymbol(expenseTotal, s.baseCurrency)}／${Money.withSymbol(budget, s.baseCurrency)}（${expenseTotal * 100 / budget}%）")
                } else {
                    setTextViewText(R.id.widget_title, com.enwei.moneymanager.ui.tr("${periodLabel}支出"))
                    setTextViewText(R.id.widget_expense, Money.withSymbol(expenseTotal, s.baseCurrency))
                    setTextViewText(R.id.widget_income, com.enwei.moneymanager.ui.tr("收入") + " ${Money.withSymbol(incomeTotal, s.baseCurrency)}" + (if (twoLines) "\n" else "　") +
                        com.enwei.moneymanager.ui.tr("結餘") + " ${Money.full(incomeTotal - expenseTotal)}")
                }
                setTextViewText(R.id.widget_legend, legend(legendN))
                setImageViewBitmap(R.id.widget_pie, pieBmp)
                setOnClickPendingIntent(R.id.widget_stats, open(context, id * 10 + 0, "stats"))
                // 下方按鈕：依設定的順序；沒選滿 3 個時其餘隱藏
                btnIds.forEachIndexed { slot, vid ->
                    val b = cfg.buttons.getOrNull(slot)?.let { WidgetConfig.BUTTONS.getOrNull(it) }
                    if (b == null) { setViewVisibility(vid, android.view.View.GONE); return@forEachIndexed }
                    setTextViewText(vid, com.enwei.moneymanager.ui.tr(b.label))
                    setTextColor(vid, b.color)
                    setOnClickPendingIntent(vid, open(context, id * 10 + slot + 1, b.route.replace("{cash}", "${cash?.id ?: 0}")))
                }
                // 按鈕之間的分隔線：按鈕少於 3 個時一起收掉
                if (cfg.buttons.size < 3) setViewVisibility(R.id.widget_div_2, android.view.View.GONE)
                if (cfg.buttons.size < 2) setViewVisibility(R.id.widget_div_1, android.view.View.GONE)
                val dark = Widgets.dark(context)
                setInt(R.id.widget_bg, "setColorFilter", if (dark) 0xFF26272B.toInt() else 0xFFFFFFFF.toInt())
                setInt(R.id.widget_bg, "setImageAlpha", cfg.alpha * 255 / 100)
                setTextColor(R.id.widget_legend, if (dark) 0xFFDDDDDD.toInt() else 0xFF444444.toInt())
                setTextColor(R.id.widget_title, if (dark) 0xFFAAAAAA.toInt() else 0xFF8A8A8A.toInt())
                val line = if (dark) 0x33FFFFFF else 0x22000000
                listOf(R.id.widget_div_top, R.id.widget_div_1, R.id.widget_div_2).forEach { setInt(it, "setBackgroundColor", line) }
            }
            // 系統字級（設定裡的字型大小）會讓所有 sp 字變大：算空間時一併考慮，否則大字級時圖例會被截
            val fs = context.resources.configuration.fontScale.coerceAtLeast(1f)
            // 依某個大小做出版面：接近正方或直的用直式（圓餅在上、文字在下），橫的用橫式；字級跟著大小調整
            fun viewsFor(w: Float, h: Float): RemoteViews {
                val tall = h >= 200f && h >= w * 0.7f
                // 橫式：圖例只放得下的項數（字會自動縮放，項數太多才會擠）
                val v = make(if (tall) R.layout.widget_summary_tall else R.layout.widget_summary, twoLines = false,
                    legendN = if (tall) 4 else if (w < 300f) 2 else 3)
                if (tall) {
                    val k = minOf(w / 220f, h / 260f).coerceIn(0.85f, 1.25f) / fs.coerceAtMost(1.3f)
                    mapOf(R.id.widget_title to 12f, R.id.widget_expense to 22f, R.id.widget_income to 12f, R.id.widget_legend to 12f)
                        .forEach { (vid, sp) -> v.setTextViewTextSize(vid, android.util.TypedValue.COMPLEX_UNIT_SP, sp * k) }
                } else if (w < 200f) v.setViewVisibility(R.id.widget_pie, android.view.View.GONE)
                return v
            }
                val v = if (android.os.Build.VERSION.SDK_INT >= 31) {
                    // Android 12 以上：一次給好幾種大小的版面，由桌面依實際大小挑「放得下的最大那一個」。
                    // 不靠桌面回報的大小（開關、旋轉、重新整理時常常不準），所以不會忽然換成被截到的版本
                    // 版面數量有上限：取常見的幾種寬高組合
                    // 橫式的字會自動縮放，只需要分「窄」「寬」「直式」幾種版面
                    val sizes = listOf(150f to 100f, 200f to 100f, 300f to 100f, 180f to 220f, 250f to 260f)
                    RemoteViews(sizes.associate { (w, h) -> android.util.SizeF(w, h) to viewsFor(w, h) })
                } else {
                    val size = Widgets.sizeOf(manager, id)
                    // 舊版 Android：用較保守的高度（最小值），寧可字小一點也不截掉
                    val o = manager.getAppWidgetOptions(id)
                    val hMin = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)?.takeIf { it > 0 }?.toFloat()
                    viewsFor(size?.first ?: 260f, hMin ?: size?.second ?: 150f)
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
