package dev.helmvox

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Canal de control de los cascos Cardo clásicos (PackTalk, PackTalk Bold…). El protocolo está
 * documentado en PROTOCOL.md; resumen:
 *   - RFCOMM (puerto serie Bluetooth), canal 12.
 *   - Cada paquete: [versión][longitud de datos][código][datos…], sin checksum.
 *   - Los "eventos" (código 0x20) llevan como datos: [0][evento][nº parámetros][parámetros…].
 *   - GetSettings (código 0x01) se responde con código 0x81 y una lista [tipo][longitud][valor…].
 *   - RegisterServices (código 0x10) suscribe a servicios; sus avisos llegan con código 0x82.
 */
object CardoLink {

    private const val TAG = "CardoLink"
    private const val RFCOMM_CHANNEL = 12
    private val FALLBACK_UUIDS = listOf(
        UUID.fromString("cd007f85-8b0b-11e6-ae22-56b6b6499611"),
        UUID.fromString("0000fd01-0000-1000-8000-00805f9b34fb"),
    )
    private val NAME_HINTS = listOf("packtalk", "cardo", "freecom", "smartpack", "spirit")

    private const val CAIP_VERSION: Byte = 0x09
    private const val OP_EVENT: Byte = 0x20
    private const val OP_GET_SETTINGS: Byte = 0x01
    private const val OP_SETTINGS_RESPONSE = 0x81
    private const val SETTING_PACKTALK_TOGGLE = 0x13 // 1 = DMC, 0 = intercom Bluetooth
    private const val SETTING_LANGUAGE = 0x07
    private const val SETTING_PPF = 0x16 // capacidades del casco
    private const val SETTING_ASR_CONFIDENCE = 0x1A // [sensibilidad][umbral "Ok Google"]
    private const val SETTING_ASR_ACTIVE = 0x1B // comandos de voz avanzados activados
    private val DIAGNOSTIC_SETTINGS =
        listOf(SETTING_PACKTALK_TOGGLE, SETTING_LANGUAGE, SETTING_PPF, SETTING_ASR_CONFIDENCE, SETTING_ASR_ACTIVE)
    private const val OP_REGISTER_SERVICES: Byte = 0x10
    private const val OP_SERVICE_UPDATE = 0x82 // [ver][len][0x82][servicio][?][estado…]
    private const val SERVICE_BATTERY = 0x01

    /** Saludo inicial: petición de versión, el casco responde con su versión de protocolo. */
    private val GET_VERSION = byteArrayOf(0x03, 0x00, 0x00)

    /**
     * Eventos del casco (ver PROTOCOL.md, "Eventos").
     * [profile] es el primer byte de los datos: 0x00 en general, 0x60 en los de teléfono.
     */
    enum class Event(val code: Int, val profile: Int = 0x00) {
        DMC_BT_SWITCH(0xCB), // alterna intercom DMC ⇄ Bluetooth (solo con el casco en reposo)
        DMC_GROUP_MUTE(0xF2), // silencia / reactiva el grupo DMC
        DMC_TALK(0xF1), // abre / cierra tu micrófono hacia el grupo DMC
        FM_ON(0x02), // enciende la radio FM
        FM_OFF(0x03), // apaga la radio FM
        FM_SEEK_UP(0x07), // busca la emisora siguiente
        FM_SEEK_DOWN(0x06), // busca la emisora anterior
        FM_PRESET(0xBB), // emisora guardada n (parámetro), n = 1…6
        MUSIC_SHARE(0x2D), // empieza / deja de compartir la música
        IC_CALL(0x9E), // abre el intercom Bluetooth (parámetro: canal A = 1, B = 2, C = 3)
        IC_END(0x17), // corta el intercom Bluetooth (parámetro: canal)
        PHONE_REDIAL(0x05, profile = 0x60), // repite la última llamada
    }

    /** Compartir la radio FM: código de operación propio (0x28) y sin datos. */
    private const val OP_FM_SHARE_TOGGLE: Byte = 0x28

    sealed interface Result {
        data object Ok : Result
        data object NoHeadset : Result
        data class Failed(val reason: String) : Result
        /** Ya estaba en el modo pedido; no se ha tocado nada. */
        data class AlreadyInMode(val dmc: Boolean) : Result
        /** Se envió el cambio; [nowDmc] es el modo nuevo si se conocía el anterior. */
        data class Switched(val nowDmc: Boolean?) : Result
    }

    /** Pone el intercom en DMC (true), Bluetooth (false) o lo alterna (null). Bloqueante. */
    fun setIntercomMode(ctx: Context, wantDmc: Boolean?): Result = withSession(ctx) { s ->
        val current = s.readDmcMode()
        Log.i(TAG, "modo actual: ${current?.let { if (it) "DMC" else "Bluetooth" } ?: "desconocido"}")
        if (wantDmc != null && current == wantDmc) return@withSession Result.AlreadyInMode(current)
        s.event(Event.DMC_BT_SWITCH)
        Result.Switched(current?.not() ?: wantDmc)
    }

