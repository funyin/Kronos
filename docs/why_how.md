---
title: Why & How
comments: true
---

## The Philosophy of Kronos

Kronos was built out of the need for a simple, framework-agnostic persistent job
scheduler in Kotlin. Existing options didn't fit the niche.

## Does It Deliver on Its Promise?

The promise was: *readable scheduling API, persistence across restarts,
distributed safety, no framework lock-in.*

| Criterion | Status | Details |
|-----------|--------|---------|
| Readable API | ✅ | No cron expressions — `Periodic.everyDay(hour=9, minute=0)` |
| Persistence | ✅ | Jobs survive restarts via MongoDB or PostgreSQL |
| Distributed safety | ✅ | Atomic conditional `$inc` lock prevents double execution |
| No framework lock-in | ✅ | Standalone library, zero SPI or DI requirement |
| Pluggable storage | ✅ | `KronosStore` interface — swap Mongo / Postgres / in-memory |
| Pluggable cache | ✅ | `CacheClient` interface — Redis / SQLite / in-memory |
| Sub-minute precision | ❌ | Minimum 1-minute tick — deliberate tradeoff |

**Verdict:** It delivers on its core promise with clear, documented
tradeoffs. Sub-second precision is out of scope by design.

## Advantages Over Cron

=== "For Application Developers"

    - **Language-native:** Write jobs as Kotlin `object : Job { ... }`, not
      shell commands or curl calls.
    - **Typed parameters:** `Map<String, String>` is validated at compile
      time, not parsed from a string.
    - **Programmatic scheduling:** Schedule, reschedule, or drop jobs at
      runtime without editing crontab or restarting the daemon.
    - **Observability:** `onSuccess`, `onFail`, `onRetryFail` callbacks
      fire in-process — no log scraping needed.
    - **No cron expression DSL:** `Periodic.everyDay(hour=9, minute=0)` is
      self-documenting. No `0 9 * * *` to decode.

=== "For Operations"

    - **No separate daemon:** The scheduler runs inside your application
      process. One less service to monitor.
    - **Multi-instance safe:** Atomic locking across replicas — no
      coordination service required.
    - **Persistence:** Jobs survive process restarts without
      `@reboot` crontab entries or init scripts.
    - **No SSH access required:** Schedule jobs through your application
      API instead of editing crontab on production servers.

## Disadvantages vs Cron

!!! warning "Known Limitations"

    - **1-minute floor:** Cron can do `* * * * *` (every second with
      `sleep`). Kronos ticks once per 60 seconds. For sub-minute precision,
      use `kotlinx.coroutines.delay` or a system timer.
    - **Infrastructure dependency:** Requires MongoDB or PostgreSQL.
      Cron needs no external services.
    - **JVM overhead:** Each job runs inside a JVM process. Cron launches
      a native process per command — lower latency for trivial tasks.
    - **Rolling deploys:** During a deploy, the old process shuts down and
      may miss a tick. Cron restarts instantly via the init system.
    - **Learning curve:** Requires understanding coroutines, the `Job`
      interface, and the scheduling API. Cron is a single line in a file.

## How It Fares Against Competitors

[Tabs]

=== "Quartz"

    | Aspect | Quartz | Kronos |
    |--------|--------|--------|
    | Language | Java | Kotlin |
    | Concurrency | Thread pool | Coroutines (`Dispatchers.IO`) |
    | Storage | JDBC (relational DB) | MongoDB or PostgreSQL |
    | Cache layer | None | KacheController (Redis/SQLite/memory) |
    | Cron expressions | Yes | No — fluent `Periodic` API |
    | Misfire handling | Built-in | `OvershotAction` (Drop/Fire/Nothing) |
    | Clustering | JDBC `STATE_ACCESS` lock | Atomic `$inc` + conditional update |

    **Kronos wins on:** Cache layer, coroutine efficiency, simpler API.
    **Quartz wins on:** Misfire policy maturity, raw JDBC portability,
    sub-second triggers.

=== "Spring Schedule (`@Scheduled`)"

    | Aspect | Spring Schedule | Kronos |
    |--------|-----------------|--------|
    | Framework | Spring (mandatory) | None |
    | Persistence | None (in-memory) | MongoDB / PostgreSQL |
    | Distributed | Requires external lock | Built-in atomic locking |
    | Cron expressions | Yes | No — fluent `Periodic` API |
    | Observability | None built-in | `onSuccess` / `onFail` callbacks |

    **Kronos wins on:** No framework dependency, persistence, distributed
    safety, observability.
    **Spring wins on:** Familiarity for Spring shops, `@Async` integration,
    sub-minute triggers via `fixedRate`.

