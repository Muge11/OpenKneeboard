package fr.coachia.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CoreTest {
    private val dir: File = Files.createTempDirectory("coach").toFile()
    private val today = LocalDate.parse("2026-10-29")

    @AfterTest
    fun cleanup() {
        dir.deleteRecursively()
    }

    private fun repo() = AppRepository(File(dir, "data.json"))

    private fun json(s: String) = AppJson.parseToJsonElement(s).jsonObject

    @Test
    fun repositoryPersists() {
        val r = repo()
        r.update { it.copy(profile = it.profile.copy(ftpW = 250)) }
        assertEquals(250, repo().data.profile.ftpW)
    }

    @Test
    fun tssFromPowerAndHeartRate() {
        val p = AthleteProfile(ftpW = 250, maxHr = 190)
        val ride = Activity("1", "T", Sport.MTB, "XC", "2026-10-28T10:00:00", 3600, weightedAvgWatts = 250.0)
        assertEquals(100.0, TrainingLoad.estimateTss(ride, p).first, 0.01)
        val run = Activity("2", "T", Sport.TRAIL, "Trail", "2026-10-28T10:00:00", 7200, avgHr = 190 * 0.89 * 0.8)
        val (tss, method) = TrainingLoad.estimateTss(run, p)
        assertEquals(TrainingLoad.Method.HEART_RATE, method)
        assertEquals(128.0, tss, 0.5)
    }

    @Test
    fun forecastUsesPlannedSessions() {
        val data = AppData(
            activities = (0 until 30).map {
                Activity("a$it", "T", Sport.RUN, "r", today.minusDays(it.toLong()).toString() + "T08:00:00", 3600, sufferScore = 60.0)
            },
            events = listOf(CalendarEvent("e", today.plusDays(2).toString(), type = EventType.PLANNED_SESSION, title = "x", targetTss = 200.0)),
        )
        val s = TrainingLoad.series(data, today, today.plusDays(5))
        val planned = s.first { it.date == today.plusDays(2) }
        assertTrue(planned.forecast)
        assertEquals(200.0, planned.tss)
        assertTrue(s.first { it.date == today.plusDays(3) }.atl > s.first { it.date == today.plusDays(1) }.atl)
    }

    @Test
    fun toolsProtectUserEvents() {
        val r = repo()
        r.update { it.copy(events = listOf(CalendarEvent("race", "2026-11-15", type = EventType.RACE, title = "Trail des Crêtes", priority = "A"))) }
        val tools = CoachTools(r, { "ok" }, { today })
        val created = tools.execute(
            "create_sessions",
            json("""{"sessions":[{"date":"2026-10-30","sport":"INDOOR_BIKE","title":"PMA","duration_min":60,
                "steps":[{"duration_sec":600,"power_low_pct":50,"power_high_pct":75},{"duration_sec":300,"power_low_pct":110}]}]}"""),
        )
        assertTrue(!created.isError, created.content)
        val aiId = r.data.events.first { it.createdBy == "ai" }.id
        val del = tools.execute("delete_sessions", json("""{"ids":["race","$aiId"]}"""))
        assertTrue(del.content.contains("Non supprimés"))
        assertEquals(listOf("race"), r.data.events.map { it.id })
        assertTrue(tools.execute("update_session", json("""{"id":"race","date":"2026-11-16"}""")).isError)

        assertTrue(tools.execute("create_sessions", json("""{"sessions":[{"date":"demain","sport":"RUN","title":"x"}]}""")).isError)
    }

    @Test
    fun zwoExport() {
        val e = CalendarEvent(
            "z", "2026-10-30", type = EventType.PLANNED_SESSION, sport = Sport.INDOOR_BIKE, title = "Seuil <2x20>",
            steps = listOf(IntervalStep(600, 50, 75), IntervalStep(1200, 95), IntervalStep(300, 55), IntervalStep(600, 70, 45)),
        )
        val zwo = Zwift.toZwo(e)
        assertTrue(zwo.contains("<Warmup Duration=\"600\" PowerLow=\"0.50\" PowerHigh=\"0.75\"/>"), zwo)
        assertTrue(zwo.contains("<SteadyState Duration=\"1200\" Power=\"0.95\"/>"))
        assertTrue(zwo.contains("<Cooldown"))
        assertTrue(zwo.contains("Seuil &lt;2x20&gt;"))
    }

    @Test
    fun stravaSyncMapsActivitiesAndCompletesSessions() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody("""{"access_token":"new","refresh_token":"r2","expires_at":9999999999}"""))
        server.enqueue(
            MockResponse().setBody(
                """[{"id":42,"name":"Trail du matin","sport_type":"TrailRun","start_date_local":"2026-10-29T07:00:00Z",
                "moving_time":5400,"distance":15000,"total_elevation_gain":800,"average_heartrate":150,"trainer":false},
                {"id":43,"name":"Zwift","sport_type":"VirtualRide","start_date_local":"2026-10-28T18:00:00Z","moving_time":3600,
                "weighted_average_watts":230,"trainer":true}]""",
            ),
        )
        server.enqueue(MockResponse().setBody("""{"id":7,"firstname":"Guillaume","ftp":265,"weight":70.5}"""))
        server.start()
        try {
            val r = repo()
            r.update {
                it.copy(
                    settings = Settings(stravaClientId = "1", stravaClientSecret = "s"),
                    strava = StravaAuth("old", "r1", expiresAt = 0),
                    events = listOf(CalendarEvent("p", "2026-10-29", type = EventType.PLANNED_SESSION, sport = Sport.RUN, title = "Footing")),
                )
            }
            val msg = StravaSync(r, StravaClient(OkHttpClient(), server.url("/").toString())).sync(nowEpochSec = 1_800_000_000)
            assertTrue(msg.contains("2 nouvelles"), msg)
            assertEquals("new", r.data.strava?.accessToken)
            assertEquals(Sport.TRAIL, r.data.activities.first { it.id == "strava-42" }.sport)
            assertEquals(Sport.INDOOR_BIKE, r.data.activities.first { it.id == "strava-43" }.sport)
            assertEquals(265, r.data.profile.stravaFtpW)
            assertEquals(SessionStatus.DONE, r.data.events.single().status)
            assertTrue(server.takeRequest().body.readUtf8().contains("grant_type=refresh_token"))
            assertEquals("Bearer new", server.takeRequest().getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    private fun history(days: Int, tss: Double = 60.0) = (0 until days).map {
        Activity("a$it", "T", Sport.TRAIL, "r", today.minusDays(it.toLong() + 1).toString() + "T08:00:00", 3600, sufferScore = tss)
    }

    @Test
    fun plannerRespectsConstraintsRacesAndRestDays() {
        val race = CalendarEvent("race", "2026-11-08", type = EventType.RACE, sport = Sport.TRAIL, title = "Trail A", priority = "A")
        val club = CalendarEvent("club", "2026-10-31", type = EventType.CLUB_TRAINING, sport = Sport.MTB, title = "Club")
        val oldAuto = CalendarEvent("old", "2026-11-02", type = EventType.PLANNED_SESSION, title = "ancienne", createdBy = "planner")
        val data = AppData(
            profile = AthleteProfile(ftpW = 250, maxHr = 188),
            activities = history(60),
            events = listOf(race, club, oldAuto),
            constraints = listOf(Constraint("c", "2026-11-03", "2026-11-04", ConstraintKind.UNAVAILABLE, "déplacement pro")),
        )
        val r = Planner.plan(data, today, 14)
        val dates = r.sessions.map { it.date }
        assertEquals(setOf("old"), r.removedIds)
        assertTrue("2026-10-31" !in dates, "pas de séance le jour du club")
        assertTrue("2026-11-03" !in dates && "2026-11-04" !in dates, "indisponibilité respectée")
        assertTrue("2026-11-08" !in dates && "2026-11-09" !in dates, "course et lendemain libres")
        assertTrue(r.sessions.any { it.date == "2026-11-07" && it.title.contains("déblocage") }, "veille de course")
        assertEquals(dates.size, dates.toSet().size, "une séance max par jour")
        assertTrue(r.sessions.all { it.createdBy == Planner.AUTHOR && it.durationMin != null })
        assertTrue(r.sessions.filter { it.sport == Sport.INDOOR_BIKE }.all { it.steps.isNotEmpty() })
        // Semaine pleine (lun. 9 nov. -> dim. 15 nov.) : au moins un jour sans séance.
        val week2 = (9..15).map { "2026-11-%02d".format(it) }
        assertTrue(week2.any { it !in dates })
        // Pas de séances dures consécutives.
        val hard = r.sessions.filter { it.intensity in listOf("VO2MAX", "THRESHOLD") }.map { LocalDate.parse(it.date) }.sorted()
        hard.zipWithNext().forEach { (a, b) -> assertTrue(b.toEpochDay() - a.toEpochDay() > 1, "$a et $b consécutifs") }
    }

    @Test
    fun plannerAppliesThroughTool() {
        val r = repo()
        r.update { AppData(activities = history(30)) }
        val out = CoachTools(r, { "ok" }, { today }).execute("auto_plan", json("""{"days":7}"""))
        assertTrue(!out.isError, out.content)
        assertTrue(r.data.events.isNotEmpty())
        val again = CoachTools(r, { "ok" }, { today }).execute("auto_plan", json("""{"days":7}"""))
        assertTrue(!again.isError)
        assertEquals(r.data.events.size, r.data.events.map { it.date }.toSet().size, "re-planifier remplace au lieu de dupliquer")
    }

    @Test
    fun analysisReport() {
        val data = AppData(
            profile = AthleteProfile(ftpW = 250, weightKg = 70.0),
            activities = history(40) + Activity("b", "T", Sport.MTB, "XC", "2026-10-20T10:00:00", 3600, weightedAvgWatts = 280.0),
            events = listOf(CalendarEvent("race", "2026-11-20", type = EventType.RACE, title = "XC régional", priority = "A")),
        )
        val txt = Analysis.report(data, today)
        assertTrue(txt.contains("## Charge"))
        assertTrue(txt.contains("XC régional"))
        assertTrue(txt.contains("test FTP"), txt)
    }

    @Test
    fun geminiCoachRunsToolLoopAndPersistsHistory() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setBody(
                """{"candidates":[{"content":{"role":"model","parts":[
                  {"text":"Je mets à jour ta FTP."},
                  {"functionCall":{"name":"update_athlete_profile","args":{"ftp_w":272}},"thoughtSignature":"sig123"}]},
                  "finishReason":"STOP"}]}""",
            ),
        )
        server.enqueue(
            MockResponse().setBody("""{"candidates":[{"content":{"role":"model","parts":[{"text":"FTP mise à jour à 272 W."}]},"finishReason":"STOP"}]}"""),
        )
        server.start()
        try {
            val r = repo()
            r.update { it.copy(settings = it.settings.copy(geminiApiKey = "k-test")) }
            val transcript = ChatTranscript(dir)
            val coach = GeminiCoach(r, transcript, CoachTools(r, { "ok" }, { today }), GeminiClient(OkHttpClient(), server.url("/").toString()), { today })
            coach.send("Ma FTP est maintenant de 272 W")
            assertEquals(272, r.data.profile.ftpW)
            assertEquals(listOf("user", "assistant", "tool", "assistant"), transcript.display.map { it.role })

            val first = server.takeRequest()
            assertEquals("/v1beta/models/gemini-flash-latest:generateContent", first.path)
            assertEquals("k-test", first.getHeader("x-goog-api-key"))
            val body1 = json(first.body.readUtf8())
            val decls = body1["tools"]!!.jsonArray[0].jsonObject["functionDeclarations"]!!.jsonArray
            assertEquals(CoachTools.definitions.size, decls.size)
            assertTrue(body1["systemInstruction"] != null)
            val body2 = json(server.takeRequest().body.readUtf8())
            val contents = body2["contents"]!!.jsonArray
            assertEquals(listOf("user", "model", "user"), contents.map { it.jsonObject["role"]!!.jsonPrimitive.content })
            val modelParts = contents[1].jsonObject["parts"]!!.jsonArray
            assertEquals("sig123", modelParts[1].jsonObject["thoughtSignature"]!!.jsonPrimitive.content)
            val fr = contents[2].jsonObject["parts"]!!.jsonArray[0].jsonObject["functionResponse"]!!.jsonObject
            assertEquals("update_athlete_profile", fr["name"]!!.jsonPrimitive.content)
            assertTrue(fr["response"]!!.jsonObject["result"]!!.jsonPrimitive.content.contains("FTP 272"))

            val reloaded = ChatTranscript(dir)
            assertEquals(4, reloaded.history.size)
            assertEquals(transcript.display, reloaded.display)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun geminiQuotaErrorKeepsHistoryValid() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":{"code":429,"message":"Resource exhausted"}}"""))
        server.start()
        try {
            val r = repo()
            r.update { it.copy(settings = it.settings.copy(geminiApiKey = "k")) }
            val transcript = ChatTranscript(dir)
            val coach = GeminiCoach(r, transcript, CoachTools(r, { "ok" }, { today }), GeminiClient(OkHttpClient(), server.url("/").toString()), { today })
            val e = runCatching { coach.send("salut") }.exceptionOrNull()
            assertTrue(e is CoachException && e.message!!.contains("Quota"))
            assertEquals("model", transcript.history.last()["role"]!!.jsonPrimitive.content)
        } finally {
            server.shutdown()
        }
    }
}
