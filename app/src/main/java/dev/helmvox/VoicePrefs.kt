package dev.helmvox

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Voz elegida por el usuario: motor TTS, voz concreta, velocidad, tono y acento con que te escucha. */
data class VoicePrefs(
    val engine: String? = null, // null = motor por defecto del móvil
    val voice: String? = null, // null = voz por defecto del motor
    val rate: Float = 1.0f,
    val pitch: Float = 1.0f,
    val listenLocale: String = defaultListenLocale(),
    val volume: Float = 0.6f, // 0,1 a 1,0: volumen propio de la voz, sobre el del canal
) {
    /** Idioma de la conversación, según el acento elegido. */
    val lang: Lang get() = if (listenLocale.startsWith("en")) Lang.EN else Lang.ES

    /** Aplica voz, velocidad y tono a un TTS ya inicializado. */
    fun applyTo(tts: TextToSpeech) {
        // En español los motores solo traen España y Latinoamérica (es-US): el resto de acentos usa es-US.
        tts.language = Locale.forLanguageTag(
            when {
                lang == Lang.EN -> listenLocale
                listenLocale == "es-ES" -> "es-ES"
                else -> "es-US"
            }
        )
        voice?.let { name -> runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == name } }
            ?.let { tts.voice = it }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_ENGINE, engine)
            .putString(KEY_VOICE, voice)
            .putFloat(KEY_RATE, rate)
            .putFloat(KEY_PITCH, pitch)
            .putString(KEY_LISTEN, listenLocale)
            .putFloat(KEY_VOLUME, volume)
            .apply()
    }

    companion object {
        private const val FILE = "voz"
        private const val KEY_ENGINE = "engine"
        private const val KEY_VOICE = "voice"
        private const val KEY_RATE = "rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_LISTEN = "listen"
        private const val KEY_VOLUME = "volume"

        /** Acentos para el reconocimiento de voz (etiquetas de idioma); el nombre lo pone la pantalla. */
        val LISTEN_LOCALES = listOf(
            "es-CL", "es-AR", "es-MX", "es-CO", "es-PE", "es-US", "es-ES",
            "en-US", "en-GB", "en-AU", "en-IN",
        )

        /** El del móvil si está en la lista; si no, inglés (US) o español latinoamericano según su idioma. */
        private fun defaultListenLocale(): String {
            val device = Locale.getDefault()
            LISTEN_LOCALES.firstOrNull { it.equals(device.toLanguageTag(), ignoreCase = true) }?.let { return it }
            return if (device.language == "en") "en-US" else "es-US"
        }

        fun load(ctx: Context): VoicePrefs {
            val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            return VoicePrefs(
                engine = p.getString(KEY_ENGINE, null),
                voice = p.getString(KEY_VOICE, null),
                rate = p.getFloat(KEY_RATE, 1.0f),
                pitch = p.getFloat(KEY_PITCH, 1.0f),
                listenLocale = p.getString(KEY_LISTEN, null) ?: defaultListenLocale(),
                volume = p.getFloat(KEY_VOLUME, 0.6f),
            )
        }
    }
}
