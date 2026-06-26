package ro.trenuri.infofer.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.InfoferParseException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InfoferSessionTest {
    @Test fun extracts_tokens_from_page_html() {
        val html = """
            <form><input name="__RequestVerificationToken" value="TOK123"/>
            <input name="ConfirmationKey" value="CONF456"/></form>
        """.trimIndent()
        val t = extractTokens(html)
        assertEquals("TOK123", t.requestVerificationToken)
        assertEquals("CONF456", t.confirmationKey)
    }

    @Test fun post_includes_tokens_and_antiabuse_fields() = runTest {
        var captured = ""
        var capturedUserAgent: String? = null
        val engine = MockEngine { req ->
            captured = (req.body as io.ktor.http.content.TextContent).text
            capturedUserAgent = req.headers[HttpHeaders.UserAgent]
            respond("<html>ok</html>", HttpStatusCode.OK)
        }
        val session = InfoferSession(HttpClient(engine))
        val body = session.postResult(
            "/ro-RO/Trains/TrainsResult",
            mapOf("TrainRunningNumber" to "5568", "Date" to "26.06.2026 0:00:00"),
            PageTokens("TOK123", "CONF456"),
        )
        assertTrue(body.contains("ok"))
        assertTrue(captured.contains("TrainRunningNumber=5568"))
        assertTrue(captured.contains("__RequestVerificationToken=TOK123"))
        assertTrue(captured.contains("ConfirmationKey=CONF456"))
        assertTrue(captured.contains("IsReCaptchaFailed=False"))
        assertTrue(captured.contains("ReCaptcha="))
        assertTrue(captured.contains("IsSearchWanted=False"))
        assertNotNull(capturedUserAgent)
        assertFalse(capturedUserAgent!!.isBlank())
    }

    @Test fun extractTokens_throws_when_verification_token_absent() {
        assertFailsWith<InfoferParseException> {
            extractTokens("<form><input name=\"ConfirmationKey\" value=\"C\"/></form>")
        }
    }
}
