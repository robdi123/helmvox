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
    private val workspaceField = EditText(activity)
    private val modelSpinner = Spinner(activity)
    private val status = ui.text("", 13f, R.color.text_muted).apply { setPadding(ui.dp(4), ui.dp(8), 0, 0) }

    init {
        orientation = VERTICAL
        background = ui.rounded(R.color.surface, 20)
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(12) }

        addView(ui.cardTitle(R.drawable.ic_spark, context.getString(R.string.card_ai)))
        addView(ui.text(
            context.getString(R.string.ai_intro),
            14f, R.color.text_muted,
        ).apply { setPadding(ui.dp(4), 0, 0, ui.dp(4)) })

        enabledSwitch.isChecked = AiPrefs.enabled(context)
        enabledSwitch.setOnCheckedChangeListener { _, _ -> save() }
        addView(ui.row(R.drawable.ic_spark, context.getString(R.string.ai_enable), null, enabledSwitch))

        addView(label(context.getString(R.string.ai_key_label)))
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

        addView(label(context.getString(R.string.ai_workspace_label)))
        workspaceField.apply {
            setText(AiPrefs.workspaceId(context))
            hint = "wrkspc_…"
            setSingleLine()
            textSize = 15f
            setTextColor(ui.color(R.color.text))
            setHintTextColor(ui.color(R.color.text_muted))
            background = ui.rounded(R.color.surface_variant, 12)
            setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(14))
            setOnFocusChangeListener { _, focused -> if (!focused) save() }
        }
        addView(workspaceField)
        addView(ui.text(context.getString(R.string.ai_workspace_hint), 12f, R.color.text_muted).apply {
            setPadding(ui.dp(4), ui.dp(6), 0, 0)
        })

        addView(label(context.getString(R.string.ai_model_label)))
        modelSpinner.adapter = ArrayAdapter(
            context, android.R.layout.simple_spinner_dropdown_item, AiModel.entries.map { context.getString(it.labelRes) },
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

        addView(ui.secondaryButton(context.getString(R.string.ai_test)) { test() })
        addView(ui.secondaryButton(context.getString(R.string.ai_get_key)) {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.anthropic.com/")))
        })
        addView(status)
    }

    private fun save() {
        val model = AiModel.entries.getOrElse(modelSpinner.selectedItemPosition) { AiModel.OPUS }
        AiPrefs.save(context, enabledSwitch.isChecked, keyField.text.toString(), model, workspaceField.text.toString())
    }

    private fun test() {
        save()
        if (AiPrefs.apiKey(context).isBlank()) {
            status.text = context.getString(R.string.ai_paste_key)
            return
        }
        status.text = context.getString(R.string.ai_asking)
        Thread {
            val started = System.currentTimeMillis()
            val sample = if (VoicePrefs.load(context).lang == Lang.EN) "put something on to listen to" else "ponme algo para escuchar"
            val result = AiParser.parse(context, sample)
            val ms = System.currentTimeMillis() - started
            activity.runOnUiThread {
                status.text = when (result) {
                    is AiParser.Result.Understood -> context.getString(R.string.ai_ok, ms.toInt(), result.command.toString())
                    AiParser.Result.NotACommand -> context.getString(R.string.ai_not_command, ms.toInt())
                    is AiParser.Result.Failed -> context.getString(R.string.ai_failed, failure(result))
                }
            }
        }.start()
    }

    private fun failure(f: AiParser.Result.Failed) = when (f.kind) {
        AiParser.Failure.NO_KEY -> context.getString(R.string.ai_err_no_key)
        AiParser.Failure.BAD_KEY -> context.getString(R.string.ai_err_bad_key)
        AiParser.Failure.SERVICE -> context.getString(R.string.ai_err_service, f.status)
        AiParser.Failure.NETWORK -> context.getString(R.string.ai_err_network)
        AiParser.Failure.NEEDS_WORKSPACE -> context.getString(R.string.ai_err_workspace)
    }

    private fun label(text: String) = ui.text(text, 13f, R.color.text_muted).apply {
        setPadding(ui.dp(4), ui.dp(12), 0, ui.dp(6))
    }
}
