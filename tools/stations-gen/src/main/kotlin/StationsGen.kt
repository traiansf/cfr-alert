import ro.trenuri.infofer.data.canonicalStationSlug
import ro.trenuri.infofer.data.normalizeStationName
import java.io.File
import java.net.URI
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
// Bounding box approach avoids needing the Overpass area database (faster, more reliable).
// Romania approx: south=43.6, west=20.2, north=48.3, east=29.7
private val OVERPASS_QUERY = """
    [out:csv(::lat,::lon,name;false)][timeout:180];
    ( node["railway"="station"](43.6,20.2,48.3,29.7);
      node["railway"="halt"](43.6,20.2,48.3,29.7); );
    out;
""".trimIndent()

// Working timetable uses internal track-group designators (Gr.A, Gr.B, Post N)
// that don't appear on the infofer passenger-facing website.  We strip them to
// add a canonical, user-searchable base-station entry alongside the group variants.
private val GROUP_SUFFIX = Regex("""\s+(?:Gr\.?\s*[A-Z]|Post\s+\d+)\s*$""")

/** Try one Overpass endpoint; return slug->coord map or null on any failure. */
private fun tryFetchOsm(url: String, client: HttpClient): Map<String, Pair<Double, Double>>? {
    return try {
        val encoded = java.net.URLEncoder.encode(OVERPASS_QUERY, "UTF-8")
        val req = HttpRequest.newBuilder(URI(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            // Many Overpass instances reject requests without a descriptive User-Agent.
            .header("User-Agent", "trenuri-stations-gen/1.0 (contact: stations-gen@trenuri.ro)")
            .POST(HttpRequest.BodyPublishers.ofString("data=$encoded"))
            .timeout(Duration.ofMinutes(5))
            .build()
        System.err.println("Fetching OSM from $url …")
        val body = client.send(req, HttpResponse.BodyHandlers.ofString()).body()
        val out = LinkedHashMap<String, Pair<Double, Double>>()
        body.lineSequence().forEach { line ->
            val cols = line.split('\t')
            if (cols.size >= 3) {
                val lat  = cols[0].toDoubleOrNull()
                val lon  = cols[1].toDoubleOrNull()
                val name = cols.drop(2).joinToString("\t")
                if (lat != null && lon != null && name.isNotBlank()) {
                    val slug = canonicalStationSlug(normalizeStationName(name))
                    if (slug.isNotEmpty()) out.putIfAbsent(slug, lat to lon)
                }
            }
        }
        System.err.println("  → parsed ${out.size} OSM nodes")
        if (out.isEmpty()) {
            System.err.println("  ✗ $url returned 0 nodes — treating as failure, will try next endpoint")
            null
        } else {
            out
        }
    } catch (e: Exception) {
        System.err.println("  ✗ $url failed: ${e.message}")
        null
    }
}

/** slug -> (lat, lon); empty map if both Overpass endpoints are unavailable. */
private fun fetchOsmCoords(): Map<String, Pair<Double, Double>> {
    val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()
    return tryFetchOsm(OVERPASS_PRIMARY, client)
        ?: tryFetchOsm(OVERPASS_MIRROR, client)
        ?: run {
            System.err.println("Warning: all Overpass endpoints failed — coords will be null for all stations.")
            emptyMap()
        }
}

fun main() {
    // (a) Download timetable XML and collect unique station names by slug.
    System.err.println("Downloading timetable XML from data.gov.ro …")
    val xml = URI(XML_URL).toURL().openStream().buffered()
    val reader = XMLInputFactory.newInstance().createXMLStreamReader(xml)
    val bySlug = LinkedHashMap<String, String>() // slug -> display name (first seen wins)

    fun add(raw: String?) {
        if (raw.isNullOrBlank()) return
        val name = normalizeStationName(raw)
        val slug = canonicalStationSlug(name)
        if (slug.isNotEmpty()) bySlug.putIfAbsent(slug, name)
    }

    while (reader.hasNext()) {
        if (reader.next() == XMLStreamConstants.START_ELEMENT && reader.localName == "ElementTrasa") {
            add(reader.getAttributeValue(null, "DenStaOrigine"))
            add(reader.getAttributeValue(null, "DenStaDestinatie"))
        }
    }
    reader.close()
    System.err.println("Timetable parsed (raw): ${bySlug.size} unique slugs")

    // Synthesize canonical base-station entries for track-group variants.
    // E.g. "Bucureşti Nord Gr.A" + "Gr.B" → add "Bucureşti Nord" → slug "Bucuresti-Nord".
    // putIfAbsent ensures the base name doesn't overwrite a real timetable entry.
    val groupExpansions = mutableListOf<Pair<String, String>>() // slug -> name to add
    bySlug.forEach { (_, name) ->
        val m = GROUP_SUFFIX.find(name) ?: return@forEach
        val baseName = name.substring(0, m.range.first).trim()
        val baseSlug = canonicalStationSlug(baseName)
        if (baseSlug.isNotEmpty()) groupExpansions += baseSlug to baseName
    }
    groupExpansions.forEach { (slug, name) -> bySlug.putIfAbsent(slug, name) }
    System.err.println("After group-expansion: ${bySlug.size} unique slugs")

    // (b)+(c) Fetch OSM coordinates and match by slug.
    val coords = fetchOsmCoords().toMutableMap()

    // Curated fallback coords for major stations that OSM tags under a different
    // name than the timetable slug (fill-if-missing — never clobber a good OSM match).
    val coordOverrides: Map<String, Pair<Double, Double>> = mapOf(
        // "Bucureşti Nord" → OSM has it as "Gara de Nord" / doesn't match slug.
        "Bucuresti-Nord" to (44.4456 to 26.0727),
        // "Iaşi" → OSM node name mismatch with timetable slug.
        "Iasi"           to (47.1690 to 27.5840),
    )
    coordOverrides.forEach { (slug, latlon) -> coords.putIfAbsent(slug, latlon) }

    // (d) Sort alphabetically by display name and emit Kotlin source.
    // Coverage guard: count only real OSM matches (not override entries) to detect a
    // degraded Overpass run before we overwrite the committed dataset.
    val osmOnlyMatchCount = bySlug.keys.count { slug ->
        coords[slug] != null && !coordOverrides.containsKey(slug)
    }
    if (osmOnlyMatchCount < 800) {
        System.err.println(
            "ERROR: OSM-only match count is $osmOnlyMatchCount — below the 800-station threshold. " +
            "This indicates a degraded Overpass response. NOT writing StationsData.kt to prevent regression."
        )
        System.exit(1)
    }

    val entries = bySlug.entries.sortedBy { it.value }
    val sb = StringBuilder()
    sb.appendLine("package ro.trenuri.infofer.data")
    sb.appendLine()
    sb.appendLine("import ro.trenuri.infofer.model.Station")
    sb.appendLine()
    sb.appendLine("// GENERATED by :tools:stations-gen. Source: data.gov.ro 2025-2026 timetable (names);")
    sb.appendLine("// OpenStreetMap via Overpass (coordinates, ODbL). Do not edit by hand.")
    sb.appendLine("internal val ALL_STATIONS: List<Station> = listOf(")
    var matched = 0
    for (e in entries) {
        val name = e.value.replace("\\", "\\\\").replace("\"", "\\\"")
        val c    = coords[e.key]
        if (c != null) matched++
        val coordArgs = if (c != null) ", lat = ${c.first}, lon = ${c.second}" else ""
        sb.appendLine("    Station(name = \"$name\", slug = \"${e.key}\"$coordArgs),")
    }
    sb.appendLine(")")

    File(OUT).writeText(sb.toString())
    println("Wrote ${entries.size} stations to $OUT; OSM coords matched: $matched")

    // Spot-check known slugs.
    listOf("Bucuresti-Nord", "Brasov").forEach { s ->
        println("contains $s = ${bySlug.containsKey(s)}; coord = ${coords[s]}")
    }
}
