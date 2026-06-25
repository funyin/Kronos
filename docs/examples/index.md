---
comments: true
---

# Examples

Runnable code examples for the most common Kronos use cases.

---

## Defining a Job

A `Job` is a Kotlin `object` (or class) that implements the `Job` interface. Override only the callbacks you need.

```kotlin
object SendReport : Job {
    override val name = "send-report"
    override val retries = 2

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        val reportId = params["reportId"] as String
        sendReport(reportId)
        return true          // return false to trigger a retry
    }

    override fun onSuccess(cycleNumber: Int, params: Map<String, Any>) {
        println("Report sent on cycle $cycleNumber")
    }

    override fun onRetryFail(retryCount: Int, cycleNumber: Int, params: Map<String, Any>, exception: Exception?) {
        println("Attempt $retryCount failed: ${exception?.message}")
    }

    override fun onFail(cycleNumber: Int, params: Map<String, Any>, exception: Exception?) {
        alertOncall("send-report failed after all retries: ${exception?.message}")
    }

    override fun onDrop(jobId: String, lastJob: Boolean) {
        if (lastJob) println("No more send-report jobs queued")
    }
}
```

---

## Scheduling a Job

```kotlin
Kronos.register(SendReport)

// One-time, runs on the next tick
Kronos.schedule(
    jobName = SendReport.name,
    params = mapOf("reportId" to "rpt-001")
)

// One-time, delayed by 1 hour
Kronos.schedule(
    jobName = SendReport.name,
    delay = 1.hours,
    params = mapOf("reportId" to "rpt-002")
)

// One-time, at an absolute UTC timestamp
Kronos.schedule(
    jobName = SendReport.name,
    startTime = ZonedDateTime.of(2024, 12, 1, 9, 0, 0, 0, ZoneOffset.UTC).toEpochSecond() * 1000,
    params = mapOf("reportId" to "rpt-003")
)
```

---

## Dropping Jobs

```kotlin
val jobId = Kronos.schedule(jobName = SendReport.name, params = emptyMap())!!

// Cancel one specific job
Kronos.dropJobId(jobId)

// Cancel all jobs with a given name
Kronos.dropJob(SendReport.name)

// Cancel every scheduled job
Kronos.dropAll()
```

---

## Retrying Failed Jobs

Set `retries` on the `Job` object. Each failure calls `onRetryFail`; `onFail` fires only after all retries are exhausted.

```kotlin
object FlakyScraper : Job {
    override val name = "scraper"
    override val retries = 3   // 1 initial attempt + 3 retries = 4 total attempts

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        return try {
            scrapeUrl(params["url"] as String)
            true
        } catch (e: IOException) {
            false   // triggers a retry
        }
    }

    override fun onRetryFail(retryCount: Int, cycleNumber: Int, params: Map<String, Any>, exception: Exception?) {
        println("Retry $retryCount/3 failed")
    }

    override fun onFail(cycleNumber: Int, params: Map<String, Any>, exception: Exception?) {
        println("All retries exhausted for ${params["url"]}")
    }
}
```

You can also override `retries` per-schedule call:

```kotlin
Kronos.schedule(
    jobName = FlakyScraper.name,
    retries = 5,               // overrides Job.retries for this instance only
    params = mapOf("url" to "https://example.com")
)
```

---

## Scheduling Recursively Inside execute()

A job can schedule new jobs from within its own `execute` — useful for fan-out or conditional follow-up work.

```kotlin
object ProcessBatch : Job {
    override val name = "process-batch"

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        val items = fetchNextBatch()

        // Schedule a follow-up for each item
        items.forEach { item ->
            Kronos.schedule(
                jobName = SendNotification.name,
                delay = 5.minutes,
                params = mapOf("itemId" to item.id)
            )
        }

        // Reschedule self if more batches remain
        if (hasMoreBatches()) {
            Kronos.schedule(
                jobName = name,
                delay = 1.minutes,
                params = params
            )
        }

        return true
    }
}
```

---

## Tracking the Next Periodic Job ID

`periodicJobLoaded` is called as soon as the next occurrence is inserted into the database — before the current cycle finishes executing. Use it to track the live job ID.

