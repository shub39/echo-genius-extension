package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.models.Lyrics
import dev.brahmkshatriya.echo.common.models.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(DelicateCoroutinesApi::class)
@ExperimentalCoroutinesApi
class ExtensionUnitTest {
    private val extension = GeniusExtension()

    // Test Setup
    private val mainThreadSurrogate = newSingleThreadContext("UI thread")
    @Before
    fun setUp() {
        Dispatchers.setMain(mainThreadSurrogate)
        extension.setSettings(MockedSettings())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain() // reset the main dispatcher to the original Main dispatcher
        mainThreadSurrogate.close()
    }

    private fun testIn(title: String, block: suspend CoroutineScope.() -> Unit) = runBlocking {
        println("\n-- $title --")
        block.invoke(this)
        println("\n")
    }

    // Actual Tests
    @Test
    fun testLyrics() = testIn("Testing Lyrics") {
        val searchResult = extension.searchTrackLyrics(
            clientId = "id",
            track = Track("0", "Satan")
        )

        searchResult.loadAll().forEach { p0 ->
            println(p0.title)
            println(p0.id)
        }
    }

    @Test
    fun getLyrics() = testIn("Getting lyrics") {
        val lyrics = extension.loadLyrics(Lyrics(id = "3836182", title = "Satan in the wait"))

        println(lyrics.lyrics)
    }

}