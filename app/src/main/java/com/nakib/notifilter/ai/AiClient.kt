package com.nakib.notifilter.ai

import com.nakib.notifilter.data.Prefs
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class Verdict(val isSpam: Boolean, val score: Int, val reason: String)

/**
 * Spam classification via OpenRouter or Google Gemini.
 * Blocking calls — run on a background thread. Returns null when no key is
 * configured or the request fails, so the caller can fall back to keywords.
 */
object AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private fun prompt(title: String, text: String, hasImage: Boolean): String {
        val imageNote = if (hasImage) {
            " This notification also contains an image/graphic. If the image text " +
                "looks like a promo banner, discount offer, prize or QR-code lure, " +
                "treat that as spam evidence."
        } else {
            ""
        }
        return "You classify phone notifications as spam or not spam. " +
            "Spam is: promotions, prize/lottery wins, loan offers, fake discounts, " +
            "clickbait, ads. Not spam is: OTPs, banking alerts, delivery updates, " +
            "messages from real contacts, system alerts.$imageNote " +
            "Reply with JSON only, no other text: " +
            "{\"spam\": true or false, \"score\": 0-100 (spam likelihood), " +
            "\"reason\": \"short reason\"}. " +
            "Notification title: \"$title\". Notification text: \"$text\"."
    }

    /** Classify with the user's configured provider. Null = no key / failure.
     *  When an image is attached, the vision model (AI settings) is used
     *  instead of the text model; "auto" means the selected provider's model. */
    fun classifySync(
        prefs: Prefs,
        title: String,
        text: String,
        imageBase64: String? = null
    ): Verdict? {
        val visionModel = if (imageBase64 != null) resolveVisionModel(prefs) else null
        return when (prefs.aiProvider) {
            "google" -> {
                if (prefs.googleKey.isBlank()) return null
                classifyGemini(prefs, title, text, imageBase64, visionModel)
            }
            else -> {
                if (prefs.openRouterKey.isBlank()) return null
                classifyOpenRouter(prefs, title, text, imageBase64, visionModel)
            }
        }
    }

    /** Resolve the vision model for the current provider, or null to use the
     *  provider's text model. "auto" (or blank) = provider model. */
    private fun resolveVisionModel(prefs: Prefs): String? {
        val v = prefs.visionModel.trim()
        if (v.isBlank() || v == "auto") return null
        return when (prefs.aiProvider) {
            // Gemini needs a bare model name: "google/gemini-2.0-flash" -> "gemini-2.0-flash".
            "google" -> v.substringAfterLast("/")
            // OpenRouter needs a provider/model id.
            else -> if (v.contains("/")) v else null
        }
    }

    private fun classifyOpenRouter(
        prefs: Prefs,
        title: String,
        text: String,
        imageBase64: String?,
        modelOverride: String? = null
    ): Verdict? {
        return try {
            val userContent = JSONArray()
            userContent.put(JSONObject().put("type", "text").put("text", prompt(title, text, imageBase64 != null)))
            if (imageBase64 != null) {
                userContent.put(
                    JSONObject().put("type", "image_url").put(
                        "image_url",
                        JSONObject().put("url", "data:image/jpeg;base64,$imageBase64")
                    )
                )
            }
            val body = JSONObject().apply {
                put("model", modelOverride ?: prefs.openRouterModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "user").put("content", userContent))
                })
                put("temperature", 0)
            }
            val request = Request.Builder()
                .url("https://openrouter.ai/api/v1/chat/completions")
                .header("Authorization", "Bearer ${prefs.openRouterKey}")
                .header("HTTP-Referer", "https://github.com/getnakib/notifilter")
                .header("X-Title", "NotiFilter")
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val content = JSONObject(response.body?.string() ?: return null)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                parseVerdict(content, prefs.strictness)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun classifyGemini(
        prefs: Prefs,
        title: String,
        text: String,
        imageBase64: String?,
        modelOverride: String? = null
    ): Verdict? {
        return try {
            val parts = JSONArray()
            parts.put(JSONObject().put("text", prompt(title, text, imageBase64 != null)))
            if (imageBase64 != null) {
                parts.put(
                    JSONObject().put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", imageBase64)
                    })
                )
            }
            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().put("parts", parts))
                })
            }
            val model = modelOverride ?: prefs.geminiModel
            val url =
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${prefs.googleKey}"
            val request = Request.Builder()
                .url(url)
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val content = JSONObject(response.body?.string() ?: return null)
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                parseVerdict(content, prefs.strictness)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Test a provider key/model. True = the API answered successfully. */
    fun testSync(provider: String, key: String, model: String): Boolean {
        if (key.isBlank()) return false
        return try {
            val request = if (provider == "google") {
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().put("parts", JSONArray().apply {
                            put(JSONObject().put("text", "Reply with the single word: ok"))
                        }))
                    })
                }
                Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            } else {
                val body = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "user").put("content", "Reply with the single word: ok"))
                    })
                }
                Request.Builder()
                    .url("https://openrouter.ai/api/v1/chat/completions")
                    .header("Authorization", "Bearer $key")
                    .post(body.toString().toRequestBody(JSON))
                    .build()
            }
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** Pull the {"spam":..,"score":..,"reason":..} JSON out of a model reply. */
    private fun parseVerdict(content: String, strictness: Int): Verdict? {
        return try {
            val start = content.indexOf('{')
            val end = content.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val json = JSONObject(content.substring(start, end + 1))
            val score = json.optInt("score", 50)
            val spam = json.optBoolean("spam", score >= strictness)
            Verdict(
                isSpam = spam || score >= strictness,
                score = score,
                reason = json.optString("reason", "AI check")
            )
        } catch (_: Exception) {
            null
        }
    }
}
