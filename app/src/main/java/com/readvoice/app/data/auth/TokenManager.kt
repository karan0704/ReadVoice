package com.readvoice.app.data.auth

import com.readvoice.app.data.net.AccessTokenSource
import com.readvoice.app.domain.TokenStore
import com.readvoice.app.domain.model.AuthToken

/**
 * Hands out access tokens and refreshes them when needed. Every method is
 * synchronized, so several requests failing at once trigger a single refresh:
 * the first caller refreshes, the rest find the new token already stored.
 *
 * The session is wiped only when Reddit definitively refuses the refresh
 * token. A network failure leaves it in place for a later retry.
 */
class TokenManager(
    private val store: TokenStore,
    private val refresher: TokenRefresher,
    private val clock: () -> Long
) : AccessTokenSource {

    @Synchronized
    override fun hasSession(): Boolean = store.load() != null

    @Synchronized
    override fun accessToken(): String? {
        val token = store.load() ?: return null
        if (token.expiresAtMillis - EXPIRY_MARGIN_MILLIS > clock()) return token.accessToken
        return refresh(token)
    }

    @Synchronized
    override fun accessTokenAfterRejection(rejected: String): String? {
        val token = store.load() ?: return null
        if (token.accessToken != rejected) return token.accessToken
        return refresh(token)
    }

    private fun refresh(current: AuthToken): String? {
        val refreshToken = current.refreshToken
        if (refreshToken == null) {
            store.clear()
            return null
        }
        return when (val result = refresher.refresh(refreshToken)) {
            is TokenResult.Granted -> {
                // Reddit may omit the refresh token on a refresh; the old one stays valid.
                val renewed = result.token.copy(
                    refreshToken = result.token.refreshToken ?: refreshToken
                )
                store.save(renewed)
                renewed.accessToken
            }
            TokenResult.Rejected -> {
                store.clear()
                null
            }
            TokenResult.Unavailable -> null
        }
    }

    private companion object {
        /** Refresh slightly early so a token cannot expire mid-request. */
        const val EXPIRY_MARGIN_MILLIS = 60_000L
    }
}
