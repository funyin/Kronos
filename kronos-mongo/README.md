# kronos-mongo

MongoDB backend adapter for [Kronos](https://funyin.github.io/Kronos/) — a persistent, distributed job scheduler for Kotlin JVM.

This module implements the `KronosStore` interface using MongoDB and provides a convenience `init` extension that wires everything together in one call.

## Installation

```kotlin
dependencies {
    implementation("com.funyinkash:kronos-mongo:0.0.8")

    // Pick a cache backend:
    implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")   // production
    // or
    implementation("com.funyinkash:kachecontroller-cache-memory:1.0.6")  // single-instance, dev / tests
}
```

The core `kronos` artifact is pulled in transitively — you only need this dependency.

## Usage

### Convenience initializer (recommended)

```kotlin
import kronos.mongo.init

Kronos.init(
    mongoConnectionString = "mongodb://localhost:27017",
    redisConnectionString = "redis://localhost:6379",
    jobsDbName = "myDb",            // optional, defaults to "kronos"
    cacheExpiry = Duration.ofMinutes(10)  // optional
)
```

### Manual store setup

Use this when you need a custom `CacheClient` (e.g. in-memory for tests):

```kotlin
import kronos.mongo.MongoKronosStore
import com.funyinkash.kachecontroller.memory.InMemoryCacheClient

val store = MongoKronosStore(
    mongoConnectionString = "mongodb://localhost:27017",
    cache = InMemoryCacheClient(),
    jobsDbName = "myDb",
)
Kronos.init(store = store)
```

## Requirements

- MongoDB 4.4+
- Java 19+
- Kotlin JVM

## Docker Compose (local dev)

```yaml
services:
  mongo:
    image: mongo:7
    ports:
      - "27017:27017"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
```

## See also

- [Full documentation](https://funyin.github.io/Kronos/)
- [kronos-exposed](../kronos-exposed) — SQL backend via Jetbrains Exposed
- [example app](../example) — working end-to-end example
