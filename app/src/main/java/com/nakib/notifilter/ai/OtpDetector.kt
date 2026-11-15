package com.nakib.notifilter.ai

/** OTP detection. Runs before anything else: an OTP is never held or blocked. */
object OtpDetector {

    private val FALLBACK = Regex("(?i)(otp|verification|one[- ]?time|passcode)")

    /**
     * Returns the OTP code (4-8 digits) or null.
     * Tries the user's regex first, then a keyword + digits fallback.
     */
    fun extractOtp(text: String, userRegex: String): String? {
        // 1. User regex: group 2 is the code when present, else group 1.
        try {
            val regex = Regex(userRegex)
            val match = regex.find(text)
            if (match != null) {
                val groups = match.groupValues
                for (i in groups.indices.reversed()) {
                    val g = groups[i]
                    if (g.matches(Regex("[0-9]{4,8}"))) return g
                }
            }
        } catch (_: Exception) {
            // Bad regex: fall through to the fallback.
        }
        // 2. Fallback: an OTP keyword plus a 4-8 digit number nearby.
        if (FALLBACK.containsMatchIn(text)) {
            Regex("[0-9]{4,8}").find(text)?.let { return it.value }
        }
        return null
    }
}
