package com.enwei.moneymanager.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters

/** Legacy worker retained so scheduled work from older versions can finish safely. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        private const val LEGACY_NOTIFICATION_ID = 9001

        fun cancelLegacy(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("daily-reminder")
            NotificationManagerCompat.from(context).cancel(LEGACY_NOTIFICATION_ID)
        }
    }
}
