package net.reddec.mylinks

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.reddec.mylinks.data.Api
import net.reddec.mylinks.data.Bookmark
import net.reddec.mylinks.data.DateFilter
import net.reddec.mylinks.data.Db
import net.reddec.mylinks.data.Prefs
import net.reddec.mylinks.data.Store
import net.reddec.mylinks.data.SyncEngine
import net.reddec.mylinks.data.SyncOutcome
import net.reddec.mylinks.data.TagCount
import net.reddec.mylinks.data.extractUrl
import net.reddec.mylinks.data.isOnline
import net.reddec.mylinks.data.normalizeUrl
import net.reddec.mylinks.work.SyncWorker

data class UiState(
    val booting: Boolean = true,
    val signedIn: Boolean = false,
    val server: String = "",
    val username: String = "",
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val dateFilter: DateFilter? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val tags: List<TagCount> = emptyList(),
    val total: Int = 0,
    val pending: Int = 0,
    val syncing: Boolean = false,
    val signingIn: Boolean = false,
    val online: Boolean = true,
    val lastSync: Long = 0L,
    val backgroundSync: Boolean = true,
    val authError: String? = null,
    val message: String? = null,
    /** Non-null while the save sheet is open; the string is the URL to pre-fill. */
    val addRequest: String? = null,
)

class MyLinksViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val store = Store(Db(application))

    var state by mutableStateOf(UiState())
        private set

    private var reloadJob: Job? = null
    private var lastAttemptAt = 0L

    init {
        val signedIn = prefs.server.isNotBlank() && !prefs.token.isNullOrBlank()
        state = state.copy(
            booting = false,
            signedIn = signedIn,
            server = prefs.server,
            username = prefs.username,
            lastSync = prefs.lastSync,
            backgroundSync = prefs.backgroundSync,
            online = isOnline(application),
        )
        reload()
        if (signedIn) {
            SyncWorker.schedule(application, prefs.backgroundSync)
            sync(manual = false)
        }
    }

    // ------------------------------------------------------------------ filters

    fun setQuery(query: String) {
        state = state.copy(query = query)
        reload(debounce = true)
    }

    fun toggleTag(tag: String) {
        val selected = state.selectedTags
        state = state.copy(selectedTags = if (tag in selected) selected - tag else selected + tag)
        reload()
    }

    fun setDateFilter(filter: DateFilter?) {
        state = state.copy(dateFilter = filter)
        reload()
    }

    fun clearFilters() {
        state = state.copy(query = "", selectedTags = emptySet(), dateFilter = null)
        reload()
    }

    // -------------------------------------------------------------- local writes

    fun saveLink(url: String, title: String, tags: List<String>) {
        val clean = normalizeUrl(url)
        if (clean.length < 8) {
            state = state.copy(message = "That does not look like a link")
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.createLocal(clean, title.trim(), tags) }
            state = state.copy(addRequest = null, message = "Saved · will sync")
            reload()
            sync(manual = false)
        }
    }

    fun addTag(rowId: Long, tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.addTag(rowId, clean) }
            reload()
            sync(manual = false)
        }
    }

    fun delete(rowId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.delete(rowId) }
            state = state.copy(message = "Deleted")
            reload()
            sync(manual = false)
        }
    }

    // -------------------------------------------------------------------- sheets

    fun openAdd(url: String = "") {
        state = state.copy(addRequest = url)
    }

    fun dismissAdd() {
        state = state.copy(addRequest = null)
    }

    fun consumeMessage() {
        state = state.copy(message = null)
    }

    fun onSharedText(text: String?) {
        if (text.isNullOrBlank()) return
        state = state.copy(addRequest = extractUrl(text).orEmpty())
    }

    // --------------------------------------------------------------------- sync

    fun sync(manual: Boolean = false) {
        if (state.syncing || !state.signedIn) return
        lastAttemptAt = System.currentTimeMillis()
        state = state.copy(syncing = true)
        viewModelScope.launch {
            val outcome = SyncEngine(prefs, store).run(force = manual)
            state = state.copy(
                syncing = false,
                online = isOnline(getApplication()),
                lastSync = prefs.lastSync,
            )
            when (outcome) {
                is SyncOutcome.Ok -> if (manual) state = state.copy(message = "Up to date")
                SyncOutcome.NeedsLogin -> state = state.copy(
                    signedIn = false,
                    authError = "Session expired — sign in again",
                )

                is SyncOutcome.Failed -> if (manual) state = state.copy(message = outcome.message)
                SyncOutcome.NotConfigured -> Unit
            }
            reload()
        }
    }

    fun onResume() {
        state = state.copy(online = isOnline(getApplication()))
        // Skip the launch-time duplicate: init() already synced a moment ago.
        val stale = System.currentTimeMillis() - lastAttemptAt > 60_000L
        if (state.signedIn && stale) sync(manual = false)
    }

    fun setBackgroundSync(enabled: Boolean) {
        prefs.backgroundSync = enabled
        state = state.copy(backgroundSync = enabled)
        SyncWorker.schedule(getApplication(), enabled)
    }

    fun syncInBackground() {
        SyncWorker.syncNow(getApplication())
    }

    // --------------------------------------------------------------------- auth

    fun signIn(server: String, username: String, password: String) {
        if (state.signingIn) return
        state = state.copy(signingIn = true, authError = null)
        viewModelScope.launch {
            try {
                val login = withContext(Dispatchers.IO) { Api.login(server, username, password) }
                prefs.saveSession(login.base, username, login.token, login.expires)
                state = state.copy(
                    signingIn = false,
                    signedIn = true,
                    server = login.base,
                    username = username,
                    authError = null,
                    lastSync = prefs.lastSync,
                )
                SyncWorker.schedule(getApplication(), prefs.backgroundSync)
                reload()
                sync(manual = true)
            } catch (e: Exception) {
                state = state.copy(signingIn = false, authError = e.message ?: "Sign in failed")
            }
        }
    }

    fun signOut() {
        prefs.clearSession()
        state = state.copy(signedIn = false, pending = 0, addRequest = null)
        SyncWorker.schedule(getApplication(), false)
        reload()
    }

    // ------------------------------------------------------------------ internals

    private data class Snapshot(
        val bookmarks: List<Bookmark>,
        val tags: List<TagCount>,
        val pending: Int,
        val total: Int,
    )

    private fun reload(debounce: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            if (debounce) delay(90)
            val current = state
            val snapshot = withContext(Dispatchers.IO) {
                // A storage failure must not take the whole UI down mid-typing.
                runCatching {
                    Snapshot(
                        bookmarks = store.list(
                            current.query,
                            current.selectedTags.toList(),
                            current.dateFilter?.from,
                            current.dateFilter?.to,
                        ),
                        tags = store.tagCounts(),
                        pending = store.countPending(),
                        total = store.countBookmarks(),
                    )
                }
            }
            snapshot.onSuccess { result ->
                state = state.copy(
                    bookmarks = result.bookmarks,
                    tags = result.tags,
                    pending = result.pending,
                    total = result.total,
                )
            }.onFailure { error ->
                Log.w("MyLinks", "could not read the library", error)
                state = state.copy(message = "Could not read the library")
            }
        }
    }
}
