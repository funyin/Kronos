# kronos-exposed

SQL backend adapter for [Kronos](https://funyin.github.io/Kronos/) — a persistent, distributed job scheduler for Kotlin JVM.

This module implements the `KronosStore` interface using [Jetbrains Exposed](https://github.com/JetBrains/Exposed) ORM, allowing Kronos to persist jobs in any JDBC-compatible database.

SQLite is bundled as a runtime dependency for local development and testing; swap in any other Exposed-supported driver for production (PostgreSQL, MySQL, etc.).

## Installation

```kotlin
dependencies {
    implementation("com.funyinkash:kronos-exposed:0.0.8")

    // Pick a cache backend:
    implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")   // production
    // or
    implementation("com.funyinkash:kachecontroller-cache-memory:1.0.6")  // single-instance, dev / tests

    // Add your JDBC driver (SQLite is bundled; for others add explicitly):
    runtimeOnly("org.postgresql:postgresql:42.7.2")      // PostgreSQL example
}
```

The core `kronos` artifact is pulled in transitively — you only need this dependency.

## Usage

```kotlin
import kronos.exposed.ExposedKronosStore
import com.funyinkash.kachecontroller.redis.RedisCacheClient
import org.jetbrains.exposed.sql.Database

val db = Database.connect(
    url = "jdbc:postgresql://localhost:5432/mydb",
    driver = "org.postgresql.Driver",
    user = "user",
    password = "password"
)

val store = ExposedKronosStore(
    database = db,
    cache = RedisCacheClient("redis://localhost:6379"),
)
Kronos.init(store = store)
```

### SQLite (local dev / tests)

```kotlin
val db = Database.connect("jdbc:sqlite:./kronos.db", driver = "org.xerial.sqlite.JDBC")
val store = ExposedKronosStore(database = db, cache = InMemoryCacheClient())
Kronos.init(store = store)
```

## Supported Databases

Any database with a JDBC driver and Exposed support:

| Database   | Driver artifact                         |
|------------|-----------------------------------------|
| SQLite     | bundled (`org.xerial:sqlite-jdbc`)      |
| PostgreSQL | `org.postgresql:postgresql`             |
| MySQL      | `com.mysql:mysql-connector-j`           |
| H2         | `com.h2database:h2`                     |

## Requirements

- Java 19+
- Kotlin JVM

## See also

- [Full documentation](https://funyin.github.io/Kronos/)
- [kronos-mongo](../kronos-mongo) — MongoDB backend
- [example app](../example) — working end-to-end example
