package io.github.parkiyong.binaryo.http

import io.github.parkiyong.binaryo.exception.BinaryoTransportException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Enhanced enterprise-ready HTTP transport using JDK HttpClient.
 *
 * Features:
 * - Configurable timeouts and connection pooling
 * - Request/response interceptors for cross-cutting concerns
 * - Better error handling with retry support
 * - Zero external dependencies
 *
 * Example with interceptors:
 * ```
 * val transport = EnhancedJdkHttpTransport(
 *     config = TransportConfig(
 *         connectTimeout = Duration.ofSeconds(5),
 *         requestTimeout = Duration.ofSeconds(30)
 *     ),
 *     requestInterceptor = { req ->
 *         // Add auth headers, logging, etc.
 *         req.header("Authorization", "Bearer token")
 *     }
 * )
 * ```
 */
class EnhancedJdkHttpTransport(
    private val config: TransportConfig = TransportConfig(),
    private val requestInterceptor: RequestInterceptor? = null,
    private val responseInterceptor: ResponseInterceptor? = null,
    private val client: HttpClient = buildClient(config)
) : Transport {

    /**
     * Configuration for HTTP transport behavior.
     */
    data class TransportConfig(
        val connectTimeout: Duration = Duration.ofSeconds(10),
        val requestTimeout: Duration = Duration.ofSeconds(30),
        val followRedirects: Boolean = true,
        val version: HttpClient.Version = HttpClient.Version.HTTP_2,
        val maxRetries: Int = 0, // 0 = no retries
        val retryDelay: Duration = Duration.ofMillis(500)
    )

    /**
     * Interceptor for modifying requests before they are sent.
     */
    fun interface RequestInterceptor {
        fun intercept(builder: HttpRequest.Builder): HttpRequest.Builder
    }

    /**
     * Interceptor for processing responses before they are returned.
     */
    fun interface ResponseInterceptor {
        fun intercept(response: Transport.SimpleResponse): Transport.SimpleResponse
    }

    override fun post(url: URI, body: ByteArray, headers: Map<String, String>): Transport.SimpleResponse {
        return executeWithRetry {
            val builder = HttpRequest.newBuilder(url)
                .timeout(config.requestTimeout)
                .header("Content-Type", "application/octet-stream")

            headers.forEach { (k, v) -> builder.header(k, v) }

            // Apply request interceptor
            val finalBuilder = requestInterceptor?.intercept(builder) ?: builder

            val req = finalBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray())

            val simpleResponse = Transport.SimpleResponse(
                resp.statusCode(),
                resp.body(),
                resp.headers().map()
            )

            // Apply response interceptor
            responseInterceptor?.intercept(simpleResponse) ?: simpleResponse
        }
    }

    override fun get(url: URI, headers: Map<String, String>): Transport.SimpleResponse {
        return executeWithRetry {
            val builder = HttpRequest.newBuilder(url)
                .timeout(config.requestTimeout)

            headers.forEach { (k, v) -> builder.header(k, v) }

            // Apply request interceptor
            val finalBuilder = requestInterceptor?.intercept(builder) ?: builder

            val req = finalBuilder.GET().build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray())

            val simpleResponse = Transport.SimpleResponse(
                resp.statusCode(),
                resp.body(),
                resp.headers().map()
            )

            // Apply response interceptor
            responseInterceptor?.intercept(simpleResponse) ?: simpleResponse
        }
    }

    /**
     * Execute request with retry logic for transient failures.
     * @throws BinaryoTransportException when network or I/O errors occur
     */
    private fun executeWithRetry(block: () -> Transport.SimpleResponse): Transport.SimpleResponse {
        var lastException: Exception? = null
        var attempt = 0

        while (attempt <= config.maxRetries) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                attempt++

                if (attempt < config.maxRetries && isRetryable(e)) {
                    // Exponential backoff: delay * 2^(attempt-1)
                    val delay = config.retryDelay.toMillis() * (1L shl (attempt - 1))
                    Thread.sleep(delay)
                } else {
                    throw wrapException(e)
                }
            }
        }

        throw wrapException(lastException ?: RuntimeException("Retry failed without exception"))
    }

    /**
     * Wrap exception in BinaryoTransportException with appropriate message.
     */
    private fun wrapException(e: Exception): BinaryoTransportException {
        return when (e) {
            is BinaryoTransportException -> e
            is java.net.http.HttpTimeoutException -> BinaryoTransportException(
                "Request timed out: ${e.message}",
                cause = e
            )
            is java.net.ConnectException -> BinaryoTransportException(
                "Failed to connect: ${e.message}",
                cause = e
            )
            is java.io.IOException -> BinaryoTransportException(
                "Network I/O error: ${e.message}",
                cause = e
            )
            else -> BinaryoTransportException(
                "Transport error: ${e.message}",
                cause = e
            )
        }
    }

    /**
     * Determine if an exception is retryable.
     * Retries connection timeouts, but not read timeouts or other errors.
     */
    private fun isRetryable(e: Exception): Boolean {
        return when {
            e is java.net.ConnectException -> true
            e is java.net.http.HttpTimeoutException -> {
                // Only retry connect timeouts, not request timeouts
                e.message?.contains("connect", ignoreCase = true) == true
            }
            else -> false
        }
    }

    companion object {
        private fun buildClient(config: TransportConfig): HttpClient {
            val builder = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout)
                .version(config.version)

            if (config.followRedirects) {
                builder.followRedirects(HttpClient.Redirect.NORMAL)
            }

            return builder.build()
        }
    }
}
