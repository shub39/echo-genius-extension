package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.clients.ExtensionClient
import dev.brahmkshatriya.echo.common.clients.LyricsClient
import dev.brahmkshatriya.echo.common.clients.LyricsSearchClient
import dev.brahmkshatriya.echo.common.helpers.ContinuationCallback.Companion.await
import dev.brahmkshatriya.echo.common.helpers.Page
import dev.brahmkshatriya.echo.common.helpers.PagedData
import dev.brahmkshatriya.echo.common.models.Feed
import dev.brahmkshatriya.echo.common.models.Feed.Companion.toFeed
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import dev.brahmkshatriya.echo.common.settings.Setting
import dev.brahmkshatriya.echo.common.settings.SettingSwitch
import dev.brahmkshatriya.echo.common.settings.SettingTextInput
import dev.brahmkshatriya.echo.common.settings.Settings
import dev.brahmkshatriya.echo.extension.lyrics_response.LyricsResponse
import dev.brahmkshatriya.echo.extension.search_response.GeniusEntity
import dev.brahmkshatriya.echo.extension.search_response.SearchResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GeniusExtension : ExtensionClient, LyricsClient, LyricsSearchClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var setting: Settings? = null

    override suspend fun onExtensionSelected() {}

    override fun setSettings(settings: Settings) {
        setting = settings
    }

    override suspend fun getSettingItems(): List<Setting> = listOf(
        SettingSwitch(
            title = "Append Song Story & Credits",
            key = SETTING_APPEND_BIO,
            summary = "Appends Genius song meaning, background, and credits below the lyrics.",
            defaultValue = SETTING_APPEND_BIO_DEFAULT
        ),
        SettingSwitch(
            title = "Include Romanized & Translations",
            key = SETTING_INCLUDE_TRANSLATIONS,
            summary = "Adds Romanized and translated versions to search results when available.",
            defaultValue = SETTING_INCLUDE_TRANSLATIONS_DEFAULT
        ),
        SettingSwitch(
            title = "Clean Section Headers",
            key = SETTING_CLEAN_HEADERS,
            summary = "Removes section headers like [Verse], [Chorus] for clean lyrics.",
            defaultValue = SETTING_CLEAN_HEADERS_DEFAULT
        ),
        SettingTextInput(
            title = "Custom Genius API Token",
            key = SETTING_CUSTOM_TOKEN,
            summary = "Use your own Genius Client Access Token (leave empty for default).",
            defaultValue = ""
        )
    )

    override suspend fun searchTrackLyrics(
        clientId: String,
        track: Track
    ): Feed<Lyrics> = withContext(Dispatchers.IO) {
        val rawTitle = track.title.trim()
        val artistName = track.artists.firstOrNull()?.name?.trim().orEmpty()
        val cleanTitle = cleanTrackTitle(rawTitle)

        // Stage 1: Search using cleaned title + artist
        var hits = searchHits("$cleanTitle $artistName".trim())

        // Stage 2: Fallback to original title + artist if clean title produced no results
        if (hits.isEmpty() && cleanTitle != rawTitle) {
            hits = searchHits("$rawTitle $artistName".trim())
        }

        // Stage 3: Fallback to cleaned title alone
        if (hits.isEmpty() && cleanTitle.isNotBlank()) {
            hits = searchHits(cleanTitle)
        }

        val lyricsList = mutableListOf<Lyrics>()

        val includeTranslations = setting?.getBoolean(SETTING_INCLUDE_TRANSLATIONS)
            ?: SETTING_INCLUDE_TRANSLATIONS_DEFAULT

        if (hits.isNotEmpty()) {
            val topHit = hits.first().result
            lyricsList.add(
                Lyrics(
                    id = topHit.id.toString(),
                    title = topHit.title,
                    subtitle = topHit.artists
                )
            )

            // Look up translation/romanization variants for the top match
            if (includeTranslations) {
                try {
                    val songDetails = fetchSongDetails(topHit.id)
                    songDetails.translationSongs.forEach { trans ->
                        val langLabel = when (trans.language?.lowercase()) {
                            "romanization" -> "Romanized"
                            "en" -> "English Translation"
                            "es" -> "Spanish Translation"
                            "zh-hant", "zh-hans", "zh" -> "Chinese Translation"
                            "ja" -> "Japanese Translation"
                            "ko" -> "Korean Translation"
                            else -> trans.language?.replaceFirstChar { it.uppercase() } ?: "Translation"
                        }
                        lyricsList.add(
                            Lyrics(
                                id = trans.id.toString(),
                                title = "[$langLabel] ${trans.title}",
                                subtitle = "Genius Translation"
                            )
                        )
                    }
                } catch (_: Exception) {
                }
            }

            // Append remaining search hits
            hits.drop(1).forEach { hit ->
                lyricsList.add(
                    Lyrics(
                        id = hit.result.id.toString(),
                        title = hit.result.title,
                        subtitle = hit.result.artists
                    )
                )
            }
        }

        lyricsList.toFeed()
    }

    override suspend fun searchLyrics(query: String): Feed<Lyrics> = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) return@withContext emptyList<Lyrics>().toFeed()

        val pagedData = PagedData.Continuous<Lyrics> { continuation ->
            val page = continuation?.toIntOrNull() ?: 1
            val (items, hasNext) = fetchSearchPage(trimmedQuery, page)
            Page(
                data = items,
                continuation = if (hasNext) (page + 1).toString() else null
            )
        }

        pagedData.toFeed()
    }

    override suspend fun loadLyrics(lyrics: Lyrics): Lyrics = withContext(Dispatchers.IO) {
        val songId = lyrics.id.toLongOrNull() ?: return@withContext lyrics
        val song = fetchSongDetails(songId)

        // 1. Resolve raw lyrics content
        var rawLyrics = song.lyrics?.plain?.trim()

        if (rawLyrics.isNullOrBlank() && song.lyrics?.dom != null) {
            rawLyrics = extractFromDom(song.lyrics.dom).trim()
        }

        if (rawLyrics.isNullOrBlank()) {
            rawLyrics = if (song.instrumental == true) "[Instrumental]" else ""
        }

        // 2. Clean section headers if preference enabled
        val cleanHeaders = setting?.getBoolean(SETTING_CLEAN_HEADERS) ?: SETTING_CLEAN_HEADERS_DEFAULT
        if (cleanHeaders && rawLyrics.isNotBlank()) {
            rawLyrics = rawLyrics.replace(Regex("""(?m)^\[.*?\]\s*$\n?"""), "").trim()
        }

        // 3. Append song story and credits if preference enabled
        val appendBio = setting?.getBoolean(SETTING_APPEND_BIO) ?: SETTING_APPEND_BIO_DEFAULT
        val finalText = if (appendBio && rawLyrics.isNotBlank()) {
            formatLyricsWithStoryAndCredits(rawLyrics, song)
        } else {
            rawLyrics
        }

        lyrics.copy(
            lyrics = Lyrics.Simple(finalText)
        )
    }

    private fun formatLyricsWithStoryAndCredits(
        lyricsText: String,
        song: dev.brahmkshatriya.echo.extension.lyrics_response.Song
    ): String {
        val story = song.description?.plain?.trim()
        val hasStory = !story.isNullOrBlank() && story != "?" && story.length > 15

        val credits = buildList {
            song.album?.name?.takeIf { it.isNotBlank() }?.let { add("• Album: $it") }
            song.releaseDate?.takeIf { it.isNotBlank() }?.let { add("• Released: $it") }
            val writers = song.writerArtists.mapNotNull { it.name }.filter { it.isNotBlank() }
            if (writers.isNotEmpty()) add("• Written by: ${writers.joinToString(", ")}")
            val producers = song.producerArtists.mapNotNull { it.name }.filter { it.isNotBlank() }
            if (producers.isNotEmpty()) add("• Produced by: ${producers.joinToString(", ")}")
        }

        if (!hasStory && credits.isEmpty()) {
            return lyricsText
        }

        return buildString {
            append(lyricsText)
            append("\n\n")
            append("════════════════════════════════\n")
            append("ABOUT THIS SONG\n")
            append("════════════════════════════════\n")
            if (hasStory) {
                append(story)
                append("\n\n")
            }
            if (credits.isNotEmpty()) {
                append(credits.joinToString("\n"))
            }
        }.trim()
    }

    private suspend fun fetchSongDetails(
        songId: Long
    ): dev.brahmkshatriya.echo.extension.lyrics_response.Song {
        val url = "${BASE_URL}songs/$songId?text_format=plain"
        val responseBody = httpGet(url)
        val parsed: LyricsResponse = json.decodeFromString(responseBody)
        return parsed.response.song
    }

    private suspend fun searchHits(query: String): List<GeniusEntity> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "${BASE_URL}search?q=$encoded"
        return try {
            val responseBody = httpGet(url)
            val parsed: SearchResponse = json.decodeFromString(responseBody)
            parsed.response.hits
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchSearchPage(
        query: String,
        page: Int
    ): Pair<List<Lyrics>, Boolean> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "${BASE_URL}search?q=$encoded&page=$page&per_page=20"
        return try {
            val responseBody = httpGet(url)
            val parsed: SearchResponse = json.decodeFromString(responseBody)
            val hits = parsed.response.hits
            val items = hits.map {
                Lyrics(
                    id = it.result.id.toString(),
                    title = it.result.title,
                    subtitle = it.result.artists
                )
            }
            items to (hits.size >= 20)
        } catch (_: Exception) {
            emptyList<Lyrics>() to false
        }
    }

    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val token = getToken()
        val request = Request.Builder()
            .url(url)
            .addHeader(AUTH_HEADER, "Bearer $token")
            .addHeader("User-Agent", USER_AGENT)
            .build()



        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Genius API error: HTTP ${response.code} ${response.message}")
            }
            response.body?.string() ?: throw IOException("Empty response body from Genius API")
        }
    }

    private fun getToken(): String {
        val custom = setting?.getString(SETTING_CUSTOM_TOKEN)?.trim()
        return if (!custom.isNullOrBlank()) custom else DEFAULT_GENIUS_API_TOKEN
    }

    private fun extractFromDom(element: JsonElement?): String {
        if (element == null) return ""
        val sb = StringBuilder()

        fun traverse(node: JsonElement) {
            when (node) {
                is JsonPrimitive -> {
                    if (node.isString) sb.append(node.content)
                }
                is JsonArray -> {
                    node.forEach { traverse(it) }
                }
                is JsonObject -> {
                    val tag = node["tag"]?.jsonPrimitive?.contentOrNull
                    if (tag == "br") {
                        sb.append("\n")
                    } else if (tag == "p") {
                        sb.append("\n\n")
                    }
                    node["children"]?.let { traverse(it) }
                }
            }
        }

        traverse(element)
        return sb.toString().trim()
    }

    private fun cleanTrackTitle(title: String): String {
        var cleaned = title
        // Strip parenthesized / bracketed video, audio, or live metadata
        cleaned = cleaned.replace(
            Regex("""(?i)\s*[\[(](official\s*(music\s*)?video|video|audio|lyric\s*video|lyrics|visualizer|mv|hd|hq|4k|extended|bonus\s*track|live(\s*at\s*[^\])]+)?)[\])]"""),
            ""
        )
        // Strip remaster tags (both bracketed and hyphenated)
        cleaned = cleaned.replace(
            Regex("""(?i)\s*[\[(]remaster(ed)?(\s*\d+)?[\])]"""),
            ""
        )
        cleaned = cleaned.replace(
            Regex("""(?i)\s*-\s*remaster(ed)?(\s*\d+)?.*$"""),
            ""
        )
        cleaned = cleaned.replace(
            Regex("""(?i)\s*-\s*live(\s*at\s*.*)?$"""),
            ""
        )
        // Strip featured artist annotations
        cleaned = cleaned.replace(
            Regex("""(?i)\s*[\[(](feat\.?|ft\.?)\s*[^\])]+[\])]"""),
            ""
        )
        return cleaned.replace(Regex("""\s+"""), " ").trim()
    }

    private companion object {
        private const val BASE_URL = "https://api.genius.com/"
        private const val AUTH_HEADER = "Authorization"
        private const val DEFAULT_GENIUS_API_TOKEN =
            "ZTejoT_ojOEasIkT9WrMBhBQOz6eYKK5QULCMECmOhvwqjRZ6WbpamFe3geHnvp3"
        private const val USER_AGENT =
            "Genius/2.15.0 (Android 14; Mobile)"

        private const val SETTING_APPEND_BIO = "append_song_bio"


        private const val SETTING_APPEND_BIO_DEFAULT = true

        private const val SETTING_CLEAN_HEADERS = "clean_headers"
        private const val SETTING_CLEAN_HEADERS_DEFAULT = false

        private const val SETTING_INCLUDE_TRANSLATIONS = "include_translations"
        private const val SETTING_INCLUDE_TRANSLATIONS_DEFAULT = true

        private const val SETTING_CUSTOM_TOKEN = "custom_token"
    }
}
