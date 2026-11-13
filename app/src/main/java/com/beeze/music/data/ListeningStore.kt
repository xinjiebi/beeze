package com.beeze.music.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 收听数据仓库：喜欢标记、播放事件流、每日收听时长，持久化到 filesDir 的 JSON 小文件。
 * For you 页的 Liked / Most Played / Recently played / 每周收听统计都从这里取数。
 *
 * 写入分两种：喜欢、播放事件这类低频变更立即落盘；
 * 播放中每 500ms 累计的收听时长只写内存，靠 [flush] 定期落盘，避免频繁 IO。
 */
class ListeningStore(context: Context) {

    private val file = File(context.filesDir, "listening_data.json")
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    var likedIds: Set<Long> = emptySet()
        private set

    /** 播放事件（歌曲 id + 时间戳），新的在队尾，封顶 2000 条防止文件无限增长 */
    private data class PlayEvent(val songId: Long, val at: Long)
    private val events = ArrayDeque<PlayEvent>()

    /** 每日收听时长："yyyy-MM-dd" -> ms，只保留最近 14 天 */
    private val dailyMs = mutableMapOf<String, Long>()

    /** 是否有未落盘的时长累计 */
    private var dirty = false

    init {
        load()
    }

    /** 切换喜欢，返回切换后是否已喜欢 */
    @Synchronized
    fun toggleLike(songId: Long): Boolean {
        likedIds = if (songId in likedIds) likedIds - songId else likedIds + songId
        save()
        return songId in likedIds
    }

    /** 批量加入喜欢，返回本次新增数量 */
    @Synchronized
    fun addLikes(songIds: Set<Long>): Int {
        val added = songIds.count { it !in likedIds }
        if (added > 0) {
            likedIds = likedIds + songIds
            save()
        }
        return added
    }

    /** 记录一次播放（切歌时调用） */
    @Synchronized
    fun recordPlay(songId: Long, at: Long = System.currentTimeMillis()) {
        events.addLast(PlayEvent(songId, at))
        while (events.size > 2000) events.removeFirst()
        save()
    }

    /** 累计收听时长（只写内存，由 [flush] 落盘） */
    @Synchronized
    fun addListeningMs(ms: Long, at: Long = System.currentTimeMillis()) {
        val key = dayFormat.format(Date(at))
        dailyMs[key] = (dailyMs[key] ?: 0L) + ms
        dirty = true
    }

    /** 把内存中的时长累计落盘（播放暂停/定时/退出时调用） */
    @Synchronized
    fun flush() {
        if (dirty) {
            dirty = false
            save()
        }
    }

    /** 最近播放的歌曲 id：按最近一次播放时间倒序去重 */
    @Synchronized
    fun recentIds(): List<Long> =
        events.reversed().map { it.songId }.distinct()

    /** 指定时间之后每首歌的播放次数，按次数倒序 */
    @Synchronized
    fun topCounts(sinceMs: Long): List<Pair<Long, Int>> =
        events.filter { it.at >= sinceMs }
            .groupingBy { it.songId }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

    /** 全部历史播放次数（Featured 取不常听用） */
    @Synchronized
    fun allTimeCounts(): Map<Long, Int> =
        events.groupingBy { it.songId }.eachCount()

    /** 最近 7 天（含今天）的收听总时长 */
    @Synchronized
    fun weeklyListeningMs(nowMs: Long = System.currentTimeMillis()): Long {
        var total = 0L
        for (i in 0..6) {
            total += dailyMs[dayFormat.format(Date(nowMs - i * DAY_MS))] ?: 0L
        }
        return total
    }

    private fun load() = runCatching {
        if (!file.isFile) return@runCatching
        val o = JSONObject(file.readText())
        val likedArr = o.optJSONArray("liked") ?: JSONArray()
        likedIds = buildSet {
            for (i in 0 until likedArr.length()) add(likedArr.getLong(i))
        }
        val eventsArr = o.optJSONArray("events") ?: JSONArray()
        for (i in 0 until eventsArr.length()) {
            val e = eventsArr.getJSONArray(i)
            events.addLast(PlayEvent(e.getLong(0), e.getLong(1)))
        }
        val dailyObj = o.optJSONObject("daily")
        dailyObj?.keys()?.forEach { key -> dailyMs[key] = dailyObj.getLong(key) }
        pruneDaily()
    }

    @Synchronized
    private fun save() = runCatching {
        val o = JSONObject()
        o.put("liked", JSONArray().apply { likedIds.forEach { put(it) } })
        o.put("events", JSONArray().apply {
            events.forEach { put(JSONArray().apply { put(it.songId); put(it.at) }) }
        })
        o.put("daily", JSONObject().apply { dailyMs.forEach { (k, v) -> put(k, v) } })
        // 先写临时文件再重命名：进程写入中途被杀也不会留下截断的坏数据
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(file)
    }

    /** 只保留最近 14 天的时长记录 */
    private fun pruneDaily() {
        val cutoff = dayFormat.format(Date(System.currentTimeMillis() - 14 * DAY_MS))
        dailyMs.keys.removeAll { it < cutoff }
    }

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
