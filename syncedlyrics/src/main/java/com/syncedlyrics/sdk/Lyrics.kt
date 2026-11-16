package com.syncedlyrics.sdk

/** All timestamps are absolute song positions in milliseconds. */
data class LyricWord(val text: String, val startMs: Long, val endMs: Long)
data class LyricLine(
    val startMs: Long,
    val text: String,
    val translation: String? = null,
    val words: List<LyricWord> = emptyList(),
)

/** Parses line-timed LRC and enhanced LRC with <mm:ss.xx> word markers. */
object LrcParser {
    private val lineTime = Regex("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val wordTime = Regex("<(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?>")
    private val offsetTag = Regex("^\\[offset:([+-]?\\d+)]$", RegexOption.IGNORE_CASE)

    @JvmStatic fun parse(lrc: String): List<LyricLine> {
        val rows = lrc.lineSequence().map { it.trim() }.toList()
        val offset = rows.firstNotNullOfOrNull { offsetTag.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() } ?: 0L
        val parsed = mutableListOf<LyricLine>()
        for (row in rows) {
            val stamps = lineTime.findAll(row).toList()
            if (stamps.isEmpty() || stamps.first().range.first != 0) continue
            val contiguous = stamps.takeWhileIndexed { index, match -> index == 0 || match.range.first == stamps[index - 1].range.last + 1 }
            val content = row.substring(contiguous.last().range.last + 1)
            val markers = wordTime.findAll(content).toList()
            val plain = wordTime.replace(content, "")
            if (plain.isBlank()) continue
            for (stamp in contiguous) {
                val start = (time(stamp) + offset).coerceAtLeast(0)
                val words = markers.mapIndexedNotNull { i, marker ->
                    val endIndex = markers.getOrNull(i + 1)?.range?.first ?: content.length
                    val part = content.substring(marker.range.last + 1, endIndex)
                    if (part.isEmpty()) null else {
                        val wordStart = (time(marker) + offset).coerceAtLeast(0)
                        val wordEnd = markers.getOrNull(i + 1)?.let { time(it) + offset } ?: wordStart
                        LyricWord(part, wordStart, wordEnd.coerceAtLeast(wordStart))
                    }
                }
                parsed += LyricLine(start, plain, words = words)
            }
        }
        val sorted = parsed.sortedBy { it.startMs }
        // The last word ends at the next line; when absent it completes instantly.
        return sorted.mapIndexed { i, line ->
            if (line.words.isEmpty()) line else {
                val nextStart = sorted.getOrNull(i + 1)?.startMs
                val words = line.words.toMutableList()
                val last = words.last()
                words[words.lastIndex] = last.copy(endMs = (nextStart ?: last.startMs).coerceAtLeast(last.startMs))
                line.copy(words = words)
            }
        }
    }

    private fun time(match: MatchResult): Long {
        val fraction = match.groupValues[3].padEnd(3, '0').ifEmpty { "0" }.toLong()
        return match.groupValues[1].toLong() * 60_000 + match.groupValues[2].toLong() * 1000 + fraction
    }

    private inline fun <T> List<T>.takeWhileIndexed(predicate: (Int, T) -> Boolean): List<T> {
        val result = mutableListOf<T>()
        forEachIndexed { i, value -> if (!predicate(i, value)) return result else result.add(value) }
        return result
    }
}
