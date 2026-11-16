package com.syncedlyrics.sdk

import org.junit.Assert.*
import org.junit.Test

class LrcParserTest {
    @Test fun `parses and sorts line timestamps including repeated lines`() {
        val result = LrcParser.parse("[00:02.20]second\n[00:01.1][00:03.123]first\n[ar:someone]")
        assertEquals(listOf(1100L, 2200L, 3123L), result.map { it.startMs })
        assertEquals(listOf("first", "second", "first"), result.map { it.text })
    }
    @Test fun `parses enhanced word timestamps and uses next line for final word`() {
        val result = LrcParser.parse("[00:01.00]<00:01.00>Hello <00:01.50>world\n[00:03.00]next")
        assertEquals("Hello world", result[0].text)
        assertEquals(listOf(LyricWord("Hello ", 1000, 1500), LyricWord("world", 1500, 3000)), result[0].words)
    }
    @Test fun `applies standard offset and keeps missing final end instantaneous`() {
        val result = LrcParser.parse("[offset:-200]\n[00:01.00]<00:01.00>end")
        assertEquals(800, result.single().startMs)
        assertEquals(LyricWord("end", 800, 800), result.single().words.single())
    }
    @Test fun `ignores metadata and empty rows`() {
        assertTrue(LrcParser.parse("[ti:title]\n[00:01.00]  \nplain text").isEmpty())
    }
}
