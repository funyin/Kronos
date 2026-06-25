---
comments: true
---

# Installation

Kronos is split into a core framework and backend adapters. Choose a backend — the core is pulled in transitively. All artifacts are published to Maven Central.

## Backend adapters

=== "Kotlin Gradle Script"

    ```kotlin
    repositories { mavenCentral() }

    dependencies {
        // Pick one backend — the core kronos API is included automatically
        implementation("com.funyinkash:kronos-mongo:$kronosVersion")
        // or implementation("com.funyinkash:kronos-exposed:$kronosVersion")
    }
    ```

=== "Gradle"

    ```groovy
    repositories { mavenCentral() }

    dependencies {
        implementation "com.funyinkash:kronos-mongo:$kronosVersion"
        // or implementation "com.funyinkash:kronos-exposed:$kronosVersion"
    }
    ```

=== "Maven"

    ```xml
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kronos-mongo</artifactId>
        <version>${kronosVersion}</version>
    </dependency>
    ```

## Cache backend

`kronos-mongo` and `kronos-exposed` require a `CacheClient` from KacheController. Pick one:

| Artifact | Use case |
|---|---|
| `kachecontroller-cache-redis` | Production |
| `kachecontroller-cache-memory` | Local dev / tests |

```kotlin
// Redis (production)
implementation("com.funyinkash:kachecontroller-cache-redis:$kacheVersion")

// In-memory (dev / tests — no Redis needed)
implementation("com.funyinkash:kachecontroller-cache-memory:$kacheVersion")
```

## Using with Maven

```xml
<dependencies>
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kronos-mongo</artifactId>
        <version>${kronosVersion}</version>
    </dependency>
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kachecontroller-cache-redis</artifactId>
        <version>${kacheVersion}</version>
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
