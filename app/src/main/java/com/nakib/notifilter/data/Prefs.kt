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

    companion object {
        val DEFAULT_BLOCK = setOf(
            "win prize", "jackpot", "90% off", "free recharge",
            "claim now", "congratulations you won", "loan offer", "limited offer"
        )
    }
}
