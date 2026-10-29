package fr.coachia.app

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.CalendarContract
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.coachia.core.AppData
import fr.coachia.core.AthleteProfile
import fr.coachia.core.CalendarEvent
import fr.coachia.core.ChatEntry
import fr.coachia.core.Constraint
import fr.coachia.core.ImageInput
import fr.coachia.core.SessionStatus
import fr.coachia.core.Settings
import fr.coachia.core.StravaClient
import fr.coachia.core.Zwift
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class Tab { CALENDAR, COACH, FITNESS, ACTIVITIES, SETTINGS }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CoachApp

    val data: StateFlow<AppData> = app.repo.state

    private val _chat = MutableStateFlow(app.transcript.display.toList())
    val chat: StateFlow<List<ChatEntry>> = _chat.asStateFlow()

    private val _coachBusy = MutableStateFlow(false)
    val coachBusy: StateFlow<Boolean> = _coachBusy.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _tab = MutableStateFlow(Tab.CALENDAR)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    /** Images waiting to be sent with the next chat message. */
    private val _pendingImages = MutableStateFlow<List<Uri>>(emptyList())
    val pendingImages: StateFlow<List<Uri>> = _pendingImages.asStateFlow()

    fun selectTab(t: Tab) {
        _tab.value = t
    }

    fun consumeMessage() {
        _message.value = null
    }

    fun toast(msg: String) {
        _message.value = msg
    }

    // ---- Coach ---------------------------------------------------------------------------------

    fun addPendingImages(uris: List<Uri>) {
        _pendingImages.value = (_pendingImages.value + uris).take(MAX_IMAGES)
    }

    fun removePendingImage(uri: Uri) {
        _pendingImages.value = _pendingImages.value - uri
    }

    fun askCoach(text: String, images: List<Uri> = _pendingImages.value) {
        if (_coachBusy.value || (text.isBlank() && images.isEmpty())) return
        _pendingImages.value = emptyList()
        _tab.value = Tab.COACH
        _coachBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val inputs = images.mapNotNull { loadImage(it) }
                    val prompt = text.ifBlank { "Analyse cette capture d'écran et enregistre les données utiles." }
                    app.coach.send(prompt, inputs) { _chat.value = app.transcript.display.toList() }
                }
            } catch (e: Exception) {
                _message.value = e.message ?: e.toString()
            } finally {
                _chat.value = app.transcript.display.toList()
                _coachBusy.value = false
            }
        }
    }

    fun newConversation() {
        if (_coachBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            app.transcript.clear()
            _chat.value = emptyList()
        }
    }

    /** Downscale to Claude's recommended size and encode as JPEG base64. */
    private fun loadImage(uri: Uri): ImageInput? = runCatching {
        val source = ImageDecoder.createSource(app.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val w = info.size.width
            val h = info.size.height
            val scale = MAX_EDGE.toFloat() / maxOf(w, h)
            if (scale < 1f) decoder.setTargetSize((w * scale).toInt(), (h * scale).toInt())
        }
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        ImageInput("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
    }.getOrElse {
        _message.value = "Image illisible : ${it.message}"
        null
    }

    // ---- Strava --------------------------------------------------------------------------------

    fun stravaAuthorizeIntent(): Intent? {
        val id = data.value.settings.stravaClientId.trim()
        if (id.isEmpty()) {
            _message.value = "Renseigne d'abord le Client ID et le Client Secret de ton application Strava."
            return null
        }
        return Intent(Intent.ACTION_VIEW, Uri.parse(StravaClient.authorizeUrl(id)))
    }

    fun handleRedirect(uri: Uri) {
        if (uri.scheme != "coachia" || uri.path?.startsWith("/strava") != true) return
        val error = uri.getQueryParameter("error")
        val code = uri.getQueryParameter("code")
        if (error != null || code == null) {
            _message.value = "Connexion Strava annulée (${error ?: "pas de code"})."
            return
        }
        val scope = uri.getQueryParameter("scope").orEmpty()
        viewModelScope.launch {
            try {
                val auth = withContext(Dispatchers.IO) { app.strava.exchangeCode(data.value.settings, code) }
                app.repo.update { it.copy(strava = auth) }
                if (!scope.contains("activity:read_all")) {
                    _message.value = "Strava connecté, mais l'accès aux activités n'a pas été autorisé : reconnecte-toi en cochant toutes les cases."
                } else {
                    _message.value = "Strava connecté (${auth.athleteName}). Synchronisation…"
                }
                syncStrava()
            } catch (e: Exception) {
                _message.value = "Échec de la connexion Strava : ${e.message}"
            }
        }
    }

    fun disconnectStrava() {
        app.repo.update { it.copy(strava = null, lastStravaSync = null) }
    }

    fun syncStrava() {
        if (_syncing.value) return
        _syncing.value = true
        viewModelScope.launch {
            try {
                _message.value = withContext(Dispatchers.IO) { app.stravaSync.sync() }
            } catch (e: Exception) {
                _message.value = e.message
            } finally {
                _syncing.value = false
            }
        }
    }

    // ---- Calendar ------------------------------------------------------------------------------

    fun saveEvent(e: CalendarEvent) {
        app.repo.update { d -> d.copy(events = d.events.filterNot { it.id == e.id } + e) }
    }

    fun deleteEvent(id: String) {
        app.repo.update { d -> d.copy(events = d.events.filterNot { it.id == id }) }
    }

    fun setStatus(id: String, status: SessionStatus) {
        app.repo.update { d -> d.copy(events = d.events.map { if (it.id == id) it.copy(status = status) else it }) }
    }

    fun saveConstraint(c: Constraint) {
        app.repo.update { d -> d.copy(constraints = d.constraints.filterNot { it.id == c.id } + c) }
    }

    fun deleteConstraint(id: String) {
        app.repo.update { d -> d.copy(constraints = d.constraints.filterNot { it.id == id }) }
    }

    fun deleteWellness(id: String) {
        app.repo.update { d -> d.copy(wellness = d.wellness.filterNot { it.id == id }) }
    }

    fun zwiftShareIntent(e: CalendarEvent): Intent {
        val dir = File(app.cacheDir, "exports").apply { mkdirs() }
        val safe = e.title.replace(Regex("[^A-Za-z0-9À-ÿ _-]"), "").trim().ifEmpty { "seance" }
        val file = File(dir, "${e.date} $safe.zwo")
        file.writeText(Zwift.toZwo(e))
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Séance Zwift : ${e.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "Exporter vers Zwift")
    }

    fun phoneCalendarIntent(e: CalendarEvent): Intent {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.parse(e.date)
        val time = e.startTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        val intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, e.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, e.description)
        if (time != null) {
            val start = day.atTime(time).atZone(zone).toInstant().toEpochMilli()
            intent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + (e.durationMin ?: 60) * 60_000L)
        } else {
            intent.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, day.atStartOfDay(zone).toInstant().toEpochMilli())
        }
        return intent
    }

    // ---- Settings ------------------------------------------------------------------------------

    fun saveSettings(s: Settings) {
        app.repo.update { it.copy(settings = s) }
        _message.value = "Réglages enregistrés."
    }

    fun saveProfile(p: AthleteProfile) {
        app.repo.update { it.copy(profile = p) }
        _message.value = "Profil enregistré."
    }

    companion object {
        const val MAX_IMAGES = 5
        const val MAX_EDGE = 1568
    }
}
