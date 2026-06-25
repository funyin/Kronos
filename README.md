[![Tests](https://github.com/funyin/Kronos/actions/workflows/test.yml/badge.svg)](https://github.com/funyin/Kronos/actions/workflows/test.yml) ![Codecov](https://img.shields.io/codecov/c/github/funyin/Kronos)
[![Netlify Status](https://api.netlify.com/api/v1/badges/e1f7938b-b7df-476c-a301-b3f60802b624/deploy-status)](https://app.netlify.com/sites/kronos-kdock/deploys)

# Kronos

A persistent, distributed job scheduler for Kotlin JVM — without the complexity of cron expressions.

Jobs run with minute-level precision, survive restarts, and work safely across multiple service instances.

## Installation

Choose a backend adapter — the core scheduler is pulled in automatically:

=== "MongoDB"

    ```kotlin
    dependencies {
        implementation("com.funyinkash:kronos-mongo:0.0.8")
        implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")
    }
    ```

=== "SQL (Exposed)"

    ```kotlin
    dependencies {
        implementation("com.funyinkash:kronos-exposed:0.0.8")
        implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")
    }
    ```

Requires a `CacheClient` from KacheController:

| Artifact | Use case |
|---|---|
| `kachecontroller-cache-redis` | Production |
| `kachecontroller-cache-memory` | Local dev / tests |

```kotlin
// Redis (production)
implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")

// In-memory (dev / tests — no Redis needed)
implementation("com.funyinkash:kachecontroller-cache-memory:1.0.6")
```

> **Architecture**: `kronos` contains the core framework (interfaces, scheduling engine, runner).
> Backend adapters (`kronos-mongo`, `kronos-exposed`) implement the storage contract and
> expose the core types transitively — you only need the backend dependency.

## Quick Start

```kotlin
import kronos.mongo.init  // convenience extension from kronos-mongo

// 1. Initialize once at application startup
Kronos.init(
    mongoConnectionString = "mongodb://localhost:27017",
    redisConnectionString = "redis://localhost:6379"
)

// 2. Register your job
Kronos.register(SendReport)

// 3. Schedule it
Kronos.schedule(
    jobName = SendReport.name,
    params = mapOf("reportId" to "123")
)

// Periodic: every day at 09:00 UTC
Kronos.schedulePeriodic(
    jobName = SendReport.name,
    periodic = Periodic.everyDay(hour = 9, minute = 0),
    params = mapOf("reportId" to "123")
)
```

## Defining a Job

```kotlin
object SendReport : Job {
    override val name = "send-report"
    override val retries = 2

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        val reportId = params["reportId"] as String
        // do work...
        return true  // return false to trigger retry
    }

    override fun onSuccess(cycleNumber: Int, params: Map<String, Any>) { /* ... */ }
    override fun onFail(cycleNumber: Int, params: Map<String, Any>, exception: Exception?) { /* ... */ }
}
```

## Periodic Schedules

```kotlin
Periodic.everyMinute()
Periodic.everyHour(minute = 30)
Periodic.everyDay(hour = 9, minute = 0)          // 24-hour UTC
Periodic.everyWeek(dayOfWeek = 1, hour = 9, minute = 0)   // 1=Mon … 7=Sun
Periodic.everyMonth(dayOfMonth = 1, hour = 9, minute = 0)
Periodic.everyYear(month = 1, dayOfMonth = 1, hour = 9, minute = 0)
```

All times are **UTC**. Adjust for your timezone when setting `hour`/`minute`.

## Managing Jobs

```kotlin
val jobId = Kronos.schedule(...)   // returns the job ID

Kronos.checkJob(jobId!!)           // JSON string of job state
Kronos.allJobs()                   // all scheduled jobs
Kronos.allJobs(SendReport.name)    // jobs by name

Kronos.dropJobId(jobId!!)          // cancel one job
Kronos.dropJob(SendReport.name)    // cancel all jobs with this name
Kronos.dropAll()                   // cancel everything
```

## Missed Jobs (OvershotAction)

Controls what happens when Kronos finds a job whose scheduled time has passed (e.g. after a restart):

```kotlin
Kronos.schedule(
    jobName = SendReport.name,
    params = mapOf("reportId" to "123"),
    overshotAction = OvershotAction.Fire   // run immediately
    // OvershotAction.Drop                 // delete it (default)
    // OvershotAction.Nothing              // leave in DB
)
```

## Pluggable Backends

Kronos separates the scheduler from its storage. Swap backends without changing application code:

```kotlin
// MongoDB + any CacheClient
val store = MongoKronosStore(
    mongoConnectionString = "mongodb://localhost:27017",
    cache = RedisCacheClient("redis://localhost:6379"),
    jobsDbName = "myDb",
    cacheExpiry = Duration.ofMinutes(10),
)
Kronos.init(store = store)

// In-memory cache instead of Redis (e.g. local dev)
val store = MongoKronosStore(
    mongoConnectionString = "mongodb://localhost:27017",
    cache = InMemoryCacheClient(),
)
```

## Licence

Kronos is licensed under the [Apache 2.0 Licence](LICENSE.txt)
