package fr.coachia.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Minimal Markdown rendering for the coach's answers: headings, lists, bold, italics, inline code. */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (raw in text.lines()) {
            val line = raw.trimEnd()
            val trimmed = line.trimStart()
            when {
                trimmed.isEmpty() -> {}
                trimmed.startsWith("#") -> {
                    val level = trimmed.takeWhile { it == '#' }.length
                    val style = when (level) {
                        1 -> MaterialTheme.typography.titleLarge
                        2 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }
                    Text(inline(trimmed.drop(level).trim()), style = style, modifier = Modifier.padding(top = 6.dp))
                }
                trimmed.matches(Regex("^(-{3,}|\\*{3,})$")) -> {}
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val indent = (line.length - trimmed.length) / 2
                    Bullet("•", trimmed.drop(2), indent)
                }
                Regex("^\\d+[.)] ").containsMatchIn(trimmed) -> {
                    val n = trimmed.substringBefore(' ')
                    Bullet(n, trimmed.substringAfter(' '), (line.length - trimmed.length) / 2)
                }
                trimmed.startsWith("|") -> Text(
                    trimmed.trim('|').split('|').joinToString("  ·  ") { it.trim() }
                        .takeUnless { it.replace("·", "").all { c -> c == '-' || c == ':' || c.isWhitespace() } } ?: "",
                    style = MaterialTheme.typography.bodySmall,
                )
                trimmed.startsWith("> ") -> Text(
                    inline(trimmed.drop(2)),
                    style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                )
                else -> Text(inline(trimmed), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Bullet(marker: String, body: String, indent: Int) {
    Row(Modifier.padding(start = (indent * 12).dp)) {
        Text(marker, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(22.dp))
        Text(inline(body), style = MaterialTheme.typography.bodyMedium)
    }
}

private val inlineRegex = Regex("(\\*\\*[^*]+\\*\\*|__[^_]+__|`[^`]+`|\\*[^*\\s][^*]*\\*)")

fun inline(text: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (m in inlineRegex.findAll(text)) {
        append(text.substring(last, m.range.first))
        val t = m.value
        when {
            t.startsWith("**") || t.startsWith("__") -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold)).also { append(t.substring(2, t.length - 2)) }
            t.startsWith("`") -> pushStyle(SpanStyle(fontFamily = FontFamily.Monospace)).also { append(t.substring(1, t.length - 1)) }
            else -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic)).also { append(t.substring(1, t.length - 1)) }
        }
        pop()
        last = m.range.last + 1
    }
    append(text.substring(last))
}
