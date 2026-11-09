package net.reddec.mylinks

import net.reddec.mylinks.data.extractUrl
import net.reddec.mylinks.data.normalizeUrl
import net.reddec.mylinks.data.exactServer
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
    fun exactServerKeepsWhatWasTyped() {
        assertEquals("http://box:8080", exactServer(" http://box:8080/ "))
        assertEquals("https://shiori.example.com", exactServer("https://shiori.example.com"))
        assertEquals("http://10.0.2.2:18080", exactServer("http://10.0.2.2:18080"))
    }

    @Test
    fun exactServerRejectsMissingScheme() {
        // No guessing between https and http: an incomplete address is reported instead.
        assertNull(exactServer("box:8080"))
        assertNull(exactServer("10.0.2.2:18080"))
        assertNull(exactServer("shiori.example.com"))
        assertNull(exactServer("https://"))
        assertNull(exactServer("   "))
    }

    @Test
    fun normalizeUrlAddsScheme() {
        assertEquals("https://example.com/x", normalizeUrl("example.com/x"))
        assertEquals("http://192.168.1.5:8080", normalizeUrl("http://192.168.1.5:8080"))
    }
}
