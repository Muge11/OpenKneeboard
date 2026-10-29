package fr.coachia.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.coachia.app.MainViewModel
import fr.coachia.app.Tab
import fr.coachia.core.ChatEntry

val quickPrompts = listOf(
    "Analyse complète de ma forme" to
        "Fais une analyse complète de mon état de forme : charge d'entraînement (CTL/ATL/TSB), volumes récents par sport, " +
        "FTP et W/kg. Donne tes prévisions pour les prochaines semaines et pour mes prochaines courses, les points de vigilance et tes recommandations.",
    "Planifier mes 2 prochaines semaines" to
        "Planifie mes séances des 14 prochains jours (lance le planificateur puis ajuste-le) en tenant compte de mes courses, " +
        "entraînements club, impératifs, disponibilités et de ma forme actuelle, puis résume le plan.",
    "Ma forme le jour de ma prochaine course" to
        "Quelle sera ma forme prévue le jour de ma prochaine course ? Le plan actuel est-il adapté ? Propose des ajustements si besoin.",
    "Je suis fatigué aujourd'hui" to
        "Je me sens fatigué aujourd'hui. Regarde mes dernières données et adapte les séances des prochains jours si nécessaire.",
    "Ma FTP est-elle à jour ?" to
        "Regarde mes sorties vélo récentes avec puissance : ma FTP te semble-t-elle à jour ? Faut-il programmer un test sur Zwift ?",
)

@Composable
fun CoachScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val chat by vm.chat.collectAsStateWithLifecycle()
    val busy by vm.coachBusy.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(chat.size, busy) {
        if (chat.isNotEmpty()) listState.animateScrollToItem(chat.size - 1 + if (busy) 1 else 0)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Coach IA", style = MaterialTheme.typography.titleLarge)
                Text("Gemini · ${data.settings.geminiModel}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = { confirmReset = true }, enabled = !busy && chat.isNotEmpty()) {
                Icon(Icons.Filled.Refresh, "Nouvelle conversation")
            }
        }

        if (data.settings.geminiApiKey.isBlank()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("Discussion IA (optionnelle et gratuite)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Pour discuter avec le coach, ajoute une clé gratuite Google AI Studio dans les Réglages. " +
                            "Sans clé, le planificateur (onglet Calendrier) et le bilan (onglet Forme) fonctionnent quand même.",
                    )
                    TextButton(onClick = { vm.selectTab(Tab.SETTINGS) }) { Text("Ouvrir les réglages") }
                }
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (chat.isEmpty()) {
                item {
                    Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Pose-moi tes questions sur ton entraînement, demande-moi une analyse, un plan, " +
                                "ou signale-moi un imprévu (fatigue, déplacement…) : j'adapte ton calendrier.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        quickPrompts.forEach { (label, prompt) ->
                            SuggestionChip(onClick = { vm.askCoach(prompt) }, label = { Text(label) }, enabled = !busy && vm.hasAi)
                        }
                    }
                }
            }
            itemsIndexed(chat) { _, entry -> ChatBubble(entry) }
            if (busy) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text("Le coach analyse…", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                    }
                }
            }
        }

        if (chat.isNotEmpty() && !busy && input.isEmpty() && vm.hasAi) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                quickPrompts.forEach { (label, prompt) -> SuggestionChip(onClick = { vm.askCoach(prompt) }, label = { Text(label) }) }
            }
        }

        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message au coach…") },
                enabled = vm.hasAi,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                maxLines = 5,
            )
            IconButton(
                onClick = { vm.askCoach(input.trim()); input = "" },
                enabled = !busy && input.isNotBlank() && vm.hasAi,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Envoyer") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Nouvelle conversation ?") },
            text = { Text("L'historique de la discussion sera effacé. Ton calendrier, tes activités et tes données de santé sont conservés.") },
            confirmButton = { TextButton(onClick = { vm.newConversation(); confirmReset = false }) { Text("Effacer") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ChatBubble(entry: ChatEntry) {
    val scheme = MaterialTheme.colorScheme
    when (entry.role) {
        "user" -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(color = scheme.primaryContainer, shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(entry.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        "tool" -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), tint = CoachColors.Done)
            Spacer(Modifier.size(4.dp))
            Text(entry.text, style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        }
        else -> Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp), modifier = Modifier.fillMaxWidth()) {
            SelectionContainer { MarkdownText(entry.text, Modifier.padding(12.dp)) }
        }
    }
}
