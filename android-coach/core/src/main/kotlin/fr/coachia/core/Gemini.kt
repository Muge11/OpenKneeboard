package fr.coachia.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class ChatEntry(
    /** "user", "assistant" or "tool" (a short note about what the coach did). */
    val role: String,
    val text: String,
)

class CoachException(message: String) : Exception(message)

/** Conversation kept on disk: the raw Gemini `contents` (model turns kept verbatim, thought signatures included) and what the UI shows. */
class ChatTranscript(dir: File, private val codec: Codec = Codec.Identity) {
    private val apiFile = File(dir, "chat-gemini.json")
    private val displayFile = File(dir, "chat-display-v2.json")

    val history: MutableList<JsonObject> = runCatching {
        AppJson.decodeFromString(ListSerializer(JsonObject.serializer()), codec.decode(apiFile.readBytes()).decodeToString()).toMutableList()
    }.getOrElse { mutableListOf() }

    val display: MutableList<ChatEntry> = runCatching {
        AppJson.decodeFromString(ListSerializer(ChatEntry.serializer()), codec.decode(displayFile.readBytes()).decodeToString()).toMutableList()
    }.getOrElse { mutableListOf() }

    fun save() {
        apiFile.writeBytes(codec.encode(AppJson.encodeToString(ListSerializer(JsonObject.serializer()), history).encodeToByteArray()))
        displayFile.writeBytes(codec.encode(AppJson.encodeToString(ListSerializer(ChatEntry.serializer()), display).encodeToByteArray()))
    }

    fun clear() {
        history.clear()
        display.clear()
        save()
    }
}

/** Minimal Gemini API client (REST, function calling). */
class GeminiClient(private val http: OkHttpClient, private val baseUrl: String = "https://generativelanguage.googleapis.com") {
    private val jsonType = "application/json".toMediaType()

    class HttpError(val code: Int, val body: String) : IOException("HTTP $code")

    private fun call(request: Request): JsonObject {
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw HttpError(resp.code, body)
            return AppJson.parseToJsonElement(body).jsonObject
        }
    }

    fun generate(apiKey: String, model: String, body: JsonObject): JsonObject {
        val name = model.removePrefix("models/")
        val req = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/v1beta/models/$name:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(body.toString().toRequestBody(jsonType))
            .build()
        return call(req)
    }

    /** Models usable for chat with this key (ids without the "models/" prefix). */
    fun listModels(apiKey: String): List<String> {
        val url = "${baseUrl.trimEnd('/')}/v1beta/models".toHttpUrl().newBuilder().addQueryParameter("pageSize", "200").build()
        val res = call(Request.Builder().url(url).header("x-goog-api-key", apiKey).build())
        return res["models"]?.jsonArray.orEmpty().mapNotNull { m ->
            val o = m.jsonObject
            val methods = o["supportedGenerationMethods"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val id = o["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") ?: return@mapNotNull null
            id.takeIf {
                "generateContent" in methods && it.startsWith("gemini") &&
                    listOf("embedding", "tts", "image", "audio", "live", "aqa").none { k -> it.contains(k) }
            }
        }.sorted()
    }
}

