---
comments: true
---

# Scheduling

Kronos provides two scheduling modes: interval-based and periodic (cron-like).

## `schedule` — interval or one-time

```kotlin
suspend fun Kronos.schedule(
    jobName: String,
    delay: Duration = Duration.ZERO,   // how long to wait before the first run
    interval: Duration? = null,        // repeat every N minutes (null = one-time)
    endTime: Long? = null,             // stop repeating after this epoch-ms timestamp
    maxCycles: Int? = null,            // stop after N executions
    retries: Int? = null,              // overrides Job.retries
    params: Map<String, String>,
    overshotAction: OvershotAction = OvershotAction.Drop,
): String?
```

Use `startTime: Long` instead of `delay` for an absolute UTC epoch-ms start time:

```kotlin
Kronos.schedule(jobName = "my-job", startTime = epochMs, params = emptyMap())
```

### Examples

```kotlin
// Run once, immediately
Kronos.schedule(jobName = "my-job", params = emptyMap())

// Run once in 1 hour
Kronos.schedule(jobName = "my-job", delay = 1.hours, params = emptyMap())

// Repeat every 15 minutes, stop after 4 runs
Kronos.schedule(
    jobName = "my-job",
    interval = 15.minutes,
    maxCycles = 4,
    params = emptyMap()
)

// Repeat every hour until a deadline
Kronos.schedule(
    jobName = "my-job",
    interval = 1.hours,
    endTime = deadlineEpochMs,
    params = emptyMap()
)
```

## `schedulePeriodic` — calendar-aligned

For jobs that must run at a specific time on a calendar pattern. See [Periodic](periodic.md) for all options.

```kotlin
// Every Monday at 08:00 UTC
Kronos.schedulePeriodic(
    jobName = "my-job",
    periodic = Periodic.everyWeek(dayOfWeek = 1, hour = 8, minute = 0),
    params = emptyMap()
)
```

## Notes

- Minimum resolution is **1 minute** — sub-minute scheduling is not supported.
- All times are **UTC**. Adjust `hour`/`minute` for your timezone.
- `cycleNumber` is injected into `params` automatically (key `"cycleNumber"`). Do not set it manually.
- If both `interval` and `maxCycles` are set, the job stops when either limit is reached first.
