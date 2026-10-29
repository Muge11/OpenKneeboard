package fr.coachia.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.coachia.app.MainViewModel
import fr.coachia.app.Tab
import fr.coachia.core.TrainingLoad
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val activityDate = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", FR)

@Composable
fun ActivitiesScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsStateWithLifecycle()
    val syncing by vm.syncing.collectAsStateWithLifecycle()

    LazyColumn(modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Activités", style = MaterialTheme.typography.titleLarge)
                    val last = data.lastStravaSync?.let {
                        LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneId.systemDefault()).format(activityDate)
                    }
                    Text(
                        when {
                            data.strava == null -> "Strava non connecté"
                            last == null -> "Jamais synchronisé"
                            else -> "Dernière synchro : $last"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (data.strava != null) {
                    Button(onClick = vm::syncStrava, enabled = !syncing) {
                        if (syncing) CircularProgressIndicator(Modifier.width(18.dp), strokeWidth = 2.dp) else Icon(Icons.Filled.Refresh, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Synchroniser")
                    }
                } else {
                    TextButton(onClick = { vm.selectTab(Tab.SETTINGS) }) { Text("Connecter Strava") }
                }
            }
        }
        if (data.activities.isEmpty()) {
            item {
                Text(
                    "Aucune activité. Connecte Strava dans les Réglages ; pense à activer dans l'appli COROS la synchronisation automatique vers Strava " +
                        "(Profil → Paramètres → Applications tierces → Strava) ainsi que celle de Zwift.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(data.activities, key = { it.id }) { a ->
            val (tss, method) = TrainingLoad.estimateTss(a, data.profile)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(a.name.ifBlank { a.sport.label }, style = MaterialTheme.typography.titleSmall)
                    val start = runCatching { LocalDateTime.parse(a.startTime).format(activityDate) }.getOrDefault(a.startTime)
                    Text("${a.sport.label} · $start", style = MaterialTheme.typography.labelMedium)
                    val parts = buildList {
                        add(formatDuration(a.movingTimeSec / 60))
                        if (a.distanceM > 0) add("%.1f km".format(a.distanceM / 1000))
                        if (a.elevationGainM > 0) add("D+ ${a.elevationGainM.roundToInt()} m")
                        a.avgHr?.let { add("FC ${it.roundToInt()}") }
                        (a.weightedAvgWatts ?: a.avgWatts)?.let { add("${it.roundToInt()} W") }
                        add("TSS ≈ ${tss.roundToInt()}" + if (method == TrainingLoad.Method.DURATION) " (estimé)" else "")
                    }
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
