# binaryo - Binary Kryo REST Client

A lightweight, high-performance REST client library for Kotlin/JVM that uses Kryo binary serialization for efficient data transfer over HTTP.

## Features

- ✅ **Pure Binary Serialization** - Direct Kryo serialization, no JSON overhead
- ✅ **Minimal External Dependencies** - Only depends on Kryo and Objenesis for serialization; uses JDK 11+ HttpClient (targets JVM 17)
- ✅ **Type-Safe** - Compile-time type checking with Kotlin
- ✅ **Schema Evolution** - Kryo's CompatibleFieldSerializer handles field changes
- ✅ **Thread-Safe** - ThreadLocal Kryo pool for zero-contention performance
- ✅ **Pluggable Transport** - Abstract transport layer for easy testing/mocking
- ✅ **Apache HttpClient Support** - Optional Apache HttpClient 5 integration with user-provided client injection
- ✅ **Enterprise-Ready** - Configurable timeouts, retry logic, interceptors
- ✅ **Lightweight** - Minimal dependencies, small JAR size

## Quick Start

### Maven Dependency

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.parkiyong</groupId>
    <artifactId>binaryo</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### Basic Usage

```kotlin
import io.github.parkiyong.binaryo.client.KryoRestClient
import io.github.parkiyong.binaryo.codec.KryoCodec
import io.github.parkiyong.binaryo.codec.DefaultKryoFactory
import io.github.parkiyong.binaryo.codec.KryoPool
import io.github.parkiyong.binaryo.http.JdkHttpTransport
import java.net.URI

// Define your data class
data class Person(val name: String, val age: Int)

// Setup (do this once, reuse the client)
val pool = KryoPool { DefaultKryoFactory.create() }
val codec = KryoCodec(pool)
val client = KryoRestClient(codec, JdkHttpTransport())

// POST and receive response
val person = Person("Alice", 30)
val response = client.postAndDecode(
    url = URI.create("http://api.example.com/person"),
    value = person,
    expected = Person::class
)

// GET and decode
val retrieved = client.getAndDecode(
    url = URI.create("http://api.example.com/person/123"),
    expected = Person::class
)
```

## API Reference

### KryoCodec

Direct binary serialization codec.

```kotlin
class KryoCodec(private val pool: KryoPool)

// Serialize to bytes
fun <T: Any> toBytes(value: T): ByteArray

// Deserialize from bytes
fun <T: Any> fromBytes(bytes: ByteArray, expected: KClass<T>): T
```

### KryoRestClient

High-level REST client with automatic serialization.

```kotlin
class KryoRestClient(
    private val codec: KryoCodec,
    private val transport: Transport
)

// POST with automatic decoding
fun <T: Any, R: Any> postAndDecode(
    url: URI,
    value: T,
    expected: KClass<R>,
    headers: Map<String, String> = emptyMap()
): R

// GET with automatic decoding
fun <T: Any> getAndDecode(
    url: URI,
    expected: KClass<T>,
    headers: Map<String, String> = emptyMap()
): T

// POST returning raw response
fun <T: Any> post(
    url: URI,
    value: T,
    headers: Map<String, String> = emptyMap()
): Transport.SimpleResponse
```

### KryoPool

Thread-local pool for Kryo instances (Kryo is not thread-safe).

```kotlin
class KryoPool(private val factory: () -> Kryo)

fun borrow(): Kryo
```

### DefaultKryoFactory

Factory for creating configured Kryo instances.

```kotlin
object DefaultKryoFactory {
    fun create(configurer: KryoConfigurer? = null): Kryo
}
```

## Advanced Usage

### Custom Kryo Configuration

```kotlin
val pool = KryoPool {
    DefaultKryoFactory.create { kryo ->
        // Register your classes for better performance
        kryo.register(Person::class.java)
        kryo.register(Address::class.java)
        
        // Add custom serializers
        kryo.register(LocalDate::class.java, LocalDateSerializer())
    }
}
```

### Custom Headers

```kotlin
val response = client.postAndDecode(
    url = uri,
    value = person,
    expected = Person::class,
    headers = mapOf(
        "Authorization" to "Bearer $token",
        "X-Request-ID" to requestId
    )
)
```

### Error Handling

The library provides a custom exception hierarchy for better error categorization:

```kotlin
import io.github.parkiyong.binaryo.exception.*

try {
    val person = client.getAndDecode(uri, Person::class)
    println(person)
} catch (e: BinaryoTransportException) {
    // HTTP or network error
    println("Transport error: ${e.message}")
    e.statusCode?.let { println("HTTP status: $it") }
    e.responseBody?.let { println("Response: ${String(it)}") }
} catch (e: BinaryoSerializationException) {
    // Serialization or deserialization error
    println("Serialization error: ${e.message}")
    e.targetType?.let { println("Target type: $it") }
} catch (e: BinaryoValidationException) {
    // Input validation error (e.g., empty byte array)
    println("Validation error: ${e.message}")
} catch (e: BinaryoException) {
    // Catch-all for any Binaryo-related error
    println("Binaryo error: ${e.message}")
}
```

