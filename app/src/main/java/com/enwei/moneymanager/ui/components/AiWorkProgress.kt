package com.enwei.moneymanager.ui.components

import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enwei.moneymanager.work.AiBackgroundWork
import com.enwei.moneymanager.work.AiWorkStatus
import com.enwei.moneymanager.work.ReportJob
import kotlinx.coroutines.delay

@Composable
fun rememberAiWorkStatus(uniqueName: String): AiWorkStatus? {
    val context = LocalContext.current
    val flow = remember(uniqueName) { AiBackgroundWork.observe(context, uniqueName) }
    val status by flow.collectAsStateWithLifecycle(initialValue = null)
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
        Text("等待網路或背景排程。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
    }
}
