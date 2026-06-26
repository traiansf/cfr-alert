package ro.trenuri.infofer

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.cookies.HttpCookies
import ro.trenuri.infofer.net.InfoferSession

fun defaultInfoferClient(): InfoferClient {
    val http = HttpClient(CIO) { install(HttpCookies) }
    return InfoferClient(InfoferSession(http))
}
