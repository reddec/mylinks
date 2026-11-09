package net.reddec.mylinks.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun isOnline(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private val SCHEME_URL = Regex("(?i)https?://[^\\s<>\"']+")
private val BARE_HOST = Regex("(?i)\\b[a-z0-9][a-z0-9-]*(?:\\.[a-z0-9-]+)*\\.[a-z]{2,}(?:/\\S*)?")

/** Pulls the most likely link out of shared text, e.g. "Some title https://example.com/x". */
fun extractUrl(text: String): String? {
    SCHEME_URL.find(text)?.value?.let { return it.trimEnd('.', ',', ';', ')', ']', '}', '"', '\'', '!') }
    BARE_HOST.find(text)?.value?.let { return "https://" + it.trimEnd('.', ',', ')', ']', '"', '\'') }
    return null
}

fun normalizeUrl(input: String): String {
    val trimmed = input.trim()
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}

/** The address exactly as entered, or null when the scheme is missing. */
fun exactServer(raw: String): String? {
    val trimmed = raw.trim().trimEnd('/')
    if (trimmed.isEmpty()) return null
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
    val host = trimmed.substringAfter("://")
    return if (host.isBlank()) null else trimmed
}
