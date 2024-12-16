package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.LyricsSearchClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.PagedData
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

private const val BASE_URL = "https://api.genius.com/"
private const val AUTH_HEADER = "Authorization"
private const val GENIUS_API_TOKEN =
    "ZTejoT_ojOEasIkT9WrMBhBQOz6eYKK5QULCMECmOhvwqjRZ6WbpamFe3geHnvp3"

class GeniusExtension : ExtensionClient, LyricsClient, LyricsSearchClient {

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun onExtensionSelected() {}

    override val settingItems: List<Setting> = emptyList()

    private lateinit var setting: Settings

    override fun setSettings(settings: Settings) {
        setting = settings
    }

    override fun searchTrackLyrics(
        clientId: String,
        track: Track
    ): PagedData<Lyrics> = PagedData.Single {
        val searchRequest = Request.Builder()
            .url(BASE_URL + "search?q=${track.title}")
            .addHeader(AUTH_HEADER, "Bearer $GENIUS_API_TOKEN")
            .build()

        resultToLyrics(searchRequest)
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

    override fun searchLyrics(query: String): PagedData<Lyrics> = PagedData.Single {
        val searchRequest = Request.Builder()
            .url(BASE_URL + "search?q=${query}")
            .addHeader(AUTH_HEADER, "Bearer $GENIUS_API_TOKEN")
            .build()

        resultToLyrics(searchRequest)
    }

    private fun extractLyrics(domNode: Dom): String {
        val lyrics = StringBuilder()

        domNode.children.forEach { child ->
            when {
                child is JsonPrimitive && child.isString -> {
                    lyrics.append(child.content).append("\n")
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

    private suspend fun resultToLyrics(searchRequest: Request): List<Lyrics> {
        val searchResponse = client.newCall(searchRequest).await()

        try {
            val jsonString = searchResponse.body.string()
            val parsedResponse: SearchResponse = json.decodeFromString(jsonString)
            val song = parsedResponse.response.hits.firstOrNull()

            if (song != null) {
                listOf(
                    Lyrics(
                        id = song.result.id.toString(),
                        title = song.result.title,
                        subtitle = song.result.artists
                    )
                )
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        }

        return emptyList()
    }

}