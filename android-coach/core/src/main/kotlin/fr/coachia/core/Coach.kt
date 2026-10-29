package fr.coachia.core

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.errors.AnthropicIoException
import com.anthropic.models.beta.messages.BetaBase64ImageSource
import com.anthropic.models.beta.messages.BetaCacheControlEphemeral
import com.anthropic.models.beta.messages.BetaContentBlockParam
import com.anthropic.models.beta.messages.BetaMessage
import com.anthropic.models.beta.messages.BetaMessageParam
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.BetaThinkingConfigAdaptive
import com.anthropic.models.beta.messages.BetaToolResultBlockParam
import com.anthropic.models.beta.messages.MessageCreateParams
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class ChatEntry(
    /** "user", "assistant" or "tool" (a short note about what the coach did). */
    val role: String,
    val text: String,
    val imageCount: Int = 0,
)

class ImageInput(val mediaType: String, val base64: String)

/** Conversation kept on disk: the exact API history (append-only, thinking blocks included) and what the UI shows. */
class ChatTranscript(private val dir: File, private val codec: Codec = Codec.Identity) {
    private val apiFile = File(dir, "chat-api.json")
    private val displayFile = File(dir, "chat-display.json")
    private val mapper = jsonMapper()

    val history: MutableList<BetaMessageParam> = load()
    val display: MutableList<ChatEntry> = loadDisplay()

    private fun load(): MutableList<BetaMessageParam> = runCatching {
        val raw = AppJson.decodeFromString(ListSerializer(kotlinx.serialization.serializer<String>()), codec.decode(apiFile.readBytes()).decodeToString())
        raw.map { mapper.readValue(it, BetaMessageParam::class.java) }.toMutableList()
    }.getOrElse { mutableListOf() }

    private fun loadDisplay(): MutableList<ChatEntry> = runCatching {
        AppJson.decodeFromString(ListSerializer(ChatEntry.serializer()), codec.decode(displayFile.readBytes()).decodeToString()).toMutableList()
    }.getOrElse { mutableListOf() }

    fun save() {
        val raw = history.map { mapper.writeValueAsString(it) }
        apiFile.writeBytes(codec.encode(AppJson.encodeToString(ListSerializer(kotlinx.serialization.serializer<String>()), raw).encodeToByteArray()))
        displayFile.writeBytes(codec.encode(AppJson.encodeToString(ListSerializer(ChatEntry.serializer()), display).encodeToByteArray()))
    }

    fun clear() {
        history.clear()
        display.clear()
        save()
    }
}

class CoachException(message: String) : Exception(message)

