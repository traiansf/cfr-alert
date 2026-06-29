package ro.trenuri.infofer.data

private val DIACRITICS = mapOf(
    'ă' to "a", 'Ă' to "A", 'â' to "a", 'Â' to "A", 'î' to "i", 'Î' to "I",
    'ș' to "s", 'Ș' to "S", 'ş' to "s", 'Ş' to "S",   // comma-below + cedilla
    'ț' to "t", 'Ț' to "T", 'ţ' to "t", 'Ţ' to "T",
)

private val SUFFIX = Regex("""\s+(?:[Hh][Mm]|[Hh][Cc][Vv]?|[Hh])\.\s*$""")

/** Strip trailing station-type abbreviations (Hm., h., hc., hcv.) and collapse whitespace. */
fun normalizeStationName(rawName: String): String =
    rawName.replace(SUFFIX, "").trim().replace(Regex("""\s+"""), " ")

/** Transliterate Romanian diacritics and hyphenate, preserving case. e.g. "Bucureşti Nord" -> "Bucuresti-Nord". */
fun canonicalStationSlug(displayName: String): String {
    val sb = StringBuilder()
    for (ch in displayName.trim()) {
        when {
            DIACRITICS.containsKey(ch) -> sb.append(DIACRITICS[ch])
            ch.isLetterOrDigit() && ch.code < 128 -> sb.append(ch)
            ch == ' ' || ch == '/' || ch == '.' || ch == '-' || ch == '_' -> sb.append('-')
            // drop any other punctuation / non-ASCII
        }
    }
    return sb.toString()
        .replace(Regex("-+"), "-")
        .trim('-')
}
