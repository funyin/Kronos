---
comments: true
---

# Periodic Schedules

`Periodic` lets you express calendar-aligned schedules without cron syntax.

All times are **UTC**. If your target time is in another timezone, subtract the UTC offset before passing `hour`.

## Available Patterns

### Every minute
```kotlin
Periodic.everyMinute()
```

### Every hour at a fixed minute
```kotlin
Periodic.everyHour(minute = 30)   // runs at :30 past every hour
```

### Every day at a fixed time
```kotlin
Periodic.everyDay(hour = 9, minute = 0)   // 09:00 UTC daily
```
`hour` is 0–23 (24-hour format).

### Every week on a specific day
```kotlin
Periodic.everyWeek(dayOfWeek = 1, hour = 9, minute = 0)  // Monday 09:00 UTC
```
`dayOfWeek`: 1 = Monday … 7 = Sunday.

### Every month on a specific day
```kotlin
Periodic.everyMonth(dayOfMonth = 1, hour = 9, minute = 0)  // 1st of every month
```
`dayOfMonth`: 1–31. Jobs scheduled for days that don't exist in a given month (e.g. day 31 in February) will be skipped that month.

### Every year on a specific date
```kotlin
Periodic.everyYear(month = 1, dayOfMonth = 1, hour = 0, minute = 0)  // Jan 1 midnight UTC
```
`month`: 1 = January … 12 = December.

## Usage

```kotlin
Kronos.schedulePeriodic(
    jobName = "monthly-report",
    periodic = Periodic.everyMonth(dayOfMonth = 1, hour = 8, minute = 0),
    params = mapOf("type" to "full"),
)
```

Optional bounds:

```kotlin
Kronos.schedulePeriodic(
    jobName = "daily-sync",
    periodic = Periodic.everyDay(hour = 2, minute = 0),
    maxCycles = 30,                    // stop after 30 runs
    endTime = someDeadlineEpochMs,     // or stop at a specific time
    params = emptyMap()
)
```
