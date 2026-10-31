package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enwei.moneymanager.work.AiBackgroundWork
import com.enwei.moneymanager.work.AiTaskProgress
import com.enwei.moneymanager.work.AiWorkStatus
import com.enwei.moneymanager.work.ReportJob
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** 觀察 WorkManager 背景排程任務狀態（專用於 NewsWorker / ReportWorker 重型背景排程）。 */
@Composable
fun rememberAiWorkStatus(uniqueName: String): AiWorkStatus? {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val infos = remember(context, uniqueName) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(uniqueName)
    }
    var status by remember(uniqueName) { mutableStateOf<AiWorkStatus?>(null) }
    DisposableEffect(infos, lifecycleOwner, uniqueName) {
        val observer = Observer<List<WorkInfo>> { status = AiBackgroundWork.status(it, uniqueName) }
        infos.observe(lifecycleOwner, observer)
        onDispose { infos.removeObserver(observer) }
    }
    return status
}

/** 顯示 WorkManager 背景排程的進度（NewsWorker / ReportWorker）。 */
@Composable
fun AiWorkProgress(status: AiWorkStatus?, modifier: Modifier = Modifier) {
    if (status == null) return
    if (!status.running) {
        status.error?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error, modifier = modifier) }
        return
    }

    var now by remember(status.startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(status.running, status.startedAt) {
        while (status.running && status.startedAt > 0) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Text(status.step, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, modifier = modifier)
    if (status.total != null && status.done != null && status.total > 0) {
        LinearProgressIndicator(
            progress = { (status.done.toFloat() / status.total).coerceIn(0f, 1f) },
            modifier = modifier,
        )
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
    if (status.startedAt > 0) {
        Text(
            "已執行 ${ReportJob.formatDuration(now - status.startedAt)}；離開畫面或 App 後仍會繼續。",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    } else {
        val waitingText = if (status.step == "等待其他 AI 工作完成…") {
            "前面還有 AI 工作；完成後會自動接續。"
        } else {
            "等待網路或背景排程。"
        }
        Text(waitingText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
}

/** 顯示輕量 AI 任務的即時進度（觀察 StateFlow）。 */
@Composable
fun AiTaskProgressIndicator(
    progressFlow: StateFlow<AiTaskProgress>,
    modifier: Modifier = Modifier,
) {
    val progress by progressFlow.collectAsStateWithLifecycle()
    AiWorkProgress(progress, modifier)
}

/** 顯示輕量 AI 任務的即時進度（直接傳入 AiTaskProgress）。 */
@Composable
fun AiWorkProgress(
    progress: AiTaskProgress,
    modifier: Modifier = Modifier,
) {
    if (!progress.running && progress.step.isBlank() && progress.error == null) return

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        if (progress.error != null) {
            Text(progress.error, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            return@Column
        }
        if (progress.step.isNotBlank()) {
            Text(
                progress.step,
                fontSize = 13.sp,
                color = if (progress.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (progress.running) {
            if (progress.total != null && progress.done != null && progress.total > 0) {
                LinearProgressIndicator(
                    progress = { (progress.done.toFloat() / progress.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * 集中顯示所有正在背景執行的 AI 排程（新聞整理、投資報告）與輕量任務進度。
 * 放置於「AI 中心」或「設定」畫面上方，讓使用者打開 APP 就能清楚掌握背景進度。
 */
@Composable
fun AiAllTasksProgress(modifier: Modifier = Modifier) {
    val newsStatus = rememberAiWorkStatus("news")
    val reportStatus = rememberAiWorkStatus("report-now")
    val reviewPrg by AiBackgroundWork.reviewProgress.collectAsStateWithLifecycle()
    val unknownPrg by AiBackgroundWork.unknownProgress.collectAsStateWithLifecycle()
    val dupPrg by AiBackgroundWork.duplicatesProgress.collectAsStateWithLifecycle()
    val tidyPrg by AiBackgroundWork.autoTidyProgress.collectAsStateWithLifecycle()

    val hasAny = (newsStatus?.running == true) || (reportStatus?.running == true) ||
            reviewPrg.running || unknownPrg.running || dupPrg.running || tidyPrg.running ||
            newsStatus?.error != null || reportStatus?.error != null

    if (!hasAny) return

    Column(modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        // 重型背景工作進度
        if (newsStatus?.running == true || newsStatus?.error != null) {
            AiWorkProgress(newsStatus, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
        if (reportStatus?.running == true || reportStatus?.error != null) {
            AiWorkProgress(reportStatus, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }

        // 輕量即時任務進度
        if (reviewPrg.running || reviewPrg.error != null) {
            AiWorkProgress(reviewPrg, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
        if (unknownPrg.running || unknownPrg.error != null) {
            AiWorkProgress(unknownPrg, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
        if (dupPrg.running || dupPrg.error != null) {
            AiWorkProgress(dupPrg, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
        if (tidyPrg.running || tidyPrg.error != null) {
            AiWorkProgress(tidyPrg, Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
    }
}
