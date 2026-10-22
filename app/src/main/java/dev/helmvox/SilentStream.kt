package dev.helmvox

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.random.Random

/**
 * Sonido inaudible por el canal de música (A2DP). El casco hace un fundido de entrada cada vez
 * que empieza a recibir audio; con esto el fundido pasa durante el silencio y la voz llega ya a
 * volumen completo. Se mantiene mientras habla para que el canal no se vuelva a dormir.
 *
 * Es ruido de ±1 sobre 32768 en lugar de ceros puros, por si algún sistema corta el silencio exacto.
 */
class SilentStream {

    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        Thread {
            val minBuffer = AudioTrack.getMinBufferSize(RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(minBuffer * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            val chunk = ShortArray(minBuffer / 2) { (Random.nextInt(3) - 1).toShort() }
            try {
                track.play()
                // write() bloquea al ritmo de reproducción, así que el bucle no gasta CPU de más.
                while (running) track.write(chunk, 0, chunk.size)
                track.stop()
            } finally {
                track.release()
            }
        }.start()
    }

    fun stop() {
        running = false
    }

    private companion object {
        const val RATE = 16_000
    }
}
