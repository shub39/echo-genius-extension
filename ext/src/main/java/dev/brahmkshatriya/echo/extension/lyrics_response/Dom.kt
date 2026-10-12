package dev.brahmkshatriya.echo.extension.lyrics_response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Dom(
    val tag: String? = null,
    val children: List<JsonElement> = emptyList()
)
