import ro.trenuri.infofer.data.canonicalStationSlug
import ro.trenuri.infofer.data.normalizeStationName
import java.io.File
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants

private const val XML_URL =
    "https://data.gov.ro/dataset/c4f71dbb-de39-49b2-b697-5b60a5f299a2/resource/0f67143e-bb88-4a06-8e7a-b35b1eb91329/download/trenuri-2025-2026_sntfc.xml"
private const val OUT =
    "infofer-client/src/commonMain/kotlin/ro/trenuri/infofer/data/StationsData.kt"
private const val OVERPASS_PRIMARY = "https://overpass-api.de/api/interpreter"
private const val OVERPASS_MIRROR  = "https://overpass.kumi.systems/api/interpreter"
private const val NOMINATIM_BASE   = "https://nominatim.openstreetmap.org/search"
private const val NOMINATIM_UA     = "trenuri-slice-b-dataset-gen/1.0 (traian.serbanuta@gmail.com)"

// Romania bounding box: south=43.6, west=20.2, north=48.3, east=29.7
private const val RO_BBOX = "43.6,20.2,48.3,29.7"

// Railway nodes: station + halt + stop; index by name, name:ro, official_name, alt_name, short_name.
private val RAILWAY_OVERPASS_QUERY = """
    [out:csv(::lat,::lon,name,"name:ro","official_name","alt_name","short_name";false)][timeout:180];
    ( node["railway"="station"]($RO_BBOX);
      node["railway"="halt"]($RO_BBOX);
      node["railway"="stop"]($RO_BBOX); );
    out;
""".trimIndent()

// Place nodes: cities, towns, villages, hamlets, suburbs.
private val PLACE_OVERPASS_QUERY = """
    [out:csv(::lat,::lon,name,"name:ro";false)][timeout:180];
    node["place"~"^(city|town|village|hamlet|suburb)$"]($RO_BBOX);
    out;
""".trimIndent()

// Track-group suffix pattern (used for synthesising base-station entries).
// E.g. "Bucureşti Nord Gr.A" or "Post 17" → strip suffix to get "Bucureşti Nord".
private val GROUP_SUFFIX = Regex("""\s+(?:Gr\.?\s*[A-Z]|Post\s+\d+)\s*$""")

// Operational sub-point detector (case-insensitive).
// IMPORTANT: \bPost\s and \bPost$ do NOT match "Posta" or "Postăvaru"
// (the word boundary breaks before "a"/"ă"). Verify after running.
private val OPERATIONAL_REGEX = Regex(
    "(?i)" +
    """\bTriaj\b|""" +
    """\bPost\s|\bPost${'$'}|""" +   // "Post 17", "Post Macazuri", "...Post" at end
    """\bRam\.|\bRamif|""" +         // "Buzău Ram.", "Barboşi Ramif."
    """\bGr\.|\bGr\s|""" +           // "Nord Gr.A", "Gr. C"
    """\bPM\.|\bP\.M\.|\bP\.O\b|""" +
    """\bMacazuri\b|\bAtelier\b|""" +
    """\bBif\.|\bBifurca|""" +
    """\bTj\."""                      // "(Tj.)", "Sibiu Tj."
)

private fun isOperationalPoint(name: String): Boolean = OPERATIONAL_REGEX.containsMatchIn(name)

// Parenthetical sub-string remover ("Câmpulung (CF)" → "Câmpulung").
private val PAREN_REGEX = Regex("""\s*\([^)]*\)""")

// Trailing period-less type-token remover (for names that escaped normalizeStationName).
// normalizeStationName already strips Hm., Hc., etc. with period; this handles the no-period form.
private val TYPE_TOKEN_REGEX = Regex("""(?i)\s+(?:Hm|Hc|Hcv|h)\s*$""")

/**
 * Returns DISTINCT non-empty slugs to try for a station display name, in priority order:
 *  1. canonicalStationSlug(normalizeStationName(name))
 *  2. same, but with any parenthetical removed first
 *  3. same, but also stripping a trailing period-less type token
 */
private fun matchCandidates(name: String): List<String> {
    val result = linkedSetOf<String>()

    val s1 = canonicalStationSlug(normalizeStationName(name))
    if (s1.isNotEmpty()) result.add(s1)

    val noParens = PAREN_REGEX.replace(name, "").trim()
    val s2 = canonicalStationSlug(normalizeStationName(noParens))
    if (s2.isNotEmpty()) result.add(s2)

    val noToken = TYPE_TOKEN_REGEX.replace(noParens, "").trim()
    val s3 = canonicalStationSlug(normalizeStationName(noToken))
    if (s3.isNotEmpty()) result.add(s3)

    return result.toList()
}

// ---------------------------------------------------------------------------
// Overpass helpers
// ---------------------------------------------------------------------------

