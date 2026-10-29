package fr.coachia.core

/** Exports a structured indoor session as a Zwift custom workout (.zwo). */
object Zwift {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    private fun pct(v: Int) = "%.2f".format(java.util.Locale.US, v / 100.0)

    fun toZwo(e: CalendarEvent): String {
        val sb = StringBuilder()
        sb.append("<workout_file>\n")
        sb.append("  <author>Coach IA</author>\n")
        sb.append("  <name>${esc(e.title)}</name>\n")
        sb.append("  <description>${esc(e.description)}</description>\n")
        sb.append("  <sportType>bike</sportType>\n")
        sb.append("  <workout>\n")
        e.steps.forEachIndexed { i, s ->
            val cad = s.cadence?.let { " Cadence=\"$it\"" } ?: ""
            val high = s.powerHighPct
            val tag = when {
                high != null && high != s.powerLowPct && i == 0 -> "Warmup"
                high != null && high != s.powerLowPct && i == e.steps.lastIndex -> "Cooldown"
                high != null && high != s.powerLowPct -> "Ramp"
                else -> null
            }
            if (tag != null) {
                sb.append("    <$tag Duration=\"${s.durationSec}\" PowerLow=\"${pct(s.powerLowPct)}\" PowerHigh=\"${pct(high!!)}\"$cad/>\n")
            } else {
                sb.append("    <SteadyState Duration=\"${s.durationSec}\" Power=\"${pct(s.powerLowPct)}\"$cad/>\n")
            }
        }
        sb.append("  </workout>\n")
        sb.append("</workout_file>\n")
        return sb.toString()
    }
}