    /**
     * Batería del casco en %, aproximada: el casco solo informa por tramos
     * (0, 20, 50, 75, 80, 100). Bloqueante.
     */
    fun readBattery(ctx: Context): Int? {
        var percent: Int? = null
        val result = withSession(ctx) { s ->
            percent = s.readBattery()
            Result.Ok
        }
        Log.i(TAG, "batería del casco: $percent ($result)")
        return percent
    }

    /** Solo lectura: lo que interesa para saber por qué el casco no entiende "Ok Google". Bloqueante. */
    data class Diagnosis(
        val dmcMode: Boolean?,
        val advancedVoiceSupported: Boolean?, // PPF, byte 3, bit 5 ("speechless")
        val advancedVoiceEnabled: Boolean?, // ajuste 27
        val endDetectThreshold: Int?, // ajuste 26, byte 0: sensibilidad (50 = alta … 250 = baja)
        val okGoogleThreshold: Int?, // ajuste 26, byte 1
        val languageCode: Int?, // ajuste 7
        val batteryPercent: Int?,
        val raw: String,
    )

    fun diagnose(ctx: Context): Pair<Result, Diagnosis?> {
        var diagnosis: Diagnosis? = null
        val result = withSession(ctx) { s ->
            val frame = s.readSettings(DIAGNOSTIC_SETTINGS)
            val battery = s.readBattery()
            diagnosis = frame?.let { parseDiagnosis(it, battery) }
                ?: Diagnosis(null, null, null, null, null, null, battery, "sin respuesta de ajustes")
            Result.Ok
        }
        return result to diagnosis
    }

    internal fun parseDiagnosis(frame: ByteArray, battery: Int?): Diagnosis {
        val ppf = settingValue(frame, SETTING_PPF)
        val asr = settingValue(frame, SETTING_ASR_CONFIDENCE)
        return Diagnosis(
            dmcMode = settingValue(frame, SETTING_PACKTALK_TOGGLE)?.firstOrNull()?.let { it.toInt() == 1 },
            advancedVoiceSupported = ppf?.getOrNull(3)?.let { (it.toInt() shr 5) and 1 == 1 },
            advancedVoiceEnabled = settingValue(frame, SETTING_ASR_ACTIVE)?.firstOrNull()?.let { it.toInt() != 0 },
            endDetectThreshold = asr?.getOrNull(0)?.let { it.toInt() and 0xFF },
            okGoogleThreshold = asr?.getOrNull(1)?.let { it.toInt() and 0xFF },
            languageCode = settingValue(frame, SETTING_LANGUAGE)?.firstOrNull()?.let { it.toInt() and 0xFF },
            batteryPercent = battery,
            raw = frame.hex(),
        )
    }

    /** Envía un evento al casco. Bloqueante. */
    fun send(ctx: Context, event: Event, vararg params: Int): Result = withSession(ctx) { s ->
        s.event(event, *params)
        Result.Ok
    }

    /** Corta el intercom Bluetooth en los tres canales (no sabemos cuál está abierto). Bloqueante. */
    fun endIntercom(ctx: Context): Result = withSession(ctx) { s ->
        for (channel in 1..3) s.event(Event.IC_END, channel)
        Result.Ok
    }

    /** Empieza / deja de compartir la radio FM con el intercom. Bloqueante. */
    fun toggleRadioSharing(ctx: Context): Result = withSession(ctx) { s ->
        s.raw(OP_FM_SHARE_TOGGLE, ByteArray(0))
        Result.Ok
    }

    private fun withSession(ctx: Context, block: (Session) -> Result): Result {
        val device = findHeadset(ctx) ?: return Result.NoHeadset
        val socket = open(device) ?: return Result.Failed("no pude abrir el canal de control")
        return try {
            val session = Session(socket)
            session.handshake()
            block(session)
        } catch (e: IOException) {
            Log.w(TAG, "fallo en la sesión", e)
            Result.Failed(e.message ?: "error de envío")
        } finally {
            runCatching { socket.close() }
        }
    }

    private class Session(socket: BluetoothSocket) {
        private val input = socket.inputStream
        private val output = socket.outputStream
        private val buffer = ByteArrayOutputStream()

        fun handshake() {
            output.write(GET_VERSION)
            awaitFrame(1500) { true }?.let { Log.i(TAG, "versión: ${it.hex()}") }
        }

        fun event(event: Event, vararg params: Int) {
            val payload = byteArrayOf(event.profile.toByte(), event.code.toByte(), params.size.toByte()) +
                ByteArray(params.size) { params[it].toByte() }
            raw(OP_EVENT, payload, label = event.name)
        }

        fun raw(opcode: Byte, payload: ByteArray, label: String = "0x%02x".format(opcode)) {
            write(opcode, payload)
            // El casco no siempre responde; esperamos un poco para no cortar antes de que lo procese.
            awaitFrame(1200) { true }?.let { Log.i(TAG, "respuesta a $label: ${it.hex()}") }
                ?: Log.i(TAG, "sin respuesta a $label")
        }

