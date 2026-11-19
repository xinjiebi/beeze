package com.beeze.music.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 设计稿字体 Tokens（Noto Sans SC 以系统默认字体族加字重代替） */
object BeezeType {
    /** 每周收听卡片的大数字：Bold 28sp（Figma display-lg） */
    val displayLg = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,          // 行高 1.286
        letterSpacing = (-0.3).sp,
    )
    val displayMd = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,          // 行高 1.33
        letterSpacing = (-0.2).sp,
    )
    val title = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,          // 行高 1.4
    )
    val bodyMd = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,          // 行高 1.625
        letterSpacing = 0.2.sp,
    )
    val button = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,          // 行高 1.5
        letterSpacing = 0.5.sp,
    )
    val bodySm = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,          // 行高 1.571
        letterSpacing = 0.2.sp,
    )
    val caption = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,          // 行高 1.5
        letterSpacing = 0.3.sp,
    )
    /** Navbar 上的 Beeze 字标：Black 18sp */
    val brand = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    )
    /** 播放器进度条时间：Medium 10sp，字距 1.2 */
    val navLink = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,          // 行高 1.4
        letterSpacing = 1.2.sp,
    )
}

val LocalBeezeDarkTheme = compositionLocalOf { false }

@Composable
fun BeezeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) BeezeDarkColors else BeezeLightColors
    val materialColors = if (darkTheme) {
        darkColorScheme(
            primary = colors.Primary,
            onPrimary = colors.OnGradient,
            primaryContainer = colors.PrimarySubtle,
            onPrimaryContainer = colors.Ink,
            background = colors.SurfaceSoft,
            onBackground = colors.Ink,
            surface = colors.Surface,
            onSurface = colors.Ink,
            surfaceVariant = colors.Canvas,
            onSurfaceVariant = colors.Body,
            outline = colors.Hairline,
        )
    } else {
        lightColorScheme(
            primary = colors.Primary,
            onPrimary = colors.OnGradient,
            primaryContainer = colors.PrimarySubtle,
            onPrimaryContainer = colors.Ink,
            background = colors.SurfaceSoft,
            onBackground = colors.Ink,
            surface = colors.Surface,
            onSurface = colors.Ink,
            surfaceVariant = colors.Canvas,
            onSurfaceVariant = colors.Body,
            outline = colors.Hairline,
        )
    }
    CompositionLocalProvider(LocalBeezeColors provides colors, LocalBeezeDarkTheme provides darkTheme) {
        MaterialTheme(colorScheme = materialColors, content = content)
    }
}
