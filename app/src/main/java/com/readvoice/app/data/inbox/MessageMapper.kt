package com.readvoice.app.data.inbox

import com.readvoice.app.domain.model.InboxItem
import com.readvoice.app.domain.model.InboxItemType
import com.readvoice.app.domain.model.PrivateMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Converts Reddit's message listings into the app's own models. */
class MessageMapper(private val json: Json) {

    /** Replies and mentions only; private messages in the same listing are skipped. */
    fun toInboxItems(listing: ListingDto): List<InboxItem> =
        listing.data.children
            .map { it.data }
            .filter { it.wasComment }
            .map { dto ->
                InboxItem(
                    id = dto.name.ifEmpty { dto.id },
                    author = dto.author?.let { "u/$it" } ?: DELETED,
                    type = typeOf(dto.type),
                    subreddit = dto.subreddit?.let { "r/$it" },
                    body = dto.body.orEmpty(),
                    createdAtMillis = millis(dto.createdUtc),
                    isUnread = dto.isNew
                )
            }

    /**
     * Flattens each thread (its first message plus the replies nested under
     * it) into single messages, and works out who the other person is from
     * the logged-in user's point of view.
     */
    fun toPrivateMessages(listing: ListingDto, username: String): List<PrivateMessage> =
        listing.data.children
            .map { it.data }
            .filter { !it.wasComment }
            .flatMap { root ->
                (listOf(root) + repliesOf(root)).map { dto ->
                    val isMine = dto.author.equals(username, ignoreCase = true)
                    PrivateMessage(
                        id = dto.name.ifEmpty { dto.id },
                        partner = partnerOf(dto, isMine),
                        threadId = root.name.ifEmpty { root.id },
                        threadSubject = root.subject.orEmpty(),
                        body = dto.body.orEmpty(),
                        createdAtMillis = millis(dto.createdUtc),
                        isMine = isMine,
                        isUnread = dto.isNew
                    )
                }
            }

    private fun repliesOf(root: MessageDto): List<MessageDto> {
        val replies = root.replies as? JsonObject ?: return emptyList()
        return try {
            json.decodeFromJsonElement(ListingDto.serializer(), replies).data.children.map { it.data }
        } catch (e: IllegalArgumentException) {
            emptyList()
        }
    }

    private fun partnerOf(dto: MessageDto, isMine: Boolean): String {
        val author = dto.author
        val subreddit = dto.subreddit
        return when {
            isMine -> recipientName(dto.dest)
            author != null -> "u/$author"
            subreddit != null -> "r/$subreddit"
            else -> DELETED
        }
    }

    /** Reddit writes a subreddit recipient as "#name". */
    private fun recipientName(dest: String?): String = when {
        dest.isNullOrEmpty() -> DELETED
        dest.startsWith("#") -> "r/" + dest.drop(1)
        else -> "u/$dest"
    }

    private fun typeOf(type: String?): InboxItemType = when (type) {
        "comment_reply" -> InboxItemType.COMMENT_REPLY
        "post_reply" -> InboxItemType.POST_REPLY
        "username_mention" -> InboxItemType.MENTION
        else -> InboxItemType.OTHER
    }

    private fun millis(createdUtc: Double): Long = (createdUtc * 1000).toLong()

    private companion object {
        const val DELETED = "[deleted]"
    }
}
