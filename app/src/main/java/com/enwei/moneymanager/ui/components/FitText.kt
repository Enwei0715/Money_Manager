package com.enwei.moneymanager.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * 單行文字，放不下時自動縮小字級（最小 [minSize]），不會被截掉或換行。
 * 用在分頁標籤、按鈕這類寬度固定的地方（介面調大時也不會擠掉文字）。
 */
@Composable
fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    @Suppress("UNUSED_PARAMETER") maxLines: Int = 1,
    @Suppress("UNUSED_PARAMETER") softWrap: Boolean = false,
    minSize: TextUnit = 9.sp,
    /** 同一排（例如一列分頁）共用的字級：只要有一個放不下，整排一起縮小，大小一致 */
    group: androidx.compose.runtime.MutableState<TextUnit>? = null,
) {
    val local = remember(text, fontSize) { mutableStateOf(fontSize) }
    val size = group ?: local
    var ready by remember(text, fontSize) { mutableStateOf(false) }
    Text(
        text, modifier.drawWithContent { if (ready) drawContent() }, color = color, fontSize = size.value, fontWeight = fontWeight,
        maxLines = 1, softWrap = false,
        onTextLayout = { r ->
            if (r.didOverflowWidth && size.value.value > minSize.value) size.value = (size.value.value - 1).sp else ready = true
        },
    )
}

/** 一排標籤共用的字級狀態。 */
@Composable
fun rememberFitGroup(size: TextUnit = 14.sp) = remember { mutableStateOf(size) }
