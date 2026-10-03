package com.readvoice.app.data.inbox

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Shape of Reddit's /message listings. Unknown fields are ignored by the parser. */
@Serializable
data class ListingDto(val data: ListingDataDto = ListingDataDto())

@Serializable
data class ListingDataDto(
    val after: String? = null,
    val children: List<ThingDto> = emptyList()
)

@Serializable
data class ThingDto(val data: MessageDto)

@Serializable
data class MessageDto(
    val id: String = "",
    val name: String = "",
    val author: String? = null,
    val dest: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val subreddit: String? = null,
    val type: String? = null,
    @SerialName("created_utc") val createdUtc: Double = 0.0,
    @SerialName("new") val isNew: Boolean = false,
    @SerialName("was_comment") val wasComment: Boolean = false,
    /** Reddit sends an empty string when there are no replies, and a listing otherwise. */
    val replies: JsonElement? = null
)

@Serializable
data class IdentityDto(val name: String = "")
