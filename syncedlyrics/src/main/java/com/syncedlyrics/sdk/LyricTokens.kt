package com.syncedlyrics.sdk

import java.text.BreakIterator
import java.util.Locale

/** UTF-16 offsets paired with intact words and punctuation, for non-CJK compositions. */
internal object LyricTokens {
    fun words(text: String): List<Pair<Int, String>> {
        val iterator = BreakIterator.getWordInstance(Locale.ROOT)
        iterator.setText(text)
        val tokens = mutableListOf<Pair<Int, String>>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val segment = text.substring(start, end)
            if (segment.any { !it.isWhitespace() }) {
                if (tokens.isNotEmpty() && segment.all { !it.isLetterOrDigit() } && tokens.last().first + tokens.last().second.length == start) {
                    val previous = tokens.removeAt(tokens.lastIndex)
                    tokens.add(previous.first to (previous.second + segment))
                } else tokens.add(start to segment)
            }
            start = end
            end = iterator.next()
        }
        return tokens
    }
    /** Compact CJK chunks; punctuation ends a phrase and stays attached to it. */
    fun classicPhrases(text: String): List<Pair<Int, String>> {
        if (text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN }) return words(text)
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val result = mutableListOf<Pair<Int, String>>()
        var groupStart = 0
        var count = 0
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val unit = text.substring(start, end)
            if (count == 0) groupStart = start
            count++
            val punctuation = unit.codePoints().anyMatch { !Character.isLetterOrDigit(it) && !Character.isWhitespace(it) }
            val next = iterator.next()
            val nextIsPunctuation = next != BreakIterator.DONE && text.substring(end, next).codePoints().anyMatch { !Character.isLetterOrDigit(it) && !Character.isWhitespace(it) }
            if (punctuation || (count >= 3 && !nextIsPunctuation) || next == BreakIterator.DONE) {
                val value = text.substring(groupStart, end)
                if (value.isNotBlank()) result.add(groupStart to value)
                count = 0
            }
            start = end
            end = next
        }
        return result
    }
    /** Prefer supplied word boundaries when they cover the line; retain offsets for timing. */
    fun classicPhrases(line: LyricLine): List<Pair<Int, String>> {
        if (line.words.isEmpty()) return classicPhrases(line.text)
        val pieces = mutableListOf<Pair<Int, String>>()
        var cursor = 0
        for (word in line.words) {
            if (word.text.isEmpty()) continue
            val start = line.text.indexOf(word.text, cursor)
            if (start < 0 || line.text.substring(cursor, start).any { !it.isWhitespace() }) return classicPhrases(line.text)
            pieces.add(start to word.text)
            cursor = start + word.text.length
        }
        if (pieces.isEmpty() || line.text.substring(cursor).any { !it.isWhitespace() }) return classicPhrases(line.text)
        if (line.text.codePoints().anyMatch { Character.UnicodeScript.of(it) == Character.UnicodeScript.LATIN }) return words(line.text)
        val result = mutableListOf<Pair<Int, String>>()
        var offset = pieces.first().first
        var chunk = ""
        for ((start, text) in pieces) {
            if (chunk.isNotEmpty() && (start != offset + chunk.length || chunk.codePointCount(0, chunk.length) + text.codePointCount(0, text.length) > 4)) {
                result.add(offset to chunk); chunk = ""
            }
            if (chunk.isEmpty()) offset = start
            chunk += text
            val punctuation = text.codePoints().anyMatch { !Character.isLetterOrDigit(it) && !Character.isWhitespace(it) }
            if (punctuation || chunk.codePointCount(0, chunk.length) >= 3) { result.add(offset to chunk); chunk = "" }
        }
        if (chunk.isNotEmpty()) result.add(offset to chunk)
        return result
    }

    fun timedRange(line: LyricLine, offset: Int, length: Int): LongRange? {
        var search = 0
        var startTime: Long? = null
        var endTime: Long? = null
        for (word in line.words) {
            val start = line.text.indexOf(word.text, search)
            if (start < 0 || word.text.isEmpty()) continue
            val end = start + word.text.length
            search = end
            if (start < offset + length && end > offset) {
                startTime = minOf(startTime ?: word.startMs, word.startMs)
                endTime = maxOf(endTime ?: word.endMs, word.endMs)
            }
        }
        return if (startTime != null && endTime != null) startTime..endTime else null
    }
}
