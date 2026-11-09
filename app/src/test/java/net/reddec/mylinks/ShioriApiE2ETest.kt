package net.reddec.mylinks

import net.reddec.mylinks.data.Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/**
 * Runs the real client against a live Shiori 1.8 instance:
 *
 * ```
 * SHIORI_DIR=/tmp/shiori SHIORI_HTTP_PORT=18080 shiori server &
 * MYLINKS_E2E_SERVER=http://127.0.0.1:18080 ./gradlew :app:testDebugUnitTest
 * ```
 *
 * Skipped when MYLINKS_E2E_SERVER is unset, so the normal suite stays hermetic.
 */
class ShioriApiE2ETest {

    private val server: String? = System.getenv("MYLINKS_E2E_SERVER")
    private val username: String = System.getenv("MYLINKS_E2E_USER") ?: "shiori"
    private val password: String = System.getenv("MYLINKS_E2E_PASSWORD") ?: "gopher"

    @Before
    fun requireServer() {
        assumeTrue("MYLINKS_E2E_SERVER not set", !server.isNullOrBlank())
    }

    @Test
    fun createTagPageAndDelete() {
        val login = Api.login(server!!, username, password)
        assertTrue("token should be issued", login.token.isNotBlank())
        assertTrue("expiry should be in the future", login.expires > System.currentTimeMillis())

        val api = Api(login.base, login.token)
        val marker = "e2e-" + System.nanoTime()
        val url = "https://example.com/$marker"

        val created = api.create(url, "MyLinks $marker", listOf("e2e"))
        assertTrue("server assigns an id", created.serverId > 0)
        assertEquals("MyLinks $marker", created.title)
        assertTrue("timestamps must parse", created.createdAt > 0)
        assertEquals(listOf("e2e"), created.tags)

        api.addTags(created.serverId, listOf("stage-$marker"))

        val listed = api.page(1).bookmarks.firstOrNull { it.serverId == created.serverId }
        assertNotNull("created bookmark must be on the first page", listed)
        assertEquals(listOf("e2e", "stage-$marker"), listed!!.tags.sorted())
        assertEquals(url, listed.url)
        assertTrue("modifiedAt must parse", listed.modifiedAt > 0)

        api.delete(listOf(created.serverId))
        val stillThere = api.page(1).bookmarks.any { it.serverId == created.serverId }
        assertTrue("deleted bookmark must be gone", !stillThere)
    }

    @Test
    fun wrongPasswordIsRejected() {
        try {
            Api.login(server!!, username, "definitely-not-the-password")
            fail("login should have failed")
        } catch (e: IOException) {
            assertTrue("message was: ${e.message}", e.message!!.contains("Wrong username or password"))
        }
    }

    @Test
    fun exactAddressLogsInAndKeepsTheScheme() {
        val login = Api.login(server!!, username, password)
        assertEquals(server!!.trimEnd('/'), login.base)
        assertTrue(login.token.isNotBlank())
    }

    @Test
    fun bareAddressIsRejectedWithAHint() {
        // No https/http guessing any more: an incomplete address must fail loudly.
        val bare = server!!.removePrefix("http://").removePrefix("https://").trimEnd('/')
        try {
            Api.login(bare, username, password)
            fail("a scheme-less address should not be guessed")
        } catch (e: IOException) {
            assertTrue("message was: ${e.message}", e.message!!.contains("http://"))
        }
    }
}