#### Exception Hierarchy

| Exception | Description | Properties |
|-----------|-------------|------------|
| `BinaryoException` | Base exception for all Binaryo errors | `message`, `cause` |
| `BinaryoTransportException` | HTTP/network transport failures | `statusCode`, `responseBody` |
| `BinaryoSerializationException` | Serialization/deserialization failures | `targetType` |
| `BinaryoValidationException` | Input validation failures | - |

**Note:** Exception types changed in this version. If you were catching `IllegalStateException` or `ClassCastException`, update your code to use `BinaryoTransportException` and `BinaryoSerializationException` respectively.

### Custom Transport

Implement your own transport for testing or different HTTP clients:

```kotlin
class MyCustomTransport : Transport {
    override fun post(url: URI, body: ByteArray, headers: Map<String, String>): Transport.SimpleResponse {
        // Your implementation
    }
    
    override fun get(url: URI, headers: Map<String, String>): Transport.SimpleResponse {
        // Your implementation
    }
}

val client = KryoRestClient(codec, MyCustomTransport())
```

### Enterprise Features (EnhancedJdkHttpTransport)

For production environments requiring advanced features like retry logic, interceptors, and fine-grained timeouts:

```kotlin
import io.github.parkiyong.binaryo.http.EnhancedJdkHttpTransport
import java.time.Duration

// Configure transport with retries and custom timeouts
val transport = EnhancedJdkHttpTransport(
    config = EnhancedJdkHttpTransport.TransportConfig(
        connectTimeout = Duration.ofSeconds(5),
        requestTimeout = Duration.ofSeconds(30),
        maxRetries = 3,
        retryDelay = Duration.ofMillis(500),
        followRedirects = true
    ),
    // Add authentication headers to every request
    requestInterceptor = { builder ->
        builder.header("Authorization", "Bearer $token")
               .header("X-API-Key", apiKey)
    },
    // Log or validate every response
    responseInterceptor = { response ->
        logger.info("Response status: ${response.status}")
        response
    }
)

val client = KryoRestClient(codec, transport)
```

**Features:**
- **Automatic Retry** - Exponential backoff for transient failures
- **Request Interceptors** - Add auth, logging, tracing headers
- **Response Interceptors** - Validate, log, or transform responses
- **Configurable Timeouts** - Per-request and connection timeouts
- **HTTP/2 Support** - Modern protocol with multiplexing
- **Zero Dependencies** - Still uses JDK HttpClient

### Simple Transport (JdkHttpTransport)

For basic use cases without enterprise features:

```kotlin
import io.github.parkiyong.binaryo.http.JdkHttpTransport

val transport = JdkHttpTransport()
val client = KryoRestClient(codec, transport)
```

### Apache HttpClient Transport

For users who prefer Apache HttpClient 5 or have existing Apache HttpClient configurations:

```kotlin
import io.github.parkiyong.binaryo.http.ApacheHttpClientTransport
import org.apache.hc.client5.http.impl.classic.HttpClients

// User creates and configures their own HttpClient
val httpClient = HttpClients.createDefault()

// Pass to transport - library doesn't manage the client lifecycle
val transport = ApacheHttpClientTransport(httpClient)
val client = KryoRestClient(codec, transport)

// User is responsible for closing the HttpClient when done
httpClient.close()
```

**Advanced Apache HttpClient Configuration:**

```kotlin
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.apache.hc.core5.util.Timeout

// Configure connection pooling
val connectionManager = PoolingHttpClientConnectionManager()
connectionManager.maxTotal = 100
connectionManager.defaultMaxPerRoute = 20

// Configure timeouts
val requestConfig = RequestConfig.custom()
    .setConnectionRequestTimeout(Timeout.ofSeconds(5))
    .setResponseTimeout(Timeout.ofSeconds(30))
    .build()

// Create custom client with full control
val httpClient = HttpClients.custom()
    .setConnectionManager(connectionManager)
    .setDefaultRequestConfig(requestConfig)
    .setUserAgent("MyApp/1.0")
    .build()

val transport = ApacheHttpClientTransport(httpClient)
val client = KryoRestClient(codec, transport)
```

**Note:** Apache HttpClient 5 is an optional dependency. Add it to your `pom.xml` if you want to use `ApacheHttpClientTransport`:

```xml
<dependency>
    <groupId>org.apache.httpcomponents.client5</groupId>
    <artifactId>httpclient5</artifactId>
    <version>5.3.1</version>
</dependency>
```

## Testing

The library includes comprehensive tests:

```bash
mvnw clean test
```

