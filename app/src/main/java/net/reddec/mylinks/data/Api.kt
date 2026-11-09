package net.reddec.mylinks.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Shiori v1.8 client. Bookmarks still live on the legacy REST endpoints; auth is the v1 JWT
 * endpoint. A plain [HttpURLConnection] keeps the APK free of extra dependencies.
 */
class Api(private val base: String, private val token: String?) {

    class AuthError : IOException("Session expired")
    class ServerError(val code: Int, val body: String) : IOException("HTTP $code")

    data class Page(val maxPage: Int, val bookmarks: List<RemoteBookmark>)

    private fun call(method: String, path: String, body: String? = null): String {
        val connection = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("Accept", "application/json")
            // Shiori wraps JSON as {"ok":true,"message":...} unless asked for the plain format.
            connection.setRequestProperty("X-Shiori-Response-Format", "new")
            if (!token.isNullOrBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                val bytes = body.toByteArray()
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()

            return when {
                code == 401 -> throw AuthError()
                code !in 200..299 -> throw ServerError(code, text.take(300))
                else -> text
            }
        } finally {
            connection.disconnect()
        }
    }

    fun page(page: Int): Page {
        val root = JSONObject(call("GET", "/api/bookmarks?page=$page")).unwrapped()
        val array = root.optJSONArray("bookmarks") ?: JSONArray()
        val bookmarks = buildList {
            for (index in 0 until array.length()) add(parseBookmark(array.getJSONObject(index)))
        }
        return Page(maxPage = root.optInt("maxPage", 1).coerceAtLeast(1), bookmarks = bookmarks)
    }

    fun create(url: String, title: String, tags: List<String>): RemoteBookmark {
        val payload = JSONObject().apply {
            put("url", url)
            put("title", title)
            put("excerpt", "")
            put("async", true)
            put("tags", tagArray(tags))
        }
        return parseBookmark(JSONObject(call("POST", "/api/bookmarks", payload.toString())).unwrapped())
    }

    fun addTags(serverId: Long, tags: List<String>) {
        val payload = JSONObject().apply {
            put("ids", JSONArray().put(serverId))
            put("tags", tagArray(tags))
        }
        call("PUT", "/api/bookmarks/tags", payload.toString())
    }

    /** Shiori's extracted article text. Empty when the server never fetched the page. */
    fun readable(serverId: Long): String {
        val root = JSONObject(call("GET", "/api/v1/bookmarks/$serverId/readable")).unwrapped()
        return root.optString("content").trim()
    }

    fun delete(serverIds: List<Long>) {
        call("DELETE", "/api/bookmarks", JSONArray(serverIds).toString())
    }

    companion object {
        data class Login(val base: String, val token: String, val expires: Long)

        /**
         * Uses the address exactly as typed. Guessing https/http and retrying hid typos and made
         * failures ambiguous, so a missing scheme is reported instead.
         */
        fun login(rawServer: String, username: String, password: String): Login {
            val base = exactServer(rawServer)
                ?: throw IOException("Start the address with http:// or https://")

            var last: Exception? = null
            for (candidate in listOf(base)) {
                val api = Api(candidate, null)
                try {
                    val payload = JSONObject().apply {
                        put("username", username)
                        put("password", password)
                        // v1.8.0 only reads "remember_me" and hands out a one-hour token otherwise;
                        // older builds only read "rememberMe". Unknown fields are ignored.
                        put("rememberMe", true)
                        put("remember_me", true)
                    }
                    val json = JSONObject(api.call("POST", "/api/v1/auth/login", payload.toString())).unwrapped()
                    return parseLogin(candidate, json)
                } catch (e: ServerError) {
                    if (e.code == 400 || e.code == 401 || e.code == 403) {
                        throw IOException("Wrong username or password")
                    }
                    last = IOException("Server answered ${e.code} — is this a Shiori instance?")
                } catch (e: AuthError) {
                    throw IOException("Wrong username or password")
                } catch (e: IOException) {
                    last = e
                }
            }
            throw IOException(last?.message ?: "Could not reach the server")
        }

        /**
         * Trades a still-valid token for a fresh thirty-day one — the only renewal Shiori offers.
         * An expired token is refused with 401 ([AuthError]), so renewal must not wait for it.
         */
        fun refresh(base: String, token: String): Login = parseLogin(
            base,
            JSONObject(Api(base, token).call("POST", "/api/v1/auth/refresh")).unwrapped(),
        )

        private fun parseLogin(base: String, json: JSONObject): Login {
            val token = json.optString("token")
            if (token.isBlank()) throw IOException("Server sent no session token")
            // The JSON field is "expires"; "expiration" is what the swagger doc claims.
            val seconds = json.optLong("expires", json.optLong("expiration", 0L))
            return Login(base, token, seconds * 1000L)
        }

        /** Handles both the plain payload and Shiori's legacy `{"ok":..,"message":..}` envelope. */
        private fun JSONObject.unwrapped(): JSONObject =
            if (optBoolean("ok", false) && optJSONObject("message") != null) {
                optJSONObject("message")!!
            } else {
                this
            }

        private fun tagArray(tags: List<String>): JSONArray = JSONArray().apply {
            tags.forEach { put(JSONObject().put("name", it)) }
        }

        private fun parseBookmark(json: JSONObject): RemoteBookmark {
            val tags = json.optJSONArray("tags")?.let { array ->
                buildList {
                    for (index in 0 until array.length()) {
                        val name = array.optJSONObject(index)?.optString("name").orEmpty()
                        if (name.isNotBlank()) add(name)
                    }
                }
            }.orEmpty()

            return RemoteBookmark(
                serverId = json.optInt("id", 0).toLong(),
                url = json.optString("url"),
                title = json.optString("title"),
                excerpt = json.optString("excerpt"),
                createdAt = parseServerTime(json.optString("createdAt")),
                modifiedAt = parseServerTime(json.optString("modifiedAt")),
                tags = tags,
            )
        }
    }
}
