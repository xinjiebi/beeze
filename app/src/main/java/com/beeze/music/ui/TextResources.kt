package com.beeze.music.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.beeze.music.R
import com.beeze.music.data.SongSortOption
import com.syncedlyrics.sdk.BackgroundStyle
import com.syncedlyrics.sdk.LyricStyle

@Composable
fun tr(@StringRes id: Int, vararg args: Any): String = stringResource(id, *args)

@Composable
fun songsCount(count: Int): String =
    if (count == 1) tr(R.string.song_count, count) else tr(R.string.songs_count, count)

@Composable
fun albumsCount(count: Int): String =
    if (count == 1) tr(R.string.album_count, count) else tr(R.string.albums_count, count)

@Composable
fun addedToQueueCount(count: Int): String =
    if (count == 1) tr(R.string.added_one_to_queue) else tr(R.string.added_to_queue_count, count)

@Composable
fun sortOptionLabel(option: SongSortOption): String = tr(
    when (option) {
        SongSortOption.Default -> R.string.sort_default
        SongSortOption.TitleAsc -> R.string.sort_title_asc
        SongSortOption.TitleDesc -> R.string.sort_title_desc
        SongSortOption.ArtistAsc -> R.string.sort_artist_asc
        SongSortOption.RecentlyAdded -> R.string.sort_recently_added
        SongSortOption.DurationDesc -> R.string.sort_duration_desc
    }
)

@Composable
fun lyricStyleLabel(style: LyricStyle): String = tr(
    when (style) {
        LyricStyle.CLASSIC -> R.string.lyric_style_classic
        LyricStyle.PARTITA -> R.string.lyric_style_partita
        LyricStyle.CAPPELLA -> R.string.lyric_style_cappella
        LyricStyle.TILT -> R.string.lyric_style_tilt
        LyricStyle.DIORAMA -> R.string.lyric_style_diorama
        LyricStyle.MONET -> R.string.lyric_style_monet
        LyricStyle.PENDOLO -> R.string.lyric_style_pendolo
        LyricStyle.STILL -> R.string.lyric_style_still
    }
)

@Composable
fun backgroundStyleLabel(style: BackgroundStyle): String = tr(
    when (style) {
        BackgroundStyle.LIQUID -> R.string.background_liquid
        BackgroundStyle.MOSAIC -> R.string.background_mosaic
        BackgroundStyle.OIL -> R.string.background_oil
    }
)
