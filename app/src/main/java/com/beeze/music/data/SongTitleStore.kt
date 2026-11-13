package com.beeze.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** App 内显示别名；只写应用私有数据，不修改音频文件或 MediaStore */
class SongTitleStore(context: Context) {

    private data class TitleOverride(val originalTitle: String, val displayTitle: String)

    private val file = File(context.filesDir, "song_titles.json")
    private val overrides = mutableMapOf<String, TitleOverride>()

    init {
        load()
    }

    @Synchronized
    fun apply(songs: List<Song>): List<Song> = songs.map { song ->
        overrides[song.path]?.let { song.copy(title = it.displayTitle) } ?: song
    }

    @Synchronized
    fun originalTitle(song: Song): String = overrides[song.path]?.originalTitle ?: song.title

    @Synchronized
    fun rename(song: Song, displayTitle: String) {
        overrides[song.path] = TitleOverride(originalTitle(song), displayTitle)
        save()
    }

    private fun load() = runCatching {
        if (!file.isFile) return@runCatching
        val arr = JSONArray(file.readText())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            overrides[o.getString("path")] = TitleOverride(
                originalTitle = o.getString("originalTitle"),
                displayTitle = o.getString("displayTitle"),
            )
        }
    }

    @Synchronized
    private fun save() = runCatching {
        val arr = JSONArray()
        overrides.forEach { (path, title) ->
            arr.put(
                JSONObject().apply {
                    put("path", path)
                    put("originalTitle", title.originalTitle)
                    put("displayTitle", title.displayTitle)
                }
            )
        }
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }
}
