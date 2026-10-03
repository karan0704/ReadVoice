package com.readvoice.app.data.auth

import com.readvoice.app.domain.TokenStore
import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.AppResult
import com.readvoice.app.domain.model.AuthToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeTokenStore(var token: AuthToken? = null) : TokenStore {
    override fun load(): AuthToken? = token
    override fun save(token: AuthToken) {
        this.token = token
    }

    override fun clear() {
        token = null
    }
}

class OAuthRepositoryTest {

    private val config = AuthConfig("client123", "readvoice://oauth", listOf("identity", "privatemessages"))
    private val granted = AuthToken("access", "refresh", 1_000L)

    private val store = FakeTokenStore()
    private val pending = object : PendingAuthStore {
        var state: String? = null
        override fun save(state: String) {
            this.state = state
        }

        override fun consume(): String? = state.also { state = null }
    }
    private val exchanger = object : CodeExchanger {
        var result: TokenResult = TokenResult.Granted(granted)
        val codes = mutableListOf<String>()
        override fun exchange(code: String): TokenResult {
            codes += code
            return result
        }
    }
    private val revoker = object : TokenRevoker {
        val revoked = mutableListOf<String>()
        override fun revoke(token: String, isRefreshToken: Boolean) {
            revoked += token
        }
    }

    private fun repository(clientId: String = config.clientId) = OAuthRepository(
        config = config.copy(clientId = clientId),
        tokenStore = store,
        pendingAuth = pending,
        stateGenerator = object : StateGenerator {
            override fun newState() = "state-abc"
        },
        exchanger = exchanger,
        revoker = revoker,
        io = Dispatchers.Unconfined
    )

    @Test
    fun beginLoginBuildsRedditUrlWithStateAndMinimalScopes() {
        val url = (repository().beginLogin() as AppResult.Success).value

        assertTrue(url.startsWith("https://www.reddit.com/api/v1/authorize.compact?"))
        assertTrue(url.contains("client_id=client123"))
        assertTrue(url.contains("state=state-abc"))
        assertTrue(url.contains("scope=identity%20privatemessages"))
        assertEquals("state-abc", pending.state)
    }

    @Test
    fun beginLoginFailsWithoutClientId() {
        assertEquals(AppResult.Failure(AppError.NOT_CONFIGURED), repository(clientId = "").beginLogin())
    }

    @Test
    fun matchingStateExchangesCodeAndSavesToken() = runBlocking {
        val repo = repository()
        repo.beginLogin()

        val result = repo.completeLogin("readvoice://oauth?state=state-abc&code=the-code#_")

        assertEquals(AppResult.Success(Unit), result)
        assertEquals(listOf("the-code"), exchanger.codes)
        assertEquals(granted, store.token)
        assertTrue(repo.isLoggedIn)
    }

    @Test
    fun wrongStateIsRejectedWithoutContactingReddit() = runBlocking {
        val repo = repository()
        repo.beginLogin()

        val result = repo.completeLogin("readvoice://oauth?state=forged&code=the-code")

        assertEquals(AppResult.Failure(AppError.AUTH_REJECTED), result)
        assertTrue(exchanger.codes.isEmpty())
        assertNull(store.token)
    }

    @Test
    fun redirectWithoutAStartedLoginIsRejected() = runBlocking {
        val result = repository().completeLogin("readvoice://oauth?state=state-abc&code=the-code")

        assertEquals(AppResult.Failure(AppError.AUTH_REJECTED), result)
        assertTrue(exchanger.codes.isEmpty())
    }

    @Test
    fun aRedirectCannotBeReplayed() = runBlocking {
        val repo = repository()
        repo.beginLogin()
        val redirect = "readvoice://oauth?state=state-abc&code=the-code"
        repo.completeLogin(redirect)

        val second = repo.completeLogin(redirect)

        assertEquals(AppResult.Failure(AppError.AUTH_REJECTED), second)
        assertEquals(1, exchanger.codes.size)
    }

    @Test
    fun redirectToAnotherAddressIsRejected() = runBlocking {
        val repo = repository()
        repo.beginLogin()

        val result = repo.completeLogin("evilapp://oauth?state=state-abc&code=the-code")

        assertEquals(AppResult.Failure(AppError.AUTH_REJECTED), result)
        assertTrue(exchanger.codes.isEmpty())
    }

    @Test
    fun deniedAccessIsRejected() = runBlocking {
        val repo = repository()
        repo.beginLogin()

        val result = repo.completeLogin("readvoice://oauth?state=state-abc&error=access_denied")

        assertEquals(AppResult.Failure(AppError.AUTH_REJECTED), result)
        assertTrue(exchanger.codes.isEmpty())
    }

    @Test
    fun unreachableRedditIsReportedAsNetworkError() = runBlocking {
        val repo = repository()
        repo.beginLogin()
        exchanger.result = TokenResult.Unavailable

        val result = repo.completeLogin("readvoice://oauth?state=state-abc&code=the-code")

        assertEquals(AppResult.Failure(AppError.NETWORK), result)
        assertNull(store.token)
    }

    @Test
    fun logoutWipesTheSessionAndRevokesTheRefreshToken() = runBlocking {
        store.token = granted
        val repo = repository()

        repo.logout()

        assertNull(store.token)
        assertFalse(repo.isLoggedIn)
        assertEquals(listOf("refresh"), revoker.revoked)
    }
}
