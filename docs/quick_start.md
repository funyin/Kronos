---
comments: true
---

## 1. Initialize

Call `init` once at application startup. It throws `IllegalStateException` if called more than once.

```kotlin
import kronos.mongo.init  // extension from kronos-mongo

Kronos.init(
    mongoConnectionString = "mongodb://localhost:27017",
    redisConnectionString = "redis://localhost:6379"
)
```

To handle errors from background job execution:

```kotlin
Kronos.onError = { throwable -> logger.error("Kronos error", throwable) }
```

## 2. Define a Job

Implement the `Job` interface. The `name` must be unique across all registered jobs.

```kotlin
object SendReport : Job {
    override val name = "send-report"
    override val retries = 2  // retry up to 2 times on failure

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        val reportId = params["reportId"] as String
        // do work...
        return true  // false triggers a retry
    }
}
```

## 3. Register

Jobs must be registered before they can be scheduled. Registration is in-memory only — do it on every startup.

```kotlin
Kronos.register(SendReport)
```

## 4. Schedule

```kotlin
// One-time, runs immediately
Kronos.schedule(
    jobName = SendReport.name,
    params = mapOf("reportId" to "abc")
)

// One-time, delayed by 5 minutes
Kronos.schedule(
    jobName = SendReport.name,
    delay = 5.minutes,
    params = mapOf("reportId" to "abc")
)

// Repeating every 30 minutes, max 10 times
Kronos.schedule(
    jobName = SendReport.name,
    interval = 30.minutes,
    maxCycles = 10,
    params = mapOf("reportId" to "abc")
)

// Periodic — every day at 09:00 UTC
Kronos.schedulePeriodic(
    jobName = SendReport.name,
    periodic = Periodic.everyDay(hour = 9, minute = 0),
    params = mapOf("reportId" to "abc")
)
```

Both `schedule` and `schedulePeriodic` return the job ID (`String?`), or `null` if insertion failed.

## 5. Manage

```kotlin
val jobId = Kronos.schedule(...)!!

Kronos.checkJob(jobId)              // JSON of current job state, null if not found
Kronos.allJobs()                    // all scheduled jobs
Kronos.allJobs(SendReport.name)     // jobs filtered by name

Kronos.dropJobId(jobId)             // cancel one specific job
Kronos.dropJob(SendReport.name)     // cancel all jobs with this name
Kronos.dropAll()                    // cancel everything
```
