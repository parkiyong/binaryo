package io.github.parkiyong.binaryo.http

import java.net.URI

/**
 * Minimal synchronous HTTP transport abstraction.
 * Keeps the core library independent of any specific HTTP client implementation.
 */
interface Transport {
    data class SimpleResponse(
        val status: Int,
        val body: ByteArray,
        val headers: Map<String, List<String>>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SimpleResponse

            if (status != other.status) return false
            if (!body.contentEquals(other.body)) return false
            if (headers != other.headers) return false

            return true
        }

        override fun hashCode(): Int {
            var result = status
            result = 31 * result + body.contentHashCode()
            result = 31 * result + headers.hashCode()
            return result
        }
    }

    fun post(url: URI, body: ByteArray, headers: Map<String, String> = emptyMap()): SimpleResponse

    fun get(url: URI, headers: Map<String, String> = emptyMap()): SimpleResponse
}
