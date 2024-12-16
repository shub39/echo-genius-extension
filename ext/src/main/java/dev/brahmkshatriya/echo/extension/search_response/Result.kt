package dev.brahmkshatriya.echo.extension.search_response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Result(
    val id: Long,
    val title: String,
    @SerialName("artist_names") val artists: String,
    val url: String
)
