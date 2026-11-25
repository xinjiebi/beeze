package com.beeze.music.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class PlaybackSnapshot(
    val songIds: List<Long>,
    val currentSongId: Long,
    val positionMs: Long,
    val playMode: String,
)

/** 上次播放快照；恢复时按当前媒体库重新解析歌曲，已删除的条目会被跳过。 */
class PlaybackStateStore(context: Context) {

    private val file = File(context.filesDir, "playback_state.json")

    fun load(): PlaybackSnapshot? = runCatching {
        if (!file.isFile) return@runCatching null
        val o = JSONObject(file.readText())
        val ids = o.getJSONArray("songs")
        val songIds = buildList {
            for (i in 0 until ids.length()) add(ids.getLong(i))
        }
        if (songIds.isEmpty()) return@runCatching null
        PlaybackSnapshot(
            songIds = songIds,
            currentSongId = o.getLong("currentSongId"),
            positionMs = o.optLong("positionMs").coerceAtLeast(0L),
            playMode = o.optString("playMode"),
        )
    }.getOrNull()

    @Synchronized
    fun save(snapshot: PlaybackSnapshot) {
        runCatching {
            val o = JSONObject().apply {
                put("songs", JSONArray().apply { snapshot.songIds.forEach { put(it) } })
                put("currentSongId", snapshot.currentSongId)
                put("positionMs", snapshot.positionMs.coerceAtLeast(0L))
                put("playMode", snapshot.playMode)
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(file)
        }
    }

    @Synchronized
    fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.tmp").delete()
    }
}
