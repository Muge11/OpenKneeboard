package fr.coachia.app

import android.app.Application
import fr.coachia.core.AppRepository
import fr.coachia.core.ChatTranscript
import fr.coachia.core.GeminiClient
import fr.coachia.core.GeminiCoach
import fr.coachia.core.CoachTools
import fr.coachia.core.StravaClient
import fr.coachia.core.StravaSync
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class CoachApp : Application() {
    lateinit var repo: AppRepository
        private set
    lateinit var transcript: ChatTranscript
        private set
    lateinit var strava: StravaClient
        private set
    lateinit var stravaSync: StravaSync
        private set
    lateinit var gemini: GeminiClient
        private set
    lateinit var coach: GeminiCoach
        private set

    override fun onCreate() {
        super.onCreate()
        val codec = KeystoreCodec()
        val dir = File(filesDir, "coach").apply { mkdirs() }
        repo = AppRepository(File(dir, "data.bin"), codec)
        transcript = ChatTranscript(dir, codec)
        strava = StravaClient(OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build())
        stravaSync = StravaSync(repo, strava)
        gemini = GeminiClient(OkHttpClient.Builder().callTimeout(5, TimeUnit.MINUTES).readTimeout(5, TimeUnit.MINUTES).build())
        coach = GeminiCoach(repo, transcript, CoachTools(repo, { stravaSync.sync() }), gemini)
    }
}
