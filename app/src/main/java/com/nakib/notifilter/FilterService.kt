package com.nakib.notifilter

import android.app.Notification
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.nakib.notifilter.ai.AiClient
import com.nakib.notifilter.ai.ImageAnalyzer
import com.nakib.notifilter.ai.OtpDetector
import com.nakib.notifilter.data.NotificationDbHelper
import com.nakib.notifilter.data.Prefs
import com.nakib.notifilter.data.Record

/**
 * Pass-through notification filter.
 *
 * - OTP: never cancelled or held. The original notification shows instantly;
 *   a copy is saved in the Inbox.
 * - Spam: cancelled with cancelNotification() and stored in Blocked only.
 *   No replacement notification is ever posted from NotiFilter.
 * - Important / not spam: left completely untouched, so it appears exactly
 *   as the original app's notification.
 * - While the AI decides, a non-OTP notification is briefly snoozed; it
 *   then reappears untouched (not spam) or is cancelled (spam).
 */
class FilterService : NotificationListenerService() {

    private val prefs by lazy { Prefs(this) }
    private val db by lazy { NotificationDbHelper(this) }

    /** Keys posted in the last 30s, to ignore snooze-expiry re-posts and
     *  rapid updates of the same notification (prevents duplicate rows and
     *  repeated AI calls for one notification). */
    private val recentKeys = mutableMapOf<String, Long>()

    /** Keys a rule has snoozed, mapped to when they may be judged again
     *  (snooze expiry + 1 min buffer). Without this, a re-appearing snoozed
     *  notification would match the same rule and be snoozed again forever. */
    private val snoozedKeys = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        prefs.listenerConnectedAt = System.currentTimeMillis()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return // Never filter our own notifications.
        val now = System.currentTimeMillis()
        val lastSeen = recentKeys[sbn.key]
        if (lastSeen != null && now - lastSeen < 30_000L) return
        recentKeys[sbn.key] = now
        if (recentKeys.size > 200) recentKeys.clear()
        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = if (bigText.isNotBlank()) bigText else text
        if (title.isBlank() && body.isBlank()) return

        // A real notification reached the listener — record it for the
        // Inbox status row ("last notification 07:02").
        prefs.lastNotificationSeen = now

        val appName = try {
            val info = packageManager.getApplicationInfo(pkg, 0)
            val label = packageManager.getApplicationLabel(info).toString().trim()
            if (label.isNotBlank() && label != pkg) label
            else pkg.substringAfterLast(".").replaceFirstChar { it.uppercaseChar() }
        } catch (_: Exception) {
            pkg.substringAfterLast(".").replaceFirstChar { it.uppercaseChar() }
        }

