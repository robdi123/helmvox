package dev.helmvox

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/** Panel inferior de la sesión de voz: círculo animado según el estado y el texto en grande. */
class VoiceSheet(ctx: Context) : FrameLayout(ctx) {

    enum class State(val label: Int, val icon: Int, val pulseMs: Long?) {
        CONNECTING(R.string.state_connecting, R.drawable.ic_bluetooth, null),
        LISTENING(R.string.state_listening, R.drawable.ic_mic, 900),
        THINKING(R.string.state_thinking, R.drawable.ic_mic, null),
        SPEAKING(R.string.state_speaking, R.drawable.ic_volume, 1400),
        HEADSET(R.string.state_headset, R.drawable.ic_bluetooth, 1400),
        LOCATING(R.string.state_locating, R.drawable.ic_location, 1400),
    }

    private val ui = Ui(ctx)
    private val ring = View(ctx).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ui.color(R.color.primary_dim))
        }
    }
    private val core = FrameLayout(ctx).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ui.color(R.color.primary))
        }
    }
    private val glyph: ImageView = ui.icon(R.drawable.ic_mic, R.color.on_primary, 34)
    private val stateLabel = ui.text("", 13f, R.color.primary, bold = true).apply { gravity = Gravity.CENTER }
    private val mainText = ui.text("", 21f).apply {
        gravity = Gravity.CENTER
        setPadding(0, ui.dp(10), 0, 0)
    }
    private var pulse: ValueAnimator? = null

    init {
        val panel = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(ui.color(R.color.surface))
                val r = ui.dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(ui.dp(24), ui.dp(28), ui.dp(24), ui.dp(40))
        }
        val orb = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(ui.dp(120), ui.dp(120))
            addView(ring, LayoutParams(ui.dp(84), ui.dp(84), Gravity.CENTER))
            addView(core, LayoutParams(ui.dp(84), ui.dp(84), Gravity.CENTER))
        }
        core.addView(glyph, LayoutParams(ui.dp(34), ui.dp(34), Gravity.CENTER))
        panel.addView(orb)
        panel.addView(stateLabel, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.dp(8) })
        panel.addView(mainText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        addView(panel, LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
        setState(State.CONNECTING)
    }

    fun setText(text: CharSequence) {
        mainText.text = text
    }

    fun setState(state: State) {
        stateLabel.text = context.getString(state.label).uppercase()
        glyph.setImageResource(state.icon)
        core.alpha = if (state == State.THINKING) 0.6f else 1f
        pulse?.cancel()
        ring.scaleX = 1f
        ring.scaleY = 1f
        ring.alpha = 1f
        val ms = state.pulseMs ?: return
        // El anillo crece y se desvanece: "late" mientras escucha o habla.
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            ring,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.42f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.42f),
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0f),
        ).apply {
            duration = ms
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onDetachedFromWindow() {
        pulse?.cancel()
        super.onDetachedFromWindow()
    }
}
