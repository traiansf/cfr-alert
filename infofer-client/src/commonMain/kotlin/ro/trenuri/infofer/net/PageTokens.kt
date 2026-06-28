package ro.trenuri.infofer.net

import com.fleeksoft.ksoup.Ksoup
import ro.trenuri.infofer.InfoferParseException

data class PageTokens(val requestVerificationToken: String, val confirmationKey: String)

fun extractTokens(html: String): PageTokens {
    val doc = Ksoup.parse(html)
    val rvt = doc.selectFirst("input[name=__RequestVerificationToken]")?.attr("value")
    val conf = doc.selectFirst("input[name=ConfirmationKey]")?.attr("value").orEmpty()
    if (rvt.isNullOrEmpty()) throw InfoferParseException("missing __RequestVerificationToken on page")
    return PageTokens(rvt, conf)
}
