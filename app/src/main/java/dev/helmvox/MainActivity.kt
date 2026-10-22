package dev.helmvox

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch

/** Pantalla principal: estado, prueba, permisos, avisos, casco, voz y lista de órdenes. */
class MainActivity : Activity() {

    private val ui by lazy { Ui(this) }
    private lateinit var chips: LinearLayout
    private lateinit var permissionRows: LinearLayout
    private lateinit var voicePanel: VoiceSettingsPanel

    private val runtimePermissions = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.READ_CONTACTS)
        add(Manifest.permission.CALL_PHONE)
        add(Manifest.permission.SEND_SMS)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(16), ui.dp(20), ui.dp(16), ui.dp(32))
        }

        column.addView(header())
        chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, ui.dp(14), 0, 0)
        }
        column.addView(chips)
        column.addView(ui.primaryButton(getString(R.string.try_now), R.drawable.ic_mic) {
            startActivity(Intent(this, VoiceActivity::class.java))
        })
        column.addView(ui.text(getString(R.string.or_say_ok_google), 13f, R.color.text_muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(8), 0, 0)
        })

        column.addView(ui.card().apply {
            addView(ui.cardTitle(R.drawable.ic_shield, getString(R.string.card_permissions)))
            permissionRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(permissionRows)
        })

        column.addView(ui.card().apply {
            addView(ui.cardTitle(R.drawable.ic_bell, getString(R.string.card_alerts)))
            addView(ui.row(
                R.drawable.ic_bell, getString(R.string.alert_messages), getString(R.string.alert_messages_sub),
                switch(AppPrefs.readMessages(context)) { AppPrefs.setReadMessages(context, it) },
            ))
            addView(ui.row(
                R.drawable.ic_battery, getString(R.string.alert_battery), getString(R.string.alert_battery_sub),
                switch(AppPrefs.batteryAlerts(context)) { AppPrefs.setBatteryAlerts(context, it) },
            ))
        })

        column.addView(ui.card().apply {
            addView(ui.cardTitle(R.drawable.ic_bluetooth, getString(R.string.card_helmet)))
            addView(ui.row(
                R.drawable.ic_build, getString(R.string.diagnosis), getString(R.string.diagnosis_sub),
                ui.icon(R.drawable.ic_chevron_right, R.color.text_muted),
            ) { runDiagnosis() })
        })

        voicePanel = VoiceSettingsPanel(this)
        column.addView(voicePanel)
        column.addView(AiSettingsPanel(this))
        column.addView(commandsCard())
        if (COFFEE_URL.isNotBlank()) column.addView(coffeeCard())

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(column)
            // Android 15+ dibuja la app bajo las barras del sistema: dejar su hueco arriba y abajo.
            setOnApplyWindowInsetsListener { _, insets ->
                val top = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).top
                else @Suppress("DEPRECATION") insets.systemWindowInsetTop
                val bottom = if (Build.VERSION.SDK_INT >= 30) insets.getInsets(WindowInsets.Type.systemBars()).bottom
                else @Suppress("DEPRECATION") insets.systemWindowInsetBottom
                column.setPadding(ui.dp(16), top + ui.dp(20), ui.dp(16), bottom + ui.dp(32))
                insets
            }
        })
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroy() {
        voicePanel.shutdown()
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(requestCode: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, perms, results)
        refreshStatus()
    }

    // --- Cabecera y estado ---

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(FrameLayout(context).apply {
            background = ui.rounded(R.color.primary, 16)
            layoutParams = LinearLayout.LayoutParams(ui.dp(52), ui.dp(52))
            addView(ui.icon(R.drawable.ic_mic, R.color.on_primary, 30).apply {
                layoutParams = FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.CENTER)
            })
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dp(14), 0, 0, 0)
            addView(ui.text("HelmVox", 26f, bold = true))
            addView(ui.text(getString(R.string.tagline), 14f, R.color.text_muted))
        })
    }

    private fun refreshStatus() {
        val runtimeOk = runtimePermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val notificationsOk = notificationAccess()
        val overlayOk = Settings.canDrawOverlays(this)
        val defaultOk = isDefaultVoiceApp()

        chips.removeAllViews()
        val headset = Headset.isConnected(this)
        chips.addView(ui.chip(getString(if (headset) R.string.chip_headset_on else R.string.chip_headset_off), headset))
        val allOk = runtimeOk && notificationsOk && overlayOk && defaultOk
        chips.addView(ui.chip(if (allOk) getString(R.string.chip_ready) else getString(R.string.chip_missing), allOk))

        permissionRows.removeAllViews()
        permissionRows.addView(permissionRow(
            getString(R.string.perm_basic), getString(R.string.perm_basic_sub), runtimeOk,
        ) { requestPermissions(runtimePermissions.toTypedArray(), 1) })
        permissionRows.addView(permissionRow(
            getString(R.string.perm_notifications), getString(R.string.perm_notifications_sub), notificationsOk,
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        permissionRows.addView(permissionRow(
            getString(R.string.perm_overlay), getString(R.string.perm_overlay_sub), overlayOk,
        ) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        permissionRows.addView(permissionRow(
            getString(R.string.perm_voice_app), getString(R.string.perm_voice_app_sub), defaultOk,
        ) {
            // Abre el selector de Android: elige HelmVox → Siempre.
            startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }

    private fun permissionRow(title: String, subtitle: String, ok: Boolean, fix: () -> Unit) =
        ui.row(null, title, if (ok) subtitle else getString(R.string.perm_tap_to_fix, subtitle), ui.statusIcon(ok),
            if (ok) null else fix)

    private fun notificationAccess(): Boolean =
        getSystemService(NotificationManager::class.java)
            .isNotificationListenerAccessGranted(ComponentName(this, CascoListenerService::class.java))

    private fun isDefaultVoiceApp(): Boolean =
        packageManager.resolveActivity(Intent(Intent.ACTION_VOICE_COMMAND), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName == packageName

    private fun switch(initial: Boolean, onChange: (Boolean) -> Unit) = Switch(this).apply {
        isChecked = initial
        setOnCheckedChangeListener { _, on -> onChange(on) }
    }

    // --- Órdenes ---

    private fun commandsCard() = ui.card().apply {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val chevron = ui.icon(R.drawable.ic_chevron_down, R.color.text_muted)
        addView(ui.row(R.drawable.ic_list, getString(R.string.commands_title), getString(R.string.commands_sub), chevron) {
            val open = body.visibility != View.VISIBLE
            body.visibility = if (open) View.VISIBLE else View.GONE
            chevron.rotation = if (open) 180f else 0f
        })
        for ((groupRes, itemsRes) in COMMANDS) {
            val items = resources.getStringArray(itemsRes)
            body.addView(ui.text(getString(groupRes), 14f, R.color.primary, bold = true).apply {
                setPadding(0, ui.dp(14), 0, ui.dp(4))
            })
            for (item in items) body.addView(ui.text("•  $item", 14f, R.color.text_muted).apply {
                setPadding(0, ui.dp(2), 0, ui.dp(2))
            })
        }
        addView(body)
    }

    // --- Apoyo ---

    private fun coffeeCard() = ui.card().apply {
        addView(ui.row(
            R.drawable.ic_coffee, getString(R.string.coffee),
            getString(R.string.coffee_sub),
            ui.icon(R.drawable.ic_chevron_right, R.color.text_muted),
        ) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COFFEE_URL))) })
    }

    // --- Diagnóstico ---

    private fun runDiagnosis() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.diagnosis_title)
            .setMessage(R.string.diagnosis_reading)
            .setPositiveButton(R.string.close, null)
            .show()
        Thread {
            val (result, d) = CardoLink.diagnose(this)
            runOnUiThread { dialog.setMessage(describe(result, d)) }
        }.start()
    }

    private fun describe(result: CardoLink.Result, d: CardoLink.Diagnosis?): String {
        if (result == CardoLink.Result.NoHeadset) return getString(R.string.diag_no_headset)
        if (result is CardoLink.Result.Failed || d == null) return getString(R.string.diag_failed)
        val unknown = getString(R.string.unknown)
        fun yesNo(b: Boolean?) = when (b) {
            true -> getString(R.string.yes)
            false -> getString(R.string.no)
            null -> unknown
        }
        fun sensitivity(v: Int?) = when (v) {
            null -> unknown
            50 -> getString(R.string.sens_high, v)
            100 -> getString(R.string.sens_medium_high, v)
            150 -> getString(R.string.sens_medium, v)
            200, 210 -> getString(R.string.sens_medium_low, v)
            250 -> getString(R.string.sens_low, v)
            else -> v.toString()
        }
        return buildString {
            appendLine(getString(R.string.diag_mode, d.dmcMode?.let { if (it) "DMC" else "Bluetooth" } ?: unknown))
            appendLine(getString(R.string.diag_battery, d.batteryPercent?.let { "~$it %" } ?: unknown))
            appendLine()
            appendLine(getString(R.string.diag_voice_header))
            appendLine(getString(R.string.diag_voice_supported, yesNo(d.advancedVoiceSupported)))
            appendLine(getString(R.string.diag_voice_enabled, yesNo(d.advancedVoiceEnabled)))
            appendLine(getString(R.string.diag_voice_sensitivity, sensitivity(d.endDetectThreshold)))
            appendLine(getString(R.string.diag_voice_ok_google, sensitivity(d.okGoogleThreshold)))
            appendLine(getString(R.string.diag_language, d.languageCode?.toString() ?: unknown))
            if (d.advancedVoiceEnabled == false) {
                appendLine()
                appendLine(getString(R.string.diag_enable_hint))
            }
            appendLine()
            append(getString(R.string.diag_raw, d.raw))
        }
    }

    private companion object {
        /** Enlace de "Regálame un café" (Buy Me a Coffee, Ko-fi…). Vacío = no se muestra la tarjeta. */
        const val COFFEE_URL = "https://buymeacoffee.com/SpawnRipple"

        /** Grupos de la lista "Qué puedes decir": título y órdenes (en res/values*). */
        val COMMANDS = listOf(
            R.string.cmd_group_route to R.array.cmd_route,
            R.string.cmd_group_phone to R.array.cmd_phone,
            R.string.cmd_group_music to R.array.cmd_music,
            R.string.cmd_group_helmet to R.array.cmd_helmet,
            R.string.cmd_group_info to R.array.cmd_info,
        )
    }
}
