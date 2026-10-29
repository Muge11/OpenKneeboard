package fr.coachia.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneOffset

class StravaException(message: String) : IOException(message)

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_at") val expiresAt: Long,
    val athlete: StravaAthlete? = null,
)

@Serializable
data class StravaAthlete(
    val id: Long = 0,
    val firstname: String? = null,
    val lastname: String? = null,
    val ftp: Int? = null,
    val weight: Double? = null,
    val sex: String? = null,
)

@Serializable
private data class StravaActivity(
    val id: Long,
    val name: String = "",
    @SerialName("sport_type") val sportType: String? = null,
    val type: String? = null,
    @SerialName("start_date_local") val startDateLocal: String,
    @SerialName("moving_time") val movingTime: Int = 0,
    val distance: Double = 0.0,
    @SerialName("total_elevation_gain") val elevation: Double = 0.0,
    @SerialName("average_heartrate") val avgHr: Double? = null,
    @SerialName("max_heartrate") val maxHr: Double? = null,
    @SerialName("average_watts") val avgWatts: Double? = null,
    @SerialName("weighted_average_watts") val weightedWatts: Double? = null,
    @SerialName("device_watts") val deviceWatts: Boolean? = null,
    @SerialName("suffer_score") val sufferScore: Double? = null,
    val trainer: Boolean = false,
)

class StravaClient(
    private val http: OkHttpClient,
    private val baseUrl: String = "https://www.strava.com",
) {
    companion object {
        const val REDIRECT_URI = "coachia://localhost/strava"
        const val SCOPE = "read,activity:read_all,profile:read_all"

        fun authorizeUrl(clientId: String): String =
            "https://www.strava.com/oauth/mobile/authorize".toHttpUrl().newBuilder()
                .addQueryParameter("client_id", clientId)
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("response_type", "code")
                .addQueryParameter("approval_prompt", "auto")
                .addQueryParameter("scope", SCOPE)
                .build().toString()

        fun mapSport(sportType: String?, trainer: Boolean): Sport = when (sportType) {
            "TrailRun" -> Sport.TRAIL
            "Run", "VirtualRun" -> Sport.RUN
            "Hike", "Walk" -> Sport.HIKE
            "MountainBikeRide", "EMountainBikeRide" -> Sport.MTB
            "VirtualRide" -> Sport.INDOOR_BIKE
            "Ride", "GravelRide", "EBikeRide", "Velomobile" -> if (trainer) Sport.INDOOR_BIKE else Sport.ROAD_BIKE
            "WeightTraining", "Workout", "Crossfit", "Yoga", "Pilates" -> Sport.STRENGTH
            else -> Sport.OTHER
        }
    }

    private fun url(path: String): HttpUrl = (baseUrl.trimEnd('/') + path).toHttpUrl()

    private fun execute(request: Request): String {
        http.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw StravaException("Strava HTTP ${resp.code}: ${body.take(300)}")
            return body
        }
    }

    private fun token(settings: Settings, vararg params: Pair<String, String>): StravaAuth {
        if (settings.stravaClientId.isBlank() || settings.stravaClientSecret.isBlank()) {
            throw StravaException("Client ID / Client Secret Strava manquants (Réglages).")
        }
        val form = FormBody.Builder()
            .add("client_id", settings.stravaClientId)
            .add("client_secret", settings.stravaClientSecret)
        params.forEach { (k, v) -> form.add(k, v) }
        val body = execute(Request.Builder().url(url("/oauth/token")).post(form.build()).build())
        val t = AppJson.decodeFromString(TokenResponse.serializer(), body)
        return StravaAuth(
            accessToken = t.accessToken,
            refreshToken = t.refreshToken,
            expiresAt = t.expiresAt,
            athleteId = t.athlete?.id ?: 0,
            athleteName = listOfNotNull(t.athlete?.firstname, t.athlete?.lastname).joinToString(" "),
        )
    }

    fun exchangeCode(settings: Settings, code: String): StravaAuth =
        token(settings, "code" to code, "grant_type" to "authorization_code")

    fun refresh(settings: Settings, auth: StravaAuth): StravaAuth =
        token(settings, "refresh_token" to auth.refreshToken, "grant_type" to "refresh_token").let {
            it.copy(
                athleteId = if (it.athleteId == 0L) auth.athleteId else it.athleteId,
                athleteName = it.athleteName.ifBlank { auth.athleteName },
            )
        }

    private fun get(path: String, auth: StravaAuth, query: Map<String, String> = emptyMap()): String {
        val u = url(path).newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return execute(Request.Builder().url(u).header("Authorization", "Bearer ${auth.accessToken}").build())
    }

    fun athlete(auth: StravaAuth): StravaAthlete =
        AppJson.decodeFromString(StravaAthlete.serializer(), get("/api/v3/athlete", auth))

    fun activitiesSince(auth: StravaAuth, afterEpochSec: Long): List<Activity> {
        val out = ArrayList<Activity>()
        var page = 1
        while (page <= 20) {
            val body = get(
                "/api/v3/athlete/activities", auth,
                mapOf("after" to afterEpochSec.toString(), "per_page" to "100", "page" to page.toString()),
            )
            val list = AppJson.decodeFromString(ListSerializer(StravaActivity.serializer()), body)
            if (list.isEmpty()) break
            list.mapTo(out) { it.toActivity() }
            if (list.size < 100) break
            page++
        }
        return out
    }

    private fun StravaActivity.toActivity() = Activity(
        id = "strava-$id",
        source = "STRAVA",
        sport = mapSport(sportType ?: type, trainer),
        name = name,
        startTime = startDateLocal.removeSuffix("Z").take(19),
        movingTimeSec = movingTime,
        distanceM = distance,
        elevationGainM = elevation,
        avgHr = avgHr,
        maxHr = maxHr,
        avgWatts = avgWatts,
        weightedAvgWatts = weightedWatts,
        sufferScore = sufferScore,
        trainer = trainer,
    )
}

