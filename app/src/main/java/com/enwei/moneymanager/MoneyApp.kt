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
    /**
     * App 層級的背景工作。任何一個工作出錯只記錄下來，不讓整個 App 閃退
     * （沒有這個處理器時，背景 coroutine 的例外會直接讓 App 結束）。
     */
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            kotlinx.coroutines.CoroutineExceptionHandler { _, e -> android.util.Log.e("MoneyApp", "Background task failed", e) },
    )

    override fun onCreate() {
        super.onCreate()
        instance = this
        // 閃退時把原因存起來，下次開 App 顯示（可以複製給開發者），再交給系統原本的處理
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                java.io.File(filesDir, CRASH_FILE).writeText(
                    "版本 ${packageManager.getPackageInfo(packageName, 0).versionName} · Android ${android.os.Build.VERSION.RELEASE} (SDK ${android.os.Build.VERSION.SDK_INT}) · " +
                        java.text.SimpleDateFormat("yyyy/M/d HH:mm:ss", java.util.Locale.TAIWAN).format(java.util.Date()) +
                        " · 執行緒 ${t.name}\n\n" + android.util.Log.getStackTraceString(e).take(6000),
                )
            }
            previous?.uncaughtException(t, e)
        }
        repo = Repository(AppDatabase.build(this), SettingsStore(this))
        // 桌面小工具、背景工作在沒開 App 時也用正確的基準幣別決定小數位
        com.enwei.moneymanager.core.Money.displayBase = repo.settings.value.baseCurrency
        news = com.enwei.moneymanager.data.NewsDataset(this)
        com.enwei.moneymanager.ui.I18n.init(this, repo.settings.value.language)
        com.enwei.moneymanager.core.TaiwanHolidays.update(com.enwei.moneymanager.data.HolidayCache.decode(repo.settings.value.holidayCache))
        com.enwei.moneymanager.core.TaiwanHolidays.setExtra(com.enwei.moneymanager.data.TyphoonDays.decode(repo.settings.value.typhoonDays))
        com.enwei.moneymanager.ui.applyAppLocale(this, repo.settings.value.language)
        // 通知分成記帳、投資、系統三大類，七種各自一個頻道
        com.enwei.moneymanager.notify.AppNotices.createChannels(this)
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
        // 恢復網路時：重新判讀沒網路時收到、規則看不懂的通知
        runCatching {
            getSystemService(android.net.ConnectivityManager::class.java).registerDefaultNetworkCallback(object : android.net.ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    appScope.launch { kotlinx.coroutines.delay(3000); repo.retryOfflineNotifications() }
                }
            })
        }
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
        const val CRASH_FILE = "last_crash.txt"
        const val CHANNEL_RECORDS = "records"
        const val CHANNEL_ALERTS = "reports"
        const val CHANNEL_PROGRESS = "progress"
        const val CHANNEL_UPDATES = "updates"
        lateinit var instance: MoneyApp
            private set
        val repo get() = instance.repo
        val news get() = instance.news
    }
}