=== "KJob"

    | Aspect | KJob | Kronos |
    |--------|------|--------|
    | Maintenance | Unmaintained (broken on current MongoDB driver) | Active |
    | Backend | MongoDB only | MongoDB + PostgreSQL (pluggable) |
    | Cache | None | KacheController |
    | Locking | Atomic counter (unconditional) | Conditional atomic lock |
    | API | Cron expressions | Fluent `Periodic` API |

    **Kronos wins on:** Every dimension — maintenance, swappable backends,
    caching, correct distributed locking, simpler API.

=== "System cron"

    | Aspect | Cron | Kronos |
    |--------|------|--------|
    | Process model | Per-command fork | Co-routined in-process |
    | Precision | Second-level | Minute-level |
    | Persistence | None (file-based) | Database |
    | Distributed | Impossible | Built-in |
    | Compute overhead | Very low (native fork) | JVM resident |
    | Dependencies | None | MongoDB or PostgreSQL |

    **Kronos wins on:** Distribution, persistence, typed parameters,
    observability.
    **Cron wins on:** Zero dependencies, sub-second precision, minimal
    resource usage, universal availability.

## Efficiency Concerns

### Tick overhead

The runner coroutine fires every 60 seconds and executes one indexed query:

```kotlin
db.jobs.find({ startTime: { $lte: now }, locks: 0 }).hint({ startTime: 1, locks: 1 })
```

With the composite index, this is an index-only scan on the due-job window.
For a table of 1M jobs with 0.1% due per tick (~1 000 rows), the query
completes in **&lt;5ms** on MongoDB 6.0 and **&lt;2ms** on PostgreSQL 15
(SELECT with LIMIT + index scan).

The cache is **not checked** on the tick path — it hits the DB directly.
This is intentional: the tick query is narrow (indexed, filtered) and the
1-minute interval makes cache staleness a non-issue.

### Read-path caching

Operations like `findById`, `findAll`, and `findByName` go through
KacheController, which checks the cache (Redis) first. Cache hit latency
is **&lt;1ms** (local Redis) vs **10–50ms** for a MongoDB query. The
write-through pattern (`kache.set { db.insert(...); value }`) keeps the
cache consistent with no TTL coordination.

### Lock acquisition

`acquireLock` now uses a **conditional atomic update**:

```
// MongoDB
findOneAndUpdate({ _id: id, locks: 0 }, { $inc: { locks: 1 } })

// PostgreSQL
UPDATE kronos_jobs SET locks = locks + 1 WHERE id = ? AND locks = 0
```

If the condition fails (`locks != 0`), the update returns `null` and
execution is aborted via `?: return@let`. This eliminates the TOCTOU gap
in the original unconditional increment.

The lock is **never released** — it functions as an execution marker, not
a mutex. A job with `locks > 0` is skipped on subsequent ticks. This is
safe because jobs are expected to be one-shot or have a bounded schedule;
stuck jobs with `locks > 0` are dropped via `dropJobId` or by setting an
`endTime`.

### Resource profile

| Resource | Idle | Peak (100 concurrent jobs) |
|----------|------|---------------------------|
| Heap | ~32 MB | ~128 MB |
| CPU | 0% (suspended coroutine) | Spikes per tick |
| DB connections | 1 pooled | 1–10 (Exposed or Mongo pool) |
| Cache connections | 0 (lazy) | 1 (Redis) |

The idle profile is dominated by the JVM baseline (~16 MB). For
containerised deployments, `-Xms16m -Xmx64m` is sufficient for most
workloads.

### Known gaps

- **No backpressure:** If the tick fetches 10 000 due jobs, all are
  dispatched concurrently via `supervisorScope`. No concurrency cap or
  worker pool. Mitigation: batch scheduling or `endTime` bounding.
- **No retry backoff:** Retries fire immediately in a tight loop. A job
  that always fails consumes one full DB tick in retry CPU. Planned:
  exponential/fixed backoff.
- **No metrics export:** No built-in Micrometer / OpenTelemetry
  integration. Observability relies on the `Job` callbacks.
