package com.readvoice.app.domain.model

enum class InboxItemType { COMMENT_REPLY, POST_REPLY, MENTION, OTHER }

/** A reply or mention shown on the Inbox tab. */
data class InboxItem(
    val id: String,
    val author: String,
    val type: InboxItemType,
    val subreddit: String?,
    val body: String,
    val createdAtMillis: Long,
    val isUnread: Boolean
)

/**
 * One private message. [partner] is the other side of the conversation,
 * already prefixed ("u/name" or "r/subreddit"). [threadId] and
 * [threadSubject] belong to the first message of the Reddit thread, so every
 * reply in a thread shares them.
 */
data class PrivateMessage(
    val id: String,
    val partner: String,
    val threadId: String,
    val threadSubject: String,
    val body: String,
    val createdAtMillis: Long,
    val isMine: Boolean,
    val isUnread: Boolean
)

/** All private messages exchanged with one partner, oldest first. */
data class Conversation(
    val partner: String,
    val messages: List<PrivateMessage>
) {
    val key: String get() = keyFor(partner)
    val lastMessage: PrivateMessage get() = messages.last()
    val hasUnread: Boolean get() = messages.any { it.isUnread && !it.isMine }

    companion object {
        fun keyFor(partner: String): String = partner.lowercase()
    }
}

/** One page of a Reddit listing; [nextCursor] is null on the last page. */
data class Page<T>(
    val items: List<T>,
    val nextCursor: String?
)
