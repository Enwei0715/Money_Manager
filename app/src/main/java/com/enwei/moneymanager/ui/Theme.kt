package com.enwei.moneymanager.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    surfaceContainerHighest = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6B6B6B),
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
    surfaceContainerHighest = Color(0xFF1C1C1C),
    surfaceContainerLowest = Color(0xFF1C1C1C),
    onSurfaceVariant = Color(0xFF9E9E9E),
    outlineVariant = Color(0xFF333333),
)

/**
 * 主題色組：每組有淺色與深色兩套。
 * 「墨」走建築作品集式的極簡：近黑主色、紙感米白底、細線分隔、低彩度。
 */
class ThemePreset(val key: String, val label: String, val swatch: Color, val light: androidx.compose.material3.ColorScheme, val dark: androidx.compose.material3.ColorScheme)

private fun lightOf(primary: Color, container: Color, onContainer: Color, bg: Color, variant: Color, line: Color, muted: Color, secondary: Color = Color(0xFF3C82E6)) = lightColorScheme(
    primary = primary, onPrimary = Color.White, primaryContainer = container, onPrimaryContainer = onContainer,
    secondary = secondary, secondaryContainer = container.copy(alpha = 0.7f).compositeOver(Color.White), tertiary = muted, tertiaryContainer = Color(0xFFFFF2D6),
    background = bg, surface = Color.White, surfaceVariant = variant, surfaceContainer = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerHigh = variant, surfaceContainerHighest = Color.White, surfaceContainerLowest = Color.White,
    // 次要文字調深兩成：小字對比要到 4.5:1 以上
    onSurfaceVariant = Color(muted.red * 0.8f, muted.green * 0.8f, muted.blue * 0.8f), outlineVariant = line, outline = line.copy(alpha = 1f).compositeOver(muted.copy(alpha = 0.4f)),
)
private fun darkOf(primary: Color, onPrimary: Color, container: Color, bg: Color, surface: Color, variant: Color, line: Color, muted: Color, secondary: Color = Color(0xFF7FB0F5)) = darkColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = container, secondary = secondary, secondaryContainer = container,
    tertiaryContainer = Color(0xFF4A3B1A), background = bg, surface = surface, surfaceVariant = variant, surfaceContainer = surface,
    surfaceContainerLow = surface, surfaceContainerHigh = variant, surfaceContainerHighest = surface, surfaceContainerLowest = surface,
    onSurfaceVariant = muted, outlineVariant = line,
)
private fun Color.compositeOver(bg: Color): Color = Color(
    red = red * alpha + bg.red * (1 - alpha), green = green * alpha + bg.green * (1 - alpha), blue = blue * alpha + bg.blue * (1 - alpha), alpha = 1f,
)

object Themes {
    val all = listOf(
        ThemePreset("coral", "珊瑚", Color(0xFFF0605A), Light, Dark),
        ThemePreset("mono", "墨", Color(0xFF1F1F1F),
            lightOf(Color(0xFF1F1F1F), Color(0xFFECE9E3), Color(0xFF1F1F1F), Color(0xFFF5F3EF), Color(0xFFEFECE6), Color(0xFFE2DED6), Color(0xFF7D786E), Color(0xFF5B5B5B)),
            darkOf(Color(0xFFEDEAE4), Color(0xFF141414), Color(0xFF2A2926), Color(0xFF0E0E0D), Color(0xFF171716), Color(0xFF222120), Color(0xFF2D2C29), Color(0xFF9B978E), Color(0xFFBDBAB3))),
        ThemePreset("sage", "苔綠", Color(0xFF4F7A5E),
            lightOf(Color(0xFF4F7A5E), Color(0xFFDCEBDF), Color(0xFF173822), Color(0xFFF3F5F1), Color(0xFFEBEFE8), Color(0xFFDDE3D9), Color(0xFF7A8578)),
            darkOf(Color(0xFF8DBF9E), Color(0xFF0E2416), Color(0xFF1F3527), Color(0xFF0F120F), Color(0xFF181C18), Color(0xFF222822), Color(0xFF2C332C), Color(0xFF97A196))),
        ThemePreset("navy", "深海", Color(0xFF2F5D8A),
            lightOf(Color(0xFF2F5D8A), Color(0xFFDDE7F2), Color(0xFF0E2A45), Color(0xFFF3F5F8), Color(0xFFEAEEF3), Color(0xFFDCE2EA), Color(0xFF76808C), Color(0xFF5E7FA3)),
            darkOf(Color(0xFF8FB4E0), Color(0xFF0B1E33), Color(0xFF1D2F45), Color(0xFF0D1015), Color(0xFF161B22), Color(0xFF1F252E), Color(0xFF2A313B), Color(0xFF939BA6))),
        ThemePreset("sand", "暖砂", Color(0xFFB07A4F),
            lightOf(Color(0xFFB07A4F), Color(0xFFF2E3D5), Color(0xFF4A2A12), Color(0xFFF7F3EE), Color(0xFFF0EAE2), Color(0xFFE5DCD1), Color(0xFF8A7E72)),
            darkOf(Color(0xFFE0B48D), Color(0xFF3A220F), Color(0xFF3B2B1E), Color(0xFF12100E), Color(0xFF1C1916), Color(0xFF26221E), Color(0xFF332E28), Color(0xFFA39A90))),
        ThemePreset("plum", "薰紫", Color(0xFF7A5C9E),
            lightOf(Color(0xFF7A5C9E), Color(0xFFEAE1F4), Color(0xFF2E1A45), Color(0xFFF5F3F8), Color(0xFFEEEAF3), Color(0xFFE0DAE8), Color(0xFF83798F)),
            darkOf(Color(0xFFC0A6E3), Color(0xFF26153A), Color(0xFF33264A), Color(0xFF110F14), Color(0xFF1A171E), Color(0xFF241F29), Color(0xFF2F2935), Color(0xFF9E96A8))),
    )
    fun of(key: String) = all.firstOrNull { it.key == key } ?: all.first()

