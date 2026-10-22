package dev.helmvox

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Texto a voz en español; cada frase avisa cuando termina de sonar. */
class Speaker(ctx: Context) : TextToSpeech.OnInitListener {

    private val main = Handler(Looper.getMainLooper())
    private val prefs = VoicePrefs.load(ctx)
    private val tts = TextToSpeech(ctx.applicationContext, this, prefs.engine)
    private val pending = mutableListOf<Pair<String, () -> Unit>>()
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    private var state = State.INIT

    /** true: sonar por el canal de llamada (enlace SCO del casco). */
    var useCallChannel = false

    private enum class State { INIT, READY, FAILED }

    override fun onInit(status: Int) {
        state = if (status == TextToSpeech.SUCCESS) State.READY else State.FAILED
        if (state == State.READY) {
            prefs.applyTo(tts)
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String) {}
                override fun onDone(id: String) = finish(id)
                @Deprecated("Deprecated in Java")
                override fun onError(id: String) = finish(id)
                override fun onError(id: String, errorCode: Int) = finish(id)
            })
        }
        pending.toList().also { pending.clear() }.forEach { (text, then) -> say(text, then) }
    }

    fun say(text: String, then: () -> Unit = {}) {
        when (state) {
            State.INIT -> pending += text to then
            State.FAILED -> main.post(then)
            State.READY -> {
                tts.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(
                            if (useCallChannel) AudioAttributes.USAGE_VOICE_COMMUNICATION
                            else AudioAttributes.USAGE_ASSISTANT
                        )
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                val id = UUID.randomUUID().toString()
                callbacks[id] = then
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.volume) }
                if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) finish(id)
            }
        }
    }

    fun shutdown() {
        callbacks.clear()
        tts.stop()
        tts.shutdown()
    }

    private fun finish(id: String) {
        callbacks.remove(id)?.let { main.post(it) }
    }
}
