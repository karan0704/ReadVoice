package com.readvoice.app.data.auth

import com.readvoice.app.data.net.RedditHosts
import com.readvoice.app.domain.model.AuthToken
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Reddit's token endpoints. An installed app has no client secret, so the
 * client ID is sent as the Basic-auth user with an empty password. All calls
 * block and must run off the main thread.
 */
class RedditAuthApi(
    private val client: OkHttpClient,
    private val config: AuthConfig,
    private val clock: () -> Long,
    private val json: Json
) : CodeExchanger, TokenRefresher, TokenRevoker {

    override fun exchange(code: String): TokenResult =
        requestToken(
            FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", config.redirectUri)
                .build()
        )

    override fun refresh(refreshToken: String): TokenResult =
        requestToken(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .build()
        )

    /** Best effort: the local session is wiped whether or not Reddit hears about it. */
    override fun revoke(token: String, isRefreshToken: Boolean) {
        val form = FormBody.Builder()
            .add("token", token)
            .add("token_type_hint", if (isRefreshToken) "refresh_token" else "access_token")
            .build()
        try {
            client.newCall(post("api/v1/revoke_token", form)).execute().close()
        } catch (e: IOException) {
            // Offline logout still succeeds locally.
        }
    }

    private fun requestToken(form: FormBody): TokenResult =
        try {
            client.newCall(post("api/v1/access_token", form)).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 429 || response.code >= 500 -> TokenResult.Unavailable
                    !response.isSuccessful -> TokenResult.Rejected
                    else -> parse(body)
                }
            }
        } catch (e: IOException) {
            TokenResult.Unavailable
        }

    private fun parse(body: String): TokenResult {
        val dto = try {
            json.decodeFromString<TokenDto>(body)
        } catch (e: IllegalArgumentException) {
            return TokenResult.Unavailable
        }
        val accessToken = dto.accessToken
        if (dto.error != null || accessToken.isNullOrEmpty()) return TokenResult.Rejected
        return TokenResult.Granted(
            AuthToken(
                accessToken = accessToken,
                refreshToken = dto.refreshToken,
                expiresAtMillis = clock() + dto.expiresInSeconds * 1000
            )
        )
    }

    private fun post(path: String, form: FormBody): Request =
        Request.Builder()
            .url("https://${RedditHosts.WEB}/$path")
            .header("Authorization", Credentials.basic(config.clientId, ""))
            .post(form)
            .build()
}

@Serializable
private data class TokenDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresInSeconds: Long = 3600,
    val error: String? = null
)
