package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.enwei.moneymanager.MoneyApp
import com.enwei.moneymanager.net.GroqAi
import com.enwei.moneymanager.work.AiBackgroundWork
import com.enwei.moneymanager.work.AiHealth
import com.enwei.moneymanager.work.ChatJob
import com.enwei.moneymanager.work.ReportJob
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 狀態列要顯示的一項。 */
private data class AiLine(
    val text: String,
    val route: String?,
    val running: Boolean,
    val progress: Float? = null,
    val warn: Boolean = false,
    val action: Pair<String, () -> Unit>? = null,
)

private fun hhmm(ms: Long) = DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

/**
 * 全 App 的 AI 狀態列（底部分頁列上方）：顯示正在進行的 AI 工作與進度、預估剩餘時間、
 * 新聞資料集的區塊完成度，以及限流或連續失敗暫停的原因。沒有事情時不佔空間。
 */
@Composable
fun AiStatusBar(nav: NavController, onOpen: (String) -> Unit = { nav.navigate(it) }) {
    val report by ReportJob.state.collectAsStateWithLifecycle()
    val chat by ChatJob.state.collectAsStateWithLifecycle()
    val review by AiBackgroundWork.reviewProgress.collectAsStateWithLifecycle()
    val unknown by AiBackgroundWork.unknownProgress.collectAsStateWithLifecycle()
    val dups by AiBackgroundWork.duplicatesProgress.collectAsStateWithLifecycle()
    val tidy by AiBackgroundWork.autoTidyProgress.collectAsStateWithLifecycle()
    val health by AiHealth.state.collectAsStateWithLifecycle()
    val grid by MoneyApp.news.grid.collectAsStateWithLifecycle()
    // 每秒更新剩餘時間與暫停倒數
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }

    val lines = buildList {
        report?.let { r ->
            if (r.running) {
                val count = if (r.done != null && r.total != null) " ${r.done}/${r.total}" else ""
                val eta = r.etaAt?.takeIf { it > now }?.let { " · 約剩 ${ReportJob.formatDuration(it - now)}" }.orEmpty()
                val blocks = if (grid.blocks > 0) " · 區塊 ${grid.complete}/${grid.blocks}" + (if (grid.unread > 0) "、待讀 ${grid.unread}" else "") else ""
                // 目前在排程的第幾步：蒐集 → 標註 → 合併題材 → 整理重點 → 產生報告
                val stepNo = when {
                    r.step.contains("標註") -> 2; r.step.contains("合併題材") -> 3; r.step.contains("重點") -> 4
                    r.step.contains("分析資料集") || r.step.contains("營收") || r.step.contains("大盤") || r.step.contains("代號") -> 5
                    else -> 1
                }
                val stepTotal = if (r.refreshOnly) 4 else 5
                add(AiLine("步驟 ${stepNo.coerceAtMost(stepTotal)}/$stepTotal · " + r.step.removeSuffix("…").substringBefore(" ${r.done}/") + count + eta + blocks,
                    if (r.refreshOnly) "advisor:2" else "advisor:0", true,
                    progress = if (r.done != null && r.total != null && r.total > 0) r.done.toFloat() / r.total else null))
            } else if (r.error != null && now - r.startedAt < 6 * 3600_000L) {
                add(AiLine(r.error, if (r.refreshOnly) "advisor:2" else "advisor:0", false, warn = true, action = "知道了" to { ReportJob.state.value = null }))
            }
        }
        chat?.takeIf { it.running }?.let {
            // 對話流程：讀取持股 → 搜尋新聞 → 讀取全文 → AI 回答 → 確認代號
            val n = when {
                it.step.contains("確認代號") -> 5; it.step.startsWith("AI") -> 4; it.step.contains("全文") -> 3; it.step.contains("搜尋") -> 2; else -> 1
            }
            add(AiLine("對話 步驟 $n/5 · " + it.step.ifBlank { "股票助理回覆中…" }, "advisor:1", true, progress = n / 5f))
        }
        if (review.running) add(AiLine(review.step.ifBlank { "收支檢討中…" }, "review", true, progress(review.done, review.total)))
        listOf(unknown, dups, tidy).filter { it.running }.forEach { add(AiLine(it.step.ifBlank { "AI 整理中…" }, "ai-hub", true, progress(it.done, it.total))) }
        if (health.paused(now)) {
            add(AiLine("${health.pauseReason ?: "背景 AI 已暫停"}（${hhmm(health.pausedUntil)} 恢復）", "ai", false, warn = true,
                action = "恢復" to { AiHealth.resume() }))
        } else if (GroqAi.enabled) {
            GroqAi.coolingUntil()?.takeIf { it > now }?.let {
                add(AiLine("AI 額度用完，${hhmm(it)} 恢復（約 ${ReportJob.formatDuration(it - now)}）", "ai", false, warn = true))
            }
        }
    }
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        lines.forEach { l -> Line(l, onOpen) }
    }
}

private fun progress(done: Int?, total: Int?) = if (done != null && total != null && total > 0) done.toFloat() / total else null

@Composable
private fun Line(l: AiLine, onOpen: (String) -> Unit) {
    val warn = MaterialTheme.colorScheme.error
    Column(Modifier.fillMaxWidth().clickable(enabled = l.route != null) { l.route?.let { onOpen(it) } }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (l.running) CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
            else Text("!", color = if (l.warn) warn else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                l.text, Modifier.weight(1f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (l.warn) warn else MaterialTheme.colorScheme.onSurface,
            )
            l.action?.let { (label, act) ->
                Text(label, color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = act).padding(start = 10.dp, top = 2.dp, bottom = 2.dp))
            }
        }
        if (l.progress != null) LinearProgressIndicator(
            progress = { l.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(2.dp),
            trackColor = Color.Transparent,
        )
    }
}
