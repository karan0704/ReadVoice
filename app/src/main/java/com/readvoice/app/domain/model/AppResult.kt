package com.readvoice.app.domain.model

/** Every failure the UI can be asked to explain. */
enum class AppError {
    NETWORK,
    UNAUTHORIZED,
    RATE_LIMITED,
    SERVER,
    INVALID_RESPONSE,
    AUTH_REJECTED,
    NOT_CONFIGURED,
    NO_BROWSER
}

/** Outcome of an operation that can fail without throwing. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>
}
