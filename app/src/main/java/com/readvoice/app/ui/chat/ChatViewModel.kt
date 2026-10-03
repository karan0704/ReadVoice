package com.readvoice.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.readvoice.app.domain.ConversationCache
import com.readvoice.app.domain.model.Conversation
import com.readvoice.app.domain.model.PrivateMessage

sealed interface ChatRow {
    /** Marks where a new Reddit thread (with its own subject) starts. */
    data class Subject(val text: String) : ChatRow
    data class Bubble(val message: PrivateMessage) : ChatRow
}

/**
 * Reads one conversation from the in-memory cache and lays it out as chat
 * rows. The conversation is null when the app was restarted and the cache is
 * empty; the screen closes itself in that case.
 */
class ChatViewModel(cache: ConversationCache, conversationKey: String) : ViewModel() {

    val conversation: Conversation? = cache.find(conversationKey)

    val rows: List<ChatRow> = conversation?.let(::rowsOf).orEmpty()

    private fun rowsOf(conversation: Conversation): List<ChatRow> {
        val rows = ArrayList<ChatRow>(conversation.messages.size + 1)
        var threadId: String? = null
        for (message in conversation.messages) {
            if (message.threadId != threadId) {
                threadId = message.threadId
                rows += ChatRow.Subject(message.threadSubject)
            }
            rows += ChatRow.Bubble(message)
        }
        return rows
    }

    companion object {
        fun factory(cache: ConversationCache, key: String): ViewModelProvider.Factory =
            viewModelFactory { initializer { ChatViewModel(cache, key) } }
    }
}
