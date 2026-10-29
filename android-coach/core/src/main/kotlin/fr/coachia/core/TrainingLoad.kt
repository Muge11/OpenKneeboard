package fr.coachia.core

import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt

data class DayLoad(
    val date: LocalDate,
    val tss: Double,
    /** Fitness (Chronic Training Load, 42 days). */
    val ctl: Double,
    /** Fatigue (Acute Training Load, 7 days). */
    val atl: Double,
    /** Form (Training Stress Balance) = yesterday's CTL - ATL. */
    val tsb: Double,
    val forecast: Boolean,
)

object TrainingLoad {
    private const val CTL_DAYS = 42.0
    private const val ATL_DAYS = 7.0

    /** How the TSS of an activity was obtained, for transparency in the UI and towards the coach. */
    enum class Method { POWER, HEART_RATE, RELATIVE_EFFORT, DURATION }

    fun lthr(profile: AthleteProfile): Double? =
        profile.lthr?.toDouble() ?: profile.maxHr?.let { it * 0.89 }

    fun estimateTss(a: Activity, profile: AthleteProfile): Pair<Double, Method> {
        val hours = a.movingTimeSec / 3600.0
        val ftp = profile.effectiveFtp
        val power = a.weightedAvgWatts ?: a.avgWatts
        if (a.sport.isBike && ftp != null && ftp > 0 && power != null && power > 0) {
            val intensity = min(power / ftp, 1.3)
            return hours * intensity * intensity * 100 to Method.POWER
        }
        val lthr = lthr(profile)
        if (a.avgHr != null && a.avgHr > 0 && lthr != null) {
            val intensity = min(a.avgHr / lthr, 1.2)
            return hours * intensity * intensity * 100 to Method.HEART_RATE
        }
        if (a.sufferScore != null && a.sufferScore > 0) return a.sufferScore to Method.RELATIVE_EFFORT
        val perHour = when (a.sport) {
            Sport.HIKE, Sport.STRENGTH -> 35.0
            Sport.OTHER -> 40.0
            else -> 55.0
        }
        return hours * perHour to Method.DURATION
    }

    private val intensityFactors = mapOf(
        "RECOVERY" to 0.55,
        "ENDURANCE" to 0.68,
        "TEMPO" to 0.80,
        "SWEET_SPOT" to 0.87,
        "THRESHOLD" to 0.92,
        "VO2MAX" to 0.90,
        "ANAEROBIC" to 0.88,
        "RACE" to 0.88,
    )

    fun estimatePlannedTss(e: CalendarEvent): Double {
        e.targetTss?.let { return it }
        if (e.steps.isNotEmpty()) {
            return e.steps.sumOf { s ->
                val pct = ((s.powerLowPct + (s.powerHighPct ?: s.powerLowPct)) / 2.0) / 100.0
                s.durationSec / 3600.0 * pct * pct * 100
            }
        }
        val minutes = e.durationMin ?: return 0.0
        val factor = intensityFactors[e.intensity?.uppercase()]
            ?: if (e.type == EventType.RACE) 0.88 else 0.70
        return minutes / 60.0 * factor * factor * 100
    }

    /**
     * Daily CTL/ATL/TSB from the first activity up to [until]. Days after [today] are a forecast
     * built from the planned sessions, races and club trainings in the calendar.
     */
    fun series(data: AppData, today: LocalDate, until: LocalDate = today): List<DayLoad> {
        val done = HashMap<LocalDate, Double>()
        for (a in data.activities) {
            val d = runCatching { LocalDate.parse(a.date) }.getOrNull() ?: continue
            done.merge(d, estimateTss(a, data.profile).first, Double::plus)
        }
        val planned = HashMap<LocalDate, Double>()
        for (e in data.events) {
            if (e.status != SessionStatus.PLANNED) continue
            val d = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: continue
            planned.merge(d, estimatePlannedTss(e), Double::plus)
        }
        val start = done.keys.minOrNull() ?: today.minusDays(42)
        val ctlK = 1 - exp(-1 / CTL_DAYS)
        val atlK = 1 - exp(-1 / ATL_DAYS)
        var ctl = 0.0
        var atl = 0.0
        val out = ArrayList<DayLoad>()
        var d = start
        while (!d.isAfter(until)) {
            val forecast = d.isAfter(today)
            val tss = if (forecast) planned[d] ?: 0.0 else (done[d] ?: 0.0)
            val tsb = ctl - atl
            ctl += (tss - ctl) * ctlK
            atl += (tss - atl) * atlK
            out += DayLoad(d, tss, ctl, atl, tsb, forecast)
            d = d.plusDays(1)
        }
        return out
    }

    fun describeForm(tsb: Double): String = when {
        tsb > 25 -> "très frais (risque de désentraînement)"
        tsb > 5 -> "frais, prêt pour une compétition"
        tsb > -10 -> "neutre"
        tsb > -30 -> "en charge productive"
        else -> "surcharge, risque de blessure/fatigue"
    }

    fun summary(data: AppData, today: LocalDate, forecastDays: Int = 21): String {
        val s = series(data, today, today.plusDays(forecastDays.toLong()))
        if (s.isEmpty()) return "Aucune donnée."
        val now = s.lastOrNull { !it.forecast } ?: s.first()
        val sb = StringBuilder()
        fun r(v: Double) = (v * 10).roundToInt() / 10.0
        sb.append("Aujourd'hui ($today) : CTL ${r(now.ctl)}, ATL ${r(now.atl)}, TSB ${r(now.tsb)} -> ${describeForm(now.tsb)}\n")
        val weekAgo = s.lastOrNull { !it.forecast && it.date == today.minusDays(7) }
        val monthAgo = s.lastOrNull { !it.forecast && it.date == today.minusDays(28) }
        weekAgo?.let { sb.append("Il y a 7 j : CTL ${r(it.ctl)} (rampe ${r(now.ctl - it.ctl)}/semaine)\n") }
        monthAgo?.let { sb.append("Il y a 28 j : CTL ${r(it.ctl)}\n") }
        sb.append("Prévision selon le calendrier :\n")
        s.filter { it.forecast && (it.date.dayOfWeek.value == 7 || it.tss > 0) }
            .forEach { sb.append("  ${it.date} (${it.date.dayOfWeek}) : TSS prévu ${r(it.tss)}, CTL ${r(it.ctl)}, TSB ${r(it.tsb)}\n") }
        return sb.toString()
    }
}
