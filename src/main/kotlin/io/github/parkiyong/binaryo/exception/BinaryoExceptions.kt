package io.github.parkiyong.binaryo.exception

/**
 * Base exception for all Binaryo-related errors.
 */
open class BinaryoException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Exception thrown when HTTP transport operations fail.
 * Includes HTTP status code when available.
 */
class BinaryoTransportException(
    message: String,
    val statusCode: Int? = null,
    val responseBody: ByteArray? = null,
    cause: Throwable? = null
) : BinaryoException(message, cause)

/**
 * Exception thrown when serialization or deserialization fails.
 */
class BinaryoSerializationException(
    message: String,
    val targetType: String? = null,
    cause: Throwable? = null
) : BinaryoException(message, cause)

/**
 * Exception thrown when input validation fails.
 */
class BinaryoValidationException(
    message: String,
    cause: Throwable? = null
) : BinaryoException(message, cause)
