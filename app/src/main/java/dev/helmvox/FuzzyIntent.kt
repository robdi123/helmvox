package dev.helmvox

/**
 * Plan B del [CommandParser]: cuando ninguna frase exacta encaja, busca la *intención* por
 * palabras clave ("tengo notificaciones de WhatsApp" → leer mensajes, "está muy fuerte" →
 * bajar volumen). Trabaja sobre el texto normalizado (minúsculas, sin tildes) y compara por
 * raíces de palabra, así que "mensaje", "mensajes" y "mensajito" cuentan igual.
 *
 * El orden de las reglas importa: las más concretas van antes que las más generales.
 */
internal object FuzzyIntent {

    /** Los datos extraídos (destino, persona) salen sin tildes; contactos y Maps las ignoran. */
    fun match(t: String): Command? {
        val w = Words(t)
        if (w.isEmpty()) return null

        // --- Control de la conversación ---
        if (w.size <= 3 && w.hasWord("cancela", "cancelar", "olvidalo", "dejalo", "nada", "nadie")) return Command.Cancel
        if (w.has("ayuda", "ayudame", "comandos") || t.contains("que puedes") || t.contains("que sabes")) return Command.Help

        // --- Ubicación ---
        val locationWord = w.has("ubicac", "localizac", "coordenad")
        if (locationWord && w.has("mand", "envi", "compart", "pasal", "pasa ")) {
            after(t, PREPOSITION_TO)?.let { who -> return Command.ShareLocation(who.cleanTarget()) }
        }
        if ((w.hasWord("donde", "adonde") && w.has("estoy", "ando", "estamos", "andamos", "encuentro", "voy")) ||
            (locationWord && !w.has("mand", "envi", "compart"))
        ) return Command.WhereAmI

        // --- Mensajes (leer) ---
        val sending = w.has("mand", "envi", "escribele", "dile", "contesta", "respond", "avisale")
        if (!sending && (w.has("mensaj", "whats", "wasap", "guasap", "notific", "sms", "telegram", "chat") ||
                (w.has("escrib") && w.has("quien", "alguien", "lee", "que me")))
        ) return Command.ReadMessages

        // --- Baterías y hora ---
        if (FUEL.containsMatchIn(t)) return Command.Navigate("gasolinera")
        if (w.has("bater", "pila")) {
            val phone = w.has("telefon", "movil", "celular", "cel ")
            val headset = w.has("casco", "intercom", "cardo", "packtalk", "auricular")
            return Command.Battery(
                when {
                    phone && !headset -> BatteryOf.PHONE
                    headset && !phone -> BatteryOf.HEADSET
                    else -> BatteryOf.BOTH
                }
            )
        }
        if (w.has("clima", "lluv", "llov", "pronostic", "temperatura", "llovizna") ||
            (w.hasWord("tiempo") && !w.has("cuanto tiempo", "a tiempo", "tiempo falta", "tiempo queda")) ||
            (w.hasWord("frio", "calor") && w.has("hace", "esta", "va a"))
        ) return Command.Weather
        if (w.hasWord("hora", "horas")) return Command.Time

        // --- Llamadas ---
        if (w.has("rellam") || (w.has("llam", "marc") && w.has("otra vez", "de nuevo", "ultim"))) return Command.Redial

        // --- Casco: modo, grupo, micrófono, compartir, intercom ---
        if (w.has("dmc", "d m c", "mesh", "malla")) return Command.IntercomMode(wantDmc = true)
        if (w.has("bluetooth", "blutu", "bluetut") && !w.has("music")) return Command.IntercomMode(wantDmc = false)
        if (w.hasWord("modo") && w.has("cambi", "pasa", "pon")) return Command.IntercomMode(wantDmc = null)
        if (w.has("grupo") && w.has("silenc", "mute", "mutea", "calla", "desmute", "reactiv", "sonido")) return Command.DmcGroupMute
        if (w.has("microfon", "micro ") || w.hasWord("micro")) return Command.DmcMic
        if (w.has("compart")) return if (w.has("radio", "fm", "emisora")) Command.ShareRadio else Command.ShareMusic
        if (w.has("intercom", "pasajer", "copilot", "acompanant")) {
            if (w.has("cort", "cierr", "cuelg", "termin", "apag", "desconect", "sal")) return Command.IntercomEnd
            val channel = when {
                w.hasWord("b", "dos", "2") -> 2
                w.hasWord("c", "tres", "3") -> 3
                else -> 1
            }
            return Command.IntercomCall(channel)
        }

        // --- Radio ---
        if (w.has("radio", "emisora", "estacion") || w.hasWord("fm")) {
            STATION_NUMBER.find(t)?.let { m ->
                val n = m.groupValues[1]
                return Command.Radio(RadioAction.STATION, n.toIntOrNull() ?: NUMBERS.getValue(n))
            }
            return Command.Radio(
                when {
                    w.has("apag", "quit", "cierr", "deten", "stop") || w.hasWord("para") -> RadioAction.OFF
                    w.has("siguient", "proxim", "otra", "busc", "cambi", "sube") -> RadioAction.NEXT
                    w.has("anterior", "atras", "vuelve", "baja") -> RadioAction.PREVIOUS
                    else -> RadioAction.ON
                }
            )
        }

        // --- Volumen ---
        val tooLoud = w.has("fuerte", "alto") && w.has("muy", "demasiado", "mucho")
        if (w.has("volumen", "bajal", "subel") || w.has("fuerte", "despacio", "bajito", "suave") ||
            w.hasWord("alto", "bajo")
        ) {
            return when {
                tooLoud || w.has("baj", "menos", "disminu", "despacio", "bajito", "suave") -> Command.Volume(up = false)
                else -> Command.Volume(up = true)
            }
        }
        if (w.has("no escucho", "no se escucha", "no oigo", "no se oye")) return Command.Volume(up = true)

        // --- Música ---
        val musicWord = w.has("music", "cancion", "tema", "spotify", "playlist", "disco", "lista") ||
            (w.has("escuch") && w.has("algo", "pon", "quiero"))
        when {
            w.has("siguient", "proxim", "salta", "saltal", "salt") -> return Command.Media(MediaAction.NEXT)
            w.has("anterior") || (musicWord && w.has("atras", "vuelve")) -> return Command.Media(MediaAction.PREVIOUS)
            musicWord && w.has("otra", "cambi", "pasa") -> return Command.Media(MediaAction.NEXT)
            // "para" solo cuenta como "parar" si no es "algo para escuchar".
            musicWord && (w.has("pausa", "apag", "deten", "quit", "stop", "silenc", "cort", "calla") ||
                (w.hasWord("para") && !w.has("escuch"))) -> return Command.Media(MediaAction.PAUSE)
            musicWord -> return Command.Media(MediaAction.PLAY)
        }

        // --- Ruta ---
        if (w.has("llevame", "llevarme", "navega", "ruta", "como llego", "como se llega", "quiero ir", "vamos",
                "indicacion", "camino", "direccion", "anda a", "ir a")
        ) {
            after(t, PREPOSITION_TO)?.let { dest ->
                val d = dest.cleanTarget()
                return Command.Navigate(if (d == "casa" || d == "mi casa") "casa" else d)
            }
        }

        // --- Llamar ---
        if (w.has("llam", "marca", "telefonea")) {
            after(t, PREPOSITION_TO)?.let { who -> return Command.Call(who.cleanTarget()) }
        }
        return null
    }

