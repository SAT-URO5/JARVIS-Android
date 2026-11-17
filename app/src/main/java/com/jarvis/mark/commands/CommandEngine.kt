package com.jarvis.mark.commands

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Commands that run on the phone itself, with no internet and no AI.
 * run() returns the reply to speak, or null when the text is not a command
 * (the caller then asks the AI instead). Arabic and English.
 */
object CommandEngine {

    private val APPS = mapOf(
        "سبوتيفاي" to "spotify", "انغامي" to "anghami", "يوتيوب" to "youtube", "واتساب" to "whatsapp",
        "واتس اب" to "whatsapp", "واتس" to "whatsapp", "كروم" to "chrome", "جوجل" to "google",
        "الكاميرا" to "camera", "كاميرا" to "camera", "الاعدادات" to "settings", "اعدادات" to "settings",
        "تيليجرام" to "telegram", "تليجرام" to "telegram", "فيسبوك" to "facebook", "فيس بوك" to "facebook",
        "انستجرام" to "instagram", "انستا" to "instagram", "جيميل" to "gmail", "الخرائط" to "maps",
        "خرائط" to "maps", "الصور" to "photos", "المعرض" to "gallery", "الهاتف" to "phone",
        "الاتصال" to "phone", "جهات الاتصال" to "contacts", "الساعه" to "clock", "الحاسبه" to "calculator",
        "الاله الحاسبه" to "calculator", "تيك توك" to "tiktok", "نتفليكس" to "netflix", "ديسكورد" to "discord",
        "المتجر" to "play store", "بلاي ستور" to "play store", "الملفات" to "files", "الرسائل" to "messages"
    )

    private fun norm(s: String): String {
        var x = s.lowercase(Locale.ROOT)
        x = x.replace(Regex("[\\u064B-\\u065F\\u0670]"), "")
        x = x.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا').replace('ة', 'ه').replace('ى', 'ي')
        val ar = "٠١٢٣٤٥٦٧٨٩"
        x = x.map { ch -> val i = ar.indexOf(ch); if (i >= 0) ('0' + i) else ch }.joinToString("")
        return x.trim()
    }

    private fun isArabic(s: String) = s.any { it.code in 0x0600..0x06FF }

