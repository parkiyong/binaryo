package io.github.parkiyong.binaryo.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.test.*

class ApacheHttpClientTransportTest {

    private lateinit var server: HttpServer
    private lateinit var baseUri: URI
    private lateinit var httpClient: CloseableHttpClient
    private lateinit var transport: ApacheHttpClientTransport

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.executor = Executors.newCachedThreadPool()

        // Echo endpoint: respond with the exact request body
        server.createContext("/echo", HttpHandler { ex ->
            val body = ex.requestBody.readBytes()
            respond(ex, 200, body)
        })

        // Get endpoint: returns a fixed response
        server.createContext("/data", HttpHandler { ex ->
            respond(ex, 200, "hello world".toByteArray(StandardCharsets.UTF_8))
        })

        // Error endpoint: always 400
        server.createContext("/error", HttpHandler { ex ->
            respond(ex, 400, "bad request".toByteArray(StandardCharsets.UTF_8))
        })

        // Headers endpoint: echoes back specific request headers in body
        server.createContext("/headers", HttpHandler { ex ->
            val customHeader = ex.requestHeaders.getFirst("X-Custom-Header") ?: "not-found"
            respond(ex, 200, customHeader.toByteArray(StandardCharsets.UTF_8))
        })

        server.start()
        val port = server.address.port
        baseUri = URI.create("http://localhost:$port")

        // Create shared HttpClient and transport for tests
        httpClient = HttpClients.createDefault()
        transport = ApacheHttpClientTransport(httpClient)
    }

    @AfterTest
    fun tearDown() {
        httpClient.close()
        server.stop(0)
    }

    @Test
    fun post_echoesBody() {
        val target = baseUri.resolve("/echo")
        val body = "test payload".toByteArray(StandardCharsets.UTF_8)

        val response = transport.post(target, body)

        assertEquals(200, response.status)
        assertContentEquals(body, response.body)
    }

    @Test
    fun get_returnsData() {
        val target = baseUri.resolve("/data")

        val response = transport.get(target)

        assertEquals(200, response.status)
        assertEquals("hello world", String(response.body, StandardCharsets.UTF_8))
    }

    @Test
    fun post_returnsErrorStatus() {
        val target = baseUri.resolve("/error")
        val body = "test".toByteArray(StandardCharsets.UTF_8)

        val response = transport.post(target, body)

        assertEquals(400, response.status)
        assertEquals("bad request", String(response.body, StandardCharsets.UTF_8))
    }

    @Test
    fun post_sendsCustomHeaders() {
        val target = baseUri.resolve("/headers")
        val body = "test".toByteArray(StandardCharsets.UTF_8)
        val headers = mapOf("X-Custom-Header" to "my-value")

        val response = transport.post(target, body, headers)

        assertEquals(200, response.status)
        assertEquals("my-value", String(response.body, StandardCharsets.UTF_8))
    }

    @Test
    fun get_sendsCustomHeaders() {
        val target = baseUri.resolve("/headers")
        val headers = mapOf("X-Custom-Header" to "get-value")

        val response = transport.get(target, headers)

        assertEquals(200, response.status)
        assertEquals("get-value", String(response.body, StandardCharsets.UTF_8))
    }

    @Test
    fun worksWithCustomConfiguredClient() {
        // Demonstrate that users can fully configure their own client
        val customClient = HttpClients.custom()
            .setUserAgent("CustomBinaryoAgent/1.0")
            .build()

        val customTransport = ApacheHttpClientTransport(customClient)

        val target = baseUri.resolve("/data")
        val response = customTransport.get(target)

        assertEquals(200, response.status)

        customClient.close()
    }

    private fun respond(
        ex: HttpExchange,
        status: Int,
        body: ByteArray,
        contentType: String = "application/octet-stream"
    ) {
        try {
            ex.responseHeaders.add("Content-Type", contentType)
            ex.sendResponseHeaders(status, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        } catch (e: IOException) {
            // IOException during response write is expected in test scenarios
            // where the client may disconnect before receiving the full response
        } finally {
            ex.close()
        }
    }
}
