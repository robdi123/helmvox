package dev.helmvox

import java.text.Normalizer

enum class MediaAction { PLAY, PAUSE, NEXT, PREVIOUS }

enum class RadioAction { ON, OFF, NEXT, PREVIOUS, STATION }

enum class BatteryOf { PHONE, HEADSET, BOTH }

sealed interface Command {
    data class Navigate(val destination: String) : Command
    data class Call(val who: String) : Command
    /** Mensaje a [who]; sin [text], la app pregunta qué decir. */
    data class Message(val who: String, val text: String? = null) : Command
    data class Media(val action: MediaAction) : Command
    /** "Pon música de Queen": busca y reproduce en la app de música. */
    data class PlaySearch(val query: String) : Command
    data class Volume(val up: Boolean) : Command
    /** Intercom del casco: true = DMC, false = Bluetooth, null = cambiar al otro. */
    data class IntercomMode(val wantDmc: Boolean?) : Command
    /** Silencia o reactiva el audio del grupo DMC (alterna). */
    data object DmcGroupMute : Command
    /** Abre o cierra tu micrófono hacia el grupo DMC (alterna). */
    data object DmcMic : Command
    data class Radio(val action: RadioAction, val station: Int = 0) : Command
    /** Empieza / deja de compartir la música con quien esté en el intercom (alterna). */
    data object ShareMusic : Command
    /** Empieza / deja de compartir la radio FM (alterna). */
    data object ShareRadio : Command
    /** Abre el intercom Bluetooth con el canal A (1), B (2) o C (3). */
    data class IntercomCall(val channel: Int) : Command
    data object IntercomEnd : Command
    /** Repite la última llamada del teléfono, desde el casco. */
    data object Redial : Command
    /** Envía un enlace de Google Maps con tu posición a [who]. */
    data class ShareLocation(val who: String) : Command
    data object WhereAmI : Command
    /** Tiempo actual y probabilidad de lluvia en las próximas horas, donde estás. */
    data object Weather : Command
    data object ReadMessages : Command
    data object Time : Command
    data class Battery(val of: BatteryOf) : Command
    data object Help : Command
    data object Cancel : Command
}

/**
 * Convierte una frase reconocida en un [Command].
 *
 * Las expresiones se evalúan sobre el texto normalizado (minúsculas, sin tildes ni
 * puntuación), pero los datos que se extraen (destino, nombre, texto del mensaje)
 * se devuelven tal y como se dijeron, con sus tildes.
 */
object CommandParser {

