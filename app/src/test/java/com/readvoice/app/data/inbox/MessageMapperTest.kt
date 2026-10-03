package com.readvoice.app.data.inbox

import com.readvoice.app.domain.ConversationGrouper
import com.readvoice.app.domain.model.InboxItemType
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageMapperTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }
    private val mapper = MessageMapper(json)

    private fun listing(text: String) = json.decodeFromString(ListingDto.serializer(), text)

    private val inboxJson = """
        {"kind":"Listing","data":{"after":"t1_next","children":[
          {"kind":"t1","data":{"id":"c1","name":"t1_c1","author":"dev_arjun","subject":"comment reply",
            "body":"Have you tried <b>this</b>?","subreddit":"android","type":"comment_reply",
            "created_utc":1700000000.0,"new":true,"was_comment":true,"replies":"","score":3}},
          {"kind":"t1","data":{"id":"c2","name":"t1_c2","author":"maya_k","body":"u/me look",
            "subreddit":"india","type":"username_mention","created_utc":1700000100.0,
            "new":false,"was_comment":true,"replies":""}},
          {"kind":"t1","data":{"id":"c3","name":"t1_c3","author":null,"body":"gone",
            "subreddit":"kotlin","type":"post_reply","created_utc":1700000200.0,
            "new":false,"was_comment":true}},
          {"kind":"t4","data":{"id":"m1","name":"t4_m1","author":"sam_reads","dest":"me",
            "subject":"Book swap","body":"private","created_utc":1700000300.0,
            "new":true,"was_comment":false,"replies":""}}
        ]}}
    """.trimIndent()

    private val messagesJson = """
        {"kind":"Listing","data":{"after":null,"children":[
          {"kind":"t4","data":{"id":"m1","name":"t4_m1","author":"sam_reads","dest":"Me",
            "subject":"Book swap","body":"Hi, still have the Dune copy?","created_utc":1700000000.0,
            "new":false,"was_comment":false,
            "replies":{"kind":"Listing","data":{"children":[
              {"kind":"t4","data":{"id":"m2","name":"t4_m2","author":"me","dest":"sam_reads",
                "subject":"re: Book swap","body":"Yes!","created_utc":1700000500.0,
                "new":false,"was_comment":false,"replies":""}},
              {"kind":"t4","data":{"id":"m3","name":"t4_m3","author":"sam_reads","dest":"Me",
                "subject":"re: Book swap","body":"Monday then.","created_utc":1700000900.0,
                "new":true,"was_comment":false,"replies":""}}
            ]}}}},
          {"kind":"t4","data":{"id":"m9","name":"t4_m9","author":null,"dest":"Me","subreddit":"books",
            "subject":"Welcome","body":"Read the rules.","created_utc":1600000000.0,
            "new":false,"was_comment":false,"replies":""}},
          {"kind":"t4","data":{"id":"m7","name":"t4_m7","author":"Me","dest":"#books",
            "subject":"Question","body":"Is this allowed?","created_utc":1600000500.0,
            "new":false,"was_comment":false,"replies":""}}
        ]}}
    """.trimIndent()

    @Test
    fun inboxKeepsRepliesAndMentionsAndSkipsPrivateMessages() {
        val items = mapper.toInboxItems(listing(inboxJson))

        assertEquals(listOf("t1_c1", "t1_c2", "t1_c3"), items.map { it.id })
        assertEquals(
            listOf(InboxItemType.COMMENT_REPLY, InboxItemType.MENTION, InboxItemType.POST_REPLY),
            items.map { it.type }
        )
        assertEquals("u/dev_arjun", items[0].author)
        assertEquals("r/android", items[0].subreddit)
        assertEquals(1_700_000_000_000L, items[0].createdAtMillis)
        assertTrue(items[0].isUnread)
        assertFalse(items[1].isUnread)
    }

    @Test
    fun messageTextIsKeptExactlyAsSent() {
        val items = mapper.toInboxItems(listing(inboxJson))

        assertEquals("Have you tried <b>this</b>?", items[0].body)
    }

    @Test
    fun deletedAuthorGetsAPlaceholder() {
        val items = mapper.toInboxItems(listing(inboxJson))

        assertEquals("[deleted]", items[2].author)
    }

    @Test
    fun threadIsFlattenedWithSidesAndSharedSubject() {
        val messages = mapper.toPrivateMessages(listing(messagesJson), "me")
        val thread = messages.filter { it.threadId == "t4_m1" }

        assertEquals(listOf("t4_m1", "t4_m2", "t4_m3"), thread.map { it.id })
        assertEquals(listOf(false, true, false), thread.map { it.isMine })
        assertTrue(thread.all { it.partner == "u/sam_reads" })
        assertTrue(thread.all { it.threadSubject == "Book swap" })
    }

    @Test
    fun subredditMessagesUseTheSubredditAsPartner() {
        val messages = mapper.toPrivateMessages(listing(messagesJson), "me")

        assertEquals("r/books", messages.first { it.id == "t4_m9" }.partner)
        assertEquals("r/books", messages.first { it.id == "t4_m7" }.partner)
        assertTrue(messages.first { it.id == "t4_m7" }.isMine)
    }

    @Test
    fun groupingGivesOneConversationPerPartnerNewestFirst() {
        val messages = mapper.toPrivateMessages(listing(messagesJson), "me")

        val conversations = ConversationGrouper().group(messages + messages)

        assertEquals(listOf("u/sam_reads", "r/books"), conversations.map { it.partner })
        assertEquals(listOf("t4_m1", "t4_m2", "t4_m3"), conversations[0].messages.map { it.id })
        assertEquals("Monday then.", conversations[0].lastMessage.body)
        assertTrue(conversations[0].hasUnread)
        assertEquals(listOf("t4_m9", "t4_m7"), conversations[1].messages.map { it.id })
        assertFalse(conversations[1].hasUnread)
    }
}
