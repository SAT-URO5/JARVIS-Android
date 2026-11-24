package com.jarvis.mark.ai

import com.jarvis.mark.Prefs
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Model lists per provider. Starts with a built-in list, and "Refresh" in Settings replaces it with
 * the models your own key can actually use right now (so dead model names never get picked).
 */
object ModelCatalog {
    val CURATED: Map<String, List<String>> = mapOf(
        "gemini" to listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-flash-lite-latest", "gemini-2.0-flash"),
        "groq" to listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "qwen/qwen3-32b"),
        "openrouter" to listOf("meta-llama/llama-3.3-70b-instruct:free", "google/gemma-3-27b-it:free", "mistralai/mistral-small-3.1-24b-instruct:free", "deepseek/deepseek-chat-v3-0324:free", "openai/gpt-oss-20b:free"),
        "mistral" to listOf("mistral-small-latest", "mistral-medium-latest", "open-mistral-nemo", "mistral-large-latest"),
        "cerebras" to listOf("llama-3.3-70b", "llama3.1-8b", "qwen-3-32b", "gpt-oss-120b")
    )

    fun candidates(prefs: Prefs, p: String): List<String> {
        val cached = prefs.get("models_$p").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (cached.isNotEmpty()) cached else (CURATED[p] ?: emptyList())
    }

    fun ttsCandidates(prefs: Prefs): List<String> {
        val cached = prefs.get("tts_models").split(",").map { it.trim() }.filter { it.isNotEmpty() }
        return if (cached.isNotEmpty()) cached else listOf("gemini-2.5-flash-preview-tts")
    }

    /** Blocking network call: run it on a background thread. Returns a short status line. */
    fun refresh(prefs: Prefs, p: String): String {
        val key = prefs.get("key_$p")
        if (key.isBlank()) return "$p: no key"
        return try {
            if (p == "gemini") refreshGemini(prefs, key) else refreshOpenAi(prefs, p, key)
        } catch (e: Exception) {
            "$p: ${e.message}"
        }
    }

    private fun refreshGemini(prefs: Prefs, key: String): String {
        val out = get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200", mapOf("x-goog-api-key" to key))
        val arr = JSONObject(out).getJSONArray("models")
        val chat = mutableListOf<String>()
        val tts = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i)
            val name = m.getString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods") ?: continue
            var ok = false
            for (j in 0 until methods.length()) if (methods.getString(j) == "generateContent") ok = true
            if (!ok || !name.startsWith("gemini")) continue
            if (name.contains("tts")) tts.add(name)
            else if (!Regex("image|embed|live|audio|robotics|computer|exp|thinking").containsMatchIn(name)) chat.add(name)
        }
        val sorted = chat.sortedBy {
            when {
                it.contains("flash") && it.contains("latest") && !it.contains("lite") -> 0
                it.contains("flash") && !it.contains("lite") -> 1
                it.contains("flash") -> 2
                else -> 3
            }
        }.take(8)
        if (sorted.isNotEmpty()) prefs.put("models_gemini", sorted.joinToString(","))
        if (tts.isNotEmpty()) prefs.put("tts_models", tts.take(4).joinToString(","))
        return "gemini: ${sorted.size} chat, ${tts.size} voice models"
    }

    private fun refreshOpenAi(prefs: Prefs, p: String, key: String): String {
        val out = get(Prefs.baseUrl(p) + "/models", mapOf("Authorization" to "Bearer $key"))
        val arr = JSONObject(out).getJSONArray("data")
        val bad = Regex("whisper|guard|tts|embed|moderat|ocr|orpheus|playai|safeguard|transcri|image|dall|diffusion|rerank|allam|compound")
        var ids = (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }
            .filter { !bad.containsMatchIn(it.lowercase()) }
        if (p == "openrouter") ids = ids.filter { it.endsWith(":free") }
        if (ids.isEmpty()) return "$p: no usable models"
        val good = Regex("versatile|70b|120b|large|medium|gpt-oss|gemini|deepseek|llama-3.3")
        val sorted = ids.sortedBy { if (good.containsMatchIn(it.lowercase())) 0 else 1 }.take(10)
        prefs.put("models_$p", sorted.joinToString(","))
        return "$p: ${sorted.size} models"
    }

    private fun get(url: String, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 20000
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            val code = c.responseCode
            if (code !in 200..299) throw AiRouter.HttpEx(code)
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
