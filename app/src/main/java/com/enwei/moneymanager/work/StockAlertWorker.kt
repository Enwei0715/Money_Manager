package com.enwei.moneymanager.work

import com.enwei.moneymanager.data.guardRule
import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.Market
import com.enwei.moneymanager.core.TaiwanHolidays
import com.enwei.moneymanager.data.advisorSnapshot
import com.enwei.moneymanager.data.saveGuardPeaks
import com.enwei.moneymanager.data.quoteKey
import com.enwei.moneymanager.net.QuoteService
import com.enwei.moneymanager.ui.tr
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 盤中股價提醒（台股交易日 9:00–13:35，每 15 分鐘）：
 * 自己設的目標價／停損價，以及依紀律算出的動態停損、移動停利、分批停利。同一件事一天只通知一次。
 */
class StockAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = MoneyApp.repo
        val s = repo.settings.value
        val now = java.time.ZonedDateTime.now(ZoneId.of("Asia/Taipei"))
        val t = now.toLocalTime()
        if (!TaiwanHolidays.isBusinessDay(now.toLocalDate()) || t.isBefore(LocalTime.of(9, 0)) || t.isAfter(LocalTime.of(13, 40))) return Result.success()
        if (!s.stockAlertsOn && s.priceAlerts.isEmpty()) return Result.success()
        runCatching { check(applicationContext) }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<StockAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("stock-alerts", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** 檢查一次並發出通知（也可在 App 開著時呼叫）。 */
        suspend fun check(context: Context) {
            val repo = MoneyApp.repo
            val s = repo.settings.value
            if (!s.featureStocks) return
            val today = LocalDate.now().toEpochDay()
            val snap = repo.advisorSnapshot(fetch = true)
            val held = snap.holdings.map { it.symbol }
            val symbols = (held + s.priceAlerts.keys).distinct()
            if (symbols.isEmpty()) return
            val quotes = QuoteService.fetch(symbols.map { com.enwei.moneymanager.core.marketOf(it) to it })
            if (quotes.isNotEmpty()) repo.saveQuotes(quotes)
            val price = quotes.associate { it.key to it.price }
            val fired = s.alertsFired.toMutableMap()
            val out = ArrayList<Triple<String, String, String>>()
            fun fire(sym: String, kind: String, title: String, text: String) {
                val key = "$sym:$kind"
                if (fired[key] == today) return
                fired[key] = today
                out += Triple(sym, title, text)
            }
            s.priceAlerts.forEach { (sym, a) ->
                val p = price[quoteKey(com.enwei.moneymanager.core.marketOf(sym), sym)] ?: return@forEach
                if (a.target != null && p >= a.target) fire(sym, "target", "$sym 達到目標價", "現價 ${"%.2f".format(p)}，已達你設定的目標 ${"%.2f".format(a.target)}。")
                if (a.stop != null && p <= a.stop) fire(sym, "stop", "$sym 跌破停損價", "現價 ${"%.2f".format(p)}，已低於你設定的停損價 ${"%.2f".format(a.stop)}。")
            }
            // 保本＋移動停利：換上即時價重算，跌破停利價／減碼價／60 分 K 均線時通知
            val peaks = HashMap<String, Double?>()
            if (s.stockAlertsOn && s.investPlan.disciplineOn && s.investPlan.guardOn) snap.holdings.forEach { h ->
                val gi = h.guardInput ?: return@forEach
                val p = price[quoteKey(Market.TW, h.symbol)] ?: return@forEach
                val g = com.enwei.moneymanager.core.ProfitGuard.advise(gi.copy(price = p), s.investPlan.guardRule())
                peaks[h.symbol] = g.peak
                if (g.triggered) fire(h.symbol, "guard-${g.action.name}", "${h.symbol} ${h.name.orEmpty()}：${g.action.label}", "${g.note}，建議賣 ${g.sellShares.toLong()} 股")
            }
            if (peaks.isNotEmpty()) repo.saveGuardPeaks(peaks)
            if (s.stockAlertsOn && s.investPlan.disciplineOn && !s.investPlan.guardOn) snap.holdings.forEach { h ->
                val p = price[quoteKey(Market.TW, h.symbol)] ?: h.price ?: return@forEach
                val ad = com.enwei.moneymanager.core.Discipline.advise(h.style, h.avgCost, p, maxOf(h.peak ?: p, p), s.investPlan.let {
                    com.enwei.moneymanager.core.DisciplineRule(it.shortStopPct, it.shortTrailPct, it.longTrailPct, it.takeProfitPct)
                })
                when {
                    ad.triggered -> fire(h.symbol, "rule-${ad.action}", "${h.symbol} ${h.name.orEmpty()}：${ad.action}", ad.note)
                    ad.action.contains("停利") -> fire(h.symbol, "tp", "${h.symbol} ${h.name.orEmpty()}：${ad.action}", ad.note)
                }
            }
            // 持股即將除權息（3 天內）：提醒一次
            snap.holdings.forEach { h ->
                val key = Market.TW to h.symbol.uppercase()
                if (!repo.dividendCache.containsKey(key)) runCatching { QuoteService.fetchDividends(Market.TW, h.symbol) }.getOrNull()?.let { repo.dividendCache[key] = it }
                repo.dividendCache[key]?.firstOrNull { it.exEpochDay in today..(today + 3) }?.let { e ->
                    fire(h.symbol, "exdiv-${e.exEpochDay}", "${h.symbol} ${h.name.orEmpty()} 即將除權息",
                        "${LocalDate.ofEpochDay(e.exEpochDay)} 除權息" + (e.perShare.takeIf { it > 0 }?.let { "，每股現金 ${"%.2f".format(it)} 元，約可領 ${"%,.0f".format(it * h.shares)} 元" } ?: ""))
                }
            }
            // 只保留近 7 天的紀錄
            repo.settings.update { it.copy(alertsFired = fired.filterValues { d -> today - d <= 7 }) }
            if (out.isEmpty()) return
            out.forEach { (sym, title, text) ->
                com.enwei.moneymanager.notify.AppNotices.show(context, com.enwei.moneymanager.notify.NoticeKind.STOCK, 9400 + Math.floorMod(sym.hashCode(), 500),
                    com.enwei.moneymanager.notify.AppNotices.builder(context, com.enwei.moneymanager.notify.NoticeKind.STOCK, 9400 + Math.floorMod(sym.hashCode(), 500), tr(title), tr(text), "stock/$sym",
                        listOf(tr(text), tr("點一下看 K 線與紀律"))).setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH))
            }
        }

        /** 背景檢查 GitHub 上是否有新版；有的話通知一次（點通知到更新頁）。 */
        suspend fun checkUpdate(context: Context) {
            val repo = MoneyApp.repo
            val s = repo.settings.value
            if (!s.notifyUpdates || !com.enwei.moneymanager.BuildConfig.SELF_UPDATE) return
            val r = runCatching { com.enwei.moneymanager.net.Updater.latest(s.githubToken) }.getOrNull() ?: return
            if (!com.enwei.moneymanager.net.Updater.isNewer(r) || r.versionCode <= s.updateNotified || r.versionCode == s.updateSkipped) return
            repo.settings.update { it.copy(updateNotified = r.versionCode) }
            com.enwei.moneymanager.notify.AppNotices.post(context, com.enwei.moneymanager.notify.NoticeKind.UPDATE, 9330, tr("有新版本") + " ${r.tag}", tr("點一下前往更新"), "settings",
                com.enwei.moneymanager.ui.screens.parseNotes(r.notes).take(2).flatMap { (h, items) -> listOf("【$h】") + items.take(3).map { "・$it" } }
                    .ifEmpty { listOf(tr("點一下前往更新")) })
        }
    }
}
