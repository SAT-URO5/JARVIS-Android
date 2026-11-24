package com.jarvis.mark

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jarvis.mark.ai.ModelCatalog
import com.jarvis.mark.voice.Speaker

/** Built in code so the whole screen is one file. Everything saves to private app storage on the phone. */
class SettingsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val savers = mutableListOf<() -> Unit>()
    private var speaker: Speaker? = null
    private var voiceChoice = ""

    private class DarkAdapter(ctx: android.content.Context, items: List<String>) :
        ArrayAdapter<String>(ctx, android.R.layout.simple_spinner_item, items) {
        init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            (super.getView(position, convertView, parent) as TextView).apply { setTextColor(Color.WHITE) }
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
            (super.getDropDownView(position, convertView, parent) as TextView).apply {
                setTextColor(Color.WHITE); setBackgroundColor(Color.parseColor("#0B1220")); setPadding(32, 24, 32, 24)
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }

        root.addView(title("SETTINGS"))
        root.addView(note("Add at least one free API key to get AI answers. Without keys, phone commands and everyday chat still work offline. Keys stay on this phone."))

        root.addView(title("AI PROVIDERS"))
        for (p in Prefs.PROVIDERS) {
            if (p == "custom") {
                root.addView(field("Custom server URL (e.g. Ollama on your Mac: http://192.168.1.5:11434/v1)", "base_custom", "", false))
                root.addView(field("Custom key (optional)", "key_custom", "", true))
                root.addView(field("Custom model name", "model_custom", "", false))
            } else {
                root.addView(field("$p API key", "key_$p", "", true))
                root.addView(choice("$p model (auto tries the list for you)", "model_$p", listOf("auto") + ModelCatalog.candidates(prefs, p), "auto"))
            }
        }
        val refresh = Button(this)
        refresh.text = "Refresh model lists from my keys"
        refresh.setOnClickListener {
            save()
            refresh.isEnabled = false
            refresh.text = "Refreshing..."
            Thread {
                val msgs = Prefs.PROVIDERS.filter { it != "custom" && prefs.get("key_$it").isNotBlank() }
                    .map { ModelCatalog.refresh(prefs, it) }
                runOnUiThread {
                    Toast.makeText(this, if (msgs.isEmpty()) "Add a key first." else msgs.joinToString("\n"), Toast.LENGTH_LONG).show()
                    recreate()
                }
            }.start()
        }
        root.addView(refresh)
        root.addView(field("Provider order", "order", "gemini,groq,openrouter,mistral,cerebras,custom", false))

        root.addView(title("VOICE (OUTPUT)"))
        root.addView(note("Online with a Gemini key you get a deep male voice. Offline it uses your phone's voice, pitched lower."))
        root.addView(choice("Voice engine", "tts_mode", listOf("auto", "gemini", "system"), "auto"))
        root.addView(choice("Gemini voice (male)", "gem_voice", listOf("Charon", "Orus", "Fenrir", "Puck", "Algenib", "Iapetus", "Umbriel", "Enceladus"), "Charon"))
        root.addView(field("Offline voice pitch (lower = deeper, 0.5 to 1.5)", "pitch", "0.7", false))
        root.addView(field("Speed (0.5 to 2.0)", "rate", "1.0", false))
        root.addView(field("English offline voice language tag", "tts_lang", "en-US", false))
        root.addView(note("Offline voice installed on this phone:"))
        val spinner = Spinner(this)
        root.addView(spinner)
        voiceChoice = prefs.get("voice_name")
        speaker = Speaker(this, prefs).also { sp ->
            sp.onReady = {
                runOnUiThread {
                    val names = listOf("(auto)") + sp.voiceNames()
                    spinner.adapter = DarkAdapter(this, names)
                    spinner.setSelection(names.indexOf(voiceChoice).coerceAtLeast(0))
                    spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            voiceChoice = if (pos == 0) "" else names[pos]
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
                }
            }
        }

        root.addView(title("MICROPHONE (INPUT)"))
        root.addView(note("On phones without Google services (Huawei), choose groq and add a free Groq key above."))
        root.addView(choice("Speech engine", "stt_mode", listOf("auto", "system", "groq"), "auto"))
        root.addView(choice("Speech language", "stt_lang", listOf("ar-EG", "en-US"), "ar-EG"))

        val test = Button(this)
        test.text = "Test voice"
        test.setOnClickListener { save(); speaker?.speak("Good evening, sir. All systems are online.") }
        root.addView(test)
        val saveBtn = Button(this)
        saveBtn.text = "Save"
        saveBtn.setOnClickListener { save(); finish() }
        root.addView(saveBtn)

        setContentView(ScrollView(this).apply { setBackgroundColor(Color.parseColor("#05080F")); addView(root) })
    }

    private fun save() {
        for (s in savers) s()
        prefs.put("voice_name", voiceChoice)
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
        savers.add { prefs.put(key, e.text.toString().trim()) }
        box.addView(e)
        return box
    }

    private fun choice(label: String, key: String, options: List<String>, def: String): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = label; setTextColor(Color.parseColor("#CDEFFF")); textSize = 12f })
        val cur = prefs.get(key, def)
        val opts = if (cur in options) options else options + cur
        val sp = Spinner(this)
        sp.adapter = DarkAdapter(this, opts)
        sp.setSelection(opts.indexOf(cur).coerceAtLeast(0))
        savers.add { prefs.put(key, sp.selectedItem as String) }
        box.addView(sp)
        return box
    }

    override fun onDestroy() {
        speaker?.shutdown()
        super.onDestroy()
    }
}
