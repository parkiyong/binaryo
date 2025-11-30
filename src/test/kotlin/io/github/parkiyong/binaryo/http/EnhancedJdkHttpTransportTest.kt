package io.github.parkiyong.binaryo.http

import java.net.URI
import java.time.Duration
import kotlin.test.*

class EnhancedJdkHttpTransportTest {

    @Test
    fun requestInterceptor_canModifyHeaders() {
        var interceptorCalled = false

        val interceptor = EnhancedJdkHttpTransport.RequestInterceptor { builder ->
            interceptorCalled = true
            builder.header("X-Custom-Header", "test-value")
        }

        val transport = EnhancedJdkHttpTransport(
            requestInterceptor = interceptor
        )

        // Note: This will fail to connect, but that's okay for testing interceptor call
        try {
            transport.get(URI.create("http://localhost:99999/test"))
        } catch (e: Exception) {
            // Expected - we're just testing interceptor was called
        }

        assertTrue(interceptorCalled, "Request interceptor should be called")
    }

    @Test
    fun responseInterceptor_canModifyResponse() {
        val transport = EnhancedJdkHttpTransport(
            responseInterceptor = EnhancedJdkHttpTransport.ResponseInterceptor { response ->
                Transport.SimpleResponse(
                    status = 999, // Modified status
                    body = response.body,
                    headers = response.headers
                )
            }
        )

        // Would need a real server to test fully
        // This demonstrates the API structure
        // TODO: Test with MOCK server
    }

    @Test
    fun config_hasReasonableDefaults() {
        val config = EnhancedJdkHttpTransport.TransportConfig()

        assertEquals(Duration.ofSeconds(10), config.connectTimeout)
        assertEquals(Duration.ofSeconds(30), config.requestTimeout)
        assertTrue(config.followRedirects)
        assertEquals(0, config.maxRetries)
    }

    @Test
    fun config_canBeCustomized() {
        val config = EnhancedJdkHttpTransport.TransportConfig(
            connectTimeout = Duration.ofSeconds(5),
            requestTimeout = Duration.ofSeconds(60),
            followRedirects = false,
            maxRetries = 3,
            retryDelay = Duration.ofMillis(100)
        )

        assertEquals(Duration.ofSeconds(5), config.connectTimeout)
        assertEquals(Duration.ofSeconds(60), config.requestTimeout)
        assertFalse(config.followRedirects)
        assertEquals(3, config.maxRetries)
        assertEquals(Duration.ofMillis(100), config.retryDelay)
    }
}
