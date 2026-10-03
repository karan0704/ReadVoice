package com.readvoice.app.data.settings

import android.content.SharedPreferences
import com.readvoice.app.domain.ClientIdSettings

/**
 * Keeps the client ID in plain preferences; it is an app identifier, not a
 * secret. [builtIn] is an ID compiled into the APK, which only debug builds
 * have, and it is used only while the user has not entered their own.
 */
class PrefsClientIdStore(
    private val prefs: SharedPreferences,
    private val builtIn: String
) : ClientIdSettings {

    override fun current(): String? =
        stored() ?: builtIn.takeIf { it.isNotBlank() }

    override fun isSetByUser(): Boolean = stored() != null

    override fun save(clientId: String) {
        prefs.edit().putString(KEY_CLIENT_ID, clientId).apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_CLIENT_ID).apply()
    }

    private fun stored(): String? =
        prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() }

    private companion object {
        const val KEY_CLIENT_ID = "client_id"
    }
}
