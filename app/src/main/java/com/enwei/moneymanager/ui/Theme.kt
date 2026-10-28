package com.enwei.moneymanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF3C82E6)
private val Coral = Color(0xFFF0605A)

/** 收支顏色設定（設定 A：收入藍支出紅；設定 B 相反），改變時所有畫面自動重繪。 */
object ColorPrefs {
    var setB by mutableStateOf(false)
}

val IncomeColor: Color get() = if (ColorPrefs.setB) Coral else Blue
val ExpenseColor: Color get() = if (ColorPrefs.setB) Blue else Coral
val BarBackground = Color(0xFFF5F5F7)
val TransferColor = Color(0xFF8C8C8C)
val SaturdayColor = Color(0xFF3C82E6)
val SundayColor = Color(0xFFF0605A)

// 台股慣例：漲紅、跌綠
val StockUp = Color(0xFFE53935)
val StockDown = Color(0xFF2E9E57)

private val Light = lightColorScheme(
    primary = Color(0xFFF0605A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE3E1),
    onPrimaryContainer = Color(0xFF5A1310),
    secondary = Color(0xFF3C82E6),
    secondaryContainer = Color(0xFFE3EEFD),
    tertiary = Color(0xFF6D6D6D),
    tertiaryContainer = Color(0xFFFFF2D6),
    background = Color(0xFFF7F7F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF1F1F1),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF4F4F4),
    onSurfaceVariant = Color(0xFF8A8A8A),
    outlineVariant = Color(0xFFE6E6E6),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A73),
    onPrimary = Color(0xFF3B0907),
    primaryContainer = Color(0xFF5A1D1A),
    secondary = Color(0xFF7FB0F5),
    secondaryContainer = Color(0xFF1E3350),
    tertiaryContainer = Color(0xFF4A3B1A),
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF262626),
    surfaceContainer = Color(0xFF1C1C1C),
    surfaceContainerLow = Color(0xFF1C1C1C),
    surfaceContainerHigh = Color(0xFF242424),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFF333333),
)

@Composable
fun MoneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
