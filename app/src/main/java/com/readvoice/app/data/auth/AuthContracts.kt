package com.readvoice.app.data.auth

import com.readvoice.app.data.net.RedditHosts
import com.readvoice.app.domain.model.AuthToken
import okhttp3.HttpUrl

/** Settings of the Reddit "installed app" this build logs in with. */
data class AuthConfig(
    val clientId: String,
    val redirectUri: String,
    val scopes: List<String>
) {
    /** The Reddit consent page for one login attempt. */
    fun authorizeUrl(state: String): String =
        HttpUrl.Builder()
            .scheme("https")
            .host(RedditHosts.WEB)
            .addPathSegments("api/v1/authorize.compact")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("state", state)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("duration", "permanent")
            .addQueryParameter("scope", scopes.joinToString(" "))
            .build()
            .toString()
}

/** Answer from Reddit's token endpoint. */
sealed interface TokenResult {
    data class Granted(val token: AuthToken) : TokenResult

    /** Reddit refused; retrying with the same input will not help. */
    data object Rejected : TokenResult

    /** Reddit could not be reached or had a temporary problem. */
    data object Unavailable : TokenResult
}

interface CodeExchanger {
    fun exchange(code: String): TokenResult
}

interface TokenRefresher {
    fun refresh(refreshToken: String): TokenResult
}

interface TokenRevoker {
    fun revoke(token: String, isRefreshToken: Boolean)
}

/** Produces the unguessable value that ties a redirect to the attempt that started it. */
interface StateGenerator {
    fun newState(): String
}

/** Remembers the state of the login attempt in progress. */
interface PendingAuthStore {
    fun save(state: String)

    /** Returns the saved state once, then forgets it. */
    fun consume(): String?
}

/** Encrypts small secrets with a key that never leaves the device. */
interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(encoded: String): String?
    fun destroyKey()
}
