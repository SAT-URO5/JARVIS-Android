package com.jarvis.mark.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.jarvis.mark.Prefs
import java.util.Locale

/** Offline-capable speech output using the phone's own text-to-speech engine. */
class Speaker(ctx: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
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
        val t = tts ?: return
        if (!ready || text.isBlank()) return
        val clean = text.replace(Regex("[*_`#]"), "")
        val loc = if (hasArabic(clean)) Locale("ar") else Locale.forLanguageTag(prefs.get("tts_lang", "en-US"))
        t.language = loc
        val wanted = prefs.get("voice_name")
        if (wanted.isNotBlank()) {
            t.voices?.firstOrNull { it.name == wanted && it.locale.language == loc.language }?.let { t.voice = it }
        }
        t.setPitch(prefs.get("pitch", "0.85").toFloatOrNull() ?: 0.85f)
        t.setSpeechRate(prefs.get("rate", "1.0").toFloatOrNull() ?: 1.0f)
        t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }

    fun voiceNames(): List<String> =
        try { tts?.voices?.map { it.name }?.sorted() ?: emptyList() } catch (e: Exception) { emptyList() }

    fun stop() { tts?.stop() }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
