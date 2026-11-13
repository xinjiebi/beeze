package com.beeze.music.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 从 App 删除的歌曲过滤记录；只隐藏 Beeze 中的条目，不删除本地文件 */
class LibraryFilterStore(context: Context) {

    private val file = File(context.filesDir, "library_filters.json")

    var songs: List<Song> = emptyList()
        private set

    init {
        load()
    }

    @Synchronized
    fun contains(id: Long, path: String): Boolean =
        songs.any { it.id == id || it.path == path }

    @Synchronized
    fun add(items: List<Song>) {
        val newItems = items.filterNot { song -> songs.any { it.id == song.id || it.path == song.path } }
        if (newItems.isEmpty()) return
        songs = songs + newItems
        save()
    }

    @Synchronized
    fun remove(song: Song) {
        songs = songs.filterNot { it.id == song.id || it.path == song.path }
        save()
    }

    private fun load() = runCatching {
        if (!file.isFile) return@runCatching
        val arr = JSONArray(file.readText())
        songs = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    Song(
                        id = o.getLong("id"),
                        title = o.getString("title"),
                        artist = o.getString("artist"),
                        album = o.getString("album"),
                        albumId = o.getLong("albumId"),
                        durationMs = o.getLong("durationMs"),
                        contentUri = Uri.parse(o.getString("contentUri")),
                        path = o.getString("path"),
                        dateAddedSeconds = o.optLong("dateAddedSeconds"),
                    )
                )
            }
        }
    }

    @Synchronized
    private fun save() = runCatching {
        val arr = JSONArray()
        songs.forEach { song ->
            arr.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("album", song.album)
                    put("albumId", song.albumId)
                    put("durationMs", song.durationMs)
                    put("contentUri", song.contentUri.toString())
                    put("path", song.path)
                    put("dateAddedSeconds", song.dateAddedSeconds)
                }
            )
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
