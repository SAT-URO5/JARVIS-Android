package com.jarvis.mark.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import com.jarvis.mark.Prefs
import com.jarvis.mark.ai.AiRouter
import com.jarvis.mark.ai.ModelCatalog
import org.json.JSONArray
import org.json.JSONObject

/**
 * Deep male assistant voice (default "Charon", the same voice family the original project uses)
 * through Gemini speech. Needs internet and a Gemini key. Works without Google Play services.
 */
class GeminiTts(private val prefs: Prefs) {
    @Volatile private var generation = 0
    @Volatile private var track: AudioTrack? = null

    fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit, onFail: () -> Unit) {
        val gen = ++generation
        Thread {
            try {
                val (pcm, rate) = fetch(text)
                if (gen != generation) return@Thread
                play(pcm, rate, gen, onStart, onDone)
            } catch (e: Exception) {
                if (gen == generation) onFail()
            }
        }.start()
    }

    fun stop() {
        generation++
        val t = track
        track = null
        try { t?.stop() } catch (_: Exception) {}
        try { t?.release() } catch (_: Exception) {}
    }

    private fun fetch(text: String): Pair<ByteArray, Int> {
        val key = prefs.get("key_gemini")
        if (key.isBlank()) throw Exception("no Gemini key")
        val voice = prefs.get("gem_voice", "Charon")
        val styled = "Say in a calm, deep, confident, refined male voice, like a polished AI butler: $text"
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", styled)))))
            .put("generationConfig", JSONObject()
                .put("responseModalities", JSONArray().put("AUDIO"))
                .put("speechConfig", JSONObject().put("voiceConfig", JSONObject()
                    .put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)))))
            .toString()
        val models = (listOf(prefs.get("tts_model", "gemini-2.5-flash-preview-tts")) + ModelCatalog.ttsCandidates(prefs)).distinct()
        var last: Exception = Exception("no voice model")
        for (m in models) {
            try {
                val out = AiRouter.post(
                    "https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent",
                    mapOf("x-goog-api-key" to key), body
                )
                val inline = JSONObject(out).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getJSONObject("inlineData")
                val data = Base64.decode(inline.getString("data"), Base64.DEFAULT)
                val rate = Regex("rate=(\\d+)").find(inline.optString("mimeType"))?.groupValues?.get(1)?.toIntOrNull() ?: 24000
                return Pair(data, rate)
            } catch (e: AiRouter.HttpEx) {
                last = e
                if (e.code == 401 || e.code == 403) throw e
            }
        }
        throw last
    }

    private fun play(pcm: ByteArray, rate: Int, gen: Int, onStart: () -> Unit, onDone: () -> Unit) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            )
            .setBufferSizeInBytes(pcm.size)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track = t
        t.write(pcm, 0, pcm.size)
        val frames = pcm.size / 2
        val limit = System.currentTimeMillis() + frames * 1000L / rate + 2000
        onStart()
        t.play()
        while (gen == generation && t.playbackHeadPosition < frames && System.currentTimeMillis() < limit) {
            Thread.sleep(50)
        }
        if (gen == generation) {
            track = null
            try { t.stop() } catch (_: Exception) {}
            try { t.release() } catch (_: Exception) {}
            onDone()
        }
    }
}
