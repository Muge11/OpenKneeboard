package fr.coachia.core

import com.anthropic.client.okhttp.AnthropicOkHttpClient
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
    fun toolsProtectUserEventsAndMergeWellness() {
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

        tools.execute("record_wellness", json("""{"date":"2026-10-29","source":"RENPHO","weight_kg":71.4,"body_fat_pct":12.1}"""))
        tools.execute("record_wellness", json("""{"date":"2026-10-29","source":"renpho","muscle_mass_kg":58.2}"""))
        val w = r.data.wellness.single()
        assertEquals(71.4, w.weightKg)
        assertEquals(58.2, w.muscleMassKg)
        assertEquals(71.4, r.data.profile.weightKg)

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

    @Test
    fun coachRunsToolLoopAndPersistsHistory() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse().setHeader("content-type", "application/json").setBody(
                """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5",
                "content":[{"type":"thinking","thinking":"","signature":"sig"},
                  {"type":"text","text":"Je regarde ton profil."},
                  {"type":"tool_use","id":"toolu_1","name":"update_athlete_profile","input":{"ftp_w":272}}],
                "stop_reason":"tool_use","stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":10}}""",
            ),
        )
        server.enqueue(
            MockResponse().setHeader("content-type", "application/json").setBody(
                """{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5",
                "content":[{"type":"text","text":"FTP mise à jour à 272 W."}],
                "stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":10}}""",
            ),
        )
        server.start()
        try {
            val r = repo()
            r.update { it.copy(settings = it.settings.copy(anthropicApiKey = "sk-test")) }
            val transcript = ChatTranscript(dir)
            val coach = Coach(
                r, transcript, CoachTools(r, { "ok" }, { today }),
                clientFactory = { key -> AnthropicOkHttpClient.builder().apiKey(key).baseUrl(server.url("/").toString()).build() },
                today = { today },
            )
            coach.send("Ma FTP est maintenant de 272 W", listOf(ImageInput("image/jpeg", "aGVsbG8=")))
            assertEquals(272, r.data.profile.ftpW)
            assertEquals(listOf("user", "assistant", "tool", "assistant"), transcript.display.map { it.role })
            assertEquals("FTP mise à jour à 272 W.", transcript.display.last().text)

            val first = server.takeRequest()
            assertTrue(first.path!!.startsWith("/v1/messages"), first.path)
            assertTrue(first.getHeader("anthropic-beta")!!.contains(Coach.FALLBACK_BETA))
            val body1 = json(first.body.readUtf8())
            assertEquals("default", body1["fallbacks"]!!.jsonPrimitive.content)
            assertEquals("adaptive", body1["thinking"]!!.jsonObject["type"]!!.jsonPrimitive.content)
            assertEquals(CoachTools.definitions.size, body1["tools"]!!.jsonArray.size)
            val body2 = json(server.takeRequest().body.readUtf8())
            val msgs = body2["messages"]!!.jsonArray
            assertEquals(3, msgs.size)
            val assistantBlocks = msgs[1].jsonObject["content"]!!.jsonArray.map { (it as JsonObject)["type"]!!.jsonPrimitive.content }
            assertEquals(listOf("thinking", "text", "tool_use"), assistantBlocks)
            val result = msgs[2].jsonObject["content"]!!.jsonArray[0].jsonObject
            assertEquals("tool_result", result["type"]!!.jsonPrimitive.content)
            assertEquals("toolu_1", result["tool_use_id"]!!.jsonPrimitive.content)

            val reloaded = ChatTranscript(dir)
            assertEquals(4, reloaded.history.size)
            assertEquals(transcript.display, reloaded.display)
        } finally {
            server.shutdown()
        }
    }
}