    /** Lo que va tras la primera "a / al / hacia / hasta / para / con" (destino o persona). */
    private fun after(t: String, preposition: Regex): String? {
        val m = preposition.find(t) ?: return null
        return t.substring(m.range.last + 1).trim().ifEmpty { null }
    }

    /** Quita artículos y posesivos del principio y "mi ubicación" etc. del final. */
    private fun String.cleanTarget(): String = this
        .replace(Regex("^((la|el|los|las|mi|mis|un|una|donde|lo de) )+"), "")
        .replace(Regex(" (mi|la|tu) (ubicacion|localizacion|posicion)$"), "")
        .replace(Regex(" (por favor|porfa|gracias)$"), "")
        .trim()

    private val PREPOSITION_TO = Regex("\\b(a|al|hacia|hasta|para|con)\\b")
    private val FUEL = Regex("\\b(gasolinera|gasolina|combustible|repostar|bencinera|bencina|nafta|servicentro)\\b")
    private val STATION_NUMBER = Regex("\\b([1-6]|uno|una|dos|tres|cuatro|cinco|seis)\\b")
    private val NUMBERS = mapOf("uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6)

    /** Palabras de la frase, para preguntar por raíces ("mensaj") o palabras exactas ("para"). */
    private class Words(private val text: String) {
        private val words = text.split(' ').filter { it.isNotEmpty() }
        private val padded = " $text "
        val size get() = words.size
        fun isEmpty() = words.isEmpty()

        /** Alguna palabra empieza por alguna raíz; las raíces con espacio se buscan como frase. */
        fun has(vararg stems: String) = stems.any { s ->
            if (' ' in s) padded.contains(" $s") else words.any { it.startsWith(s) }
        }

        fun hasWord(vararg exact: String) = exact.any { it in words }
    }
}
