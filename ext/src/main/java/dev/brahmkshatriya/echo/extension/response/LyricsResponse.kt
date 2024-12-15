package dev.brahmkshatriya.echo.extension.response

import kotlinx.serialization.Serializable

@Serializable
data class LyricsResponse(
    val response: Response,
)