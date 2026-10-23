package dev.helmvox

import org.json.JSONObject

/**
 * Contrato entre la IA y la app: el esquema JSON que la IA debe rellenar y cómo se convierte
 * en un [Command]. Sin dependencias de red, para poder probarlo en el PC.
 */
object AiIntent {

    val INTENTS = listOf(
        "navigate", "call", "redial", "send_message", "read_messages", "share_location", "where_am_i",
        "play_music", "pause_music", "next_track", "previous_track", "play_search", "volume_up", "volume_down",
        "intercom_mode", "group_mute", "mic_toggle", "share_music", "share_radio", "intercom_call", "intercom_end",
        "radio_on", "radio_off", "radio_next", "radio_previous", "radio_station",
        "time", "weather", "battery", "help", "cancel", "unknown",
    )

    /** Instrucciones para la IA: qué significa cada intención y qué campos rellenar. */
    val SYSTEM_PROMPT = """
        Eres el intérprete de órdenes de HelmVox, un asistente de voz para motoristas que hablan en
        español (de cualquier país) o en inglés a través de un intercomunicador de casco. Recibes la
        transcripción de lo que dijo el motorista (puede tener errores de reconocimiento de voz) y
        devuelves UNA intención con sus datos. No converses: solo clasifica. Los datos (destino,
        nombre, texto, búsqueda) se copian en el idioma en que se dijeron.

        Intenciones:
        - navigate: ir a un lugar. destination = el lugar tal como se dijo ("casa", "Valparaíso", "gasolinera").
        - call: llamar a alguien. who = nombre o número.
        - redial: repetir la última llamada.
        - send_message: enviar un mensaje. who = destinatario, text = el texto a enviar (null si no lo dijo).
        - read_messages: leer mensajes/notificaciones/WhatsApp recibidos.
        - share_location: enviar mi ubicación. who = destinatario.
        - where_am_i: decir dónde estoy.
        - play_music / pause_music / next_track / previous_track: control de la música.
        - play_search: poner música concreta. query = artista, canción o lista.
        - volume_up / volume_down: subir o bajar el volumen (p. ej. "está muy fuerte" = volume_down).
        - intercom_mode: cambiar el intercom del casco. want_dmc = true (DMC/malla), false (Bluetooth), null (alternar).
        - group_mute: silenciar o reactivar el grupo DMC.
        - mic_toggle: silenciar o activar mi micrófono hacia el grupo.
        - share_music / share_radio: compartir la música o la radio con el intercom.
        - intercom_call: abrir el intercom Bluetooth. channel = 1, 2 o 3 (A, B, C); 1 si no se dice.
        - intercom_end: cortar el intercom.
        - radio_on / radio_off / radio_next / radio_previous: radio FM del casco.
        - radio_station: poner la emisora guardada número station (1 a 6).
        - time: qué hora es.
        - weather: el tiempo / clima / si va a llover / temperatura.
        - battery: batería. battery_of = "phone", "headset" o "both".
        - help: qué puede hacer el asistente. cancel: el usuario cancela.
        - unknown: no es ninguna de las anteriores o no se entiende.

        Rellena solo los campos que use la intención; el resto, null.
    """.trimIndent()

    /** Esquema JSON de la respuesta (salida estructurada: todos los campos requeridos, nulos si no aplican). */
    fun schema(): Map<String, Any> {
        fun nullable(type: String) = mapOf("type" to listOf(type, "null"))
        val props = linkedMapOf<String, Any>(
            "intent" to mapOf("type" to "string", "enum" to INTENTS),
            "destination" to nullable("string"),
            "who" to nullable("string"),
            "text" to nullable("string"),
            "query" to nullable("string"),
            "want_dmc" to nullable("boolean"),
            "channel" to nullable("integer"),
            "station" to nullable("integer"),
            "battery_of" to nullable("string"), // "phone", "headset" o "both" (ver SYSTEM_PROMPT)
        )
        return mapOf(
            "type" to "object",
            "properties" to props,
            "required" to props.keys.toList(),
            "additionalProperties" to false,
        )
    }

    /** JSON de la IA → orden de la app (null si no hay una orden clara o faltan datos). */
    fun toCommand(json: JSONObject): Command? {
        fun str(key: String) = json.optString(key, "").takeIf { !json.isNull(key) && it.isNotBlank() }?.trim()
        fun int(key: String) = if (json.isNull(key) || !json.has(key)) null else json.optInt(key)
        return when (json.optString("intent")) {
            "navigate" -> str("destination")?.let { Command.Navigate(it) }
            "call" -> str("who")?.let { Command.Call(it) }
            "redial" -> Command.Redial
            "send_message" -> str("who")?.let { Command.Message(it, str("text")) }
            "read_messages" -> Command.ReadMessages
            "share_location" -> str("who")?.let { Command.ShareLocation(it) }
            "where_am_i" -> Command.WhereAmI
            "play_music" -> Command.Media(MediaAction.PLAY)
            "pause_music" -> Command.Media(MediaAction.PAUSE)
            "next_track" -> Command.Media(MediaAction.NEXT)
            "previous_track" -> Command.Media(MediaAction.PREVIOUS)
            "play_search" -> str("query")?.let { Command.PlaySearch(it) } ?: Command.Media(MediaAction.PLAY)
            "volume_up" -> Command.Volume(up = true)
            "volume_down" -> Command.Volume(up = false)
            "intercom_mode" -> Command.IntercomMode(if (json.isNull("want_dmc")) null else json.optBoolean("want_dmc"))
            "group_mute" -> Command.DmcGroupMute
            "mic_toggle" -> Command.DmcMic
            "share_music" -> Command.ShareMusic
            "share_radio" -> Command.ShareRadio
            "intercom_call" -> Command.IntercomCall((int("channel") ?: 1).coerceIn(1, 3))
            "intercom_end" -> Command.IntercomEnd
            "radio_on" -> Command.Radio(RadioAction.ON)
            "radio_off" -> Command.Radio(RadioAction.OFF)
            "radio_next" -> Command.Radio(RadioAction.NEXT)
            "radio_previous" -> Command.Radio(RadioAction.PREVIOUS)
            "radio_station" -> int("station")?.takeIf { it in 1..6 }?.let { Command.Radio(RadioAction.STATION, it) }
            "time" -> Command.Time
            "weather" -> Command.Weather
            "battery" -> Command.Battery(
                when (str("battery_of")) {
                    "phone" -> BatteryOf.PHONE
                    "headset" -> BatteryOf.HEADSET
                    else -> BatteryOf.BOTH
                }
            )
            "help" -> Command.Help
            "cancel" -> Command.Cancel
            else -> null
        }
    }
}
