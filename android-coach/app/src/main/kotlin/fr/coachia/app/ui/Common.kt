package fr.coachia.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import fr.coachia.core.EventType
import fr.coachia.core.SessionStatus
import fr.coachia.core.CalendarEvent
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

val FR: Locale = Locale.FRENCH
val dayTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)
val monthTitle: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", FR)
val shortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", FR)

fun String.capitalized() = replaceFirstChar { it.titlecase(FR) }

fun LocalDate.pretty(): String = format(dayTitle).capitalized()

fun formatDuration(min: Int): String = if (min >= 60) "%dh%02d".format(min / 60, min % 60) else "$min min"

fun eventColor(e: CalendarEvent) = when {
    e.status == SessionStatus.DONE -> CoachColors.Done
    e.type == EventType.RACE -> CoachColors.Race
    e.type == EventType.CLUB_TRAINING -> CoachColors.Club
    else -> CoachColors.Planned
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(optionLabel(it)) }, onClick = { onSelect(it); expanded = false })
            }
        }
    }
}

@Composable
fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, decimal: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() || (decimal && (it == '.' || it == ',')) }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

fun String.toDoubleLenient(): Double? = replace(',', '.').toDoubleOrNull()
