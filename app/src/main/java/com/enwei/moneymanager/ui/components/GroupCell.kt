package com.enwei.moneymanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.enwei.moneymanager.ui.AppearancePrefs

/**
 * 分組清單的一格：同一組的列合成一張圓角卡片（第一列上圓角、最後一列下圓角），
 * 左右留白、組與組之間露出底色，取代整條色帶的分隔。
 */
@Composable
fun Modifier.groupCell(first: Boolean, last: Boolean): Modifier {
    val r = AppearancePrefs.cardRadius
    val shape = RoundedCornerShape(
        topStart = if (first) r else 0.dp, topEnd = if (first) r else 0.dp,
        bottomStart = if (last) r else 0.dp, bottomEnd = if (last) r else 0.dp,
    )
    return this.padding(horizontal = 12.dp).clip(shape).background(MaterialTheme.colorScheme.surface, shape)
}
