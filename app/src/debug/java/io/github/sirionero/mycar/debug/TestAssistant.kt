package io.github.sirionero.mycar.debug

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.util.Log

/*
 * Debug builds only: a stand-in voice assistant, for the Car preview workflow to make the
 * phone's default assistant and see how Android starts it. An assistant learns how it was called
 * from onShow's flags and arguments - the swipe up from a bottom corner, say, against a typed
 * request - and decides from them whether to open listening. The workflow starts it once from
 * myCar's microphone and once with the swipe itself, and compares the two log lines.
 */

class TestAssistantService : VoiceInteractionService()

class TestAssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = TestAssistantSession(this)
}

class TestAssistantSession(context: Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        @Suppress("DEPRECATION")
        val extras = args?.let { b -> b.keySet().sorted().joinToString { "$it=${b.get(it)}" } }.orEmpty()
        val gesture = (showFlags and VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE) != 0
        Log.i("myCar", "test assistant: shown flags=$showFlags gesture=$gesture args=[$extras]")
    }
}

/** Android won't take a voice assistant without a speech recognizer to go with it. This one hears nothing. */
class TestRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: RecognitionService.Callback?) = Unit
    override fun onCancel(listener: RecognitionService.Callback?) = Unit
    override fun onStopListening(listener: RecognitionService.Callback?) = Unit
}
