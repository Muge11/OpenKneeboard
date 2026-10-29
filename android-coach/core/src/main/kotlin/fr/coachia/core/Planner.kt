package fr.coachia.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import kotlin.math.roundToInt

/**
 * Planificateur intégré, gratuit et hors ligne : périodisation simple (blocs 3+1, affûtage avant course),
 * respect des disponibilités, des impératifs et des évènements saisis, alternance dur/facile.
 */
object Planner {
    const val AUTHOR = "planner"

    /** Disponibilités utilisées tant que l'athlète n'a rien saisi dans son profil. */
    val defaultAvailability = mapOf(
        DayOfWeek.MONDAY to 0, DayOfWeek.TUESDAY to 60, DayOfWeek.WEDNESDAY to 75, DayOfWeek.THURSDAY to 60,
        DayOfWeek.FRIDAY to 0, DayOfWeek.SATURDAY to 150, DayOfWeek.SUNDAY to 120,
    )

    class Result(val sessions: List<CalendarEvent>, val removedIds: Set<String>, val summary: String)

    private enum class Focus { RUN, BIKE, MIXED }
    private enum class Phase(val label: String, val factor: Double) {
        BUILD("développement", 1.0),
        RECOVERY("semaine allégée", 0.65),
        TAPER("affûtage", 0.6),
        BASE("foncier", 0.9),
    }

    private class Day(
        val date: LocalDate,
        var minutes: Int,
        var recoveryOnly: Boolean = false,
        var noRun: Boolean = false,
        var blocked: Boolean = false,
        var hard: Boolean = false,
        var openers: Boolean = false,
    )

    fun isAuto(e: CalendarEvent) = e.createdBy == AUTHOR || e.createdBy == "ai"

