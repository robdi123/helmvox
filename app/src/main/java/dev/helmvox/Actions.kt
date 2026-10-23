package dev.helmvox

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Build
import android.provider.ContactsContract
import android.provider.MediaStore
import android.telephony.SmsManager
import android.view.KeyEvent
import java.text.Normalizer

data class Contact(val name: String, val number: String)

/** Lo que la app hace en el móvil una vez entendido el comando. */
object Actions {

    /** Modo de Google Maps: "d" coche, "l" moto (dos ruedas; solo en algunos países). */
    private const val NAV_MODE = "d"

    fun navigate(ctx: Context, destination: String) {
        val nav = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$NAV_MODE"),
        ).setPackage("com.google.android.apps.maps").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(nav)
        } catch (_: ActivityNotFoundException) {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(destination)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun call(ctx: Context, number: String) {
        val action = if (granted(ctx, Manifest.permission.CALL_PHONE)) Intent.ACTION_CALL else Intent.ACTION_DIAL
        ctx.startActivity(Intent(action, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Envía el SMS directamente si hay permiso; si no, abre la app de mensajes con el texto. */
    fun sms(ctx: Context, number: String, text: String): Boolean {
        if (!granted(ctx, Manifest.permission.SEND_SMS)) {
            ctx.startActivity(
                Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null))
                    .putExtra("sms_body", text)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return false
        }
        val sm = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java)
        else @Suppress("DEPRECATION") SmsManager.getDefault()
        sm.sendMultipartTextMessage(number, null, sm.divideMessage(text), null, null)
        return true
    }

    fun media(ctx: Context, action: MediaAction) {
        val code = when (action) {
            MediaAction.PLAY -> KeyEvent.KEYCODE_MEDIA_PLAY
            MediaAction.PAUSE -> KeyEvent.KEYCODE_MEDIA_PAUSE
            MediaAction.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            MediaAction.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
        }
        val am = ctx.getSystemService(AudioManager::class.java)
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Apps de música por orden de preferencia, para no mostrar el selector de apps en ruta. */
    private val MUSIC_APPS = listOf(
        "com.spotify.music",
        "com.google.android.apps.youtube.music",
        "deezer.android.app",
        "com.amazon.mp3",
        "com.apple.android.music",
    )

    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * "Pon algo" sin nada que reanudar: abre la app de música, le pide reproducir y, si sigue en
     * silencio, busca [fallbackQuery]. Usa el contexto de la aplicación: sigue aunque se cierre la
     * pantalla de voz.
     */
    fun playSomething(ctx: Context, fallbackQuery: String) {
        val app = ctx.applicationContext
        val am = app.getSystemService(AudioManager::class.java)
        // Al frente: sin suscripción, algunas apps (YouTube Music) no reproducen en segundo plano.
        openMusicApp(app, "")
        main.postDelayed({
            if (am.isMusicActive) return@postDelayed
            // Ya abierta, su sesión multimedia acepta "play" (retoma la cola o sus recomendaciones).
            musicController(app)?.transportControls?.play() ?: media(app, MediaAction.PLAY)
            main.postDelayed({ if (!am.isMusicActive) playFromSearch(app, fallbackQuery) }, 2500)
        }, 3000)
    }

    /**
     * Reproduce [query] en la app de música. Las apps (YouTube Music, Spotify…) solo *buscan* con el
     * Intent estándar, pero *reproducen* si se lo pide su sesión multimedia, como hacen Android Auto
     * y el asistente. Eso necesita el permiso de leer notificaciones; sin él, solo se abre la búsqueda.
     *
     * La app se trae siempre al frente antes: sin suscripción, YouTube Music no reproduce en segundo
     * plano y contesta "No se puede reproducir la pista".
     */
    fun playFromSearch(ctx: Context, query: String): Boolean {
        val app = ctx.applicationContext
        val pkg = musicPackages(app).firstOrNull()
        if (pkg == YOUTUBE_MUSIC && query.isNotBlank()) return openYouTubeMusicResults(app, query)
        if (query.isBlank() || pkg == null) return openAndAskSession(app, query)
        // 1º, como Android Auto: buscar en el catálogo de la app y reproducir un resultado concreto.
        MusicBrowser.playSearch(app, pkg, query) { ok -> if (!ok) openAndAskSession(app, query) }
        return true
    }

    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    /**
     * YouTube Music solo deja reproducir una búsqueda a apps de Google (asistente, Android Auto):
     * con el Intent estándar no busca, su sesión responde "No se puede reproducir la pista" y su
     * catálogo rechaza la conexión. Lo único fiable es abrir los resultados para tocar uno.
     */
    fun searchNeedsTap(ctx: Context) = musicPackages(ctx.applicationContext).firstOrNull() == YOUTUBE_MUSIC

    private fun openYouTubeMusicResults(ctx: Context, query: String): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query)))
                .setPackage(YOUTUBE_MUSIC)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    /** Plan B: abrir la app (búsqueda) y pedir "reproduce esta búsqueda" a su sesión multimedia. */
    private fun openAndAskSession(app: Context, query: String): Boolean {
        val alreadyOpen = musicController(app) != null
        if (!openMusicApp(app, query)) return false
        // Hay que esperar a que la app esté al frente (y, si estaba cerrada, a que cree su sesión).
        main.postDelayed({
            musicController(app)?.transportControls?.playFromSearch(query, Bundle())
        }, if (alreadyOpen) 1500 else 3500)
        return true
    }

    private fun openMusicApp(ctx: Context, query: String): Boolean {
        val intent = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val handlers = ctx.packageManager.queryIntentActivities(intent, 0).map { it.activityInfo.packageName }.toSet()
        val pkg = musicPackages(ctx).firstOrNull { it in handlers } ?: handlers.firstOrNull() ?: return false
        return try {
            ctx.startActivity(intent.setPackage(pkg))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    /** Sesión de una app de música que acepte "reproducir búsqueda" (la que suena, o la preferida). */
    private fun musicController(ctx: Context): MediaController? {
        val sessions = try {
            ctx.getSystemService(MediaSessionManager::class.java)
                .getActiveSessions(ComponentName(ctx, CascoListenerService::class.java))
        } catch (_: SecurityException) {
            return null // sin permiso de leer notificaciones
        }
        val usable = sessions.filter { (it.playbackState?.actions ?: 0L) and PlaybackState.ACTION_PLAY_FROM_SEARCH != 0L }
        val order = musicPackages(ctx)
        return usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: usable.filter { it.packageName in order }.minByOrNull { order.indexOf(it.packageName) }
    }

    /** Apps de música instaladas, en orden de preferencia. */
    private fun musicPackages(ctx: Context) = MUSIC_APPS.filter { pkg ->
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0) }.isSuccess
    }

    fun volume(ctx: Context, up: Boolean) {
        val am = ctx.getSystemService(AudioManager::class.java)
        repeat(2) {
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0,
            )
        }
    }

    fun batteryPercent(ctx: Context): Int =
        ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /**
     * Busca un contacto por nombre hablado. Un número dictado ("600 123 456") se usa tal cual.
     * Prioridad: nombre exacto > empieza por > alguna palabra coincide > contiene.
     */
    fun findContact(ctx: Context, spoken: String): Contact? {
        val digits = spoken.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 3) return Contact(digits, digits)
        if (!granted(ctx, Manifest.permission.READ_CONTACTS)) return null

        val wanted = fold(spoken)
        if (wanted.isEmpty()) return null
        var best: Contact? = null
        var bestScore = 0
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                val n = fold(name)
                val score = when {
                    n == wanted -> 4
                    n.startsWith(wanted) -> 3
                    n.split(' ').any { it == wanted } -> 2
                    n.contains(wanted) -> 1
                    else -> 0
                }
                if (score > bestScore) {
                    best = Contact(name, number)
                    bestScore = score
                }
            }
        }
        return best
    }

    private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex(" +"), " ")
        .trim()

    private fun granted(ctx: Context, perm: String) =
        ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED
}
