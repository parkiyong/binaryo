package io.github.parkiyong.binaryo.codec

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.io.Input
import com.esotericsoftware.kryo.io.Output
import com.esotericsoftware.kryo.serializers.CompatibleFieldSerializer
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy
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
    Output(256, -1).use { out ->
        writeClassAndObject(out, value)
        out.flush()
        return out.toBytes()
    }
}

@Suppress("UNCHECKED_CAST")
fun <T: Any> Kryo.readAny(bytes: ByteArray, expected: KClass<T>): T {
    Input(bytes).use { input ->
        val obj = readClassAndObject(input)
        return expected.java.cast(obj)
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
     */
    fun <T: Any> fromBytes(bytes: ByteArray, expected: KClass<T>): T {
        val kryo = pool.borrow()
        return kryo.readAny(bytes, expected)
    }
}
