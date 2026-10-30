package com.enwei.moneymanager.ui.components

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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Observer
import com.enwei.moneymanager.work.AiBackgroundWork
import com.enwei.moneymanager.work.AiWorkStatus
import com.enwei.moneymanager.work.ReportJob
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.delay

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
    if (status.total != null && status.done != null) {
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
