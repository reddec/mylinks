package net.reddec.mylinks.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Bookmark(
    val rowId: Long,
    /** null until the bookmark exists on the server (offline-created row). */
    val serverId: Long?,
    val url: String,
    val title: String,
    val excerpt: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val tags: List<String>,
)

/** A bookmark as the Shiori server reports it. */
data class RemoteBookmark(
    val serverId: Long,
    val url: String,
    val title: String,
    val excerpt: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val tags: List<String>,
)

data class TagCount(val name: String, val count: Int)

/** One queued local mutation, replayed to the server on the next successful sync. */
data class OutboxEntry(
    val seq: Long,
    /** create | tag | delete */
    val kind: String,
    val rowId: Long?,
    val serverId: Long?,
    val tag: String?,
)

data class DateFilter(val label: String, val from: Long?, val to: Long?)

class Db(context: Context) : SQLiteOpenHelper(context, "mylinks.db", null, 2) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        // The UI and the background worker open the same file; wait instead of failing on
        // contention. PRAGMA returns a row, so this has to go through rawQuery, not execSQL.
        db.rawQuery("PRAGMA busy_timeout = 5000", null).use { it.moveToFirst() }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE bookmarks(
                row_id      INTEGER PRIMARY KEY AUTOINCREMENT,
                server_id   INTEGER UNIQUE,
                url         TEXT NOT NULL,
                title       TEXT NOT NULL DEFAULT '',
                excerpt     TEXT NOT NULL DEFAULT '',
                created_at  INTEGER NOT NULL,
                modified_at INTEGER NOT NULL,
                content     TEXT NOT NULL DEFAULT '',
                content_at  INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_bookmarks_created ON bookmarks(created_at DESC)")
        db.execSQL("CREATE TABLE bookmark_tags(bookmark_row INTEGER NOT NULL, tag TEXT NOT NULL, PRIMARY KEY(bookmark_row, tag))")
        db.execSQL("CREATE INDEX idx_bookmark_tags_tag ON bookmark_tags(tag)")
        db.execSQL(
            """
            CREATE TABLE outbox(
                seq       INTEGER PRIMARY KEY AUTOINCREMENT,
                kind      TEXT NOT NULL,
                row_id    INTEGER,
                server_id INTEGER,
                tag       TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE TABLE sync_seen(server_id INTEGER PRIMARY KEY)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN content TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE bookmarks ADD COLUMN content_at INTEGER NOT NULL DEFAULT 0")
        }
    }
}