/** Parse a 7-column railway CSV (lat, lon, name, name:ro, official_name, alt_name, short_name). */
private fun parseRailwayCsv(body: String): LinkedHashMap<String, Pair<Double, Double>> {
    val out = LinkedHashMap<String, Pair<Double, Double>>()
    body.lineSequence().forEach { line ->
        val cols = line.split('\t')
        if (cols.size < 3) return@forEach
        val lat = cols[0].toDoubleOrNull() ?: return@forEach
        val lon = cols[1].toDoubleOrNull() ?: return@forEach
        val coord = lat to lon
        // Collect all name fields; split alt_name on ';'.
        val fields = mutableListOf<String>()
        if (cols.size > 2) fields.add(cols[2])                              // name
        if (cols.size > 3) fields.add(cols[3])                              // name:ro
        if (cols.size > 4) fields.add(cols[4])                              // official_name
        if (cols.size > 5) cols[5].split(';').mapTo(fields) { it.trim() }   // alt_name (multi)
        if (cols.size > 6) fields.add(cols[6])                              // short_name
        for (field in fields) {
            if (field.isBlank()) continue
            val slug = canonicalStationSlug(normalizeStationName(field))
            if (slug.isNotEmpty()) out.putIfAbsent(slug, coord)
        }
    }
    return out
}

/** Parse a 4-column place CSV (lat, lon, name, name:ro). */
private fun parsePlaceCsv(body: String): LinkedHashMap<String, Pair<Double, Double>> {
    val out = LinkedHashMap<String, Pair<Double, Double>>()
    body.lineSequence().forEach { line ->
        val cols = line.split('\t')
        if (cols.size < 3) return@forEach
        val lat = cols[0].toDoubleOrNull() ?: return@forEach
        val lon = cols[1].toDoubleOrNull() ?: return@forEach
        val coord = lat to lon
        val fields = listOf(cols.getOrElse(2) { "" }, cols.getOrElse(3) { "" })
        for (field in fields) {
            if (field.isBlank()) continue
            val slug = canonicalStationSlug(normalizeStationName(field))
            if (slug.isNotEmpty()) out.putIfAbsent(slug, coord)
        }
    }
    return out
}

