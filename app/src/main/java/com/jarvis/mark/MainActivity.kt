package com.jarvis.mark

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.mark.ai.AiRouter
import com.jarvis.mark.commands.CommandEngine
import com.jarvis.mark.voice.CloudStt
import com.jarvis.mark.voice.Speaker

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var reactor: ReactorView
    private lateinit var logView: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var status: TextView
    private lateinit var btnLang: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var speaker: Speaker
    private lateinit var cloud: CloudStt

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private val history = mutableListOf<AiRouter.Turn>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        reactor = findViewById(R.id.reactor)
        logView = findViewById(R.id.log)
        scroll = findViewById(R.id.scroll)
        input = findViewById(R.id.input)
        status = findViewById(R.id.status)
        btnLang = findViewById(R.id.btnLang)
        btnMic = findViewById(R.id.btnMic)

        speaker = Speaker(this, prefs)
        cloud = CloudStt(this, prefs)
        speaker.onStart = { runOnUiThread { reactor.state = ReactorView.State.SPEAKING; status.text = "SPEAKING" } }
        speaker.onDone = { runOnUiThread { reactor.state = ReactorView.State.IDLE; status.text = "READY" } }

        findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnSend).setOnClickListener { sendTyped() }
        input.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_SEND) { sendTyped(); true } else false
        }
        btnMic.setOnClickListener { if (listening) stopListening() else startListening() }
        btnLang.setOnClickListener {
            prefs.put("stt_lang", if (prefs.get("stt_lang", "ar-EG") == "ar-EG") "en-US" else "ar-EG")
            refreshLang()
        }
        refreshLang()
        status.text = "READY"
        log("JARVIS", "At your service. تحت أمرك.")
        if (!AiRouter.hasAnyKey(this)) {
            log("SYSTEM", "No AI key yet. Phone commands work offline. Open settings to add a free key.")
        }
        handleLaunch(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunch(intent)
    }

    private fun handleLaunch(i: Intent?) {
        if (i == null) return
        if (i.getBooleanExtra("listen", false) || i.action == Intent.ACTION_ASSIST || i.action == Intent.ACTION_VOICE_COMMAND) {
            window.decorView.post { startListening() }
        }
    }

    private fun refreshLang() {
        btnLang.text = if (prefs.get("stt_lang", "ar-EG") == "ar-EG") "AR" else "EN"
    }

    private fun sendTyped() {
        val t = input.text.toString().trim()
        input.setText("")
        if (t.isNotEmpty()) handle(t)
    }

    private fun log(who: String, text: String) {
        logView.append("$who> $text\n\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun isArabic(s: String) = s.any { it.code in 0x0600..0x06FF }

    private fun handle(text: String) {
        log("YOU", text)
        val low = text.trim().lowercase()
        if (Regex("^(stop|quiet|silence|اسكت|بس|كفايه|كفاية)[.!؟?]*$").matches(low)) {
            speaker.stop()
            reactor.state = ReactorView.State.IDLE
            return
        }
        val local = try { CommandEngine.run(this, text) } catch (e: Exception) { null }
        if (local != null) { reply(local); return }
        val chat = SmallTalk.reply(this, text)
        if (chat != null) { reply(chat); return }

        reactor.state = ReactorView.State.THINKING
        status.text = "THINKING"
        history.add(AiRouter.Turn("user", text))
        while (history.size > 12) history.removeAt(0)
        val snapshot = history.toList()
        Thread {
            val result = AiRouter.ask(this, snapshot)
            runOnUiThread {
                result.onSuccess {
                    history.add(AiRouter.Turn("assistant", it))
                    reply(it)
                }.onFailure {
                    val msg = if (isArabic(text))
                        "مفيش اتصال بالذكاء الاصطناعي دلوقتي. أوامر الموبايل لسه شغالة بدون نت. (${it.message})"
                    else "I can't reach an AI right now. Phone commands still work offline. (${it.message})"
                    reply(msg)
                }
            }
        }.start()
    }

    private fun reply(text: String) {
        log("JARVIS", text)
        if (text.isNotBlank()) speaker.speak(text) else reactor.state = ReactorView.State.IDLE
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            return
        }
        val mode = prefs.get("stt_mode", "auto")
        val systemOk = SpeechRecognizer.isRecognitionAvailable(this)
        if (mode == "groq" || (mode == "auto" && !systemOk)) { startCloud(); return }
        if (!systemOk) {
            log("SYSTEM", "This phone has no speech-recognition engine. Set Speech engine to groq in Settings, or type.")
            return
        }
        speaker.stop()
        recognizer?.destroy()
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                listening = true
                reactor.state = ReactorView.State.LISTENING
                status.text = "LISTENING"
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                listening = false
                reactor.state = ReactorView.State.IDLE
                status.text = "READY"
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (prefs.get("stt_mode", "auto") == "auto" && prefs.get("key_groq").isNotBlank()) startCloud()
                    else log("SYSTEM", "Speech recognition error $error.")
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                reactor.state = ReactorView.State.IDLE
                val said = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!said.isNullOrBlank()) handle(said) else status.text = "READY"
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.get("stt_lang", "ar-EG"))
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        sr.startListening(i)
    }

    private fun startCloud() {
        if (prefs.get("key_groq").isBlank()) {
            log("SYSTEM", "No speech engine on this phone. Add a free Groq key in Settings (console.groq.com) so I can hear you, or type.")
            return
        }
        speaker.stop()
        listening = true
        reactor.state = ReactorView.State.LISTENING
        status.text = "LISTENING"
        cloud.start { text, err ->
            listening = false
            reactor.state = ReactorView.State.IDLE
            status.text = "READY"
            if (err != null) log("SYSTEM", err)
            else if (!text.isNullOrBlank()) handle(text)
        }
    }

    private fun stopListening() {
        if (cloud.active) { cloud.stopNow(); return }
        recognizer?.stopListening()
        listening = false
        reactor.state = ReactorView.State.IDLE
        status.text = "READY"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startListening()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        cloud.release()
        speaker.shutdown()
        super.onDestroy()
    }
}
