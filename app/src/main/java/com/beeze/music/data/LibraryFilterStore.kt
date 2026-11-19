package com.beeze.music.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 从 App 删除的过滤记录；只隐藏 Beeze 中的条目，不删除本地文件 */
class LibraryFilterStore(context: Context) {

    private val file = File(context.filesDir, "library_filters.json")
    private val folderFile = File(context.filesDir, "library_folder_filters.json")
    private val preferences = context.getSharedPreferences("library_scan", Context.MODE_PRIVATE)

    var songs: List<Song> = emptyList()
        private set

    var folders: List<FilteredFolder> = emptyList()
        private set

    var skipShortAudio: Boolean
        get() = preferences.getBoolean("skip_short_audio", true)
        set(value) = preferences.edit().putBoolean("skip_short_audio", value).apply()

    init {
        load()
        loadFolders()
    }

    @Synchronized
    fun contains(id: Long, path: String): Boolean =
        songs.any { it.id == id || it.path == path } || isFolderFiltered(path)

    @Synchronized
    fun add(items: List<Song>) {
        val newItems = items.filterNot { song -> songs.any { it.id == song.id || it.path == song.path } }
        if (newItems.isEmpty()) return
        songs = songs + newItems
    }

    @Synchronized
    fun remove(song: Song) {
        songs = songs.filterNot { it.id == song.id || it.path == song.path }
        save()
    }

    @Synchronized
    fun addFolder(folder: FolderItem) {
        if (folders.any { it.path == folder.path }) return
        folders = folders + FilteredFolder(folder.name, folder.path, folder.songs.size)
    }

    @Synchronized
    fun persist() {
        save()
        saveFolders()
    }

    @Synchronized
    fun removeFolder(folder: FilteredFolder) {
        folders = folders.filterNot { it.path == folder.path }
        saveFolders()
    }

    private fun isFolderFiltered(songPath: String): Boolean {
        val directory = songPath.substringBeforeLast('/', "")
        return folders.any { directory == it.path || directory.startsWith("${it.path}/") }
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

    private fun loadFolders() = runCatching {
        if (!folderFile.isFile) return@runCatching
        val arr = JSONArray(folderFile.readText())
        folders = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    FilteredFolder(
                        name = o.getString("name"),
                        path = o.getString("path"),
                        songCount = o.optInt("songCount"),
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
        writeAtomically(file, arr)
    }

    @Synchronized
    private fun saveFolders() = runCatching {
        val arr = JSONArray()
        folders.forEach { folder ->
            arr.put(
                JSONObject().apply {
                    put("name", folder.name)
                    put("path", folder.path)
                    put("songCount", folder.songCount)
                }
            )
        }
        writeAtomically(folderFile, arr)
    }

    private fun writeAtomically(target: File, value: JSONArray) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(value.toString())
        tmp.renameTo(target)
    }
}

data class FilteredFolder(
    val name: String,
    val path: String,
    val songCount: Int,
)
