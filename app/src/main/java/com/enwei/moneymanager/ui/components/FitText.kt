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
) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }
    var ready by remember(text, fontSize) { mutableStateOf(false) }
    Text(
        text, modifier.drawWithContent { if (ready) drawContent() }, color = color, fontSize = size, fontWeight = fontWeight,
        maxLines = 1, softWrap = false,
        onTextLayout = { r ->
            if (r.didOverflowWidth && size.value > minSize.value) size = (size.value - 1).sp else ready = true
        },
    )
}
