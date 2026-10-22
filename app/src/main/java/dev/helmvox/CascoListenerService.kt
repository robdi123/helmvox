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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = onPhoneBattery(i)
        }
        batteryReceiver = receiver
        registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        main.postDelayed(headsetCheck, HEADSET_BATTERY_EVERY_MS)
    }

    override fun onListenerDisconnected() {
        batteryReceiver?.let { runCatching { unregisterReceiver(it) } }
        batteryReceiver = null
        main.removeCallbacks(headsetCheck)
    }

    // --- Mensajes ---

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val label = MESSAGING_APPS[sbn.packageName] ?: return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        val (sender, text) = parse(n) ?: return
        val msg = MessageStore.add(sbn.packageName, label, sender, text, n) ?: return
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
            Announcer(this).say("Mensaje de ${msg.sender}: ${msg.text}")
        }
    }

    /** Remitente y texto del último mensaje; usa MessagingStyle si la app lo publica. */
    @Suppress("DEPRECATION")
    private fun parse(n: Notification): Pair<String, String>? {
        val extras = n.extras
        // MessagingStyle guarda cada mensaje como un Bundle con "text" y "sender_person" / "sender".
        val last = extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? Bundle
        val lastSender = (last?.getParcelable<Person>("sender_person"))?.name ?: last?.getCharSequence("sender")
        val title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)
        val sender = lastSender ?: title ?: return null
        val text = last?.getCharSequence("text") ?: extras.getCharSequence(Notification.EXTRA_TEXT) ?: return null
        // En grupos: "Nombre del grupo, Juan".
        val who = if (title != null && lastSender != null && title.toString() != lastSender.toString()) "$title, $lastSender" else sender
        return who.toString() to text.toString()
    }

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
            Announcer(this).say("Atención: al móvil le queda el $percent por ciento de batería.")
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
                val text = if (percent == 0) "Atención: al casco casi no le queda batería."
                else "Atención: al casco le queda más o menos el $percent por ciento de batería."
                if (!VoiceActivity.isActive) Announcer(this).say(text)
            }
        }.start()
    }

    private companion object {
        const val TAG = "HelmVox"
        const val HEADSET_BATTERY_EVERY_MS = 10 * 60 * 1000L
        val MESSAGING_APPS = mapOf(
            "com.whatsapp" to "WhatsApp",
            "com.whatsapp.w4b" to "WhatsApp Business",
            "org.telegram.messenger" to "Telegram",
            "com.google.android.apps.messaging" to "Mensajes",
            "com.samsung.android.messaging" to "Mensajes",
        )
    }
}