/** Discussion avec le coach via Gemini (offre gratuite Google AI Studio), avec les mêmes outils que l'appli. */
class GeminiCoach(
    private val repo: AppRepository,
    private val transcript: ChatTranscript,
    private val tools: CoachTools,
    private val client: GeminiClient,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    private fun dateContext(): String {
        val d = today()
        return "[Contexte automatique : nous sommes le ${d.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRENCH))} ($d)]"
    }

    private fun textPart(text: String) = buildJsonObject { put("text", text) }

    private fun userTurn(parts: List<JsonObject>) = buildJsonObject {
        put("role", "user")
        put("parts", JsonArray(parts))
    }

    private val toolsJson: JsonArray = buildJsonArray {
        add(
            buildJsonObject {
                put(
                    "functionDeclarations",
                    JsonArray(
                        CoachTools.definitions.map { t ->
                            buildJsonObject {
                                put("name", t.name)
                                put("description", t.description)
                                t.parameters?.let { put("parameters", it) }
                            }
                        },
                    ),
                )
            },
        )
    }

    private fun requestBody() = buildJsonObject {
        putJsonObject("systemInstruction") { putJsonArray("parts") { add(textPart(CoachPrompt.SYSTEM)) } }
        put("contents", JsonArray(transcript.history))
        put("tools", toolsJson)
    }

    /** Blocking: call from a background thread. [onUpdate] is called whenever [ChatTranscript.display] changes. */
    fun send(text: String, onUpdate: () -> Unit = {}) {
        val settings = repo.data.settings
        val key = settings.geminiApiKey.trim()
        if (key.isEmpty()) {
            throw CoachException("Ajoute une clé Gemini gratuite dans les Réglages pour discuter avec le coach. Le planificateur et le bilan fonctionnent sans.")
        }
        val model = settings.geminiModel.ifBlank { Settings.DEFAULT_MODEL }
        transcript.history += userTurn(listOf(textPart(dateContext() + "\n\n" + text)))
        transcript.display += ChatEntry("user", text)
        transcript.save()
        onUpdate()

        repeat(MAX_TURNS) {
            val res = try {
                client.generate(key, model, requestBody())
            } catch (e: GeminiClient.HttpError) {
                rollback()
                throw CoachException(explain(e))
            } catch (e: IOException) {
                rollback()
                throw CoachException("Connexion impossible à Gemini : ${e.message}")
            }
            val candidate = res["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            val content = candidate?.get("content") as? JsonObject
            val parts = (content?.get("parts") as? JsonArray)?.map { it.jsonObject }.orEmpty()
            if (content == null || parts.isEmpty()) {
                val reason = candidate?.get("finishReason")?.jsonPrimitive?.contentOrNull
                    ?: (res["promptFeedback"] as? JsonObject)?.get("blockReason")?.jsonPrimitive?.contentOrNull ?: "réponse vide"
                rollback()
                transcript.display += ChatEntry("assistant", "Gemini n'a pas répondu ($reason). Reformule ta demande.")
                transcript.save()
                onUpdate()
                return
            }
            transcript.history += buildJsonObject {
                put("role", "model")
                put("parts", JsonArray(parts))
            }
            val answer = parts.filter { it["thought"]?.jsonPrimitive?.contentOrNull != "true" }
                .mapNotNull { it["text"]?.jsonPrimitive?.contentOrNull }.joinToString("").trim()
            val calls = parts.mapNotNull { it["functionCall"] as? JsonObject }
            if (calls.isEmpty()) {
                val truncated = candidate["finishReason"]?.jsonPrimitive?.contentOrNull == "MAX_TOKENS"
                transcript.display += ChatEntry("assistant", (answer + if (truncated) "\n\n(réponse tronquée, demande la suite)" else "").ifBlank { "…" })
                transcript.save()
                onUpdate()
                return
            }
            if (answer.isNotEmpty()) transcript.display += ChatEntry("assistant", answer)
            val responses = calls.map { call ->
                val name = call["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val args = call["args"] as? JsonObject ?: JsonObject(emptyMap())
                val outcome = tools.execute(name, args)
                outcome.note?.let { transcript.display += ChatEntry("tool", it) }
                buildJsonObject {
                    putJsonObject("functionResponse") {
                        call["id"]?.let { put("id", it) }
                        put("name", name)
                        putJsonObject("response") { put(if (outcome.isError) "error" else "result", outcome.content) }
                    }
                }
            }
            transcript.history += userTurn(responses)
            transcript.save()
            onUpdate()
        }
        transcript.display += ChatEntry("assistant", "J'ai atteint la limite d'actions pour ce message. Dis-moi « continue » pour poursuivre.")
        transcript.save()
        onUpdate()
    }

    /** Keeps the history alternating user/model after a failed request. */
    private fun rollback() {
        val last = transcript.history.lastOrNull() ?: return
        if (last["role"]?.jsonPrimitive?.contentOrNull == "user") {
            transcript.history += buildJsonObject {
                put("role", "model")
                putJsonArray("parts") { add(textPart("(La requête précédente a échoué.)")) }
            }
        }
        transcript.save()
    }

    private fun explain(e: GeminiClient.HttpError): String {
        val msg = runCatching {
            AppJson.parseToJsonElement(e.body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull().orEmpty().take(300)
        return when (e.code) {
            429 -> "Quota gratuit Gemini atteint pour le moment. Réessaie dans une minute (ou demain si c'est le quota du jour). $msg"
            400, 401, 403 -> "Gemini refuse la requête (clé invalide ou modèle non autorisé) : $msg"
            404 -> "Modèle « ${repo.data.settings.geminiModel} » introuvable : choisis-en un autre dans les Réglages (« Charger les modèles »)."
            in 500..599 -> "Gemini est momentanément indisponible (${e.code}). Réessaie un peu plus tard."
            else -> "Erreur Gemini ${e.code} : $msg"
        }
    }

    companion object {
        const val MAX_TURNS = 15
    }
}
