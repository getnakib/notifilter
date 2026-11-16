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

/**
 * A FilterBox-style rule: per-app control is rule-scoped, not a dedicated
 * per-app screen. A rule applies to the apps in [apps]; keywords are
 * optional — with no keywords it matches the whole app. [action] is
 * "dismiss" or "snooze". [snoozeMinutes] is 15, 60, or -1 = until dismissed
 * (implemented as a 7-day snooze). [startHour]/[endHour] are -1 = no time
 * window, otherwise 0..23 (end < start means overnight, e.g. 22–07).
 */
data class Rule(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val apps: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    val action: String = "dismiss",
    val snoozeMinutes: Int = 15,
    val startHour: Int = -1,
    val endHour: Int = -1
) {
    val isSnooze: Boolean get() = action == "snooze"
    val hasTimeWindow: Boolean get() = startHour in 0..23 && endHour in 0..23

    /** Snooze duration in ms. -1 (until dismissed) maps to 7 days. */
    val snoozeDurationMs: Long
        get() = if (snoozeMinutes < 0) 7L * 24 * 60 * 60 * 1000 else snoozeMinutes * 60_000L

    fun snoozeLabel(): String = when (snoozeMinutes) {
        15 -> "15 min"
        60 -> "1 hour"
        else -> "Until dismissed"
    }

    fun actionLabel(): String = if (isSnooze) "Snooze ${snoozeLabel()}" else "Dismiss"

    fun timeLabel(): String? = if (hasTimeWindow) {
        "%02d:00–%02d:00".format(startHour, endHour)
    } else {
        null
    }

    /** App + keyword + time-window match. Keyword match is case-insensitive
     *  substring on title/body; with no keywords the app alone matches. */
    fun matches(pkg: String, title: String, body: String, hour: Int): Boolean {
        if (!enabled) return false
        if (apps.isNotEmpty() && !apps.contains(pkg)) return false
        if (hasTimeWindow) {
            val inside = if (startHour <= endHour) {
                hour in startHour until endHour
            } else {
                hour >= startHour || hour < endHour
            }
            if (!inside) return false
        }
        if (keywords.isEmpty()) return true
        val text = "$title $body".lowercase()
        return keywords.any { it.isNotBlank() && text.contains(it.lowercase()) }
    }
}

/** Minimal JSON string-array codec (no JSON library in the app). */
object JsonList {
    fun encode(items: List<String>): String {
        val sb = StringBuilder("[")
        items.forEachIndexed { i, item ->
            if (i > 0) sb.append(",")
            sb.append("\"")
            for (ch in item) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(ch)
                }
            }
            sb.append("\"")
        }
        sb.append("]")
        return sb.toString()
    }

    fun decode(json: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val n = json.length
        while (i < n) {
            val c = json[i]
            if (c == '"') {
                val sb = StringBuilder()
                i++
                while (i < n) {
                    val ch = json[i]
                    if (ch == '\\' && i + 1 < n) {
                        when (json[i + 1]) {
                            '\\' -> sb.append('\\')
                            '"' -> sb.append('"')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            else -> sb.append(json[i + 1])
                        }
                        i += 2
                    } else if (ch == '"') {
                        i++
                        break
                    } else {
                        sb.append(ch)
                        i++
                    }
                }
                out.add(sb.toString())
            } else {
                i++
            }
        }
        return out
    }
}

class NotificationDbHelper(context: Context) :
    SQLiteOpenHelper(context, "notifilter.db", null, 3) {

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
        createRulesTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v2: store the StatusBarNotification key so the Inbox can cancel
        // (Mark as spam / Dismiss) the original notification.
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE records ADD COLUMN key TEXT NOT NULL DEFAULT ''")
        }
        // v3: add the rules table only. The records table and her stored
        // notifications are untouched.
        if (oldVersion < 3) {
            createRulesTable(db)
        }
    }

    private fun createRulesTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS rules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                apps TEXT NOT NULL DEFAULT '[]',
                keywords TEXT NOT NULL DEFAULT '[]',
                action TEXT NOT NULL DEFAULT 'dismiss',
                snooze_minutes INTEGER NOT NULL DEFAULT 15,
                start_hour INTEGER NOT NULL DEFAULT -1,
                end_hour INTEGER NOT NULL DEFAULT -1
            )"""
        )
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

    // ---- Rules (FilterBox way: per-app control is rule-scoped) ----

    fun insertRule(rule: Rule): Long {
        val values = ContentValues().apply {
            put("name", rule.name)
            put("enabled", if (rule.enabled) 1 else 0)
            put("apps", JsonList.encode(rule.apps))
            put("keywords", JsonList.encode(rule.keywords))
            put("action", rule.action)
            put("snooze_minutes", rule.snoozeMinutes)
            put("start_hour", rule.startHour)
            put("end_hour", rule.endHour)
        }
        return writableDatabase.insert("rules", null, values)
    }

    fun updateRule(rule: Rule) {
        val values = ContentValues().apply {
            put("name", rule.name)
            put("enabled", if (rule.enabled) 1 else 0)
            put("apps", JsonList.encode(rule.apps))
            put("keywords", JsonList.encode(rule.keywords))
            put("action", rule.action)
            put("snooze_minutes", rule.snoozeMinutes)
            put("start_hour", rule.startHour)
            put("end_hour", rule.endHour)
        }
        writableDatabase.update("rules", values, "id = ?", arrayOf(rule.id.toString()))
    }

    fun deleteRule(id: Long) {
        writableDatabase.delete("rules", "id = ?", arrayOf(id.toString()))
    }

    fun listRules(): List<Rule> {
        val out = mutableListOf<Rule>()
        readableDatabase.query("rules", null, null, null, null, null, "id ASC")
            .use { c ->
                while (c.moveToNext()) out.add(ruleFromCursor(c))
            }
        return out
    }

    fun getRule(id: Long): Rule? {
        readableDatabase.query("rules", null, "id = ?", arrayOf(id.toString()), null, null, null)
            .use { c ->
                if (c.moveToFirst()) return ruleFromCursor(c)
            }
        return null
    }

    private fun ruleFromCursor(c: android.database.Cursor): Rule {
        fun s(name: String): String = c.getString(c.getColumnIndexOrThrow(name)) ?: ""
        fun i(name: String): Int = c.getInt(c.getColumnIndexOrThrow(name))
        return Rule(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            name = s("name"),
            enabled = i("enabled") == 1,
            apps = JsonList.decode(s("apps")),
            keywords = JsonList.decode(s("keywords")),
            action = s("action").ifBlank { "dismiss" },
            snoozeMinutes = i("snooze_minutes"),
            startHour = i("start_hour"),
            endHour = i("end_hour")
        )
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
