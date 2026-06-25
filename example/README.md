# Kronos Example App

A minimal runnable Kotlin application demonstrating Kronos job scheduling with the MongoDB backend.

## Prerequisites

- JDK 19+
- Docker (for MongoDB and Redis)

## Running

1. Start the dependencies:

```bash
docker compose up -d
```

This starts MongoDB on port `27016` and Redis on port `6379`.

2. Run the app:

```bash
./gradlew run
```

## What it does

- Initializes Kronos against the local MongoDB and Redis containers
- Registers two jobs: `SayHello` and `TestJob`
- Schedules `SayHello` as a one-time job and as a periodic job running every minute
- After 2 minutes, cancels the periodic job by ID and by name
- `SayHello.execute()` also schedules `TestJob` from within execution to demonstrate in-cycle scheduling

## Project structure

```
example/
├── app/
│   ├── src/main/kotlin/example/
│   │   └── App.kt          # entry point, job definitions
│   └── build.gradle.kts
├── docker-compose.yml
└── settings.gradle.kts
```

## Adapting for SQL

Swap the import and init call:

```kotlin
// replace:
import kronos.mongo.init
Kronos.init(mongoConnectionString = "...", redisConnectionString = "...")

// with:
import kronos.exposed.ExposedKronosStore
val store = ExposedKronosStore(database = db, cache = RedisCacheClient("redis://..."))
Kronos.init(store = store)
```
