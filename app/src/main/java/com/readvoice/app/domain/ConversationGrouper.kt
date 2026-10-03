package com.readvoice.app.domain

import com.readvoice.app.domain.model.Conversation
import com.readvoice.app.domain.model.PrivateMessage

/**
 * Turns a flat list of private messages into one conversation per partner.
 * Messages are ordered oldest first inside a conversation, and conversations
 * are ordered by their most recent message.
 */
class ConversationGrouper {

    fun group(messages: List<PrivateMessage>): List<Conversation> =
        messages
            .distinctBy { it.id }
            .groupBy { Conversation.keyFor(it.partner) }
            .values
            .map { group ->
                val ordered = group.sortedBy { it.createdAtMillis }
                Conversation(partner = ordered.last().partner, messages = ordered)
            }
            .sortedByDescending { it.lastMessage.createdAtMillis }
}
