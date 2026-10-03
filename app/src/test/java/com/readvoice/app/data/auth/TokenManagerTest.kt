package com.readvoice.app.data.auth

import com.readvoice.app.domain.model.AuthToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenManagerTest {

    private var now = 1_000_000L
    private val store = FakeTokenStore()
    private val refresher = object : TokenRefresher {
        var result: TokenResult = TokenResult.Unavailable
        var calls = 0
        override fun refresh(refreshToken: String): TokenResult {
            calls++
            return result
        }
    }
    private val manager = TokenManager(store, refresher) { now }

    private fun token(access: String, expiresIn: Long, refresh: String? = "refresh") =
        AuthToken(access, refresh, now + expiresIn)

    @Test
    fun validTokenIsReturnedWithoutRefreshing() {
        store.token = token("fresh", expiresIn = 3_600_000)

        assertEquals("fresh", manager.accessToken())
        assertEquals(0, refresher.calls)
    }

    @Test
    fun expiredTokenIsRefreshedAndKeepsTheOldRefreshToken() {
        store.token = token("old", expiresIn = -1)
        refresher.result = TokenResult.Granted(AuthToken("new", null, now + 3_600_000))

        assertEquals("new", manager.accessToken())
        assertEquals("refresh", store.token?.refreshToken)
    }

    @Test
    fun refusedRefreshWipesTheSession() {
        store.token = token("old", expiresIn = -1)
        refresher.result = TokenResult.Rejected

        assertNull(manager.accessToken())
        assertNull(store.token)
        assertFalse(manager.hasSession())
    }

    @Test
    fun networkFailureKeepsTheSessionForLater() {
        store.token = token("old", expiresIn = -1)
        refresher.result = TokenResult.Unavailable

        assertNull(manager.accessToken())
        assertTrue(manager.hasSession())
    }

    @Test
    fun rejectedTokenTriggersOneRefreshForSeveralCallers() {
        store.token = token("stale", expiresIn = 3_600_000)
        refresher.result = TokenResult.Granted(AuthToken("renewed", "refresh", now + 3_600_000))

        assertEquals("renewed", manager.accessTokenAfterRejection("stale"))
        assertEquals("renewed", manager.accessTokenAfterRejection("stale"))
        assertEquals(1, refresher.calls)
    }

    @Test
    fun sessionWithoutRefreshTokenEndsWhenItExpires() {
        store.token = token("old", expiresIn = -1, refresh = null)

        assertNull(manager.accessToken())
        assertNull(store.token)
        assertEquals(0, refresher.calls)
    }
}
