package fr.coachia.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.coachia.app.MainViewModel
import fr.coachia.app.Tab

private data class TabSpec(val tab: Tab, val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabSpec(Tab.CALENDAR, "Calendrier", Icons.Filled.DateRange),
    TabSpec(Tab.COACH, "Coach", Icons.Filled.Face),
    TabSpec(Tab.FITNESS, "Forme", Icons.Filled.Favorite),
    TabSpec(Tab.ACTIVITIES, "Activités", Icons.AutoMirrored.Filled.List),
    TabSpec(Tab.SETTINGS, "Réglages", Icons.Filled.Settings),
)

@Composable
fun MainScreen(vm: MainViewModel) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            vm.consumeMessage()
            snackbar.showSnackbar(it)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEach { spec ->
                    NavigationBarItem(
                        selected = tab == spec.tab,
                        onClick = { vm.selectTab(spec.tab) },
                        icon = { Icon(spec.icon, contentDescription = spec.label) },
                        label = { Text(spec.label) },
                    )
                }
            }
        },
    ) { padding ->
        val m = Modifier.padding(padding)
        when (tab) {
            Tab.CALENDAR -> CalendarScreen(vm, m)
            Tab.COACH -> CoachScreen(vm, m)
            Tab.FITNESS -> FitnessScreen(vm, m)
            Tab.ACTIVITIES -> ActivitiesScreen(vm, m)
            Tab.SETTINGS -> SettingsScreen(vm, m)
        }
    }
}
