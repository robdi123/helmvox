package dev.helmvox

import android.app.Notification
import android.app.Person
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/**
 * Servicio que Android mantiene vivo mientras tenga permiso de "acceso a notificaciones":
 *  - Lee los mensajes nuevos de WhatsApp y SMS y, con el casco conectado, abre HelmVox para
 *    leerlos y dejarte responder.
 *  - Vigila la batería del móvil y, cada 10 minutos, la del casco, y avisa si bajan del 20 % / 10 %.
 */
class CascoListenerService : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private val phoneBattery = BatteryWatch()
    private val headsetBattery = BatteryWatch()
    private var batteryReceiver: BroadcastReceiver? = null

    private val headsetCheck = object : Runnable {
        override fun run() {
            checkHeadsetBattery()
            main.postDelayed(this, HEADSET_BATTERY_EVERY_MS)
        }
    }

    override fun onListenerConnected() {
        Log.i(TAG, "servicio de notificaciones conectado")
        instance = this
        // Los mensajes que ya estaban en la barra antes de arrancar: guardarlos sin anunciarlos.
        ingestActive()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onPhoneBattery(i)
        }
        batteryReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        main.postDelayed(headsetCheck, HEADSET_BATTERY_EVERY_MS)
    }

    override fun onListenerDisconnected() {
        instance = null
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
        main.removeCallbacks(headsetCheck)
    }

    // --- Mensajes ---

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val msg = ingest(sbn).lastOrNull() ?: return
        Log.i(TAG, "mensaje de ${msg.sender} por ${msg.appLabel} (responder: ${msg.canReply})")

        if (!AppPrefs.readMessages(this) || !Headset.isConnected(this) || VoiceActivity.isActive) return
        if (Settings.canDrawOverlays(this)) {
            // Con permiso para mostrarse encima, Android deja abrir la pantalla de voz desde aquí.
            startActivity(
                Intent(this, VoiceActivity::class.java)
                    .setAction(VoiceActivity.ACTION_ANNOUNCE_MESSAGE)
                    .putExtra(VoiceActivity.EXTRA_MESSAGE_ID, msg.id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } else {
            MessageStore.markRead(msg.id)
            Announcer(this).say(phrases().announceMessage(msg.sender, msg.text))
        }
    }

    /** Recoge las notificaciones de mensajería que ya están en la barra (sin anunciarlas). */
    fun ingestActive() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val added = active.sortedBy { it.postTime }.sumOf { ingest(it).size }
        Log.i(TAG, "mensajes ya en la barra: $added nuevos")
    }

    /** Guarda los mensajes de una notificación de mensajería; devuelve los que eran nuevos. */
    private fun ingest(sbn: StatusBarNotification): List<IncomingMessage> {
        val label = MESSAGING_APPS[sbn.packageName] ?: return emptyList()
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return emptyList()
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return emptyList()
        return parse(n).mapNotNull { (sender, text) -> MessageStore.add(sbn.packageName, label, sender, text, n) }
    }

    /**
     * Remitente y texto de cada mensaje de la notificación (MessagingStyle guarda los no leídos de
     * esa conversación); si la app no usa ese estilo, el título y el texto de la notificación.
     */
    @Suppress("DEPRECATION")
    private fun parse(n: Notification): List<Pair<String, String>> {
        val extras = n.extras
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
        // Cada mensaje es un Bundle con "text" y "sender_person" / "sender".
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.mapNotNull { it as? Bundle }.orEmpty()
        if (messages.isEmpty()) {
            val text = extras.getCharSequence(Notification.EXTRA_TEXT) ?: return emptyList()
            return listOfNotNull(title?.let { it.toString() to text.toString() })
        }
        return messages.mapNotNull { m ->
            val sender = (m.getParcelable<Person>("sender_person"))?.name ?: m.getCharSequence("sender") ?: title
            val text = m.getCharSequence("text")
            if (sender == null || text == null) return@mapNotNull null
            // En grupos: "Nombre del grupo, Juan".
            val who = if (title != null && title.toString() != sender.toString()) "$title, $sender" else sender.toString()
            who to text.toString()
        }
    }

    /** Frases en el idioma de la conversación (puede cambiar en ajustes mientras el servicio vive). */
    private fun phrases() = Phrases(VoicePrefs.load(this).lang)

    // --- Batería ---

    private fun onPhoneBattery(i: Intent) {
        if (!AppPrefs.batteryAlerts(this)) return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0) return
        val percent = level * 100 / scale
        val charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        val crossed = phoneBattery.update(percent, charging) ?: return
        if (Headset.isConnected(this) && !VoiceActivity.isActive) {
            Announcer(this).say(phrases().phoneBatteryAlert(percent))
        }
        Log.i(TAG, "batería del móvil bajo el $crossed %")
    }

    private fun checkHeadsetBattery() {
        if (!AppPrefs.batteryAlerts(this) || !Headset.isConnected(this) || VoiceActivity.isActive) return
        Thread {
            val percent = CardoLink.readBattery(this) ?: return@Thread
            main.post {
                val crossed = headsetBattery.update(percent, charging = false) ?: return@post
                Log.i(TAG, "batería del casco bajo el $crossed %")
                if (!VoiceActivity.isActive) Announcer(this).say(phrases().headsetBatteryAlert(percent))
            }
        }.start()
    }

    companion object {
        /** El servicio conectado (si Android lo tiene en marcha), para leer la barra a demanda. */
        @Volatile
        var instance: CascoListenerService? = null
            private set

        private const val TAG = "HelmVox"
        private const val HEADSET_BATTERY_EVERY_MS = 10 * 60 * 1000L
        private val MESSAGING_APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.messaging" to "Mensajes",
            "com.samsung.android.messaging" to "Mensajes",
        )
    }
}
