package com.beeze.music.ui

import android.content.Context

/** Folia 外观选择。宿主保存当前选项，以便面板展示并在 WebView 创建后恢复。 */
object LyricThemes {
    val modes = listOf(
        "classic", "cadenza", "partita", "fume", "claddagh", "cappella", "tilt",
        "diorama", "monet", "pendolo", "sonnet", "tempera", "lumiere", "still",
    )
    private const val PREFS = "beeze"
    // v2：默认主题从 classic 改为 pendolo（v1 存的 classic 默认值不视为用户主动选择）
    private const val KEY = "lyric_theme_index_v2"
    private const val BACKGROUND_KEY = "lyric_background_v1"
    private val DEFAULT_INDEX = modes.indexOf("pendolo")
    val backgrounds = listOf("common", "latent", "monet", "nomand", "sora")

    private fun index(context: Context): Int {
        val stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY, DEFAULT_INDEX)
        return stored.coerceIn(modes.indices)
    }

    /** 当前样式（默认 pendolo） */
    fun current(context: Context): String = modes[index(context)]

    fun select(context: Context, mode: String) {
        val selected = modes.indexOf(mode)
        if (selected < 0) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY, selected).apply()
    }

    fun currentBackground(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(BACKGROUND_KEY, "monet")
            ?.takeIf { it in backgrounds } ?: "monet"

    fun selectBackground(context: Context, mode: String) {
        if (mode !in backgrounds) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(BACKGROUND_KEY, mode).apply()
    }
}