    fun plan(data: AppData, today: LocalDate, days: Int = 14): Result {
        val horizon = (0 until days).map { today.plusDays(it.toLong()) }
        val end = horizon.last()
        val removed = data.events.filter {
            isAuto(it) && it.status == SessionStatus.PLANNED && it.date >= today.toString() && it.date <= end.toString()
        }
        val kept = data.events.filterNot { it in removed }
        val now = TrainingLoad.series(data.copy(events = emptyList()), today).lastOrNull()
        val ctl = now?.ctl ?: 0.0
        val tsb = now?.tsb ?: 0.0
        val p = data.profile

        val races = kept.filter { it.type == EventType.RACE && it.date >= today.toString() }.sortedBy { it.date }
        val target = races.firstOrNull { it.priority == "A" } ?: races.firstOrNull { it.priority != "C" } ?: races.firstOrNull()
        val focus = when {
            target == null -> Focus.MIXED
            target.sport.isBike -> Focus.BIKE
            target.sport.isRun -> Focus.RUN
            else -> Focus.MIXED
        }

        val dayInfo = horizon.associateWith { d ->
            val base = p.availability[d.dayOfWeek.name] ?: defaultAvailability.getValue(d.dayOfWeek)
            Day(d, base)
        }
        for (c in data.constraints) {
            val s = LocalDate.parse(c.startDate)
            val e = LocalDate.parse(c.endDate)
            for (day in dayInfo.values) {
                if (day.date.isBefore(s) || day.date.isAfter(e)) continue
                when (c.kind) {
                    ConstraintKind.UNAVAILABLE, ConstraintKind.ILLNESS, ConstraintKind.TRAVEL -> day.blocked = true
                    ConstraintKind.LIMITED_TIME -> day.minutes = minOf(day.minutes, c.maxMinutesPerDay ?: 45)
                    ConstraintKind.FATIGUE -> { day.recoveryOnly = true; day.minutes = minOf(day.minutes, 45) }
                    ConstraintKind.INJURY -> { day.recoveryOnly = true; day.noRun = true; day.minutes = minOf(day.minutes, 45) }
                    ConstraintKind.OTHER -> {}
                }
            }
        }
        // Évènements fixes : pas de nouvelle séance ce jour-là ; course => veille "déblocage", lendemain repos.
        for (e in kept) {
            val d = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: continue
            if (e.type == EventType.OTHER) continue
            dayInfo[d]?.let { it.blocked = true; it.hard = e.type != EventType.PLANNED_SESSION || (e.intensity ?: "") !in listOf("RECOVERY", "ENDURANCE") }
            if (e.type == EventType.RACE) {
                dayInfo[d.minusDays(1)]?.let { it.openers = true }
                dayInfo[d.plusDays(1)]?.blocked = true
                listOf(2L, 3L).forEach { k -> dayInfo[d.plusDays(k)]?.recoveryOnly = true }
            }
        }
        if (tsb < -25) horizon.take(2).forEach { dayInfo.getValue(it).recoveryOnly = true }

        val sessions = ArrayList<CalendarEvent>()
        val notes = ArrayList<String>()
        horizon.groupBy { it.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) to it.get(IsoFields.WEEK_BASED_YEAR) }.values.forEach { week ->
            val weekStart = week.first()
            val phase = phaseFor(weekStart, races)
            val weeklyTarget = (ctl * 7 * 1.08 + 20).coerceIn(180.0, 900.0) * phase.factor * week.size / 7.0
            val existingTss = kept.filter { it.date in week.map(LocalDate::toString) }.sumOf { TrainingLoad.estimatePlannedTss(it) }
            val weekSessions = planWeek(week.map { dayInfo.getValue(it) }, phase, focus, p, weekStart, weeklyTarget - existingTss)
            sessions += weekSessions
            notes += "Semaine du ${weekStart} : ${phase.label}, objectif ≈ ${weeklyTarget.roundToInt()} TSS, ${weekSessions.size} séance(s)"
        }
        val summary = buildString {
            append("Plan généré du $today au $end")
            target?.let { append(" — objectif : ${it.title} le ${it.date} (priorité ${it.priority ?: "?"})") }
            append(".\nCTL actuelle ${ctl.roundToInt()}, TSB ${tsb.roundToInt()}.\n")
            notes.forEach { append("• ").append(it).append('\n') }
            if (removed.isNotEmpty()) append("${removed.size} ancienne(s) séance(s) automatique(s) remplacée(s).")
        }
        return Result(sessions, removed.map { it.id }.toSet(), summary.trim())
    }

    private fun phaseFor(weekStart: LocalDate, races: List<CalendarEvent>): Phase {
        for (r in races) {
            val d = LocalDate.parse(r.date)
            val window = if (r.priority == "A") 10L else if (r.priority == "B") 4L else 0L
            val daysToRace = ChronoUnit.DAYS.between(weekStart, d)
            if (window > 0 && daysToRace in 0..window) return Phase.TAPER
        }
        if (races.isEmpty()) return if (weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 4 == 0) Phase.RECOVERY else Phase.BASE
        return if (weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 4 == 0) Phase.RECOVERY else Phase.BUILD
    }

    private fun planWeek(days: List<Day>, phase: Phase, focus: Focus, p: AthleteProfile, weekStart: LocalDate, budget: Double): List<CalendarEvent> {
        val out = ArrayList<CalendarEvent>()
        val free = days.filter { !it.blocked && it.minutes >= 30 }.toMutableList()
        // Au moins un jour de repos complet par semaine pleine.
        if (days.size == 7 && free.size == 7) free.remove(free.minBy { it.minutes })

        days.filter { it.openers && !it.blocked }.forEach { d ->
            out += Templates.openers(d.date, p)
            free.remove(d)
        }

        val weekParity = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) % 2
        val longDay = free.filter { !it.recoveryOnly && it.minutes >= 90 }
            .maxWithOrNull(compareBy<Day>({ it.date.dayOfWeek.value >= 6 }, { it.minutes }))
            ?.takeIf { phase != Phase.TAPER || it.minutes >= 90 }
        if (longDay != null) {
            val minutes = (longDay.minutes * if (phase == Phase.BUILD) 1.0 else phase.factor + 0.1).roundToInt().coerceIn(75, longDay.minutes)
            val runLong = !longDay.noRun && (focus == Focus.RUN || (focus == Focus.MIXED && weekParity == 0))
            out += if (runLong) Templates.longTrail(longDay.date, minutes, p) else Templates.longMtb(longDay.date, minutes, p)
            longDay.hard = true
            free.remove(longDay)
        }

        fun adjacentHard(d: Day) = days.any { it.hard && ChronoUnit.DAYS.between(it.date, d.date).let { k -> k == 1L || k == -1L } }

        val keyCount = when (phase) {
            Phase.BUILD -> 2
            Phase.BASE -> 1
            Phase.RECOVERY, Phase.TAPER -> 1
        }
        val keyKinds = when (focus) {
            Focus.RUN -> listOf("run", "bike")
            Focus.BIKE -> listOf("bike", "mtb")
            Focus.MIXED -> if (weekParity == 0) listOf("bike", "run") else listOf("run", "bike")
        }
        var keysPlaced = 0
        for (kind in keyKinds) {
            if (keysPlaced >= keyCount) break
            val d = free.filter { !it.recoveryOnly && it.minutes >= 45 && !adjacentHard(it) && !(kind == "run" && it.noRun) }
                .maxByOrNull { it.minutes } ?: continue
            val variant = (weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + keysPlaced) % 2
            out += when {
                phase == Phase.RECOVERY || phase == Phase.TAPER ->
                    if (kind == "run") Templates.runStrides(d.date, minOf(d.minutes, 50), p) else Templates.bikeTempo(d.date, minOf(d.minutes, 55), p)
                kind == "run" -> Templates.trailHills(d.date, minOf(d.minutes, 75), p)
                kind == "mtb" && d.date.dayOfWeek.value >= 6 -> Templates.mtbPma(d.date, minOf(d.minutes, 90), p)
                variant == 0 -> Templates.bikeVo2(d.date, minOf(d.minutes, 65), p)
                else -> Templates.bikeThreshold(d.date, minOf(d.minutes, 70), p)
            }
            d.hard = true
            free.remove(d)
            keysPlaced++
        }

        var spent = out.sumOf { TrainingLoad.estimatePlannedTss(it) }
        for (d in free.sortedByDescending { it.minutes }) {
            if (spent >= budget) break
            val endurance = !d.recoveryOnly
            val minutes = if (endurance) (d.minutes * phase.factor).roundToInt().coerceIn(30, d.minutes) else minOf(d.minutes, 40)
            val runEasy = !d.noRun && when (focus) {
                Focus.RUN -> d.date.dayOfWeek.value % 2 == 0
                Focus.BIKE -> false
                Focus.MIXED -> d.date.dayOfWeek.value % 3 == 0
            }
            val e = when {
                !endurance -> Templates.recovery(d.date, minutes, p)
                runEasy -> Templates.easyRun(d.date, minutes, p)
                d.date.dayOfWeek.value >= 6 -> Templates.mtbEndurance(d.date, minutes, p)
                else -> Templates.bikeEndurance(d.date, minutes, p)
            }
            out += e
            spent += TrainingLoad.estimatePlannedTss(e)
        }
        return out.sortedBy { it.date }
    }
}