        @Suppress("DEPRECATION")
        val picture = extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE)
        val hasImage = picture != null

        // Paused: record and leave everything untouched.
        if (prefs.paused) {
            db.insert(Record(pkg = pkg, appName = appName, title = title, body = body, hasImage = hasImage, key = sbn.key))
            return
        }

        val fullText = "$title $body"

        // 1. OTP first — never cancelled, never held (when OTP is enabled).
        if (prefs.otpEnabled) {
            val otp = OtpDetector.extractOtp(fullText, prefs.otpRegex)
            if (otp != null) {
                db.insert(
                    Record(
                        pkg = pkg, appName = appName, title = title, body = body,
                        otp = otp, isOtp = true, hasImage = hasImage, key = sbn.key
                    )
                )
                return
            }
        }

        // 2. Matching enabled rule (FilterBox way: app + keyword + time
        //    window). OTP already won above — a rule never blocks an OTP.
        //    A notification a rule just snoozed is not judged again until
        //    its snooze has expired, or it would be snoozed forever.
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val alreadySnoozed = snoozedKeys[sbn.key]?.let { it > now } == true
        val rule = if (alreadySnoozed) null else db.listRules()
            .firstOrNull { it.matches(pkg, title, body, hour) }
        if (rule != null) {
            if (rule.isSnooze) {
                val durationMs = rule.snoozeDurationMs
                var snoozed = false
                try {
                    snoozeNotification(sbn.key, durationMs)
                    snoozed = true
                } catch (_: Exception) {
                    // Snooze unavailable on this device: leave it shown.
                }
                if (snoozed) {
                    snoozedKeys[sbn.key] = now + durationMs + 60_000L
                    if (snoozedKeys.size > 200) {
                        snoozedKeys.entries.removeIf { it.value <= now }
                    }
                }
                db.insert(
                    Record(
                        pkg = pkg, appName = appName, title = title, body = body,
                        status = "SHOWN",
                        reason = if (snoozed) "Rule: ${rule.name} · Snoozed ${rule.snoozeLabel()}"
                        else "Rule: ${rule.name} · Snooze failed",
                        hasImage = hasImage, key = sbn.key
                    )
                )
                return
            }
            // Dismiss: cancel the original, store in Blocked only.
            cancelNotification(sbn.key)
            db.insert(
                Record(
                    pkg = pkg, appName = appName, title = title, body = body,
                    status = "BLOCKED", reason = "Rule: ${rule.name}",
                    hasImage = hasImage, key = sbn.key
                )
            )
            return
        }

        // 3. Always-allow list.
        if (matchesAny(fullText, prefs.allowList) || matchesAny(appName, prefs.allowList)) {
            db.insert(
                Record(pkg = pkg, appName = appName, title = title, body = body, hasImage = hasImage, key = sbn.key)
            )
            return
        }

        // 4. Block keywords.
        val hitKeyword = firstMatch(fullText, prefs.blockKeywords)
        if (hitKeyword != null) {
            cancelNotification(sbn.key)
            db.insert(
                Record(
                    pkg = pkg, appName = appName, title = title, body = body,
                    status = "BLOCKED", reason = "Keyword: $hitKeyword", hasImage = hasImage, key = sbn.key
                )
            )
            return
        }

        // 5. Regex mode: no AI at all — everything else passes through.
        if (prefs.mode == "regex") {
            db.insert(
                Record(pkg = pkg, appName = appName, title = title, body = body, hasImage = hasImage, key = sbn.key)
            )
            return
        }

        // 6. AI mode. No key configured -> keyword fallback on promo words.
        val hasKey = if (prefs.aiProvider == "google") {
            prefs.googleKey.isNotBlank()
        } else {
            prefs.openRouterKey.isNotBlank()
        }
        if (!hasKey) {
            val promoHit = firstMatch(fullText, PROMO_WORDS)
            if (promoHit != null) {
                cancelNotification(sbn.key)
                db.insert(
                    Record(
                        pkg = pkg, appName = appName, title = title, body = body,
                        status = "BLOCKED", reason = "Keyword: $promoHit", hasImage = hasImage, key = sbn.key
                    )
                )
            } else {
                db.insert(
                    Record(pkg = pkg, appName = appName, title = title, body = body, hasImage = hasImage, key = sbn.key)
                )
            }
            return
        }

        // Briefly snooze while the AI decides, then cancel or let it reappear.
        try {
            snoozeNotification(sbn.key, 8_000L)
        } catch (_: Exception) {
            // Snooze unavailable on this device: decide without holding.
        }
        val imageBase64 = if (hasImage && prefs.imageAnalysisEnabled && (prefs.imageOcr || prefs.imagePromoBlock)) {
            ImageAnalyzer.toBase64Jpeg(picture)
        } else {
            null
        }
        Thread {
            val verdict = AiClient.classifySync(prefs, title, body, imageBase64)
            if (verdict == null) {
                // AI failed: leave the notification alone, record as shown.
                db.insert(
                    Record(pkg = pkg, appName = appName, title = title, body = body, hasImage = hasImage, key = sbn.key)
                )
                return@Thread
            }
            if (verdict.isSpam) {
                try {
                    cancelNotification(sbn.key)
                } catch (_: Exception) {
                }
                db.insert(
                    Record(
                        pkg = pkg, appName = appName, title = title, body = body,
                        status = "BLOCKED",
                        reason = "AI ${verdict.score}% — ${verdict.reason}",
                        hasImage = hasImage, key = sbn.key
                    )
                )
            } else {
                db.insert(
                    Record(
                        pkg = pkg, appName = appName, title = title, body = body,
                        reason = "AI ${verdict.score}%",
                        hasImage = hasImage, key = sbn.key
                    )
                )
            }
        }.start()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // A dismissed/removed notification may legitimately re-appear later
        // with the same key, so forget it here.
        recentKeys.remove(sbn.key)
    }

    private fun matchesAny(text: String, keywords: Set<String>): Boolean {
        val lower = text.lowercase()
        return keywords.any { it.isNotBlank() && lower.contains(it.lowercase()) }
    }

    private fun firstMatch(text: String, keywords: Set<String>): String? {
        val lower = text.lowercase()
        return keywords.firstOrNull { it.isNotBlank() && lower.contains(it.lowercase()) }
    }

    companion object {
        /** The running listener instance, so the Inbox can cancel (Mark as
         *  spam / Dismiss) the original notification by key. Null when the
         *  listener is not bound. */
        @Volatile
        private var instance: FilterService? = null

        /** True while the listener is bound (notification access granted and
         *  the service connected). Used by the Inbox status row. */
        fun isConnected(): Boolean = instance != null

        /** Cancel the original notification with this key. Returns true if
         *  the listener was running and the cancel was issued. Never posts
         *  anything as NotiFilter. */
        fun cancelByKey(key: String): Boolean {
            if (key.isBlank()) return false
            return try {
                val svc = instance ?: return false
                svc.cancelNotification(key)
                true
            } catch (_: Exception) {
                false
            }
        }

        /** Snooze the original notification with this key. Returns true if
         *  the listener was running and the snooze was issued. */
        fun snoozeByKey(key: String, durationMs: Long): Boolean {
            if (key.isBlank()) return false
            return try {
                val svc = instance ?: return false
                svc.snoozeNotification(key, durationMs)
                true
            } catch (_: Exception) {
                false
            }
        }

        val PROMO_WORDS = setOf(
            "win prize", "jackpot", "90% off", "free recharge",
            "claim now", "congratulations you won", "loan offer", "limited offer",
            "50% off", "flash sale", "buy now", "subscribe now"
        )
    }
}
