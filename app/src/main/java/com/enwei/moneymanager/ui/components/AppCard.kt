package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.ui.AppearancePrefs

/**
 * 全 App 的區塊容器。
 * 「線條」樣式（預設）：不畫卡片，只用表面色＋上下兩條細線分隔，資訊清楚、不花俏；
 * 「卡片」樣式：圓角卡片（外觀設定可切換）。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (AppearancePrefs.cards) {
        Card(modifier, colors = colors, content = content)
        return
    }
    val line = MaterialTheme.colorScheme.outlineVariant
    // 有指定特別底色（例如提醒用的紅、藍底）時保留，沒有就用表面色
    val bg = colors.containerColor.takeIf { it != CardDefaults.cardColors().containerColor } ?: MaterialTheme.colorScheme.surface
    Column(
        modifier.background(bg).drawBehind {
            val w = 0.6.dp.toPx()
            drawLine(line, Offset(0f, 0f), Offset(size.width, 0f), w)
            drawLine(line, Offset(0f, size.height - w), Offset(size.width, size.height - w), w)
        },
        content = content,
    )
}

/** 給沒有 Composable 環境的地方用：目前是否為卡片樣式。 */
val isCardStyle: Boolean get() = AppearancePrefs.cards

@Suppress("unused")
private val unusedColor = Color.Unspecified
