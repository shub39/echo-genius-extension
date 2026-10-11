package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Artist
import dev.brahmkshatriya.echo.common.models.Feed.Companion.pagedDataOfFirst
import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class ExtensionUnitTest {

    private val extension = GeniusExtension()
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")
    private val settings = MockedSettings()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        extension.setSettings(settings)
        runBlocking {
            extension.onInitialize()
            extension.onExtensionSelected()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        mainThreadSurrogate.close()
    }

    @Test
    fun testSettings() = runBlocking {
        val items = extension.getSettingItems()
        println("Settings count: ${items.size}")
        items.forEach { println("  - ${it.title} (${it.key})") }
        assertTrue("Settings should have items", items.isNotEmpty())
    }

    @Test
    fun testSearchTrackLyricsEnglish() = runBlocking {
        val track = Track(
            id = "1",
            title = "Bohemian Rhapsody",
            artists = listOf(Artist("1", "Queen"))
        )
        val feed = extension.searchTrackLyrics("test", track)
        val items = feed.pagedDataOfFirst().loadPage(null).data
        println("Found ${items.size} lyrics matches for Bohemian Rhapsody:")
        items.take(3).forEach { println("  - ${it.title} by ${it.subtitle} (id: ${it.id})") }

        assertTrue("Search results should not be empty", items.isNotEmpty())
        assertTrue(
            "First result should be Bohemian Rhapsody",
            items.first().title.contains("Bohemian Rhapsody", ignoreCase = true)
        )
    }

    @Test
    fun testSearchTrackLyricsWithDirtyTitle() = runBlocking {
        val track = Track(
            id = "2",
            title = "Bohemian Rhapsody (Official Video) [Remastered 2011]",
            artists = listOf(Artist("1", "Queen"))
        )
        val feed = extension.searchTrackLyrics("test", track)
        val items = feed.pagedDataOfFirst().loadPage(null).data
        println("Found ${items.size} matches for dirty title:")
        items.take(3).forEach { println("  - ${it.title} (id: ${it.id})") }

        assertTrue("Dirty title should be cleaned and match", items.isNotEmpty())
        assertTrue(
            "Cleaned title should match Bohemian Rhapsody",
            items.first().title.contains("Bohemian Rhapsody", ignoreCase = true)
        )
    }

    @Test
    fun testSearchTrackLyricsWithTranslations() = runBlocking {
        val track = Track(
            id = "3",
            title = "Gurenge",
            artists = listOf(Artist("3", "LiSA"))
        )
        val feed = extension.searchTrackLyrics("test", track)
        val items = feed.pagedDataOfFirst().loadPage(null).data
        println("Found ${items.size} items for LiSA Gurenge:")
        items.take(5).forEach { println("  - ${it.title} (id: ${it.id})") }

        assertTrue("Should return matches for Gurenge", items.isNotEmpty())
        val hasTranslation = items.any {
            it.title.contains("[Romanized]", ignoreCase = true) ||
                it.title.contains("[English Translation]", ignoreCase = true)
        }
        assertTrue("Should include Romanized or English Translation for Gurenge", hasTranslation)
    }

    @Test
    fun testLoadLyricsWithStoryAndCredits() = runBlocking {
        val lyricItem = Lyrics(id = "1063", title = "Bohemian Rhapsody", subtitle = "Queen")
        val loaded = extension.loadLyrics(lyricItem)
        val simple = loaded.lyrics as? Lyrics.Simple
        assertNotNull("Lyrics should be loaded as Simple", simple)

        val text = simple!!.text
        println("Loaded lyrics sample (first 300 chars):\n${text.take(300)}...")

        assertTrue("Lyrics should contain iconic verse", text.contains("Is this the real life?"))
        assertTrue("Lyrics should contain ABOUT THIS SONG section", text.contains("ABOUT THIS SONG"))
        assertTrue("Lyrics should contain Album credit", text.contains("A Night at the Opera"))
    }

    @Test
    fun testLoadLyricsCleanHeaders() = runBlocking {
        settings.putBoolean("clean_headers", true)
        val lyricItem = Lyrics(id = "1063", title = "Bohemian Rhapsody", subtitle = "Queen")
        val loaded = extension.loadLyrics(lyricItem)
        val simple = loaded.lyrics as? Lyrics.Simple
        assertNotNull("Lyrics should be loaded as Simple", simple)

        val text = simple!!.text
        assertFalse("Section headers like [Intro] should be stripped", text.contains("[Intro]"))
        assertTrue("Lyrics body text should remain", text.contains("Is this the real life?"))
    }

    @Test
    fun testContinuousSearchPagination() = runBlocking {
        val feed = extension.searchLyrics("Coldplay")
        val pagedData = feed.pagedDataOfFirst()

        val page1 = pagedData.loadPage(null)
        println("Page 1 items count: ${page1.data.size}, continuation: ${page1.continuation}")
        assertTrue("Page 1 should have results", page1.data.isNotEmpty())
        assertNotNull("Page 1 should have continuation for next page", page1.continuation)

        val page2 = pagedData.loadPage(page1.continuation)
        println("Page 2 items count: ${page2.data.size}, continuation: ${page2.continuation}")
        assertTrue("Page 2 should have results", page2.data.isNotEmpty())
    }
}
