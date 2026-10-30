package com.beeze.music.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

/** 通过 MediaStore 查询本机音频 */
class MediaStoreRepository(private val context: Context) {

    companion object {
        // Han-Latin 转写需要 API 29+（ICU）
        private val hanLatin by lazy {
            if (Build.VERSION.SDK_INT >= 29) android.icu.text.Transliterator.getInstance("Han-Latin") else null
        }
        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        /**
         * 排序键：中文按拼音、英文按字母混排 A→Z；数字/符号等非字母开头的归入 # 组排最后。
         * 返回可直接字符串比较的键（"0" 前缀 = 字母组，"1" 前缀 = # 组）。
         * API 26-28 没有 ICU 转写，退化为小写字母序。
         */
        fun sortKeyOf(name: String): String {
            val tr = hanLatin ?: return "0" + name.lowercase()
            val normalized = Normalizer.normalize(tr.transliterate(name), Normalizer.Form.NFD)
                .replace(COMBINING_MARKS, "") // 去拼音声调等组合记号
                .lowercase()
                .replace(" ", "")
            val startsWithLetter = normalized.firstOrNull()?.let { it in 'a'..'z' } == true
            return (if (startsWithLetter) "0" else "1") + normalized
        }
    }

    suspend fun querySongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
        )
        // 只取音乐文件，且过滤掉过短的铃声片段
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // 注意：部分 ROM（如 MIUI）的 MediaProvider 不支持 COLLATE LOCALIZED，会抛
        // IllegalArgumentException("Invalid token LOCALIZED")，因此只用普通排序，
        // 本地化排序在内存中完成
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val cursor = try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)
        } catch (e: Exception) {
            null
        }
        cursor
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol) ?: continue
                    songs += Song(
                        id = id,
                        title = cursor.getString(titleCol) ?: "未知标题",
                        artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "未知艺术家",
                        album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" } ?: "未知专辑",
                        albumId = cursor.getLong(albumIdCol),
                        durationMs = cursor.getLong(durationCol),
                        contentUri = ContentUris.withAppendedId(collection, id),
                        path = path,
                    )
                }
            }
        // 本地化排序放在内存中做（中文按拼音、英文按字母混排 A→Z，非字母开头的 # 组排最后），
        // 兼容不支持 COLLATE LOCALIZED 的 ROM
        songs.sortWith(compareBy { sortKeyOf(it.title) })
        songs
    }

    /** 按歌曲所在目录聚合为文件夹列表 */
    fun groupByFolder(songs: List<Song>): List<FolderItem> =
        songs.groupBy { it.path.substringBeforeLast('/', "") }
            .map { (dir, list) ->
                FolderItem(
                    name = dir.substringAfterLast('/').ifBlank { dir },
                    path = dir,
                    songs = list,
                )
            }
            .sortedWith(compareBy { sortKeyOf(it.name) })

    /**
     * 按专辑聚合。
     * 不按 MediaStore 的 albumId 分组：新版 MediaStore 会把目录算进 albumId，
     * 同一张专辑分散在不同文件夹时会被拆成多条。改用「专辑名 + 艺术家」归一化
     * 合并；带上艺术家是为了避免不同歌手的同名专辑被误并。
     */
    fun groupByAlbum(songs: List<Song>): List<AlbumItem> =
        songs.groupBy { it.album.trim().lowercase() to it.artist.trim().lowercase() }
            .map { (_, list) ->
                AlbumItem(
                    albumId = list.first().albumId,
                    name = list.first().album,
                    artist = list.first().artist,
                    songs = list,
                )
            }
            .sortedWith(compareBy { sortKeyOf(it.name) })

    /** 按艺术家聚合 */
    fun groupByArtist(songs: List<Song>): List<ArtistItem> =
        songs.groupBy { it.artist }
            .map { (name, list) -> ArtistItem(name = name, songs = list) }
            .sortedWith(compareBy { sortKeyOf(it.name) })

    // ---------- 扫描结果缓存（冷启动先渲染缓存，再后台刷新） ----------

    private val cacheFile get() = File(context.filesDir, "songs_cache.json")

    /** 同步读缓存（~80KB 小文件，冷启动时直接在主线程调用，首帧即可渲染列表） */
    fun readCachedSongs(): List<Song>? = runCatching {
        val arr = JSONArray(cacheFile.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val id = o.getLong("id")
                add(
                    Song(
                        id = id,
                        title = o.getString("title"),
                        artist = o.getString("artist"),
                        album = o.getString("album"),
                        albumId = o.getLong("albumId"),
                        durationMs = o.getLong("durationMs"),
                        contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                        ),
                        path = o.getString("path"),
                    )
                )
            }
        }
    }.getOrNull()

    suspend fun saveCachedSongs(songs: List<Song>) = withContext(Dispatchers.IO) {
        runCatching {
            val arr = JSONArray()
            songs.forEach { s ->
                arr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("title", s.title)
                        put("artist", s.artist)
                        put("album", s.album)
                        put("albumId", s.albumId)
                        put("durationMs", s.durationMs)
                        put("path", s.path)
                    }
                )
            }
            // 先写临时文件再重命名：进程写入中途被杀也不会留下截断的坏缓存
            val tmp = File(context.filesDir, "songs_cache.json.tmp")
            tmp.writeText(arr.toString())
            tmp.renameTo(cacheFile)
        }
    }
}
