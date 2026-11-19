package com.beeze.music.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** 应用全部界面颜色。品牌色、功能渐变和播放器暗底颜色都在这里统一调整。 */
@Immutable
data class BeezeColorPalette(
    val Primary: Color,
    val PrimarySubtle: Color,
    val Ink: Color,
    val Body: Color,
    val Muted: Color,
    val Surface: Color,
    val Canvas: Color,
    val SurfaceSoft: Color,
    val Hairline: Color,
    val Transparent: Color,
    val Scrim: Color,
    val OnGradient: Color,
    val Disc: Color,
    val MiniPlayerTitle: Color,
    val MiniPlayerArtist: Color,
    val SectionPlayBackground: Color,
    val SectionPlayIcon: Color,
    val PlayerText: Color,
    val PlayerTextDim: Color,
    val PlayerIconFaint: Color,
    val PlayerTrack: Color,
    val PlayerAccent: Color,
    val PlayerShade: Color,
    val LyricCardStart: Color,
    val LyricCardEnd: Color,
    val LikedCardStart: Color,
    val LikedCardEnd: Color,
    val ShuffleCardStart: Color,
    val ShuffleCardEnd: Color,
    val MostPlayedCardStart: Color,
    val MostPlayedCardEnd: Color,
    val LikedCoverStart: Color,
    val LikedCoverEnd: Color,
    val EntryLyricStart: Color,
    val EntryLyricEnd: Color,
    val EntryLikedStart: Color,
    val EntryLikedEnd: Color,
    val EntryMostPlayedStart: Color,
    val EntryMostPlayedEnd: Color,
)

val BeezeLightColors = BeezeColorPalette(
    Primary = Color(0xFFA135FF),
    PrimarySubtle = Color(0xFFE7DAF3),
    Ink = Color(0xFF1C1C1E),
    Body = Color(0xFF3A3A3C),
    Muted = Color(0xFF8E8E93),
    Surface = Color(0xFFFFFFFF),
    Canvas = Color(0xFFF7F7F5),
    SurfaceSoft = Color(0xFFF0F0ED),
    Hairline = Color(0xFFE5E5E0),
    Transparent = Color(0x00000000),
    Scrim = Color(0xA6000000),
    OnGradient = Color(0xFFFFFFFF),
    Disc = Color(0xFF1C1C1E),
    MiniPlayerTitle = Color(0xFF333333),
    MiniPlayerArtist = Color(0xFF666666),
    SectionPlayBackground = Color(0xFF1C1C1E),
    SectionPlayIcon = Color(0xFFFFFFFF),
    PlayerText = Color(0xFFFFFFFF),
    PlayerTextDim = Color(0xB3FFFFFF),
    PlayerIconFaint = Color(0x8CFFFFFF),
    PlayerTrack = Color(0x40FFFFFF),
    PlayerAccent = Color(0xFFC98AFF),
    PlayerShade = Color(0xFF000000),
    LyricCardStart = Color(0xFF07BA9A),
    LyricCardEnd = Color(0xFFA135FF),
    LikedCardStart = Color(0xFFCE0CB7),
    LikedCardEnd = Color(0xFFF0C63E),
    ShuffleCardStart = Color(0xFF7313D2),
    ShuffleCardEnd = Color(0xFF2898E9),
    MostPlayedCardStart = Color(0xFF18B110),
    MostPlayedCardEnd = Color(0xFF1BA8EE),
    LikedCoverStart = Color(0xFFE237A6),
    LikedCoverEnd = Color(0xFFEBB470),
    EntryLyricStart = Color(0xFF07BA99),
    EntryLyricEnd = Color(0xFF963EF7),
    EntryLikedStart = Color(0xFFD325A8),
    EntryLikedEnd = Color(0xFFEFBF43),
    EntryMostPlayedStart = Color(0xFF18B022),
    EntryMostPlayedEnd = Color(0xFF1BA8EB),
)

