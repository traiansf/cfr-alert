package ro.trenuri.infofer

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.cookies.HttpCookies
import ro.trenuri.infofer.net.InfoferSession

fun defaultInfoferClient(): InfoferClient {
    val http = HttpClient(OkHttp) { install(HttpCookies) }
    return InfoferClient(InfoferSession(http))
}
