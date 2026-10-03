package com.readvoice.app.data.net

/**
 * What the HTTP layer needs from the session. Calls block, because OkHttp
 * interceptors and authenticators run on the request's own thread.
 */
interface AccessTokenSource {
    /** True while a session is stored, even if its access token has expired. */
    fun hasSession(): Boolean

    /** A usable access token, refreshing it first when it has expired. */
    fun accessToken(): String?

    /** A replacement for a token Reddit just refused, or null if none can be had. */
    fun accessTokenAfterRejection(rejected: String): String?
}
