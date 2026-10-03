package com.readvoice.app.domain

import com.readvoice.app.domain.model.AppResult
import com.readvoice.app.domain.model.Appearance
import com.readvoice.app.domain.model.AuthToken
import com.readvoice.app.domain.model.Conversation
import com.readvoice.app.domain.model.InboxItem
import com.readvoice.app.domain.model.Page
import com.readvoice.app.domain.model.PrivateMessage

/** Persists the session. Implementations must keep it unreadable to other apps. */
interface TokenStore {
    fun load(): AuthToken?
    fun save(token: AuthToken)
    fun clear()
}

interface AuthRepository {
    val isLoggedIn: Boolean

    /** Starts a login attempt and returns the Reddit page the browser must open. */
    fun beginLogin(): AppResult<String>

    /** Finishes the attempt using the address Reddit redirected back to. */
    suspend fun completeLogin(redirectUri: String): AppResult<Unit>

    /** Revokes the session on Reddit and wipes it from the phone. */
    suspend fun logout()
}

interface AccountRepository {
    suspend fun username(): AppResult<String>
    fun forget()
}

interface MessageRepository {
    suspend fun loadInbox(after: String?): AppResult<Page<InboxItem>>
    suspend fun loadPrivateMessages(after: String?): AppResult<Page<PrivateMessage>>
}

/** Memory-only holder that lets the chat screen read what the list screen loaded. */
interface ConversationCache {
    fun replaceAll(conversations: List<Conversation>)
    fun find(key: String): Conversation?
    fun clear()
}

interface AppearanceSettings {
    fun current(): Appearance
    fun save(appearance: Appearance)
}
