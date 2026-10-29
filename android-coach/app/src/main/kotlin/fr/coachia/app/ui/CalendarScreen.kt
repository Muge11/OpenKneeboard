package fr.coachia.app.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.coachia.app.MainViewModel
import fr.coachia.core.CalendarEvent
import fr.coachia.core.Constraint
import fr.coachia.core.ConstraintKind
import fr.coachia.core.EventType
import fr.coachia.core.SessionStatus
import fr.coachia.core.Sport
import fr.coachia.core.TrainingLoad
import fr.coachia.core.newId
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

private val weekdayInitials = listOf("L", "M", "M", "J", "V", "S", "D")

@Composable
fun CalendarScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val data by vm.data.collectAsStateWithLifecycle()
    val busy by vm.coachBusy.collectAsStateWithLifecycle()
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var selected by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var adding by remember { mutableStateOf<EventType?>(null) }
    var addingConstraint by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    val ym = YearMonth.parse(month)
    val sel = LocalDate.parse(selected)
    val today = LocalDate.now()

    val eventsByDay = data.events.groupBy { it.date }
    fun constraintsOn(d: LocalDate) = data.constraints.filter {
        !d.isBefore(LocalDate.parse(it.startDate)) && !d.isAfter(LocalDate.parse(it.endDate))
    }

    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = ym.minusMonths(1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Mois précédent")
                    }
                    Text(
                        ym.atDay(1).format(monthTitle).capitalized(),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                    )
                    IconButton(onClick = { month = ym.plusMonths(1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Mois suivant")
                    }
                }
            }
            item {
                MonthGrid(
                    ym = ym, selected = sel, today = today,
                    dots = { d -> eventsByDay[d.toString()].orEmpty().map(::eventColor) },
                    blocked = { d -> constraintsOn(d).isNotEmpty() },
                    onSelect = { selected = it.toString() },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    FilledTonalButton(
                        enabled = !busy,
                        onClick = {
                            vm.askCoach(
                                "Planifie mes séances des 14 prochains jours à partir d'aujourd'hui, en tenant compte de mes courses, " +
                                    "entraînements club, impératifs, disponibilités et de ma forme actuelle. Remplace tes anciennes séances " +
                                    "non réalisées sur cette période si besoin, puis résume le plan.",
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Star, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Planifier 2 semaines avec l'IA")
                    }
                }
            }
            item {
                Text(sel.pretty(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            val dayConstraints = constraintsOn(sel)
            items(dayConstraints, key = { "c" + it.id }) { c -> ConstraintCard(c, onDelete = { vm.deleteConstraint(c.id) }) }
            val dayEvents = eventsByDay[selected].orEmpty().sortedBy { it.startTime ?: "" }
            if (dayEvents.isEmpty() && dayConstraints.isEmpty()) {
                item { Text("Rien de prévu ce jour-là.", style = MaterialTheme.typography.bodyMedium) }
            }
            items(dayEvents, key = { it.id }) { e -> EventCard(vm, e, onEdit = { editing = e }) }
            item { Spacer(Modifier.height(96.dp)) }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                onClick = { showMenu = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Ajouter") },
            )
        }
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Ajouter le ${sel.format(shortDate)}") },
            text = {
                Column {
                    listOf(EventType.RACE, EventType.CLUB_TRAINING, EventType.PLANNED_SESSION, EventType.OTHER).forEach { t ->
                        TextButton(onClick = { showMenu = false; adding = t }) { Text(t.label) }
                    }
                    TextButton(onClick = { showMenu = false; addingConstraint = true }) { Text("Impératif / impondérable") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showMenu = false }) { Text("Annuler") } },
        )
    }

    adding?.let { t ->
        EventDialog(
            initial = CalendarEvent(id = newId(), date = selected, type = t, title = "", sport = if (t == EventType.RACE) Sport.TRAIL else Sport.MTB),
            onDismiss = { adding = null },
            onSave = { vm.saveEvent(it); adding = null },
        )
    }
    editing?.let { e ->
        EventDialog(initial = e, onDismiss = { editing = null }, onSave = { vm.saveEvent(it); editing = null })
    }
    if (addingConstraint) {
        ConstraintDialog(
            date = selected,
            onDismiss = { addingConstraint = false },
            onSave = { vm.saveConstraint(it); addingConstraint = false },
        )
    }
}

@Composable
private fun MonthGrid(
    ym: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    dots: (LocalDate) -> List<Color>,
    blocked: (LocalDate) -> Boolean,
    onSelect: (LocalDate) -> Unit,
) {
    val first = ym.atDay(1)
    val offset = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val cells = offset + ym.lengthOfMonth()
    val rows = (cells + 6) / 7
    Column {
        Row(Modifier.fillMaxWidth()) {
            weekdayInitials.forEach {
                Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - offset
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (idx in 0 until ym.lengthOfMonth()) {
                            val d = first.plusDays(idx.toLong())
                            DayCell(d, d == selected, d == today, dots(d), blocked(d)) { onSelect(d) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCell(d: LocalDate, selected: Boolean, today: Boolean, dots: List<Color>, blocked: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        selected -> scheme.primaryContainer
        blocked -> CoachColors.Constraint.copy(alpha = 0.25f)
        else -> Color.Transparent
    }
    Column(
        Modifier.fillMaxSize()
            .background(bg, RoundedCornerShape(8.dp))
            .then(if (today) Modifier.border(2.dp, scheme.primary, RoundedCornerShape(8.dp)) else Modifier)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            d.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), maxItemsInEachRow = 4) {
            dots.take(4).forEach { Box(Modifier.size(6.dp).background(it, CircleShape)) }
        }
    }
}

@Composable
private fun ConstraintCard(c: Constraint, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = CoachColors.Constraint.copy(alpha = 0.18f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text("${c.kind.label} · ${c.startDate} → ${c.endDate}", style = MaterialTheme.typography.labelLarge)
            Text(c.description, style = MaterialTheme.typography.bodyMedium)
            c.maxMinutesPerDay?.let { Text("Maximum ${formatDuration(it)} / jour", style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) { Text("Supprimer") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(vm: MainViewModel, e: CalendarEvent, onEdit: () -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Row(Modifier.padding(12.dp)) {
            Box(Modifier.width(4.dp).height(48.dp).background(eventColor(e), RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleSmall)
                val meta = buildList {
                    add(e.type.label)
                    add(e.sport.label)
                    e.startTime?.let { add(it) }
                    e.durationMin?.let { add(formatDuration(it)) }
                    e.distanceKm?.let { add("${it} km") }
                    e.elevationM?.let { add("D+ ${it.roundToInt()} m") }
                    e.priority?.let { add("Priorité $it") }
                    TrainingLoad.estimatePlannedTss(e).takeIf { it > 0 }?.let { add("TSS ≈ ${it.roundToInt()}") }
                    if (e.createdBy == "ai") add("par le coach")
                    when (e.status) {
                        SessionStatus.DONE -> add("✓ réalisée")
                        SessionStatus.SKIPPED -> add("✗ non faite")
                        SessionStatus.PLANNED -> {}
                    }
                }
                Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (expanded) {
                    if (e.description.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        MarkdownText(e.description)
                    }
                    if (e.steps.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Blocs (% FTP) :", style = MaterialTheme.typography.labelLarge)
                        val ftp = vm.data.value.profile.effectiveFtp
                        e.steps.forEach { s ->
                            val pct = if (s.powerHighPct != null && s.powerHighPct != s.powerLowPct) "${s.powerLowPct}→${s.powerHighPct} %" else "${s.powerLowPct} %"
                            val watts = ftp?.let { f ->
                                val lo = f * s.powerLowPct / 100
                                val hi = s.powerHighPct?.let { f * it / 100 }
                                if (hi != null && hi != lo) " ($lo→$hi W)" else " ($lo W)"
                            } ?: ""
                            Text(
                                "• ${formatSec(s.durationSec)} à $pct$watts${s.cadence?.let { " · $it rpm" } ?: ""} ${s.label}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (e.status != SessionStatus.DONE) AssistChip(onClick = { vm.setStatus(e.id, SessionStatus.DONE) }, label = { Text("Faite") })
                        if (e.status != SessionStatus.SKIPPED) AssistChip(onClick = { vm.setStatus(e.id, SessionStatus.SKIPPED) }, label = { Text("Pas faite") })
                        if (e.status != SessionStatus.PLANNED) AssistChip(onClick = { vm.setStatus(e.id, SessionStatus.PLANNED) }, label = { Text("À faire") })
                        AssistChip(onClick = onEdit, label = { Text("Modifier") })
                        if (e.steps.isNotEmpty()) {
                            AssistChip(onClick = { context.startActivity(vm.zwiftShareIntent(e)) }, label = { Text("Export Zwift") })
                        }
                        AssistChip(
                            onClick = {
                                try {
                                    context.startActivity(vm.phoneCalendarIntent(e))
                                } catch (_: ActivityNotFoundException) {
                                    vm.toast("Aucune application d'agenda trouvée.")
                                }
                            },
                            label = { Text("Agenda du tél.") },
                        )
                        AssistChip(
                            onClick = { vm.askCoach("Peux-tu m'expliquer la séance « ${e.title} » du ${e.date} et me donner des conseils pour la réussir ?") },
                            label = { Text("Demander au coach") },
                        )
                        AssistChip(onClick = { confirmDelete = true }, label = { Text("Supprimer") })
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${e.title} » ?") },
            confirmButton = { TextButton(onClick = { vm.deleteEvent(e.id); confirmDelete = false }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

private fun formatSec(sec: Int): String = when {
    sec >= 3600 -> "%dh%02d".format(sec / 3600, sec % 3600 / 60)
    sec % 60 == 0 -> "${sec / 60} min"
    sec >= 60 -> "${sec / 60} min ${sec % 60} s"
    else -> "$sec s"
}

private fun validDate(s: String) = runCatching { LocalDate.parse(s.trim()) }.isSuccess

@Composable
private fun EventDialog(initial: CalendarEvent, onDismiss: () -> Unit, onSave: (CalendarEvent) -> Unit) {
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(initial.date) }
    var time by remember { mutableStateOf(initial.startTime.orEmpty()) }
    var sport by remember { mutableStateOf(initial.sport) }
    var type by remember { mutableStateOf(initial.type) }
    var duration by remember { mutableStateOf(initial.durationMin?.toString().orEmpty()) }
    var distance by remember { mutableStateOf(initial.distanceKm?.toString().orEmpty()) }
    var elevation by remember { mutableStateOf(initial.elevationM?.roundToInt()?.toString().orEmpty()) }
    var priority by remember { mutableStateOf(initial.priority ?: "A") }
    var description by remember { mutableStateOf(initial.description) }
    val ok = title.isNotBlank() && validDate(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.title.isEmpty()) type.label else "Modifier") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Titre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Dropdown("Type", EventType.entries.toList(), type, { it.label }, { type = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(date, { date = it }, label = { Text("Date AAAA-MM-JJ") }, singleLine = true, isError = !validDate(date), modifier = Modifier.weight(1.4f))
                    OutlinedTextField(time, { time = it }, label = { Text("Heure") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Dropdown("Sport", Sport.entries.toList(), sport, { it.label }, { sport = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NumberField("Durée (min)", duration, { duration = it }, Modifier.weight(1f))
                    NumberField("Km", distance, { distance = it }, Modifier.weight(1f), decimal = true)
                    NumberField("D+ (m)", elevation, { elevation = it }, Modifier.weight(1f))
                }
                if (type == EventType.RACE) {
                    Dropdown("Priorité", listOf("A", "B", "C"), priority, { "Course $it" }, { priority = it }, Modifier.fillMaxWidth())
                }
                OutlinedTextField(description, { description = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(
                    initial.copy(
                        title = title.trim(), date = date.trim(), startTime = time.trim().ifEmpty { null }, sport = sport, type = type,
                        durationMin = duration.toIntOrNull(), distanceKm = distance.toDoubleLenient(), elevationM = elevation.toDoubleOrNull(),
                        priority = if (type == EventType.RACE) priority else null, description = description.trim(),
                    ),
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun ConstraintDialog(date: String, onDismiss: () -> Unit, onSave: (Constraint) -> Unit) {
    var start by remember { mutableStateOf(date) }
    var end by remember { mutableStateOf(date) }
    var kind by remember { mutableStateOf(ConstraintKind.UNAVAILABLE) }
    var description by remember { mutableStateOf("") }
    var maxMin by remember { mutableStateOf("") }
    val ok = description.isNotBlank() && validDate(start) && validDate(end) && end.trim() >= start.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impératif / impondérable") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Dropdown("Type", ConstraintKind.entries.toList(), kind, { it.label }, { kind = it }, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Du") }, singleLine = true, isError = !validDate(start), modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("Au") }, singleLine = true, isError = !validDate(end), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (kind == ConstraintKind.LIMITED_TIME) {
                    NumberField("Temps max par jour (min)", maxMin, { maxMin = it }, Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ok, onClick = {
                onSave(
                    Constraint(
                        id = newId(), startDate = start.trim(), endDate = end.trim(), kind = kind, description = description.trim(),
                        maxMinutesPerDay = maxMin.toIntOrNull(), createdAt = LocalDate.now().toString(),
                    ),
                )
            }) { Text("Enregistrer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
