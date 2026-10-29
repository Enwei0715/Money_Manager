package com.enwei.moneymanager

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.enwei.moneymanager.data.AppDatabase
import com.enwei.moneymanager.data.Repository
import com.enwei.moneymanager.data.SettingsStore
import com.enwei.moneymanager.work.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class MoneyApp : Application() {
    lateinit var repo: Repository
        private set
    /** 投資助理的新聞資料集 */
    lateinit var news: com.enwei.moneymanager.data.NewsDataset
        private set
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(AppDatabase.build(this), SettingsStore(this))
        news = com.enwei.moneymanager.data.NewsDataset(this)
        com.enwei.moneymanager.ui.I18n.init(this, repo.settings.value.language)
        com.enwei.moneymanager.core.TaiwanHolidays.update(com.enwei.moneymanager.data.HolidayCache.decode(repo.settings.value.holidayCache))
        com.enwei.moneymanager.core.TaiwanHolidays.setExtra(com.enwei.moneymanager.data.TyphoonDays.decode(repo.settings.value.typhoonDays))
        com.enwei.moneymanager.ui.applyAppLocale(this, repo.settings.value.language)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDS, "自動記帳", NotificationManager.IMPORTANCE_LOW).apply {
                description = "從通知自動建立交易時提示"
            },
        )
        appScope.launch {
            repo.ensureSeedData()
            repo.runRecurring()
            repo.runFeeRebates()
        }
        SyncWorker.schedule(this)
        SyncWorker.scheduleTyphoon(this)
        // 打開 App 時也檢查一次颱風停班公告
        appScope.launch { runCatching { SyncWorker.refreshTyphoon(repo) } }
        com.enwei.moneymanager.work.NewsWorker.schedule(this)
        com.enwei.moneymanager.work.StockAlertWorker.schedule(this)
        com.enwei.moneymanager.work.BackupWorker.schedule(this)
        // 資料有變動就更新桌面小工具
        appScope.launch {
            repo.transactions.debounce(1500).collect { com.enwei.moneymanager.widget.Widgets.refreshAll(this@MoneyApp) }
        }
        com.enwei.moneymanager.work.ReminderWorker.schedule(this)
        com.enwei.moneymanager.notify.QuickAdd.sync(this, repo.settings.value.quickAdd)
    }

    companion object {
        const val CHANNEL_RECORDS = "records"
        lateinit var instance: MoneyApp
            private set
        val repo get() = instance.repo
        val news get() = instance.news
    }
}
