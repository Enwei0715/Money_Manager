package com.enwei.moneymanager.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.Repository
import com.enwei.moneymanager.net.FxService
import com.enwei.moneymanager.net.QuoteService
import java.util.concurrent.TimeUnit

/** 每隔數小時：產生週期交易、信用卡自動扣繳、更新匯率與股價。 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = MoneyApp.repo
        repo.runRecurring()
        repo.runFeeRebates()
        // AI 自動整理重複的交易（沒開 AI 時不做事）
        runCatching { repo.aiAutoTidy() }
        runCatching { warnFunding(applicationContext, repo) }
        runCatching { warnLoans(applicationContext, repo) }
        val ok = runCatching { refreshMarket(repo) }.isSuccess
        return if (ok || runAttemptCount > 2) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(4, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "sync-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<SyncWorker>().build(),
            )
        }

        suspend fun refreshFx(repo: Repository) {
            val base = repo.settings.value.baseCurrency
            val daily = runCatching { FxService.fetch(base) }
            daily.getOrNull()?.let { repo.saveFx(it) }
            // 自己用到的幣別再用即時報價覆蓋
            val used = repo.usedCurrencies() + repo.settings.value.subCurrency + FxService.common.take(8)
            val live = FxService.fetchRealtime(base, used)
            if (live.isNotEmpty()) repo.saveFx(live)
            if (daily.isFailure && live.isEmpty()) daily.getOrThrow()
            repo.settings.update { it.copy(lastFxUpdate = System.currentTimeMillis()) }
        }

        suspend fun refreshQuotes(repo: Repository) {
            val symbols = repo.allTrades().map { it.market to it.symbol.uppercase() }.distinct()
            if (symbols.isEmpty()) return
            repo.saveQuotes(QuoteService.fetch(symbols))
            repo.settings.update { it.copy(lastQuoteUpdate = System.currentTimeMillis()) }
            com.enwei.moneymanager.widget.Widgets.refreshAll(MoneyApp.instance)
        }

        /** 自動除息：抓持有過的台股除息資料並分配到各批庫存。 */
        /** 下載除權息資料並重算自動股利；[only] 指定時只處理這些代號（儲存交易後用）。 */
        suspend fun refreshDividends(repo: Repository, only: Set<String>? = null) {
            repo.pruneAutoDividends()
            repo.allTrades().filter { it.market == com.enwei.moneymanager.core.Market.TW && it.side == com.enwei.moneymanager.core.StockSide.BUY }
                .map { it.symbol.uppercase() }.distinct().filter { only == null || it in only }.forEach { sym ->
                    val events = runCatching { com.enwei.moneymanager.net.QuoteService.fetchDividends(com.enwei.moneymanager.core.Market.TW, sym) }.getOrNull()
                        ?: return@forEach
                    repo.dividendCache[com.enwei.moneymanager.core.Market.TW to sym] = events
                    if (events.isNotEmpty()) runCatching { repo.applyDividends(com.enwei.moneymanager.core.Market.TW, sym, events) }
                }
        }

        /**
         * 每次同步重新下載今年與明年的政府辦公日曆（政府若調整放假會跟著更新），
         * 並檢查人事行政總處的颱風停班公告。
         */
        suspend fun refreshHolidays(repo: Repository) {
            runCatching { refreshTyphoon(repo) }
            val now = java.time.LocalDate.now().year
            val got = HashMap<Int, com.enwei.moneymanager.core.YearCalendar>()
            for (y in now..now + 1) {
                val root = runCatching {
                    com.enwei.moneymanager.net.Http.getJson("https://raw.githubusercontent.com/ruyut/TaiwanCalendar/master/data/$y.json")
                }.getOrNull() as? kotlinx.serialization.json.JsonArray ?: continue
                val hol = HashMap<String, String>(); val work = HashSet<String>(); val names = HashMap<String, String>()
                root.forEach { e ->
                    val o = e as? kotlinx.serialization.json.JsonObject ?: return@forEach
                    val date = (o["date"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: return@forEach
                    if (date.length != 8) return@forEach
                    val isHoliday = (o["isHoliday"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "true"
                    val week = (o["week"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                    val weekend = week == "六" || week == "日"
                    val md = date.substring(4)
                    if (isHoliday && !weekend) hol[md] = (o["description"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    if (!isHoliday && weekend) work += md
                    val desc = (o["description"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
                    if (desc.isNotBlank() && (weekend || !isHoliday)) names[md] = desc
                }
                // 資料不完整（例如明年尚未公布）就不採用
                if (root.size >= 300 && hol.size >= 5) got[y] = com.enwei.moneymanager.core.YearCalendar(hol, work, names)
            }
            if (got.isEmpty()) return
            com.enwei.moneymanager.core.TaiwanHolidays.update(got)
            val all = com.enwei.moneymanager.data.HolidayCache.decode(repo.settings.value.holidayCache) + got
            repo.settings.update { it.copy(holidayCache = com.enwei.moneymanager.data.HolidayCache.encode(all)) }
        }

        /** 臺北市因颱風等天然災害全天停止上班時，金融機構也停止營業（信用卡繳款截止日、自動扣繳順延）。 */
        suspend fun refreshTyphoon(repo: Repository) {
            val html = com.enwei.moneymanager.net.Http.getText("https://www.dgpa.gov.tw/typh/daily/nds.html")
            val today = java.time.LocalDate.now()
            val notice = com.enwei.moneymanager.core.TyphoonNotice.parse(html, today)
            val old = com.enwei.moneymanager.data.TyphoonDays.decode(repo.settings.value.typhoonDays)
            val merged = old.toMutableMap()
            notice.forEach { (d, stop) -> if (stop) merged[d] = "颱風假" else merged.remove(d) }
            merged.keys.removeAll { it.isBefore(today.minusYears(2)) }
            com.enwei.moneymanager.core.TaiwanHolidays.setExtra(merged)
            if (merged != old) repo.settings.update { it.copy(typhoonDays = com.enwei.moneymanager.data.TyphoonDays.encode(merged)) }
        }

        /** 7 天內要自動扣繳卡費、扣款帳戶餘額卻不夠時通知（同一件事只通知一次）。 */
        suspend fun warnFunding(context: Context, repo: Repository) {
            val list = repo.fundingWarnings(days = 7)
            val key = list.joinToString(";") { "${it.from.id}:${it.date}:${it.needed}" }
            if (key == repo.settings.value.fundingWarnKey) return
            repo.settings.update { it.copy(fundingWarnKey = key) }
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (list.isEmpty() || !nm.areNotificationsEnabled()) return
            val w = list.first()
            val open = android.app.PendingIntent.getActivity(
                context, 9300,
                android.content.Intent(context, com.enwei.moneymanager.ui.MainActivity::class.java)
                    .putExtra(com.enwei.moneymanager.ui.MainActivity.EXTRA_ROUTE, "acct/${w.from.id}"),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                9300,
                androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                    .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
                    .setContentTitle(com.enwei.moneymanager.ui.tr("「${w.from.name}」餘額不足，卡費可能扣款失敗"))
                    .setContentText(
                        "${w.date.monthValue}/${w.date.dayOfMonth} " + com.enwei.moneymanager.ui.tr("自動扣繳") + " ${com.enwei.moneymanager.core.Money.withSymbol(w.needed, w.from.currency)}，" +
                            com.enwei.moneymanager.ui.tr("還差") + " ${com.enwei.moneymanager.core.Money.withSymbol(w.shortBy, w.from.currency)}",
                    )
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }

        /** 3 天內要收付的借貸款項、14 天內到期或已逾期的借貸（同樣的內容只通知一次）。 */
        suspend fun warnLoans(context: Context, repo: Repository) {
            val list = repo.loanReminders(days = 3)
            val key = list.joinToString(";")
            if (key == repo.settings.value.loanWarnKey) return
            repo.settings.update { it.copy(loanWarnKey = key) }
            val nm = context.getSystemService(android.app.NotificationManager::class.java)
            if (list.isEmpty() || !nm.areNotificationsEnabled()) return
            val open = android.app.PendingIntent.getActivity(
                context, 9310,
                android.content.Intent(context, com.enwei.moneymanager.ui.MainActivity::class.java)
                    .putExtra(com.enwei.moneymanager.ui.MainActivity.EXTRA_ROUTE, "accounts"),
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
            )
            nm.notify(
                9310,
                androidx.core.app.NotificationCompat.Builder(context, MoneyApp.CHANNEL_RECORDS)
                    .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
                    .setContentTitle(com.enwei.moneymanager.ui.tr("借貸提醒"))
                    .setContentText(list.first())
                    .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(list.joinToString("\n")))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build(),
            )
        }

        suspend fun refreshMarket(repo: Repository) {
            val fx = runCatching { refreshFx(repo) }
            val q = runCatching { refreshQuotes(repo) }
            runCatching { refreshDividends(repo) }
            runCatching { refreshHolidays(repo) }
            fx.getOrThrow(); q.getOrThrow()
        }
    }
}
