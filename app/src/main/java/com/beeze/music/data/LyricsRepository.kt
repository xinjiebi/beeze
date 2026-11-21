package com.beeze.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class LyricsFolder(val treeUri: Uri, val name: String)

/** 歌词仓库：在用户授权的 SAF 目录中建立 LRC 索引，再按歌曲路径匹配。 */
object LyricsRepository {
    private const val PREFS = "lyrics_folders"
    private const val KEY_TREES = "trees"
    private lateinit var appContext: Context
    private val indexMutex = Mutex()
    @Volatile private var lyricIndex: Map<String, Uri> = emptyMap()
    private var scannedFiles: List<LyricFile> = emptyList()
    private var scannedFolderSignature = ""

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun canReadLyrics(): Boolean = folders().isNotEmpty()

    fun folders(): List<LyricsFolder> {
        val persisted = appContext.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(mutableSetOf()) { it.uri.toString() }
        return savedTreeUris()
            .filter { it.toString() in persisted }
            .map { LyricsFolder(it, folderName(it)) }
    }

    fun addFolder(treeUri: Uri): Boolean = runCatching {
        appContext.contentResolver.takePersistableUriPermission(
            treeUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
        val trees = savedTreeUris().mapTo(mutableSetOf()) { it.toString() }
        trees += treeUri.toString()
        preferences().edit().putStringSet(KEY_TREES, trees).apply()
        true
    }.getOrDefault(false)

    fun removeFolder(treeUri: Uri) {
        val trees = savedTreeUris().mapTo(mutableSetOf()) { it.toString() }
        trees -= treeUri.toString()
        preferences().edit().putStringSet(KEY_TREES, trees).apply()
        runCatching {
            appContext.contentResolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    suspend fun rebuildIndex(songs: List<Song>, reloadFiles: Boolean = false) =
        withContext(Dispatchers.IO) {
            indexMutex.withLock {
                val folders = folders()
                if (folders.isEmpty()) {
                    scannedFiles = emptyList()
                    scannedFolderSignature = ""
                    lyricIndex = emptyMap()
                    return@withLock
                }
                val signature = folders.map { it.treeUri.toString() }.sorted().joinToString("|")
                if (reloadFiles || signature != scannedFolderSignature) {
                    scannedFiles = folders.flatMap { scanTree(it.treeUri) }
                    scannedFolderSignature = signature
                }
                val candidates = scannedFiles
                    .groupBy { songKey(it.directoryPath, it.baseName) }
                    .mapValues { (_, files) ->
                        files.minWithOrNull(compareBy<LyricFile> { it.priority }.thenBy { it.uri.toString() })!!.uri
                    }
                lyricIndex = buildMap {
                    songs.forEach { song ->
                        candidates[songKey(song.path)]?.let { put(song.path, it) }
                    }
                }
            }
        }

    fun hasLrc(song: Song): Boolean = song.path in lyricIndex

    fun loadLrc(song: Song): String? = lyricIndex[song.path]?.let { uri ->
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {
                filterTranslation(it.readText())
            }
        }.getOrNull()
    }

    fun findMatchingLine(song: Song, query: String): String? {
        if (query.isBlank() || !hasLrc(song)) return null
        return loadLrc(song)?.lineSequence()
            ?.map { line ->
                val text = lineRegex.find(line)?.groupValues?.get(2) ?: line
                text.replace(enhancedTimestampRegex, "").trim()
            }
            ?.firstOrNull { it.contains(query, ignoreCase = true) }
    }

    private data class LyricFile(
        val uri: Uri,
        val directoryPath: String,
        val baseName: String,
        val priority: Int,
    )

    private data class TraversalDirectory(val documentId: String, val relativePath: String)

    private fun scanTree(treeUri: Uri): List<LyricFile> {
        val rootPath = rootPath(treeUri) ?: return emptyList()
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val pending = ArrayDeque<TraversalDirectory>()
        pending += TraversalDirectory(rootDocumentId, "")
        val result = mutableListOf<LyricFile>()
        while (pending.isNotEmpty()) {
            val directory = pending.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                directory.documentId,
            )
            val cursor = runCatching {
                appContext.contentResolver.query(
                    childrenUri,
                    arrayOf(
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                    ),
                    null,
                    null,
                    null,
                )
            }.getOrNull() ?: continue
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = it.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (it.moveToNext()) {
                    val documentId = it.getString(idColumn)
                    val name = it.getString(nameColumn) ?: continue
                    if (it.getString(mimeColumn) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        val relativePath = listOf(directory.relativePath, name)
                            .filter(String::isNotEmpty)
                            .joinToString("/")
                        pending += TraversalDirectory(documentId, relativePath)
                    } else if (name.endsWith(".lrc", ignoreCase = true)) {
                        val translated = name.substringBeforeLast('.').endsWith("-歌词", ignoreCase = true)
                        val baseName = name.substringBeforeLast('.').let { base ->
                            if (translated) base.dropLast(3) else base
                        }
                        result += LyricFile(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            directoryPath = File(rootPath, directory.relativePath).path,
                            baseName = baseName,
                            priority = if (translated) 1 else 0,
                        )
                    }
                }
            }
        }
        return result
    }

