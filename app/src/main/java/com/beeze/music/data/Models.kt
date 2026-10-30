package com.beeze.music.data

import android.net.Uri

/** MediaStore 专辑封面表 URI */
internal val ALBUM_ART_BASE_URI: Uri = Uri.parse("content://media/external/audio/albumart")

/**
 * 封面加载模型：专辑 ID（查封面表）+ 音频内容 URI（内嵌封面兜底）。
 * 很多音频的封面只内嵌在文件里，封面表查不到，需要回退提取。
 */
data class SongArtwork(
    val albumId: Long,
    val contentUri: Uri,
)

/** 一首本机音频 */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val contentUri: Uri,
    val path: String,
) {
    val artwork: SongArtwork
        get() = SongArtwork(albumId, contentUri)
}

/** 文件夹聚合 */
data class FolderItem(
    val name: String,
    val path: String,
    val songs: List<Song>,
)

/** 专辑聚合 */
data class AlbumItem(
    val albumId: Long,
    val name: String,
    val artist: String,
    val songs: List<Song>,
) {
    /** 列表唯一键：合并分组后不同组可能共享同一 albumId（如 MediaStore 给整个 ringtones 目录同一个 id），
     *  因此用「专辑名 + 艺术家」作 LazyColumn/LazyRow 的 key */
    val key: String
        get() = "$name|$artist"

    val artwork: SongArtwork?
        get() = songs.firstOrNull()?.let { SongArtwork(albumId, it.contentUri) }
}

/** 艺术家聚合 */
data class ArtistItem(
    val name: String,
    val songs: List<Song>,
)
