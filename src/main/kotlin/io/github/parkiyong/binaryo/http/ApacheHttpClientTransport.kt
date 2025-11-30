package io.github.parkiyong.binaryo.http

import org.apache.hc.client5.http.classic.HttpClient
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.apache.hc.core5.http.io.entity.EntityUtils
import java.net.URI

/**
 * Transport implementation backed by Apache HttpClient 5.
 *
 * This transport does NOT instantiate the HttpClient itself - users must provide
 * their own pre-configured client instance. This allows full control over:
 * - Connection pooling
 * - Timeouts
 * - SSL/TLS configuration
 * - Proxy settings
 * - Authentication
 *
 * Example:
 * ```
 * // User creates and configures their own client
 * val httpClient = HttpClients.custom()
 *     .setConnectionManager(...)
 *     .setDefaultRequestConfig(...)
 *     .build()
 *
 * // Pass it to the transport
 * val transport = ApacheHttpClientTransport(httpClient)
 * val client = KryoRestClient(codec, transport)
 * ```
 *
 * Note: The caller is responsible for closing the HttpClient when done.
 */
class ApacheHttpClientTransport(
    private val httpClient: HttpClient
) : Transport {

    override fun post(url: URI, body: ByteArray, headers: Map<String, String>): Transport.SimpleResponse {
        val httpPost = HttpPost(url)
        httpPost.entity = ByteArrayEntity(body, ContentType.APPLICATION_OCTET_STREAM)
        // Explicitly set Content-Type header unless user provided one (case-insensitive)
        if (headers.keys.none { it.equals("Content-Type", ignoreCase = true) }) {
            httpPost.setHeader("Content-Type", "application/octet-stream")
        }
        headers.forEach { (k, v) -> httpPost.setHeader(k, v) }

        return httpClient.execute(httpPost) { response ->
            val responseHeaders = response.headers
                .groupBy({ it.name }, { it.value })

            Transport.SimpleResponse(
                status = response.code,
                body = response.entity?.let { EntityUtils.toByteArray(it) } ?: ByteArray(0),
                headers = responseHeaders
            )
        }
    }

    override fun get(url: URI, headers: Map<String, String>): Transport.SimpleResponse {
        val httpGet = HttpGet(url)
        headers.forEach { (k, v) -> httpGet.setHeader(k, v) }

        return httpClient.execute(httpGet) { response ->
            val responseHeaders = response.headers
                .groupBy({ it.name }, { it.value })

            Transport.SimpleResponse(
                status = response.code,
                body = response.entity?.let { EntityUtils.toByteArray(it) } ?: ByteArray(0),
                headers = responseHeaders
            )
        }
    }
}
