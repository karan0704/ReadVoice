package com.readvoice.app.data.inbox

import com.readvoice.app.data.net.RedditApiClient
import com.readvoice.app.domain.AccountRepository
import com.readvoice.app.domain.ConversationCache
import com.readvoice.app.domain.MessageRepository
import com.readvoice.app.domain.model.AppError
import com.readvoice.app.domain.model.AppResult
import com.readvoice.app.domain.model.Conversation
import com.readvoice.app.domain.model.InboxItem
import com.readvoice.app.domain.model.Page
import com.readvoice.app.domain.model.PrivateMessage
import kotlinx.serialization.json.Json

/** The two read-only message listings the app uses. Nothing is marked as read. */
class RedditMessageApi(
    private val client: RedditApiClient,
    private val json: Json
) {
    suspend fun inbox(after: String?): AppResult<ListingDto> = listing("message/inbox", after)

    suspend fun privateMessages(after: String?): AppResult<ListingDto> =
        listing("message/messages", after)

    private suspend fun listing(path: String, after: String?): AppResult<ListingDto> {
        val query = buildMap {
            put("limit", PAGE_SIZE)
            put("mark", "false")
            if (after != null) put("after", after)
        }
        return when (val result = client.get(path, query)) {
            is AppResult.Failure -> result
            is AppResult.Success -> try {
                AppResult.Success(json.decodeFromString(ListingDto.serializer(), result.value))
            } catch (e: IllegalArgumentException) {
                AppResult.Failure(AppError.INVALID_RESPONSE)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = "50"
    }
}

class InboxRepository(
    private val api: RedditMessageApi,
    private val mapper: MessageMapper,
    private val account: AccountRepository
) : MessageRepository {

    /**
     * Reddit's inbox listing mixes replies with private messages, so a page
     * can be empty once the private messages are filtered out. A few more
     * pages are fetched in that case so the list never stalls on a blank page.
     */
    override suspend fun loadInbox(after: String?): AppResult<Page<InboxItem>> {
        var cursor = after
        repeat(MAX_PAGES_PER_LOAD) {
            when (val result = api.inbox(cursor)) {
                is AppResult.Failure -> return result
                is AppResult.Success -> {
                    val items = mapper.toInboxItems(result.value)
                    val next = result.value.data.after
                    if (items.isNotEmpty() || next == null) {
                        return AppResult.Success(Page(items, next))
                    }
                    cursor = next
                }
            }
        }
        return AppResult.Success(Page(emptyList(), cursor))
    }

    override suspend fun loadPrivateMessages(after: String?): AppResult<Page<PrivateMessage>> {
        val username = when (val result = account.username()) {
            is AppResult.Failure -> return result
            is AppResult.Success -> result.value
        }
        return when (val result = api.privateMessages(after)) {
            is AppResult.Failure -> result
            is AppResult.Success -> AppResult.Success(
                Page(
                    mapper.toPrivateMessages(result.value, username),
                    result.value.data.after
                )
            )
        }
    }

    private companion object {
        const val MAX_PAGES_PER_LOAD = 3
    }
}

/** Looks up the logged-in username once and keeps it in memory. */
class RedditAccountRepository(
    private val client: RedditApiClient,
    private val json: Json
) : AccountRepository {

    @Volatile
    private var cached: String? = null

    override suspend fun username(): AppResult<String> {
        cached?.let { return AppResult.Success(it) }
        return when (val result = client.get("api/v1/me")) {
            is AppResult.Failure -> result
            is AppResult.Success -> {
                val name = try {
                    json.decodeFromString(IdentityDto.serializer(), result.value).name
                } catch (e: IllegalArgumentException) {
                    ""
                }
                if (name.isEmpty()) {
                    AppResult.Failure(AppError.INVALID_RESPONSE)
                } else {
                    cached = name
                    AppResult.Success(name)
                }
            }
        }
    }

    override fun forget() {
        cached = null
    }
}

/** Conversations live in memory only; they are gone when the app closes. */
class InMemoryConversationCache : ConversationCache {

    @Volatile
    private var byKey: Map<String, Conversation> = emptyMap()

    override fun replaceAll(conversations: List<Conversation>) {
        byKey = conversations.associateBy { it.key }
    }

    override fun find(key: String): Conversation? = byKey[key]

    override fun clear() {
        byKey = emptyMap()
    }
}
