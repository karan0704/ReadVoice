package com.readvoice.app.domain

/**
 * Ends the session and drops everything held in memory about the account, so
 * nothing from one user is still around when another logs in.
 */
class LogoutUseCase(
    private val auth: AuthRepository,
    private val account: AccountRepository,
    private val conversations: ConversationCache
) {
    suspend fun run() {
        auth.logout()
        account.forget()
        conversations.clear()
    }
}
