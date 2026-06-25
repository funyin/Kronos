[![Tests](https://github.com/funyin/Kronos/actions/workflows/test.yml/badge.svg)](https://github.com/funyin/Kronos/actions/workflows/test.yml) ![Codecov](https://img.shields.io/codecov/c/github/funyin/Kronos)
[![Netlify Status](https://api.netlify.com/api/v1/badges/e1f7938b-b7df-476c-a301-b3f60802b624/deploy-status)](https://app.netlify.com/sites/kronos-kdock/deploys)

# Kronos

A persistent, distributed job scheduler for Kotlin JVM — without the complexity of cron expressions.

Jobs run with minute-level precision, survive restarts, and work safely across multiple service instances using MongoDB-backed distributed locking.

**[Full documentation →](https://funyin.github.io/Kronos/)**

## Installation

Choose a backend adapter — the core scheduler is pulled in automatically.

### MongoDB

```kotlin
dependencies {
    implementation("com.funyinkash:kronos-mongo:0.0.8")
    implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")
}
```

### SQL (Exposed)

```kotlin
dependencies {
    implementation("com.funyinkash:kronos-exposed:0.0.8")
    implementation("com.funyinkash:kachecontroller-cache-redis:1.0.6")
}
```

### Cache backends

| Artifact | Use case |
|---|---|
| `kachecontroller-cache-redis:1.0.6` | Production |
| `kachecontroller-cache-memory:1.0.6` | Single-instance deployments, local dev, and tests (no Redis needed) |

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
    override val retries = 2  // number of retry attempts on failure

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        val reportId = params["reportId"] as String
        // do work...
        return true  // return false to trigger a retry
    }

    // Called after a successful execution
    override fun onSuccess(cycleNumber: Int, params: Map<String, Any>) { }

    // Called after each failed attempt (while retries remain)
    override fun onFail(cycleNumber: Int, params: Map<String, Any>, exception: Exception?) { }

    // Called after all retries are exhausted
    override fun onRetryFail(cycleNumber: Int, params: Map<String, Any>, exception: Exception?) { }

    // Return true to SKIP this cycle; false (default) to allow it.
    // Use this to express patterns Periodic can't: bi-weekly, "every 2nd Tuesday",
    // feature-flag gating, public holiday checks, or any runtime condition.
    override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean = false

    // Called when the job is dropped (cancelled or maxCycles reached)
    override fun onDrop(cycleNumber: Int, params: Map<String, Any>) { }
}
```

## Scheduling Options

```kotlin
Kronos.schedule(
    jobName = SendReport.name,
    params = mapOf("reportId" to "123"),
    delay = 5_000L,                    // wait 5 s before first run (ms)
    startTime = System.currentTimeMillis() + 60_000L,  // earliest run time (epoch ms, UTC)
    endTime = System.currentTimeMillis() + 3_600_000L, // stop scheduling after this time
    maxCycles = 10,                    // maximum number of executions
    retries = 3,                       // retry attempts per cycle on failure
    overshotAction = OvershotAction.Fire  // what to do if a scheduled time is missed
)
```

## Periodic Schedules

```kotlin
Periodic.everyMinute()
Periodic.everyHour(minute = 30)
Periodic.everyDay(hour = 9, minute = 0)                       // 24-hour UTC
Periodic.everyWeek(dayOfWeek = 1, hour = 9, minute = 0)      // 1 = Mon … 7 = Sun
Periodic.everyMonth(dayOfMonth = 1, hour = 9, minute = 0)
Periodic.everyYear(month = 1, dayOfMonth = 1, hour = 9, minute = 0)
```

All times are **UTC**. Adjust for your timezone when setting `hour`/`minute`.

## Fine-grained scheduling with `challengeRun`

`Periodic` covers the common frequencies (minute, hour, day, week, month, year). For patterns that fall between those — bi-weekly, "the 2nd Tuesday of every month", holiday-aware jobs — use `challengeRun` as a runtime gate.

`challengeRun` is called before each execution. Return `true` to **skip** the cycle; `false` (default) to let it run. The job remains scheduled; only that one cycle is suppressed.

**Bi-weekly** — schedule weekly, skip every other cycle:

```kotlin
override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
    return cycleNumber % 2 != 0  // skip odd cycles, run on even
}
```

**Every Tuesday only** — schedule daily, gate on day of week:

```kotlin
override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
    return LocalDate.now(ZoneOffset.UTC).dayOfWeek != DayOfWeek.TUESDAY
}
```

**2nd Tuesday of the month** — schedule weekly, combine both checks:

```kotlin
override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
    val today = LocalDate.now(ZoneOffset.UTC)
    val isTuesday = today.dayOfWeek == DayOfWeek.TUESDAY
    val isSecond = today.dayOfMonth in 8..14  // 2nd occurrence is always day 8–14
    return !(isTuesday && isSecond)
}
```

This is logic a cron expression cannot represent — and because it's plain Kotlin, you can incorporate feature flags, database lookups, or any runtime condition.

## Missed Jobs (OvershotAction)

Controls what happens when Kronos finds a job whose scheduled time has already passed (e.g. after a restart):

| Value | Behaviour |
|---|---|
| `OvershotAction.Fire` | Run the job immediately |
| `OvershotAction.Drop` | Delete the job (default) |
| `OvershotAction.Nothing` | Leave it in the DB and wait for the next occurrence |

```kotlin
Kronos.schedule(
    jobName = SendReport.name,
    params = mapOf("reportId" to "123"),
    overshotAction = OvershotAction.Fire
)
```

## Managing Jobs

```kotlin
val jobId = Kronos.schedule(...)   // returns the job ID

Kronos.checkJob(jobId!!)           // JSON string of job state
Kronos.allJobs()                   // all scheduled jobs
Kronos.allJobs(SendReport.name)    // jobs filtered by name

Kronos.dropJobId(jobId!!)          // cancel one job
Kronos.dropJob(SendReport.name)    // cancel all jobs with this name
Kronos.dropAll()                   // cancel everything
```

## Pluggable Backends

Swap backends without changing application code:

```kotlin
// MongoDB + Redis
val store = MongoKronosStore(
    mongoConnectionString = "mongodb://localhost:27017",
    cache = RedisCacheClient("redis://localhost:6379"),
    jobsDbName = "myDb",
    cacheExpiry = Duration.ofMinutes(10),
)
Kronos.init(store = store)

// In-memory cache (single-instance, local dev / tests — no Redis needed)
val store = MongoKronosStore(
    mongoConnectionString = "mongodb://localhost:27017",
    cache = InMemoryCacheClient(),
)
Kronos.init(store = store)
```

See [kronos-mongo](kronos-mongo/) and [kronos-exposed](kronos-exposed/) for backend-specific setup, and the [example app](example/) for a runnable end-to-end demo.

## Licence

Kronos is licensed under the [Apache 2.0 Licence](LICENSE.txt)
