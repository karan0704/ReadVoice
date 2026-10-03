package com.readvoice.app.data.net

import okhttp3.Authenticator
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Builds the app's HTTP clients. Both allow modern TLS only and refuse to
 * follow a redirect from HTTPS down to HTTP. Nothing is logged, so tokens
 * cannot end up in logcat.
 */
class HttpClientFactory(private val userAgent: String) {

    /** Client for the login endpoints, which authenticate with the client ID. */
    fun base(): OkHttpClient =
        OkHttpClient.Builder()
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder().header("User-Agent", userAgent).build()
                )
            }
            .build()

    /** Client for the data API; shares the base client's connection pool. */
    fun authenticated(base: OkHttpClient, tokens: AccessTokenSource): OkHttpClient =
        base.newBuilder()
            .addInterceptor(BearerInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens))
            .build()
}

/**
 * Attaches the access token, and only to requests for the Reddit API host, so
 * the token is never sent anywhere else.
 */
class BearerInterceptor(private val tokens: AccessTokenSource) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != RedditHosts.API) return chain.proceed(request)
        val token = tokens.accessToken() ?: throw IOException("No usable access token")
        return chain.proceed(request.withBearer(token))
    }
}

/** Retries a request once with a fresh token after Reddit answers 401. */
class TokenAuthenticator(private val tokens: AccessTokenSource) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null
        val rejected = response.request.header(AUTHORIZATION)?.removePrefix(BEARER_PREFIX)
            ?: return null
        val fresh = tokens.accessTokenAfterRejection(rejected) ?: return null
        return response.request.withBearer(fresh)
    }
}

private const val AUTHORIZATION = "Authorization"
private const val BEARER_PREFIX = "Bearer "

private fun Request.withBearer(token: String): Request =
    newBuilder().header(AUTHORIZATION, BEARER_PREFIX + token).build()
