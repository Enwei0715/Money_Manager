package com.enwei.moneymanager.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val IncomeColor = Color(0xFF2E9E6A)
val ExpenseColor = Color(0xFFD9534F)

private val Light = lightColorScheme(
    primary = Color(0xFF1B7F5C),
    secondary = Color(0xFF4F6358),
    tertiary = Color(0xFF3D6373),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF7FD8B1),
    secondary = Color(0xFFB6CCBF),
    tertiary = Color(0xFFA5CCDF),
)

@Composable
fun MoneyTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
