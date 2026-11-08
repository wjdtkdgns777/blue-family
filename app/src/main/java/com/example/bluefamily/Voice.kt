package com.example.bluefamily

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode

class Voice(context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val tts = TextToSpeech(context, TtsMode.ON_DEVICE)
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener { if (it < 0) tts.stop() }
        .build()
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, TONE_VOLUME)

    private var ready = false

    var speaking = false
        private set

    private val events = object : EventListener {
        override fun onStart() = Unit
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onDone() = finish()
        override fun onStop() = finish()
        override fun onError(error: TtsError) = finish()
    }

    init {
        tts.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                tts.setLocale(Locale.KOREA)
                tts.setAudioAttributes(attributes)
                ready = true
            }

            override fun onFailure(error: TtsError) {
                Log.w(TAG, "tts $error")
            }
        })
        tts.addEventListener(events)
    }

    fun speak(text: String): Boolean {
        if (!ready || speaking) return false
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        speaking = true
        tts.speak(text)
        return true
    }

    fun chime() {
        if (speaking || audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
        tone.startTone(ToneGenerator.TONE_PROP_ACK, TONE_MS)
        main.postDelayed({ if (!speaking) audio.abandonAudioFocusRequest(focus) }, TONE_MS.toLong())
    }

    fun release() {
        tts.removeEventListener(events)
        tts.release()
        tone.release()
    }

    private fun finish() {
        main.post {
            audio.abandonAudioFocusRequest(focus)
            speaking = false
        }
    }

    private companion object {
        const val TAG = "BlueFamily"
        const val TONE_VOLUME = 60
        const val TONE_MS = 160
    }
}