class Coach(
    private val repo: AppRepository,
    private val transcript: ChatTranscript,
    private val tools: CoachTools,
    private val clientFactory: (String) -> AnthropicClient = { key ->
        AnthropicOkHttpClient.builder().apiKey(key).timeout(Duration.ofMinutes(10)).build()
    },
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private var client: AnthropicClient? = null
    private var clientKey: String? = null

    private fun client(): AnthropicClient {
        val key = repo.data.settings.anthropicApiKey.trim()
        if (key.isEmpty()) throw CoachException("Ajoute ta clé API Anthropic dans les Réglages pour parler au coach.")
        if (client == null || clientKey != key) {
            client = clientFactory(key)
            clientKey = key
        }
        return client!!
    }

    private fun dateContext(): String {
        val d = today()
        val fmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH)
        return "[Contexte automatique : nous sommes le ${d.format(fmt)} (${d})]"
    }

    /**
     * Sends one user turn and runs the tool loop until the coach answers.
     * Blocking: call from a background thread. [onUpdate] is called whenever [ChatTranscript.display] changes.
     */
    fun send(text: String, images: List<ImageInput> = emptyList(), onUpdate: () -> Unit = {}) {
        val api = client()
        val blocks = ArrayList<BetaContentBlockParam>()
        for (img in images) {
            blocks += BetaContentBlockParam.ofImage(
                BetaBase64ImageSource.builder()
                    .mediaType(BetaBase64ImageSource.MediaType.of(img.mediaType))
                    .data(img.base64)
                    .build(),
            )
        }
        blocks += BetaContentBlockParam.ofText(dateContext() + "\n\n" + text)
        transcript.history += BetaMessageParam.ofUserOfContentBlockParams(blocks)
        transcript.display += ChatEntry("user", text, images.size)
        transcript.save()
        onUpdate()

        repeat(MAX_TURNS) {
            val response = try {
                api.beta().messages().create(buildParams())
            } catch (e: AnthropicServiceException) {
                rollbackDangling()
                throw CoachException("Erreur de l'API Claude (${e.statusCode()}) : ${e.message}")
            } catch (e: AnthropicIoException) {
                rollbackDangling()
                throw CoachException("Connexion impossible à l'API Claude : ${e.message}")
            }
            transcript.history += response.toParam()

            val answer = response.content().mapNotNull { b -> b.text().map { it.text() }.orElse(null) }
                .joinToString("\n").trim()
            val stop = response.stopReason().orElse(null)

            if (stop == BetaStopReason.REFUSAL) {
                transcript.display += ChatEntry("assistant", answer.ifEmpty { "Le modèle a refusé de répondre à cette demande. Reformule ta question." })
                transcript.save()
                onUpdate()
                return
            }
            if (stop == BetaStopReason.TOOL_USE) {
                if (answer.isNotEmpty()) transcript.display += ChatEntry("assistant", answer)
                transcript.history += BetaMessageParam.ofUserOfContentBlockParams(runTools(response))
                transcript.save()
                onUpdate()
                return@repeat
            }
            if (stop == BetaStopReason.PAUSE_TURN) {
                transcript.save()
                return@repeat
            }
            val suffix = if (stop == BetaStopReason.MAX_TOKENS) "\n\n(réponse tronquée, demande la suite)" else ""
            transcript.display += ChatEntry("assistant", (answer + suffix).ifBlank { "…" })
            transcript.save()
            onUpdate()
            return
        }
        transcript.display += ChatEntry("assistant", "J'ai atteint la limite d'actions pour ce message. Dis-moi « continue » pour poursuivre.")
        transcript.save()
        onUpdate()
    }

    /** A failed request must not leave a user turn without answer that would break the next request. */
    private fun rollbackDangling() {
        val last = transcript.history.lastOrNull() ?: return
        if (last.isUser()) {
            // Keep the history valid: answer the dangling turn with a short assistant message.
            transcript.history += BetaMessageParam.ofAssistant("(La requête précédente a échoué.)")
        }
        transcript.save()
    }

    private fun runTools(response: BetaMessage): List<BetaContentBlockParam> {
        val results = ArrayList<BetaContentBlockParam>()
        for (block in response.content()) {
            val use = block.toolUse().orElse(null) ?: continue
            val input: JsonObject = runCatching {
                AppJson.parseToJsonElement(jsonMapper().writeValueAsString(use._input())).jsonObject
            }.getOrElse { JsonObject(emptyMap()) }
            val outcome = tools.execute(use.name(), input)
            outcome.note?.let { transcript.display += ChatEntry("tool", it) }
            results += BetaContentBlockParam.ofToolResult(
                BetaToolResultBlockParam.builder()
                    .toolUseId(use.id())
                    .content(outcome.content)
                    .isError(outcome.isError)
                    .build(),
            )
        }
        return results
    }

    private fun buildParams(): MessageCreateParams {
        val b = MessageCreateParams.builder()
            .model(repo.data.settings.model.ifBlank { Settings.DEFAULT_MODEL })
            .maxTokens(16000L)
            .system(CoachPrompt.SYSTEM)
            .thinking(BetaThinkingConfigAdaptive.builder().build())
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.HIGH).build())
            .cacheControl(BetaCacheControlEphemeral.builder().build())
            .messages(transcript.history.toList())
            .addBeta(FALLBACK_BETA)
            .fallbacksDefault()
        CoachTools.definitions.forEach { b.addTool(it) }
        return b.build()
    }

    companion object {
        const val MAX_TURNS = 20
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
    }
}
