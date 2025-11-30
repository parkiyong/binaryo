package io.github.parkiyong.binaryo.server

import io.github.parkiyong.binaryo.codec.DefaultKryoFactory
import io.github.parkiyong.binaryo.codec.KryoPool
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KryoServerCodecTest {

    private lateinit var serverCodec: KryoServerCodec

    // Define a simple data class for testing
    data class Person(val name: String, val age: Int)

    @BeforeTest
    fun setUp() {
        // Create a KryoPool with the default factory
        val pool = KryoPool { DefaultKryoFactory.create() }
        // Initialize the server codec
        serverCodec = KryoServerCodec(pool)
    }

    @Test
    fun `fromRequest should deserialize a valid byte array to an object`() {
        // 1. Arrange
        val originalPerson = Person("Alice", 30)
        // Manually serialize the object to get a byte array
        val requestBytes = serverCodec.toResponse(originalPerson)

        // 2. Act
        val deserializedPerson = serverCodec.fromRequest(requestBytes, Person::class)

        // 3. Assert
        assertIs<Person>(deserializedPerson)
        assertEquals("Alice", deserializedPerson.name)
        assertEquals(30, deserializedPerson.age)
        assertEquals(originalPerson, deserializedPerson)
    }

    @Test
    fun `toResponse should serialize an object to a byte array`() {
        // 1. Arrange
        val person = Person("Bob", 42)

        // 2. Act
        val responseBytes = serverCodec.toResponse(person)

        // 3. Assert
        // Deserialize the byte array back to an object to verify correctness
        val deserializedPerson = serverCodec.fromRequest(responseBytes, Person::class)
        assertEquals(person, deserializedPerson)
    }

    @Test
    fun `fromRequest with type mismatch should throw ClassCastException`() {
        // 1. Arrange
        data class Item(val x: String)
        val originalPerson = Person("Dan", 18)
        val requestBytes = serverCodec.toResponse(originalPerson)

        // 2. Act & 3. Assert
        assertFailsWith<ClassCastException> {
            serverCodec.fromRequest(requestBytes, Item::class)
        }
    }

    @Test
    fun `roundTrip should preserve complex objects`() {
        // 1. Arrange
        data class Address(val street: String, val city: String)
        data class Employee(val name: String, val age: Int, val address: Address, val tags: List<String>)

        val originalEmployee = Employee(
            name = "Alice",
            age = 35,
            address = Address("123 Main St", "Springfield"),
            tags = listOf("kotlin", "java", "rust")
        )

        // 2. Act
        val requestBytes = serverCodec.toResponse(originalEmployee)
        val deserializedEmployee = serverCodec.fromRequest(requestBytes, Employee::class)

        // 3. Assert
        assertEquals(originalEmployee, deserializedEmployee)
    }

    @Test
    fun `roundTrip should handle nullable fields`() {
        // 1. Arrange
        data class Optional(val name: String, val nickname: String?)

        // Case 1: Nullable field is null
        // 2. Act
        val withNull = Optional("Robert", null)
        val bytesNull = serverCodec.toResponse(withNull)
        val restoredNull = serverCodec.fromRequest(bytesNull, Optional::class)
        // 3. Assert
        assertEquals(withNull, restoredNull)

        // Case 2: Nullable field has a value
        // 2. Act
        val withValue = Optional("Robert", "Bob")
        val bytesValue = serverCodec.toResponse(withValue)
        val restoredValue = serverCodec.fromRequest(bytesValue, Optional::class)
        // 3. Assert
        assertEquals(withValue, restoredValue)
    }
}
