package dev.helmvox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Dice una frase por el casco sin abrir ninguna pantalla (avisos de batería, mensajes sin
 * permiso para responder). Baja la música mientras habla y la devuelve al terminar.
 */
class Announcer(private val ctx: Context) {

    private val main = Handler(Looper.getMainLooper())
    private val am = ctx.getSystemService(AudioManager::class.java)
    private val warmup = SilentStream()

    fun say(text: String) {
        val speaker = Speaker(ctx)
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        am.requestAudioFocus(focus)
        // Mismo truco que en las respuestas: que el fundido de entrada del casco pase en silencio.
        warmup.start()
        main.postDelayed({
            speaker.say(text) {
                warmup.stop()
                am.abandonAudioFocusRequest(focus)
                speaker.shutdown()
            }
        }, WARMUP_MS)
    }

    private companion object {
        const val WARMUP_MS = 1300L
    }
}
