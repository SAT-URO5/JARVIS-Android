package com.jarvis.mark.service

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import com.jarvis.mark.MainActivity

/** Makes JARVIS selectable as the phone's default digital assistant. */
class JarvisVoiceService : VoiceInteractionService()

class JarvisSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = JarvisSession(this)
}

/** Runs when the user triggers the assistant (corner swipe, power-button hold, etc.). */
class JarvisSession(ctx: Context) : VoiceInteractionSession(ctx) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val i = Intent(context, MainActivity::class.java).putExtra("listen", true)
        startAssistantActivity(i)
    }
}

/** Required by the assistant declaration. Real recognition happens inside MainActivity. */
class JarvisRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        listener?.error(SpeechRecognizer.ERROR_CLIENT)
    }
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
