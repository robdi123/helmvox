package dev.helmvox

import android.location.Location
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Tiempo actual y lluvia de las próximas horas, de Open-Meteo (gratis, sin clave ni cuenta). */
data class WeatherNow(
    val temperature: Int,
    val code: Int, // código meteorológico WMO
    val windKmh: Int,
    val rainChance: Int?, // % máximo en las próximas horas
)

object Weather {

    private const val TAG = "HelmVox"
    private const val HOURS_AHEAD = 4

    /** Bloqueante: llamar fuera del hilo principal. Null si no hay conexión o el servicio falla. */
    fun fetch(loc: Location): WeatherNow? = try {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.US, loc.latitude, loc.longitude) +
                "&current=temperature_2m,weather_code,wind_speed_10m" +
                "&hourly=precipitation_probability&forecast_hours=$HOURS_AHEAD&timezone=auto"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
        }
        try {
            parse(JSONObject(conn.inputStream.bufferedReader().readText()))
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        Log.w(TAG, "no pude leer el tiempo", e)
        null
    }

    internal fun parse(json: JSONObject): WeatherNow {
        val current = json.getJSONObject("current")
        val rain = json.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
        val maxRain = rain?.let { arr -> (0 until arr.length()).filterNot { arr.isNull(it) }.maxOfOrNull { arr.getInt(it) } }
        return WeatherNow(
            temperature = Math.round(current.getDouble("temperature_2m")).toInt(),
            code = current.getInt("weather_code"),
            windKmh = Math.round(current.getDouble("wind_speed_10m")).toInt(),
            rainChance = maxRain,
        )
    }

    /** Descripción del código WMO, en el idioma de la conversación. */
    fun describe(code: Int, lang: Lang): String {
        val es = lang == Lang.ES
        fun t(a: String, b: String) = if (es) a else b
        return when (code) {
            0 -> t("despejado", "clear")
            1, 2 -> t("parcialmente nublado", "partly cloudy")
            3 -> t("nublado", "overcast")
            45, 48 -> t("con niebla", "foggy")
            in 51..57 -> t("con llovizna", "drizzling")
            in 61..67 -> t("lloviendo", "raining")
            in 71..77 -> t("nevando", "snowing")
            in 80..82 -> t("con chubascos", "showery")
            85, 86 -> t("con chubascos de nieve", "with snow showers")
            in 95..99 -> t("con tormenta", "stormy")
            else -> t("variable", "changeable")
        }
    }

    const val HOURS = HOURS_AHEAD
}
