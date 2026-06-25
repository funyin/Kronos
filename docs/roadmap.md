---
comments: true
---

# Roadmap

Unordered list of planned and potential features. Open an issue if something
here is important to you.

## Completed

- **Pluggable backends** — `KronosStore` interface decouples the scheduler
  from MongoDB/Redis. Swap backends without changing application code.
- **`kronos-mongo` adapter** — MongoDB + any `CacheClient` (Redis, SQLite,
  in-memory).
- **`kronos-exposed` adapter** — PostgreSQL / SQLite / H2 via Exposed ORM + any
  `CacheClient`.
- **Conditional distributed lock** — `acquireLock` now atomically
  increments only when `locks == 0`, eliminating the TOCTOU gap.
- **Optimized queries** — Indexed, filtered fetch replaces full collection
  scans.
- **Retry ordering fix** — `onFail` is correctly called after all retries
  are exhausted, not before.
- **acquireLock null-safety** — Lock failure aborts execution cleanly
  (`?: return@let`).

## Planned

- **Backoff retry** — Configurable delay between retry attempts
  (exponential or fixed).
- **`InMemoryKronosStore`** — No-database store for unit tests; eliminates
  Testcontainers dependency for scheduler logic tests.
- **Dashboard** — Self-hosted UI (Kobweb) to view, log, and manage jobs.
  Auth via time-rotating DB tokens.
- **Micrometer / OpenTelemetry metrics** — Export tick duration, queue
  depth, and execution counters.
