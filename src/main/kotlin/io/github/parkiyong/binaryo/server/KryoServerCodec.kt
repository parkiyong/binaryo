package io.github.parkiyong.binaryo.server

import io.github.parkiyong.binaryo.codec.KryoCodec
import io.github.parkiyong.binaryo.codec.KryoPool
import kotlin.reflect.KClass

/**
 * Server-side codec that deserializes request bodies and serializes response bodies
 * using Kryo binary format.
 *
 * This class is designed to be framework-agnostic. It provides clear, intention-revealing
 * methods for common server-side operations. It leverages the core [KryoCodec] for the
 * underlying serialization logic.
 *
 * @param codec The core [KryoCodec] instance to use for serialization.
 */
class KryoServerCodec(private val codec: KryoCodec) {
    /**
     * Creates a new [KryoServerCodec] with a default [KryoCodec] instance
     * configured with the given [KryoPool].
     *
     * @param pool The [KryoPool] to use for managing Kryo instances.
     */
    constructor(pool: KryoPool) : this(KryoCodec(pool))

    /**
     * Deserializes a binary Kryo payload from a request body into an object of the specified type.
     *
     * @param T The expected type of the deserialized object.
     * @param bytes The raw byte array from the request body.
     * @param expected The [KClass] of the expected type.
     * @return The deserialized object.
     * @throws ClassCastException if the deserialized object is not of the expected type.
     */
    fun <T : Any> fromRequest(bytes: ByteArray, expected: KClass<T>): T {
        return codec.fromBytes(bytes, expected)
    }

    /**
     * Serializes a response object into a binary Kryo payload.
     *
     * @param T The type of the object to serialize.
     * @param value The object to serialize.
     * @return A byte array containing the binary Kryo payload.
     */
    fun <T : Any> toResponse(value: T): ByteArray {
        return codec.toBytes(value)
    }
}
