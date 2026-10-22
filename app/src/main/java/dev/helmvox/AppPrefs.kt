package dev.helmvox

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Interruptores de la pantalla principal. */
object AppPrefs {
    private const val FILE = "app"
    private const val KEY_READ_MESSAGES = "read_messages"
    private const val KEY_BATTERY_ALERTS = "battery_alerts"

    fun readMessages(ctx: Context) = prefs(ctx).getBoolean(KEY_READ_MESSAGES, true)
    fun setReadMessages(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_READ_MESSAGES, on).apply()

    fun batteryAlerts(ctx: Context) = prefs(ctx).getBoolean(KEY_BATTERY_ALERTS, true)
    fun setBatteryAlerts(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean(KEY_BATTERY_ALERTS, on).apply()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/** ¿Está el casco Cardo conectado como salida de audio? Solo entonces se leen mensajes y avisos. */
object Headset {
    private val NAME_HINTS = listOf("packtalk", "cardo", "freecom", "smartpack", "spirit")

    fun isConnected(ctx: Context): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java)
        return am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { d ->
            (d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) &&
                NAME_HINTS.any { it in d.productName.toString().lowercase() }
        }
    }
}