/** Try one Overpass endpoint. Returns null on failure or empty response. */
private fun tryFetchOverpass(
    url: String, query: String, label: String, client: HttpClient
): Map<String, Pair<Double, Double>>? {
    return try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val req = HttpRequest.newBuilder(URI(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("User-Agent", "trenuri-stations-gen/1.0 (contact: stations-gen@trenuri.ro)")
            .POST(HttpRequest.BodyPublishers.ofString("data=$encoded"))
            .timeout(Duration.ofMinutes(5))
            .build()
        System.err.println("Fetching OSM $label from $url …")
        val body = client.send(req, HttpResponse.BodyHandlers.ofString()).body()
        val out = when (label) {
            "railway" -> parseRailwayCsv(body)
            else      -> parsePlaceCsv(body)
        }
        System.err.println("  → parsed ${out.size} $label slugs")
        if (out.isEmpty()) {
            System.err.println("  ✗ $url returned 0 $label nodes — treating as failure, will try next endpoint")
            null
        } else out
    } catch (e: Exception) {
        System.err.println("  ✗ $url failed ($label): ${e.message}")
        null
    }
}

private fun fetchRailwayCoords(client: HttpClient): Map<String, Pair<Double, Double>> =
    tryFetchOverpass(OVERPASS_PRIMARY, RAILWAY_OVERPASS_QUERY, "railway", client)
        ?: tryFetchOverpass(OVERPASS_MIRROR, RAILWAY_OVERPASS_QUERY, "railway", client)
        ?: run {
            System.err.println("Warning: all Overpass endpoints failed for railway nodes.")
            emptyMap()
        }

private fun fetchPlaceCoords(client: HttpClient): Map<String, Pair<Double, Double>> =
    tryFetchOverpass(OVERPASS_PRIMARY, PLACE_OVERPASS_QUERY, "place", client)
        ?: tryFetchOverpass(OVERPASS_MIRROR, PLACE_OVERPASS_QUERY, "place", client)
        ?: run {
            System.err.println("Warning: all Overpass endpoints failed for place nodes.")
            emptyMap()
        }

// ---------------------------------------------------------------------------
// Nominatim helper
// ---------------------------------------------------------------------------

/**
 * Query Nominatim for a single place name (with Romania country bias).
 * Returns null on failure/empty response — never throws.
 */
private fun fetchNominatimCoord(cleanedName: String, client: HttpClient): Pair<Double, Double>? {
    return try {
        val encoded = URLEncoder.encode("$cleanedName, Romania", "UTF-8")
        val url = "$NOMINATIM_BASE?q=$encoded&format=jsonv2&limit=1&countrycodes=ro"
        val req = HttpRequest.newBuilder(URI(url))
            .header("User-Agent", NOMINATIM_UA)
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build()
        val body = client.send(req, HttpResponse.BodyHandlers.ofString()).body()
        val lat = Regex(""""lat"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        val lon = Regex(""""lon"\s*:\s*"([^"]+)"""").find(body)?.groupValues?.get(1)?.toDoubleOrNull()
        if (lat != null && lon != null) lat to lon else null
    } catch (e: Exception) {
        System.err.println("  Nominatim error for '$cleanedName': ${e.message}")
        null
    }
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

fun main() {
    // --- Step 1: Download timetable XML ---
    System.err.println("Downloading timetable XML from data.gov.ro …")
    val xml = URI(XML_URL).toURL().openStream().buffered()
    val reader = XMLInputFactory.newInstance().createXMLStreamReader(xml)

    val rawNames = mutableSetOf<String>()
    while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "ElementTrasa") {
            listOf(
                reader.getAttributeValue(null, "DenStaOrigine"),
                reader.getAttributeValue(null, "DenStaDestinatie")
            ).forEach { raw ->
                if (!raw.isNullOrBlank()) rawNames.add(normalizeStationName(raw))
            }
        }
    }
    reader.close()
    System.err.println("Timetable parsed: ${rawNames.size} unique (normalised) names")

    // --- Step 2: Identify and report operational sub-points ---
    val dropped = rawNames.filter { isOperationalPoint(it) }.sorted()
    System.err.println("\n=== DROPPED OPERATIONAL POINTS (${dropped.size}) ===")
    dropped.forEach { System.err.println("  DROP: $it") }
    System.err.println("=== END DROPPED LIST ===\n")

    // --- Step 3: Build bySlug from non-operational names ---
    val bySlug = LinkedHashMap<String, String>() // slug → display name
    rawNames.filter { !isOperationalPoint(it) }.sorted().forEach { name ->
        val slug = canonicalStationSlug(name)
        if (slug.isNotEmpty()) bySlug.putIfAbsent(slug, name)
    }

    // --- Step 4: Synthesise base-station entries from group variants ---
    // Done over ALL raw names (including dropped operational ones) so that
    // e.g. "Bucureşti Nord Gr.A" (dropped) still adds "Bucureşti Nord".
    val groupExpansions = mutableListOf<Pair<String, String>>()
    rawNames.forEach { name ->
        val m = GROUP_SUFFIX.find(name) ?: return@forEach
        val baseName = name.substring(0, m.range.first).trim()
        val baseSlug = canonicalStationSlug(baseName)
        if (baseSlug.isNotEmpty()) groupExpansions += baseSlug to baseName
    }
    groupExpansions.forEach { (slug, name) ->
        // Skip group-expansion base names that are themselves operational
        // (e.g. "Buzău Ram. Gr.A" → base "Buzău Ram." is still a ramification).
        if (!isOperationalPoint(name)) bySlug.putIfAbsent(slug, name)
    }
    System.err.println("Station slugs (passenger, after group-expansion): ${bySlug.size}")

    // --- Step 5: Fetch OSM coordinates ---
    val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    val railwayCoords: Map<String, Pair<Double, Double>> = fetchRailwayCoords(httpClient)
    val placeCoords:   Map<String, Pair<Double, Double>> = fetchPlaceCoords(httpClient)

    // --- Step 6: Resolve coordinates by tier ---
    val resolvedCoords = LinkedHashMap<String, Pair<Double, Double>>()
    val tierRailway    = mutableSetOf<String>()
    val tierPlace      = mutableSetOf<String>()

    for ((slug, name) in bySlug) {
        val candidates = matchCandidates(name)
        // Tier 1: railway
        val railCoord = candidates.firstNotNullOfOrNull { railwayCoords[it] }
        if (railCoord != null) {
            resolvedCoords[slug] = railCoord
            tierRailway.add(slug)
            continue
        }
        // Tier 2: place nodes
        val placeCoord = candidates.firstNotNullOfOrNull { placeCoords[it] }
        if (placeCoord != null) {
            resolvedCoords[slug] = placeCoord
            tierPlace.add(slug)
        }
    }

    // --- Coverage guard (OSM only) ---
    // Abort before writing if railway+place OSM hits < 800 (Overpass outage detector).
    val osmMatchCount = tierRailway.size + tierPlace.size
    if (osmMatchCount < 800) {
        System.err.println(
            "ERROR: OSM match count (railway=${tierRailway.size} + place=${tierPlace.size}) = $osmMatchCount " +
            "is below the 800-station threshold. Likely a degraded Overpass response. " +
            "NOT writing StationsData.kt to prevent regression. Retry up to ~3×."
        )
        System.exit(1)
    }
    System.err.println("Coverage guard passed: $osmMatchCount stations resolved via OSM " +
        "(railway=${tierRailway.size}, place=${tierPlace.size})")

    // --- Step 7: Nominatim for residual unmatched stations ---
    val tierNominatim  = mutableSetOf<String>()
    val unresolved     = bySlug.keys.filter { it !in resolvedCoords }
    System.err.println("Nominatim queries needed for ${unresolved.size} unresolved stations …")

    val nominatimCache = mutableMapOf<String, Pair<Double, Double>?>() // cleanedName → coord (null = not found)
    for (slug in unresolved) {
        val name = bySlug[slug] ?: continue
        // Derive the cleanest human-readable form for geocoding
        val cleanedName = TYPE_TOKEN_REGEX.replace(PAREN_REGEX.replace(name, "").trim(), "").trim()

        if (nominatimCache.containsKey(cleanedName)) {
            // Re-use cached result (another slug had the same cleaned name)
            nominatimCache[cleanedName]?.let { coord ->
                resolvedCoords[slug] = coord
                tierNominatim.add(slug)
            }
        } else {
            Thread.sleep(1100) // Nominatim ToS: ≤1 req/s
            System.err.print("  Nominatim '$cleanedName' … ")
            val coord = fetchNominatimCoord(cleanedName, httpClient)
            nominatimCache[cleanedName] = coord
            if (coord != null) {
                resolvedCoords[slug] = coord
                tierNominatim.add(slug)
                System.err.println("(${coord.first}, ${coord.second})")
            } else {
                System.err.println("not found")
            }
        }
    }

    // --- Step 8: coordOverrides (authoritative curated fallbacks — applied last, always win) ---
    val coordOverrides: Map<String, Pair<Double, Double>> = mapOf(
        // "Bucureşti Nord" → OSM historically tagged under different name variant.
        "Bucuresti-Nord" to (44.4456 to 26.0727),
        // "Iaşi" → OSM node name mismatch with timetable slug.
        "Iasi"           to (47.1690 to 27.5840),
    )
    val tierOverride = mutableSetOf<String>()
    coordOverrides.forEach { (slug, latlon) ->
        if (slug in bySlug) {
            resolvedCoords[slug] = latlon
            tierRailway.remove(slug)
            tierPlace.remove(slug)
            tierNominatim.remove(slug)
            tierOverride.add(slug)
        }
    }

    // --- Step 9: Coverage report ---
    val stillNull = bySlug.keys.filter { it !in resolvedCoords }.sorted()
    System.err.println("\n=== COVERAGE REPORT ===")
    System.err.println("Total stations (passenger, after drop): ${bySlug.size}")
    System.err.println("  Railway tier:   ${tierRailway.size}")
    System.err.println("  Place tier:     ${tierPlace.size}")
    System.err.println("  Nominatim tier: ${tierNominatim.size}")
    System.err.println("  Override tier:  ${tierOverride.size}")
    System.err.println("  Still null:     ${stillNull.size}")
    if (stillNull.isNotEmpty()) {
        System.err.println("  Still-null stations:")
        stillNull.forEach { s -> System.err.println("    $s  (\"${bySlug[s]}\")") }
    }
    System.err.println("=== END COVERAGE REPORT ===\n")

    // --- Step 10: Emit Kotlin source ---
    val entries = bySlug.entries.sortedBy { it.value }
    val sb = StringBuilder()
    sb.appendLine("package ro.trenuri.infofer.data")
    sb.appendLine()
    sb.appendLine("import ro.trenuri.infofer.model.Station")
    sb.appendLine()
    sb.appendLine("// GENERATED by :tools:stations-gen. Source: data.gov.ro 2025-2026 timetable (names);")
    sb.appendLine("// OpenStreetMap via Overpass (coordinates, ODbL). Do not edit by hand.")
    sb.appendLine("internal val ALL_STATIONS: List<Station> = listOf(")
    for (e in entries) {
        val name    = e.value.replace("\\", "\\\\").replace("\"", "\\\"")
        val c       = resolvedCoords[e.key]
        val coordArgs = if (c != null) ", lat = ${c.first}, lon = ${c.second}" else ""
        sb.appendLine("    Station(name = \"$name\", slug = \"${e.key}\"$coordArgs),")
    }
    sb.appendLine(")")

    File(OUT).writeText(sb.toString())
    val withCoords = entries.count { resolvedCoords.containsKey(it.key) }
    println("Wrote ${entries.size} stations to $OUT  (with coords: $withCoords / ${entries.size}; still-null: ${stillNull.size})")
    listOf("Bucuresti-Nord", "Brasov", "Iasi").forEach { s ->
        println("  check $s: present=${bySlug.containsKey(s)}, coord=${resolvedCoords[s]}")
    }
}
