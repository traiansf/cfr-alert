package ro.trenuri.infofer.net

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import ro.trenuri.infofer.InfoferNetworkException

class InfoferSession(
    private val http: HttpClient,
    private val baseUrl: String = "https://mersultrenurilor.infofer.ro",
) {
    suspend fun getPage(path: String): String =
        try {
            http.get("$baseUrl$path") {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }.bodyAsText()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InfoferNetworkException("GET $path failed", e)
        }

    suspend fun postResult(path: String, fields: Map<String, String>, pageTokens: PageTokens): String {
        val params = Parameters.build {
            fields.forEach { (k, v) -> append(k, v) }
            append("ReCaptcha", "")
            append("IsReCaptchaFailed", "False")
            append("IsSearchWanted", "False")
            append("ConfirmationKey", pageTokens.confirmationKey)
            append("__RequestVerificationToken", pageTokens.requestVerificationToken)
        }
        return try {
            http.post("$baseUrl$path") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(params.formUrlEncode())
            }.bodyAsText()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InfoferNetworkException("POST $path failed", e)
        }
    }

    companion object {
        const val USER_AGENT = "TrenuriApp/0.1 (+https://github.com/; informational; contact in README)"
    }
}
