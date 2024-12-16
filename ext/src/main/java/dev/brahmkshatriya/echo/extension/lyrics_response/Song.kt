package dev.brahmkshatriya.echo.extension.lyrics_response

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val lyrics: Lyrics
)