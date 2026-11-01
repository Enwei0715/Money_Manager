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
        // 通知分成四類，使用者可以在系統設定分別開關
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_RECORDS, "自動記帳", NotificationManager.IMPORTANCE_LOW).apply { description = "從通知自動建立交易時提示" },
                NotificationChannel(CHANNEL_ALERTS, "提醒", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "股價、卡費、借貸提醒，報告完成或失敗，助理回覆" },
                NotificationChannel(CHANNEL_PROGRESS, "AI 進度", NotificationManager.IMPORTANCE_LOW).apply { description = "新聞更新與報告產生的進度" },
                NotificationChannel(CHANNEL_UPDATES, "App 更新", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "有新版本時通知" },
            ),
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
        com.enwei.moneymanager.work.ReminderWorker.cancelLegacy(this)
        com.enwei.moneymanager.notify.QuickAdd.cancelLegacy(this)
    }

    companion object {
        const val CHANNEL_RECORDS = "records"
        const val CHANNEL_ALERTS = "alerts"
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_UPDATES = "updates"
        lateinit var instance: MoneyApp
            private set
        val repo get() = instance.repo
        val news get() = instance.news
    }
}
