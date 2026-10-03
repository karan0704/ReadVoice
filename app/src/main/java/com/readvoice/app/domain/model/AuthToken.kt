package com.readvoice.app.domain.model

/**
 * A Reddit OAuth session. The refresh token is absent when Reddit granted a
 * temporary session only.
 */
data class AuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAtMillis: Long
)
