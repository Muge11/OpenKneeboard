package fr.coachia.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonElement as JE
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import kotlin.math.roundToInt

class ToolOutcome(val content: String, val isError: Boolean = false, val note: String? = null)

/** Provider-neutral tool declaration: [parameters] is an OpenAPI-style JSON schema object, null when the tool takes no input. */
class ToolDef(val name: String, val description: String, val parameters: JsonObject?)

private class BadInput(message: String) : Exception(message)

/** The client-side tools the coach can call. Everything reads and writes the local [AppRepository]. */
class CoachTools(
    private val repo: AppRepository,
    private val syncStrava: () -> String,
    private val today: () -> LocalDate = { LocalDate.now() },
) {
    fun execute(name: String, input: JsonObject): ToolOutcome = try {
        when (name) {
            "get_athlete_profile" -> getProfile()
            "update_athlete_profile" -> updateProfile(input)
            "get_activities" -> getActivities(input)
            "get_fitness" -> getFitness(input)
            "get_analysis" -> ToolOutcome(Analysis.report(repo.data, today()))
            "auto_plan" -> autoPlan(input)
            "get_calendar" -> getCalendar(input)
            "create_sessions" -> createSessions(input)
            "update_session" -> updateSession(input)
            "delete_sessions" -> deleteSessions(input)
            "add_constraint" -> addConstraint(input)
            "sync_strava" -> ToolOutcome(syncStrava(), note = "Synchronisation Strava")
            else -> ToolOutcome("Outil inconnu : $name", isError = true)
        }
    } catch (e: BadInput) {
        ToolOutcome("Paramètres invalides : ${e.message}", isError = true)
    } catch (e: Exception) {
        ToolOutcome("Erreur : ${e.message}", isError = true)
    }

    // ---- input helpers -------------------------------------------------------------------------

    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull
    private fun JsonObject.num(k: String): Double? = (this[k] as? JsonPrimitive)?.let { it.doubleOrNull ?: it.contentOrNull?.toDoubleOrNull() }
    private fun JsonObject.int(k: String): Int? = num(k)?.roundToInt()
    private fun JsonObject.reqStr(k: String): String = str(k)?.takeIf { it.isNotBlank() } ?: throw BadInput("'$k' est requis")
    private fun JsonObject.date(k: String): String? = str(k)?.let {
        runCatching { LocalDate.parse(it).toString() }.getOrElse { throw BadInput("'$k' doit être une date AAAA-MM-JJ") }
    }
    private fun JsonObject.reqDate(k: String): String = date(k) ?: throw BadInput("'$k' est requis")

    private inline fun <reified E : Enum<E>> parseEnum(v: String?, k: String): E? = v?.let { s ->
        enumValues<E>().firstOrNull { it.name.equals(s, ignoreCase = true) }
            ?: throw BadInput("'$k' doit valoir ${enumValues<E>().joinToString { it.name }}")
    }

    private fun range(input: JsonObject, defaultBack: Long, defaultForward: Long): Pair<LocalDate, LocalDate> {
        val from = input.date("from")?.let(LocalDate::parse) ?: today().minusDays(defaultBack)
        val to = input.date("to")?.let(LocalDate::parse) ?: today().plusDays(defaultForward)
        return from to to
    }

    private fun inRange(d: String, r: Pair<LocalDate, LocalDate>): Boolean {
        val x = runCatching { LocalDate.parse(d.take(10)) }.getOrNull() ?: return false
        return !x.isBefore(r.first) && !x.isAfter(r.second)
    }

    private fun round1(v: Double) = (v * 10).roundToInt() / 10.0

    // ---- tools ---------------------------------------------------------------------------------

    private fun getProfile(): ToolOutcome {
        val d = repo.data
        val p = d.profile
        val json = AppJson.encodeToJsonElement(AthleteProfile.serializer(), p).jsonObject
        val extra = buildJsonObject {
            put("effective_ftp_w", p.effectiveFtp)
            val ftp = p.effectiveFtp
            val w = p.weightKg
            if (ftp != null && w != null) put("ftp_w_per_kg", round1(ftp / w * 10) / 10)
            TrainingLoad.lthr(p)?.let { put("lthr_used_bpm", it.roundToInt()) }
            if (ftp != null) {
                put("power_zones_w", "Z1 <${(ftp * 0.55).roundToInt()} | Z2 ${(ftp * 0.56).roundToInt()}-${(ftp * 0.75).roundToInt()} | Z3 ${(ftp * 0.76).roundToInt()}-${(ftp * 0.90).roundToInt()} | Z4 ${(ftp * 0.91).roundToInt()}-${(ftp * 1.05).roundToInt()} | Z5 ${(ftp * 1.06).roundToInt()}-${(ftp * 1.20).roundToInt()} | Z6 >${(ftp * 1.20).roundToInt()}")
            }
            put("strava_connected", d.strava != null)
            put("activities_count", d.activities.size)
        }
        return ToolOutcome(JsonObject(json + extra).toString())
    }

    private fun updateProfile(input: JsonObject): ToolOutcome {
        val changes = mutableListOf<String>()
        repo.update { d ->
            var p = d.profile
            input.int("ftp_w")?.let { p = p.copy(ftpW = it, ftpUpdated = today().toString()); changes += "FTP $it W" }
            input.int("max_hr")?.let { p = p.copy(maxHr = it); changes += "FC max $it" }
            input.int("resting_hr")?.let { p = p.copy(restingHr = it); changes += "FC repos $it" }
            input.int("lthr")?.let { p = p.copy(lthr = it); changes += "FC seuil $it" }
            input.num("weight_kg")?.let { p = p.copy(weightKg = it); changes += "poids $it kg" }
            input.str("goals")?.let { p = p.copy(goals = it); changes += "objectifs" }
            input.str("availability_notes")?.let { p = p.copy(availabilityNotes = it); changes += "notes de disponibilité" }
            (input["availability"] as? JsonObject)?.let { av ->
                val map = p.availability.toMutableMap()
                av.forEach { (k, v) -> (v as? JsonPrimitive)?.intOrNull?.let { map[k.uppercase()] = it } }
                p = p.copy(availability = map)
                changes += "disponibilités"
            }
            d.copy(profile = p)
        }
        if (changes.isEmpty()) return ToolOutcome("Aucun champ reconnu.", isError = true)
        return ToolOutcome("Profil mis à jour : ${changes.joinToString()}", note = "Profil mis à jour : ${changes.joinToString()}")
    }

    private fun getActivities(input: JsonObject): ToolOutcome {
        val r = range(input, 28, 0)
        val d = repo.data
        val list = d.activities.filter { inRange(it.date, r) }.sortedBy { it.startTime }
        val sb = StringBuilder("${list.size} activités du ${r.first} au ${r.second}\n")
        for (a in list) {
            val (tss, method) = TrainingLoad.estimateTss(a, d.profile)
            sb.append("- ${a.startTime.replace('T', ' ').take(16)} ${a.sport.name} « ${a.name} » ")
            sb.append("${a.movingTimeSec / 60} min, ${round1(a.distanceM / 1000)} km, D+ ${a.elevationGainM.roundToInt()} m")
            a.avgHr?.let { sb.append(", FC moy ${it.roundToInt()}") }
            a.maxHr?.let { sb.append(" max ${it.roundToInt()}") }
            a.avgWatts?.let { sb.append(", P moy ${it.roundToInt()} W") }
            a.weightedAvgWatts?.let { sb.append(", NP ${it.roundToInt()} W") }
            a.sufferScore?.let { sb.append(", effort relatif ${it.roundToInt()}") }
            if (a.trainer) sb.append(", home trainer")
            sb.append(", TSS≈${tss.roundToInt()} (${method.name.lowercase()}) [${a.source}]\n")
        }
        return ToolOutcome(sb.toString())
    }

    private fun getFitness(input: JsonObject): ToolOutcome {
        val d = repo.data
        val days = (input.int("forecast_days") ?: 28).coerceIn(0, 120)
        val sb = StringBuilder(TrainingLoad.summary(d, today(), days))
        sb.append("\nVolumes hebdomadaires (12 dernières semaines) :\n")
        val start = today().minusWeeks(12).with(java.time.DayOfWeek.MONDAY)
        var w = start
        while (!w.isAfter(today())) {
            val end = w.plusDays(6)
            val acts = d.activities.filter { inRange(it.date, w to end) }
            if (acts.isNotEmpty()) {
                val bySport = acts.groupBy { it.sport }.entries.joinToString(", ") { (s, l) ->
                    "${s.name} ${l.sumOf { it.movingTimeSec } / 60} min/${round1(l.sumOf { it.distanceM } / 1000)} km/D+${l.sumOf { it.elevationGainM }.roundToInt()}"
                }
                val tss = acts.sumOf { TrainingLoad.estimateTss(it, d.profile).first }.roundToInt()
                sb.append("  semaine du $w : TSS $tss — $bySport\n")
            } else {
                sb.append("  semaine du $w : aucune activité\n")
            }
            w = w.plusWeeks(1)
        }
        val upcoming = d.events.filter { it.type == EventType.RACE && !LocalDate.parse(it.date).isBefore(today()) }.sortedBy { it.date }
        if (upcoming.isNotEmpty()) {
            sb.append("\nProchaines courses :\n")
            upcoming.forEach { sb.append("  ${it.date} ${it.title} (priorité ${it.priority ?: "?"})\n") }
        }
        return ToolOutcome(sb.toString())
    }

    private fun autoPlan(input: JsonObject): ToolOutcome {
        val days = (input.int("days") ?: 14).coerceIn(3, 42)
        val result = Planner.plan(repo.data, today(), days)
        repo.update { d -> d.copy(events = d.events.filterNot { it.id in result.removedIds } + result.sessions) }
        val list = result.sessions.joinToString("\n") { "- [${it.id}] ${it.date} ${it.sport.name} ${it.title} (${it.durationMin} min)" }
        return ToolOutcome(result.summary + "\nSéances créées :\n" + list, note = "Planificateur : ${result.sessions.size} séance(s) sur $days jours")
    }

    private fun getCalendar(input: JsonObject): ToolOutcome {
        val r = range(input, 7, 28)
        val d = repo.data
        val events = d.events.filter { inRange(it.date, r) }.sortedWith(compareBy({ it.date }, { it.startTime ?: "" }))
        val constraints = d.constraints.filter {
            val s = LocalDate.parse(it.startDate)
            val e = LocalDate.parse(it.endDate)
            !e.isBefore(r.first) && !s.isAfter(r.second)
        }
        val sb = StringBuilder("Calendrier du ${r.first} au ${r.second}\n")
        sb.append("Évènements (${events.size}) :\n")
        for (e in events) {
            sb.append("- [${e.id}] ${e.date}${e.startTime?.let { " $it" } ?: ""} ${e.type.name} ${e.sport.name} « ${e.title} »")
            e.durationMin?.let { sb.append(", $it min") }
            e.distanceKm?.let { sb.append(", $it km") }
            e.elevationM?.let { sb.append(", D+ ${it.roundToInt()} m") }
            e.intensity?.let { sb.append(", $it") }
            e.priority?.let { sb.append(", priorité $it") }
            sb.append(", TSS≈${TrainingLoad.estimatePlannedTss(e).roundToInt()}")
            sb.append(", statut ${e.status.name}, créé par ${e.createdBy}")
            if (e.steps.isNotEmpty()) sb.append(", ${e.steps.size} blocs")
            if (e.description.isNotBlank()) sb.append("\n    ${e.description.replace("\n", " ").take(400)}")
            sb.append('\n')
        }
        sb.append("Impératifs / impondérables (${constraints.size}) :\n")
        for (c in constraints) {
            sb.append("- [${c.id}] ${c.startDate} → ${c.endDate} ${c.kind.name} : ${c.description}")
            c.maxMinutesPerDay?.let { sb.append(" (max $it min/jour)") }
            sb.append('\n')
        }
        val p = d.profile
        if (p.availability.isNotEmpty() || p.availabilityNotes.isNotBlank()) {
            sb.append("Disponibilités habituelles : ${p.availability.entries.joinToString { "${it.key} ${it.value} min" }} ${p.availabilityNotes}\n")
        }
        return ToolOutcome(sb.toString())
    }

    private fun parseSteps(arr: JsonElement?): List<IntervalStep> = (arr as? JsonArray)?.map { el ->
        val o = el.jsonObject
        IntervalStep(
            durationSec = o.int("duration_sec") ?: throw BadInput("steps[].duration_sec requis"),
            powerLowPct = o.int("power_low_pct") ?: throw BadInput("steps[].power_low_pct requis"),
            powerHighPct = o.int("power_high_pct"),
            cadence = o.int("cadence"),
            label = o.str("label").orEmpty(),
        )
    } ?: emptyList()

    private fun createSessions(input: JsonObject): ToolOutcome {
        val arr = input["sessions"] as? JsonArray ?: throw BadInput("'sessions' doit être une liste")
        val created = arr.map { el ->
            val o = el.jsonObject
            val type = parseEnum<EventType>(o.str("type"), "type") ?: EventType.PLANNED_SESSION
            if (type != EventType.PLANNED_SESSION) throw BadInput("le coach ne crée que des séances PLANNED_SESSION")
            CalendarEvent(
                id = newId(),
                date = o.reqDate("date"),
                startTime = o.str("start_time"),
                type = type,
                sport = parseEnum<Sport>(o.str("sport"), "sport") ?: throw BadInput("'sport' requis"),
                title = o.reqStr("title"),
                description = o.str("description").orEmpty(),
                durationMin = o.int("duration_min"),
                distanceKm = o.num("distance_km"),
                elevationM = o.num("elevation_m"),
                targetTss = o.num("target_tss"),
                intensity = o.str("intensity")?.uppercase(),
                steps = parseSteps(o["steps"]),
                createdBy = "ai",
            )
        }
        repo.update { d -> d.copy(events = d.events + created) }
        val ids = created.joinToString("\n") { "- [${it.id}] ${it.date} ${it.title}" }
        return ToolOutcome("${created.size} séances créées :\n$ids", note = "${created.size} séance(s) ajoutée(s) au calendrier")
    }

    private fun updateSession(input: JsonObject): ToolOutcome {
        val id = input.reqStr("id")
        val current = repo.data.events.firstOrNull { it.id == id } ?: throw BadInput("évènement $id introuvable")
        val status = parseEnum<SessionStatus>(input.str("status"), "status")
        val onlyStatus = input.keys.all { it == "id" || it == "status" }
        if (!Planner.isAuto(current) && !onlyStatus) {
            throw BadInput("cet évènement a été saisi par l'athlète : seul son statut peut être modifié. Demande-lui de le changer.")
        }
        val updated = current.copy(
            date = input.date("date") ?: current.date,
            startTime = input.str("start_time") ?: current.startTime,
            sport = parseEnum<Sport>(input.str("sport"), "sport") ?: current.sport,
            title = input.str("title") ?: current.title,
            description = input.str("description") ?: current.description,
            durationMin = input.int("duration_min") ?: current.durationMin,
            distanceKm = input.num("distance_km") ?: current.distanceKm,
            elevationM = input.num("elevation_m") ?: current.elevationM,
            targetTss = input.num("target_tss") ?: current.targetTss,
            intensity = input.str("intensity")?.uppercase() ?: current.intensity,
            steps = if (input.containsKey("steps")) parseSteps(input["steps"]) else current.steps,
            status = status ?: current.status,
        )
        repo.update { d -> d.copy(events = d.events.map { if (it.id == id) updated else it }) }
        return ToolOutcome("Évènement $id mis à jour.", note = "Séance modifiée : ${updated.date} ${updated.title}")
    }

    private fun deleteSessions(input: JsonObject): ToolOutcome {
        val ids = (input["ids"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet()
            ?: throw BadInput("'ids' doit être une liste")
        val events = repo.data.events.filter { it.id in ids }
        val protectedOnes = events.filterNot(Planner::isAuto)
        val deletable = events.filter(Planner::isAuto).map { it.id }.toSet()
        repo.update { d -> d.copy(events = d.events.filterNot { it.id in deletable }) }
        val sb = StringBuilder("${deletable.size} séance(s) supprimée(s).")
        if (protectedOnes.isNotEmpty()) sb.append(" Non supprimés (saisis par l'athlète) : ${protectedOnes.joinToString { it.title }}.")
        val missing = ids - events.map { it.id }.toSet()
        if (missing.isNotEmpty()) sb.append(" Introuvables : ${missing.joinToString()}.")
        return ToolOutcome(sb.toString(), note = if (deletable.isEmpty()) null else "${deletable.size} séance(s) retirée(s) du calendrier")
    }

    private fun addConstraint(input: JsonObject): ToolOutcome {
        val start = input.reqDate("start_date")
        val c = Constraint(
            id = newId(),
            startDate = start,
            endDate = input.date("end_date") ?: start,
            kind = parseEnum<ConstraintKind>(input.str("kind"), "kind") ?: ConstraintKind.OTHER,
            description = input.reqStr("description"),
            maxMinutesPerDay = input.int("max_minutes_per_day"),
            createdAt = today().toString(),
        )
        if (c.endDate < c.startDate) throw BadInput("end_date avant start_date")
        repo.update { d -> d.copy(constraints = d.constraints + c) }
        return ToolOutcome("Impératif enregistré [${c.id}].", note = "Impératif ajouté : ${c.kind.label} ${c.startDate} → ${c.endDate}")
    }

    // ---- definitions ---------------------------------------------------------------------------

    companion object {
        private fun schema(value: Any?): JE = when (value) {
            null -> JsonNull
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to schema(v) })
            is List<*> -> JsonArray(value.map(::schema))
            else -> error("unsupported $value")
        }

        private fun obj(props: Map<String, Any>, required: List<String> = emptyList()): JsonObject {
            val m = mutableMapOf<String, Any>("type" to "object", "properties" to props)
            if (required.isNotEmpty()) m["required"] = required
            return schema(m) as JsonObject
        }

        private fun s(desc: String) = mapOf("type" to "string", "description" to desc)
        private fun n(desc: String) = mapOf("type" to "number", "description" to desc)
        private fun i(desc: String) = mapOf("type" to "integer", "description" to desc)
        private fun e(desc: String, values: List<String>) = mapOf("type" to "string", "enum" to values, "description" to desc)

        private val dateRange = mapOf(
            "from" to s("Date de début AAAA-MM-JJ (incluse)"),
            "to" to s("Date de fin AAAA-MM-JJ (incluse)"),
        )

        private val sports = Sport.entries.map { it.name }
        private val intensities = listOf("RECOVERY", "ENDURANCE", "TEMPO", "SWEET_SPOT", "THRESHOLD", "VO2MAX", "ANAEROBIC", "RACE")

        private val stepSchema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "duration_sec" to i("Durée du bloc en secondes"),
                "power_low_pct" to i("Puissance en % de FTP (début du bloc si rampe)"),
                "power_high_pct" to i("Puissance de fin en % de FTP pour une rampe (échauffement/retour au calme)"),
                "cadence" to i("Cadence cible rpm (optionnel)"),
                "label" to s("Libellé court"),
            ),
            "required" to listOf("duration_sec", "power_low_pct"),
        )

        private val sessionFields = mapOf(
            "date" to s("AAAA-MM-JJ"),
            "start_time" to s("Heure HH:MM (optionnel)"),
            "sport" to e("Sport", sports),
            "title" to s("Titre court, ex. « Sortie longue trail D+ »"),
            "description" to s("Consignes détaillées : échauffement, corps de séance, zones, terrain, retour au calme"),
            "duration_min" to i("Durée prévue en minutes"),
            "distance_km" to n("Distance prévue (optionnel)"),
            "elevation_m" to n("Dénivelé positif prévu (optionnel)"),
            "intensity" to e("Intensité dominante", intensities),
            "target_tss" to n("TSS estimé (optionnel, sinon calculé)"),
            "steps" to mapOf(
                "type" to "array",
                "items" to stepSchema,
                "description" to "Blocs structurés en % FTP, obligatoires pour INDOOR_BIKE (export Zwift)",
            ),
        )

        private val weekDays = java.time.DayOfWeek.entries.associate { it.name to i("Minutes disponibles le ${it.name}") }

        val definitions: List<ToolDef> = listOf(
            ToolDef(
                "get_athlete_profile",
                "Profil de l'athlète : FTP (et W/kg), FC max/repos/seuil, poids, objectifs, disponibilités hebdomadaires, matériel, zones de puissance.",
                null,
            ),
            ToolDef(
                "update_athlete_profile",
                "Met à jour le profil (seulement les champs fournis).",
                obj(
                    mapOf(
                        "ftp_w" to i("FTP en watts"),
                        "max_hr" to i("FC max"),
                        "resting_hr" to i("FC de repos"),
                        "lthr" to i("FC au seuil lactique"),
                        "weight_kg" to n("Poids en kg"),
                        "goals" to s("Objectifs de la saison (texte complet, remplace l'existant)"),
                        "availability" to mapOf("type" to "object", "properties" to weekDays, "description" to "Minutes disponibles par jour"),
                        "availability_notes" to s("Précisions sur les disponibilités"),
                    ),
                ),
            ),
            ToolDef(
                "get_activities",
                "Activités réalisées (Strava, y compris COROS et Zwift) avec durée, distance, D+, FC, puissance et TSS estimé. Par défaut les 28 derniers jours.",
                obj(dateRange),
            ),
            ToolDef(
                "get_fitness",
                "Charge d'entraînement : CTL/ATL/TSB du jour, évolution, prévision jour par jour d'après le calendrier, volumes hebdomadaires des 12 dernières semaines et prochaines courses.",
                obj(mapOf("forecast_days" to i("Nombre de jours de prévision (défaut 28, max 120)"))),
            ),
            ToolDef(
                "get_analysis",
                "Bilan automatique complet : charge, volumes, puissance, prévisions pour les courses et recommandations calculées.",
                null,
            ),
            ToolDef(
                "get_calendar",
                "Calendrier : courses, entraînements club, séances planifiées (id, statut, auteur user/planner/ai) et impératifs. Par défaut de J-7 à J+28.",
                obj(dateRange),
            ),
            ToolDef(
                "auto_plan",
                "Lance le planificateur intégré à partir d'aujourd'hui : remplace les séances automatiques non réalisées de la période par un nouveau plan.",
                obj(mapOf("days" to i("Nombre de jours à planifier (défaut 14, max 42)"))),
            ),
            ToolDef(
                "create_sessions",
                "Ajoute des séances planifiées au calendrier (plusieurs à la fois). Vérifie d'abord le calendrier pour éviter les doublons.",
                obj(
                    mapOf(
                        "sessions" to mapOf(
                            "type" to "array",
                            "items" to mapOf("type" to "object", "properties" to sessionFields, "required" to listOf("date", "sport", "title", "duration_min")),
                        ),
                    ),
                    listOf("sessions"),
                ),
            ),
            ToolDef(
                "update_session",
                "Modifie une séance automatique (date, contenu…). Pour les évènements saisis par l'athlète seul le statut (DONE/SKIPPED/PLANNED) peut changer.",
                obj(sessionFields + mapOf("id" to s("Identifiant de l'évènement"), "status" to e("Statut", SessionStatus.entries.map { it.name })), listOf("id")),
            ),
            ToolDef(
                "delete_sessions",
                "Supprime des séances automatiques (les évènements de l'athlète sont protégés).",
                obj(mapOf("ids" to mapOf("type" to "array", "items" to mapOf("type" to "string"))), listOf("ids")),
            ),
            ToolDef(
                "add_constraint",
                "Enregistre un impératif ou impondérable signalé par l'athlète (indisponibilité, temps limité, fatigue, maladie, blessure, déplacement…).",
                obj(
                    mapOf(
                        "start_date" to s("AAAA-MM-JJ"),
                        "end_date" to s("AAAA-MM-JJ (défaut = start_date)"),
                        "kind" to e("Type", ConstraintKind.entries.map { it.name }),
                        "description" to s("Description"),
                        "max_minutes_per_day" to i("Temps d'entraînement maximum par jour pendant la période (optionnel)"),
                    ),
                    listOf("start_date", "kind", "description"),
                ),
            ),
            ToolDef(
                "sync_strava",
                "Récupère les dernières activités Strava (si l'athlète dit avoir fait une séance qui n'apparaît pas encore).",
                null,
            ),
        )
    }
}
