package dev.helmvox

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.UnauthorizedException
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason
import org.json.JSONObject
import java.time.Duration

/** Modelos de Claude que el usuario puede elegir (con su propia clave de API). */
enum class AiModel(val id: String, val label: String) {
    OPUS("claude-opus-5-5", "Opus 5.5 · el más listo"),
    SONNET("claude-sonnet-5-5", "Sonnet 5.5 · equilibrado"),
    HAIKU("claude-haiku-5-5", "Haiku 5.5 · rápido y barato"),
}

/** Ajustes de la IA (opcional): activada, clave de API de Anthropic y modelo. Solo en este móvil. */
object AiPrefs {
    private const val FILE = "ai"

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)
    fun apiKey(ctx: Context) = prefs(ctx).getString("api_key", "").orEmpty()
    fun model(ctx: Context) =
        prefs(ctx).getString("model", null)?.let { id -> AiModel.entries.firstOrNull { it.id == id } } ?: AiModel.OPUS

    fun save(ctx: Context, enabled: Boolean, apiKey: String, model: AiModel) {
        prefs(ctx).edit()
            .putBoolean("enabled", enabled)
            .putString("api_key", apiKey.trim())
            .putString("model", model.id)
            .apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

/**
 * Plan C del intérprete: si ni las frases exactas ni las palabras clave encajan, y hay internet
 * y clave configurada, se le pregunta a Claude qué orden es. Bloqueante: llamar fuera del hilo
 * principal.
 */
object AiParser {

    private const val TAG = "HelmVox"

    sealed interface Result {
        data class Understood(val command: Command) : Result
        data object NotACommand : Result
        data class Failed(val reason: String) : Result
    }

    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    fun isReady(ctx: Context) = AiPrefs.enabled(ctx) && AiPrefs.apiKey(ctx).isNotBlank() && isOnline(ctx)

    fun parse(ctx: Context, heard: String): Result {
        val key = AiPrefs.apiKey(ctx)
        if (key.isBlank()) return Result.Failed("sin clave de API")
        val model = AiPrefs.model(ctx)
        return try {
            val params = MessageCreateParams.builder()
                .model(model.id)
                // Incluye el razonamiento (siempre activo en estos modelos); la respuesta en sí es corta.
                .maxTokens(2048L)
                .system(AiIntent.SYSTEM_PROMPT)
                .outputConfig(
                    OutputConfig.builder()
                        // Clasificar una frase es fácil: esfuerzo bajo = respuesta rápida en ruta.
                        .effort(OutputConfig.Effort.LOW)
                        .format(
                            JsonOutputFormat.builder()
                                .schema(
                                    JsonOutputFormat.Schema.builder()
                                        .putAllAdditionalProperties(AiIntent.schema().mapValues { JsonValue.from(it.value) })
                                        .build()
                                )
                                .build()
                        )
                        .build()
                )
                .addUserMessage(heard)
                .apply {
                    // Si el modelo rechazara la petición, que el servidor la reintente con otro modelo.
                    if (model != AiModel.HAIKU) {
                        putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
                        putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                    }
                }
                .build()

            val response = client(key).messages().create(params)
            if (response.stopReason().orElse(null) == StopReason.REFUSAL) return Result.NotACommand
            val json = response.content().firstNotNullOfOrNull { block -> block.text().orElse(null)?.text() }
                ?: return Result.NotACommand
            Log.i(TAG, "IA ($model): $json")
            AiIntent.toCommand(JSONObject(json))?.let { Result.Understood(it) } ?: Result.NotACommand
        } catch (e: UnauthorizedException) {
            Result.Failed("la clave de API no es válida")
        } catch (e: AnthropicServiceException) {
            Log.w(TAG, "error de la API", e)
            Result.Failed("error del servicio (${e.statusCode()})")
        } catch (e: Exception) {
            Log.w(TAG, "fallo llamando a la IA", e)
            Result.Failed("sin conexión con la IA")
        }
    }

    @Synchronized
    private fun client(key: String): AnthropicClient {
        client?.takeIf { clientKey == key }?.let { return it }
        return AnthropicOkHttpClient.builder()
            .apiKey(key)
            // En moto no se puede esperar: mejor caer al modo local que quedarse colgado.
            .timeout(Duration.ofSeconds(8))
            .maxRetries(0)
            .build()
            .also {
                client = it
                clientKey = key
            }
    }

    private fun isOnline(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