/** Contenu des séances proposées par le planificateur intégré. */
object Templates {
    private fun warmup(min: Int = 10) = IntervalStep(min * 60, 50, 75, label = "échauffement")
    private fun cooldown(min: Int = 8) = IntervalStep(min * 60, 70, 45, label = "retour au calme")
    private fun rec(min: Double) = IntervalStep((min * 60).roundToInt(), 55, label = "récup")

    private fun hrZones(p: AthleteProfile): String {
        val lthr = TrainingLoad.lthr(p) ?: return ""
        fun b(f: Double) = (lthr * f).roundToInt()
        return " (Z2 ≈ ${b(0.81)}-${b(0.89)} bpm, seuil ≈ ${b(0.95)}-${b(1.0)} bpm)"
    }

    private fun watts(p: AthleteProfile, pct: Int) = p.effectiveFtp?.let { " ≈ ${it * pct / 100} W" } ?: ""

    private fun event(date: LocalDate, sport: Sport, title: String, desc: String, minutes: Int, intensity: String,
                      steps: List<IntervalStep> = emptyList(), elevation: Double? = null) = CalendarEvent(
        id = newId(), date = date.toString(), type = EventType.PLANNED_SESSION, sport = sport, title = title,
        description = desc, durationMin = minutes, intensity = intensity, steps = steps, elevationM = elevation,
        createdBy = Planner.AUTHOR,
    )

