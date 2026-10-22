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
enum class AiModel(val id: String, val labelRes: Int) {
    OPUS("claude-opus-5-5", R.string.ai_model_opus),
    SONNET("claude-sonnet-5-5", R.string.ai_model_sonnet),
    HAIKU("claude-haiku-5-5", R.string.ai_model_haiku),
}

/** Ajustes de la IA (opcional): activada, clave de API de Anthropic y modelo. Solo en este móvil. */
object AiPrefs {
    private const val FILE = "ai"

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("enabled", false)
    fun apiKey(ctx: Context) = prefs(ctx).getString("api_key", "").orEmpty()
    fun model(ctx: Context) =
        prefs(ctx).getString("model", null)?.let { id -> AiModel.entries.firstOrNull { it.id == id } } ?: AiModel.OPUS

    /** Solo para claves con alcance de organización: el workspace al que se carga el uso. */
    fun workspaceId(ctx: Context) = prefs(ctx).getString("workspace_id", "").orEmpty()

    fun save(ctx: Context, enabled: Boolean, apiKey: String, model: AiModel, workspaceId: String) {
        prefs(ctx).edit()
            .putBoolean("enabled", enabled)
            .putString("api_key", apiKey.trim())
            .putString("workspace_id", workspaceId.trim())
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
        data class Failed(val kind: Failure, val status: Int = 0) : Result
    }

    enum class Failure { NO_KEY, BAD_KEY, NEEDS_WORKSPACE, SERVICE, NETWORK }

    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    fun isReady(ctx: Context) = AiPrefs.enabled(ctx) && AiPrefs.apiKey(ctx).isNotBlank() && isOnline(ctx)

    fun parse(ctx: Context, heard: String): Result {
        val key = AiPrefs.apiKey(ctx)
        if (key.isBlank()) return Result.Failed(Failure.NO_KEY)
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
                    // Las claves de organización necesitan saber a qué workspace cargar el uso.
                    AiPrefs.workspaceId(ctx).takeIf { it.isNotBlank() }?.let { putAdditionalHeader("anthropic-workspace-id", it) }
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
            Result.Failed(Failure.BAD_KEY)
        } catch (e: AnthropicServiceException) {
            Log.w(TAG, "error de la API", e)
            if (e.statusCode() == 400 && e.message.orEmpty().contains("anthropic-workspace-id")) {
                Result.Failed(Failure.NEEDS_WORKSPACE)
            } else {
                Result.Failed(Failure.SERVICE, e.statusCode())
            }
        } catch (e: Exception) {
            Log.w(TAG, "fallo llamando a la IA", e)
            Result.Failed(Failure.NETWORK)
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