/** Pulls new Strava activities into the repository and marks matching planned sessions as done. */
class StravaSync(private val repo: AppRepository, private val client: StravaClient) {
    fun sync(nowEpochSec: Long = System.currentTimeMillis() / 1000): String {
        val data = repo.data
        var auth = data.strava ?: throw StravaException("Strava n'est pas connecté (Réglages).")
        if (auth.expiresAt - 300 < nowEpochSec) {
            auth = client.refresh(data.settings, auth)
            val refreshed = auth
            repo.update { it.copy(strava = refreshed) }
        }
        val after = data.lastStravaSync?.let { it - 3 * 86400 }
            ?: LocalDate.now().minusDays(365).atStartOfDay().toEpochSecond(ZoneOffset.UTC)
        val fetched = client.activitiesSince(auth, after)
        val athlete = runCatching { client.athlete(auth) }.getOrNull()
        var added = 0
        repo.update { d ->
            val byId = d.activities.associateBy { it.id }.toMutableMap()
            for (a in fetched) {
                if (a.id !in byId) added++
                byId[a.id] = a
            }
            val activities = byId.values.sortedByDescending { it.startTime }
            d.copy(
                activities = activities,
                events = markCompleted(d.events, activities),
                lastStravaSync = nowEpochSec,
                profile = d.profile.copy(
                    stravaFtpW = athlete?.ftp ?: d.profile.stravaFtpW,
                    weightKg = d.profile.weightKg ?: athlete?.weight,
                    name = d.profile.name.ifBlank { listOfNotNull(athlete?.firstname).joinToString() },
                ),
            )
        }
        return "Strava : ${fetched.size} activités lues, $added nouvelles."
    }

    companion object {
        fun sameFamily(a: Sport, b: Sport) = a == b || (a.isBike && b.isBike) || (a.isRun && b.isRun)

        fun markCompleted(events: List<CalendarEvent>, activities: List<Activity>): List<CalendarEvent> {
            val used = events.mapNotNull { it.completedActivityId }.toMutableSet()
            return events.map { e ->
                if (e.status != SessionStatus.PLANNED || e.type == EventType.OTHER) return@map e
                val match = activities.firstOrNull {
                    it.date == e.date && it.id !in used && sameFamily(it.sport, e.sport)
                } ?: return@map e
                used += match.id
                e.copy(status = SessionStatus.DONE, completedActivityId = match.id)
            }
        }
    }
}
