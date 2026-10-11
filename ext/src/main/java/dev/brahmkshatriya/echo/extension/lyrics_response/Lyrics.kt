package dev.brahmkshatriya.echo.extension.lyrics_response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Lyrics(
    val plain: String? = null,
    val dom: JsonElement? = null
)