```kotlin
object DailySync : Job {
    override val name = "daily-sync"

    @Volatile var currentJobId: String? = null

    override fun periodicJobLoaded(originJobId: String, nextJobId: String) {
        currentJobId = nextJobId
        println("Next daily-sync scheduled as $nextJobId")
    }
}

// To cancel the next pending run at any time:
DailySync.currentJobId?.let { Kronos.dropJobId(it) }
```

---

## Challenge Job Execution

`challengeRun` is a runtime gate called before each execution. Return `true` to **skip** the cycle, `false` to allow it. Use this for scheduling patterns that `Periodic` alone cannot express.

### Skip on public holidays

```kotlin
object Payroll : Job {
    override val name = "payroll"

    override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
        return isPublicHoliday(LocalDate.now(ZoneOffset.UTC))
    }
}
```

### Run only on Tuesdays (schedule daily, gate on day)

```kotlin
object TuesdayReport : Job {
    override val name = "tuesday-report"

    override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
        return LocalDate.now(ZoneOffset.UTC).dayOfWeek != DayOfWeek.TUESDAY
    }
}

// Schedule daily — challengeRun filters to Tuesdays only
Kronos.schedulePeriodic(
    jobName = TuesdayReport.name,
    periodic = Periodic.everyDay(hour = 9, minute = 0),
    params = emptyMap()
)
```

### Bi-weekly (run every other cycle)

```kotlin
object BiWeeklyDigest : Job {
    override val name = "bi-weekly-digest"

    override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
        return cycleNumber % 2 != 0    // skip odd cycles, run on even
    }
}

Kronos.schedulePeriodic(
    jobName = BiWeeklyDigest.name,
    periodic = Periodic.everyWeek(dayOfWeek = 1, hour = 8, minute = 0),
    params = emptyMap()
)
```

### Second Tuesday of the month

```kotlin
object MonthlyTeamUpdate : Job {
    override val name = "monthly-team-update"

    override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
        val today = LocalDate.now(ZoneOffset.UTC)
        val isTuesday = today.dayOfWeek == DayOfWeek.TUESDAY
        val isSecondWeek = today.dayOfMonth in 8..14
        return !(isTuesday && isSecondWeek)
    }
}

// Schedule weekly on Tuesday — challengeRun narrows it to the 2nd occurrence
Kronos.schedulePeriodic(
    jobName = MonthlyTeamUpdate.name,
    periodic = Periodic.everyWeek(dayOfWeek = 2, hour = 10, minute = 0),
    params = emptyMap()
)
```

---

## Periodic Schedule Examples

### Every minute

```kotlin
Kronos.schedulePeriodic(
    jobName = "health-check",
    periodic = Periodic.everyMinute(),
    params = emptyMap()
)
```

### Every hour at a fixed minute

```kotlin
Kronos.schedulePeriodic(
    jobName = "cache-refresh",
    periodic = Periodic.everyHour(minute = 30),   // runs at :30 past every hour
    params = emptyMap()
)
```

### Every 32 minutes (interval-based, not calendar-aligned)

Use `schedule` with an `interval` for non-standard intervals that don't map to a clock pattern:

```kotlin
Kronos.schedule(
    jobName = "metrics-flush",
    interval = 32.minutes,
    params = emptyMap()
)
```

### Every day at a specific time

```kotlin
Kronos.schedulePeriodic(
    jobName = "daily-report",
    periodic = Periodic.everyDay(hour = 9, minute = 0),   // 09:00 UTC
    params = emptyMap()
)
```

### Every 2 days

```kotlin
Kronos.schedule(
    jobName = "alternate-day-sync",
    interval = 48.hours,
    params = emptyMap()
)
```

### Every week on a specific day

```kotlin
Kronos.schedulePeriodic(
    jobName = "weekly-digest",
    periodic = Periodic.everyWeek(dayOfWeek = 5, hour = 17, minute = 0),  // Friday 17:00 UTC
    params = emptyMap()
)
```

### Every month on a specific day and time

```kotlin
Kronos.schedulePeriodic(
    jobName = "monthly-invoice",
    periodic = Periodic.everyMonth(dayOfMonth = 1, hour = 8, minute = 0),  // 1st of each month
    params = mapOf("type" to "invoice")
)
```

### Every year on a specific date

```kotlin
Kronos.schedulePeriodic(
    jobName = "annual-audit",
    periodic = Periodic.everyYear(month = 1, dayOfMonth = 1, hour = 0, minute = 0),  // Jan 1 midnight UTC
    params = emptyMap()
)
```
