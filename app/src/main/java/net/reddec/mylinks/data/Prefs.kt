package net.reddec.mylinks.data

import android.content.Context

/** Small typed wrapper over SharedPreferences (server + session only). */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("mylinks", Context.MODE_PRIVATE)

    var server: String
        get() = sp.getString("server", "").orEmpty()
        set(value) = sp.edit().putString("server", value).apply()

    var username: String
        get() = sp.getString("username", "").orEmpty()
        set(value) = sp.edit().putString("username", value).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(value) = sp.edit().putString("token", value).apply()

    var tokenExpires: Long
        get() = sp.getLong("token_expires", 0L)
        set(value) = sp.edit().putLong("token_expires", value).apply()

    var lastSync: Long
        get() = sp.getLong("last_sync", 0L)
        set(value) = sp.edit().putLong("last_sync", value).apply()

    var lastFullSync: Long
        get() = sp.getLong("last_full_sync", 0L)
        set(value) = sp.edit().putLong("last_full_sync", value).apply()

    /** Keep article text on the phone so links can be read offline. */
    var offlineText: Boolean
        get() = sp.getBoolean("offline_text", true)
        set(value) = sp.edit().putBoolean("offline_text", value).apply()

    var backgroundSync: Boolean
        get() = sp.getBoolean("background_sync", true)
        set(value) = sp.edit().putBoolean("background_sync", value).apply()

    /** Recently used addresses, newest first — offered as suggestions on the sign-in screen. */
    fun knownServers(): List<String> =
        sp.getString("known_servers", "").orEmpty().split("\n").filter { it.isNotBlank() }

    fun rememberServer(server: String) {
        val updated = (listOf(server) + knownServers().filter { it != server }).take(5)
        sp.edit().putString("known_servers", updated.joinToString("\n")).apply()
    }

    fun saveSession(server: String, username: String, token: String, expires: Long) {
        sp.edit()
            .putString("server", server)
            .putString("username", username)
            .putString("token", token)
            .putLong("token_expires", expires)
            .apply()
    }

    fun clearSession() {
        sp.edit().remove("token").remove("token_expires").apply()
    }
}
