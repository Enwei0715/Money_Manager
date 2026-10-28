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
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        repo = Repository(AppDatabase.build(this), SettingsStore(this))
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_RECORDS, "自動記帳", NotificationManager.IMPORTANCE_LOW).apply {
                description = "從通知自動建立交易時提示"
            },
        )
        appScope.launch {
            repo.ensureSeedData()
            repo.runRecurring()
        }
        SyncWorker.schedule(this)
        // 資料有變動就更新桌面小工具
        appScope.launch {
            repo.transactions.debounce(1500).collect { com.enwei.moneymanager.widget.SummaryWidget.refresh(this@MoneyApp) }
        }
        com.enwei.moneymanager.work.ReminderWorker.schedule(this)
        com.enwei.moneymanager.notify.QuickAdd.sync(this, repo.settings.value.quickAdd)
    }

    companion object {
        const val CHANNEL_RECORDS = "records"
        lateinit var instance: MoneyApp
            private set
        val repo get() = instance.repo
    }
}
