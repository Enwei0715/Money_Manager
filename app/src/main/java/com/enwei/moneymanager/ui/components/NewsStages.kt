package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 新聞排程的階段（狀態列與新聞頁共用）：
 * 蒐集 → thread 標註（每次 20 則）→ 合併題材 → block 整理（依題材）→ grid 大局 → 產生報告。
 */
object NewsStages {
    val short = listOf("蒐集", "標註", "合併", "整理", "大局", "報告")
    val full = listOf("蒐集新聞", "thread 標註", "合併題材", "block 整理", "grid 市場大局", "產生報告")

    fun of(step: String): Int = when {
        step.contains("thread") || step.contains("標註") -> 1
        step.contains("合併題材") -> 2
        step.contains("block") || step.contains("重點") -> 3
        step.contains("grid") || step.contains("大局") -> 4
        step.contains("分析資料集") || step.contains("營收") || step.contains("大盤") || step.contains("代號") || step.contains("報告") -> 5
        else -> 0
    }
}

/** 分段進度條：每個階段一格（完成、進行中、未開始），下方一行階段名稱，不會被截斷。 */
@Composable
fun StageBar(step: String, withReport: Boolean, modifier: Modifier = Modifier) {
    val n = if (withReport) 6 else 5
    val cur = NewsStages.of(step).coerceAtMost(n - 1)
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(n) { i ->
                Box(Modifier.weight(1f).height(4.dp).background(
                    when { i < cur -> on; i == cur -> on.copy(alpha = 0.55f); else -> off }, RoundedCornerShape(2.dp)))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(n) { i ->
                Text(NewsStages.short[i], fontSize = 11.sp, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                    fontWeight = if (i == cur) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (i <= cur) on else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
