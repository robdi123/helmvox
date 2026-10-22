package dev.helmvox

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import java.util.Locale

/**
 * Sección "Voz" de la pantalla principal: acento con que te escucha, voz (de cualquier motor
 * instalado, agrupadas por país), velocidad, tono y botón de prueba.
 */
class VoiceSettingsPanel(ctx: Context) : LinearLayout(ctx) {

    /** Una voz concreta de un motor concreto; voice == null es "por defecto". */
    private data class Entry(val engine: String?, val engineLabel: String, val voice: Voice?)

    private var prefs = VoicePrefs.load(ctx)
    private val engines = mutableMapOf<String, TextToSpeech>() // motor → instancia para probar
    private var entries: List<Entry> = listOf(Entry(null, "", null))
    private var populating = false

    private val ui = Ui(ctx)
    private val listenSpinner = Spinner(ctx).apply { styleSpinner() }
    private val voiceSpinner = Spinner(ctx).apply { styleSpinner() }
    private val voiceCount = ui.text("", 12f, R.color.text_muted).apply { setPadding(ui.dp(4), ui.dp(6), 0, 0) }
    private val rateLabel = ui.text("", 14f, R.color.text_muted).apply { setPadding(ui.dp(12), ui.dp(10), 0, 0) }
    private val pitchLabel = ui.text("", 14f, R.color.text_muted).apply { setPadding(ui.dp(12), ui.dp(10), 0, 0) }
    private val volumeLabel = ui.text("", 14f, R.color.text_muted).apply { setPadding(ui.dp(12), ui.dp(10), 0, 0) }

    init {
        orientation = VERTICAL
        background = ui.rounded(R.color.surface, 20)
        setPadding(ui.dp(16), ui.dp(16), ui.dp(16), ui.dp(16))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = ui.dp(12) }

        addView(ui.cardTitle(R.drawable.ic_volume, "Voz e idioma"))
        addView(label("Acento con que te escucha"))
        addView(field(listenSpinner))
        addView(label("Voz con que te responde"))
        addView(field(voiceSpinner))
        addView(voiceCount)
        addView(spacer())
        addView(rateLabel)
        addView(slider(prefs.rate) { v -> prefs = prefs.copy(rate = v); showLevels() })
        addView(pitchLabel)
        addView(slider(prefs.pitch) { v -> prefs = prefs.copy(pitch = v); showLevels() })
        addView(volumeLabel)
        addView(slider(prefs.volume, min = 0.1f, step = 0.05f, steps = 18) { v ->
            prefs = prefs.copy(volume = v)
            showLevels()
        })
        addView(ui.secondaryButton("▶  Probar voz") { speakSample() })
        showLevels()

