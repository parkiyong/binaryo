package io.github.parkiyong.binaryo.codec

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
import io.github.parkiyong.binaryo.exception.BinaryoSerializationException
import io.github.parkiyong.binaryo.exception.BinaryoValidationException
import org.objenesis.strategy.StdInstantiatorStrategy
import kotlin.reflect.KClass

/**
 * Factory/configurer for Kryo instances. Kryo is NOT thread-safe; use [KryoPool].
 */
fun interface KryoConfigurer { fun configure(kryo: Kryo) }

object DefaultKryoFactory {
    fun create(configurer: KryoConfigurer? = null): Kryo = Kryo().apply {
        // Compatible serializer tolerates field additions/removals better across versions
        setDefaultSerializer(CompatibleFieldSerializer::class.java)
        isRegistrationRequired = false
        references = true
        // Allow constructing classes without no-arg constructors (common for Kotlin data classes)
        instantiatorStrategy = DefaultInstantiatorStrategy(StdInstantiatorStrategy())
        configurer?.configure(this)
    }
}

/**
 * Very small pool backed by ThreadLocal for zero-contention performance.
 */
class KryoPool(private val factory: () -> Kryo) {
    private val local = ThreadLocal.withInitial(factory)
    fun borrow(): Kryo = local.get()
}

fun Kryo.writeAny(value: Any): ByteArray {
    try {
        Output(256, -1).use { out ->
            writeClassAndObject(out, value)
            out.flush()
            return out.toBytes()
        }
    } catch (e: Exception) {
        throw BinaryoSerializationException(
            "Failed to serialize object of type ${value::class.qualifiedName}",
            targetType = value::class.qualifiedName,
            cause = e
        )
    }
}

@Suppress("UNCHECKED_CAST")
fun <T: Any> Kryo.readAny(bytes: ByteArray, expected: KClass<T>): T {
    try {
        Input(bytes).use { input ->
            val obj = readClassAndObject(input)
            return expected.java.cast(obj)
        }
    } catch (e: ClassCastException) {
        throw BinaryoSerializationException(
            "Failed to deserialize to type ${expected.qualifiedName}: type mismatch",
            targetType = expected.qualifiedName,
            cause = e
        )
    } catch (e: Exception) {
        throw BinaryoSerializationException(
            "Failed to deserialize to type ${expected.qualifiedName}",
            targetType = expected.qualifiedName,
            cause = e
        )
    }
}

/**
 * Simple codec that serializes objects directly to Kryo binary format.
 * No JSON envelope, no Base64 encoding - just pure binary Kryo serialization.
 */
class KryoCodec(private val pool: KryoPool) {
    /**
     * Serialize [value] to binary Kryo format.
     */
    fun <T: Any> toBytes(value: T): ByteArray {
        val kryo = pool.borrow()
        return kryo.writeAny(value)
    }

    /**
     * Deserialize binary Kryo data back to an object of [expected] type.
     * @throws BinaryoValidationException if the input byte array is empty
     * @throws BinaryoSerializationException if deserialization fails
     */
    fun <T: Any> fromBytes(bytes: ByteArray, expected: KClass<T>): T {
        if (bytes.isEmpty()) {
            throw BinaryoValidationException("Cannot deserialize empty byte array")
        }
        val kryo = pool.borrow()
        return kryo.readAny(bytes, expected)
    }
}
