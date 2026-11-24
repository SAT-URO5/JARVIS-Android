package com.jarvis.mark

import android.content.Context
import java.util.Locale
import kotlin.random.Random

/**
 * The offline "common sense" brain: instant, natural replies to everyday phrases (greetings, thanks,
 * how are you, jokes...). Data lives in assets/smalltalk_*.txt, one topic per line:
 *   arabic phrases ; ... # english phrases ; ... # arabic replies | ... # english replies | ...
 */
object SmallTalk {
    private class Item(val ar: List<String>, val en: List<String>, val arR: List<String>, val enR: List<String>) {
        var last = -1
    }

    private var items: List<Item>? = null

    private fun norm(s: String): String {
        var x = s.lowercase(Locale.ROOT)
        x = x.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        x = x.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        x = x.replace(Regex("[?؟!.,،:;\"]+"), " ").replace("'", "")
        return x.replace(Regex("\\s+"), " ").trim()
    }

    private fun load(ctx: Context): List<Item> {
        items?.let { return it }
        val out = mutableListOf<Item>()
        val names = ctx.assets.list("")?.filter { it.startsWith("smalltalk") && it.endsWith(".txt") }?.sorted() ?: emptyList()
        for (n in names) {
            ctx.assets.open(n).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank() || line.startsWith("//")) continue
                    val f = line.split("#")
                    if (f.size < 4) continue
                    out.add(
                        Item(
                            f[0].split(";").map { norm(it) }.filter { it.isNotEmpty() },
                            f[1].split(";").map { norm(it) }.filter { it.isNotEmpty() },
                            f[2].split("|").map { it.trim() }.filter { it.isNotEmpty() },
                            f[3].split("|").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    )
                }
            }
        }
        items = out
        return out
    }

    /** Returns a reply, or null when the text is not everyday small talk. */
    fun reply(ctx: Context, raw: String): String? {
        val all = load(ctx)
        val arabic = raw.any { it.code in 0x0600..0x06FF }
        val drop = setOf("jarvis", "جارفيس", "جارفس", "جاروفيس", "يا")
        val tokens = norm(raw).split(" ").filter { it.isNotEmpty() && it !in drop }
        if (tokens.isEmpty() || tokens.size > 8) return null
        val padded = " " + tokens.joinToString(" ") + " "
        var best: Item? = null
        var bestScore = 0
        for (item in all) {
            for (p in (if (arabic) item.ar else item.en)) {
                val size = p.split(" ").size
                if (size > bestScore && tokens.size <= size + 1 && padded.contains(" $p ")) {
                    best = item
                    bestScore = size
                }
            }
        }
        val hit = best ?: return null
        val replies = (if (arabic) hit.arR else hit.enR).ifEmpty { if (arabic) hit.enR else hit.arR }
        if (replies.isEmpty()) return null
        var i = Random.nextInt(replies.size)
        if (replies.size > 1 && i == hit.last) i = (i + 1) % replies.size
        hit.last = i
        return replies[i]
    }
}
