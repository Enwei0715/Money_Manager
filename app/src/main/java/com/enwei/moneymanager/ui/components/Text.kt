package com.enwei.moneymanager.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import com.enwei.moneymanager.ui.I18n
import androidx.compose.material3.Text as M3Text

/** 全 App 共用的文字元件：英文模式時自動翻譯介面文字。 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    /** 沒指定時：有行數上限就用「…」結尾（全 App 一致，不會直接截斷） */
    overflow: TextOverflow? = null,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) = M3Text(
    text = I18n.tr(text), modifier = modifier, color = color, fontSize = fontSize, fontStyle = fontStyle, fontWeight = fontWeight,
    fontFamily = fontFamily, letterSpacing = letterSpacing, textDecoration = textDecoration, textAlign = textAlign,
    lineHeight = fitLineHeight(fontSize, lineHeight), overflow = overflow ?: if (maxLines != Int.MAX_VALUE || !softWrap) TextOverflow.Ellipsis else TextOverflow.Clip, softWrap = softWrap, maxLines = maxLines, minLines = minLines,
    onTextLayout = onTextLayout, style = style,
)

/**
 * 行高跟著字級：只給字級時用 1.3 倍；給的行高比 1.2 倍字級還小時拉到 1.2 倍。
 * 避免沿用主題 20sp 的固定行高——大字（標題、200% 字級）上下被切或兩行疊在一起，小字則浪費空間。
 */
private fun fitLineHeight(fontSize: TextUnit, lineHeight: TextUnit): TextUnit {
    if (!fontSize.isSp) return lineHeight
    if (!lineHeight.isSp) return fontSize * 1.3f
    return if (lineHeight.value < fontSize.value * 1.2f) fontSize * 1.2f else lineHeight
}
