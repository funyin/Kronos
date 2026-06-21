# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kronos is a Kotlin JVM library for distributed job scheduling backed by MongoDB (persistence) and Redis (caching). Jobs run with minute-level precision; the runner ticks every 60 seconds.

- **Group**: `com.funyinkash`
- **Current version**: `0.0.7-SNAPSHOT`
- **Java toolchain**: 19

## Build Commands

```bash
./gradlew build                        # Build and run all checks
./gradlew test                         # Run all tests
./gradlew test --tests "*KronosTest*"  # Run a specific test class
./gradlew test --tests "kronos.SchedulerTest.someTest"  # Run a specific test
./gradlew dokkaHtml                    # Generate API docs
./gradlew publish                      # Publish to Maven Central (requires GPG key)
```

To run the example app:
```bash
cd example && ./gradlew run
```

**Coverage enforcement**: Kover requires ≥85% line coverage; `./gradlew build` will fail if coverage drops below that threshold.

## Architecture

### Core Flow

```
Kronos.init(mongoUri, redisUri)
  └─ starts JobRunner coroutine loop (ticks every minute)
       └─ handleJobs() → fetches pending KronoJobs from MongoDB
            └─ handleJob() → validate() → runJob()
                                              ├─ acquire lock
                                              ├─ job.execute(cycleNumber, params)
                                              ├─ invoke callbacks (onSuccess/onFail/onRetryFail)
                                              └─ reschedule or drop
```

### Key Classes

| File | Role |
|---|---|
| `Kronos.kt` | Singleton entry point. Holds job registry, MongoDB/Redis connections, and the `KacheController` caching layer. |
| `Job.kt` | Interface that consumers implement. Provides lifecycle callbacks: `execute`, `onSuccess`, `onFail`, `onRetryFail`, `challengeRun`, `onDrop`, `periodicJobLoaded`, `onLasCycleDrop`. |
| `Schedule.kt` | Extension functions on `Kronos`: `schedule()` for interval/one-time jobs, `schedulePeriodic()` for cron-like patterns. |
| `KronoJob.kt` | Data class stored in MongoDB. Contains scheduling metadata, cycle tracking, lock counter, and `OvershotAction`. Also defines the `Periodic` builder and `OvershotAction` enum. |
| `JobRunner.kt` | Coroutine loop. `validate()` checks whether a job's scheduled time matches the current minute before dispatching. |
| `RunJob.kt` | Execution logic: lock management, retry loop, callback dispatch, rescheduling for repeated jobs. |

### Concurrency Model

- Lock field on `KronoJob` (incremented atomically in MongoDB) prevents duplicate execution across multiple service instances.
- Each job runs inside a `supervisorScope` so failures are isolated.

### Scheduling Modes

- **`schedule()`** – one-time or interval-based (e.g., every N minutes). Supports `delay`, `startTime`, `endTime`, `maxCycles`, `retries`, `overshotAction`.
- **`schedulePeriodic()`** – cron-like via `Periodic` builder (`everyMinute()`, `everyHour(minute)`, `everyDay(hour, minute)`, `everyWeek(...)`, `everyMonth(...)`, `everyYear(...)`). Validates components (hour 0–23, minute 0–59, dayOfWeek 1–7, dayOfMonth 1–31, month 1–12).
- **`OvershotAction`** – `Fire` (run immediately if missed), `Drop` (skip), `Nothing` (wait for next occurrence).

## Testing

Tests use **JUnit 5** with **MockK** for spying and **Testcontainers** for real MongoDB and Redis instances — there are no in-memory fakes.

| Test file | Coverage |
|---|---|
| `KronosTest.kt` | Integration: initialization, job registration, scheduling, dropping, querying, 10k-job load test |
| `SchedulerTest.kt` | Scheduling precision: all `Periodic` frequencies, `OvershotAction` variants, `maxCycles`, `endTime` |

`TestDataProvider.kt` manages container lifecycle and exposes helpers (`initKronos()`, `registerSampleJob()`, `scheduleSampleJob()`).

`StandardTestDispatcher` is used in `SchedulerTest` to advance virtual time deterministically without real-wall-clock waits.

## Usage Pattern (from example app)

```kotlin
Kronos.init(mongoConnectionString, redisConnectionString)
Kronos.register(MyJob)                                     // register before scheduling
Kronos.schedule(jobName = "my-job", params = mapOf(...))
Kronos.schedulePeriodic(jobName = "my-job", periodic = Periodic.everyDay(hour = 9, minute = 0))

// Management
Kronos.dropJobId(id)   // remove one job
Kronos.dropJob(name)   // remove all jobs with that name
Kronos.dropAll()
Kronos.checkJob(id)    // query status
Kronos.allJobs()
```

## Important Constraints

- All times are **UTC**; consumers must account for timezone offsets when setting hour/minute values.
- Minimum scheduling resolution is **1 minute**.
- Job names must be unique; re-registering the same name throws `IllegalStateException`.
- Publishing to Maven Central requires a GPG private key configured in `gradle.properties`.
- The multiplatform plugin is declared but only the **JVM target** is active; JS/Native targets are commented out.
