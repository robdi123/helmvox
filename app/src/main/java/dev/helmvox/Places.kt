package dev.helmvox

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Ubicación actual y dirección legible. Todo bloqueante: llamar fuera del hilo principal. */
object Places {

    fun hasPermission(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Posición actual (GPS o red, lo que llegue primero); si tarda, la última conocida si es reciente. */
    @SuppressLint("MissingPermission")
    fun current(ctx: Context, timeoutMs: Long = 10_000): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (Build.VERSION.SDK_INT >= 30) {
            val latch = CountDownLatch(1)
            var result: Location? = null
            val cancel = CancellationSignal()
            for (p in providers) {
                lm.getCurrentLocation(p, cancel, ctx.mainExecutor) { loc ->
                    if (loc != null && result == null) {
                        result = loc
                        latch.countDown()
                    }
                }
            }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            cancel.cancel()
            result?.let { return it }
        }
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < MAX_LAST_KNOWN_AGE_MS }
            .maxByOrNull { it.time }
    }

    fun mapsLink(loc: Location) = "https://maps.google.com/?q=%.6f,%.6f".format(Locale.US, loc.latitude, loc.longitude)

    /** "Avenida Providencia 1234, Providencia" o null si no hay geocodificador / internet. */
    @Suppress("DEPRECATION")
    fun describe(ctx: Context, loc: Location): String? {
        if (!Geocoder.isPresent()) return null
        val a = runCatching {
            Geocoder(ctx, Locale.forLanguageTag("es")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
        }.getOrNull() ?: return null
        val street = listOfNotNull(a.thoroughfare, a.subThoroughfare).joinToString(" ").ifBlank { null }
        val area = a.subLocality ?: a.locality ?: a.subAdminArea
        return listOfNotNull(street, area).joinToString(", ").ifBlank { a.getAddressLine(0) }
    }

    private const val MAX_LAST_KNOWN_AGE_MS = 5 * 60 * 1000L
}
