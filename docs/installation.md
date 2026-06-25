---
comments: true
---

# Installation

Kronos is split into a core framework and backend adapters. Choose a backend — the core is pulled in transitively. All artifacts are published to Maven Central.

## Backend adapters

!!! tip "Latest version"
    Replace `KRONOS_VERSION` with the [latest release](https://github.com/funyin/Kronos/releases) (currently **0.0.8**) and `KACHE_VERSION` with the latest KacheController release (currently **1.0.6**).

=== "Kotlin Gradle Script"

    ```kotlin
    repositories { mavenCentral() }

    dependencies {
        // Pick one backend — the core kronos API is included automatically
        implementation("com.funyinkash:kronos-mongo:KRONOS_VERSION")
        // or implementation("com.funyinkash:kronos-exposed:KRONOS_VERSION")
    }
    ```

=== "Gradle"

    ```groovy
    repositories { mavenCentral() }

    dependencies {
        implementation "com.funyinkash:kronos-mongo:KRONOS_VERSION"
        // or implementation "com.funyinkash:kronos-exposed:KRONOS_VERSION"
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kronos-mongo</artifactId>
        <version>KRONOS_VERSION</version>
    </dependency>
    ```

## Cache backend

`kronos-mongo` and `kronos-exposed` require a `CacheClient` from KacheController. Pick one:

| Artifact | Use case |
|---|---|
| `kachecontroller-cache-redis` | Production |
| `kachecontroller-cache-memory` | Single-instance deployments, local dev, and tests (no Redis needed) |

```kotlin
// Redis (production / multi-instance)
implementation("com.funyinkash:kachecontroller-cache-redis:KACHE_VERSION")

// In-memory (single-instance, local dev / tests — no Redis needed)
implementation("com.funyinkash:kachecontroller-cache-memory:KACHE_VERSION")
```

## Using with Maven

```xml
<dependencies>
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kronos-mongo</artifactId>
        <version>KRONOS_VERSION</version>
    </dependency>
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kachecontroller-cache-redis</artifactId>
        <version>KACHE_VERSION</version>
    </dependency>
</dependencies>
```

## JVM target only

Kronos currently targets **JVM only** (Java 19 toolchain). JS and Native targets are not yet available.

## Architecture

Kronos follows a pluggable-backend pattern:

| Artifact | Role |
|---|---|
| `kronos` / `kronos-jvm` | Core framework — `Kronos` singleton, `KronosStore` interface, `Job` interface, scheduling, runner, execution |
| `kronos-mongo` | MongoDB `KronosStore` implementation + convenience `Kronos.init(mongoUri, redisUri)` |
| `kronos-exposed` | SQL `KronosStore` implementation via JetBrains Exposed + convenience `Kronos.init(dataSource, cache)` |

The core types (`Kronos`, `Job`, `KronoJob`, etc.) are exposed transitively via `kronos-mongo` and `kronos-exposed` at `compile` scope. You only need to add the backend dependency. If you're implementing a custom `KronosStore`, depend on `kronos` directly.
