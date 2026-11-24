package com.jarvis.mark.ai

import android.content.Context
import com.jarvis.mark.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Tries each configured provider in order. Inside a provider it tries the chosen model first, then the
 * next models in its list. A bad key skips the provider, a dead model or rate limit moves to the next model.
 * Call from a background thread.
 */
object AiRouter {
    const val SYSTEM = "You are JARVIS, a concise, polite voice assistant running on an Android phone. " +
        "Reply in the same language the user writes in; if they write Arabic, answer in Egyptian-friendly Arabic. " +
        "Keep answers short and easy to speak aloud. Do not use markdown, lists or emojis."

    data class Turn(val role: String, val text: String) // role: "user" or "assistant"

    class HttpEx(val code: Int) : Exception("HTTP $code")

    fun hasAnyKey(ctx: Context): Boolean {
        val p = Prefs(ctx)
        return Prefs.PROVIDERS.any { name ->
            if (name == "custom") p.get("base_custom").isNotBlank() else p.get("key_$name").isNotBlank()
        }
    }

    fun ask(ctx: Context, history: List<Turn>): Result<String> {
        val prefs = Prefs(ctx)
        val order = prefs.get("order", "gemini,groq,openrouter,mistral,cerebras,custom")
            .split(",").map { it.trim() }.filter { it.isNotEmpty() }
        var lastErr = "No provider is configured"
        for (name in order) {
            val key = prefs.get("key_$name")
            if (name == "custom") {
                if (prefs.get("base_custom").isBlank()) continue
            } else if (key.isBlank()) continue
            for (model in modelsFor(prefs, name)) {
                try {
                    val reply = if (name == "gemini") gemini(model, key, history)
                    else openAi(prefs, name, model, key, history)
                    if (reply.isNotBlank()) return Result.success(reply.trim())
                } catch (e: HttpEx) {
                    lastErr = "$name/$model: HTTP ${e.code}"
                    if (e.code == 401 || e.code == 403) break
                } catch (e: Exception) {
                    lastErr = "$name: ${e.message}"
                    break
                }
            }
        }
        return Result.failure(Exception(lastErr))
    }

    private fun modelsFor(prefs: Prefs, name: String): List<String> {
        if (name == "custom") return listOf(prefs.get("model_custom")).filter { it.isNotBlank() }
        val chosen = prefs.get("model_$name", "auto")
        val pool = ModelCatalog.candidates(prefs, name)
        val list = if (chosen.isNotBlank() && chosen != "auto") listOf(chosen) + pool else pool
        return list.distinct().take(4)
    }

    internal fun post(url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 8000
            c.readTimeout = 40000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw HttpEx(code)
            return text
        } finally {
            c.disconnect()
        }
    }

    private fun openAi(prefs: Prefs, name: String, model: String, key: String, history: List<Turn>): String {
        val base = (if (name == "custom") prefs.get("base_custom") else Prefs.baseUrl(name)).trimEnd('/')
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM))
        history.forEach { msgs.put(JSONObject().put("role", it.role).put("content", it.text)) }
        val body = JSONObject().put("model", model).put("messages", msgs).toString()
        val headers = if (key.isNotBlank()) mapOf("Authorization" to "Bearer $key") else emptyMap()
        val out = post("$base/chat/completions", headers, body)
        return JSONObject(out).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
    }

    private fun gemini(model: String, key: String, history: List<Turn>): String {
        val contents = JSONArray()
        history.forEach {
            contents.put(
                JSONObject()
                    .put("role", if (it.role == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", it.text)))
            )
        }
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", contents)
            .toString()
        val out = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to key), body
        )
        return JSONObject(out).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
    }
}
