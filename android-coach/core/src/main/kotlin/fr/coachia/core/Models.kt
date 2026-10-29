package fr.coachia.core

import kotlinx.serialization.Serializable

@Serializable
enum class Sport(val label: String) {
    TRAIL("Trail"),
    RUN("Course à pied"),
    HIKE("Randonnée"),
    MTB("VTT XC"),
    ROAD_BIKE("Vélo route"),
    INDOOR_BIKE("Home trainer (Zwift)"),
    STRENGTH("Renforcement"),
    OTHER("Autre");

    val isBike: Boolean get() = this == MTB || this == ROAD_BIKE || this == INDOOR_BIKE
    val isRun: Boolean get() = this == TRAIL || this == RUN || this == HIKE
}

@Serializable
data class Activity(
    val id: String,
    val source: String,
    val sport: Sport,
    val name: String,
    /** ISO-8601 local date-time, e.g. 2026-10-29T07:30:00 */
    val startTime: String,
    val movingTimeSec: Int,
    val distanceM: Double = 0.0,
    val elevationGainM: Double = 0.0,
    val avgHr: Double? = null,
    val maxHr: Double? = null,
    val avgWatts: Double? = null,
    val weightedAvgWatts: Double? = null,
    val sufferScore: Double? = null,
    val trainer: Boolean = false,
) {
    val date: String get() = startTime.take(10)
}

@Serializable
enum class EventType(val label: String) {
    RACE("Course / compétition"),
    CLUB_TRAINING("Entraînement club"),
    PLANNED_SESSION("Séance planifiée"),
    OTHER("Autre évènement"),
}

@Serializable
enum class SessionStatus { PLANNED, DONE, SKIPPED }

/** One step of a structured indoor workout, power expressed in % of FTP. */
@Serializable
data class IntervalStep(
    val durationSec: Int,
    val powerLowPct: Int,
    val powerHighPct: Int? = null,
    val cadence: Int? = null,
    val label: String = "",
)

@Serializable
data class CalendarEvent(
    val id: String,
    val date: String,
    val startTime: String? = null,
    val type: EventType,
    val sport: Sport = Sport.OTHER,
    val title: String,
    val description: String = "",
    val durationMin: Int? = null,
    val distanceKm: Double? = null,
    val elevationM: Double? = null,
    val targetTss: Double? = null,
    /** RECOVERY, ENDURANCE, TEMPO, SWEET_SPOT, THRESHOLD, VO2MAX, ANAEROBIC, RACE */
    val intensity: String? = null,
    /** A, B or C for races. */
    val priority: String? = null,
    val steps: List<IntervalStep> = emptyList(),
    val status: SessionStatus = SessionStatus.PLANNED,
    /** "user" or "ai" */
    val createdBy: String = "user",
    val completedActivityId: String? = null,
)

@Serializable
enum class ConstraintKind(val label: String) {
    UNAVAILABLE("Indisponible"),
    LIMITED_TIME("Temps limité"),
    FATIGUE("Fatigue"),
    ILLNESS("Maladie"),
    INJURY("Blessure / douleur"),
    TRAVEL("Déplacement"),
    OTHER("Autre"),
}

/** Impératifs et impondérables saisis au fil de l'eau. */
@Serializable
data class Constraint(
    val id: String,
    val startDate: String,
    val endDate: String,
    val kind: ConstraintKind,
    val description: String,
    val maxMinutesPerDay: Int? = null,
    val createdAt: String = "",
)

@Serializable
data class WellnessEntry(
    val id: String,
    val date: String,
    /** RENPHO, COROS, MANUAL, ... */
    val source: String,
    val weightKg: Double? = null,
    val bmi: Double? = null,
    val bodyFatPct: Double? = null,
    val muscleMassKg: Double? = null,
    val skeletalMusclePct: Double? = null,
    val bodyWaterPct: Double? = null,
    val boneMassKg: Double? = null,
    val proteinPct: Double? = null,
    val visceralFat: Double? = null,
    val subcutaneousFatPct: Double? = null,
    val bmrKcal: Double? = null,
    val metabolicAge: Int? = null,
    val restingHr: Double? = null,
    val hrvMs: Double? = null,
    val sleepHours: Double? = null,
    val sleepScore: Double? = null,
    val stress: Double? = null,
    val vo2max: Double? = null,
    val trainingLoad: Double? = null,
    val recoveryPct: Double? = null,
    val notes: String = "",
)

@Serializable
data class AthleteProfile(
    val name: String = "",
    val birthYear: Int? = null,
    val sex: String? = null,
    val heightCm: Double? = null,
    val weightKg: Double? = null,
    /** FTP saisie manuellement ou fixée par le coach IA (prioritaire). */
    val ftpW: Int? = null,
    /** FTP lue sur le profil Strava. */
    val stravaFtpW: Int? = null,
    val ftpUpdated: String? = null,
    val maxHr: Int? = null,
    val restingHr: Int? = null,
    val lthr: Int? = null,
    val goals: String = "",
    /** Minutes disponibles par jour, clé = MONDAY..SUNDAY. */
    val availability: Map<String, Int> = emptyMap(),
    val availabilityNotes: String = "",
    val equipment: String = "Home trainer Zwift Ride (séances intérieures sur Zwift), VTT XC, chaussures de trail",
) {
    val effectiveFtp: Int? get() = ftpW ?: stravaFtpW
}

@Serializable
data class StravaAuth(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val athleteId: Long = 0,
    val athleteName: String = "",
)

@Serializable
data class Settings(
    val anthropicApiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val stravaClientId: String = "",
    val stravaClientSecret: String = "",
) {
    companion object {
        const val DEFAULT_MODEL = "claude-opus-5-5"
    }
}

@Serializable
data class AppData(
    val profile: AthleteProfile = AthleteProfile(),
    val settings: Settings = Settings(),
    val strava: StravaAuth? = null,
    val lastStravaSync: Long? = null,
    val activities: List<Activity> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val constraints: List<Constraint> = emptyList(),
    val wellness: List<WellnessEntry> = emptyList(),
)
