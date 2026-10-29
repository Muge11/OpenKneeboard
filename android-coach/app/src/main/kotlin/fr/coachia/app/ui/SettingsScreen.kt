package fr.coachia.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.coachia.app.MainViewModel
import fr.coachia.core.Settings
import java.time.DayOfWeek
import java.time.format.TextStyle


@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val s = data.settings
    val p = data.profile

    val models by vm.models.collectAsStateWithLifecycle()
    var apiKey by remember(s) { mutableStateOf(s.geminiApiKey) }
    var model by remember(s) { mutableStateOf(s.geminiModel) }
    var clientId by remember(s) { mutableStateOf(s.stravaClientId) }
    var clientSecret by remember(s) { mutableStateOf(s.stravaClientSecret) }

    var name by remember(p) { mutableStateOf(p.name) }
    var birthYear by remember(p) { mutableStateOf(p.birthYear?.toString().orEmpty()) }
    var sex by remember(p) { mutableStateOf(p.sex ?: "") }
    var height by remember(p) { mutableStateOf(p.heightCm?.toString().orEmpty()) }
    var weight by remember(p) { mutableStateOf(p.weightKg?.toString().orEmpty()) }
    var ftp by remember(p) { mutableStateOf(p.ftpW?.toString().orEmpty()) }
    var maxHr by remember(p) { mutableStateOf(p.maxHr?.toString().orEmpty()) }
    var restHr by remember(p) { mutableStateOf(p.restingHr?.toString().orEmpty()) }
    var lthr by remember(p) { mutableStateOf(p.lthr?.toString().orEmpty()) }
    var goals by remember(p) { mutableStateOf(p.goals) }
    var equipment by remember(p) { mutableStateOf(p.equipment) }
    var availNotes by remember(p) { mutableStateOf(p.availabilityNotes) }
    val avail = remember(p) {
        DayOfWeek.entries.associate { it.name to mutableStateOf(p.availability[it.name]?.toString().orEmpty()) }
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.titleLarge)

        Section("Discussion IA — Gemini (gratuit, optionnel)") {
            Text(
                "Le planificateur et le bilan sont intégrés et gratuits. Pour discuter avec le coach, crée une clé gratuite sur " +
                    "aistudio.google.com/apikey (compte Google, sans carte bancaire) et colle-la ici. L'offre gratuite a des quotas " +
                    "par minute et par jour ; en offre gratuite, Google peut utiliser les échanges pour améliorer ses modèles.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                apiKey, { apiKey = it.trim() }, label = { Text("Clé API Gemini") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
            val options = (listOf(Settings.DEFAULT_MODEL, model) + models).distinct()
            Dropdown("Modèle", options, model, { if (it == Settings.DEFAULT_MODEL) "$it (recommandé)" else it }, { model = it }, Modifier.fillMaxWidth())
            OutlinedButton(onClick = { vm.loadModels(apiKey) }, enabled = apiKey.isNotBlank()) { Text("Charger les modèles disponibles") }
        }

        Section("Strava") {
            if (data.strava != null) {
                Text("Connecté : ${data.strava?.athleteName?.ifBlank { "compte Strava" }}", style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                "1. Sur strava.com/settings/api, crée une application (nom libre, site web libre) avec « Authorization Callback Domain » = localhost.\n" +
                    "2. Copie ici le Client ID et le Client Secret, enregistre, puis touche « Connecter Strava ».",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(clientId, { clientId = it.trim() }, label = { Text("Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                clientSecret, { clientSecret = it.trim() }, label = { Text("Client Secret") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = { vm.saveSettings(s.copy(geminiApiKey = apiKey, geminiModel = model, stravaClientId = clientId, stravaClientSecret = clientSecret)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer les réglages") }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    vm.saveSettings(s.copy(geminiApiKey = apiKey, geminiModel = model, stravaClientId = clientId, stravaClientSecret = clientSecret))
                    vm.stravaAuthorizeIntent()?.let(context::startActivity)
                },
                modifier = Modifier.weight(1f),
            ) { Text(if (data.strava == null) "Connecter Strava" else "Reconnecter Strava") }
            if (data.strava != null) {
                OutlinedButton(onClick = vm::disconnectStrava, modifier = Modifier.weight(1f)) { Text("Déconnecter") }
            }
        }

        Section("COROS") {
            Text(
                "COROS ne propose pas d'accès direct (API) aux particuliers : tes activités COROS arrivent via Strava. " +
                    "Dans l'appli COROS, active la synchro automatique (Profil → Paramètres → Applications tierces → Strava) ; " +
                    "fais de même dans Zwift. Elles apparaissent ensuite ici à chaque synchronisation.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Section("Profil athlète") {
            OutlinedTextField(name, { name = it }, label = { Text("Prénom") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("Année naiss.", birthYear, { birthYear = it }, Modifier.weight(1f))
                Dropdown("Sexe", listOf("", "H", "F"), sex, { if (it.isEmpty()) "–" else it }, { sex = it }, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("Taille (cm)", height, { height = it }, Modifier.weight(1f), decimal = true)
                NumberField("Poids (kg)", weight, { weight = it }, Modifier.weight(1f), decimal = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("FTP (W)", ftp, { ftp = it }, Modifier.weight(1f))
                NumberField("FC max", maxHr, { maxHr = it }, Modifier.weight(1f))
            }
            if (p.stravaFtpW != null) {
                Text("FTP lue sur Strava : ${p.stravaFtpW} W (utilisée si le champ FTP est vide).", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NumberField("FC repos", restHr, { restHr = it }, Modifier.weight(1f))
                NumberField("FC seuil", lthr, { lthr = it }, Modifier.weight(1f))
            }
            OutlinedTextField(
                goals, { goals = it }, label = { Text("Objectifs de la saison") }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex. : finir l'Ultra des Coursières en 7 h, top 20 en VTT XC régional…") },
            )
            OutlinedTextField(equipment, { equipment = it }, label = { Text("Matériel") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        }

        Section("Disponibilités habituelles (minutes par jour)") {
            DayOfWeek.entries.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { d ->
                        val state = avail.getValue(d.name)
                        NumberField(d.getDisplayName(TextStyle.SHORT, FR).capitalized(), state.value, { state.value = it }, Modifier.weight(1f))
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            OutlinedTextField(
                availNotes, { availNotes = it }, label = { Text("Précisions") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ex. : sortie longue le dimanche matin, home trainer en semaine après 19 h…") },
            )
        }

        Button(
            onClick = {
                vm.saveProfile(
                    p.copy(
                        name = name.trim(), birthYear = birthYear.toIntOrNull(), sex = sex.ifEmpty { null },
                        heightCm = height.toDoubleLenient(), weightKg = weight.toDoubleLenient(),
                        ftpW = ftp.toIntOrNull(), ftpUpdated = if (ftp.toIntOrNull() != p.ftpW) java.time.LocalDate.now().toString() else p.ftpUpdated,
                        maxHr = maxHr.toIntOrNull(), restingHr = restHr.toIntOrNull(), lthr = lthr.toIntOrNull(),
                        goals = goals.trim(), equipment = equipment.trim(), availabilityNotes = availNotes.trim(),
                        availability = avail.mapNotNull { (k, v) -> v.value.toIntOrNull()?.let { k to it } }.toMap(),
                    ),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer le profil") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
