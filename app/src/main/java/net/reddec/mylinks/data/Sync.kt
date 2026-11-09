package net.reddec.mylinks.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface SyncOutcome {
    data class Ok(val total: Int, val full: Boolean) : SyncOutcome
    data object NotConfigured : SyncOutcome
    data object NeedsLogin : SyncOutcome
    data class Failed(val message: String) : SyncOutcome
}

/**
 * Push-then-pull reconciliation with local-wins semantics for queued edits.
 *
 * The legacy API has no "changed since" filter, so an incremental pull walks pages until it
 * finds a page where every bookmark is already mirrored; a full pull (every [FULL_SYNC_INTERVAL])
 * walks every page and prunes rows the server dropped.
 */
class SyncEngine(private val prefs: Prefs, private val store: Store) {

    /**
     * Bookmarks first (fast, makes the library usable), then every article Shiori has text for.
     * [downloadText] is reported separately so the UI can show the library while text streams in.
     */
    suspend fun run(force: Boolean = false): SyncOutcome {
        val outcome = syncBookmarks(force)
        if (outcome is SyncOutcome.Ok && prefs.offlineText) downloadText()
        return outcome
    }

    suspend fun syncBookmarks(force: Boolean = false): SyncOutcome = withContext(Dispatchers.IO) {
        if (prefs.server.isBlank()) return@withContext SyncOutcome.NotConfigured
        if (prefs.token.isNullOrBlank()) return@withContext SyncOutcome.NeedsLogin

        val api = Api(prefs.server, prefs.token)
        val full = force || System.currentTimeMillis() - prefs.lastFullSync > FULL_SYNC_INTERVAL

        try {
            push(api)
            pull(api, full)
            prefs.lastSync = System.currentTimeMillis()
            if (full) prefs.lastFullSync = prefs.lastSync
            SyncOutcome.Ok(store.countBookmarks(), full)
        } catch (_: Api.AuthError) {
            prefs.clearSession()
            SyncOutcome.NeedsLogin
        } catch (e: Exception) {
            Log.w("MyLinks", "sync failed", e)
            SyncOutcome.Failed(e.message ?: "Sync failed")
        }
    }

    private fun push(api: Api) {
        for (entry in store.outbox()) {
            try {
                when (entry.kind) {
                    "create" -> {
                        val rowId = entry.rowId
                        val bookmark = if (rowId == null) null else store.get(rowId)
                        if (rowId == null || bookmark == null) {
                            store.dequeue(entry.seq)
                        } else {
                            // Shiori fetches the real title itself when we send an empty one.
                            val title = if (bookmark.title == bookmark.url) "" else bookmark.title
                            store.attachServer(rowId, api.create(bookmark.url, title, bookmark.tags))
                            store.dequeue(entry.seq)
                        }
                    }

                    "tag" -> {
                        val tag = entry.tag
                        val serverId = entry.rowId?.let { store.get(it) }?.serverId
                        when {
                            // Its create entry is still queued; it is replayed first next time.
                            serverId == null -> continue
                            // A nameless tag would make the server reject the whole batch.
                            tag.isNullOrBlank() -> store.dequeue(entry.seq)
                            else -> {
                                api.addTags(serverId, listOf(tag))
                                store.dequeue(entry.seq)
                            }
                        }
                    }

                    "delete" -> {
                        val serverId = entry.serverId
                        if (serverId == null) {
                            store.dequeue(entry.seq)
                        } else {
                            api.delete(listOf(serverId))
                            store.dequeue(entry.seq)
                        }
                    }

                    else -> store.dequeue(entry.seq)
                }
            } catch (e: Api.ServerError) {
                // 4xx means the server will never accept this entry, so retrying forever is wrong.
                if (e.code in 400..499) store.dequeue(entry.seq) else throw e
            }
        }
    }

    /**
     * Pulls the extracted text of *every* bookmark Shiori has a copy of, newest first, in batches.
     * It goes through Shiori (never the sites themselves) and stops quietly on the first failure,
     * leaving the rest for the next sync. [onProgress] receives the running cached-article count.
     */
    suspend fun downloadText(onProgress: suspend (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        if (prefs.token.isNullOrBlank()) return@withContext 0
        val api = Api(prefs.server, prefs.token)

        var downloaded = 0
        while (true) {
            val batch = store.rowIdsMissingContent(TEXT_BATCH)
            if (batch.isEmpty()) break

            var progressed = false
            for (rowId in batch) {
                val serverId = store.serverIdFor(rowId) ?: continue
                val text = try {
                    api.readable(serverId)
                } catch (e: Api.AuthError) {
                    break
                } catch (_: Exception) {
                    return@withContext downloaded // offline: the next sync continues where this stopped
                }
                // Saving marks the row checked even when Shiori has no text for it.
                store.saveContent(rowId, text)
                progressed = true
                downloaded++
            }
            if (!progressed) break
            onProgress(store.countWithContent())
        }
        onProgress(store.countWithContent())
        downloaded
    }

    private fun pull(api: Api, full: Boolean) {
        val pendingDeletes = store.pendingDeleteIds()
        if (full) store.seenClear()

        var page = 1
        var maxPage = 1
        while (page <= maxPage) {
            val result = api.page(page)
            maxPage = result.maxPage

            var pageFullyKnown = result.bookmarks.isNotEmpty()
            for (remote in result.bookmarks) {
                if (full) store.seenAdd(remote.serverId)
                if (remote.serverId in pendingDeletes) continue
                if (!store.needsUpdate(remote)) continue
                store.upsertRemote(remote)
                pageFullyKnown = false
            }

            if (!full && (pageFullyKnown || result.bookmarks.isEmpty())) break
            page++
        }

        if (full) store.pruneMissing()
    }

    private companion object {
        const val FULL_SYNC_INTERVAL = 12L * 60L * 60L * 1000L
        const val TEXT_BATCH = 20
    }
}
