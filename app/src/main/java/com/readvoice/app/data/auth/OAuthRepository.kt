package com.readvoice.app.data.auth

import com.readvoice.app.domain.AuthRepository
import com.readvoice.app.domain.TokenStore
import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder
import java.security.MessageDigest

/**
 * Reddit login using the OAuth authorization-code flow for installed apps.
 *
 * A login attempt is accepted only when the redirect carries the same state
 * value this app generated for it. The state is consumed on first use, so a
 * redirect cannot be replayed.
 */
class OAuthRepository(
    private val config: AuthConfig,
    private val tokenStore: TokenStore,
    private val pendingAuth: PendingAuthStore,
    private val stateGenerator: StateGenerator,
    private val exchanger: CodeExchanger,
    private val revoker: TokenRevoker,
    private val io: CoroutineDispatcher
) : AuthRepository {

    override val isLoggedIn: Boolean
        get() = tokenStore.load() != null

    override fun beginLogin(): AppResult<String> {
        if (config.clientId.isBlank()) return AppResult.Failure(AppError.NOT_CONFIGURED)
        val state = stateGenerator.newState()
        pendingAuth.save(state)
        return AppResult.Success(config.authorizeUrl(state))
    }

    override suspend fun completeLogin(redirectUri: String): AppResult<Unit> = withContext(io) {
        val expectedState = pendingAuth.consume()
        val params = RedirectParser.parse(redirectUri, config.redirectUri)
        val code = params?.get("code")
        when {
            params == null || expectedState == null -> rejected()
            !sameValue(expectedState, params["state"]) -> rejected()
            params["error"] != null || code.isNullOrEmpty() -> rejected()
            else -> when (val result = exchanger.exchange(code)) {
                is TokenResult.Granted -> {
                    tokenStore.save(result.token)
                    AppResult.Success(Unit)
                }
                TokenResult.Rejected -> rejected()
                TokenResult.Unavailable -> AppResult.Failure(AppError.NETWORK)
            }
        }
    }

    override suspend fun logout() = withContext(io) {
        val token = tokenStore.load()
        // Wipe first, so the phone is clean even if the revoke call fails.
        tokenStore.clear()
        if (token != null) {
            val refreshToken = token.refreshToken
            if (refreshToken != null) {
                revoker.revoke(refreshToken, isRefreshToken = true)
            } else {
                revoker.revoke(token.accessToken, isRefreshToken = false)
            }
        }
    }

    private fun rejected(): AppResult<Unit> = AppResult.Failure(AppError.AUTH_REJECTED)

    /** Compares in constant time so the check leaks nothing about the expected value. */
    private fun sameValue(expected: String, actual: String?): Boolean =
        actual != null && MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
}

/** Reads the query parameters of a redirect, but only if it targets this app's redirect address. */
internal object RedirectParser {

    fun parse(redirect: String, expectedRedirect: String): Map<String, String>? {
        val uri = toUri(redirect) ?: return null
        val expected = toUri(expectedRedirect) ?: return null
        if (!uri.scheme.equals(expected.scheme, ignoreCase = true)) return null
        if (!uri.host.equals(expected.host, ignoreCase = true)) return null
        return uri.rawQuery.orEmpty()
            .split('&')
            .filter { it.isNotEmpty() }
            .associate { pair ->
                val separator = pair.indexOf('=')
                if (separator < 0) {
                    decode(pair) to ""
                } else {
                    decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
                }
            }
    }

    private fun toUri(value: String): URI? =
        try {
            URI(value)
        } catch (e: URISyntaxException) {
            null
        }

    private fun decode(value: String): String =
        try {
            URLDecoder.decode(value, "UTF-8")
        } catch (e: IllegalArgumentException) {
            ""
        }
}
