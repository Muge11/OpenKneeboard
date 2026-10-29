package fr.coachia.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Bilan de forme calculé localement (sans IA), en Markdown. */
object Analysis {
    fun report(data: AppData, today: LocalDate): String {
        val p = data.profile
        val series = TrainingLoad.series(data, today, today.plusDays(60))
        val past = series.filter { !it.forecast }
        val now = past.lastOrNull()
        val sb = StringBuilder()
        val advice = ArrayList<String>()
        fun r(v: Double) = v.roundToInt()

        sb.append("## Charge d'entraînement\n")
        if (now == null || data.activities.isEmpty()) {
            sb.append("Pas encore d'activités : connecte Strava et lance une synchronisation.\n")
            return sb.toString()
        }
        sb.append("- Forme (CTL) : **${r(now.ctl)}**, fatigue (ATL) : **${r(now.atl)}**, fraîcheur (TSB) : **${r(now.tsb)}** → ${TrainingLoad.describeForm(now.tsb)}\n")
        val ctl7 = past.firstOrNull { it.date == today.minusDays(7) }?.ctl
        val ctl28 = past.firstOrNull { it.date == today.minusDays(28) }?.ctl
        ctl7?.let {
            val ramp = now.ctl - it
            sb.append("- Évolution sur 7 jours : ${if (ramp >= 0) "+" else ""}${"%.1f".format(ramp)} CTL\n")
            if (ramp > 7) advice += "La charge monte vite (+${r(ramp)} CTL en une semaine) : prévois une semaine allégée bientôt pour limiter le risque de blessure."
            if (ramp < -4) advice += "Ta forme de fond baisse : reprends progressivement de la régularité."
        }
        ctl28?.let { sb.append("- Sur 28 jours : CTL ${r(it)} → ${r(now.ctl)}\n") }
        if (now.tsb < -30) advice += "Fraîcheur très basse (TSB ${r(now.tsb)}) : 2-3 jours faciles s'imposent."

        sb.append("\n## Volume des 4 dernières semaines\n")
        val recent = data.activities.filter { !LocalDate.parse(it.date).isBefore(today.minusDays(27)) }
        if (recent.isEmpty()) sb.append("Aucune activité.\n")
        recent.groupBy { it.sport }.toList().sortedByDescending { (_, l) -> l.sumOf { it.movingTimeSec } }.forEach { (s, l) ->
            val h = l.sumOf { it.movingTimeSec } / 3600.0
            sb.append("- ${s.label} : ${l.size} sorties, ${"%.1f".format(h)} h, ${r(l.sumOf { it.distanceM } / 1000)} km, D+ ${r(l.sumOf { it.elevationGainM })} m\n")
        }
        val perWeek = recent.size / 4.0
        sb.append("- Régularité : ${"%.1f".format(perWeek)} séances par semaine\n")
        data.activities.maxByOrNull { it.startTime }?.let {
            val since = ChronoUnit.DAYS.between(LocalDateTime.parse(it.startTime).toLocalDate(), today)
            if (since >= 5) advice += "Aucune activité depuis $since jours."
        }

        sb.append("\n## Puissance\n")
        val ftp = p.effectiveFtp
        val weight = p.weightKg
        if (ftp == null) {
            sb.append("FTP inconnue : renseigne-la dans Réglages (ou sur ton profil Strava).\n")
        } else {
            sb.append("- FTP : **$ftp W**${weight?.let { " · ${"%.2f".format(ftp / it)} W/kg" } ?: ""}\n")
            val best = data.activities.filter {
                it.sport.isBike && it.movingTimeSec >= 20 * 60 && !LocalDate.parse(it.date).isBefore(today.minusDays(60))
            }.mapNotNull { a -> (a.weightedAvgWatts ?: a.avgWatts)?.let { w -> a to w } }.maxByOrNull { it.second }
            best?.let { (a, w) ->
                val estimate = if (a.movingTimeSec <= 75 * 60) w * 0.95 else w
                sb.append("- Meilleure puissance normalisée récente : ${r(w)} W (« ${a.name} », ${a.date})\n")
                if (estimate > ftp * 1.03) advice += "Tes efforts récents suggèrent une FTP autour de ${r(estimate)} W : programme un test FTP sur Zwift."
            }
        }

        val races = data.events.filter { it.type == EventType.RACE && it.date >= today.toString() }.sortedBy { it.date }
        sb.append("\n## Prévisions\n")
        val week = series.filter { it.forecast && !it.date.isAfter(today.plusDays(7)) }
        sb.append("- Charge prévue sur 7 jours : ${r(week.sumOf { it.tss })} TSS (7 derniers jours : ${r(past.takeLast(7).sumOf { it.tss })})\n")
        series.firstOrNull { it.date == today.plusDays(14) }?.let { sb.append("- Dans 14 jours : CTL ${r(it.ctl)}, TSB ${r(it.tsb)}\n") }
        if (races.isEmpty()) sb.append("- Aucune course au calendrier : ajoute tes objectifs pour un plan ciblé.\n")
        races.take(4).forEach { race ->
            val d = series.firstOrNull { it.date.toString() == race.date }
            if (d == null) {
                sb.append("- ${race.title} (${race.date}) : trop loin pour une prévision fiable\n")
            } else {
                sb.append("- ${race.title} (${race.date}, priorité ${race.priority ?: "?"}) : CTL ${r(d.ctl)}, TSB ${r(d.tsb)} → ${TrainingLoad.describeForm(d.tsb)}\n")
                if (race.priority == "A" && d.tsb < -5) advice += "Pour « ${race.title} », la fraîcheur prévue est trop basse : allège les 10 jours précédents (le planificateur le fait automatiquement)."
                if (race.priority == "A" && d.tsb > 25) advice += "Pour « ${race.title} », tu risques d'arriver trop reposé : garde quelques rappels d'intensité."
            }
        }
        val plannedAhead = data.events.count { it.status == SessionStatus.PLANNED && it.date > today.toString() }
        if (plannedAhead == 0) advice += "Aucune séance planifiée : touche « Planifier 2 semaines »."

        sb.append("\n## Recommandations\n")
        if (advice.isEmpty()) advice += "Rien d'alarmant : continue ainsi en respectant l'alternance dur / facile."
        advice.forEach { sb.append("- ").append(it).append('\n') }
        return sb.toString()
    }
}
