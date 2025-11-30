package io.github.parkiyong.binaryo.http

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Transport implementation backed by JDK 11+ HttpClient (we target JVM 17).
 * Zero third-party dependencies.
 */
class JdkHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()
) : Transport {

    override fun post(url: URI, body: ByteArray, headers: Map<String, String>): Transport.SimpleResponse {
        val builder = HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/octet-stream")
        headers.forEach { (k, v) -> builder.header(k, v) }
        val req = builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray())
        return Transport.SimpleResponse(resp.statusCode(), resp.body(), resp.headers().map())
    }

    override fun get(url: URI, headers: Map<String, String>): Transport.SimpleResponse {
        val builder = HttpRequest.newBuilder(url)
            .timeout(Duration.ofSeconds(30))
        headers.forEach { (k, v) -> builder.header(k, v) }
        val req = builder.GET().build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray())
        return Transport.SimpleResponse(resp.statusCode(), resp.body(), resp.headers().map())
    }
}
