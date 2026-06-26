package ro.trenuri.infofer

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.net.InfoferSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InfoferClientTest {
    private fun clientReturning(pageHtml: String, resultHtml: String): InfoferClient {
        val engine = MockEngine { req ->
            val body = if (req.method == HttpMethod.Get) pageHtml else resultHtml
            respond(body, HttpStatusCode.OK)
        }
        return InfoferClient(InfoferSession(HttpClient(engine)))
    }

    @Test fun getTrain_parses_fixture_result() = runTest {
        val page = """<input name="__RequestVerificationToken" value="T"/><input name="ConfirmationKey" value="C"/>"""
        val client = clientReturning(page, Fixtures.load("train-result-5568.html"))
        val train = client.getTrain("5568", 2026, 6, 26)
        assertEquals("5568", train.trainNumber)
        assertEquals(2, train.branches.first().delay?.minutes)
    }

    @Test fun searchItineraries_parses_fixture_result() = runTest {
        val page = """<input name="__RequestVerificationToken" value="T"/><input name="ConfirmationKey" value="C"/>"""
        val client = clientReturning(page, Fixtures.load("itineraries-bucuresti-brasov.html"))
        val opts = client.searchItineraries("Bucuresti-Nord", "Brasov", 2026, 6, 26)
        assertTrue(opts.size >= 10)
    }
}
