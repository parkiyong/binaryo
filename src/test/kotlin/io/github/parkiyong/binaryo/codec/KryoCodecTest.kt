package io.github.parkiyong.binaryo.codec

import io.github.parkiyong.binaryo.exception.BinaryoSerializationException
import io.github.parkiyong.binaryo.exception.BinaryoValidationException
import kotlin.test.*

private data class User(val name: String, val age: Int)
private data class Item(val x: String)

class KryoCodecTest {

    private val pool = KryoPool { DefaultKryoFactory.create() }
    private val codec = KryoCodec(pool)

    @Test
    fun roundTrip_preservesObject() {
        val original = User("Ann", 30)
        val bytes = codec.toBytes(original)
        val restored = codec.fromBytes(bytes, User::class)
        assertEquals(original, restored)
    }

    @Test
    fun toBytes_producesBinaryData() {
        val original = User("Bob", 25)
        val bytes = codec.toBytes(original)
        assertTrue(bytes.isNotEmpty())
        // Binary Kryo data should not be valid UTF-8 text
        assertFails {
            bytes.toString(Charsets.UTF_8).also { require(it.contains("Bob")) }
        }
    }

    @Test
    fun fromBytes_typeMismatch_throwsSerializationException() {
        val original = User("Dan", 18)
        val bytes = codec.toBytes(original)
        val ex = assertFailsWith<BinaryoSerializationException> {
            codec.fromBytes(bytes, Item::class)
        }
        assertTrue(ex.message!!.contains("Item"))
        assertEquals("io.github.parkiyong.binaryo.codec.Item", ex.targetType)
    }

    @Test
    fun roundTrip_complexObject() {
        data class Address(val street: String, val city: String)
        data class Employee(val name: String, val age: Int, val address: Address, val tags: List<String>)

        val original = Employee(
            name = "Alice",
            age = 35,
            address = Address("123 Main St", "Springfield"),
            tags = listOf("kotlin", "java", "rust")
        )

        val bytes = codec.toBytes(original)
        val restored = codec.fromBytes(bytes, Employee::class)
        assertEquals(original, restored)
    }

    @Test
    fun roundTrip_nullableFields() {
        data class Optional(val name: String, val nickname: String?)

        val withNull = Optional("Robert", null)
        val bytesNull = codec.toBytes(withNull)
        val restoredNull = codec.fromBytes(bytesNull, Optional::class)
        assertEquals(withNull, restoredNull)

        val withValue = Optional("Robert", "Bob")
        val bytesValue = codec.toBytes(withValue)
        val restoredValue = codec.fromBytes(bytesValue, Optional::class)
        assertEquals(withValue, restoredValue)
    }

    @Test
    fun fromBytes_emptyArray_throwsValidationException() {
        val ex = assertFailsWith<BinaryoValidationException> {
            codec.fromBytes(ByteArray(0), User::class)
        }
        assertTrue(ex.message!!.contains("empty"))
    }
}
