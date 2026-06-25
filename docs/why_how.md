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
| Distributed safety | ✅ | Atomic conditional lock prevents double execution |
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

## Efficiency

### Tick flow

Every 60 seconds the runner fires a single indexed query:

```
-- MongoDB
db.jobs.find({ startTime: { $lte: now }, locks: 0 })

-- SQL
SELECT * FROM kronos_jobs WHERE start_time <= ? AND locks = 0
```

Both backends create a composite index on `(start_time, locks)` at
initialization, so the query is a narrow index range scan over the due-job
window rather than a full table scan.

For **periodic** jobs, the query returns all jobs whose `startTime` is in the
past and whose `locks` counter is zero. A second validation pass in the runner
then checks whether the job's calendar pattern (hour, minute, day-of-week,
etc.) matches the current UTC minute before dispatching. This two-stage
approach keeps the DB query simple while supporting complex periodic patterns
in application code.

### Read-path caching

`findById`, `findAll`, and `findByName` go through KacheController, which
checks the cache (Redis or in-memory) first. The tick query (`fetchDueJobs`)
bypasses the cache and always hits the DB — the 1-minute interval makes cache
staleness a non-issue on the hot path, and the narrow indexed query is fast
enough to not warrant caching.

### Lock acquisition

Kronos uses a **conditional atomic increment** to prevent duplicate execution
across instances:

```
-- MongoDB (atomic, single round-trip)
findOneAndUpdate({ _id: id, locks: 0 }, { $inc: { locks: 1 } })
→ returns the document before update, or null if locks ≠ 0

-- SQL
UPDATE kronos_jobs SET locks = locks + 1 WHERE id = ? AND locks = 0
```

On the MongoDB backend, `acquireLock` returns `null` when another instance
already holds the lock — execution aborts cleanly. On the SQL backend, the
`UPDATE` is equally atomic, but the current implementation returns the job row
regardless of whether the update was applied, meaning the null-check guard in
the runner is ineffective under contention. This is a known gap (see below).

The lock is never released. It functions as a one-shot execution marker: a job
with `locks > 0` is permanently skipped by the runner, then dropped after
execution completes.

### Resource profile

Measured against the MongoDB backend with a Redis cache, 100 concurrent
trivial jobs (500 ms `delay`, no allocations), JVM max heap 2 GB:

| Checkpoint | Heap used |
|---|---|
| Idle — after init, no jobs | ~50 MB |
| After scheduling 100 jobs | ~66 MB |
| Peak — during concurrent execution | ~67 MB |
| After execution settled | ~67 MB |

The ~17 MB delta between idle and 100 concurrent jobs reflects coroutines'
low per-task overhead — a thread-based scheduler would consume roughly 1 MB
of stack space per concurrent task. Real-world peaks depend on how much each
job allocates during execution.

The idle footprint (~50 MB) is driven by the MongoDB and Redis driver
initialization, not Kronos itself. For containerised deployments,
`-Xmx128m` is a safe floor for most workloads. Connection pool size is
controlled by your MongoDB driver or `DataSource` configuration — Kronos
does not manage it directly.

| Resource | Idle | Active |
|----------|------|--------|
| Heap | ~50 MB | ~67 MB (100 concurrent jobs) |
| CPU | ~0% (suspended coroutine) | Spikes per tick |
| DB connections | Pool (driver-managed) | Pool (driver-managed) |
| Cache connections | 0 (lazy) | 1 (Redis) |

## Known Gaps

- **No backpressure:** If a tick returns 10 000 due jobs, all are dispatched
  concurrently via `supervisorScope`. There is no concurrency cap or worker
  pool. Mitigation: use `endTime` or `maxCycles` to bound job volume, or batch
  scheduling on the application side.

- **No retry backoff:** Retries fire in a tight loop with no delay. A job that
  always fails burns a full DB round-trip per retry within the same tick.
  Planned: configurable fixed or exponential backoff.

- **SQL locking gap:** `ExposedKronosStore.acquireLock` performs an atomic
  conditional `UPDATE` but then unconditionally SELECTs and returns the row,
  so it can return a non-null result even when the lock was not acquired. Under
  concurrent load on the SQL backend, two instances could execute the same job.
  The MongoDB backend does not have this issue. Fix planned.

- **`InMemoryKronosStore` missing:** No in-process store exists for unit tests.
  Tests currently require Testcontainers (real MongoDB + Redis). Planned.
