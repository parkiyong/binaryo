package io.github.parkiyong.binaryo.client

import io.github.parkiyong.binaryo.codec.KryoCodec
import io.github.parkiyong.binaryo.exception.BinaryoSerializationException
import io.github.parkiyong.binaryo.exception.BinaryoTransportException
import io.github.parkiyong.binaryo.http.Transport
import java.net.URI
import java.nio.charset.StandardCharsets
import kotlin.reflect.KClass

/**
 * High-level REST client that uses [KryoCodec] to serialize/deserialize
 * objects directly as binary Kryo payloads and a pluggable [Transport] to perform HTTP calls.
 */
class KryoRestClient(
    private val codec: KryoCodec,
    private val transport: Transport
) {
    /**
     * POST a value as binary Kryo payload. Returns raw HTTP response (for callers that
     * don't want automatic decoding).
     */
    fun <T: Any> post(
        url: URI,
        value: T,
        headers: Map<String, String> = emptyMap()
    ): Transport.SimpleResponse {
        val bytes = codec.toBytes(value)
        val extraHeaders = mapOf(
            "Accept" to "application/octet-stream",
            "X-Serializer" to "kryo-binary-v1"
        ) + headers
        return transport.post(url, bytes, extraHeaders)
    }

    /**
     * POST a value and expect a binary Kryo response that decodes to [expected].
     * @throws BinaryoTransportException if response status is not 2xx
     * @throws BinaryoSerializationException if deserialization fails
     */
    fun <T: Any, R: Any> postAndDecode(
        url: URI,
        value: T,
        expected: KClass<R>,
        headers: Map<String, String> = emptyMap()
    ): R {
        val resp = post(url, value, headers)
        if (resp.status !in 200..299) {
            val snippet = resp.body.decodeToStringSafely()
            throw BinaryoTransportException(
                "HTTP ${resp.status}: $snippet",
                statusCode = resp.status,
                responseBody = resp.body
            )
        }
        try {
            return codec.fromBytes(resp.body, expected)
        } catch (e: BinaryoSerializationException) {
            throw e
        } catch (e: Exception) {
            throw BinaryoSerializationException(
                "Failed to deserialize response to type ${expected.qualifiedName}",
                targetType = expected.qualifiedName,
                cause = e
            )
        }
    }

    /**
     * GET and decode a binary Kryo response to [expected].
     * @throws BinaryoTransportException if response status is not 2xx
     * @throws BinaryoSerializationException if deserialization fails
     */
    fun <T: Any> getAndDecode(
        url: URI,
        expected: KClass<T>,
        headers: Map<String, String> = emptyMap()
    ): T {
        val extraHeaders = mapOf("Accept" to "application/octet-stream") + headers
        val resp = transport.get(url, extraHeaders)
        if (resp.status !in 200..299) {
            val snippet = resp.body.decodeToStringSafely()
            throw BinaryoTransportException(
                "HTTP ${resp.status}: $snippet",
                statusCode = resp.status,
                responseBody = resp.body
            )
        }
        try {
            return codec.fromBytes(resp.body, expected)
        } catch (e: BinaryoSerializationException) {
            throw e
        } catch (e: Exception) {
            throw BinaryoSerializationException(
                "Failed to deserialize response to type ${expected.qualifiedName}",
                targetType = expected.qualifiedName,
                cause = e
            )
        }
    }
}

private fun ByteArray.decodeToStringSafely(max: Int = 512): String {
    val s = try { String(this, StandardCharsets.UTF_8) } catch (_: Throwable) { "<${size} bytes>" }
    return if (s.length <= max) s else s.take(max) + "…"
}
