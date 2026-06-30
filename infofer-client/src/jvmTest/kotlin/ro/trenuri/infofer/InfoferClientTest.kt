package ro.trenuri.infofer

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import ro.trenuri.infofer.model.BoardKind
import ro.trenuri.infofer.net.InfoferSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test fun getTrain_throws_for_not_found_page() = runTest {
        val notFoundPage = Fixtures.load("train-page-notfound-9999999.html")
        val client = clientReturning(notFoundPage, "")
        assertFailsWith<InfoferTrainNotFoundException> {
            client.getTrain("9999999", 2026, 6, 28)
        }
    }

    @Test fun getStationBoard_get_page_carries_the_date_context() = runTest {
        // infofer only serves the StationsResult board inline when the GET page that
        // mints the antiforgery token already carries the requested date (?Date=...).
        // Without it, future dates return a 121-byte JS redirect instead of the board.
        val requests = mutableListOf<Pair<HttpMethod, String>>()
        val page = """<input name="__RequestVerificationToken" value="T"/><input name="ConfirmationKey" value="C"/>"""
        val engine = MockEngine { req ->
            requests += req.method to req.url.toString()
            val body = if (req.method == HttpMethod.Get) page else Fixtures.load("station-board-brasov.html")
            respond(body, HttpStatusCode.OK)
        }
        val client = InfoferClient(InfoferSession(HttpClient(engine)))
        client.getStationBoard("Brasov", BoardKind.DEPARTURES, 2026, 7, 1)

        val getUrl = requests.first { it.first == HttpMethod.Get }.second
        assertTrue(getUrl.contains("Date=01.07.2026"), "GET page must carry the date context, was: $getUrl")
    }

    @Test fun searchItineraries_parses_fixture_result() = runTest {
        val page = """<input name="__RequestVerificationToken" value="T"/><input name="ConfirmationKey" value="C"/>"""
        val client = clientReturning(page, Fixtures.load("itineraries-bucuresti-brasov.html"))
        val opts = client.searchItineraries("Bucuresti-Nord", "Brasov", 2026, 6, 26)
        assertTrue(opts.size >= 10)
    }
}
