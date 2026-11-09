package net.reddec.mylinks.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

private const val TAG_SEP = "\u001f"
private val WHITESPACE = Regex("\\s+")

/**
 * All local state lives here: the bookmark mirror, its tags, and the outbox of pending
 * mutations. The UI never talks to the network; sync reconciles this store with the server.
 */
class Store(private val helper: Db) {

    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ------------------------------------------------------------------ reads

    fun countBookmarks(): Int = db.rawQuery("SELECT COUNT(*) FROM bookmarks", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    fun countPending(): Int = db.rawQuery("SELECT COUNT(*) FROM outbox", null).use { c ->
        if (c.moveToFirst()) c.getInt(0) else 0
    }

    /**
     * Offline search: every whitespace-separated term must appear in title, url, excerpt or a
     * tag. [tags] are AND-ed, [from] inclusive and [to] exclusive, both on the added date.
     */
    fun list(query: String, tags: List<String>, from: Long?, to: Long?): List<Bookmark> {
        val sql = StringBuilder(
            """
            SELECT b.row_id, b.server_id, b.url, b.title, b.excerpt, b.created_at, b.modified_at,
                   (SELECT group_concat(tag, char(31)) FROM (
                        SELECT tag FROM bookmark_tags x WHERE x.bookmark_row = b.row_id ORDER BY tag)) AS tag_list
            FROM bookmarks b
            WHERE 1 = 1
            """.trimIndent()
        )
        val args = ArrayList<String>()

        for (term in query.trim().split(WHITESPACE).filter { it.isNotEmpty() }) {
            val like = "%" + escapeLike(term) + "%"
            sql.append(
                " AND (b.title LIKE ? ESCAPE '\\' OR b.url LIKE ? ESCAPE '\\' OR b.excerpt LIKE ? ESCAPE '\\'" +
                    " OR EXISTS(SELECT 1 FROM bookmark_tags tg WHERE tg.bookmark_row = b.row_id AND tg.tag LIKE ? ESCAPE '\\'))"
            )
            repeat(4) { args.add(like) }
        }
        for (tag in tags) {
            sql.append(" AND EXISTS(SELECT 1 FROM bookmark_tags tf WHERE tf.bookmark_row = b.row_id AND tf.tag = ?)")
            args.add(tag)
        }
        from?.let { sql.append(" AND b.created_at >= ?"); args.add(it.toString()) }
        to?.let { sql.append(" AND b.created_at < ?"); args.add(it.toString()) }
        sql.append(" ORDER BY b.created_at DESC, b.row_id DESC")

        return db.rawQuery(sql.toString(), args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(c.toBookmark()) }
        }
    }

    fun get(rowId: Long): Bookmark? = queryOne("b.row_id = ?", arrayOf(rowId.toString()))

