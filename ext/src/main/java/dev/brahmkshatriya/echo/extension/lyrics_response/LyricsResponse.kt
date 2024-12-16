package dev.brahmkshatriya.echo.extension.lyrics_response

import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponse(
    val response: Response
)
