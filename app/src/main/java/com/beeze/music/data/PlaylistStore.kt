package com.beeze.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 歌单仓库：用户自建歌单（名称 + 歌曲 id 列表），持久化到 filesDir 的 JSON 小文件。
 */
class PlaylistStore(context: Context) {

    private val file = File(context.filesDir, "playlists.json")

    var playlists: List<Playlist> = emptyList()
        private set

    init {
        load()
    }

    /** 新建歌单，返回新建的歌单 */
    @Synchronized
    fun create(name: String): Playlist {
        val playlist = Playlist(
            id = System.currentTimeMillis(),
            name = name.trim().ifBlank { "Playlist ${playlists.size + 1}" },
            songIds = emptyList(),
        )
        playlists = playlists + playlist
        save()
        return playlist
    }

    /** 把歌曲加入歌单；歌单不存在或歌曲已存在时返回 false */
    @Synchronized
    fun addSong(playlistId: Long, songId: Long): Boolean {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        val playlist = playlists[index]
        if (songId in playlist.songIds) return false
        playlists = playlists.toMutableList().apply {
            this[index] = playlist.copy(songIds = playlist.songIds + songId)
        }
        save()
        return true
    }

    /** 批量加入歌曲并跳过重复项，返回实际新增数量 */
    @Synchronized
    fun addSongs(playlistId: Long, songIds: List<Long>): Int {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return 0
        val playlist = playlists[index]
        val newIds = songIds.distinct().filterNot { it in playlist.songIds }
        if (newIds.isEmpty()) return 0
        playlists = playlists.toMutableList().apply {
            this[index] = playlist.copy(songIds = playlist.songIds + newIds)
        }
        save()
        return newIds.size
    }

    /** 新建歌单并加入歌曲 */
    @Synchronized
    fun createWithSongs(name: String, songIds: List<Long>): Playlist {
        val playlist = create(name)
        addSongs(playlist.id, songIds)
        return playlists.first { it.id == playlist.id }
    }

    @Synchronized
    fun rename(playlistId: Long, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return false
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return false
        playlists = playlists.toMutableList().apply {
            this[index] = this[index].copy(name = trimmed)
        }
        save()
        return true
    }

    @Synchronized
    fun delete(playlistId: Long): Boolean {
        val remaining = playlists.filterNot { it.id == playlistId }
        if (remaining.size == playlists.size) return false
        playlists = remaining
        save()
        return true
    }

    /** 仅从指定歌单移除歌曲，不影响媒体库 */
    @Synchronized
    fun removeSongs(playlistId: Long, songIds: Set<Long>) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return
        val playlist = playlists[index]
        val remaining = playlist.songIds.filterNot { it in songIds }
        if (remaining.size == playlist.songIds.size) return
        playlists = playlists.toMutableList().apply {
            this[index] = playlist.copy(songIds = remaining)
        }
        save()
    }

    /** 从所有歌单移除已在 App 内删除的歌曲 */
    @Synchronized
    fun removeSongsEverywhere(songIds: Set<Long>) {
        playlists = playlists.map { playlist ->
            playlist.copy(songIds = playlist.songIds.filterNot { it in songIds })
        }
        save()
    }

    /** 保存歌单内完整顺序 */
    @Synchronized
    fun reorder(playlistId: Long, songIds: List<Long>) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index < 0) return
        val playlist = playlists[index]
        if (songIds.toSet() != playlist.songIds.toSet() || songIds.size != playlist.songIds.size) return
        playlists = playlists.toMutableList().apply {
            this[index] = playlist.copy(songIds = songIds)
        }
        save()
    }

    private fun load() = runCatching {
        if (!file.isFile) return@runCatching
        val arr = JSONArray(file.readText())
        playlists = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ids = o.optJSONArray("songs") ?: JSONArray()
                add(
                    Playlist(
                        id = o.getLong("id"),
                        name = o.getString("name"),
                        songIds = buildList { for (j in 0 until ids.length()) add(ids.getLong(j)) },
                    )
                )
            }
        }
    }

    @Synchronized
    private fun save() = runCatching {
        val arr = JSONArray()
        playlists.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("songs", JSONArray().apply { p.songIds.forEach { put(it) } })
                }
            )
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
