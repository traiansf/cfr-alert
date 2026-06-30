package ro.trenuri.infofer.util

import ro.trenuri.infofer.model.TrainCategory

private val DIACRITICS = mapOf(
    'ș' to 's', 'ş' to 's', 'Ș' to 'S', 'Ş' to 'S',
    'ț' to 't', 'ţ' to 't', 'Ț' to 'T', 'Ţ' to 'T',
    'ă' to 'a', 'Ă' to 'A', 'â' to 'a', 'Â' to 'A',
    'î' to 'i', 'Î' to 'I',
)

fun foldDiacritics(s: String): String = buildString {
    for (c in s) append(DIACRITICS[c] ?: c)
}

fun stationSlug(name: String): String =
    foldDiacritics(name).trim()
        .replace(Regex("[\\s/]+"), "-")
        .replace(Regex("[^A-Za-z0-9-]"), "")
        .replace(Regex("-+"), "-")
        .trim('-')

private fun pad2(n: Int) = n.toString().padStart(2, '0')

/** `DD.MM.YYYY 0:00:00` — the form infofer expects in POST result bodies. */
fun formatInfoferDate(year: Int, month: Int, day: Int): String =
    "${formatInfoferDateOnly(year, month, day)} 0:00:00"

/** `DD.MM.YYYY` — the form infofer expects in the `?Date=` query of a feature GET page. */
fun formatInfoferDateOnly(year: Int, month: Int, day: Int): String =
    "${pad2(day)}.${pad2(month)}.$year"

fun parseCategory(raw: String): TrainCategory {
    val t = raw.substringAfterLast("span-train-category-").trim().uppercase().replace("-", "")
    return when (t) {
        "R" -> TrainCategory.R
        "RE" -> TrainCategory.RE
        "RA" -> TrainCategory.RA
        "IR" -> TrainCategory.IR
        "IRN" -> TrainCategory.IRN
        "IC" -> TrainCategory.IC
        "ICN" -> TrainCategory.ICN
        "RR" -> TrainCategory.RR
        "IRA" -> TrainCategory.IRA
        "RRF" -> TrainCategory.RRF
        else -> TrainCategory.OTHER
    }
}