    private val WAKE = Regex(
        "^((oye|hey|ok|eh|venga) )*((casco|cardo|moto) )?(por favor )?" +
            // Cortesías al principio: "¿puedes poner…?", "quiero que pongas…"
            "((me )?(puedes|podrias|puedas|podria) |(quiero|necesito) que )?"
    )
    private val CANCEL = Regex("^(cancela|cancelar|nada|olvidalo|dejalo|no importa)\\b")
    private val HELP = Regex("^(ayuda|que puedes hacer|que sabes hacer|comandos)\\b")
    private val MESSAGE = Regex(
        "^(manda|mandale|envia|enviale|escribe|escribele|dile|avisale|contestale|responde|respondele)" +
            "( (un|el) (mensaje|sms|whatsapp|wasap))?( (a|al|para))?( (la|el))? (?<who>.+?)" +
            " (diciendo|que diga|que ponga|que dice|que) (?<text>.+)$"
    )
    /** "Manda un mensaje / WhatsApp a Nano" sin texto (a veces el reconocedor se come el verbo). */
    private val MESSAGE_TO = Regex(
        "^((manda|mandale|mandar|envia|enviale|enviar|escribe|escribele|escribir|escribirle) )?((un|el|otro) )?" +
            "(mensaje|mensajito|sms|whatsapp|wasap)( (de|por) (whatsapp|wasap|sms|telegram))?( (a|al|para))?( (la|el))? (?<who>.+)$"
    )
    private val WRITE_TO = Regex("^(escribele|escribirle|escribe|mandale|enviale) (a|al)( (la|el))? (?<who>.+)$")
    private val INTERCOM_MODE = Regex(
        "^(cambia|cambiar|pasa|pasar|pon|activa|modo)( (a|al|el|de))?( (modo|intercom))?" +
            "( (a|al|de))?( (intercom|modo))?( (?<target>dmc|d m c|de m c|mesh|malla|bluetooth|blutu|bluetut|blue tooth))?$"
    )
    private val MODE_WORD = Regex("\\bmodo\\b")
    private val MY_PREFIX = Regex("(?i)^mi ")
    private val GENERIC_MUSIC = Regex("^(otra|otro|esta|este|la siguiente|siguiente|la anterior|anterior|algo|una cancion|la musica|musica)\\b")
    private val COURTESY = Regex("( (por favor|porfa|porfavor|gracias))+$")
    private val DMC_MIC = Regex(
        "^(silencia|mutea|activa|desactiva|corta|abre|cierra|apaga|enciende|quita)( (el|mi))? (micro|microfono)" +
            "( (del|en el|al|para el) (grupo|intercom))?$"
    )
    private val DMC_MUTE = Regex(
        "^((silencia|silenciar|mutea|mutear|calla|desmutea|reactiva|quita el silencio|quitale el silencio)" +
            "( (a|al|el|del))? (grupo|intercom|dmc|d m c)|(silencio|mute)( (en|al|del))?( el)? (grupo|intercom))$"
    )
    private val RADIO_ON = Regex("^((enciende|pon|activa|abre|pon me|ponme)( la)? (radio|fm)|radio)$")
    private val RADIO_OFF = Regex("^(apaga|quita|desactiva|cierra|para)( la)? (radio|fm)$")
    private val RADIO_NEXT = Regex(
        "^((siguiente|proxima|cambia de|busca|busca otra|sube de|sube) (emisora|estacion)|(emisora|estacion) siguiente)$"
    )
    private val RADIO_PREV = Regex("^((anterior|baja de|baja) (emisora|estacion)|(emisora|estacion) anterior)$")
    private val RADIO_STATION = Regex(
        "^((pon|cambia a|ve a|vete a)( la)? )?(emisora|estacion|presintonia|preset)( numero)? " +
            "(?<n>[1-6]|uno|una|dos|tres|cuatro|cinco|seis)$"
    )
    private val SHARE = Regex(
        "^(comparte|compartir|compartirla|deja de compartir|dejar de compartir|para de compartir|no compartas)" +
            "( (la|mi))? (?<what>musica|cancion|radio|fm)" +
            "( con (el |la |los )?(pasajero|pasajera|copiloto|grupo|intercom|todos|companero|companera))?$"
    )
    private val SHARED = Regex("^(?<what>musica|radio) compartida$")
    private val REDIAL = Regex(
        "^(vuelve a llamar|rellama|rellamar|repite (la )?(ultima )?llamada|llama (otra vez|de nuevo)" +
            "|llama al ultimo( numero| que (me )?(llamo|llamaste|marque))?|(la )?ultima llamada)$"
    )
    private val IC_CALL = Regex(
        "^(abre|activa|conecta|inicia|empieza|llama al|llama por el|habla con el|habla por el|habla por)" +
            "( el)? (intercom|intercomunicador|canal)( (canal )?(?<ch>a|b|c|uno|dos|tres|1|2|3))?$"
    )
    private val IC_CALL_RIDER = Regex(
        "^(llama|habla con|conecta con|conectame con)( (al|el|a la|a|la))? (pasajero|pasajera|copiloto|companero|companera)$"
    )
    private val IC_END = Regex(
        "^(cierra|corta|cuelga|termina|apaga|desconecta)( el)? (intercom|intercomunicador)$"
    )
    private val SHARE_LOCATION = Regex(
        "^(manda|mandale|envia|enviale|comparte|compartir|pasale|pasa)( (mi|la))? " +
            "(ubicacion|localizacion|posicion|coordenadas)( actual)? (a|al|con|para)( (el|la))? (?<who>.+)$"
    )
    private val SHARE_LOCATION_TO_FIRST = Regex(
        "^(manda|mandale|envia|enviale|pasale)( (a|al))? (?<who>.+?) (mi |la )?(ubicacion|localizacion|posicion)$"
    )
    private val WHERE_AM_I = Regex(
        "^(donde estoy|donde me encuentro|en que (calle|lugar|comuna|ciudad|parte|zona) estoy|cual es mi ubicacion)$"
    )
    private val READ_MESSAGES = Regex(
        "^((lee|leeme|lea|leer)( (los|mis))? (mensajes|whatsapp|whatsapps|wasap|wasaps)( (nuevos|pendientes))?" +
            "|(tengo|hay) (mensajes|whatsapp|whatsapps)( nuevos)?)$"
    )
    private val CHANNELS = mapOf("a" to 1, "uno" to 1, "1" to 1, "b" to 2, "dos" to 2, "2" to 2, "c" to 3, "tres" to 3, "3" to 3)
    private val NUMBERS = mapOf("uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6)
    private val PLAY_SEARCH = Regex(
        "^(pon|ponme|pon me|poner|ponerme|pongas|reproduce|reproducir|reproduzcas|quiero escuchar|escuchar|toca|tocar)" +
            "( (musica de|la musica de|canciones de|una cancion de|la cancion de|un tema de|temas de|algo de|un poco de" +
            "|la cancion|el disco|la lista|la playlist))?" +
            " (?<q>.+?)( en (spotify|youtube music|youtube|deezer|apple music|amazon music))?$"
    )
    private val CALL = Regex("^(llama|llamar|llamame|marca|marcar|telefonea)( (a|al))? (.+)$")
    private val NAVIGATE = Regex(
        "^(navega|navegar|llevame|vamos|ir|quiero ir|ruta|como llego|como se va)( (a|al|hacia|hasta|para))? (.+)$"
    )
    private val GO_HOME = Regex("^(a )?(casa|trabajo|el trabajo)$")
    private val FUEL = Regex(
        "\\b(gasolinera|gasolina|combustible|repostar|bencinera|bencina|nafta|estacion de servicio|servicentro)\\b"
    )
    private val MEDIA = listOf(
        Regex(
            "^(pausa|pausar|para|parar|deten|detener|stop|quita|apaga|apagar|corta|silencia|calla)" +
                "( (la|el))?( (musica|cancion|audio))?$"
        ) to MediaAction.PAUSE,
        Regex(
            "^((play|reproduce|reproducir|pon|ponme|pon me|poner|ponerme|pongas|continua|reanuda|dale|dale a|sigue con|vuelve a poner)" +
                "( (la|el|algo de|un poco de|mi|mis))?( (musica|cancion|canciones|audio|playlist|lista))?" +
                "( (otra vez|de nuevo))?|musica|quiero (escuchar )?musica)$"
        ) to MediaAction.PLAY,
        Regex("^(siguiente|proxima|salta|pasa|cambia)( (la|de))?( (cancion|tema))?( siguiente)?$") to MediaAction.NEXT,
        Regex("^(la |cancion |tema )?anterior( (cancion|tema))?$") to MediaAction.PREVIOUS,
        Regex("^(vuelve|atras)( (a la|la))?( (cancion|tema))?( anterior)?$") to MediaAction.PREVIOUS,
    )
    private val VOLUME_UP = Regex("^((sube|subir|subele)( el)? (volumen|musica)|mas (alto|volumen))$")
    private val VOLUME_DOWN = Regex("^((baja|bajar|bajale)( el)? (volumen|musica)|mas bajo|menos volumen)$")
    private val TIME = Regex("\\bhora\\b")
    private val BATTERY = Regex("\\bbateria\\b")
    private val PHONE_WORD = Regex("\\b(telefono|movil|celular|smartphone)\\b")
    private val HEADSET_WORD = Regex("\\b(casco|intercom|intercomunicador|cardo|packtalk|pack talk|auricular|auriculares)\\b")
    private val YES = Regex("^(si|vale|claro|envia|envialo|mandalo|correcto|dale|ok|venga|afirmativo)\\b")
    private val TRAILING_PLEASE = Regex("(?i)[\\s,]*por favor[.!]*$")

    /** Orden para [spoken] en el idioma de la conversación (español por defecto). */
    fun parse(spoken: String, lang: Lang = Lang.ES): Command? =
        if (lang == Lang.EN) EnglishParser.parse(spoken) else parseSpanish(spoken)

    fun isYes(spoken: String, lang: Lang = Lang.ES): Boolean =
        if (lang == Lang.EN) EnglishParser.isYes(spoken) else YES.containsMatchIn(Normalized(spoken).text)

    private fun parseSpanish(spoken: String): Command? {
        val full = Normalized(spoken)
        val start = WAKE.find(full.text)?.range?.last?.plus(1) ?: 0
        val withoutWake = full.drop(start)
        val n = withoutWake.take(COURTESY.find(withoutWake.text)?.range?.first ?: withoutWake.text.length)
        val t = n.text
        if (t.isEmpty()) return null

        if (CANCEL.containsMatchIn(t)) return Command.Cancel
        if (HELP.containsMatchIn(t)) return Command.Help

        INTERCOM_MODE.find(t)?.let { m ->
            val target = m.groups["target"]?.value
            if (target != null) return Command.IntercomMode(wantDmc = !target.startsWith("b"))
            if (MODE_WORD.containsMatchIn(t)) return Command.IntercomMode(wantDmc = null)
        }
        if (DMC_MIC.matches(t)) return Command.DmcMic
        if (DMC_MUTE.matches(t)) return Command.DmcGroupMute
        if (RADIO_ON.matches(t)) return Command.Radio(RadioAction.ON)
        if (RADIO_OFF.matches(t)) return Command.Radio(RadioAction.OFF)
        if (RADIO_NEXT.matches(t)) return Command.Radio(RadioAction.NEXT)
        if (RADIO_PREV.matches(t)) return Command.Radio(RadioAction.PREVIOUS)
        RADIO_STATION.matchEntire(t)?.let { m ->
            val raw = m.groups["n"]!!.value
            return Command.Radio(RadioAction.STATION, raw.toIntOrNull() ?: NUMBERS.getValue(raw))
        }
        (SHARE.matchEntire(t) ?: SHARED.matchEntire(t))?.let { m ->
            val what = m.groups["what"]!!.value
            return if (what == "radio" || what == "fm") Command.ShareRadio else Command.ShareMusic
        }
        if (REDIAL.matches(t)) return Command.Redial
        (SHARE_LOCATION.matchEntire(t) ?: SHARE_LOCATION_TO_FIRST.matchEntire(t))?.let { m ->
            return Command.ShareLocation(clean(n.original(m.groups["who"]!!.range)))
        }
        if (WHERE_AM_I.matches(t)) return Command.WhereAmI
        if (READ_MESSAGES.matches(t)) return Command.ReadMessages
        IC_CALL.matchEntire(t)?.let { m ->
            return Command.IntercomCall(m.groups["ch"]?.value?.let(CHANNELS::getValue) ?: 1)
        }
        if (IC_CALL_RIDER.matches(t)) return Command.IntercomCall(1)
        if (IC_END.matches(t)) return Command.IntercomEnd
        MESSAGE.find(t)?.let { m ->
            return Command.Message(n.original(m.groups["who"]!!.range), n.original(m.groups["text"]!!.range))
        }
        (MESSAGE_TO.matchEntire(t) ?: WRITE_TO.matchEntire(t))?.let { m ->
            return Command.Message(clean(n.original(m.groups["who"]!!.range)))
        }
        CALL.find(t)?.let { m -> return Command.Call(clean(n.original(m.groups[4]!!.range))) }
        NAVIGATE.find(t)?.let { m ->
            val dest = n.original(m.groups[4]!!.range)
            return Command.Navigate(
                if (FUEL.containsMatchIn(m.groups[4]!!.value)) "gasolinera" else clean(dest).replace(MY_PREFIX, "")
            )
        }
        GO_HOME.find(t)?.let { m -> return Command.Navigate(n.original(m.groups[2]!!.range)) }

        for ((regex, action) in MEDIA) if (regex.matches(t)) return Command.Media(action)
        if (VOLUME_UP.matches(t)) return Command.Volume(up = true)
        if (VOLUME_DOWN.matches(t)) return Command.Volume(up = false)
        if (TIME.containsMatchIn(t)) return Command.Time
        if (BATTERY.containsMatchIn(t)) {
            val phone = PHONE_WORD.containsMatchIn(t)
            val headset = HEADSET_WORD.containsMatchIn(t)
            return Command.Battery(
                when {
                    phone && !headset -> BatteryOf.PHONE
                    headset && !phone -> BatteryOf.HEADSET
                    else -> BatteryOf.BOTH
                }
            )
        }
        if (FUEL.containsMatchIn(t)) return Command.Navigate("gasolinera")
        PLAY_SEARCH.matchEntire(t)?.let { m ->
            // "pon otra canción" no es buscar una canción llamada "otra canción".
            if (!GENERIC_MUSIC.containsMatchIn(m.groups["q"]!!.value)) {
                return Command.PlaySearch(n.original(m.groups["q"]!!.range))
            }
        }
        // Ninguna frase exacta: buscar la intención por palabras clave.
        return FuzzyIntent.match(t)
    }


    private fun clean(s: String) = s.replace(TRAILING_PLEASE, "").trim().trimEnd('.', '!', '?')

    /** Texto normalizado que recuerda de qué posición del original salió cada carácter. */
    internal class Normalized private constructor(
        private val orig: String,
        val text: String,
        private val map: IntArray,
    ) {
        constructor(orig: String) : this(orig, build(orig))
        private constructor(orig: String, built: Pair<String, IntArray>) : this(orig, built.first, built.second)

        fun original(range: IntRange): String = orig.substring(map[range.first], map[range.last] + 1)

        fun drop(n: Int) = Normalized(orig, text.substring(n), map.copyOfRange(n, map.size))

        fun take(n: Int): Normalized {
            val kept = text.substring(0, n).trimEnd()
            return Normalized(orig, kept, map.copyOfRange(0, kept.length))
        }

        companion object {
            private fun fold(c: Char): Char {
                val base = Normalizer.normalize(c.toString(), Normalizer.Form.NFD)[0].lowercaseChar()
                return if (base in 'a'..'z' || base in '0'..'9') base else ' '
            }

            private fun build(orig: String): Pair<String, IntArray> {
                val sb = StringBuilder()
                val map = ArrayList<Int>()
                orig.forEachIndexed { i, c ->
                    val f = fold(c)
                    if (f == ' ' && (sb.isEmpty() || sb.last() == ' ')) return@forEachIndexed
                    sb.append(f)
                    map.add(i)
                }
                while (sb.isNotEmpty() && sb.last() == ' ') {
                    sb.setLength(sb.length - 1)
                    map.removeAt(map.size - 1)
                }
                return sb.toString() to map.toIntArray()
            }
        }
    }
}
