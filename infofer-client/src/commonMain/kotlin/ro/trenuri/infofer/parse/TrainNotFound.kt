package ro.trenuri.infofer.parse

import ro.trenuri.infofer.util.foldDiacritics

// The not-found phrase is "Nu a fost găsit niciun tren cu acest număr!". We match
// on the diacritic-free middle substring so detection works whether the page
// encodes the diacritics as Unicode (ă) or HTML entities (&#x103;).
private const val NOT_FOUND_MARKER = "niciun tren cu acest"

/** True when the train GET page is infofer's "no such train number" page. */
fun isTrainNotFoundPage(pageHtml: String): Boolean =
    foldDiacritics(pageHtml).contains(NOT_FOUND_MARKER, ignoreCase = true)
