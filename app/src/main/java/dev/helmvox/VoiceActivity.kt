package dev.helmvox

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.time.LocalTime

/**
 * Una "sesión" de voz: abre el audio del casco, pita, escucha una orden,
 * responde por el casco y ejecuta la acción. Luego se cierra sola.
 */
class VoiceActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private lateinit var sheet: VoiceSheet
    private lateinit var am: AudioManager
    private lateinit var route: BtAudioRoute
    private lateinit var speaker: Speaker
    private var recognizer: SpeechRecognizer? = null
    private val warmup = SilentStream()
    private var focus: AudioFocusRequest? = null
    private var onCallChannel = false
    private var retries = 0
    private var recognizerRestarts = 0
    private var audioReleased = false

    /** Cada escucha tiene un número; los avisos de escuchas anteriores se ignoran. */
    private var listenId = 0

    /** Acento elegido; si el reconocedor no lo tiene, se cambia al general de su idioma. */
    private val listenLocale by lazy { VoicePrefs.load(this).listenLocale }
    private val lang by lazy { VoicePrefs.load(this).lang }
    private val ph by lazy { Phrases(lang) }
    private val fallbackLocale get() = if (lang == Lang.EN) "en-US" else "es-US"
    private var localeOverride: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isActive = true
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        sheet = VoiceSheet(this)
        setContentView(sheet)

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            show(ph.noRecognizer())
            return
        }

        am = getSystemService(AudioManager::class.java)
        speaker = Speaker(this)
        route = BtAudioRoute(this)
        requestFocus()
        route.start(VoicePrefs.load(this).volume) { sco ->
            onCallChannel = sco
            speaker.useCallChannel = sco
            val announce = intent?.action == ACTION_ANNOUNCE_MESSAGE
            val message = if (announce) MessageStore.get(intent.getLongExtra(EXTRA_MESSAGE_ID, -1)) else null
            when {
                message != null -> announceMessages(listOf(message))
                announce -> done() // el mensaje ya no está (reinicio de la app): nada que leer
                else -> {
                    show(if (sco) ph.sayCommand() else ph.noHeadsetMic())
                    listen(::onCommand)
                }
            }
        }
    }

    override fun onDestroy() {
        isActive = false
        warmup.stop()
        releaseAudio()
        if (::speaker.isInitialized) speaker.shutdown()
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // --- Escuchar ---

    /** true mientras se dicta un mensaje: el reconocedor espera más antes de dar por terminada la frase. */
    private var dictation = false

    private fun listen(onText: (List<String>) -> Unit) = listen(dictating = false, onText)

    private fun listen(dictating: Boolean, onText: (List<String>) -> Unit) {
        dictation = dictating
        sheet.setState(VoiceSheet.State.LISTENING)
        // Sin pitido propio: el reconocedor de Google ya pita al empezar a escuchar, y un segundo
        // pitido por el casco hacía que su cancelación de eco borrara lo primero que decías.
        startRecognizer(onText)
    }

    private fun startRecognizer(onText: (List<String>) -> Unit) {
        val id = ++listenId
        val r = recognizer ?: createRecognizer().also { recognizer = it }
        r.cancel()
        r.setRecognitionListener(object : RecognitionListener {
            private fun current() = id == listenId

            override fun onResults(results: Bundle) {
                if (!current()) return
                listenId++
                val heard = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                Log.i(TAG, "oído: $heard")
                if (heard.isEmpty()) onRecognitionError(SpeechRecognizer.ERROR_NO_MATCH, onText) else onText(heard)
            }

            override fun onError(error: Int) {
                if (!current()) return
                listenId++
                onRecognitionError(error, onText)
            }

            private val started = System.currentTimeMillis()
            private fun ms() = System.currentTimeMillis() - started

            override fun onReadyForSpeech(params: Bundle?) {
                Log.i(TAG, "reconocedor listo (+${ms()} ms)")
            }
            override fun onBeginningOfSpeech() { Log.i(TAG, "empiezas a hablar (+${ms()} ms)") }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { Log.i(TAG, "fin de la frase (+${ms()} ms)") }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        r.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeOverride ?: listenLocale)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
                .apply {
                    // Al dictar uno se para a pensar: no cortar en la primera pausa.
                    if (dictation) {
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000L)
                    }
                }
        )
    }

    /** Usa el reconocedor de la app de Google si está (mejor con frases libres); si no, el del sistema. */
    private fun createRecognizer(): SpeechRecognizer {
        val services = packageManager.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
        val google = services.firstOrNull {
            it.serviceInfo.packageName == GOOGLE_APP && it.serviceInfo.name == GOOGLE_RECOGNIZER
        }
        Log.i(TAG, "reconocedor: ${google?.serviceInfo?.name ?: "el del sistema"}")
        return if (google != null) {
            SpeechRecognizer.createSpeechRecognizer(this, ComponentName(GOOGLE_APP, GOOGLE_RECOGNIZER))
        } else {
            SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    private fun onRecognitionError(error: Int, onText: (List<String>) -> Unit) {
        Log.w(TAG, "error del reconocedor: $error")
        when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                if (retries < MAX_RETRIES) {
                    retries++
                    ask(ph.didntHear()) { listen(onText) }
                } else {
                    say(ph.givingUp()) { done() }
                }

            // Fallos del propio servicio (ocupado, desconectado…): se recrea y se reintenta sin decir nada.
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED, SpeechRecognizer.ERROR_SERVER ->
                if (recognizerRestarts < MAX_RECOGNIZER_RESTARTS) {
                    recognizerRestarts++
                    recognizer?.destroy()
                    recognizer = null
                    main.postDelayed({ startRecognizer(onText) }, 600)
                } else {
                    say(ph.recognizerDown()) { done() }
                }

            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                if (localeOverride == null && listenLocale != fallbackLocale) {
                    Log.w(TAG, "el reconocedor no tiene $listenLocale; uso $fallbackLocale")
                    localeOverride = fallbackLocale
                    main.postDelayed({ startRecognizer(onText) }, 300)
                } else {
                    say(ph.languageMissing()) { done() }
                }

            else -> {
                say(ph.recognitionError(error)) { done() }
            }
        }
    }

    // --- Entender y ejecutar ---

    private fun onCommand(heard: List<String>) {
        show("«${heard.first()}»")
        sheet.setState(VoiceSheet.State.THINKING)
        val cmd = heard.firstNotNullOfOrNull { CommandParser.parse(it, lang) }
        Log.i(TAG, "comando: $cmd")
        when {
            cmd != null -> execute(cmd)
            // Ni frase exacta ni palabras clave: con internet y clave, que lo interprete la IA.
            AiParser.isReady(this) -> Thread {
                val result = AiParser.parse(this, heard.first())
                Log.i(TAG, "IA: $result")
                main.post {
                    if (result is AiParser.Result.Understood) execute(result.command) else notUnderstood()
                }
            }.start()
            else -> notUnderstood()
        }
    }

    private fun notUnderstood() {
        if (retries < MAX_RETRIES) {
            retries++
            ask(ph.notUnderstood()) {
                listen(::onCommand)
            }
        } else {
            say(ph.notUnderstoodAgain()) { done() }
        }
    }

    private fun execute(cmd: Command) {
        when (cmd) {
            is Command.Navigate -> say(ph.navigating(cmd.destination)) {
                releaseAudio()
                Actions.navigate(this, cmd.destination)
                done()
            }

            is Command.Call -> {
                val contact = Actions.findContact(this, cmd.who)
                    ?: return say(ph.contactNotFound(cmd.who)) { done() }
                say(ph.calling(contact.name)) {
                    releaseAudio()
                    Actions.call(this, contact.number)
                    done()
                }
            }

            is Command.Message -> sendMessage(cmd.who, cmd.text)

            Command.ReadMessages -> {
                CascoListenerService.instance?.ingestActive() // por si hay mensajes de antes de arrancar
                val unread = MessageStore.unread()
                if (unread.isEmpty()) say(ph.noNewMessages()) { done() } else announceMessages(unread)
            }

            is Command.ShareLocation -> shareLocation(cmd.who)
            Command.WhereAmI -> whereAmI()
            Command.Weather -> weather()

            is Command.IntercomMode -> say(ph.ok()) {
                headsetCommand(STANDBY_WAIT_MS, { CardoLink.setIntercomMode(this, cmd.wantDmc) }) { r ->
                    when (r) {
                        is CardoLink.Result.AlreadyInMode -> ph.alreadyInMode(r.dmc)
                        else -> null // el propio casco anuncia el modo nuevo
                    }
                }
            }

            Command.DmcGroupMute -> say(ph.ok()) {
                headsetCommand(HEADSET_WAIT_MS, { CardoLink.send(this, CardoLink.Event.DMC_GROUP_MUTE) }) { null }
            }

            Command.DmcMic -> say(ph.ok()) {
                headsetCommand(HEADSET_WAIT_MS, { CardoLink.send(this, CardoLink.Event.DMC_TALK) }) { null }
            }

            is Command.Radio -> radio(cmd)

            // Compartir: al terminar se suelta el foco y la música vuelve a sonar, ya compartida.
            Command.ShareMusic -> say(ph.ok()) {
                headsetCommand(HEADSET_WAIT_MS, { CardoLink.send(this, CardoLink.Event.MUSIC_SHARE) }) { null }
            }

            Command.ShareRadio -> say(ph.ok()) {
                headsetCommand(HEADSET_WAIT_MS, { CardoLink.toggleRadioSharing(this) }) { null }
            }

            is Command.IntercomCall -> say(ph.openingIntercom()) {
                headsetCommand(
                    HEADSET_WAIT_MS,
                    { CardoLink.send(this, CardoLink.Event.IC_CALL, cmd.channel) },
                ) { null }
            }

            Command.IntercomEnd -> say(ph.closingIntercom()) {
                headsetCommand(HEADSET_WAIT_MS, { CardoLink.endIntercom(this) }) { null }
            }

            Command.Redial -> say(ph.redialing()) {
                headsetCommand(HEADSET_WAIT_MS, { CardoLink.send(this, CardoLink.Event.PHONE_REDIAL) }) { null }
            }

            is Command.Media -> {
                releaseAudio()
                main.postDelayed({
                    Actions.media(this, cmd.action)
                    if (cmd.action != MediaAction.PLAY) return@postDelayed done()
                    // Si no había nada que reanudar, abre la app de música y que ponga algo.
                    main.postDelayed({
                        if (!am.isMusicActive) Actions.playSomething(this, if (lang == Lang.EN) "music" else "música")
                        done()
                    }, 1500)
                }, 700)
            }

            is Command.PlaySearch -> {
                // YouTube Music no deja a otras apps reproducir una búsqueda: se abren los resultados.
                val onlyResults = Actions.searchNeedsTap(this)
                say(if (onlyResults) ph.searchOpened(cmd.query) else ph.playing(cmd.query)) {
                    releaseAudio()
                    main.postDelayed({
                        if (Actions.playFromSearch(this, cmd.query)) done()
                        else say(ph.noMusicApp()) { done() }
                    }, 500)
                }
            }

            is Command.Volume -> afterAudio { Actions.volume(this, cmd.up) }

            Command.Time -> {
                val now = LocalTime.now()
                say(ph.time(now.hour, now.minute)) { done() }
            }

            is Command.Battery -> battery(cmd.of)
            Command.Help -> say(ph.help()) { done() }
            Command.Cancel -> say(ph.ok()) { done() }
        }
    }

    // --- Mensajes ---

    /**
     * Envía [text] a [who]: si le escribió hace poco por WhatsApp (u otra app que deje responder),
     * en esa conversación; si no, por SMS. Sin texto, primero pregunta qué decir.
     */
    private fun sendMessage(who: String, text: String?) {
        val chat = MessageStore.latestReplyableFrom(who)
        val contact = if (chat == null) Actions.findContact(this, who) else null
        val name = chat?.sender ?: contact?.name ?: return say(ph.contactNotFound(who)) { done() }
        fun confirm(body: String) {
            retries = MAX_RETRIES // en la confirmación no reintentamos: si no se oye, no se envía
            ask(ph.confirmMessage(name, body)) {
                listen { answer ->
                    if (!answer.any { CommandParser.isYes(it, lang) }) return@listen say(ph.cancelled()) { done() }
                    val sent = if (chat != null) MessageStore.reply(this, chat, body) else Actions.sms(this, contact!!.number, body)
                    say(if (sent) ph.sent() else ph.leftInMessages()) { done() }
                }
            }
        }
        if (text != null) return confirm(text)
        retries = 0
        ask(ph.whatToSay(name)) { listen(dictating = true) { dictated -> confirm(dictated.first()) } }
    }

    /** Lee los mensajes y ofrece responder al último (si su app lo permite). */
    private fun announceMessages(messages: List<IncomingMessage>) {
        messages.forEach { MessageStore.markRead(it.id) }
        val last = messages.last()
        val intro = if (messages.size == 1) {
            ph.oneMessage(last.sender, last.appLabel, last.text)
        } else {
            ph.manyMessages(messages.map { it.sender to it.text })
        }
        if (!last.canReply) return say(intro) { done() }
        retries = MAX_RETRIES // en preguntas de sí/no no se reintenta: si no se oye, no se hace nada
        ask("$intro ${ph.replyTo(last.sender)}") {
            listen { answer ->
                if (answer.any { CommandParser.isYes(it, lang) }) dictateReply(last) else say(ph.ok()) { done() }
            }
        }
    }

    private fun dictateReply(msg: IncomingMessage) {
        retries = 0
        ask(ph.sayYourReply()) {
            listen(dictating = true) { dictated ->
                val text = dictated.first()
                retries = MAX_RETRIES
                ask(ph.confirmReply(text)) {
                    listen { confirm ->
                        if (!confirm.any { CommandParser.isYes(it, lang) }) return@listen say(ph.cancelled()) { done() }
                        val sent = MessageStore.reply(this, msg, text)
                        say(if (sent) ph.sent() else ph.replyFailed()) { done() }
                    }
                }
            }
        }
    }

    // --- Ubicación ---

    private fun shareLocation(who: String) {
        if (!Places.hasPermission(this)) return say(ph.locationPermission()) { done() }
        show("…")
        sheet.setState(VoiceSheet.State.LOCATING)
        Thread {
            val loc = Places.current(this)
            main.post {
                if (loc == null) return@post say(ph.noLocation()) { done() }
                val text = ph.iAmHere(Places.mapsLink(loc))
                // Si te escribió hace poco por WhatsApp, se responde en esa conversación; si no, SMS.
                val chat = MessageStore.latestReplyableFrom(who)
                val contact = if (chat == null) Actions.findContact(this, who) else null
                val name = chat?.sender ?: contact?.name
                    ?: return@post say(ph.contactNotFound(who)) { done() }
                val via = chat?.appLabel ?: "SMS"
                retries = MAX_RETRIES
                ask(ph.confirmLocation(name, via)) {
                    listen { answer ->
                        if (!answer.any { CommandParser.isYes(it, lang) }) return@listen say(ph.cancelled()) { done() }
                        val sent = if (chat != null) MessageStore.reply(this, chat, text)
                        else Actions.sms(this, contact!!.number, text)
                        say(if (sent) ph.locationSent(name) else ph.leftInMessages()) { done() }
                    }
                }
            }
        }.start()
    }

    private fun whereAmI() {
        if (!Places.hasPermission(this)) return say(ph.locationPermission()) { done() }
        show("…")
        sheet.setState(VoiceSheet.State.LOCATING)
        Thread {
            val loc = Places.current(this)
            val place = loc?.let { Places.describe(this, it) }
            main.post {
                val text = when {
                    loc == null -> ph.noLocation()
                    place == null -> ph.noAddress()
                    else -> ph.youAreIn(place)
                }
                say(text) { done() }
            }
        }.start()
    }

    private fun weather() {
        if (!Places.hasPermission(this)) return say(ph.locationPermission()) { done() }
        show("…")
        sheet.setState(VoiceSheet.State.LOCATING)
        Thread {
            val now = Places.current(this)?.let { Weather.fetch(it) }
            main.post { say(now?.let(ph::weather) ?: ph.noWeather()) { done() } }
        }.start()
    }

    private fun battery(of: BatteryOf) {
        val phone = ph.phoneBattery(Actions.batteryPercent(this))
        if (of == BatteryOf.PHONE) return say(phone) { done() }
        // Solo lectura: el casco responde aunque el canal de voz siga abierto.
        show("…")
        sheet.setState(VoiceSheet.State.HEADSET)
        Thread {
            val percent = CardoLink.readBattery(this)
            main.post {
                val headset = ph.headsetBattery(percent)
                say(if (of == BatteryOf.BOTH) "$headset $phone" else headset) { done() }
            }
        }.start()
    }

    private fun radio(cmd: Command.Radio) {
        val (text, event, params) = when (cmd.action) {
            RadioAction.ON -> Triple(ph.radioOn(), CardoLink.Event.FM_ON, intArrayOf())
            RadioAction.OFF -> Triple(ph.radioOff(), CardoLink.Event.FM_OFF, intArrayOf())
            RadioAction.NEXT -> Triple(ph.seekingStation(), CardoLink.Event.FM_SEEK_UP, intArrayOf())
            RadioAction.PREVIOUS -> Triple(ph.seekingStation(), CardoLink.Event.FM_SEEK_DOWN, intArrayOf())
            RadioAction.STATION -> Triple(ph.station(cmd.station), CardoLink.Event.FM_PRESET, intArrayOf(cmd.station))
        }
        say(text) {
            // La música del móvil le quitaría el sitio a la radio: se pausa de verdad, no solo por el foco.
            if (cmd.action != RadioAction.OFF) Actions.media(this, MediaAction.PAUSE)
            headsetCommand(HEADSET_WAIT_MS, { CardoLink.send(this, event, *params) }) { null }
        }
    }

    /**
     * Envía una orden al casco por su canal de control. El casco ignora órdenes mientras el
     * canal de voz (SCO) está abierto, así que primero se cierra, manteniendo el foco de audio
     * para que la música siga en pausa. [reply] da la frase final para un resultado correcto
     * (null = no decir nada); los fallos tienen su propia frase.
     */
    private fun headsetCommand(
        waitMs: Long,
        work: () -> CardoLink.Result,
        reply: (CardoLink.Result) -> String?,
    ) {
        recognizer?.destroy()
        recognizer = null
        route.stop()
        onCallChannel = false
        speaker.useCallChannel = false
        show("…")
        sheet.setState(VoiceSheet.State.HEADSET)
        main.postDelayed({
            Thread {
                val result = work()
                Log.i(TAG, "casco: $result")
                main.post {
                    val text = when (result) {
                        CardoLink.Result.NoHeadset -> ph.noHeadset()
                        is CardoLink.Result.Failed -> ph.headsetFailed()
                        else -> reply(result)
                    }
                    if (text == null) done() else say(text) { done() }
                }
            }.start()
        }, waitMs)
    }

    /** Suelta el casco y espera a que vuelva la música antes de tocarla. */
    private fun afterAudio(action: () -> Unit) {
        releaseAudio()
        main.postDelayed({
            action()
            done()
        }, 700)
    }

    // --- Audio ---

    /**
     * Respuesta final: suelta el canal de llamada (mono, calidad telefónica y a volumen de llamada)
     * y habla por el de música, que suena mejor y respeta tu volumen. La música sigue en pausa
     * porque el foco de audio se mantiene hasta [done].
     */
    private fun say(text: String, then: () -> Unit) {
        show(text)
        sheet.setState(VoiceSheet.State.SPEAKING)
        if (onCallChannel) {
            listenId++
            recognizer?.destroy()
            recognizer = null
            route.stop()
            onCallChannel = false
            speaker.useCallChannel = false
        }
        // El casco hace un fundido al empezar a recibir música: que ocurra durante un silencio.
        // Se para al terminar la frase, porque con música sonando el casco no acepta órdenes.
        warmup.start()
        main.postDelayed({
            speaker.say(text) {
                warmup.stop()
                then()
            }
        }, A2DP_WARMUP_MS)
    }

    /**
     * Pregunta que espera respuesta: se dice por el canal de llamada, que así sigue abierto entre la
     * pregunta y tu respuesta. Cerrarlo y reabrirlo hacía que el micrófono del casco tardara en
     * volver a transmitir y se perdía el principio de lo que dices. El volumen de ese canal se baja
     * al "Volumen de la voz" mientras está abierto (ver BtAudioRoute).
     */
    private fun ask(text: String, then: () -> Unit) {
        if (onCallChannel) return speak(text, then)
        // Si ya se había pasado al canal de música, se reabre el de llamada antes de preguntar.
        sheet.setState(VoiceSheet.State.CONNECTING)
        route.start(VoicePrefs.load(this).volume) { sco ->
            onCallChannel = sco
            speaker.useCallChannel = sco
            main.postDelayed({ if (!isFinishing) speak(text, then) }, REOPEN_SETTLE_MS)
        }
    }

    private fun speak(text: String, then: () -> Unit) {
        show(text)
        sheet.setState(VoiceSheet.State.SPEAKING)
        speaker.say(text, then)
    }

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .build()
        focus = req
        am.requestAudioFocus(req)
    }

    private fun releaseAudio() {
        if (audioReleased || !::am.isInitialized) return
        audioReleased = true
        listenId++
        recognizer?.destroy()
        recognizer = null
        route.stop()
        focus?.let { am.abandonAudioFocusRequest(it) }
        focus = null
    }

    private fun done() {
        releaseAudio()
        finish()
    }

    private fun show(text: String) {
        sheet.setText(text)
    }

    companion object {
        const val ACTION_ANNOUNCE_MESSAGE = "dev.helmvox.ANNOUNCE_MESSAGE"
        const val EXTRA_MESSAGE_ID = "message_id"

        /** true mientras hay una sesión de voz abierta (para no pisarla con avisos). */
        @Volatile
        var isActive = false
            private set

        private const val TAG = "HelmVox"
        const val MAX_RETRIES = 1
        // Cambio del canal de llamada al de música más el fundido de entrada del casco.
        const val A2DP_WARMUP_MS = 1300L
        const val REOPEN_SETTLE_MS = 900L
        const val MAX_RECOGNIZER_RESTARTS = 2
        const val STANDBY_WAIT_MS = 2500L
        const val HEADSET_WAIT_MS = 1500L
        const val GOOGLE_APP = "com.google.android.googlequicksearchbox"
        const val GOOGLE_RECOGNIZER = "com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
    }
}