    fun bikeVo2(d: LocalDate, avail: Int, p: AthleteProfile): CalendarEvent {
        val reps = ((avail - 18) / 7).coerceIn(3, 6)
        val steps = listOf(warmup()) + (1..reps).flatMap { listOf(IntervalStep(240, 115, label = "VO2max"), rec(3.0)) } + cooldown()
        return event(d, Sport.INDOOR_BIKE, "Zwift — PMA $reps × 4 min",
            "$reps × 4 min à 115 % FTP${watts(p, 115)}, récupération 3 min à 55 %. Cadence 95-105 rpm. Exporte la séance vers Zwift.",
            steps.sumOf { it.durationSec } / 60, "VO2MAX", steps)
    }

    fun bikeThreshold(d: LocalDate, avail: Int, p: AthleteProfile): CalendarEvent {
        val reps = ((avail - 18) / 16).coerceIn(1, 3)
        val steps = listOf(warmup()) + (1..reps).flatMap { listOf(IntervalStep(720, 95, label = "seuil"), rec(4.0)) } + cooldown()
        return event(d, Sport.INDOOR_BIKE, "Zwift — Seuil $reps × 12 min",
            "$reps × 12 min à 95 % FTP${watts(p, 95)}, récupération 4 min. Position stable, cadence 85-95 rpm.",
            steps.sumOf { it.durationSec } / 60, "THRESHOLD", steps)
    }

    fun bikeTempo(d: LocalDate, avail: Int, p: AthleteProfile): CalendarEvent {
        val reps = ((avail - 18) / 15).coerceIn(1, 3)
        val steps = listOf(warmup()) + (1..reps).flatMap { listOf(IntervalStep(600, 83, label = "tempo"), rec(5.0)) } + cooldown()
        return event(d, Sport.INDOOR_BIKE, "Zwift — Tempo $reps × 10 min",
            "$reps × 10 min à 83 % FTP${watts(p, 83)} : rester en contrôle, séance d'entretien.",
            steps.sumOf { it.durationSec } / 60, "TEMPO", steps)
    }

    fun bikeEndurance(d: LocalDate, minutes: Int, p: AthleteProfile): CalendarEvent {
        val steps = listOf(IntervalStep(600, 50, 68, label = "échauffement"), IntervalStep((minutes - 15).coerceAtLeast(10) * 60, 68, label = "endurance"), IntervalStep(300, 65, 50, label = "retour au calme"))
        return event(d, Sport.INDOOR_BIKE, "Zwift — Endurance ${formatMin(minutes)}",
            "Endurance fondamentale à 65-70 % FTP${watts(p, 68)}, aisance respiratoire. Peut se faire dehors en VTT sur terrain roulant.",
            steps.sumOf { it.durationSec } / 60, "ENDURANCE", steps)
    }

    fun recovery(d: LocalDate, minutes: Int, p: AthleteProfile): CalendarEvent {
        val steps = listOf(IntervalStep(minutes * 60, 55, label = "récupération"))
        return event(d, Sport.INDOOR_BIKE, "Récupération active ${formatMin(minutes)}",
            "Pédalage très facile à 50-55 % FTP${watts(p, 55)}, ou repos complet si fatigue marquée.",
            minutes, "RECOVERY", steps)
    }