    /** 字體：0 系統、1 黑體、2 明體（襯線） */
    /** 字體：系統字體不用下載；其他從 Google Fonts 下載真正的中文字形（下載前先用系統字體） */
    val fonts = listOf("系統字體", "黑體", "明體", "楷體")
    private val gfProvider by lazy {
        androidx.compose.ui.text.googlefonts.GoogleFont.Provider(
            providerAuthority = "com.google.android.gms.fonts",
            providerPackage = "com.google.android.gms",
            certificates = com.enwei.moneymanager.R.array.com_google_android_gms_fonts_certs,
        )
    }
    private fun gf(name: String, vararg weights: androidx.compose.ui.text.font.FontWeight): androidx.compose.ui.text.font.FontFamily {
        val g = androidx.compose.ui.text.googlefonts.GoogleFont(name)
        return androidx.compose.ui.text.font.FontFamily(weights.map { w -> androidx.compose.ui.text.googlefonts.Font(googleFont = g, fontProvider = gfProvider, weight = w) })
    }
    private val families by lazy {
        val n = androidx.compose.ui.text.font.FontWeight.Normal
        val m = androidx.compose.ui.text.font.FontWeight.Medium
        val b = androidx.compose.ui.text.font.FontWeight.Bold
        val sb = androidx.compose.ui.text.font.FontWeight.SemiBold
        // 不載入 Bold：粗體會落到 SemiBold，粗細差距小、版面比較整齊
        listOf(null, gf("Noto Sans TC", n, m, sb), gf("Noto Serif TC", n, m, sb), gf("LXGW WenKai TC", n, b))
    }
    fun family(i: Int): androidx.compose.ui.text.font.FontFamily? = families.getOrNull(i)
    /** 圓角：0 圓潤、1 細緻、2 直角 */
    val corners = listOf("圓潤", "細緻", "直角")
    fun shapes(i: Int) = when (i) {
        1 -> androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(2.dp), small = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(6.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
        2 -> androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(0.dp), small = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(0.dp), large = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
        else -> androidx.compose.material3.Shapes()
    }
}

/** 目前的外觀設定（給少數自己畫形狀的元件讀，例如圓角）。 */
object AppearancePrefs {
    var corner by mutableStateOf(0)
    /** 區塊樣式：false 線條（預設，像股票頁）、true 卡片 */
    var cards by mutableStateOf(false)
    /** 列表圖示：交易列、帳戶列左邊的圓形圖示（外觀 → 圖示） */
    var icons by mutableStateOf(true)
    /** 卡片圓角（依圓角設定） */
    val cardRadius get() = when (corner) { 1 -> 6.dp; 2 -> 0.dp; else -> 12.dp }
}

/** themeMode：0 跟隨系統、1 淺色、2 深色 */
@Composable
fun isDarkTheme(themeMode: Int): Boolean = when (themeMode) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

/** 「標準（100%）」實際的縮放：系統大小的 93%；其他選項以此為基準 */
const val BASE_UI_SCALE = 0.93f

/**
 * [uiScale] 等比例縮放整個介面（dp 與 sp 一起縮放），讓所有畫面的大小一致調整，
 * 不必逐一修改各元件的字級與間距。
 */
@Composable
fun MoneyTheme(
    dark: Boolean = isSystemInDarkTheme(), uiScale: Float = 1f,
    /** 主題色組（Themes.all 的 key） */
    preset: String = "coral",
    /** 字體：0 系統、1 黑體、2 明體 */
    font: Int = 0,
    /** 字體大小（和介面大小分開，只放大文字） */
    textScale: Float = 1f,
    /** 圓角：0 圓潤、1 細緻、2 直角 */
    corner: Int = 0,
    /** 數字等寬（金額上下對齊） */
    tabular: Boolean = true,
    /** 區塊樣式：false 線條、true 卡片 */
    cards: Boolean = false,
    /** 列表圖示 */
    icons: Boolean = true,
    content: @Composable () -> Unit,
) {
    AppearancePrefs.cards = cards
    AppearancePrefs.icons = icons
    val base = LocalDensity.current
    val p = Themes.of(preset)
    AppearancePrefs.corner = corner
    val typo = androidx.compose.runtime.remember(font, tabular) { typographyOf(Themes.family(font), tabular) }
    // 系統字級照實套用（最大 200%，Android 14 起的上限），弱視或長輩放大字體時內文一定跟著變大；
    // 高度固定的工具列另用 [ChromeScale] 限制，不會被撐變形
    CompositionLocalProvider(LocalDensity provides Density(base.density * uiScale * BASE_UI_SCALE, base.fontScale.coerceIn(0.85f, 2f) * textScale)) {
        MaterialTheme(colorScheme = if (dark) p.dark else p.light, typography = typo, shapes = Themes.shapes(corner), content = content)
    }
}

/** 套上字體與等寬數字後的字級表。 */
private fun typographyOf(family: androidx.compose.ui.text.font.FontFamily?, tabular: Boolean): androidx.compose.material3.Typography {
    fun androidx.compose.ui.text.TextStyle.f() = copy(fontFamily = family ?: fontFamily, fontFeatureSettings = if (tabular) "tnum" else null)
    val t = AppTypography
    return t.copy(
        displayLarge = t.displayLarge.f(), displayMedium = t.displayMedium.f(), displaySmall = t.displaySmall.f(),
        headlineLarge = t.headlineLarge.f(), headlineMedium = t.headlineMedium.f(), headlineSmall = t.headlineSmall.f(),
        titleLarge = t.titleLarge.f(), titleMedium = t.titleMedium.f(), titleSmall = t.titleSmall.f(),
        bodyLarge = t.bodyLarge.f(), bodyMedium = t.bodyMedium.f(), bodySmall = t.bodySmall.f(),
        labelLarge = t.labelLarge.f(), labelMedium = t.labelMedium.f(), labelSmall = t.labelSmall.f(),
    )
}


/**
 * 全 App 統一的字級：沒有指定大小的文字（預設 bodyLarge 16sp、標題 22sp）也和其他畫面一致。
 * 層級：大標 18、標題 15、內文 14、次要 13、說明 12、標籤 11。
 */
private val AppTypography = androidx.compose.material3.Typography().let { t ->
    fun androidx.compose.ui.text.TextStyle.sized(size: Int, line: Int) =
        copy(fontSize = androidx.compose.ui.unit.TextUnit(size.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp),
            lineHeight = androidx.compose.ui.unit.TextUnit(line.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp))
    t.copy(
        headlineSmall = t.headlineSmall.sized(20, 26),
        titleLarge = t.titleLarge.sized(18, 24),
        titleMedium = t.titleMedium.sized(15, 21),
        titleSmall = t.titleSmall.sized(14, 20),
        bodyLarge = t.bodyLarge.sized(14, 20),
        bodyMedium = t.bodyMedium.sized(13, 18),
        bodySmall = t.bodySmall.sized(13, 17),
        labelLarge = t.labelLarge.sized(13, 18),
        labelMedium = t.labelMedium.sized(13, 17),
        labelSmall = t.labelSmall.sized(11, 14),
    )
}


/**
 * 全 App 的字級（文字大小一律從這裡取，不再各頁自訂）：
 * 大數字 26／大標 18／區塊標題 15（粗）／內文 14／次要 13／說明 12／標籤 11／徽章 10。
 */
object AppText {
    val display = androidx.compose.ui.unit.TextUnit(26f, androidx.compose.ui.unit.TextUnitType.Sp)
    val title = androidx.compose.ui.unit.TextUnit(18f, androidx.compose.ui.unit.TextUnitType.Sp)
    val section = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)
    val body = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp)
    val secondary = androidx.compose.ui.unit.TextUnit(13.5f, androidx.compose.ui.unit.TextUnitType.Sp)
    val caption = androidx.compose.ui.unit.TextUnit(13f, androidx.compose.ui.unit.TextUnitType.Sp)
    val label = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
    val badge = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)
}


/**
 * 工具列（標題列、分頁列、底部分頁、狀態列）的文字不跟系統字級放大：高度固定的列不會被撐變形；
 * 仍會跟 App 的「介面大小」一起等比例縮放。
 */
@Composable
fun ChromeScale(content: @Composable () -> Unit) {
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale.coerceAtMost(1.15f)), content = content)
}
