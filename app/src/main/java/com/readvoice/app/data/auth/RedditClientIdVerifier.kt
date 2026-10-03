package com.readvoice.app.data.auth

import com.readvoice.app.data.net.RedditHosts
import com.readvoice.app.domain.ClientIdCheck
import com.readvoice.app.domain.ClientIdVerifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Asks Reddit for an anonymous, app-only token using the given client ID.
 * Reddit grants one only to a real "installed app" ID, so the answer tells
 * whether the ID is usable without the user having to log in first. The
 * token itself is discarded. No user data is involved, and the device ID
 * sent is Reddit's documented "do not track" placeholder.
 *
 * This cannot confirm the redirect URI; a wrong one only shows up as an
 * error on Reddit's own page during login.
 */
class RedditClientIdVerifier(
    private val client: OkHttpClient,
    private val io: CoroutineDispatcher
) : ClientIdVerifier {

    override suspend fun verify(clientId: String): ClientIdCheck = withContext(io) {
        val request = Request.Builder()
            .url("https://${RedditHosts.WEB}/api/v1/access_token")
            .header("Authorization", Credentials.basic(clientId, ""))
            .post(
                FormBody.Builder()
                    .add("grant_type", INSTALLED_CLIENT_GRANT)
                    .add("device_id", ANONYMOUS_DEVICE_ID)
                    .build()
            )
            .build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> ClientIdCheck.VALID
                    response.code == 429 || response.code >= 500 -> ClientIdCheck.UNREACHABLE
                    else -> ClientIdCheck.INVALID
                }
            }
        } catch (e: IOException) {
            ClientIdCheck.UNREACHABLE
        }
    }

    private companion object {
        const val INSTALLED_CLIENT_GRANT = "https://oauth.reddit.com/grants/installed_client"
        const val ANONYMOUS_DEVICE_ID = "DO_NOT_TRACK_THIS_DEVICE"
    }
}
