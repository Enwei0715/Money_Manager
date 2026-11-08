package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.ui.AppearancePrefs

/**
 * 分組清單的一格：同一組的列合成一張圓角卡片（第一列上圓角、最後一列下圓角），
 * 左右留白、組與組之間露出底色，取代整條色帶的分隔。
 */
@Composable
fun Modifier.groupCell(first: Boolean, last: Boolean): Modifier {
    // 線條樣式：整排、不圓角（分隔線由各列自己畫）
    if (!AppearancePrefs.cards) return this.background(MaterialTheme.colorScheme.surface)
    val r = AppearancePrefs.cardRadius
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    return this.padding(horizontal = 12.dp).clip(shape).background(MaterialTheme.colorScheme.surface, shape)
}

/** 一個區塊：線條樣式＝表面色＋上下細線；卡片樣式＝圓角底色。 */
@Composable
fun Modifier.blockSurface(color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface): Modifier {
    if (AppearancePrefs.cards) {
        val shape = RoundedCornerShape(AppearancePrefs.cardRadius)
        return this.clip(shape).background(color, shape)
    }
    return this.bleedBlock(MaterialTheme.colorScheme.surface)
}

/** 清單裡的一列：線條樣式＝整排＋底線、列與列相接；卡片樣式＝左右留白的圓角小卡。 */
@Composable
fun Modifier.listRow(color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLow): Modifier {
    if (AppearancePrefs.cards) {
        val shape = RoundedCornerShape(AppearancePrefs.cardRadius)
        return this.padding(horizontal = 12.dp, vertical = 3.dp).clip(shape).background(color, shape)
    }
    return this.bleedBlock(MaterialTheme.colorScheme.surface, top = false, lineInset = 16.dp)
}
