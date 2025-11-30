package io.github.parkiyong.binaryo.exception

import kotlin.test.*

class BinaryoExceptionsTest {

    @Test
    fun binaryoException_isRuntimeException() {
        val ex = BinaryoException("test message")
        assertTrue(ex is RuntimeException)
        assertEquals("test message", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun binaryoException_preservesCause() {
        val cause = RuntimeException("original")
        val ex = BinaryoException("wrapper", cause)
        assertEquals("wrapper", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun binaryoTransportException_extendsBinaryoException() {
        val ex = BinaryoTransportException("transport error")
        assertTrue(ex is BinaryoException)
        assertTrue(ex is RuntimeException)
    }

    @Test
    fun binaryoTransportException_includesStatusCode() {
        val responseBody = "error body".toByteArray()
        val ex = BinaryoTransportException(
            "HTTP 500 error",
            statusCode = 500,
            responseBody = responseBody
        )
        assertEquals("HTTP 500 error", ex.message)
        assertEquals(500, ex.statusCode)
        assertContentEquals(responseBody, ex.responseBody)
    }

    @Test
    fun binaryoTransportException_statusCodeCanBeNull() {
        val ex = BinaryoTransportException("connection failed")
        assertNull(ex.statusCode)
        assertNull(ex.responseBody)
    }

    @Test
    fun binaryoTransportException_preservesCause() {
        val cause = java.io.IOException("network error")
        val ex = BinaryoTransportException("transport failed", cause = cause)
        assertSame(cause, ex.cause)
    }

    @Test
    fun binaryoSerializationException_extendsBinaryoException() {
        val ex = BinaryoSerializationException("serialization error")
        assertTrue(ex is BinaryoException)
        assertTrue(ex is RuntimeException)
    }

    @Test
    fun binaryoSerializationException_includesTargetType() {
        val ex = BinaryoSerializationException(
            "Failed to deserialize",
            targetType = "com.example.User"
        )
        assertEquals("Failed to deserialize", ex.message)
        assertEquals("com.example.User", ex.targetType)
    }

    @Test
    fun binaryoSerializationException_targetTypeCanBeNull() {
        val ex = BinaryoSerializationException("generic error")
        assertNull(ex.targetType)
    }

    @Test
    fun binaryoSerializationException_preservesCause() {
        val cause = ClassCastException("type mismatch")
        val ex = BinaryoSerializationException(
            "cast failed",
            targetType = "com.example.Person",
            cause = cause
        )
        assertSame(cause, ex.cause)
        assertEquals("com.example.Person", ex.targetType)
    }

    @Test
    fun binaryoValidationException_extendsBinaryoException() {
        val ex = BinaryoValidationException("validation error")
        assertTrue(ex is BinaryoException)
        assertTrue(ex is RuntimeException)
    }

    @Test
    fun binaryoValidationException_preservesCause() {
        val cause = IllegalArgumentException("invalid input")
        val ex = BinaryoValidationException("validation failed", cause)
        assertEquals("validation failed", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun allExceptions_formProperHierarchy() {
        // All custom exceptions should be catchable by BinaryoException
        val exceptions = listOf(
            BinaryoException("base"),
            BinaryoTransportException("transport"),
            BinaryoSerializationException("serialization"),
            BinaryoValidationException("validation")
        )

        for (ex in exceptions) {
            assertTrue(ex is BinaryoException, "${ex::class.simpleName} should extend BinaryoException")
            assertTrue(ex is RuntimeException, "${ex::class.simpleName} should extend RuntimeException")
        }
    }
}