        val accents = VoicePrefs.LISTEN_LOCALES.entries.toList()
        listenSpinner.adapter = adapter(accents.map { it.key })
        listenSpinner.setSelection(accents.indexOfFirst { it.value == prefs.listenLocale }.coerceAtLeast(0))
        listenSpinner.onItemSelectedListener = onSelect { pos ->
            prefs = prefs.copy(listenLocale = accents[pos].value)
            prefs.save(context)
        }
        voiceSpinner.onItemSelectedListener = onSelect { pos ->
            val e = entries.getOrNull(pos) ?: return@onSelect
            prefs = prefs.copy(engine = e.engine, voice = e.voice?.name)
            prefs.save(context)
        }
        discoverEngines()
    }

    fun shutdown() {
        engines.values.forEach { it.shutdown() }
        engines.clear()
    }

    /** Arranca el motor por defecto para saber qué motores hay, y luego uno por cada motor. */
    private fun discoverEngines() {
        var probe: TextToSpeech? = null
        probe = TextToSpeech(context, { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            val p = probe!!
            post {
                engines[p.defaultEngine] = p
                p.engines.filter { it.name != p.defaultEngine }.forEach { info -> startEngine(info.name) }
                refreshList()
            }
        })
    }

    private fun startEngine(name: String) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) post {
                engines[name] = tts!!
                refreshList()
            }
        }, name)
    }

    private fun refreshList() {
        val found = engines.flatMap { (name, tts) ->
            val label = tts.engines.firstOrNull { it.name == name }?.label ?: name
            runCatching { tts.voices }.getOrNull().orEmpty()
                .filter { it.locale.language == "es" && !it.features.contains("notInstalled") }
                .map { Entry(name, label, it) }
        }.sortedWith(compareBy({ countryRank(it.voice!!.locale.country) }, { it.engineLabel }, { it.voice!!.name }))
        Log.i(TAG, "voces en español: " + found.joinToString { "${it.voice!!.locale.toLanguageTag()} ${it.voice.name}" })

        populating = true
        entries = listOf(Entry(null, "", null)) + found
        voiceSpinner.adapter = adapter(entries.map(::describe))
        voiceSpinner.setSelection(
            entries.indexOfFirst { it.voice != null && it.voice.name == prefs.voice && it.engine == prefs.engine }
                .coerceAtLeast(0)
        )
        val countries = found.map { countryName(it.voice!!.locale) }.distinct()
        voiceCount.text = if (found.isEmpty()) "No hay voces en español instaladas en este móvil."
        else "${found.size} voces en español: ${countries.joinToString()}"
        post { populating = false }
    }

    private fun describe(e: Entry): String {
        val v = e.voice ?: return "Por defecto del móvil"
        val online = if (v.isNetworkConnectionRequired) " · necesita internet" else ""
        return "${countryName(v.locale)} · ${e.engineLabel} · ${v.name}$online"
    }

    private fun countryName(locale: Locale): String = when (locale.country) {
        "ES" -> "España (castellano)"
        "US" -> "Latinoamérica (EE. UU.)"
        "" -> "Español"
        else -> locale.getDisplayCountry(Locale.forLanguageTag("es")).ifEmpty { locale.country }
    }

    private fun countryRank(country: String) = listOf("CL", "AR", "MX", "CO", "PE", "US", "ES").indexOf(country)
        .let { if (it < 0) 99 else it }

    private fun speakSample() {
        prefs.save(context)
        val tts = prefs.engine?.let { engines[it] } ?: engines.values.firstOrNull() ?: return
        prefs.applyTo(tts)
        val params = android.os.Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, prefs.volume) }
        tts.speak(SAMPLE, TextToSpeech.QUEUE_FLUSH, params, "muestra")
    }

    private fun showLevels() {
        rateLabel.text = "Velocidad: %.2fx".format(prefs.rate)
        pitchLabel.text = "Tono: %.2fx".format(prefs.pitch)
        volumeLabel.text = "Volumen de la voz: %d %%".format((prefs.volume * 100).toInt())
    }

    /** Barra de [min] a min + steps·step (por defecto 0,5x a 2,0x en pasos de 0,05). */
    private fun slider(
        initial: Float,
        min: Float = 0.5f,
        step: Float = 0.05f,
        steps: Int = 30,
        onChange: (Float) -> Unit,
    ) = SeekBar(context).apply {
        setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        max = steps
        progress = Math.round((initial - min) / step).coerceIn(0, steps)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser) onChange(min + p * step)
            }

            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) = prefs.save(context)
        })
    }

    private fun onSelect(action: (Int) -> Unit) = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
            if (!populating) action(pos)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {}
    }

    private fun adapter(items: List<String>) =
        ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, items)

    private fun label(text: String) = ui.text(text, 13f, R.color.text_muted).apply {
        setPadding(ui.dp(4), ui.dp(10), 0, ui.dp(6))
    }

    private fun Spinner.styleSpinner() {
        background = null
        setPadding(ui.dp(4), 0, ui.dp(40), 0)
        setPopupBackgroundDrawable(ui.rounded(R.color.surface_variant, 12))
    }

    /** Desplegable en una caja redondeada con su flecha a la derecha. */
    private fun field(spinner: Spinner) = android.widget.FrameLayout(context).apply {
        background = ui.rounded(R.color.surface_variant, 12)
        minimumHeight = ui.dp(52)
        addView(spinner, android.widget.FrameLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, android.view.Gravity.CENTER_VERTICAL,
        ))
        addView(ui.icon(R.drawable.ic_chevron_down, R.color.text_muted), android.widget.FrameLayout.LayoutParams(
            ui.dp(24), ui.dp(24), android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL,
        ).apply { marginEnd = ui.dp(12) })
    }

    private fun spacer() = android.view.View(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, ui.dp(12))
    }

    private companion object {
        const val TAG = "HelmVox"
        const val SAMPLE = "Hola, soy HelmVox. Navegando a la bencinera más cercana."
    }
}