    fun openers(d: LocalDate, p: AthleteProfile): CalendarEvent {
        val steps = listOf(warmup(15)) + (1..3).flatMap { listOf(IntervalStep(60, 110, label = "accélération"), rec(3.0)) } + listOf(IntervalStep(300, 65), cooldown(8))
        return event(d, Sport.INDOOR_BIKE, "Veille de course — déblocage",
            "Séance courte pour arriver frais et réveillé : 3 × 1 min à 110 % FTP${watts(p, 110)}. En trail, remplace par 25 min de footing + 4 lignes droites.",
            steps.sumOf { it.durationSec } / 60, "RECOVERY", steps)
    }

    fun trailHills(d: LocalDate, avail: Int, p: AthleteProfile): CalendarEvent {
        val reps = ((avail - 30) / 6).coerceIn(4, 10)
        val minutes = 30 + reps * 6
        return event(d, Sport.TRAIL, "Trail — côtes $reps × 3 min",
            "Échauffement 20 min facile, puis $reps × 3 min en côte (8-12 %) à allure seuil${hrZones(p)}, récupération en redescendant au trot. " +
                "Retour au calme 10 min. Bâtons possibles si ta course en utilise.",
            minutes, "THRESHOLD", elevation = reps * 60.0)
    }

    fun runStrides(d: LocalDate, minutes: Int, p: AthleteProfile) = event(d, Sport.RUN, "Footing + lignes droites",
        "${formatMin(minutes)} en endurance${hrZones(p)} puis 6 × 20 s d'accélération progressive, récupération marchée.",
        minutes, "ENDURANCE")

    fun easyRun(d: LocalDate, minutes: Int, p: AthleteProfile) = event(d, Sport.RUN, "Footing endurance ${formatMin(minutes)}",
        "Course facile en endurance fondamentale${hrZones(p)}, sur sentier si possible.",
        minutes, "ENDURANCE")

    fun longTrail(d: LocalDate, minutes: Int, p: AthleteProfile) = event(d, Sport.TRAIL, "Sortie longue trail ${formatMin(minutes)}",
        "Sortie longue en endurance${hrZones(p)} avec environ ${minutes * 8} m de D+. Marche active dans les fortes pentes, " +
            "travaille les descentes. Teste ton alimentation de course (glucides toutes les 30-45 min).",
        minutes, "ENDURANCE", elevation = minutes * 8.0)

    fun longMtb(d: LocalDate, minutes: Int, p: AthleteProfile) = event(d, Sport.MTB, "Sortie longue VTT ${formatMin(minutes)}",
        "Endurance en VTT XC (65-75 % FTP${watts(p, 70)}) avec environ ${minutes * 6} m de D+. Profite des descentes pour le pilotage technique.",
        minutes, "ENDURANCE", elevation = minutes * 6.0)

    fun mtbEndurance(d: LocalDate, minutes: Int, p: AthleteProfile) = event(d, Sport.MTB, "VTT endurance ${formatMin(minutes)}",
        "Sortie VTT facile (65-70 % FTP${watts(p, 68)}), terrain roulant. Par mauvais temps : même durée sur Zwift à 68 %.",
        minutes, "ENDURANCE")

    fun mtbPma(d: LocalDate, avail: Int, p: AthleteProfile) = event(d, Sport.MTB, "VTT — PMA en montée",
        "Échauffement 20 min, puis 6 × 2 min en montée à 120 % FTP${watts(p, 120)} (rythme course XC), récupération 3 min en redescendant. " +
            "Termine en endurance avec un passage technique.",
        minOf(avail, 90), "VO2MAX", elevation = 500.0)

    private fun formatMin(m: Int) = if (m >= 60) "%dh%02d".format(m / 60, m % 60) else "$m min"
}
