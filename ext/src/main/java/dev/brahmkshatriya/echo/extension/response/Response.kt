package dev.brahmkshatriya.echo.extension.response

import kotlinx.serialization.Serializable

@Serializable
data class Response(
    val hits: List<GeniusEntity>
)