        /** Lee el ajuste nº 19 (modo de intercom): true = DMC, false = Bluetooth, null = no lo sé. */
        fun readDmcMode(): Boolean? {
            val frame = readSettings(listOf(SETTING_PACKTALK_TOGGLE)) ?: return null
            return settingValue(frame, SETTING_PACKTALK_TOGGLE)?.firstOrNull()?.let { it.toInt() == 1 }
        }

        /** GetSettings con varios tipos: los datos son pares [tipo][0]. */
        fun readSettings(types: List<Int>): ByteArray? {
            write(OP_GET_SETTINGS, ByteArray(types.size * 2) { if (it % 2 == 0) types[it / 2].toByte() else 0 })
            val frame = awaitFrame(2500) { isSettingsFrame(it) } ?: return null
            Log.i(TAG, "ajustes: ${frame.hex()}")
            return frame
        }

        /** Se suscribe al servicio de batería; el casco responde con su estado actual. */
        fun readBattery(): Int? {
            write(OP_REGISTER_SERVICES, byteArrayOf(SERVICE_BATTERY.toByte()))
            val frame = awaitFrame(2500) { isBatteryUpdate(it) } ?: return null
            Log.i(TAG, "servicio batería: ${frame.hex()}")
            return batteryPercent(frame)
        }

        private fun write(opcode: Byte, payload: ByteArray) {
            output.write(byteArrayOf(CAIP_VERSION, payload.size.toByte(), opcode) + payload)
            output.flush()
        }

        /** Espera una trama completa que cumpla [accept]; descarta (y registra) las demás. */
        private fun awaitFrame(ms: Long, accept: (ByteArray) -> Boolean): ByteArray? {
            val deadline = System.currentTimeMillis() + ms
            while (true) {
                nextFrame()?.let { frame ->
                    if (accept(frame)) return frame
                    Log.i(TAG, "trama ignorada: ${frame.hex()}")
                }
                if (System.currentTimeMillis() >= deadline) return null
                if (input.available() > 0) {
                    val chunk = ByteArray(input.available().coerceAtMost(1024))
                    val n = input.read(chunk)
                    if (n > 0) buffer.write(chunk, 0, n)
                } else {
                    Thread.sleep(30)
                }
            }
        }

        private fun nextFrame(): ByteArray? {
            val bytes = buffer.toByteArray()
            if (bytes.size < 3) return null
            val size = 3 + (bytes[1].toInt() and 0xFF)
            if (bytes.size < size) return null
            buffer.reset()
            buffer.write(bytes, size, bytes.size - size)
            return bytes.copyOf(size)
        }
    }

    /**
     * Respuesta de ajustes: el casco contesta a una petición con el mismo código (0x01) y, cuando
     * los manda por su cuenta (p. ej. tras cambiar de modo), con 0x81.
     */
    internal fun isSettingsFrame(frame: ByteArray) =
        frame.size > 3 && (frame[2].toInt() and 0xFF).let { it == OP_SETTINGS_RESPONSE || it == OP_GET_SETTINGS.toInt() }

    internal fun isBatteryUpdate(frame: ByteArray) =
        frame.size >= 6 && (frame[2].toInt() and 0xFF) == OP_SERVICE_UPDATE && frame[3].toInt() == SERVICE_BATTERY

    /** Estado de batería (byte 5 del aviso del servicio) → % aproximado (ver PROTOCOL.md). */
    internal fun batteryPercent(frame: ByteArray): Int? = when (frame.getOrNull(5)?.toInt()) {
        0 -> 0
        1 -> 20
        3 -> 50
        5 -> 75
        7 -> 80
        9 -> 100
        else -> null
    }

    /** Busca [type] en la lista [tipo][longitud][valor…] de una respuesta de ajustes. */
    internal fun settingValue(frame: ByteArray, type: Int): ByteArray? {
        var i = 3
        while (i + 1 < frame.size) {
            val t = frame[i].toInt() and 0xFF
            val len = frame[i + 1].toInt() and 0xFF
            if (i + 2 + len > frame.size) return null
            if (t == type) return frame.copyOfRange(i + 2, i + 2 + len)
            i += 2 + len
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun findHeadset(ctx: Context): BluetoothDevice? {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter ?: return null
        return runCatching {
            adapter.bondedDevices.firstOrNull { d ->
                val name = d.name?.lowercase().orEmpty()
                NAME_HINTS.any { it in name }
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission", "DiscouragedPrivateApi")
    private fun open(device: BluetoothDevice): BluetoothSocket? {
        val attempts = buildList<() -> BluetoothSocket> {
            add {
                // Canal RFCOMM fijo (12), sin consulta SDP.
                device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                    .invoke(device, RFCOMM_CHANNEL) as BluetoothSocket
            }
            FALLBACK_UUIDS.forEach { uuid -> add { device.createInsecureRfcommSocketToServiceRecord(uuid) } }
        }
        for (make in attempts) {
            val s = runCatching(make).getOrNull() ?: continue
            try {
                s.connect()
                return s
            } catch (e: IOException) {
                Log.w(TAG, "connect falló", e)
                runCatching { s.close() }
            }
        }
        return null
    }

    private fun ByteArray.hex() = joinToString(" ") { "%02x".format(it) }
}
