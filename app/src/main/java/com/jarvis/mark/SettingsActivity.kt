package com.jarvis.mark

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.mark.voice.Speaker

/** Built in code so the whole screen is one file. Everything saves to private app storage on the phone. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val fields = mutableListOf<Pair<String, EditText>>()
    private var speaker: Speaker? = null
    private var chosenVoice = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(title("SETTINGS"))
        root.addView(note("Add at least one free API key to get AI answers. Without keys, phone commands still work offline. Keys stay on this phone."))

        root.addView(title("AI PROVIDERS (tried in this order)"))
        root.addView(field("Order", "order", "gemini,groq,openrouter,mistral,cerebras,custom", false))
        for (p in Prefs.PROVIDERS) {
            if (p == "custom") {
                root.addView(field("Custom base URL (Ollama on your Mac, etc.)", "base_custom", "", false))
                root.addView(field("Custom API key (optional)", "key_custom", "", true))
                root.addView(field("Custom model", "model_custom", "", false))
            } else {
                root.addView(field("$p API key", "key_$p", "", true))
                root.addView(field("$p model", "model_$p", Prefs.defaultModel(p), false))
            }
        }

        root.addView(title("VOICE"))
        root.addView(field("Speech language (ar-EG or en-US)", "stt_lang", "ar-EG", false))
        root.addView(field("English voice language tag", "tts_lang", "en-US", false))
        root.addView(field("Pitch (lower = deeper, 0.5 to 1.5)", "pitch", "0.85", false))
        root.addView(field("Speed (0.5 to 2.0)", "rate", "1.0", false))
        root.addView(note("Voice installed on this phone:"))
        val spinner = Spinner(this)
        root.addView(spinner)
        speaker = Speaker(this, prefs).also { sp ->
            sp.onReady = {
                runOnUiThread {
                    val names = listOf("(default)") + sp.voiceNames()
                    spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
                    val cur = prefs.get("voice_name")
                    spinner.setSelection(names.indexOf(cur).coerceAtLeast(0))
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            chosenVoice = if (pos == 0) "" else names[pos]
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                }
            }
        }
        chosenVoice = prefs.get("voice_name")

        val test = Button(this).apply {
            text = "Test voice"
            setOnClickListener { save(); speaker?.speak("Good evening. All systems are online.") }
        }
        root.addView(test)
        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener { save(); finish() }
        }
        root.addView(saveBtn)

        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#05080F")); addView(root) })
    }

    private fun save() {
        for ((k, e) in fields) prefs.put(k, e.text.toString().trim())
        prefs.put("voice_name", chosenVoice)
    }

    private fun title(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#00E5FF")); textSize = 16f; setPadding(0, 28, 0, 8)
    }

    private fun note(t: String) = TextView(this).apply {
        text = t; setTextColor(Color.parseColor("#7FB6C4")); textSize = 12f; setPadding(0, 0, 0, 8)
    }

    private fun field(label: String, key: String, def: String, secret: Boolean): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = label; setTextColor(Color.parseColor("#CDEFFF")); textSize = 12f })
        val e = EditText(this).apply {
            setText(prefs.get(key, def))
            setTextColor(Color.WHITE)
            setSingleLine()
            if (secret) inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        fields.add(key to e)
        box.addView(e)
        return box
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }
}
