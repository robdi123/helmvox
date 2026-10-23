package dev.helmvox

/** Idioma de la conversación por voz (lo que escucha y lo que responde). */
enum class Lang { ES, EN }

/**
 * Todo lo que HelmVox dice en voz alta, en cada idioma. La interfaz (pantallas) usa los recursos
 * de Android (res/values*); esto es solo la voz, que sigue al acento elegido en "Voz e idioma".
 */
class Phrases(val lang: Lang) {
    private val es = lang == Lang.ES
    private fun t(es: String, en: String) = if (this.es) es else en

    // --- Escuchar ---
    fun sayCommand() = t("Di una orden", "Say a command")
    fun noHeadsetMic() = t("Sin casco: te escucho por el micrófono del móvil", "No helmet: listening through the phone's mic")
    fun noRecognizer() = t("Este móvil no tiene reconocimiento de voz disponible.", "Speech recognition isn't available on this phone.")
    fun didntHear() = t("No te oigo, repite.", "I didn't catch that, say it again.")
    fun givingUp() = t("Vale, lo dejo.", "OK, never mind.")
    fun recognizerDown() = t("El reconocimiento de voz no responde.", "Speech recognition isn't responding.")
    fun languageMissing() = t("El reconocimiento de voz no tiene este idioma instalado.", "Speech recognition doesn't have this language installed.")
    fun recognitionError(code: Int) = t("Error de reconocimiento, código $code.", "Speech recognition error, code $code.")
    fun notUnderstood() = t(
        "No te entendí. Puedo ayudarte con mensajes, música, rutas, llamadas, batería, la radio o el casco. Dime.",
        "I didn't understand. I can help with messages, music, directions, calls, battery, the radio or your helmet. Go ahead.",
    )
    fun notUnderstoodAgain() = t("Tampoco te entendí. Inténtalo otra vez cuando quieras.", "Still didn't get that. Try again whenever you like.")

    // --- Respuestas cortas ---
    fun ok() = t("Vale.", "OK.")
    fun cancelled() = t("Cancelado.", "Cancelled.")
    fun sent() = t("Enviado.", "Sent.")
    fun leftInMessages() = t("Te lo dejo escrito en mensajes.", "I've left it ready in your messages app.")

    // --- Rutas, llamadas, mensajes ---
    fun navigating(dest: String) = t("Navegando a $dest.", "Navigating to $dest.")
    fun gasStationQuery() = t("gasolinera", "gas station")
    fun contactNotFound(who: String) = t("No encuentro a $who en tus contactos.", "I can't find $who in your contacts.")
    fun calling(name: String) = t("Llamando a $name.", "Calling $name.")
    fun redialing() = t("Repitiendo la última llamada.", "Calling the last number again.")
    fun whatsappReady(name: String) = t(
        "Te dejé el mensaje para $name listo en WhatsApp. Toca enviar.",
        "Your message to $name is ready in WhatsApp. Tap send.",
    )
    fun noWhatsapp() = t("No pude abrir WhatsApp.", "I couldn't open WhatsApp.")
    fun whatToSay(name: String) = t("¿Qué le digo a $name?", "What should I tell $name?")
    fun confirmMessage(name: String, text: String) = t("¿Envío a $name: $text?", "Send to $name: $text?")
    fun noNewMessages() = t("No tienes mensajes nuevos.", "You have no new messages.")
    fun oneMessage(sender: String, app: String, text: String) =
        t("Mensaje de $sender por $app: $text.", "Message from $sender on $app: $text.")
    fun manyMessages(items: List<Pair<String, String>>) = t(
        "Tienes ${items.size} mensajes. " + items.joinToString(" ") { "De ${it.first}: ${it.second}." },
        "You have ${items.size} messages. " + items.joinToString(" ") { "From ${it.first}: ${it.second}." },
    )
    fun replyTo(sender: String) = t("¿Respondes a $sender?", "Reply to $sender?")
    fun sayYourReply() = t("Dime la respuesta.", "What should I reply?")
    fun confirmReply(text: String) = t("¿Envío: $text?", "Send: $text?")
    fun replyFailed() = t("No pude responder; la notificación ya no está.", "I couldn't reply; the notification is gone.")
    fun announceMessage(sender: String, text: String) = t("Mensaje de $sender: $text", "Message from $sender: $text")

    // --- Ubicación ---
    fun locationPermission() = t("Falta el permiso de ubicación. Actívalo en HelmVox.", "Location permission is missing. Turn it on in HelmVox.")
    fun noLocation() = t("No consigo tu ubicación ahora mismo.", "I can't get your location right now.")
    fun iAmHere(link: String) = t("Estoy aquí: $link", "I'm here: $link")
    fun confirmLocation(name: String, via: String) = t("¿Envío tu ubicación a $name por $via?", "Send your location to $name via $via?")
    fun locationSent(name: String) = t("Ubicación enviada a $name.", "Location sent to $name.")
    fun noAddress() = t("Tengo tu posición, pero sin internet no puedo decirte la dirección.", "I have your position, but without internet I can't tell you the address.")
    fun youAreIn(place: String) = t("Estás en $place.", "You're at $place.")