    private fun rootPath(treeUri: Uri): String? {
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val volume = documentId.substringBefore(':', "")
        if (volume.isEmpty()) return null
        val storageRoot = if (volume == "primary") "/storage/emulated/0" else "/storage/$volume"
        val relativePath = documentId.substringAfter(':', "")
        return File(storageRoot, relativePath).path
    }

    private fun folderName(treeUri: Uri): String {
        val documentId = DocumentsContract.getTreeDocumentId(treeUri)
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return runCatching {
            appContext.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull() ?: documentId.substringAfter(':').ifBlank { documentId }
    }

    private fun savedTreeUris(): List<Uri> = preferences().getStringSet(KEY_TREES, emptySet())
        .orEmpty()
        .map(Uri::parse)

    private fun preferences() = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun songKey(path: String): String = songKey(
        path.substringBeforeLast('/', ""),
        path.substringAfterLast('/').substringBeforeLast('.'),
    )

    private fun songKey(directory: String, baseName: String): String =
        directory.trimEnd('/') + "/" + baseName.lowercase(Locale.ROOT)

    /** 一行 LRC：时间戳前缀 + 正文（兼容增强 LRC 的逐字时间标签） */
    private val lineRegex = Regex("^((?:\\[[^\\]]+\\])+)(.*)$")
    private val enhancedTimestampRegex = Regex("<[^>]+>")

    /** 是否含 CJK 表意文字（汉字，中日文共用） */
    private fun containsHan(text: String): Boolean =
        text.any { it in '\u4e00'..'\u9fff' || it in '\u3400'..'\u4dbf' }

    /** 是否含日文假名 */
    private fun containsKana(text: String): Boolean =
        text.any { it in '\u3040'..'\u30ff' }

    /** 是否含拉丁字母 */
    private fun containsLatin(text: String): Boolean =
        text.any { it in 'A'..'Z' || it in 'a'..'z' }

    /**
     * 是否为中文翻译行：含汉字但不含假名和拉丁字母（纯中文行）。
     * 日文原文含假名、英文原文含拉丁字母，都不会被误判为翻译。
     */
    private fun isTranslationLine(text: String): Boolean =
        containsHan(text) && !containsKana(text) && !containsLatin(text)

    /**
     * 外语歌的 LRC 常在同一时间戳下放「中文翻译 + 原文」两行（翻译在前），
     * 歌词渲染会优先显示第一行即翻译。这里按时间戳分组，非中文歌曲每组只保留
     * 非翻译行（原文）；中文歌曲（歌词整体以纯中文行为主）原样保留。
     */
    fun filterTranslation(lrc: String): String {
        val lines = lrc.lines()
        val texts = lines.mapNotNull { lineRegex.find(it)?.groupValues?.get(2)?.trim() }
            .filter { it.isNotEmpty() }
        // 中文歌的各行都是纯中文；外语歌带翻译时翻译行最多占一半（与原文一一对应）
        val isChineseSong = texts.isNotEmpty() && texts.count { isTranslationLine(it) } * 2 > texts.size
        if (isChineseSong) return lrc

        val out = ArrayList<String>(lines.size)
        var i = 0
        while (i < lines.size) {
            val match = lineRegex.find(lines[i])
            if (match == null) {
                out += lines[i]
                i++
                continue
            }
            // 连续的同时间戳行成一组（翻译与原文同组）
            val prefix = match.groupValues[1]
            val group = mutableListOf<String>()
            var j = i
            while (j < lines.size && lineRegex.find(lines[j])?.groupValues?.get(1) == prefix) {
                group += lines[j]
                j++
            }
            out += if (group.size == 1) {
                group.first()
            } else {
                // 组内优先保留非空且非翻译的正文行（原文），全空则留第一行
                val textsOfGroup = group.map { lineRegex.find(it)!!.groupValues[2] }
                val nonBlank = group.filterIndexed { index, _ -> textsOfGroup[index].isNotBlank() }
                nonBlank.firstOrNull { !isTranslationLine(lineRegex.find(it)!!.groupValues[2]) }
                    ?: nonBlank.firstOrNull()
                    ?: group.first()
            }
            i = j
        }
        return out.joinToString("\n")
    }
}
