package com.enwei.moneymanager.work

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enwei.moneymanager.BuildConfig
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.data.FullBackupIO
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.sync.withLock

/**
 * 自動雲端備份：把完整備份（.lgbak）覆寫到使用者選定的檔案位置。
 * 位置可以是 Google 雲端硬碟、OneDrive、Dropbox 或手機資料夾（由系統檔案選擇器提供），不需要任何開發者金鑰。
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val force = inputData.getBoolean("force", false)
        val s = MoneyApp.repo.settings.value
        if (s.autoBackupUri.isBlank()) return Result.success()
        val due = s.autoBackupDays > 0 && System.currentTimeMillis() - s.lastAutoBackup >= s.autoBackupDays * 24L * 3600 * 1000 - 3600_000
        if (!force && !due) return Result.success()
        // 先寫到本機暫存檔並讀回驗證，完整無誤才覆蓋雲端那份：
        // 直接以 "wt" 開啟會先清空舊備份，匯出途中失敗就只剩一個壞檔。
        // 每次用不同的暫存檔，並且同一時間只跑一個備份（定期備份和「立即備份」不會互相蓋掉）
        val r = lock.withLock {
            val tmp = java.io.File.createTempFile("auto-backup", ".tmp", applicationContext.cacheDir)
            try {
                runCatching {
                    tmp.outputStream().use { FullBackupIO.export(MoneyApp.repo, BuildConfig.VERSION_NAME, it) }
                    tmp.inputStream().use { FullBackupIO.read(it) }
                    applicationContext.contentResolver.openOutputStream(Uri.parse(s.autoBackupUri), "wt")!!.use { out ->
                        tmp.inputStream().use { it.copyTo(out) }
                    }
                }
            } finally { tmp.delete() }
        }
        r.exceptionOrNull()?.let { if (it is kotlinx.coroutines.CancellationException) throw it }
        // 資料夾權限失效、檔案被刪：重試也不會成功，直接通知使用者重新選擇
        val permanent = r.exceptionOrNull().let { it is SecurityException || it is java.io.FileNotFoundException || it is NullPointerException }
        MoneyApp.repo.settings.update {
            it.copy(
                lastAutoBackup = if (r.isSuccess) System.currentTimeMillis() else it.lastAutoBackup,
                lastAutoBackupError = r.exceptionOrNull()?.let { e -> com.enwei.moneymanager.ui.friendlyError(e) }.orEmpty(),
            )
        }
        if (r.isFailure && (permanent || runAttemptCount >= 2)) {
            com.enwei.moneymanager.notify.AppNotices.post(applicationContext, com.enwei.moneymanager.notify.NoticeKind.BACKUP, 9340,
                com.enwei.moneymanager.ui.tr("自動備份失敗"),
                if (permanent) com.enwei.moneymanager.ui.tr("備份資料夾無法存取，請重新選擇") else com.enwei.moneymanager.ui.friendlyError(r.exceptionOrNull()!!),
                "backup")
        }
        return when {
            r.isSuccess -> Result.success()
            permanent -> Result.failure()
            else -> Result.retry()
        }
    }

    companion object {
        private val lock = kotlinx.coroutines.sync.Mutex()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<BackupWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("auto-backup", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun runNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<BackupWorker>().setInputData(androidx.work.workDataOf("force" to true)).build()
            WorkManager.getInstance(context).enqueueUniqueWork("auto-backup-now", ExistingWorkPolicy.REPLACE, req)
        }
    }
}
