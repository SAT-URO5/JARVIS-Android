package com.jarvis.mark

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("jarvis", Context.MODE_PRIVATE)

    fun get(key: String, def: String = ""): String = sp.getString(key, def) ?: def
    fun put(key: String, value: String) = sp.edit().putString(key, value).apply()

    companion object {
        val PROVIDERS = listOf("gemini", "groq", "openrouter", "mistral", "cerebras", "custom")

        // Model names change over time. Every one of them can be edited in Settings.
        fun defaultModel(p: String) = when (p) {
            "gemini" -> "gemini-flash-latest"
            "groq" -> "llama-3.3-70b-versatile"
            "openrouter" -> "meta-llama/llama-3.3-70b-instruct:free"
            "mistral" -> "mistral-small-latest"
            "cerebras" -> "llama-3.3-70b"
            else -> ""
        }

        fun baseUrl(p: String) = when (p) {
            "groq" -> "https://api.groq.com/openai/v1"
            "openrouter" -> "https://openrouter.ai/api/v1"
            "mistral" -> "https://api.mistral.ai/v1"
            "cerebras" -> "https://api.cerebras.ai/v1"
            else -> ""
        }
    }
}
