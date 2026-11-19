package com.beeze.music.data

import android.content.Context

/** 各列表类型独立保存排序方式 */
enum class SongSortScope {
    Songs, Folder, Album, Artist, Entry, Generic,
}

enum class SongSortOption {
    Default, TitleAsc, TitleDesc, ArtistAsc, RecentlyAdded, DurationDesc,
}

class SongSortStore(context: Context) {
    private val preferences = context.getSharedPreferences("song_sort", Context.MODE_PRIVATE)

    fun get(scope: SongSortScope): SongSortOption =
        preferences.getString(scope.name, null)
            ?.let { name -> SongSortOption.entries.firstOrNull { it.name == name } }
            ?: SongSortOption.Default

    fun set(scope: SongSortScope, option: SongSortOption) {
        preferences.edit().putString(scope.name, option.name).apply()
    }
}

private data class KeyedSong(
    val song: Song,
    val titleKey: String,
    val artistKey: String? = null,
)

private fun List<Song>.withSortKeys(includeArtist: Boolean): List<KeyedSong> = map { song ->
    KeyedSong(
        song = song,
        titleKey = MediaStoreRepository.sortKeyOf(song.title),
        artistKey = if (includeArtist) MediaStoreRepository.sortKeyOf(song.artist) else null,
    )
}

fun List<Song>.sortedForDisplay(option: SongSortOption): List<Song> {
    if (option == SongSortOption.Default) return this
    val keyed = withSortKeys(includeArtist = option == SongSortOption.ArtistAsc)
    val sorted = when (option) {
        SongSortOption.Default -> keyed
        SongSortOption.TitleAsc -> keyed.sortedBy { it.titleKey }
        SongSortOption.TitleDesc -> keyed.sortedByDescending { it.titleKey }
        SongSortOption.ArtistAsc -> keyed.sortedWith(
            compareBy<KeyedSong> { it.artistKey.orEmpty() }.thenBy { it.titleKey }
        )
        SongSortOption.RecentlyAdded -> keyed.sortedWith(
            compareByDescending<KeyedSong> { it.song.dateAddedSeconds }.thenBy { it.titleKey }
        )
        SongSortOption.DurationDesc -> keyed.sortedWith(
            compareByDescending<KeyedSong> { it.song.durationMs }.thenBy { it.titleKey }
        )
    }
    return sorted.map { it.song }
}
