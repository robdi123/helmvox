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
        column.addView(ui.primaryButton("Probar ahora", R.drawable.ic_mic) {
            startActivity(Intent(this, VoiceActivity::class.java))
        })
        column.addView(ui.text("…o di «Ok Google» al casco", 13f, R.color.text_muted).apply {
            gravity = Gravity.CENTER
            setPadding(0, ui.dp(8), 0, 0)
        })

        column.addView(ui.card().apply {
            addView(ui.cardTitle(R.drawable.ic_shield, "Permisos"))
            permissionRows = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            addView(permissionRows)
        })

        column.addView(ui.card().apply {
            addView(ui.cardTitle(R.drawable.ic_bell, "Avisos en el casco"))
            addView(ui.row(
                R.drawable.ic_bell, "Mensajes", "Te lee WhatsApp, Telegram y SMS al llegar",
                switch(AppPrefs.readMessages(context)) { AppPrefs.setReadMessages(context, it) },
            ))
            addView(ui.row(
                R.drawable.ic_battery, "Batería baja", "Avisa al bajar del 20 % y del 10 %",
                switch(AppPrefs.batteryAlerts(context)) { AppPrefs.setBatteryAlerts(context, it) },
            ))
        })

        column.addView(ui.card().apply {
            addView(ui.cardTitle(R.drawable.ic_bluetooth, "Casco"))
            addView(ui.row(
                R.drawable.ic_build, "Diagnóstico", "Modo, batería y comandos de voz. Cierra Cardo Connect antes.",
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
            addView(ui.text("Asistente de voz para tu Cardo", 14f, R.color.text_muted))
        })
    }

    private fun refreshStatus() {
        val runtimeOk = runtimePermissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
        val notificationsOk = notificationAccess()
        val overlayOk = Settings.canDrawOverlays(this)
        val defaultOk = isDefaultVoiceApp()

        chips.removeAllViews()
        val headset = Headset.isConnected(this)
        chips.addView(ui.chip(if (headset) "Casco conectado" else "Casco no conectado", headset))
        val allOk = runtimeOk && notificationsOk && overlayOk && defaultOk
        chips.addView(ui.chip(if (allOk) "Todo listo" else "Faltan permisos", allOk))

        permissionRows.removeAllViews()
        permissionRows.addView(permissionRow(
            "Micrófono, contactos, llamadas, SMS y ubicación", "Lo básico para entenderte y actuar", runtimeOk,
        ) { requestPermissions(runtimePermissions.toTypedArray(), 1) })
        permissionRows.addView(permissionRow(
            "Leer notificaciones", "Para leerte los mensajes y vigilar la batería", notificationsOk,
        ) { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        permissionRows.addView(permissionRow(
            "Mostrarse sobre otras apps", "Para poder responder mensajes con la voz", overlayOk,
        ) { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        permissionRows.addView(permissionRow(
            "App de voz del casco", "Que «Ok Google» y el botón del casco abran HelmVox", defaultOk,
        ) {
            // Abre el selector de Android: elige HelmVox → Siempre.
            startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
    }

    private fun permissionRow(title: String, subtitle: String, ok: Boolean, fix: () -> Unit) =
        ui.row(null, title, if (ok) subtitle else "$subtitle · toca para activarlo", ui.statusIcon(ok),
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
        addView(ui.row(R.drawable.ic_list, "Qué puedes decir", "Toca para ver todas las órdenes", chevron) {
            val open = body.visibility != View.VISIBLE
            body.visibility = if (open) View.VISIBLE else View.GONE
            chevron.rotation = if (open) 180f else 0f
        })
        for ((group, items) in COMMANDS) {
            body.addView(ui.text(group, 14f, R.color.primary, bold = true).apply {
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
            R.drawable.ic_coffee, "Regálame un café",
            "HelmVox es gratis y de código abierto. Si te sirve en la ruta, puedes invitarme un café ☕",
            ui.icon(R.drawable.ic_chevron_right, R.color.text_muted),
        ) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(COFFEE_URL))) })
    }

    // --- Diagnóstico ---

    private fun runDiagnosis() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("Diagnóstico del casco")
            .setMessage("Leyendo el casco…")
            .setPositiveButton("Cerrar", null)
            .show()
        Thread {
            val (result, d) = CardoLink.diagnose(this)
            runOnUiThread { dialog.setMessage(describe(result, d)) }
        }.start()
    }

    private fun describe(result: CardoLink.Result, d: CardoLink.Diagnosis?): String {
        if (result == CardoLink.Result.NoHeadset) return "No encuentro el casco Cardo emparejado."
        if (result is CardoLink.Result.Failed || d == null) {
            return "No pude hablar con el casco. ¿Está encendido y Cardo Connect cerrada del todo?"
        }
        fun yesNo(b: Boolean?) = when (b) {
            true -> "sí"
            false -> "no"
            null -> "desconocido"
        }
        fun sensitivity(v: Int?) = when (v) {
            null -> "desconocida"
            50 -> "alta (50)"
            100 -> "media-alta (100)"
            150 -> "media (150)"
            200, 210 -> "media-baja ($v)"
            250 -> "baja (250)"
            else -> "$v"
        }
        return buildString {
            appendLine("Modo intercom: ${d.dmcMode?.let { if (it) "DMC" else "Bluetooth" } ?: "desconocido"}")
            appendLine("Batería: ${d.batteryPercent?.let { "~$it %" } ?: "desconocida"}")
            appendLine()
            appendLine("Comandos de voz avanzados («Ok Google»):")
            appendLine("  • El casco los admite: ${yesNo(d.advancedVoiceSupported)}")
            appendLine("  • Están activados: ${yesNo(d.advancedVoiceEnabled)}")
            appendLine("  • Sensibilidad del control por voz: ${sensitivity(d.endDetectThreshold)}")
            appendLine("  • Umbral de «Ok Google»: ${sensitivity(d.okGoogleThreshold)}")
            appendLine("  • Código de idioma del casco: ${d.languageCode ?: "desconocido"}")
            if (d.advancedVoiceEnabled == false) {
                appendLine()
                appendLine("→ Actívalos en Cardo Connect: Ajustes → Comandos de voz.")
            }
            appendLine()
            append("Datos: ${d.raw}")
        }
    }

    private companion object {
        /** Enlace de "Regálame un café" (Buy Me a Coffee, Ko-fi…). Vacío = no se muestra la tarjeta. */
        const val COFFEE_URL = "https://buymeacoffee.com/SpawnRipple"

        val COMMANDS = listOf(
            "Ruta" to listOf("Navega a… / Llévame a…", "A casa / Al trabajo", "Bencinera / Gasolinera"),
            "Teléfono y mensajes" to listOf(
                "Llama a… (contacto o número)", "Vuelve a llamar",
                "Manda un mensaje a… diciendo…", "Lee mis mensajes / ¿Tengo mensajes?",
                "Manda mi ubicación a…", "¿Dónde estoy?",
            ),
            "Música" to listOf(
                "Pon la música / Pausa / Apaga la música", "Siguiente / Anterior",
                "Pon música de… / Pon … en Spotify", "Sube / Baja el volumen",
            ),
            "Casco (con Cardo Connect cerrada)" to listOf(
                "Cambia a modo DMC / Bluetooth", "Silencia el grupo", "Silencia mi micro / Activa el micro",
                "Enciende / Apaga la radio", "Siguiente / Anterior emisora · Emisora 3",
                "Comparte la música / la radio", "Abre el intercom (canal A, B o C) / Corta el intercom",
            ),
            "Información" to listOf("¿Qué hora es?", "Batería / del móvil / del casco", "Ayuda / Cancela"),
        )
    }
}
