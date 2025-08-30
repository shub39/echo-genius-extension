package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.LyricsSearchClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.lyrics_response.Dom
import dev.brahmkshatriya.echo.extension.lyrics_response.LyricsResponse
import dev.brahmkshatriya.echo.extension.search_response.SearchResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

class GeniusExtension : ExtensionClient, LyricsClient, LyricsSearchClient {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun onExtensionSelected() {}

    private lateinit var setting: Settings

    override suspend fun getSettingItems(): List<Setting> = emptyList()

    override fun setSettings(settings: Settings) {
        setting = settings
    }

    override suspend fun searchTrackLyrics(
        clientId: String,
        track: Track
    ): Feed<Lyrics> {
        val searchQuery = "${track.title} ${track.artists.firstOrNull()?.name ?: ""}".trim()
        val searchRequest = Request.Builder()
            .url(BASE_URL + "search?q=$searchQuery")
            .addHeader(AUTH_HEADER, "Bearer $GENIUS_API_TOKEN")
            .build()

        return resultToLyrics(searchRequest)
    }

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics {
        val lyricsRequest = Request.Builder()
            .url(BASE_URL + "songs/${lyrics.id}")
            .addHeader(AUTH_HEADER, "Bearer $GENIUS_API_TOKEN")
            .build()
        val lyricsResponse = client.newCall(lyricsRequest).await()
        val parsedLyrics: LyricsResponse = json.decodeFromString(lyricsResponse.body.string())
        val jsonLyrics = parsedLyrics.response.song.lyrics.dom
        val extractedLyrics = extractLyrics(jsonLyrics)

        return lyrics.copy(
            lyrics = Lyrics.Simple(extractedLyrics)
        )
    }

    private fun extractLyrics(domNode: Dom): String {
        val lyrics = StringBuilder()

        domNode.children.forEach { child ->
            when {
                child is JsonPrimitive && child.isString -> {
                    lyrics.append(child.content)
                }

                child is JsonObject -> {
                    val tag = child["tag"]?.jsonPrimitive?.content

                    if (tag == "br") {
                        lyrics.append("\n")
                    } else if (tag != null) {
                        val nestedNode = json.decodeFromJsonElement<Dom>(child)
                        lyrics.append(extractLyrics(nestedNode))
                    }
                }
            }
        }

        return lyrics.toString().trim()
    }

    private suspend inline fun resultToLyrics(searchRequest: Request): Feed<Lyrics> {
        val searchResponse = client.newCall(searchRequest).await()

        val jsonString = searchResponse.body.string()
        val parsedResponse: SearchResponse = json.decodeFromString(jsonString)
        val hits = parsedResponse.response.hits

        return hits.map {
            Lyrics(
                id = it.result.id.toString(),
                title = it.result.title,
                subtitle = it.result.artists
            )
        }.toFeed()
    }

    override suspend fun searchLyrics(query: String): Feed<Lyrics> {
        val searchRequest = Request.Builder()
            .url(BASE_URL + "search?q=${query}")
            .addHeader(AUTH_HEADER, "Bearer $GENIUS_API_TOKEN")
            .build()

        return resultToLyrics(searchRequest)
    }

    private companion object {
        private const val BASE_URL = "https://api.genius.com/"
        private const val AUTH_HEADER = "Authorization"
        private const val GENIUS_API_TOKEN =
            "ZTejoT_ojOEasIkT9WrMBhBQOz6eYKK5QULCMECmOhvwqjRZ6WbpamFe3geHnvp3"
    }
}