package com.nakib.notifilter.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** One stored notification. status is SHOWN or BLOCKED. `key` is the
 *  StatusBarNotification key, used to cancel / dismiss the original
 *  notification from the Inbox (Mark as spam / Dismiss). */
data class Record(
    val id: Long = 0,
    val pkg: String,
    val appName: String,
    val title: String,
    val body: String,
    val otp: String? = null,
    val time: Long = System.currentTimeMillis(),
    val status: String = "SHOWN",
    val reason: String? = null,
    val isOtp: Boolean = false,
    val hasImage: Boolean = false,
    val isRead: Boolean = false,
    val key: String = ""
)

data class AppStat(
    val pkg: String,
    val appName: String,
    val total: Int,
    val real: Int,
    val spam: Int
) {
    val spamPct: Int get() = if (total == 0) 0 else spam * 100 / total
    val realPct: Int get() = if (total == 0) 0 else real * 100 / total
}

class NotificationDbHelper(context: Context) :
    SQLiteOpenHelper(context, "notifilter.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                pkg TEXT NOT NULL,
                app_name TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                otp TEXT,
                time INTEGER NOT NULL,
                status TEXT NOT NULL,
                reason TEXT,
                is_otp INTEGER NOT NULL DEFAULT 0,
                has_image INTEGER NOT NULL DEFAULT 0,
                is_read INTEGER NOT NULL DEFAULT 0,
                key TEXT NOT NULL DEFAULT ''
            )"""
        )
        db.execSQL("CREATE INDEX idx_status ON records(status)")
        db.execSQL("CREATE INDEX idx_pkg ON records(pkg)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v2: store the StatusBarNotification key so the Inbox can cancel
        // (Mark as spam / Dismiss) the original notification.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN key TEXT NOT NULL DEFAULT ''")
        }
    }

    fun insert(record: Record): Long {
        val values = ContentValues().apply {
            put("pkg", record.pkg)
            put("app_name", record.appName)
            put("title", record.title)
            put("body", record.body)
            put("otp", record.otp)
            put("time", record.time)
            put("status", record.status)
            put("reason", record.reason)
            put("is_otp", if (record.isOtp) 1 else 0)
            put("has_image", if (record.hasImage) 1 else 0)
            put("is_read", if (record.isRead) 1 else 0)
            put("key", record.key)
        }
        return writableDatabase.insert("records", null, values)
    }

    /** Move a shown record to Blocked (Mark as spam). Counts as spam. */
    fun markSpam(id: Long, reason: String = "Marked as spam") {
        val values = ContentValues().apply {
            put("status", "BLOCKED")
            put("reason", reason)
        }
        writableDatabase.update("records", values, "id = ?", arrayOf(id.toString()))
    }

    fun listByStatus(status: String): List<Record> {
        val out = mutableListOf<Record>()
        readableDatabase.query(
            "records", null, "status = ?", arrayOf(status),
            null, null, "time DESC"
        ).use { c ->
            while (c.moveToNext()) out.add(fromCursor(c))
        }
        return out
    }

    fun all(): List<Record> {
        val out = mutableListOf<Record>()
        readableDatabase.query("records", null, null, null, null, null, "time DESC")
            .use { c ->
                while (c.moveToNext()) out.add(fromCursor(c))
            }
        return out
    }

    fun delete(id: Long) {
        writableDatabase.delete("records", "id = ?", arrayOf(id.toString()))
    }

    fun restore(id: Long) {
        val values = ContentValues().apply {
            put("status", "SHOWN")
            put("reason", "Restored")
        }
        writableDatabase.update("records", values, "id = ?", arrayOf(id.toString()))
    }

    fun deleteAllBlocked() {
        writableDatabase.delete("records", "status = ?", arrayOf("BLOCKED"))
    }

    fun markAllRead() {
        val values = ContentValues().apply { put("is_read", 1) }
        writableDatabase.update("records", values, null, null)
    }

    /** Per-app stats computed from the same stored rows as Inbox/Blocked. */
    fun appStats(): List<AppStat> {
        val grouped = all().groupBy { it.pkg }
        return grouped.map { (pkg, rows) ->
            AppStat(
                pkg = pkg,
                appName = rows.first().appName,
                total = rows.size,
                real = rows.count { it.status == "SHOWN" },
                spam = rows.count { it.status == "BLOCKED" }
            )
        }.sortedByDescending { it.total }
    }

    private fun fromCursor(c: android.database.Cursor): Record {
        fun s(name: String): String = c.getString(c.getColumnIndexOrThrow(name)) ?: ""
        fun sOrNull(name: String): String? = c.getString(c.getColumnIndexOrThrow(name))
        // The key column was added in v2; tolerate its absence defensively.
        val keyIdx = c.getColumnIndex("key")
        return Record(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            pkg = s("pkg"),
            appName = s("app_name"),
            title = s("title"),
            body = s("body"),
            otp = sOrNull("otp"),
            time = c.getLong(c.getColumnIndexOrThrow("time")),
            status = s("status"),
            reason = sOrNull("reason"),
            isOtp = c.getInt(c.getColumnIndexOrThrow("is_otp")) == 1,
            hasImage = c.getInt(c.getColumnIndexOrThrow("has_image")) == 1,
            isRead = c.getInt(c.getColumnIndexOrThrow("is_read")) == 1,
            key = if (keyIdx >= 0) c.getString(keyIdx).orEmpty() else ""
        )
    }
}
