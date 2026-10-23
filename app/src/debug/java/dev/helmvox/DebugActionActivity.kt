package dev.helmvox

import android.app.Activity
import android.os.Bundle
import android.util.Log

/**
 * Solo depuración. Ejecuta una frase como si se hubiera dicho, sin reconocimiento de voz:
 * adb shell am start -n dev.helmvox/.DebugActionActivity --es play "Bruno Mars"
 */
class DebugActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra("play")?.let {
            Log.i("HelmVox", "depuración: reproducir «$it»")
            Actions.playFromSearch(this, it)
        }
        finish()
    }
}
