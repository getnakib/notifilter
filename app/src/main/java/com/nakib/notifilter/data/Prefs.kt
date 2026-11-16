package com.nakib.notifilter.data

import android.content.Context

/**
 * All user settings. API keys live in SharedPreferences on the device only.
 * They are never logged and never leave the device except in the API call
 * the user configured.
 */
class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("notifilter", Context.MODE_PRIVATE)

    var mode: String
        get() = sp.getString("mode", "ai") ?: "ai" // "ai" or "regex"
        set(v) = sp.edit().putString("mode", v).apply()

    var paused: Boolean
        get() = sp.getBoolean("paused", false)
        set(v) = sp.edit().putBoolean("paused", v).apply()

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) = sp.edit().putBoolean("onboarded", v).apply()

    var openRouterKey: String
        get() = sp.getString("openrouter_key", "") ?: ""
        set(v) = sp.edit().putString("openrouter_key", v).apply()

    var googleKey: String
        get() = sp.getString("google_key", "") ?: ""
        set(v) = sp.edit().putString("google_key", v).apply()

    var openRouterModel: String
        get() = sp.getString("openrouter_model", "openai/gpt-4o-mini") ?: "openai/gpt-4o-mini"
        set(v) = sp.edit().putString("openrouter_model", v).apply()

    var geminiModel: String
        get() = sp.getString("gemini_model", "gemini-2.0-flash") ?: "gemini-2.0-flash"
        set(v) = sp.edit().putString("gemini_model", v).apply()

    var visionModel: String
        get() = sp.getString("vision_model", "gemini-2.0-flash") ?: "gemini-2.0-flash"
        set(v) = sp.edit().putString("vision_model", v).apply()

    /** AI provider: "openrouter" or "google". */
    var aiProvider: String
        get() = sp.getString("ai_provider", "openrouter") ?: "openrouter"
        set(v) = sp.edit().putString("ai_provider", v).apply()

    /** Spam threshold 0-100. A score at or above this is spam. */
    var strictness: Int
        get() = sp.getInt("strictness", 60)
        set(v) = sp.edit().putInt("strictness", v.coerceIn(0, 100)).apply()

    var otpRegex: String
        get() = sp.getString(
            "otp_regex",
            "(?i)(otp|verification|one[- ]?time|passcode|code)[^0-9]{0,30}([0-9]{4,8})"
        ) ?: ""
        set(v) = sp.edit().putString("otp_regex", v).apply()

    /** OTP fast-path on/off. Default true = OTP is matched by regex first and
     *  shown instantly, never sent to AI. Bound to the "OTP first" switch in
     *  Rules and the "Instant OTP" switch in AI settings. */
    var otpEnabled: Boolean
        get() = sp.getBoolean("otp_enabled", true)
        set(v) = sp.edit().putBoolean("otp_enabled", v).apply()

    /** Master switch for image/graphic analysis (Rules card). Default true. */
    var imageAnalysisEnabled: Boolean
        get() = sp.getBoolean("image_analysis_enabled", true)
        set(v) = sp.edit().putBoolean("image_analysis_enabled", v).apply()

    /** Behaviour: blocked notifications never ring. Default true. */
    var silentBlocked: Boolean
        get() = sp.getBoolean("silent_blocked", true)
        set(v) = sp.edit().putBoolean("silent_blocked", v).apply()

    /** Behaviour: one daily recap notification at 9 pm. Default false. */
    var dailySummary: Boolean
        get() = sp.getBoolean("daily_summary", false)
        set(v) = sp.edit().putBoolean("daily_summary", v).apply()

    var blockKeywords: MutableSet<String>
        get() = (sp.getStringSet("block_keywords", DEFAULT_BLOCK) ?: DEFAULT_BLOCK)
            .toMutableSet()
        set(v) = sp.edit().putStringSet("block_keywords", v).apply()

    var allowList: MutableSet<String>
        get() = (sp.getStringSet("allow_list", emptySet()) ?: emptySet()).toMutableSet()
        set(v) = sp.edit().putStringSet("allow_list", v).apply()

    var imageOcr: Boolean
        get() = sp.getBoolean("image_ocr", true)
        set(v) = sp.edit().putBoolean("image_ocr", v).apply()

    var imagePromoBlock: Boolean
        get() = sp.getBoolean("image_promo_block", true)
        set(v) = sp.edit().putBoolean("image_promo_block", v).apply()

    var imageQrCheck: Boolean
        get() = sp.getBoolean("image_qr_check", false)
        set(v) = sp.edit().putBoolean("image_qr_check", v).apply()

    /** When the listener last saw a notification (millis, 0 = never). Set by
     *  FilterService; shown in the Inbox status row so Rani can tell at a
     *  glance whether notifications are reaching the app. */
    var lastNotificationSeen: Long
        get() = sp.getLong("last_notification_seen", 0L)
        set(v) = sp.edit().putLong("last_notification_seen", v).apply()

    /** When the listener last connected (millis, 0 = never). Set by
     *  FilterService.onListenerConnected(). */
    var listenerConnectedAt: Long
        get() = sp.getLong("listener_connected_at", 0L)
        set(v) = sp.edit().putLong("listener_connected_at", v).apply()

    companion object {
        val DEFAULT_BLOCK = setOf(
            "win prize", "jackpot", "90% off", "free recharge",
            "claim now", "congratulations you won", "loan offer", "limited offer"
        )
    }
}
