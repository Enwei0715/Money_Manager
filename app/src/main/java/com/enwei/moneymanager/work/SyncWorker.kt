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
        }

        suspend fun refreshMarket(repo: Repository) {
            val fx = runCatching { refreshFx(repo) }
            val q = runCatching { refreshQuotes(repo) }
            fx.getOrThrow(); q.getOrThrow()
        }
    }
}
