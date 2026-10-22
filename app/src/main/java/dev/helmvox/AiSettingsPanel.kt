package dev.helmvox

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Switch

/**
 * Sección "Inteligencia artificial": opcional. Cada usuario pone su propia clave de API de
 * Anthropic; se guarda solo en este móvil y se usa únicamente cuando la app no entiende una frase.
 */
class AiSettingsPanel(private val activity: Activity) : LinearLayout(activity) {

    private val ui = Ui(activity)
    private val enabledSwitch = Switch(activity)
    private val keyField = EditText(activity)
    private val modelSpinner = Spinner(activity)
    private val status = ui.text("", 13f, R.color.text_muted).apply { setPadding(ui.dp(4), ui.dp(8), 0, 0) }

    init {
        orientation = VERTICAL
        background = ui.rounded(R.color.surface, 20)
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(12) }

        addView(ui.cardTitle(R.drawable.ic_spark, "Inteligencia artificial"))
        addView(ui.text(
            "Opcional. Si la app no entiende una frase y hay internet, se la pregunta a Claude con " +
                "tu propia clave de API. Sin clave o sin cobertura, todo sigue funcionando en local.",
            14f, R.color.text_muted,
        ).apply { setPadding(ui.dp(4), 0, 0, ui.dp(4)) })

        enabledSwitch.isChecked = AiPrefs.enabled(context)
        enabledSwitch.setOnCheckedChangeListener { _, _ -> save() }
        addView(ui.row(R.drawable.ic_spark, "Usar IA cuando no entienda", null, enabledSwitch))

        addView(label("Clave de API de Anthropic"))
        keyField.apply {
            setText(AiPrefs.apiKey(context))
            hint = "sk-ant-…"
            // setSingleLine() quita el modo contraseña, así que va antes de fijarlo.
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 15f
            setTextColor(ui.color(R.color.text))
            setHintTextColor(ui.color(R.color.text_muted))
            background = ui.rounded(R.color.surface_variant, 12)
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
            setOnFocusChangeListener { _, focused -> if (!focused) save() }
        }
        addView(keyField)

        addView(label("Modelo"))
        modelSpinner.adapter = ArrayAdapter(
            context, android.R.layout.simple_spinner_dropdown_item, AiModel.entries.map { it.label },
        )
        modelSpinner.setSelection(AiModel.entries.indexOf(AiPrefs.model(context)))
        modelSpinner.background = null
        modelSpinner.setPopupBackgroundDrawable(ui.rounded(R.color.surface_variant, 12))
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) = save()
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        addView(FrameLayout(context).apply {
            background = ui.rounded(R.color.surface_variant, 12)
            minimumHeight = ui.dp(52)
            addView(modelSpinner, FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER_VERTICAL,
            ))
        })

        addView(ui.secondaryButton("Probar con «ponme algo para escuchar»") { test() })
        addView(ui.secondaryButton("Conseguir una clave en console.anthropic.com") {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/")))
        })
        addView(status)
    }

    private fun save() {
        val model = AiModel.entries.getOrElse(modelSpinner.selectedItemPosition) { AiModel.OPUS }
        AiPrefs.save(context, enabledSwitch.isChecked, keyField.text.toString(), model)
    }

    private fun test() {
        save()
        if (AiPrefs.apiKey(context).isBlank()) {
            status.text = "Pega primero tu clave de API."
            return
        }
        status.text = "Preguntando a Claude…"
        Thread {
            val started = System.currentTimeMillis()
            val result = AiParser.parse(context, "ponme algo para escuchar")
            val ms = System.currentTimeMillis() - started
            activity.runOnUiThread {
                status.text = when (result) {
                    is AiParser.Result.Understood -> "✔ Funciona (${ms} ms): entendió ${result.command}."
                    AiParser.Result.NotACommand -> "Respondió, pero no reconoció la orden (${ms} ms)."
                    is AiParser.Result.Failed -> "✖ No funcionó: ${result.reason}."
                }
            }
        }.start()
    }

    private fun label(text: String) = ui.text(text, 13f, R.color.text_muted).apply {
        setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(6))
    }
}