val BeezeDarkColors = BeezeColorPalette(
    Primary = Color(0xFFC98AFF),
    PrimarySubtle = Color(0xFF3B2B4A),
    Ink = Color(0xFFF5F5F5),
    Body = Color(0xFFE5E5E7),
    Muted = Color(0xFFA3A3AB),
    Surface = Color(0xFF242327),
    Canvas = Color(0xFF1C1B1F),
    SurfaceSoft = Color(0xFF121215),
    Hairline = Color(0xFF414047),
    Transparent = Color(0x00000000),
    Scrim = Color(0xA6000000),
    OnGradient = Color(0xFFFFFFFF),
    Disc = Color(0xFF1C1C1E),
    MiniPlayerTitle = Color(0xFFF5F5F5),
    MiniPlayerArtist = Color(0xFFB3B3BB),
    SectionPlayBackground = Color(0x24FFFFFF),
    SectionPlayIcon = Color(0xFF000000),
    PlayerText = Color(0xFFFFFFFF),
    PlayerTextDim = Color(0xB3FFFFFF),
    PlayerIconFaint = Color(0x8CFFFFFF),
    PlayerTrack = Color(0x40FFFFFF),
    PlayerAccent = Color(0xFFC98AFF),
    PlayerShade = Color(0xFF000000),
    LyricCardStart = Color(0xFF07BA9A),
    LyricCardEnd = Color(0xFFA135FF),
    LikedCardStart = Color(0xFFCE0CB7),
    LikedCardEnd = Color(0xFFF0C63E),
    ShuffleCardStart = Color(0xFF7313D2),
    ShuffleCardEnd = Color(0xFF2898E9),
    MostPlayedCardStart = Color(0xFF18B110),
    MostPlayedCardEnd = Color(0xFF1BA8EE),
    LikedCoverStart = Color(0xFFE237A6),
    LikedCoverEnd = Color(0xFFEBB470),
    EntryLyricStart = Color(0xFF07BA99),
    EntryLyricEnd = Color(0xFF963EF7),
    EntryLikedStart = Color(0xFFD325A8),
    EntryLikedEnd = Color(0xFFEFBF43),
    EntryMostPlayedStart = Color(0xFF18B022),
    EntryMostPlayedEnd = Color(0xFF1BA8EB),
)

internal val LocalBeezeColors = compositionLocalOf { BeezeLightColors }

/** 保留现有调用方式，实际颜色由 BeezeTheme 根据浅色/深色模式提供。 */
object BeezeColors {
    val Primary @Composable get() = LocalBeezeColors.current.Primary
    val PrimarySubtle @Composable get() = LocalBeezeColors.current.PrimarySubtle
    val Ink @Composable get() = LocalBeezeColors.current.Ink
    val Body @Composable get() = LocalBeezeColors.current.Body
    val Muted @Composable get() = LocalBeezeColors.current.Muted
    val Surface @Composable get() = LocalBeezeColors.current.Surface
    val Canvas @Composable get() = LocalBeezeColors.current.Canvas
    val SurfaceSoft @Composable get() = LocalBeezeColors.current.SurfaceSoft
    val Hairline @Composable get() = LocalBeezeColors.current.Hairline
    val Transparent @Composable get() = LocalBeezeColors.current.Transparent
    val Scrim @Composable get() = LocalBeezeColors.current.Scrim
    val OnGradient = BeezeLightColors.OnGradient
    val Disc = BeezeLightColors.Disc
    val MiniPlayerTitle @Composable get() = LocalBeezeColors.current.MiniPlayerTitle
    val MiniPlayerArtist @Composable get() = LocalBeezeColors.current.MiniPlayerArtist
    val SectionPlayBackground @Composable get() = LocalBeezeColors.current.SectionPlayBackground
    val SectionPlayIcon @Composable get() = LocalBeezeColors.current.SectionPlayIcon
    val PlayerText @Composable get() = LocalBeezeColors.current.PlayerText
    val PlayerTextDim @Composable get() = LocalBeezeColors.current.PlayerTextDim
    val PlayerIconFaint @Composable get() = LocalBeezeColors.current.PlayerIconFaint
    val PlayerTrack @Composable get() = LocalBeezeColors.current.PlayerTrack
    val PlayerAccent @Composable get() = LocalBeezeColors.current.PlayerAccent
    val PlayerShade @Composable get() = LocalBeezeColors.current.PlayerShade
    val LyricCardStart @Composable get() = LocalBeezeColors.current.LyricCardStart
    val LyricCardEnd @Composable get() = LocalBeezeColors.current.LyricCardEnd
    val LikedCardStart @Composable get() = LocalBeezeColors.current.LikedCardStart
    val LikedCardEnd @Composable get() = LocalBeezeColors.current.LikedCardEnd
    val ShuffleCardStart @Composable get() = LocalBeezeColors.current.ShuffleCardStart
    val ShuffleCardEnd @Composable get() = LocalBeezeColors.current.ShuffleCardEnd
    val MostPlayedCardStart @Composable get() = LocalBeezeColors.current.MostPlayedCardStart
    val MostPlayedCardEnd @Composable get() = LocalBeezeColors.current.MostPlayedCardEnd
    val LikedCoverStart @Composable get() = LocalBeezeColors.current.LikedCoverStart
    val LikedCoverEnd @Composable get() = LocalBeezeColors.current.LikedCoverEnd
    val EntryLyricStart @Composable get() = LocalBeezeColors.current.EntryLyricStart
    val EntryLyricEnd @Composable get() = LocalBeezeColors.current.EntryLyricEnd
    val EntryLikedStart @Composable get() = LocalBeezeColors.current.EntryLikedStart
    val EntryLikedEnd @Composable get() = LocalBeezeColors.current.EntryLikedEnd
    val EntryMostPlayedStart @Composable get() = LocalBeezeColors.current.EntryMostPlayedStart
    val EntryMostPlayedEnd @Composable get() = LocalBeezeColors.current.EntryMostPlayedEnd
}
