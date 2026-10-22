package dev.helmvox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.annotation.TargetApi

/**
 * Abre el enlace de voz Bluetooth (SCO/HFP) con el casco para usar su micrófono y
 * su altavoz. Llama a `onReady(true)` cuando el enlace está listo, o `onReady(false)`
 * si no hay casco o tarda demasiado (entonces se usa el micrófono del móvil).
 */
class BtAudioRoute(private val ctx: Context) {

    private val am = ctx.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private var onReady: ((Boolean) -> Unit)? = null
    private var deviceListener: Any? = null
    private var scoReceiver: BroadcastReceiver? = null
    private var active = false

    private val timeout = Runnable { deliver(false) }

    fun start(onReady: (Boolean) -> Unit) {
        this.onReady = onReady
        active = true
        am.mode = AudioManager.MODE_IN_COMMUNICATION
        main.postDelayed(timeout, TIMEOUT_MS)
        if (Build.VERSION.SDK_INT >= 31) startModern() else startLegacy()
    }

    fun stop() {
        if (!active) return
        active = false
        onReady = null
        main.removeCallbacks(timeout)
        if (Build.VERSION.SDK_INT >= 31) stopModern() else stopLegacy()
        am.mode = AudioManager.MODE_NORMAL
    }

    private fun deliver(sco: Boolean) {
        val cb = onReady ?: return
        onReady = null
        main.removeCallbacks(timeout)
        // Pequeña pausa para que el enlace SCO termine de asentarse antes de hablar.
        main.postDelayed({ cb(sco) }, if (sco) SETTLE_MS else 0)
    }

    @TargetApi(31)
    private fun startModern() {
        val dev = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
            ?: return deliver(false)
        val listener = AudioManager.OnCommunicationDeviceChangedListener { d ->
            if (d?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) deliver(true)
        }
        deviceListener = listener
        am.addOnCommunicationDeviceChangedListener(ctx.mainExecutor, listener)
        if (!am.setCommunicationDevice(dev)) return deliver(false)
        if (am.communicationDevice?.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) deliver(true)
    }

    @TargetApi(31)
    private fun stopModern() {
        (deviceListener as? AudioManager.OnCommunicationDeviceChangedListener)
            ?.let { am.removeOnCommunicationDeviceChangedListener(it) }
        deviceListener = null
        am.clearCommunicationDevice()
    }

    @Suppress("DEPRECATION")
    private fun startLegacy() {
        if (!am.isBluetoothScoAvailableOffCall) return deliver(false)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val state = i.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)
                if (state == AudioManager.SCO_AUDIO_STATE_CONNECTED) deliver(true)
            }
        }
        scoReceiver = receiver
        ctx.registerReceiver(receiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        am.startBluetoothSco()
        am.isBluetoothScoOn = true
    }

    @Suppress("DEPRECATION")
    private fun stopLegacy() {
        scoReceiver?.let { runCatching { ctx.unregisterReceiver(it) } }
        scoReceiver = null
        am.isBluetoothScoOn = false
        am.stopBluetoothSco()
    }

    private companion object {
        const val TIMEOUT_MS = 4000L
        const val SETTLE_MS = 400L
    }
}
