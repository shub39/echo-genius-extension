package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
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

class GeniusExtension : ExtensionClient, LyricsClient {

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
        val searchResponse = client.newCall(searchRequest).await()

        try {
            val jsonString = searchResponse.body.string()
            val parsedResponse: SearchResponse = json.decodeFromString(jsonString)
            val songId = parsedResponse.response.hits[0].result.id

            val lyricsRequest = Request.Builder()
                .url(BASE_URL + "songs/$songId")
                .addHeader(AUTH_HEADER, "Bearer $GENIUS_API_TOKEN")
                .build()
            val lyricsResponse = client.newCall(lyricsRequest).await()
            val parsedLyrics: LyricsResponse = json.decodeFromString(lyricsResponse.body.string())
            val jsonLyrics = parsedLyrics.response.song.lyrics.dom

            val lyrics = extractLyrics(jsonLyrics)

            listOf(
                Lyrics(
                    id = track.id,
                    title = track.title,
                    subtitle = "from Genius",
                    lyrics = Lyrics.Simple(lyrics)
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            return@Single emptyList()
        }

        return@Single emptyList()
    }

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = lyrics

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

}