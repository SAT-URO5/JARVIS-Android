package com.jarvis.mark.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.jarvis.mark.Prefs
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Speech-to-text for phones without Google speech services (e.g. Huawei).
 * Records with the microphone, stops on silence, and sends the audio to Groq's Whisper.
 */
class CloudStt(private val ctx: Context, private val prefs: Prefs) {
    private val handler = Handler(Looper.getMainLooper())
    private var rec: MediaRecorder? = null
    private var file: File? = null
    private var callback: ((String?, String?) -> Unit)? = null
    private var spoke = false
    var active = false
        private set

    fun start(cb: (String?, String?) -> Unit) {
        callback = cb
        val f = File(ctx.cacheDir, "stt.m4a")
        f.delete()
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(16000)
            r.setAudioEncodingBitRate(64000)
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) {}
            cb(null, "Microphone error: ${e.message}")
            return
        }
        rec = r
        file = f
        spoke = false
        active = true
        var quiet = 0
        var ticks = 0
        val poll = object : Runnable {
            override fun run() {
                if (!active) return
                ticks++
                val amp = try { r.maxAmplitude } catch (e: Exception) { 0 }
                if (amp > 2500) { spoke = true; quiet = 0 } else if (spoke) quiet++
                if ((spoke && quiet >= 6) || ticks >= 75 || (!spoke && ticks >= 40)) finish()
                else handler.postDelayed(this, 200)
            }
        }
        handler.postDelayed(poll, 300)
    }

    fun stopNow() { if (active) finish() }

    fun release() {
        active = false
        try { rec?.release() } catch (_: Exception) {}
        rec = null
    }

    private fun finish() {
        if (!active) return
        active = false
        val cb = callback
        try { rec?.stop() } catch (_: RuntimeException) { spoke = false }
        try { rec?.release() } catch (_: Exception) {}
        rec = null
        val f = file
        if (!spoke || f == null || !f.exists()) { cb?.invoke(null, null); return }
        Thread {
            try {
                val text = transcribe(f)
                handler.post { cb?.invoke(text, null) }
            } catch (e: Exception) {
                handler.post { cb?.invoke(null, "Cloud speech failed: ${e.message}") }
            }
        }.start()
    }

    private fun transcribe(f: File): String {
        val key = prefs.get("key_groq")
        if (key.isBlank()) throw Exception("no Groq key")
        val lang = prefs.get("stt_lang", "ar-EG").substringBefore('-')
        val boundary = "----jarvis" + System.currentTimeMillis()
        val c = URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.doOutput = true
            c.connectTimeout = 8000
            c.readTimeout = 30000
            c.setRequestProperty("Authorization", "Bearer $key")
            c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            c.outputStream.use { out ->
                fun field(n: String, v: String) {
                    out.write("--$boundary\r\nContent-Disposition: form-data; name=\"$n\"\r\n\r\n$v\r\n".toByteArray())
                }
                field("model", prefs.get("stt_model", "whisper-large-v3-turbo"))
                field("response_format", "json")
                if (lang.isNotBlank()) field("language", lang)
                out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"a.m4a\"\r\n" +
                    "Content-Type: audio/mp4\r\n\r\n").toByteArray())
                out.write(f.readBytes())
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw Exception("HTTP $code")
            return JSONObject(body).getString("text").trim()
        } finally {
            c.disconnect()
        }
    }
}
