package com.readvoice.app.data.settings

import android.content.SharedPreferences
import com.readvoice.app.domain.AppearanceSettings
import com.readvoice.app.domain.model.Appearance
import com.readvoice.app.domain.model.FontChoice
import com.readvoice.app.domain.model.ThemeMode

/** Appearance choices are not sensitive, so they are stored as plain preferences. */
class PrefsAppearanceStore(private val prefs: SharedPreferences) : AppearanceSettings {

    override fun current(): Appearance {
        val defaults = Appearance()
        return Appearance(
            theme = enumOr(prefs.getString(KEY_THEME, null), defaults.theme),
            font = enumOr(prefs.getString(KEY_FONT, null), defaults.font),
            textScale = prefs.getFloat(KEY_TEXT_SCALE, defaults.textScale)
        )
    }

    override fun save(appearance: Appearance) {
        prefs.edit()
            .putString(KEY_THEME, appearance.theme.name)
            .putString(KEY_FONT, appearance.font.name)
            .putFloat(KEY_TEXT_SCALE, appearance.textScale)
            .apply()
    }

    /** Falls back to the default if a stored name no longer exists. */
    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == name } ?: fallback

    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_FONT = "font"
        const val KEY_TEXT_SCALE = "text_scale"
    }
}
