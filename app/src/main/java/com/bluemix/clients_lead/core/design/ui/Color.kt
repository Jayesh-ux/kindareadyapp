package ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Black: Color = Color(0xFF000000)
val Gray900: Color = Color(0xFF282828)
val Gray800: Color = Color(0xFF4b4b4b)
val Gray700: Color = Color(0xFF5e5e5e)
val Gray600: Color = Color(0xFF727272)
val Gray500: Color = Color(0xFF868686)
val Gray400: Color = Color(0xFFC7C7C7)
val Gray300: Color = Color(0xFFDFDFDF)
val Gray200: Color = Color(0xFFE2E2E2)
val Gray100: Color = Color(0xFFF7F7F7)
val Gray50: Color = Color(0xFFFFFFFF)
val White: Color = Color(0xFFFFFFFF)

val Red900: Color = Color(0xFF520810)
val Red800: Color = Color(0xFF950f22)
val Red700: Color = Color(0xFFbb032a)
val Red600: Color = Color(0xFFde1135)
val Red500: Color = Color(0xFFf83446)
val Red400: Color = Color(0xFFfc7f79)
val Red300: Color = Color(0xFFffb2ab)
val Red200: Color = Color(0xFFffd2cd)
val Red100: Color = Color(0xFFffe1de)
val Red50: Color = Color(0xFFfff0ee)

val Blue900: Color = Color(0xFF276EF1)
val Blue800: Color = Color(0xFF3F7EF2)
val Blue700: Color = Color(0xFF578EF4)
val Blue600: Color = Color(0xFF6F9EF5)
val Blue500: Color = Color(0xFF87AEF7)
val Blue400: Color = Color(0xFF9FBFF8)
val Blue300: Color = Color(0xFFB7CEFA)
val Blue200: Color = Color(0xFFCFDEFB)
val Blue100: Color = Color(0xFFE7EEFD)
val Blue50: Color = Color(0xFFFFFFFF)

val Green950: Color = Color(0xFF0B4627)
val Green900: Color = Color(0xFF16643B)
val Green800: Color = Color(0xFF1A7544)
val Green700: Color = Color(0xFF178C4E)
val Green600: Color = Color(0xFF1DAF61)
val Green500: Color = Color(0xFF1FC16B)
val Green400: Color = Color(0xFF3EE089)
val Green300: Color = Color(0xFF84EBB4)
val Green200: Color = Color(0xFFC2F5DA)
val Green100: Color = Color(0xFFD0FBE9)
val Green50: Color = Color(0xFFE0FAEC)

@Immutable
data class Colors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val error: Color,
    val onError: Color,
    val success: Color,
    val onSuccess: Color,
    val disabled: Color,
    val onDisabled: Color,
    val surface: Color,
    val onSurface: Color,
    val background: Color,
    val onBackground: Color,
    val outline: Color,
    val transparent: Color = Color.Transparent,
    val white: Color = White,
    val black: Color = Black,
    val text: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val scrim: Color,
    val elevation: Color,
)

val Slate900: Color = Color(0xFF0F172A)
val Slate800: Color = Color(0xFF1E293B)
val Slate700: Color = Color(0xFF334155)
val Slate600: Color = Color(0xFF475569)
val Slate500: Color = Color(0xFF64748B)
val Slate400: Color = Color(0xFF94A3B8)
val Slate300: Color = Color(0xFFCBD5E1)
val Slate200: Color = Color(0xFFE2E8F0)
val Slate100: Color = Color(0xFFF1F5F9)
val Slate50: Color = Color(0xFFF8FAFC)

internal val LightColors =
    Colors(
        primary = Slate900,
        onPrimary = White,
        secondary = Slate600,
        onSecondary = White,
        tertiary = Blue700,
        onTertiary = White,
        surface = White,
        onSurface = Slate900,
        error = Red600,
        onError = White,
        success = Green700,
        onSuccess = White,
        disabled = Slate200,
        onDisabled = Slate400,
        background = Slate50,
        onBackground = Slate900,
        outline = Slate300,
        transparent = Color.Transparent,
        white = White,
        black = Black,
        text = Slate900,
        textSecondary = Slate600,
        textDisabled = Slate400,
        scrim = Black.copy(alpha = 0.32f),
        elevation = Slate300,
    )

internal val DarkColors =
    Colors(
        primary = White,
        onPrimary = Slate900,
        secondary = Slate400,
        onSecondary = White,
        tertiary = Blue400,
        onTertiary = Slate900,
        surface = Slate900,
        onSurface = White,
        error = Red400,
        onError = Black,
        success = Green400,
        onSuccess = Black,
        disabled = Slate800,
        onDisabled = Slate600,
        background = Color(0xFF020617),
        onBackground = White,
        outline = Slate700,
        transparent = Color.Transparent,
        white = White,
        black = Black,
        text = White,
        textSecondary = Slate400,
        textDisabled = Slate600,
        scrim = Black.copy(alpha = 0.72f),
        elevation = Slate800,
    )

val LocalColors = staticCompositionLocalOf { LightColors }
val LocalContentColor = compositionLocalOf { Color.Black }
val LocalContentAlpha = compositionLocalOf { 1f }

fun Colors.contentColorFor(backgroundColor: Color): Color {
    return when (backgroundColor) {
        primary -> onPrimary
        secondary -> onSecondary
        tertiary -> onTertiary
        surface -> onSurface
        error -> onError
        success -> onSuccess
        disabled -> onDisabled
        background -> onBackground
        outline -> onBackground
        elevation -> onBackground
        scrim -> onPrimary
        transparent -> onBackground
        white -> black
        black -> white
        text -> background
        textSecondary -> background
        textDisabled -> background
        else -> onBackground // Default to onBackground for unknown colors
    }
}
