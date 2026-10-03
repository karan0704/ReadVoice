package com.readvoice.app.data.net

import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.AppResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Read-only access to the Reddit data API. Failures come back as [AppError]
 * values instead of exceptions.
 *
 * UNAUTHORIZED is reported only once the stored session is gone. That is the
 * signal the UI uses to return to the login screen, and tying it to the store
 * keeps the login screen from bouncing straight back while a session exists.
 */
class RedditApiClient(
    private val client: OkHttpClient,
    private val tokens: AccessTokenSource,
    private val io: CoroutineDispatcher
) {

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): AppResult<String> =
        withContext(io) {
            val url = HttpUrl.Builder()
                .scheme("https")
                .host(RedditHosts.API)
                .addPathSegments(path)
                .apply { query.forEach { (name, value) -> addQueryParameter(name, value) } }
                // raw_json=1 stops Reddit from HTML-escaping message text.
                .addQueryParameter("raw_json", "1")
                .build()
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    when {
                        response.isSuccessful -> AppResult.Success(body)
                        response.code == 401 || response.code == 403 -> sessionFailure(AppError.SERVER)
                        response.code == 429 -> AppResult.Failure(AppError.RATE_LIMITED)
                        else -> AppResult.Failure(AppError.SERVER)
                    }
                }
            } catch (e: IOException) {
                sessionFailure(AppError.NETWORK)
            }
        }

    private fun sessionFailure(whileLoggedIn: AppError): AppResult.Failure =
        AppResult.Failure(if (tokens.hasSession()) whileLoggedIn else AppError.UNAUTHORIZED)
}
