package com.beeze.music.data

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppearanceMode { SYSTEM, LIGHT, DARK }

enum class AppLanguage { SYSTEM, SIMPLIFIED_CHINESE, ENGLISH }

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var appearance: AppearanceMode
        get() = readEnum("appearance", AppearanceMode.SYSTEM)
        set(value) = preferences.edit().putString("appearance", value.name).apply()

    var language: AppLanguage
        get() = readEnum("language", AppLanguage.SYSTEM)
        set(value) = preferences.edit().putString("language", value.name).apply()

    private inline fun <reified T : Enum<T>> readEnum(key: String, default: T): T =
        preferences.getString(key, null)?.let { name ->
            enumValues<T>().firstOrNull { it.name == name }
        } ?: default
}

object AppLocale {
    fun wrap(context: Context): Context {
        val language = AppSettingsStore(context).language
        val locale = when (language) {
            AppLanguage.SYSTEM -> if (isSimplifiedChinese(Locale.getDefault())) {
                Locale.SIMPLIFIED_CHINESE
            } else {
                Locale.ENGLISH
            }
            AppLanguage.SIMPLIFIED_CHINESE -> Locale.SIMPLIFIED_CHINESE
            AppLanguage.ENGLISH -> Locale.ENGLISH
        }
        val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration)
    }

    fun isSimplifiedChinese(context: Context): Boolean =
        isSimplifiedChinese(context.resources.configuration.locales[0])

    private fun isSimplifiedChinese(locale: Locale): Boolean =
        locale.language == "zh" && (locale.script == "Hans" || locale.country == "CN")
}