    fun tagCounts(limit: Int = 80): List<TagCount> = db.rawQuery(
        "SELECT tag, COUNT(*) AS c FROM bookmark_tags GROUP BY tag ORDER BY c DESC, tag ASC LIMIT ?",
        arrayOf(limit.toString()),
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(TagCount(c.getString(0), c.getInt(1)))
        }
    }

    fun tagsFor(rowId: Long): List<String> = db.rawQuery(
        "SELECT tag FROM bookmark_tags WHERE bookmark_row = ? ORDER BY tag",
        arrayOf(rowId.toString()),
    ).use { c ->
        buildList { while (c.moveToNext()) add(c.getString(0)) }
    }

    /**
     * True when the mirror should take the server's copy.
     *
     * Comparing timestamps alone is not enough: Shiori reports second-resolution times and
     * rewrites a bookmark right after fetching its metadata, so a same-second change would look
     * "already synced", and tag edits made server-side do not always bump `modifiedAt`. Title,
     * url and the tag set are compared too, which makes the mirror converge exactly.
     */
    fun needsUpdate(remote: RemoteBookmark): Boolean = db.rawQuery(
        """
        SELECT b.modified_at, b.title, b.url,
               (SELECT group_concat(tag, char(31)) FROM (
                    SELECT tag FROM bookmark_tags x WHERE x.bookmark_row = b.row_id ORDER BY tag)) AS tag_list
        FROM bookmarks b WHERE b.server_id = ? LIMIT 1
        """.trimIndent(),
        arrayOf(remote.serverId.toString()),
    ).use { c ->
        if (!c.moveToFirst()) return true
        val localModifiedAt = c.getLong(0)
        val localTitle = c.getString(1).orEmpty()
        val localUrl = c.getString(2).orEmpty()
        val localTags = c.getString(3).orEmpty()

        val remoteTitle = remote.title.ifBlank { remote.url }
        val remoteTags = remote.tags.sorted().joinToString(TAG_SEP)

        remote.modifiedAt != localModifiedAt ||
            localTitle != remoteTitle ||
            localUrl != remote.url ||
            localTags != remoteTags
    }

    fun pendingDeleteIds(): Set<Long> = db.rawQuery(
        "SELECT server_id FROM outbox WHERE kind = 'delete' AND server_id IS NOT NULL",
        null,
    ).use { c -> buildSet { while (c.moveToNext()) add(c.getLong(0)) } }

    fun outbox(): List<OutboxEntry> = db.rawQuery(
        "SELECT seq, kind, row_id, server_id, tag FROM outbox ORDER BY seq",
        null,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    OutboxEntry(
                        seq = c.getLong(0),
                        kind = c.getString(1),
                        rowId = if (c.isNull(2)) null else c.getLong(2),
                        serverId = if (c.isNull(3)) null else c.getLong(3),
                        tag = if (c.isNull(4)) null else c.getString(4),
                    )
                )
            }
        }
    }

    // -------------------------------------------------------------- local edits

    fun createLocal(url: String, title: String, tags: List<String>): Long {
        db.beginTransaction()
        try {
            val now = System.currentTimeMillis()
            val values = ContentValues().apply {
                put("url", url)
                put("title", title.ifBlank { url })
                put("excerpt", "")
                put("created_at", now)
                put("modified_at", now)
            }
            val rowId = db.insertOrThrow("bookmarks", null, values)
            insertTagsLocked(rowId, tags)
            enqueueLocked(kind = "create", rowId = rowId)
            db.setTransactionSuccessful()
            return rowId
        } finally {
            db.endTransaction()
        }
    }

    fun addTag(rowId: Long, tag: String) {
        val clean = tag.trim()
        if (clean.isEmpty()) return
        db.beginTransaction()
        try {
            insertTagsLocked(rowId, listOf(clean))
            enqueueLocked(kind = "tag", rowId = rowId, tag = clean)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Hides the row immediately; the server delete is queued when the row is known remotely. */
    fun delete(rowId: Long) {
        db.beginTransaction()
        try {
            val serverId = serverIdLocked(rowId)
            db.delete("bookmark_tags", "bookmark_row = ?", arrayOf(rowId.toString()))
            db.delete("bookmarks", "row_id = ?", arrayOf(rowId.toString()))
            db.delete("outbox", "row_id = ? AND kind IN ('create', 'tag')", arrayOf(rowId.toString()))
            if (serverId != null) enqueueLocked(kind = "delete", serverId = serverId)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ----------------------------------------------------------- remote writes

    fun upsertRemote(remote: RemoteBookmark) {
        db.beginTransaction()
        try {
            val existing = rowIdForServerLocked(remote.serverId)
            val values = ContentValues().apply {
                put("server_id", remote.serverId)
                put("url", remote.url)
                put("title", remote.title.ifBlank { remote.url })
                put("excerpt", remote.excerpt)
                put("created_at", remote.createdAt)
                put("modified_at", remote.modifiedAt)
            }
            val rowId = if (existing == null) {
                db.insertOrThrow("bookmarks", null, values)
            } else {
                db.update("bookmarks", values, "row_id = ?", arrayOf(existing.toString()))
                existing
            }
            replaceTagsLocked(rowId, remote.tags)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Binds a locally-created row to the bookmark the server just returned. */
    fun attachServer(rowId: Long, remote: RemoteBookmark) {
        db.beginTransaction()
        try {
            val clash = rowIdForServerLocked(remote.serverId)
            if (clash != null && clash != rowId) {
                db.delete("bookmark_tags", "bookmark_row = ?", arrayOf(clash.toString()))
                db.delete("bookmarks", "row_id = ?", arrayOf(clash.toString()))
            }
            val values = ContentValues().apply {
                put("server_id", remote.serverId)
                put("url", remote.url)
                put("title", remote.title.ifBlank { remote.url })
                put("excerpt", remote.excerpt)
                put("created_at", remote.createdAt)
                put("modified_at", remote.modifiedAt)
            }
            db.update("bookmarks", values, "row_id = ?", arrayOf(rowId.toString()))
            if (remote.tags.isNotEmpty()) replaceTagsLocked(rowId, remote.tags)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun dequeue(seq: Long) {
        db.delete("outbox", "seq = ?", arrayOf(seq.toString()))
    }

    // ------------------------------------------------------------ full-sync sweep

    fun seenClear() = db.execSQL("DELETE FROM sync_seen")

    fun seenAdd(serverId: Long) = db.execSQL("INSERT OR IGNORE INTO sync_seen(server_id) VALUES(?)", arrayOf(serverId))

    /** Drops every mirrored row the server no longer has, unless we have a delete queued for it. */
    fun pruneMissing() {
        db.beginTransaction()
        try {
            db.execSQL(
                """
                DELETE FROM bookmarks
                WHERE server_id IS NOT NULL
                  AND server_id NOT IN (SELECT server_id FROM sync_seen)
                  AND server_id NOT IN (SELECT server_id FROM outbox WHERE kind = 'delete' AND server_id IS NOT NULL)
                """.trimIndent()
            )
            db.execSQL("DELETE FROM bookmark_tags WHERE bookmark_row NOT IN (SELECT row_id FROM bookmarks)")
            db.execSQL("DELETE FROM sync_seen")
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun queryOne(where: String, args: Array<String>): Bookmark? {
        val sql =
            """
            SELECT b.row_id, b.server_id, b.url, b.title, b.excerpt, b.created_at, b.modified_at,
                   (SELECT group_concat(tag, char(31)) FROM (
                        SELECT tag FROM bookmark_tags x WHERE x.bookmark_row = b.row_id ORDER BY tag)) AS tag_list
            FROM bookmarks b WHERE $where LIMIT 1
            """.trimIndent()
        return db.rawQuery(sql, args).use { if (it.moveToFirst()) it.toBookmark() else null }
    }

    private fun Cursor.toBookmark(): Bookmark = Bookmark(
        rowId = getLong(0),
        serverId = if (isNull(1)) null else getLong(1),
        url = getString(2).orEmpty(),
        title = getString(3).orEmpty(),
        excerpt = getString(4).orEmpty(),
        createdAt = getLong(5),
        modifiedAt = getLong(6),
        tags = getString(7).orEmpty().split(TAG_SEP).filter { it.isNotBlank() },
    )

    private fun rowIdForServerLocked(serverId: Long): Long? = db.rawQuery(
        "SELECT row_id FROM bookmarks WHERE server_id = ?",
        arrayOf(serverId.toString()),
    ).use { if (it.moveToFirst()) it.getLong(0) else null }

    private fun serverIdLocked(rowId: Long): Long? = db.rawQuery(
        "SELECT server_id FROM bookmarks WHERE row_id = ?",
        arrayOf(rowId.toString()),
    ).use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null }

    private fun enqueueLocked(kind: String, rowId: Long? = null, serverId: Long? = null, tag: String? = null) {
        db.insertOrThrow(
            "outbox",
            null,
            ContentValues().apply {
                put("kind", kind)
                rowId?.let { put("row_id", it) }
                serverId?.let { put("server_id", it) }
                tag?.let { put("tag", it) }
            },
        )
    }

    private fun insertTagsLocked(rowId: Long, tags: List<String>) {
        if (tags.isEmpty()) return
        val statement = db.compileStatement("INSERT OR IGNORE INTO bookmark_tags(bookmark_row, tag) VALUES(?, ?)")
        statement.use {
            for (tag in tags.map { it.trim() }.filter { it.isNotEmpty() }.distinct()) {
                it.clearBindings()
                it.bindLong(1, rowId)
                it.bindString(2, tag)
                it.execute()
            }
        }
    }

    private fun replaceTagsLocked(rowId: Long, tags: List<String>) {
        db.delete("bookmark_tags", "bookmark_row = ?", arrayOf(rowId.toString()))
        insertTagsLocked(rowId, tags)
    }
}

private fun escapeLike(input: String): String =
    input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