Test coverage:
- Binary codec serialization/deserialization
- REST client HTTP operations
- Error handling
- Type safety
- Complex objects and nullable fields

## Server-Side Usage

The library provides a `KryoServerCodec` to handle server-side Kryo serialization and deserialization in a framework-agnostic way.

### Basic Setup

```kotlin
import io.github.parkiyong.binaryo.codec.DefaultKryoFactory
import io.github.parkiyong.binaryo.codec.KryoPool
import io.github.parkiyong.binaryo.server.KryoServerCodec

// 1. Create a KryoPool (reuse this instance)
val pool = KryoPool { DefaultKryoFactory.create() }

// 2. Create a server-side codec
val serverCodec = KryoServerCodec(pool)

// Example data class
data class User(val id: String, val name: String)

// 3. In your web framework (e.g., Ktor, Spring Boot):
//    - Get the raw request body as a ByteArray
//    - Deserialize it using fromRequest()
val requestBytes: ByteArray = // ... get from framework
val user = serverCodec.fromRequest(requestBytes, User::class)

// 4. To send a response:
//    - Serialize your response object using toResponse()
//    - Send the resulting ByteArray with "application/octet-stream"
val responseUser = User("123", "Alice")
val responseBytes = serverCodec.toResponse(responseUser)
// ... send responseBytes with correct content type
```

The `KryoServerCodec` provides two main methods:
- `fromRequest(bytes: ByteArray, expected: KClass<T>): T`: Deserializes a request body.
- `toResponse(value: T): ByteArray`: Serializes a response object.

## Project Structure

```
src/
├── main/kotlin/io/github/parkiyong/binaryo/
│   ├── client/
│   │   └── KryoRestClient.kt            # REST client
│   ├── codec/
│   │   └── KryoSupport.kt               # Kryo codec, pool, factory
│   ├── examples/
│   │   └── TransportExamples.kt         # Usage examples
│   ├── exception/
│   │   └── BinaryoExceptions.kt         # Custom exceptions
│   ├── http/
│   │   ├── Transport.kt                 # Transport interface
│   │   ├── JdkHttpTransport.kt          # JDK HttpClient transport
│   │   ├── EnhancedJdkHttpTransport.kt  # Enterprise JDK transport
│   │   └── ApacheHttpClientTransport.kt # Apache HttpClient transport
│   └── server/
│       └── KryoServerCodec.kt           # Server-side codec
└── test/kotlin/io/github/parkiyong/binaryo/
    ├── client/
    │   └── KryoRestClientTest.kt
    ├── codec/
    │   └── KryoCodecTest.kt
    ├── exception/
    │   └── BinaryoExceptionsTest.kt
    ├── http/
    │   ├── EnhancedJdkHttpTransportTest.kt
    │   └── ApacheHttpClientTransportTest.kt
    └── server/
        └── KryoServerCodecTest.kt
```

## Dependencies

### Production
- **Kryo** (5.6.2) - Binary serialization framework
- **Objenesis** (3.3) - Required by Kryo for object instantiation
- **Kotlin stdlib** (2.2.21)

### Optional
- **Apache HttpClient 5** (5.5.1) - Alternative HTTP client (only if using `ApacheHttpClientTransport`)

### Test
- **kotlin-test-junit5** (2.2.21)

## HTTP Protocol

### Content-Type
All requests use `application/octet-stream` for binary Kryo payloads.

### Custom Headers
- `X-Serializer: kryo-binary-v1` - Indicates the serialization format
- `Accept: application/octet-stream` - Expected response format

### Response Codes
- `2xx` - Success, response body deserialized automatically
- `4xx/5xx` - Error, throws `BinaryoTransportException` with status code and response body

## Performance Characteristics

- **Binary Size**: Typically 30-50% smaller than JSON
- **Serialization Speed**: 2-5x faster than JSON (depending on object complexity)
- **Thread Safety**: ThreadLocal pool ensures zero lock contention
- **Memory**: Efficient, reuses Kryo instances per thread

## Schema Evolution

Kryo's `CompatibleFieldSerializer` allows safe schema evolution:

✅ **Safe Changes**:
- Adding new fields with default values
- Removing fields
- Renaming fields (with `@FieldName` annotation)
- Changing field order

⚠️ **Breaking Changes**:
- Changing field types
- Making nullable fields non-nullable

## Requirements

- **JVM**: 17+
- **Kotlin**: 1.9+ (built with 2.2.20)
- **Maven**: 3.6+

## Building

```bash
# Build JAR
mvnw clean package

# Run tests
mvnw test

# Full verification
mvnw clean verify
```

## License

Apache License 2.0 - See `LICENSE` file for details.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Run `mvnw clean verify` to ensure all tests pass
5. Submit a pull request

## Support

For issues, questions, or contributions, please open an issue on the project repository.

---

**binaryo** - Fast, efficient, binary REST communication for Kotlin/JVM.

