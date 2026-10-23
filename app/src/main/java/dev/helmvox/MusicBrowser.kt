package dev.helmvox

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.session.MediaControllerCompat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.media.MediaBrowserService
import android.util.Log

/**
 * "Pon música de X" por la vía de Android Auto: se conecta al catálogo (MediaBrowserService) de la
 * app de música, busca [query] y reproduce el primer resultado por su id. Algunas apps solo aceptan
 * así la reproducción de una búsqueda concreta.
 */
object MusicBrowser {

    private const val TAG = "HelmVox"
    private const val TIMEOUT_MS = 8000L
    private val main = Handler(Looper.getMainLooper())

    /** Llama a [done] con true si consiguió mandar a reproducir un resultado. Hilo principal. */
    fun playSearch(ctx: Context, pkg: String, query: String, done: (Boolean) -> Unit) {
        val service = ctx.packageManager
            .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), 0)
            .firstOrNull()?.serviceInfo
            ?: return done(false)
        var finished = false
        lateinit var browser: MediaBrowserCompat
        fun finish(ok: Boolean, why: String) {
            if (finished) return
            finished = true
            Log.i(TAG, "catálogo de $pkg: $why")
            runCatching { browser.disconnect() }
            done(ok)
        }
        browser = MediaBrowserCompat(ctx, ComponentName(service.packageName, service.name), object : MediaBrowserCompat.ConnectionCallback() {
            override fun onConnected() {
                browser.search(query, Bundle(), object : MediaBrowserCompat.SearchCallback() {
                    override fun onSearchResult(q: String, extras: Bundle?, items: List<MediaBrowserCompat.MediaItem>) {
                        val item = items.firstOrNull { it.isPlayable } ?: return finish(false, "sin resultados reproducibles")
                        MediaControllerCompat(ctx, browser.sessionToken).transportControls.playFromMediaId(item.mediaId, Bundle())
                        finish(true, "reproduciendo «${item.description.title}»")
                    }

                    override fun onError(q: String, extras: Bundle?) = finish(false, "la búsqueda falló")
                })
            }

            override fun onConnectionFailed() = finish(false, "rechazó la conexión")
            override fun onConnectionSuspended() = finish(false, "conexión suspendida")
        }, null)
        main.postDelayed({ finish(false, "sin respuesta") }, TIMEOUT_MS)
        browser.connect()
    }
}
