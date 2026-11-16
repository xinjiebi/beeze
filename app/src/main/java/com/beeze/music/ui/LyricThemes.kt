package com.beeze.music.ui

import android.content.Context
import com.syncedlyrics.sdk.BackgroundStyle
import com.syncedlyrics.sdk.LyricStyle

/** SyncedLyrics 外观选择。宿主持久化选项，并在 View 创建后恢复。 */
object LyricThemes {
    val modes = LyricStyle.entries
    val backgrounds = BackgroundStyle.entries
    private const val PREFS = "beeze"
    private const val KEY = "synced_lyric_style_v1"
    private const val BACKGROUND_KEY = "synced_lyric_background_v1"
    private val DEFAULT_INDEX = modes.indexOf(LyricStyle.PENDOLO)

    private fun index(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY, DEFAULT_INDEX)
        return stored.coerceIn(modes.indices)
    }

    fun current(context: Context): LyricStyle = modes[index(context)]

    fun select(context: Context, mode: LyricStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, modes.indexOf(mode)).apply()
    }

    fun currentBackground(context: Context): BackgroundStyle {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(BACKGROUND_KEY, backgrounds.indexOf(BackgroundStyle.LIQUID))
        return backgrounds[stored.coerceIn(backgrounds.indices)]
    }

    fun selectBackground(context: Context, mode: BackgroundStyle) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(BACKGROUND_KEY, backgrounds.indexOf(mode)).apply()
    }
}
