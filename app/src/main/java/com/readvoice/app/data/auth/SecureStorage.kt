package com.readvoice.app.data.auth

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.readvoice.app.domain.TokenStore
import com.readvoice.app.domain.model.AuthToken
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key generated inside the Android Keystore. The key
 * cannot be exported, so the ciphertext is useless off this device. The
 * output is the random IV followed by the ciphertext, Base64-encoded.
 */
class KeystoreCipher(private val alias: String) : SecretCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    /** Returns null when the data was tampered with or the key is gone. */
    override fun decrypt(encoded: String): String? =
        try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES)
            )
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }

    override fun destroyKey() {
        try {
            keyStore().deleteEntry(alias)
        } catch (e: GeneralSecurityException) {
            // Nothing to delete.
        }
    }

    private fun key(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * Keeps the session encrypted in the app's private preferences. The decrypted
 * token is cached in memory, so the Keystore is used once per app start.
 */
class EncryptedTokenStore(
    private val prefs: SharedPreferences,
    private val cipher: SecretCipher,
    private val json: Json
) : TokenStore {

    private var cached: AuthToken? = null
    private var loaded = false

    @Synchronized
    override fun load(): AuthToken? {
        if (!loaded) {
            cached = readFromDisk()
            loaded = true
        }
        return cached
    }

    @Synchronized
    override fun save(token: AuthToken) {
        cached = token
        loaded = true
        val plain = json.encodeToString(
            StoredToken.serializer(),
            StoredToken(token.accessToken, token.refreshToken, token.expiresAtMillis)
        )
        try {
            prefs.edit().putString(KEY_SESSION, cipher.encrypt(plain)).apply()
        } catch (e: GeneralSecurityException) {
            // The Keystore is unavailable: the session lives for this run only
            // and is never written to disk unencrypted.
            prefs.edit().remove(KEY_SESSION).apply()
        }
    }

    @Synchronized
    override fun clear() {
        cached = null
        loaded = true
        prefs.edit().remove(KEY_SESSION).apply()
        cipher.destroyKey()
    }

    private fun readFromDisk(): AuthToken? {
        val encoded = prefs.getString(KEY_SESSION, null) ?: return null
        val stored = try {
            cipher.decrypt(encoded)?.let { json.decodeFromString(StoredToken.serializer(), it) }
        } catch (e: IllegalArgumentException) {
            null
        }
        if (stored == null) {
            // Unreadable data is worthless; remove it so the user can log in again.
            prefs.edit().remove(KEY_SESSION).apply()
            return null
        }
        return AuthToken(stored.accessToken, stored.refreshToken, stored.expiresAtMillis)
    }

    @Serializable
    private data class StoredToken(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long
    )

    private companion object {
        const val KEY_SESSION = "session"
    }
}

/** 32 random bytes from the system's secure generator, written as hex. */
class SecureRandomStateGenerator : StateGenerator {

    private val random = SecureRandom()

    override fun newState(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * The state is kept on disk because Android may stop the app while the user
 * is on Reddit's page in the browser. It is single-use and expires quickly.
 */
class PrefsPendingAuthStore(
    private val prefs: SharedPreferences,
    private val clock: () -> Long
) : PendingAuthStore {

    override fun save(state: String) {
        prefs.edit().putString(KEY_STATE, state).putLong(KEY_CREATED, clock()).apply()
    }

    override fun consume(): String? {
        val state = prefs.getString(KEY_STATE, null)
        val created = prefs.getLong(KEY_CREATED, 0L)
        prefs.edit().remove(KEY_STATE).remove(KEY_CREATED).apply()
        return state?.takeIf { clock() - created <= MAX_AGE_MILLIS }
    }

    private companion object {
        const val KEY_STATE = "pending_state"
        const val KEY_CREATED = "pending_created"
        const val MAX_AGE_MILLIS = 10 * 60 * 1000L
    }
}
