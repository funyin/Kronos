---
comments: true
---

# Runner

The runner is a background coroutine that ticks every minute, fetches jobs that are due, and dispatches them for execution.

## OvershotAction

When Kronos finds a job whose scheduled time has already passed (e.g. after a server restart), `OvershotAction` controls what happens:

| Value | Behaviour |
|---|---|
| `Drop` | Job is deleted immediately. **(default)** |
| `Fire` | Job runs immediately, ignoring the missed window. |
| `Nothing` | Job is left in the database untouched. Avoid this — stale jobs accumulate. |

```kotlin
Kronos.schedule(
    jobName = "my-job",
    params = emptyMap(),
    overshotAction = OvershotAction.Fire
)
```

## Distributed Locking

Each job record holds a `locks` counter. When an instance begins executing a job, it increments the counter atomically. The runner only dispatches jobs where `locks == 0`, so a job already being processed by one instance is skipped by others.

This is a best-effort guard. For strict once-only guarantees in high-concurrency environments, make your `execute` implementation idempotent.

## Timing

- The runner ticks every **60 seconds**.
- A job scheduled for "now" may wait up to 60 seconds before its first tick, unless it was scheduled within the current minute window (in which case Kronos fires it eagerly on insertion).
- Minimum scheduling resolution is **1 minute**.
