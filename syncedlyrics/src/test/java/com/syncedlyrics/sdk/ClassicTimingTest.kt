package com.syncedlyrics.sdk

import org.junit.Assert.*
import org.junit.Test

class ClassicTimingTest {
    @Test fun `existing enhanced lrc fixture preserves per word ranges`() {
        val fixture = javaClass.getResource("/stage-demo-enhanced.lrc")!!.readText()
        val line = LrcParser.parse(fixture).first()
        assertEquals(listOf("Folia", "stage", "demo"), LyricTokens.classicPhrases(line).map { it.second })
        assertEquals(420L..780L, LyricTokens.timedRange(line, 6, 5))
    }
    @Test fun `repeated words retain distinct timing`() {
        val line = LyricLine(0, "go go go", words = listOf(LyricWord("go", 0, 300), LyricWord("go", 400, 700), LyricWord("go", 800, 1100)))
        assertEquals(400L..700L, LyricTokens.timedRange(line, 3, 2))
        assertEquals(800L..1100L, LyricTokens.timedRange(line, 6, 2))
    }
    @Test fun `long english and mixed text preserve words`() {
        val text = "Hold supercalifragilisticexpialidocious 继续 moving"
        val phrases = LyricTokens.classicPhrases(text)
        assertTrue(phrases.any { it.second == "supercalifragilisticexpialidocious" })
        assertEquals("Holdsupercalifragilisticexpialidocious继续moving", phrases.joinToString("") { it.second })
    }
}
