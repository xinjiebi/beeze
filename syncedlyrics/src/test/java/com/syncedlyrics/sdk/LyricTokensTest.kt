package com.syncedlyrics.sdk

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTokensTest {
    @Test fun `english words remain intact and retain offsets`() {
        assertEquals(listOf(0 to "Let", 4 to "the", 8 to "music", 14 to "carry", 20 to "us", 23 to "home!"), LyricTokens.words("Let the music carry us home!"))
    }
    @Test fun `empty whitespace yields no words`() { assertEquals(emptyList<Pair<Int, String>>(), LyricTokens.words("   ")) }
    @Test fun `classic phrases retain punctuation and preserve source`() {
        val text = "让音乐，陪我们回家！"
        val parts = LyricTokens.classicPhrases(text)
        assertEquals(text, parts.joinToString("") { it.second })
        assertEquals(0, parts.first().first)
        assertEquals("让音乐，", parts.first().second)
    }
    @Test fun `classic english retains complete words`() {
        assertEquals(listOf("Let", "music", "flow"), LyricTokens.classicPhrases("Let music flow").map { it.second })
    }
    @Test fun `timed phrases preserve supplied word boundaries`() {
        val line = LyricLine(0, "这一刻不需要等待", words = listOf(LyricWord("这一刻", 0, 300), LyricWord("不需要", 300, 700), LyricWord("等待", 700, 1000)))
        assertEquals(listOf("这一刻", "不需要", "等待"), LyricTokens.classicPhrases(line).map { it.second })
        assertEquals(300L..700L, LyricTokens.timedRange(line, 3, 3))
    }
    @Test fun `incomplete timing falls back without dropping text`() {
        val line = LyricLine(0, "你好世界", words = listOf(LyricWord("你好", 0, 500)))
        assertEquals(line.text, LyricTokens.classicPhrases(line).joinToString("") { it.second })
    }
}
