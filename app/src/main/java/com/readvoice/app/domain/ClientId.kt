package com.readvoice.app.domain

/**
 * The Reddit client ID each user supplies for their own "installed app".
 * It identifies the app to Reddit, not the person, and is not a secret.
 */
interface ClientIdSettings {
    /** The ID to log in with, or null when none has been set up yet. */
    fun current(): String?

    /** True when the ID was entered on this phone rather than built into the APK. */
    fun isSetByUser(): Boolean

    fun save(clientId: String)
    fun clear()
}

/** Shape checks that need no network: Reddit IDs are short runs of letters, digits, "-" and "_". */
object ClientIdRules {

    private val PATTERN = Regex("^[A-Za-z0-9_-]{10,40}$")

    /** Drops the spaces and line breaks that tend to come along when copying from a web page. */
    fun normalize(input: String): String = input.trim()

    fun isValid(clientId: String): Boolean = PATTERN.matches(clientId)
}

enum class ClientIdCheck {
    /** Reddit knows this ID and it belongs to an installed app. */
    VALID,

    /** Reddit does not accept it: mistyped, deleted, or the wrong app type. */
    INVALID,

    /** Reddit could not be asked right now. */
    UNREACHABLE
}

interface ClientIdVerifier {
    suspend fun verify(clientId: String): ClientIdCheck
}
