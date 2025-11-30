package io.github.parkiyong.binaryo.client

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import io.github.parkiyong.binaryo.codec.KryoCodec
import io.github.parkiyong.binaryo.codec.DefaultKryoFactory
import io.github.parkiyong.binaryo.codec.KryoPool
import io.github.parkiyong.binaryo.http.JdkHttpTransport
import io.github.parkiyong.binaryo.http.Transport
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.test.*

private data class Person(val name: String, val age: Int)

class KryoRestClientTest {

    private lateinit var server: HttpServer
    private lateinit var baseUri: URI

    private val pool = KryoPool { DefaultKryoFactory.create() }
    private val codec = KryoCodec(pool)

    @BeforeTest
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(0), 0)
        server.executor = Executors.newCachedThreadPool()

        // Echo endpoint: respond with the exact request body
        server.createContext("/echo", HttpHandler { ex ->
            val body = ex.requestBody.readBytes()
            respond(ex, 200, body)
        })

        // Person endpoint: returns binary Kryo for a fixed Person
        server.createContext("/person", HttpHandler { ex ->
            val bytes = codec.toBytes(Person("Eve", 22))
            respond(ex, 200, bytes)
        })

        // Error endpoint: always 400
        server.createContext("/error", HttpHandler { ex ->
            respond(ex, 400, "bad request".toByteArray(StandardCharsets.UTF_8), "text/plain; charset=utf-8")
        })

        server.start()
        val port = server.address.port
        baseUri = URI.create("http://localhost:$port")
    }

    @AfterTest
    fun tearDown() {
        server.stop(0)
    }

    @Test
    fun postAndDecode_roundTripEcho() {
        val client = KryoRestClient(codec, JdkHttpTransport())
        val target = baseUri.resolve("/echo")
        val sent = Person("Ann", 30)
        val received = client.postAndDecode(target, sent, Person::class)
        assertEquals(sent, received)
    }

    @Test
    fun getAndDecode_receivesPerson() {
        val client = KryoRestClient(codec, JdkHttpTransport())
        val target = baseUri.resolve("/person")
        val received = client.getAndDecode(target, Person::class)
        assertEquals(Person("Eve", 22), received)
    }

    @Test
    fun postAndDecode_httpErrorThrows() {
        val client = KryoRestClient(codec, JdkHttpTransport())
        val target = baseUri.resolve("/error")
        val ex = assertFailsWith<IllegalStateException> {
            client.postAndDecode(target, Person("Bob", 25), Person::class)
        }
        assertTrue(ex.message!!.contains("HTTP 400"))
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
            // ignore in tests
        } finally {
            ex.close()
        }
    }
}
