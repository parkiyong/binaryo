package io.github.parkiyong.binaryo.examples

import io.github.parkiyong.binaryo.client.KryoRestClient
import io.github.parkiyong.binaryo.codec.DefaultKryoFactory
import io.github.parkiyong.binaryo.codec.KryoCodec
import io.github.parkiyong.binaryo.codec.KryoPool
import io.github.parkiyong.binaryo.http.ApacheHttpClientTransport
import io.github.parkiyong.binaryo.http.EnhancedJdkHttpTransport
import io.github.parkiyong.binaryo.http.JdkHttpTransport
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.util.Timeout
import java.net.URI
import java.time.Duration

/**
 * Examples demonstrating different HTTP transport options for Binaryo.
 *
 * See HTTP_CLIENT_ANALYSIS.md for detailed comparison.
 */
object TransportExamples {

    data class Person(val name: String, val age: Int)

    /**
     * Basic usage with simple JDK HttpClient transport.
     * Best for: Simple use cases, minimal configuration needed.
     */
    fun basicExample() {
        val pool = KryoPool { DefaultKryoFactory.create() }
        val codec = KryoCodec(pool)

        // Simple transport with sensible defaults
        val transport = JdkHttpTransport()
        val client = KryoRestClient(codec, transport)

        val person = Person("Alice", 30)
        val response = client.postAndDecode(
            url = URI.create("http://api.example.com/person"),
            value = person,
            expected = Person::class
        )

        println("Response: $response")
    }

    /**
     * Enterprise-ready transport with retry logic.
     * Best for: Production environments with flaky networks.
     */
    fun enterpriseWithRetries() {
        val pool = KryoPool { DefaultKryoFactory.create() }
        val codec = KryoCodec(pool)

        val transport = EnhancedJdkHttpTransport(
            config = EnhancedJdkHttpTransport.TransportConfig(
                connectTimeout = Duration.ofSeconds(5),
                requestTimeout = Duration.ofSeconds(30),
                maxRetries = 3,  // Retry up to 3 times
                retryDelay = Duration.ofMillis(500),  // Start with 500ms, exponential backoff
                followRedirects = true
            )
        )

        val client = KryoRestClient(codec, transport)

        // Will automatically retry on connection failures
        val person = client.getAndDecode(
            url = URI.create("http://api.example.com/person/123"),
            expected = Person::class
        )

        println("Retrieved: $person")
    }

    /**
     * Transport with authentication interceptor.
     * Best for: APIs requiring auth headers on every request.
     */
    fun withAuthentication() {
        val pool = KryoPool { DefaultKryoFactory.create() }
        val codec = KryoCodec(pool)

        val token = "your-api-token"

        val transport = EnhancedJdkHttpTransport(
            requestInterceptor = { builder ->
                builder.header("Authorization", "Bearer $token")
                       .header("X-API-Version", "v1")
            }
        )

        val client = KryoRestClient(codec, transport)

        // Every request will include auth headers automatically
        val person = client.getAndDecode(
            url = URI.create("http://api.example.com/person/123"),
            expected = Person::class
        )
    }

    /**
     * Transport with logging and monitoring.
     * Best for: Debugging, observability, metrics collection.
     */
    fun withLoggingAndMonitoring() {
        val pool = KryoPool { DefaultKryoFactory.create() }
        val codec = KryoCodec(pool)

        val transport = EnhancedJdkHttpTransport(
            requestInterceptor = { builder ->
                println("[REQUEST] Sending request...")
                builder.header("X-Request-ID", java.util.UUID.randomUUID().toString())
            },
            responseInterceptor = { response ->
                println("[RESPONSE] Status: ${response.status}, Body size: ${response.body.size} bytes")
                // Could integrate with metrics here
                // metrics.recordResponseTime(...)
                response
            }
        )

        val client = KryoRestClient(codec, transport)

        val person = Person("Bob", 25)
        client.postAndDecode(
            url = URI.create("http://api.example.com/person"),
            value = person,
            expected = Person::class
        )
    }

