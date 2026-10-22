package dev.helmvox

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Componentes visuales de la app (tarjetas, botones, filas), todos con el tema oscuro y ámbar. */
class Ui(private val ctx: Context) {

    fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun color(id: Int) = ctx.getColor(id)

    fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color(fill))
        stroke?.let { setStroke(dp(1), color(it)) }
    }

    /** Fondo con efecto de pulsación (onda) sobre [content]. */
    fun pressable(content: Drawable?, radiusDp: Int) =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, rounded(R.color.text, radiusDp))

    fun icon(res: Int, tint: Int, sizeDp: Int = 24) = ImageView(ctx).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color(tint))
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun text(value: CharSequence, sizeSp: Float = 15f, tint: Int = R.color.text, bold: Boolean = false) =
        TextView(ctx).apply {
            text = value
            textSize = sizeSp
            setTextColor(color(tint))
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setLineSpacing(0f, 1.15f)
        }

    /** Tarjeta: superficie redondeada con margen superior. */
    fun card(): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(R.color.surface, 20)
        setPadding(dp(16), dp(16), dp(16), dp(12))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
    }

    /** Título de tarjeta con icono ámbar. */
    fun cardTitle(iconRes: Int, title: String) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 0, 0, dp(8))
        addView(icon(iconRes, R.color.primary, 22))
        addView(text(title, 18f, bold = true).apply { setPadding(dp(10), 0, 0, 0) })
    }

    /** Fila: icono, título y subtítulo, y algo a la derecha (estado, interruptor, flecha). */
    fun row(
        iconRes: Int?,
        title: String,
        subtitle: String? = null,
        trailing: View? = null,
        onClick: (() -> Unit)? = null,
    ) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(56)
        setPadding(dp(4), dp(8), dp(4), dp(8))
        if (onClick != null) {
            background = pressable(null, 12)
            setOnClickListener { onClick() }
        }
        iconRes?.let { addView(icon(it, R.color.text_muted)) }
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (iconRes != null) dp(14) else 0, 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f)
            addView(text(title, 16f))
            subtitle?.let { addView(text(it, 13f, R.color.text_muted)) }
        })
        trailing?.let { addView(it) }
    }

    fun statusIcon(ok: Boolean) =
        icon(if (ok) R.drawable.ic_check_circle else R.drawable.ic_warning, if (ok) R.color.success else R.color.warning)

    /** Botón principal: grande, ámbar, con icono. */
    fun primaryButton(label: String, iconRes: Int, onClick: () -> Unit) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        minimumHeight = dp(60)
        background = pressable(rounded(R.color.primary, 18), 18)
        addView(icon(iconRes, R.color.on_primary, 26))
        addView(text(label, 18f, R.color.on_primary, bold = true).apply { setPadding(dp(10), 0, 0, 0) })
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(12) }
        setOnClickListener { onClick() }
    }

    /** Botón secundario: contorno, para acciones dentro de una tarjeta. */
    fun secondaryButton(label: String, onClick: () -> Unit) = TextView(ctx).apply {
        text = label
        textSize = 15f
        gravity = Gravity.CENTER
        setTextColor(color(R.color.primary))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        minHeight = dp(48)
        background = pressable(rounded(R.color.surface, 14, stroke = R.color.outline), 14)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        setOnClickListener { onClick() }
    }

    /** Etiqueta de estado con punto de color ("Casco conectado"). */
    fun chip(label: String, ok: Boolean) = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(if (ok) R.color.success_dim else R.color.warning_dim, 50)
        setPadding(dp(10), dp(6), dp(12), dp(6))
        addView(View(ctx).apply {
            background = rounded(if (ok) R.color.success else R.color.warning, 50)
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8))
        })
        addView(text(label, 13f, if (ok) R.color.success else R.color.warning).apply { setPadding(dp(6), 0, 0, 0) })
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { rightMargin = dp(8) }
    }

    fun divider() = View(ctx).apply {
        setBackgroundColor(color(R.color.outline))
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
            topMargin = dp(4)
            bottomMargin = dp(4)
        }
    }
}
