package fr.coachia.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

class Series(val label: String, val color: Color, val values: List<Double?>)

/**
 * Simple multi-line chart. Points at index >= [forecastFrom] are drawn dashed (forecast).
 * A vertical marker is drawn at [forecastFrom] ("aujourd'hui").
 */
@Composable
fun LineChart(
    series: List<Series>,
    modifier: Modifier = Modifier,
    forecastFrom: Int? = null,
    xLabels: Pair<String, String>? = null,
    zeroLine: Boolean = false,
) {
    val all = series.flatMap { it.values.filterNotNull() }
    if (all.isEmpty()) {
        Text("Pas encore de données.", style = MaterialTheme.typography.bodySmall, modifier = modifier)
        return
    }
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val todayColor = MaterialTheme.colorScheme.onSurfaceVariant
    var min = all.min()
    var max = all.max()
    if (zeroLine) min = minOf(min, 0.0)
    if (max - min < 1e-6) { max += 1; min -= 1 }
    val pad = (max - min) * 0.08
    min -= pad
    max += pad
    Column(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            series.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(s.color, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(s.label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("%.0f".format(max), style = MaterialTheme.typography.labelSmall)
        }
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            val n = series.maxOf { it.values.size }
            if (n < 2) return@Canvas
            fun x(i: Int) = size.width * i / (n - 1).toFloat()
            fun y(v: Double) = (size.height * (1 - (v - min) / (max - min))).toFloat()
            for (k in 0..4) {
                val yy = size.height * k / 4f
                drawLine(gridColor, Offset(0f, yy), Offset(size.width, yy), strokeWidth = 1f)
            }
            if (zeroLine && min < 0 && max > 0) {
                drawLine(todayColor, Offset(0f, y(0.0)), Offset(size.width, y(0.0)), strokeWidth = 1.5f)
            }
            forecastFrom?.takeIf { it in 0 until n }?.let { f ->
                drawLine(
                    todayColor, Offset(x(f), 0f), Offset(x(f), size.height), strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
                )
            }
            for (s in series) {
                val solid = Path()
                val dashed = Path()
                var startedSolid = false
                var startedDashed = false
                s.values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val p = Offset(x(i), y(v))
                    val isForecast = forecastFrom != null && i >= forecastFrom
                    if (!isForecast || i == forecastFrom) {
                        if (!startedSolid) solid.moveTo(p.x, p.y).also { startedSolid = true } else solid.lineTo(p.x, p.y)
                    }
                    if (isForecast) {
                        if (!startedDashed) dashed.moveTo(p.x, p.y).also { startedDashed = true } else dashed.lineTo(p.x, p.y)
                    }
                }
                drawPath(solid, s.color, style = Stroke(width = 4f))
                drawPath(dashed, s.color, style = Stroke(width = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%.0f".format(min), style = MaterialTheme.typography.labelSmall)
            xLabels?.let {
                Text("${it.first}  →  ${it.second}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