    /**
     * Full production-ready configuration.
     * Best for: Enterprise applications with comprehensive requirements.
     */
    fun productionConfiguration() {
        val pool = KryoPool { DefaultKryoFactory.create() }
        val codec = KryoCodec(pool)

        val apiToken = System.getenv("API_TOKEN") ?: throw IllegalStateException("API_TOKEN not set")

        val transport = EnhancedJdkHttpTransport(
            config = EnhancedJdkHttpTransport.TransportConfig(
                connectTimeout = Duration.ofSeconds(5),
                requestTimeout = Duration.ofSeconds(30),
                maxRetries = 3,
                retryDelay = Duration.ofMillis(500),
                followRedirects = true,
                version = java.net.http.HttpClient.Version.HTTP_2  // Use HTTP/2 for better performance
            ),
            requestInterceptor = { builder ->
                // Add auth and tracing
                builder.header("Authorization", "Bearer $apiToken")
                       .header("X-Request-ID", java.util.UUID.randomUUID().toString())
                       .header("User-Agent", "Binaryo/1.0")
            },
            responseInterceptor = { response ->
                // Log and validate
                if (response.status >= 500) {
                    println("[ERROR] Server error: ${response.status}")
                }

                // Could add circuit breaker logic here
                // if (response.status >= 500) circuitBreaker.recordFailure()

                response
            }
        )

        val client = KryoRestClient(codec, transport)

        try {
            val person = client.getAndDecode(
                url = URI.create("http://api.example.com/person/123"),
                expected = Person::class
            )
            println("Success: $person")
        } catch (e: Exception) {
            println("Failed after retries: ${e.message}")
            // Alert monitoring system
        }
    }

    /**
     * Performance comparison note:
     *
     * JDK HttpClient (both simple and enhanced):
     * - Excellent connection pooling
     * - HTTP/2 multiplexing support
     * - Low memory footprint
     * - Zero external dependencies
     * - Typical throughput: 10k+ req/s for binary payloads
     *
     * Apache HttpClient 5:
     * - More configuration options
     * - Well-established in enterprise environments
     * - Slightly more overhead
     * - Additional ~2MB in dependencies
     * - Similar performance characteristics
     *
     * For most use cases, JDK HttpClient is the better choice.
     * Use Apache HttpClient when you have specific enterprise requirements
     * or existing Apache HttpClient configurations to reuse.
     */

    /**
     * Using Apache HttpClient 5 with user-provided client.
     * Best for: Existing Apache HttpClient configurations, enterprise environments.
     *
     * Note: The library does NOT instantiate the HttpClient - users must
     * create and configure their own client instance.
     */
    fun withApacheHttpClient() {
        val pool = KryoPool { DefaultKryoFactory.create() }
        val codec = KryoCodec(pool)

        // User creates their own fully-configured Apache HttpClient
        val httpClient = HttpClients.createDefault()

        // Pass it to the transport - library doesn't manage the client lifecycle
        val transport = ApacheHttpClientTransport(httpClient)
        val client = KryoRestClient(codec, transport)

        val person = Person("Charlie", 35)
        val response = client.postAndDecode(
            url = URI.create("http://api.example.com/person"),
            value = person,
            expected = Person::class
        )

        println("Response: $response")

        // User is responsible for closing the HttpClient
        httpClient.close()
    }

    /**
     * Apache HttpClient with custom connection pooling and timeouts.
     * Best for: High-throughput applications with specific connection requirements.
     */
    fun withApacheHttpClientAdvanced() {
        val pool = KryoPool { DefaultKryoFactory.create() }
        val codec = KryoCodec(pool)

        // User configures connection pooling
        val connectionManager = PoolingHttpClientConnectionManager()
        connectionManager.maxTotal = 100
        connectionManager.defaultMaxPerRoute = 20

        // User configures timeouts
        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(5))
            .setResponseTimeout(Timeout.ofSeconds(30))
            .build()

        // User creates custom client with all configurations
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .setUserAgent("Binaryo/1.0")
            .build()

        // Pass it to the transport
        val transport = ApacheHttpClientTransport(httpClient)
        val client = KryoRestClient(codec, transport)

        val person = client.getAndDecode(
            url = URI.create("http://api.example.com/person/123"),
            expected = Person::class
        )

        println("Retrieved: $person")

        // User manages the lifecycle
        httpClient.close()
    }
}
