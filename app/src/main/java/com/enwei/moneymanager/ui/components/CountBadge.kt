package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 有數字角標的圖示按鈕（全 App 唯一的寫法）。
 *
 * 角標一律畫在 48dp 按鈕的「內側」右上角：不用 BadgedBox 往外推、不放進會裁成圓形的 IconButton、
 * 不用負的 offset，所以任何字級、兩位數都不會被切掉。數字超過 99 顯示「99+」；[dot] 為 true 時只畫小圓點。
 */
@Composable
fun BadgedIconButton(
    onClick: () -> Unit,
    count: Int,
    description: String,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    badgeColor: Color = MaterialTheme.colorScheme.error,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier.size(48.dp).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (count > 0) "$description（$count）" else description },
        contentAlignment = Alignment.Center,
    ) {
        icon()
        if (dot) Box(Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp).size(8.dp).background(badgeColor, CircleShape))
        else if (count > 0) Box(Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 2.dp)) {
            // 字級最多放大 1.15 倍：按鈕固定 48dp，角標不會因系統大字被撐出去
            com.enwei.moneymanager.ui.ChromeScale {
                Box(
                    Modifier.defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                        .background(badgeColor, RoundedCornerShape(9.dp)).padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (count > 99) "99+" else "$count", color = MaterialTheme.colorScheme.onError, fontSize = 11.sp, lineHeight = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/**
 * 浮動按鈕（＋記一筆、新增）：往下捲時收起、往上捲或停在頂端時出現，
 * 捲動清單時不會一直蓋住右側的金額。
 */
@Composable
fun ScrollAwareFab(content: @Composable () -> Unit) {
    androidx.compose.animation.AnimatedVisibility(
        visible = !(Chrome.bottomHidden || Chrome.topHidden),
        enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut(),
    ) { content() }
}
