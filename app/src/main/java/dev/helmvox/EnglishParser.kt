package dev.helmvox

/**
 * Intérprete de órdenes en inglés. Mismo enfoque que el español: primero frases con datos
 * (destino, persona, texto del mensaje) y después palabras clave para todo lo demás.
 * Los datos extraídos se devuelven tal y como se dijeron (mayúsculas incluidas).
 */
internal object EnglishParser {

    private val WAKE = Regex("^((hey|ok|okay|hi|so) )*((helmet|helmvox|cardo|bike) )?(please )?")
    private val COURTESY = Regex("( (please|thanks|thank you|now))+$")
    private val YES = Regex("^(yes|yeah|yep|yup|sure|ok|okay|send it|send|do it|go ahead|correct|affirmative|please do|of course)\\b")

    private val MESSAGE = Regex(
        "^(send|text|message|tell|whatsapp)( (a|an) (message|text|whatsapp))?( (to|for))? (?<who>.+?)" +
            " (saying|that says|that|to say) (?<text>.+)$"
    )
    private val SHARE_LOCATION = Regex(
        "^(send|share|text|give)( (my|our))? (location|position|coordinates|pin)( (to|with))? (?<who>.+)$"
    )
    private val SHARE_LOCATION_TO_FIRST = Regex("^(send|text|give) (?<who>.+?) (my|our) (location|position|pin)$")
    private val CALL = Regex("^(call|phone|dial|ring)( up)? (?<who>.+)$")
    private val NAVIGATE = Regex(
        "^(navigate|take me|drive|go|get me|directions|route|show me the way|how do i get|lets go|let s go)" +
            "( (to|toward|towards))? (?<dest>.+)$"
    )
    private val PLAY_SEARCH = Regex(
        "^(play|put on|listen to)( some)?( (music|songs) by)? (?<q>.+?)" +
            "( on (spotify|youtube music|youtube|deezer|apple music|amazon music))?$"
    )
    private val GENERIC_MUSIC = Regex("^(music|some music|the music|something|another|a song|next|the next|this|it)\\b")
    private val STATION_NUMBER = Regex("\\b([1-6]|one|two|three|four|five|six)\\b")
    private val NUMBERS = mapOf("one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6)
    private val ARTICLES = Regex("^((the|my|a|an) )+")

    fun isYes(spoken: String) = YES.containsMatchIn(CommandParser.Normalized(spoken).text)

    fun parse(spoken: String): Command? {
        val full = CommandParser.Normalized(spoken)
        val start = WAKE.find(full.text)?.range?.last?.plus(1) ?: 0
        val withoutWake = full.drop(start)
        val n = withoutWake.take(COURTESY.find(withoutWake.text)?.range?.first ?: withoutWake.text.length)
        val t = n.text
        if (t.isEmpty()) return null
        fun original(m: MatchResult, group: String) = n.original(m.groups[group]!!.range).trim().trimEnd('.', '!', '?')
        val w = Words(t)

        // --- Control ---
        if (w.size <= 3 && w.has("cancel", "never mind", "nevermind", "forget it", "nothing")) return Command.Cancel
        if (w.hasWord("help") || t.contains("what can you do") || w.hasWord("commands")) return Command.Help

        // --- Frases con datos ---
        MESSAGE.matchEntire(t)?.let { m -> return Command.Message(original(m, "who").clean(), original(m, "text")) }
        (SHARE_LOCATION.matchEntire(t) ?: SHARE_LOCATION_TO_FIRST.matchEntire(t))?.let { m ->
            return Command.ShareLocation(original(m, "who").clean())
        }
        if (w.has("redial", "call back", "call again", "call the last", "last number", "call him back", "call her back", "call them back")) {
            return Command.Redial
        }
        if (w.has("where am i", "where are we", "my location", "what street", "what town", "what city")) return Command.WhereAmI
        if (w.has("message", "text", "whatsapp", "notification", "telegram", "sms") &&
            w.has("read", "check", "any", "do i have", "new", "unread", "got", "show") ||
            w.has("who messaged", "who texted", "who wrote")
        ) return Command.ReadMessages

        // --- Casco ---
        if (w.hasWord("dmc", "mesh") || t.contains("d m c")) return Command.IntercomMode(wantDmc = true)
        if (w.hasWord("bluetooth") && w.has("switch", "mode", "change", "go", "use", "intercom")) return Command.IntercomMode(wantDmc = false)
        if (w.hasWord("mode") && w.has("switch", "change", "toggle")) return Command.IntercomMode(wantDmc = null)
        if (w.hasWord("group") && w.has("mute", "unmute", "silence", "quiet")) return Command.DmcGroupMute
        if (w.has("mic", "microphone")) return Command.DmcMic
        if (w.has("share")) return if (w.has("radio", "fm", "station")) Command.ShareRadio else Command.ShareMusic
        if (w.has("intercom", "passenger", "pillion")) {
            if (w.has("close", "end", "hang up", "stop", "disconnect", "cut", "turn off")) return Command.IntercomEnd
            val channel = when {
                w.hasWord("b", "two", "2") -> 2
                w.hasWord("c", "three", "3") -> 3
                else -> 1
            }
            return Command.IntercomCall(channel)
        }

        // --- Información ---
        if (w.has("battery", "charge left", "juice")) {
            val phone = w.has("phone", "mobile", "cell")
            val headset = w.has("helmet", "headset", "intercom", "cardo", "packtalk")
            return Command.Battery(
                when {
                    phone && !headset -> BatteryOf.PHONE
                    headset && !phone -> BatteryOf.HEADSET
                    else -> BatteryOf.BOTH
                }
            )
        }
        if (w.hasWord("time") || t.contains("what time")) return Command.Time

        // --- Combustible y rutas ---
        if (w.has("gas station", "petrol", "fuel", "refuel") || (w.hasWord("gas") && !w.hasWord("pedal"))) {
            return Command.Navigate("gas station")
        }
        // --- Volumen ---
        val tooLoud = w.has("too loud", "too high", "so loud")
        if (tooLoud || w.has("quieter", "volume down", "turn it down", "turn down", "lower the volume", "lower volume", "softer")) {
            return Command.Volume(up = false)
        }
        if (w.has("louder", "volume up", "turn it up", "turn up", "raise the volume", "can t hear", "cant hear", "cannot hear")) {
            return Command.Volume(up = true)
        }

        // --- Música ---
        val musicWord = w.has("music", "song", "track", "tune", "spotify", "playlist", "album")
        val radioWord = w.hasWord("radio", "fm", "station", "stations", "preset")
        if (!radioWord) when {
            w.has("skip", "next song", "next track", "next one") || (musicWord && w.has("next", "another", "change", "different")) ->
                return Command.Media(MediaAction.NEXT)
            w.has("previous", "last song", "go back", "play that again", "back one") -> return Command.Media(MediaAction.PREVIOUS)
            w.has("pause") || (musicWord && w.has("stop", "turn off", "kill", "silence", "mute")) ->
                return Command.Media(MediaAction.PAUSE)
        }
        // --- Radio ---
        if (w.hasWord("radio", "fm", "station", "preset")) {
            if (w.hasWord("station", "preset")) STATION_NUMBER.find(t)?.let { m ->
                val num = m.groupValues[1]
                return Command.Radio(RadioAction.STATION, num.toIntOrNull() ?: NUMBERS.getValue(num))
            }
            return Command.Radio(
                when {
                    w.has("off", "stop", "close", "kill") -> RadioAction.OFF
                    w.has("next", "seek", "another", "find", "search", "up") -> RadioAction.NEXT
                    w.has("previous", "back", "last", "down") -> RadioAction.PREVIOUS
                    else -> RadioAction.ON
                }
            )
        }

        // --- Rutas y llamadas ---
        if (t == "home" || t == "go home" || t == "take me home" || t == "lets go home" || t == "head home") return Command.Navigate("home")
        NAVIGATE.matchEntire(t)?.let { m ->
            val dest = original(m, "dest").clean()
            return Command.Navigate(if (dest.equals("home", true) || dest.equals("my home", true)) "home" else dest)
        }
        CALL.matchEntire(t)?.let { m -> return Command.Call(original(m, "who").clean()) }

        PLAY_SEARCH.matchEntire(t)?.let { m ->
            if (!GENERIC_MUSIC.containsMatchIn(m.groups["q"]!!.value)) return Command.PlaySearch(original(m, "q"))
        }
        if (musicWord || w.has("resume", "play", "unpause")) return Command.Media(MediaAction.PLAY)
        return null
    }

    private fun String.clean() = replace(ARTICLES, "").replace(Regex("(?i)^(the|my|a|an) "), "").trim()

    /** Palabras de la frase; las raíces con espacio se buscan como frase completa. */
    private class Words(text: String) {
        private val words = text.split(' ').filter { it.isNotEmpty() }
        private val padded = " $text "
        val size get() = words.size

        fun has(vararg stems: String) = stems.any { s ->
            if (' ' in s) padded.contains(" $s ") else words.any { it.startsWith(s) }
        }

        fun hasWord(vararg exact: String) = exact.any { it in words }
    }
}
