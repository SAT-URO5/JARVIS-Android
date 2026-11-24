package com.jarvis.mark.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarvis.mark.Prefs
import java.util.Locale

/**
 * Speech output. Online with a Gemini key: deep male voice (Charon). Otherwise, or if that fails:
 * the phone's own offline voice, pitched lower so it sounds closer to a butler-style assistant.
 */
class Speaker(ctx: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
    private val gem = GeminiTts(prefs)
    private var ready = false

    var onStart: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null
    var onReady: (() -> Unit)? = null

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { onStart?.invoke() }
            override fun onDone(utteranceId: String?) { onDone?.invoke() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone?.invoke() }
        })
        onReady?.invoke()
    }

    private fun hasArabic(s: String) = s.any { it.code in 0x0600..0x06FF }

    fun speak(text: String) {
        val clean = text.replace(Regex("[*_`#]"), "").trim()
        if (clean.isEmpty()) return
        val mode = prefs.get("tts_mode", "auto")
        if (mode != "system" && prefs.get("key_gemini").isNotBlank()) {
            gem.speak(clean.take(500), { onStart?.invoke() }, { onDone?.invoke() }, { systemSpeak(clean) })
            return
        }
        systemSpeak(clean)
    }

    private fun systemSpeak(clean: String) {
        val t = tts ?: return
        if (!ready) { onDone?.invoke(); return }
        val loc = if (hasArabic(clean)) Locale("ar") else Locale.forLanguageTag(prefs.get("tts_lang", "en-US"))
        t.language = loc
        val voices = try { t.voices?.filter { it.locale.language == loc.language } ?: emptyList() } catch (e: Exception) { emptyList() }
        val wanted = prefs.get("voice_name")
        val chosen = voices.firstOrNull { it.name == wanted }
            ?: voices.firstOrNull { it.name.lowercase().contains("male") && !it.name.lowercase().contains("female") }
        if (chosen != null) t.voice = chosen
        t.setPitch(prefs.get("pitch", "0.7").toFloatOrNull() ?: 0.7f)
        t.setSpeechRate(prefs.get("rate", "1.0").toFloatOrNull() ?: 1.0f)
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun voiceNames(): List<String> =
        try { tts?.voices?.map { it.name }?.sorted() ?: emptyList() } catch (e: Exception) { emptyList() }

    fun stop() {
        gem.stop()
        tts?.stop()
    }

    fun shutdown() {
        gem.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
