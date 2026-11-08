package com.enwei.moneymanager.ui.components

import com.enwei.moneymanager.ui.AppText

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 頁面內的分段切換（例如檢討的「總覽／結構／習慣／AI」）：把很長的頁面切成幾段，
 * 每段一個畫面就看得完，不必一直往下滑找資訊。
 */
@Composable
fun SubTabs(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) = com.enwei.moneymanager.ui.ChromeScale {
    val outer = RoundedCornerShape(com.enwei.moneymanager.ui.AppearancePrefs.cardRadius)
    val inner = RoundedCornerShape((com.enwei.moneymanager.ui.AppearancePrefs.cardRadius - 3.dp).coerceAtLeast(0.dp))
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, outer).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f)
                    .background(if (sel) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant, inner)
                    .clickable { onSelect(i) }.padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = AppText.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
