package com.beeze.music.data

import android.os.Build
import android.os.Environment
import java.io.File

/**
 * 歌词仓库：读取音频同目录下的同名 .lrc 文件。
 * API 30+ 读取非媒体文件需要 MANAGE_EXTERNAL_STORAGE（设置页手动开启），
 * 无权限或无文件时返回 null，播放页显示无歌词态。
 */
object LyricsRepository {

    /** 是否已具备读取歌词文件的权限 */
    fun canReadLyrics(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    /** 查找并读取歌词：优先「同名.lrc」，其次「同名-歌词.lrc」；过滤外语歌的中文翻译行 */
    fun loadLrc(song: Song): String? {
        if (!canReadLyrics()) return null
        val audio = File(song.path)
        val dir = audio.parentFile ?: return null
        val base = audio.nameWithoutExtension
        val candidates = listOf(
            File(dir, "$base.lrc"),
            File(dir, "$base-歌词.lrc"),
        )
        return candidates.firstOrNull { it.isFile }?.let { file ->
            runCatching { filterTranslation(file.readText(Charsets.UTF_8)) }.getOrNull()
        }
    }

    /** 一行 LRC：时间戳前缀 + 正文（兼容增强 LRC 的逐字时间标签） */
    private val lineRegex = Regex("^((?:\\[[^\\]]+\\])+)(.*)$")

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
