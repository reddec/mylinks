package net.reddec.mylinks

import net.reddec.mylinks.data.extractUrl
import net.reddec.mylinks.data.normalizeUrl
import net.reddec.mylinks.data.serverCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinksTest {

    @Test
    fun extractsUrlFromSharedText() {
        assertEquals(
            "https://example.com/a/b?c=1",
            extractUrl("A cool post https://example.com/a/b?c=1"),
        )
    }

    @Test
    fun dropsTrailingPunctuation() {
        assertEquals("https://example.com/x", extractUrl("read this: https://example.com/x."))
        assertEquals("https://example.com/x", extractUrl("(https://example.com/x)"))
    }

    @Test
    fun bareHostGetsHttps() {
        assertEquals("https://sqlite.org/fts5.html", extractUrl("sqlite.org/fts5.html"))
    }

    @Test
    fun textWithoutLinkYieldsNull() {
        assertNull(extractUrl("just a thought, no links"))
    }

    @Test
    fun candidatesTryHttpsThenHttp() {
        assertEquals(listOf("https://box:8080", "http://box:8080"), serverCandidates("box:8080"))
        assertEquals(listOf("http://box"), serverCandidates(" http://box/ "))
        assertEquals(emptyList<String>(), serverCandidates("   "))
    }

    @Test
    fun normalizeUrlAddsScheme() {
        assertEquals("https://example.com/x", normalizeUrl("example.com/x"))
        assertEquals("http://192.168.1.5:8080", normalizeUrl("http://192.168.1.5:8080"))
    }
}
