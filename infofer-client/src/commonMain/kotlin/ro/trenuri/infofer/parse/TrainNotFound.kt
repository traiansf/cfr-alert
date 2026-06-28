package ro.trenuri.infofer.parse

import ro.trenuri.infofer.util.foldDiacritics

private const val NOT_FOUND_MARKER = "nu a fost gasit niciun tren"

/** True when the train GET page is infofer's "no such train number" page. */
fun isTrainNotFoundPage(pageHtml: String): Boolean =
    foldDiacritics(pageHtml).contains(NOT_FOUND_MARKER, ignoreCase = true)
