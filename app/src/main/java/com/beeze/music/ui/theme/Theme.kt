package com.beeze.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 设计稿颜色 Tokens */
object BeezeColors {
    val Primary = Color(0xFFA135FF)
    val PrimarySubtle = Color(0xFFE7DAF3)
    val Ink = Color(0xFF1C1C1E)
    val Body = Color(0xFF3A3A3C)
    val Muted = Color(0xFF8E8E93)
    val Surface = Color(0xFFFFFFFF)
    val Canvas = Color(0xFFF7F7F5)
    val SurfaceSoft = Color(0xFFF0F0ED)
    val Hairline = Color(0xFFE5E5E0)
}

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

private val BeezeColorScheme = lightColorScheme(
    primary = BeezeColors.Primary,
    background = BeezeColors.SurfaceSoft,
    surface = BeezeColors.Surface,
    onBackground = BeezeColors.Ink,
    onSurface = BeezeColors.Ink,
)

@Composable
fun BeezeTheme(content: @Composable () -> Unit) {
    // Material3 仅作基础，UI 颜色与字体均按设计稿定制
    MaterialTheme(colorScheme = BeezeColorScheme, content = content)
}
