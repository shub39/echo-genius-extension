package dev.brahmkshatriya.echo.extension.lyrics_response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: Long? = null,
    val title: String? = null,
    @SerialName("artist_names") val artistNames: String? = null,
    val album: AlbumInfo? = null,
    @SerialName("release_date_for_display") val releaseDate: String? = null,
    val instrumental: Boolean? = false,
    val lyrics: Lyrics? = null,
    val description: DescriptionPayload? = null,
    @SerialName("producer_artists") val producerArtists: List<ArtistInfo> = emptyList(),
    @SerialName("writer_artists") val writerArtists: List<ArtistInfo> = emptyList(),
    @SerialName("translation_songs") val translationSongs: List<TranslationSong> = emptyList()
)

@Serializable
data class AlbumInfo(
    val name: String? = null
)

@Serializable
data class ArtistInfo(
    val name: String? = null
)

@Serializable
data class DescriptionPayload(
    val plain: String? = null
)

@Serializable
data class TranslationSong(
    val id: Long,
    val title: String,
    val language: String? = null,
    val path: String? = null
)
