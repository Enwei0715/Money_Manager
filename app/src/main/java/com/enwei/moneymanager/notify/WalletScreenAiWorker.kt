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
import com.enwei.moneymanager.work.AiBackgroundWork
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
        if (scanTime <= 0 || packageName.isBlank() || !fileName.matches(Regex("wallet-ai-\\d+\\.txt"))) {
            return Result.failure(workDataOf("error" to "錢包 AI 工作資料無效。"))
        }

        setProgress(AiBackgroundWork.progress("等待其他 AI 工作完成…", 0))
        return AiBackgroundWork.withAiExecution {
            val startedAt = System.currentTimeMillis()
            try {
                setProgress(AiBackgroundWork.progress("讀取錢包交易文字…", startedAt))
                val texts = withContext(Dispatchers.IO) {
                    if (!file.isFile) throw IllegalStateException("暫存畫面資料已不存在，請重新開啟錢包交易紀錄。")
                    file.readLines()
                }
                val records = GroqAi.parseScreen(texts, LocalDate.now())
                if (records.isEmpty()) throw IllegalStateException(GroqAi.lastError ?: "AI 沒有辨識出交易紀錄。")
                setProgress(AiBackgroundWork.progress("匯入辨識出的交易…", startedAt, 0, records.size))
                val result = MoneyApp.repo.ingestScreenRecords(packageName, records, dryRun = dryRun)
                // 結果顯示在「錢包畫面讀取」頁；新增的交易由下一行發記帳通知，不另外發「判讀結果」通知
                updateScan(scanTime, packageName, lineCount, records, result, status = "AI 判讀完成")
                if (!dryRun && result.created > 0) WalletScreenReader.notifyWalletImported(applicationContext, result.created)
                withContext(Dispatchers.IO) { file.delete() }
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = "AI 錢包判讀失敗：${e.message ?: e.javaClass.simpleName}"
                updateScan(scanTime, packageName, lineCount, emptyList(), null, problem = message)
                withContext(Dispatchers.IO) { file.delete() }
                Result.failure(workDataOf("error" to message))
            }
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
}
