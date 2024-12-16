package dev.brahmkshatriya.echo.extension.search_response

import kotlinx.serialization.Serializable

@Serializable
data class SearchResponse(
    val response: Response,
)