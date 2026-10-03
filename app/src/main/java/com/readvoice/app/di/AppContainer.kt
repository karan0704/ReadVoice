package com.readvoice.app.di

import android.content.Context
import android.content.SharedPreferences
import com.readvoice.app.BuildConfig
import com.readvoice.app.data.auth.AuthConfig
import com.readvoice.app.data.auth.EncryptedTokenStore
import com.readvoice.app.data.auth.KeystoreCipher
import com.readvoice.app.data.auth.OAuthRepository
import com.readvoice.app.data.auth.PrefsPendingAuthStore
import com.readvoice.app.data.auth.RedditAuthApi
import com.readvoice.app.data.auth.RedditClientIdVerifier
import com.readvoice.app.data.auth.SecureRandomStateGenerator
import com.readvoice.app.data.auth.TokenManager
import com.readvoice.app.data.inbox.InMemoryConversationCache
import com.readvoice.app.data.inbox.InboxRepository
import com.readvoice.app.data.inbox.MessageMapper
import com.readvoice.app.data.inbox.RedditAccountRepository
import com.readvoice.app.data.inbox.RedditMessageApi
import com.readvoice.app.data.net.HttpClientFactory
import com.readvoice.app.data.net.RedditApiClient
import com.readvoice.app.data.settings.PrefsAppearanceStore
import com.readvoice.app.data.settings.PrefsClientIdStore
import com.readvoice.app.domain.AccountRepository
import com.readvoice.app.domain.AppearanceSettings
import com.readvoice.app.domain.AuthRepository
import com.readvoice.app.domain.ClientIdSettings
import com.readvoice.app.domain.ClientIdVerifier
import com.readvoice.app.domain.ConversationCache
import com.readvoice.app.domain.ConversationGrouper
import com.readvoice.app.domain.LogoutUseCase
import com.readvoice.app.domain.MessageRepository
import com.readvoice.app.domain.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json

/**
 * The one place where concrete classes are chosen and wired together. The
 * rest of the app sees only the interfaces exposed here, so swapping an
 * implementation means editing this file alone.
 *
 * Everything network-related is created lazily to keep app start fast.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val clock: () -> Long = System::currentTimeMillis
    private val io = Dispatchers.IO
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * Each user enters their own client ID on the setup screen. The value
     * compiled into the APK is blank except in debug builds, where it lets a
     * developer skip that screen.
     */
    val clientIdSettings: ClientIdSettings =
        PrefsClientIdStore(prefs("client_id"), BuildConfig.REDDIT_CLIENT_ID)

    /**
     * The only access the app asks Reddit for: who you are, and your messages.
     *
     * The client ID is read once, when this container is built; the app
     * builds a new container whenever the ID changes. Before the setup
     * screen existed it came straight from the build:
     *
     *     clientId = BuildConfig.REDDIT_CLIENT_ID,
     */
    private val authConfig = AuthConfig(
        clientId = clientIdSettings.current().orEmpty(),
        redirectUri = BuildConfig.REDDIT_REDIRECT_URI,
        scopes = listOf("identity", "privatemessages")
    )

    private val httpFactory = HttpClientFactory(
        "android:${BuildConfig.APPLICATION_ID}:v${BuildConfig.VERSION_NAME} (open-source client)"
    )
    private val baseClient by lazy { httpFactory.base() }

    private val tokenStore: TokenStore by lazy {
        EncryptedTokenStore(prefs("session"), KeystoreCipher("readvoice.session.key"), json)
    }
    private val authApi by lazy { RedditAuthApi(baseClient, authConfig, clock, json) }
    private val tokenManager by lazy { TokenManager(tokenStore, authApi, clock) }
    private val apiClient by lazy {
        RedditApiClient(httpFactory.authenticated(baseClient, tokenManager), tokenManager, io)
    }

    val appearanceSettings: AppearanceSettings = PrefsAppearanceStore(prefs("appearance"))

    val authRepository: AuthRepository by lazy {
        OAuthRepository(
            config = authConfig,
            tokenStore = tokenStore,
            pendingAuth = PrefsPendingAuthStore(prefs("pending_auth"), clock),
            stateGenerator = SecureRandomStateGenerator(),
            exchanger = authApi,
            revoker = authApi,
            io = io
        )
    }

    val clientIdVerifier: ClientIdVerifier by lazy { RedditClientIdVerifier(baseClient, io) }

    val accountRepository: AccountRepository by lazy { RedditAccountRepository(apiClient, json) }

    val messageRepository: MessageRepository by lazy {
        InboxRepository(RedditMessageApi(apiClient, json), MessageMapper(json), accountRepository)
    }

    val conversationCache: ConversationCache = InMemoryConversationCache()

    val conversationGrouper = ConversationGrouper()

    val logoutUseCase: LogoutUseCase by lazy {
        LogoutUseCase(authRepository, accountRepository, conversationCache)
    }

    private fun prefs(name: String): SharedPreferences =
        appContext.getSharedPreferences(name, Context.MODE_PRIVATE)
}
