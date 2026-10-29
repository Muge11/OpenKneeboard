package fr.coachia.app.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

object CoachColors {
    val Race = Color(0xFFE53935)
    val Club = Color(0xFFFB8C00)
    val Planned = Color(0xFF1E88E5)
    val Done = Color(0xFF43A047)
    val Constraint = Color(0xFF8D6E63)
    val Ctl = Color(0xFF1E88E5)
    val Atl = Color(0xFFD81B60)
    val Tsb = Color(0xFF43A047)
}

@Composable
fun CoachTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
        dark -> darkColorScheme(primary = Color(0xFF81C784), secondary = Color(0xFFFFD54F))
        else -> lightColorScheme(primary = Color(0xFF2E7D32), secondary = Color(0xFFF9A825))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
