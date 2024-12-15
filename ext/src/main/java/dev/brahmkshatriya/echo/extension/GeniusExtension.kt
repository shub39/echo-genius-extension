package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.response.LyricsResponse
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

private const val BASE_URL = "https://api.genius.com/"
private const val AUTH_HEADER = "Authorization"
private const val GENIUS_API_TOKEN =
    "ZTejoT_ojOEasIkT9WrMBhBQOz6eYKK5QULCMECmOhvwqjRZ6WbpamFe3geHnvp3"

class GeniusExtension : ExtensionClient, LyricsClient {

    private val client = OkHttpClient()

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
            val json = Json { ignoreUnknownKeys = true }
            val jsonString = searchResponse.body.string()
            println(jsonString)
            val parsedResponse: LyricsResponse = json.decodeFromString(jsonString)
            val songId = parsedResponse.response.hits[0].result.id

            val lyricsRequest = Request.Builder()
                .url(BASE_URL + "songs/$songId")
                .addHeader(AUTH_HEADER, "Bearer $GENIUS_API_TOKEN")
                .build()
            val lyricsResponse = client.newCall(lyricsRequest).await()

            println(lyricsResponse.body.string())

        } catch (e: Exception) {
            e.printStackTrace()

            return@Single emptyList()
        }

        return@Single emptyList()
    }

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = lyrics

    private fun formatGeniusLyrics(rawLyrics: String): String {
        return rawLyrics.lines()
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .replace("[", "\n[")
            .removePrefix("\n")
    }
}