    // --- Tiempo ---
    fun weather(w: WeatherNow): String {
        val sky = Weather.describe(w.code, lang)
        val rain = w.rainChance?.let {
            t(" Probabilidad de lluvia en las próximas ${Weather.HOURS} horas: $it por ciento.",
                " Chance of rain in the next ${Weather.HOURS} hours: $it percent.")
        } ?: ""
        return t(
            "Ahora hay ${w.temperature} grados y está $sky. Viento de ${w.windKmh} kilómetros por hora.$rain",
            "It's ${w.temperature} degrees and $sky. Wind at ${w.windKmh} kilometers per hour.$rain",
        )
    }
    fun noWeather() = t("No pude consultar el tiempo; puede que no haya internet.", "I couldn't get the weather; there may be no internet.")

    // --- Música ---
    fun playing(query: String) = t("Poniendo $query.", "Playing $query.")
    fun searchOpened(query: String) = t(
        "Te abro los resultados de $query en YouTube Music. Toca una canción para empezar.",
        "Opening results for $query in YouTube Music. Tap a song to start.",
    )
    fun noMusicApp() = t("No encuentro ninguna app de música.", "I can't find a music app.")

    // --- Información ---
    fun time(hour: Int, minute: Int) =
        t("Son las $hour y ${"%02d".format(minute)}.", "It's ${(hour + 11) % 12 + 1}:${"%02d".format(minute)} ${if (hour < 12) "AM" else "PM"}.")
    fun phoneBattery(percent: Int) = t("El móvil está al $percent por ciento.", "Your phone is at $percent percent.")
    fun headsetBattery(percent: Int?) = when (percent) {
        null -> t("No pude leer la batería del casco.", "I couldn't read the helmet battery.")
        0 -> t("Al casco casi no le queda batería.", "The helmet battery is almost empty.")
        else -> t("El casco está más o menos al $percent por ciento.", "The helmet is at about $percent percent.")
    }
    fun phoneBatteryAlert(percent: Int) =
        t("Atención: al móvil le queda el $percent por ciento de batería.", "Heads up: your phone is down to $percent percent.")
    fun headsetBatteryAlert(percent: Int) = if (percent == 0) {
        t("Atención: al casco casi no le queda batería.", "Heads up: the helmet battery is almost empty.")
    } else {
        t("Atención: al casco le queda más o menos el $percent por ciento de batería.", "Heads up: the helmet is down to about $percent percent.")
    }
    fun help() = t(
        "Puedes decir: navega a, llama a, manda un mensaje a, lee mis mensajes, manda mi ubicación a, dónde estoy, cómo está el tiempo, " +
            "pon la música, pon música de, siguiente, sube el volumen, cambia a modo DMC o Bluetooth, silencia el grupo, " +
            "enciende la radio, comparte la música, abre el intercom, vuelve a llamar, qué hora es, batería del móvil o batería del casco.",
        "You can say: navigate to, call, text someone, read my messages, send my location to, where am I, what's the weather, " +
            "play music, play something by, next song, volume up, switch to DMC or Bluetooth, mute the group, " +
            "turn on the radio, share music, open the intercom, redial, what time is it, phone battery or helmet battery.",
    )

    // --- Casco ---
    fun alreadyInMode(dmc: Boolean) = t("Ya estás en modo ${if (dmc) "DMC" else "Bluetooth"}.", "You're already in ${if (dmc) "DMC" else "Bluetooth"} mode.")
    fun openingIntercom() = t("Abriendo el intercom.", "Opening the intercom.")
    fun closingIntercom() = t("Cerrando el intercom.", "Closing the intercom.")
    fun radioOn() = t("Encendiendo la radio.", "Turning the radio on.")
    fun radioOff() = t("Apagando la radio.", "Turning the radio off.")
    fun seekingStation() = t("Buscando emisora.", "Looking for a station.")
    fun station(n: Int) = t("Emisora $n.", "Station $n.")
    fun noHeadset() = t("No encuentro el casco Cardo emparejado.", "I can't find a paired Cardo helmet.")
    fun headsetFailed() = t(
        "No pude hablar con el casco. Cierra Cardo Connect y prueba otra vez.",
        "I couldn't talk to the helmet. Close Cardo Connect and try again.",
    )

    // --- Voz de muestra ---
    fun sample() = t("Hola, soy HelmVox. Navegando a la bencinera más cercana.", "Hi, I'm HelmVox. Navigating to the nearest gas station.")
}