    fun run(ctx: Context, raw: String): String? {
        val t = norm(raw).replace(Regex("[?؟!.,،]+$"), "").trim()
        val ar = isArabic(raw)
        val short = t.split(" ").size <= 6
        fun r(en: String, arText: String) = if (ar) arText else en
        try {
            // ---- flashlight
            if (short && Regex("flashlight|torch|فلاش|كشاف").containsMatchIn(t)) {
                val off = Regex("\\boff\\b|اطفي|اطفى|طفي|اقفل|وقف").containsMatchIn(t)
                return if (torch(ctx, !off)) r(if (off) "Flashlight off." else "Flashlight on.",
                    if (off) "تم إطفاء الكشاف." else "تم تشغيل الكشاف.")
                else r("This phone has no flashlight I can reach.", "مش لاقي فلاش في الجهاز ده.")
            }
            // ---- volume
            if (short && Regex("volume|الصوت|صوت").containsMatchIn(t) && !Regex("اغني|ميوزك|موسيقي|song|music|play|شغل").containsMatchIn(t)) {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val reply: String? = when {
                    Regex("mute|كتم|صامت").containsMatchIn(t) -> {
                        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                        r("Muted.", "تم كتم الصوت.")
                    }
                    Regex("\\bup\\b|raise|increase|louder|ارفع|علي|زود|اعلي").containsMatchIn(t) -> {
                        repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }
                        r("Volume up.", "رفعت الصوت.")
                    }
                    Regex("down|lower|decrease|quieter|وطي|قلل|اخفض").containsMatchIn(t) -> {
                        repeat(3) { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) }
                        r("Volume down.", "وطيت الصوت.")
                    }
                    else -> null
                }
                if (reply != null) return reply
            }
            // ---- battery
            if (short && Regex("battery|بطاريه|الشحن").containsMatchIn(t)) {
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                return r("Battery is at $pct percent.", "البطارية على $pct في المية.")
            }
            // ---- time / date
            if (Regex("^(what time is it|what is the time|what's the time|the time|time|الساعه كام|كام الساعه|الساعه كم|الوقت كام|الوقت)$").containsMatchIn(t)) {
                val now = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
                return r("It's $now.", "الساعة $now.")
            }
            if (Regex("^(what is the date|what's the date|what is today's date|what's today's date|today's date|date|التاريخ|النهارده كام|النهارده ايه|اليوم ايه)$").containsMatchIn(t)) {
                val d = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))
                return r("Today is $d.", "النهارده $d.")
            }
            // ---- timer
            Regex("(timer|مؤقت|تايمر|عداد).*?(\\d+)\\s*(second|sec|ثانيه|ثواني|minute|min|دقيقه|دقايق|دقائق|hour|ساعه|ساعات)").find(t)?.let { m ->
                val n = m.groupValues[2].toInt()
                val unit = m.groupValues[3]
                val secs = when {
                    unit.startsWith("sec") || unit.startsWith("ثان") -> n
                    unit.startsWith("min") || unit.startsWith("دق") -> n * 60
                    else -> n * 3600
                }
                ctx.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, secs)
                    .putExtra(AlarmClock.EXTRA_SKIP_UI, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return r("Timer set.", "ظبطت المؤقت.")
            }
            // ---- alarm
            Regex("(alarm|wake me|منبه|صحيني|صحي).*?(\\d{1,2})(?:[:.](\\d{2}))?").find(t)?.let { m ->
                var h = m.groupValues[2].toInt()
                val min = m.groupValues[3].ifEmpty { "0" }.toInt()
                val pm = Regex("\\bpm\\b|مساء|بليل|العصر|الظهر").containsMatchIn(t)
                if (pm && h < 12) h += 12
                if (!pm && h == 12 && Regex("\\bam\\b|صباح|فجر").containsMatchIn(t)) h = 0
                if (h in 0..23 && min in 0..59) {
                    ctx.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, min).putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    return r("Alarm set for %d:%02d.".format(h, min), "ظبطت المنبه على %d:%02d.".format(h, min))
                }
            }
            // ---- calculator
            val calc = Regex("^(calculate|calc|احسب|حساب)\\s+(.+)$").find(t)?.groupValues?.get(2)
                ?: t.takeIf { Regex("^[\\d\\s+\\-*/().x×÷]+$").matches(it) && Regex("[+\\-*/x×÷]").containsMatchIn(it) }
            if (calc != null) {
                val v = Calc(calc.replace("x", "*").replace("×", "*").replace("÷", "/")).evaluate()
                if (v != null) {
                    val s = if (v == Math.floor(v) && Math.abs(v) < 1e15) v.toLong().toString() else "%.6f".format(Locale.ENGLISH, v).trimEnd('0').trimEnd('.')
                    return r("The answer is $s.", "الناتج $s.")
                }
            }
            // ---- media transport
            if (Regex("^(next|skip)( song| track)?$|^(التالي|التاليه|الاغنيه الجايه|الاغنيه اللي بعدها|بعدها)$").containsMatchIn(t)) { media(ctx, KeyEvent.KEYCODE_MEDIA_NEXT); return r("Next.", "التالية.") }
            if (Regex("^(previous|go back|previous song|السابق|السابقه|اللي قبلها|الاغنيه اللي فاتت)$").containsMatchIn(t)) { media(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS); return r("Previous.", "السابقة.") }
            if (Regex("^(pause|pause music|pause song|وقف|اوقف|ايقاف|وقف الاغنيه|وقف الموسيقي|وقف الميوزك)$").containsMatchIn(t)) { media(ctx, KeyEvent.KEYCODE_MEDIA_PAUSE); return r("Paused.", "وقفت.") }
            if (Regex("^(resume|resume music|continue|كمل|استمر|كملي|كمل الاغنيه|كمل الموسيقي)$").containsMatchIn(t)) { media(ctx, KeyEvent.KEYCODE_MEDIA_PLAY); return r("Resuming.", "كملت.") }
            // ---- play music
            val musicWords = "اغنيه|اغاني|اغنيات|ميوزك|موسيقي|song|songs|music|track"
            if (Regex("(play|شغل|شغلي|شغللي|سمعني)").containsMatchIn(t) && Regex(musicWords).containsMatchIn(t) && !Regex("youtube|يوتيوب").containsMatchIn(t)) {
                val pkg = when {
                    Regex("spotify|سبوتيفاي").containsMatchIn(t) -> "com.spotify.music"
                    Regex("anghami|انغامي").containsMatchIn(t) -> "com.anghami"
                    else -> null
                }
                val stop = setOf("play", "شغلي", "شغللي", "شغل", "سمعني", "على", "علي", "في", "من", "on", "in", "with",
                    "spotify", "سبوتيفاي", "anghami", "انغامي") + musicWords.split("|")
                val q = t.split(" ").filter { it.isNotEmpty() && it !in stop }.joinToString(" ")
                val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    .putExtra(SearchManager.QUERY, q)
                if (pkg != null) i.setPackage(pkg)
                val launch = if (pkg == null) Intent.createChooser(i, "Music") else i
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try {
                    ctx.startActivity(launch)
                    if (q.isEmpty()) r("Playing music.", "شغلت الموسيقى.") else r("Playing $q.", "بشغل $q.")
                } catch (e: Exception) {
                    r("I couldn't find a music app that can do that.", "مش لاقي تطبيق موسيقى يقدر يعمل كده.")
                }
            }
            // ---- youtube
            if (Regex("youtube|يوتيوب").containsMatchIn(t) && Regex("search|ابحث|دور|شغل|play|فيديو").containsMatchIn(t)) {
                val yStop = setOf("youtube", "يوتيوب", "search", "ابحث", "دور", "شغل", "play", "عن", "علي", "على", "في", "for", "on", "فيديو")
                val q = t.split(" ").filter { it.isNotEmpty() && it !in yStop }.joinToString(" ")
                web(ctx, "https://www.youtube.com/results?search_query=" + Uri.encode(q))
                return r("Searching YouTube for $q.", "بدور في يوتيوب على $q.")
            }
            // ---- web search
            Regex("^(search for|search|google|ابحث عن|ابحث|دور علي|دورلي علي|دور على)\\s+(.+)$").find(t)?.let { m ->
                web(ctx, "https://www.google.com/search?q=" + Uri.encode(m.groupValues[2]))
                return r("Searching for ${m.groupValues[2]}.", "بدور على ${m.groupValues[2]}.")
            }
            // ---- navigation
            Regex("^(navigate to|directions to|take me to|وديني|وصلني|اتجاهات الي|اتجاهات|خرائط)\\s*(.+)$").find(t)?.let { m ->
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(m.groupValues[2]))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return r("Opening the map.", "فتحت الخريطة.")
            }
            // ---- call (dials the number; you press the green button)
            Regex("^(call|dial|اتصل ب|اتصل|كلم)\\s*(.*)$").find(t)?.let { m ->
                val num = m.groupValues[2].replace(Regex("[^0-9+]"), "")
                return if (num.length >= 3) {
                    ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$num")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    r("Dialing $num.", "بطلب $num.")
                } else r("Say the number. Calling by contact name is coming in the next version.", "قولّي الرقم. الاتصال بالاسم هيجي في النسخة الجاية.")
            }
            // ---- email draft
            Regex("^(email|mail|ابعت ايميل|اكتب ايميل|ايميل)\\s*(.*)$").find(t)?.let { m ->
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_TEXT, raw.substringAfter(' ', "")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ctx.startActivity(i)
                return r("Opening an email draft.", "فتحت مسودة إيميل.")
            }
            // ---- settings pages
            val settingsAction = when {
                Regex("wifi|wi-fi|واي فاي").containsMatchIn(t) -> Settings.ACTION_WIFI_SETTINGS
                Regex("bluetooth|بلوتوث").containsMatchIn(t) -> Settings.ACTION_BLUETOOTH_SETTINGS
                Regex("airplane|طيران").containsMatchIn(t) -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
                Regex("location|gps|الموقع").containsMatchIn(t) -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                Regex("brightness|السطوع|الاضاءه").containsMatchIn(t) -> Settings.ACTION_DISPLAY_SETTINGS
                else -> null
            }
            if (settingsAction != null) {
                ctx.startActivity(Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return r("Opening the settings page. Android doesn't let apps flip this switch directly.",
                    "فتحتلك صفحة الإعدادات. أندرويد مش بيسمح للتطبيقات تغيّرها بنفسها.")
            }
            // ---- open an app
            Regex("^(open|launch|start|افتح|شغل|روح علي|ادخل علي|ادخل)\\s+(.+)$").find(t)?.let { m ->
                return openApp(ctx, m.groupValues[2].trim(), ar)
            }
        } catch (e: Exception) {
            return if (ar) "حصلت مشكلة وأنا بنفذ الأمر." else "Something went wrong running that command."
        }
        return null
    }

    private fun web(ctx: Context, url: String) {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun media(ctx: Context, code: Int) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private fun torch(ctx: Context, on: Boolean): Boolean {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return false
        cm.setTorchMode(id, on)
        return true
    }

    private fun openApp(ctx: Context, name: String, ar: Boolean): String {
        val pm = ctx.packageManager
        val query = (APPS[name] ?: name).lowercase(Locale.ROOT)
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(main, 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.ROOT).contains(query) }
            ?: apps.firstOrNull { it.activityInfo.packageName.lowercase(Locale.ROOT).contains(query) }
        val launch = hit?.let { pm.getLaunchIntentForPackage(it.activityInfo.packageName) }
        if (hit == null || launch == null) {
            return if (ar) "مش لاقي تطبيق اسمه $name." else "I couldn't find an app called $name."
        }
        ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val label = hit.loadLabel(pm).toString()
        return if (ar) "بفتح $label." else "Opening $label."
    }

    /** Small recursive-descent calculator: + - * / and parentheses. */
    private class Calc(private val s: String) {
        private var i = 0
        private fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        private fun ws() { while (peek() == ' ') i++ }

        fun evaluate(): Double? = try {
            val v = expr(); ws(); if (i == s.length && !v.isNaN() && !v.isInfinite()) v else null
        } catch (e: Exception) { null }

        private fun expr(): Double {
            var v = term()
            while (true) {
                ws()
                when (peek()) {
                    '+' -> { i++; v += term() }
                    '-' -> { i++; v -= term() }
                    else -> return v
                }
            }
        }

        private fun term(): Double {
            var v = factor()
            while (true) {
                ws()
                when (peek()) {
                    '*' -> { i++; v *= factor() }
                    '/' -> { i++; v /= factor() }
                    else -> return v
                }
            }
        }

        private fun factor(): Double {
            ws()
            if (peek() == '-') { i++; return -factor() }
            if (peek() == '(') {
                i++
                val v = expr()
                ws()
                if (peek() == ')') i++
                return v
            }
            val start = i
            while (peek().isDigit() || peek() == '.') i++
            return s.substring(start, i).toDouble()
        }
    }
}
