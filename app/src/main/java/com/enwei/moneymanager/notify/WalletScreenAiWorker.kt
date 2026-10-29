package com.enwei.moneymanager.notify

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.core.ScreenRecord
import com.enwei.moneymanager.core.ScreenRecordParser
import com.enwei.moneymanager.net.GroqAi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

object WalletScreenAiWorker {
    suspend fun enqueue(
        context: Context,
        packageName: String,
        scanTime: Long,
        texts: List<String>,
        dryRun: Boolean,
    ) {
        val fileName = "wallet-ai-$scanTime.txt"
        val file = File(context.cacheDir, fileName)
        withContext(Dispatchers.IO) { file.writeText(texts.joinToString("\n")) }
        try {
            val request = OneTimeWorkRequestBuilder<WalletAiWorker>()
                .setInputData(
                    workDataOf(
                        "package" to packageName,
                        "scanTime" to scanTime,
                        "lineCount" to texts.size,
                        "dryRun" to dryRun,
                        "fileName" to fileName,
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("wallet-ai-$scanTime", ExistingWorkPolicy.KEEP, request)
        } catch (e: Exception) {
            file.delete()
            throw e
        }
    }
}

class WalletAiWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val scanTime = inputData.getLong("scanTime", 0)
        val packageName = inputData.getString("package").orEmpty()
        val lineCount = inputData.getInt("lineCount", 0)
        val dryRun = inputData.getBoolean("dryRun", false)
        val fileName = inputData.getString("fileName").orEmpty()
        val file = File(applicationContext.cacheDir, fileName)
        val notificationId = 850_000 + (scanTime % 100_000).toInt()
        val startedAt = System.currentTimeMillis()
        if (scanTime <= 0 || packageName.isBlank() || !fileName.matches(Regex("wallet-ai-\\d+\\.txt"))) {
            return Result.failure(workDataOf("error" to "錢包 AI 工作資料無效。"))
        }

        return try {
            setProgress(AiBackgroundWork.progress("讀取錢包交易文字…", startedAt))
            val texts = withContext(Dispatchers.IO) {
                if (!file.isFile) throw IllegalStateException("暫存畫面資料已不存在，請重新開啟錢包交易紀錄。")
                file.readLines()
            }
            val records = GroqAi.parseScreen(texts, LocalDate.now())
            if (records.isEmpty()) throw IllegalStateException(GroqAi.lastError ?: "AI 沒有辨識出交易紀錄。")
            setProgress(AiBackgroundWork.progress("匯入辨識出的交易…", startedAt, 0, records.size))
            val result = MoneyApp.repo.ingestScreenRecords(packageName, records, dryRun = dryRun)
            val message = if (result.wallet == null) {
                "辨識出 ${result.total} 筆，但找不到對應的電子支付帳戶。"
            } else {
                "AI 辨識 ${result.total} 筆；新增 ${result.created} 筆、已存在 ${result.existing} 筆。"
            }
            updateScan(scanTime, packageName, lineCount, records, result, status = "AI 判讀完成")
            if (!dryRun && result.created > 0) WalletScreenReader.notifyWalletImported(applicationContext, result.created)
            postCompletion(notificationId, message)
            withContext(Dispatchers.IO) { file.delete() }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = "AI 錢包判讀失敗：${e.message ?: e.javaClass.simpleName}"
            updateScan(scanTime, packageName, lineCount, emptyList(), null, problem = message)
            postCompletion(notificationId, message)
            withContext(Dispatchers.IO) { file.delete() }
            Result.failure(workDataOf("error" to message))
        }
    }

    private fun updateScan(
        time: Long,
        packageName: String,
        lineCount: Int,
        records: List<ScreenRecord>,
        result: com.enwei.moneymanager.data.Repository.ScreenIngestResult?,
        problem: String? = null,
        status: String? = null,
    ) {
        if (WalletScreenReader.scans.value.any { it.time == time }) {
            WalletScreenReader.updateScan(time) {
                it.copy(records = records, result = result, problem = problem, byAi = true, status = status)
            }
        } else {
            WalletScreenReader.record(
                WalletScreenReader.ScanEntry(time, packageName, lineCount, records, result, problem, byAi = true, status = status),
            )
        }
    }

    private fun postCompletion(id: Int, message: String) {
        val manager = applicationContext.getSystemService(android.app.NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        manager.notify(
            id + 100_000,
            androidx.core.app.NotificationCompat.Builder(applicationContext, MoneyApp.CHANNEL_RECORDS)
                .setSmallIcon(com.enwei.moneymanager.R.drawable.ic_stat_ledger)
                .setContentTitle("錢包 AI 判讀結果")
                .setContentText(message)
                .setAutoCancel(true)
                .setContentIntent(
                    android.app.PendingIntent.getActivity(
                        applicationContext,
                        id,
                        Intent(applicationContext, com.enwei.moneymanager.ui.MainActivity::class.java),
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
                .build(),
        )
    }
}
