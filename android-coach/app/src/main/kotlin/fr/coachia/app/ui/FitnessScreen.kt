package fr.coachia.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.coachia.app.MainViewModel
import fr.coachia.core.EventType
import fr.coachia.core.TrainingLoad
import fr.coachia.core.WellnessEntry
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val chartDate = DateTimeFormatter.ofPattern("d MMM", FR)

@Composable
fun FitnessScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsStateWithLifecycle()
    val busy by vm.coachBusy.collectAsStateWithLifecycle()
    var historyDays by rememberSaveable { mutableIntStateOf(90) }
    val today = LocalDate.now()
    val forecastDays = 28
    val series = remember(data, historyDays) { TrainingLoad.series(data, today, today.plusDays(forecastDays.toLong())) }
    val window = series.filter { !it.date.isBefore(today.minusDays(historyDays.toLong())) }
    val now = series.lastOrNull { !it.forecast }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MainViewModel.MAX_IMAGES)) { uris ->
        if (uris.isNotEmpty()) {
            vm.askCoach(
                "Voici des captures d'écran (Renpho, COROS ou autre). Lis toutes les valeurs, enregistre-les avec la bonne date et la bonne source, " +
                    "puis commente l'évolution par rapport à mes données précédentes.",
                uris,
            )
        }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Forme & santé", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tile("Forme (CTL)", now?.ctl?.roundToInt()?.toString() ?: "–", Modifier.weight(1f))
            Tile("Fatigue (ATL)", now?.atl?.roundToInt()?.toString() ?: "–", Modifier.weight(1f))
            Tile("Fraîcheur (TSB)", now?.tsb?.roundToInt()?.toString() ?: "–", Modifier.weight(1f))
        }
        now?.let { Text("État : ${TrainingLoad.describeForm(it.tsb)}", style = MaterialTheme.typography.bodyMedium) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val p = data.profile
            val weight = p.weightKg ?: data.wellness.filter { it.weightKg != null }.maxByOrNull { it.date }?.weightKg
            Tile("FTP", p.effectiveFtp?.let { "$it W" } ?: "–", Modifier.weight(1f), if (p.ftpW == null && p.stravaFtpW != null) "Strava" else p.ftpUpdated)
            Tile("W/kg", if (p.effectiveFtp != null && weight != null) "%.2f".format(p.effectiveFtp!! / weight) else "–", Modifier.weight(1f))
            Tile("Poids", weight?.let { "%.1f kg".format(it) } ?: "–", Modifier.weight(1f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.askCoach(quickPrompts[0].second) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Star, null)
                Spacer(Modifier.width(6.dp))
                Text("Analyse IA complète")
            }
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = !busy,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("Capture Renpho/COROS")
            }
        }

        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Charge d'entraînement et prévision", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(42, 90, 180, 365).forEach { d ->
                        FilterChip(selected = historyDays == d, onClick = { historyDays = d }, label = { Text("$d j") })
                    }
                }
                val firstForecast = window.indexOfFirst { it.forecast }.takeIf { it >= 0 }
                LineChart(
                    series = listOf(
                        Series("CTL", CoachColors.Ctl, window.map { it.ctl }),
                        Series("ATL", CoachColors.Atl, window.map { it.atl }),
                        Series("TSB", CoachColors.Tsb, window.map { it.tsb }),
                    ),
                    forecastFrom = firstForecast?.let { it - 1 },
                    xLabels = window.firstOrNull()?.let { it.date.format(chartDate) to window.last().date.format(chartDate) },
                    zeroLine = true,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    "Traits pleins : réalisé. Pointillés : prévision à $forecastDays jours d'après les séances du calendrier.",
                    style = MaterialTheme.typography.bodySmall,
                )
                val races = data.events.filter { it.type == EventType.RACE && !LocalDate.parse(it.date).isBefore(today) }.sortedBy { it.date }
                races.take(3).forEach { r ->
                    val day = series.firstOrNull { it.date.toString() == r.date }
                    val txt = day?.let { "CTL ${it.ctl.roundToInt()}, TSB ${it.tsb.roundToInt()} (${TrainingLoad.describeForm(it.tsb)})" }
                        ?: "au-delà de la prévision"
                    Text("🏁 ${r.title} (${LocalDate.parse(r.date).format(shortDate)}) : $txt", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        val weights = data.wellness.filter { it.weightKg != null }.sortedBy { it.date }
        Card {
            Column(Modifier.padding(12.dp)) {
                Text("Poids et composition", style = MaterialTheme.typography.titleMedium)
                if (weights.size >= 2) {
                    LineChart(
                        series = listOf(Series("Poids (kg)", CoachColors.Planned, weights.map { it.weightKg })),
                        xLabels = LocalDate.parse(weights.first().date).format(chartDate) to LocalDate.parse(weights.last().date).format(chartDate),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    val fat = weights.filter { it.bodyFatPct != null }
                    if (fat.size >= 2) {
                        LineChart(
                            series = listOf(Series("Masse grasse (%)", CoachColors.Atl, fat.map { it.bodyFatPct })),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    Text(
                        "Envoie des captures de l'appli Renpho (bouton ci-dessus ou « Partager » depuis Renpho) : le coach lit les valeurs et les enregistre.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        Text("Dernières mesures", style = MaterialTheme.typography.titleMedium)
        val recent = data.wellness.sortedByDescending { it.date }.take(15)
        if (recent.isEmpty()) Text("Aucune mesure enregistrée.", style = MaterialTheme.typography.bodySmall)
        recent.forEach { w -> WellnessCard(w) { vm.deleteWellness(w.id) } }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Tile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(value, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Start)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun WellnessCard(w: WellnessEntry, onDelete: () -> Unit) {
    val parts = buildList {
        w.weightKg?.let { add("Poids $it kg") }
        w.bodyFatPct?.let { add("MG $it %") }
        w.muscleMassKg?.let { add("Muscle $it kg") }
        w.skeletalMusclePct?.let { add("Muscle squel. $it %") }
        w.bodyWaterPct?.let { add("Eau $it %") }
        w.boneMassKg?.let { add("Os $it kg") }
        w.proteinPct?.let { add("Protéines $it %") }
        w.visceralFat?.let { add("Viscérale $it") }
        w.bmrKcal?.let { add("BMR ${it.roundToInt()} kcal") }
        w.metabolicAge?.let { add("Âge métab. $it") }
        w.restingHr?.let { add("FC repos ${it.roundToInt()}") }
        w.hrvMs?.let { add("VFC ${it.roundToInt()} ms") }
        w.sleepHours?.let { add("Sommeil $it h") }
        w.sleepScore?.let { add("Score sommeil ${it.roundToInt()}") }
        w.recoveryPct?.let { add("Récup ${it.roundToInt()} %") }
        w.vo2max?.let { add("VO2max $it") }
        w.stress?.let { add("Stress ${it.roundToInt()}") }
        w.trainingLoad?.let { add("Charge ${it.roundToInt()}") }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("${LocalDate.parse(w.date).format(shortDate)} · ${w.source}", style = MaterialTheme.typography.labelLarge)
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
            if (w.notes.isNotBlank()) Text(w.notes, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onDelete) { Text("Supprimer") }
        }
    }
}
