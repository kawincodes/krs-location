package com.krstechsolutions.krslocation.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Colour tokens mirroring shadcn/ui's default "zinc" theme
 * (background / foreground / primary / secondary / muted / accent / destructive / border / ring).
 */
@Immutable
data class ShadColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val popover: Color,
    val popoverForeground: Color,
    val primary: Color,
    val primaryForeground: Color,
    val secondary: Color,
    val secondaryForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val accentForeground: Color,
    val destructive: Color,
    val destructiveForeground: Color,
    val success: Color,
    val successForeground: Color,
    val border: Color,
    val input: Color,
    val ring: Color,
    val isDark: Boolean,
)

val LightShadColors = ShadColors(
    background = Color(0xFFFFFFFF),
    foreground = Color(0xFF09090B),
    card = Color(0xFFFFFFFF),
    cardForeground = Color(0xFF09090B),
    popover = Color(0xFFFFFFFF),
    popoverForeground = Color(0xFF09090B),
    primary = Color(0xFF18181B),
    primaryForeground = Color(0xFFFAFAFA),
    secondary = Color(0xFFF4F4F5),
    secondaryForeground = Color(0xFF18181B),
    muted = Color(0xFFF4F4F5),
    mutedForeground = Color(0xFF71717A),
    accent = Color(0xFFF4F4F5),
    accentForeground = Color(0xFF18181B),
    destructive = Color(0xFFEF4444),
    destructiveForeground = Color(0xFFFAFAFA),
    success = Color(0xFF16A34A),
    successForeground = Color(0xFFFAFAFA),
    border = Color(0xFFE4E4E7),
    input = Color(0xFFE4E4E7),
    ring = Color(0xFF18181B),
    isDark = false,
)

val DarkShadColors = ShadColors(
    background = Color(0xFF09090B),
    foreground = Color(0xFFFAFAFA),
    card = Color(0xFF09090B),
    cardForeground = Color(0xFFFAFAFA),
    popover = Color(0xFF09090B),
    popoverForeground = Color(0xFFFAFAFA),
    primary = Color(0xFFFAFAFA),
    primaryForeground = Color(0xFF18181B),
    secondary = Color(0xFF27272A),
    secondaryForeground = Color(0xFFFAFAFA),
    muted = Color(0xFF27272A),
    mutedForeground = Color(0xFFA1A1AA),
    accent = Color(0xFF27272A),
    accentForeground = Color(0xFFFAFAFA),
    destructive = Color(0xFF7F1D1D),
    destructiveForeground = Color(0xFFFAFAFA),
    success = Color(0xFF22C55E),
    successForeground = Color(0xFF052E16),
    border = Color(0xFF27272A),
    input = Color(0xFF27272A),
    ring = Color(0xFFD4D4D8),
    isDark = true,
)

/** shadcn `--radius: 0.5rem` and its derived sizes. */
object ShadRadius {
    val sm = 4.dp
    val md = 6.dp
    val lg = 8.dp
    val xl = 12.dp
    val full = 999.dp

    val smShape = RoundedCornerShape(sm)
    val mdShape = RoundedCornerShape(md)
    val lgShape = RoundedCornerShape(lg)
    val xlShape = RoundedCornerShape(xl)
    val fullShape = RoundedCornerShape(full)
}

@Immutable
data class ShadTypography(
    val h3: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    val h4: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.2).sp,
    ),
    val p: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    val small: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    val muted: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    val xs: TextStyle = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    val mono: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
)

val LocalShadColors = staticCompositionLocalOf { LightShadColors }
val LocalShadTypography = staticCompositionLocalOf { ShadTypography() }

object ShadTheme {
    val colors: ShadColors
        @Composable @ReadOnlyComposable get() = LocalShadColors.current
    val typography: ShadTypography
        @Composable @ReadOnlyComposable get() = LocalShadTypography.current
}

@Composable
fun ShadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkShadColors else LightShadColors
    val typography = ShadTypography()
    CompositionLocalProvider(
        LocalShadColors provides colors,
        LocalShadTypography provides typography,
        LocalContentColor provides colors.foreground,
        LocalTextStyle provides typography.p,
        LocalIndication provides ripple(),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(colors.background)
        ) {
            content()
        }
    }
}
