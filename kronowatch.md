# Kronos Cloud / KronoWatch — Detailed SaaS Implementation Plan

## Overview of What We're Building

The strategy is to build three things in parallel tracks that feed each other:

1. A new Gradle submodule `kronos-monitor` — a lightweight SDK that wraps the existing `Job` interface and emits telemetry over HTTP. Zero breaking changes to the existing library.
2. A standalone Kotlin/Ktor backend service (`kronowatch-api`) — receives pings, stores execution history, evaluates alert rules, dispatches notifications.
3. A React/Next.js frontend (`kronowatch-ui`) — the dashboard, auth flows, billing, and status pages.

The key architectural insight from reading the code: `RunJob.kt` is the single place where every job outcome is observed. All telemetry hooks live there. We never need to modify `Job.kt`, `Kronos.kt`, or `KronoJob.kt`. The telemetry SDK wraps the `Job` interface as a decorator, intercepting the lifecycle callbacks.

---

## Monorepo Structure

This should live as a new top-level repository, not inside the existing Kronos library repo. The Kronos library stays a standalone publishable artifact. KronoWatch depends on it as an external Maven artifact.

```
kronowatch/
  kronos-monitor/          # Kotlin SDK (Gradle submodule, published to Maven Central)
  kronowatch-api/          # Ktor backend service
  kronowatch-ui/           # Next.js frontend
  kronowatch-mcp/          # MCP server (Phase 3)
  infra/                   # Docker Compose for local dev, Terraform for prod
  docs/                    # OpenAPI spec, llms.txt, ai-plugin.json
```

---

## Phase 1: MVP (Weeks 1-6)

The goal of Phase 1 is: a developer adds one dependency, calls `Kronos.enableMonitoring(apiKey = "...")`, and within 5 minutes can see their jobs on a dashboard. Nothing more.

### Component 1: `kronos-monitor` SDK

This is a new Gradle submodule published as `com.funyinkash:kronos-monitor:1.0.0`.

**Technology**: Pure Kotlin JVM. Dependencies: `ktor-client-cio` for HTTP, `kotlinx-serialization-json`, `kotlinx-coroutines-core`. Target: Kotlin 1.9, JVM 11 minimum (not 19 like the library, to maximize compatibility).

**Core design — the `MonitoredJob` decorator:**

The existing `Job` interface has 8 lifecycle methods. `MonitoredJob` wraps any `Job` and intercepts every callback to build a `TelemetryEvent` and ship it asynchronously to the KronoWatch API. Crucially, it uses `supervisorScope` internally so a network failure never causes the user's job to fail.

Key data captured per execution:
- `jobName`, `jobId` (from `KronoJob.id` — we need to thread this through)
- `cycleNumber`, `success: Boolean`, `durationMs: Long`
- `exception: String?` (class name + message, no stack trace to keep payload small)
- `retryCount: Int`, `totalRetries: Int`
- `scheduledTime: Long` (from `KronoJob.startTime`), `actualStartTime: Long`
- `tags: Map<String, String>` (user-provided custom metadata)

**The critical problem**: `RunJob.kt` calls `job.execute()` directly, and `Job` has no access to `KronoJob.id` or `KronoJob.startTime` — those are held by `RunJob`. To capture duration and scheduled time, we need the start timestamp to be available inside the decorator. The cleanest solution is a `ThreadLocal`/coroutine-context approach: before `execute()` is called, `RunJob.kt` would need to set context. But we cannot modify `RunJob.kt` without forking the library.

**The practical MVP solution**: In Phase 1, measure duration inside `execute()` using `System.currentTimeMillis()` at the start and then report it in `onSuccess`/`onFail` using a coroutine-context key stored in a `ConcurrentHashMap<Int, Long>` keyed by `cycleNumber`. This is slightly less accurate (misses time spent in lock acquisition) but works without touching the library.

For Phase 2, add a proper `MonitoringKronosStore` wrapper that decorates `KronosStore` and passes execution context through — that gives us the `KronoJob.startTime` for missed-run detection.

**`KronosMonitoring.kt`** — the public API entry point:

```kotlin
object KronosMonitoring {
    fun enable(
        apiKey: String,
        endpoint: String = "https://api.kronowatch.io",
        environment: String = "production",
        flushIntervalMs: Long = 5_000,
    )
    
    // Wraps a Job with telemetry. Users call this in Kronos.register():
    // Kronos.register(KronosMonitoring.monitor(MyJob))
    fun <T : Job> monitor(job: T): Job
    
    // Zero-config alternative that patches Kronos.register() globally:
    fun Kronos.enableMonitoring(apiKey: String, endpoint: String = "...")
}
```

The `Kronos.enableMonitoring()` extension wraps `Kronos.register()` — it replaces the internal `jobs` map by overriding what gets registered. Since `Kronos.jobs` is `internal`, in Phase 1 the simpler `KronosMonitoring.monitor(job)` approach is cleaner. The one-liner setup in Phase 2 can be achieved by providing a `MonitoringKronosStore` that hooks at the `KronosStore` level.

**Telemetry buffering**: Events go into an in-memory `Channel<TelemetryEvent>(capacity = 1000)`. A background coroutine flushes in batches of up to 50 every 5 seconds via a single `POST /v1/telemetry/batch` call. If the channel is full, events are dropped with a warning log — never blocking the user's job.

**Files to create in `kronos-monitor`:**

- `kronos/monitor/KronosMonitoring.kt` — `object` with `enable()` and `monitor()`
- `kronos/monitor/MonitoredJob.kt` — decorator implementing `Job`
- `kronos/monitor/TelemetryEvent.kt` — `@Serializable data class`
- `kronos/monitor/TelemetryClient.kt` — Ktor HTTP client, batch flush
- `kronos/monitor/TelemetryBuffer.kt` — `Channel`-based buffer with background coroutine
- `kronos/monitor/HeartbeatEmitter.kt` — emits a `HEARTBEAT` ping every 60 seconds (proves the Kronos runner is alive)

**Heartbeat design**: `HeartbeatEmitter` starts a separate coroutine that fires `POST /v1/heartbeat` with `{ apiKey, schedulerInstanceId, timestamp }` every 60 seconds. The `schedulerInstanceId` is `hostname + UUID` generated at init time. This is how the "scheduler down" alert works server-side.

**`gradle.properties` / `build.gradle.kts` for `kronos-monitor`:**

```kotlin
dependencies {
    implementation("io.ktor:ktor-client-cio:2.3.7")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    compileOnly("com.funyinkash:kronos:0.0.8")  // compileOnly — user already has it
}
```

### Component 2: `kronowatch-api` Backend

**Technology**: Kotlin + Ktor 2.x + Exposed ORM + PostgreSQL. Reasoning:
- Ktor: consistent with the Kotlin ecosystem, lightweight, coroutine-native. Spring Boot is overkill for this and carries JVM startup weight that matters for cold starts on cheap hosting.
- Exposed: already in the Kronos codebase (`kronos-exposed` module), you already know it.
- PostgreSQL: time-series-friendly with TimescaleDB extension for Phase 2. In Phase 1, plain PostgreSQL works fine. Avoid MongoDB for the SaaS backend — you want relational integrity for billing, tenancy, and alerting.
- Redis: continue using it for session storage and rate limiting.

**Database schema (Phase 1):**

```sql
-- Multi-tenancy backbone
organizations(id, name, slug, plan, created_at)
api_keys(id, org_id, key_hash, name, last_used_at, created_at)
users(id, org_id, email, password_hash, role, created_at)

-- Monitor registry (one row per job name per org)
monitors(id, org_id, name, display_name, schedule_expression, 
         grace_period_minutes, expected_duration_ms, created_at)

-- Core time-series table (write-heavy, index on monitor_id + started_at)
executions(id, monitor_id, org_id, cycle_number, 
           scheduled_at, started_at, finished_at, duration_ms,
           status, -- 'success' | 'failure' | 'dropped' | 'challenged'
           retry_count, total_retries, exception_class, exception_message,
           instance_id, -- which scheduler instance ran it
           created_at)

-- Heartbeats
scheduler_instances(id, org_id, instance_id, last_seen_at, metadata)

-- Alerting (Phase 1 basic)
alert_channels(id, org_id, type, -- 'email' | 'slack' | 'webhook'
               config jsonb, -- {email: "..."} or {webhookUrl: "..."} 
               created_at)
alert_rules(id, monitor_id, org_id, type, -- 'failure' | 'missed' | 'duration'
            threshold, channel_id, created_at)
alert_events(id, rule_id, monitor_id, org_id, 
             triggered_at, resolved_at, message)
```

**Ktor routing structure:**

```
POST /v1/telemetry/batch       # SDK sends execution events here
POST /v1/heartbeat             # SDK sends heartbeat here
GET  /v1/monitors              # List monitors
GET  /v1/monitors/:id          # Monitor detail
GET  /v1/monitors/:id/executions  # Execution history with pagination
GET  /v1/monitors/:id/stats    # Duration histogram, success rate
POST /v1/auth/register         # Org signup
POST /v1/auth/login
POST /v1/alert-channels        # CRUD for alert channels  
POST /v1/alert-rules
```

**Authentication**: API keys in header `X-Api-Key`. For dashboard calls: JWT in `Authorization: Bearer`. Keep these two auth paths completely separate in Ktor plugins.

**Telemetry ingestion pipeline**: The `POST /v1/telemetry/batch` endpoint receives up to 50 `TelemetryEvent` objects. For each:
1. Resolve `apiKey` -> `orgId` (cached in Redis with 5-minute TTL).
2. Upsert into `monitors` by `jobName` (auto-register on first ping).
3. Bulk insert into `executions`.
4. Publish job names to a Redis pub/sub channel `alerts:evaluate` so the alert engine processes asynchronously.

Phase 1 has the alert engine run in the same JVM as a coroutine loop consuming from the Redis channel. Phase 2 extracts it to a separate worker process.

**Files to create in `kronowatch-api`:**

```
src/main/kotlin/kronowatch/
  Application.kt               # Ktor server setup, plugins, routing
  plugins/
    Authentication.kt          # API key + JWT plugin config
    Serialization.kt
    CORS.kt
  routes/
    TelemetryRoutes.kt         # POST /v1/telemetry/batch, POST /v1/heartbeat  
    MonitorRoutes.kt           # GET monitors, executions, stats
    AuthRoutes.kt              # register, login
    AlertRoutes.kt
  db/
    Database.kt                # Exposed Database setup, Hikari pool
    tables/
      OrganizationsTable.kt
      ApiKeysTable.kt
      MonitorsTable.kt
      ExecutionsTable.kt
      SchedulerInstancesTable.kt
      AlertRulesTable.kt
      AlertEventsTable.kt
  services/
    TelemetryService.kt        # Ingest, upsert monitors, bulk insert executions
    MonitorService.kt          # Query + aggregation logic
    AlertEngine.kt             # Evaluate rules, dispatch notifications
    HeartbeatService.kt        # Track scheduler instances, detect down
    AuthService.kt             # Key hashing, JWT generation
  notifications/
    EmailNotifier.kt           # Resend.com API (simplest for Phase 1)
    SlackNotifier.kt           # Phase 1: basic incoming webhook
  models/
    TelemetryEvent.kt
    Monitor.kt
    Execution.kt
```

**Alert Engine (Phase 1):** A coroutine that wakes up every 30 seconds and runs three queries:
- Failed runs: `SELECT monitor_id, COUNT(*) WHERE status = 'failure' AND started_at > now() - 5min GROUP BY monitor_id HAVING COUNT(*) >= threshold`
- Missed runs: For each monitor with an expected schedule, check if `MAX(finished_at)` is older than `(grace_period_minutes)` past the expected fire time. This requires storing `schedule_expression` in the `monitors` table as a cron string — the SDK sends the `Periodic.every` type and the time components, and the backend converts to a human-readable cron.
- Duration anomalies: Compare the rolling P95 duration against `expected_duration_ms * 1.5`.

For missed-run detection, the SDK should send the schedule metadata from `KronoJob.periodic` / `KronoJob.interval` when registering the monitor. This can be added as an optional field in `TelemetryEvent`.

### Component 3: `kronowatch-ui` Frontend (Phase 1 — Minimal)

**Technology**: Next.js 14 (App Router) + TypeScript + Tailwind CSS + shadcn/ui. This is the pragmatic choice — you get SSR for the public status pages for free, great SEO, and the shadcn components (built on Radix) give you accessible, unstyled-but-beautiful UI without a component library lock-in.

For Phase 1 the frontend is minimal:
- Sign up / log in (Next Auth with credentials provider backed by your API)
- Dashboard: list of monitors with last execution status (green/yellow/red)
- Monitor detail: last 50 executions table with duration column
- Settings: API key display, email alert channel setup

Use SWR or React Query for data fetching. Deploy to Vercel — zero config, great DX.

**Phase 1 does not need:**
- Billing (use Polar.sh or Stripe later)
- Status pages (Phase 2)
- Charts (a simple table is enough to validate)

### Infrastructure (Phase 1)

Run everything on a single $12/month Hetzner CPX21 (3 vCPU, 4 GB) with Docker Compose:
- PostgreSQL 16
- Redis 7
- kronowatch-api (fat JAR via shadowJar)
- Caddy for HTTPS termination and reverse proxy

This is intentionally minimal — validate the product before spending on managed infra.

**`infra/docker-compose.prod.yml`:**
```yaml
services:
  postgres:
    image: postgres:16-alpine
    volumes:
      - postgres-data:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: kronowatch
      POSTGRES_USER: kronowatch
      POSTGRES_PASSWORD: ${PG_PASSWORD}
  
  redis:
    image: redis:7-alpine
    
  api:
    image: kronowatch/api:${VERSION}
    environment:
      DATABASE_URL: jdbc:postgresql://postgres:5432/kronowatch
      REDIS_URL: redis://redis:6379
      JWT_SECRET: ${JWT_SECRET}
    depends_on: [postgres, redis]
    
  caddy:
    image: caddy:2-alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile
      - caddy-data:/data
```

### End-to-End Test (Phase 1)

The test path is:
1. Register on the dashboard, get an API key.
2. Add `com.funyinkash:kronos-monitor:1.0.0` to the example app.
3. Call `KronosMonitoring.enable(apiKey = "kw_xxx")` before `Kronos.init()`.
4. Wrap the job: `Kronos.register(KronosMonitoring.monitor(SayHello))`.
5. Run the example app. Within 60 seconds, see `say-hello` appear as a green monitor on the dashboard with a successful execution row.
6. Modify `SayHello.execute` to return `false`. See the monitor go red and receive an email.

---

## Phase 2: Revenue (Weeks 7-18)

Phase 2 is about making it worth paying for. Add the depth that Cronitor/Healthchecks don't have.

### 2.1 Deep Observability (The JVM Differentiator)

Add `MonitoringKronosStore` to `kronos-monitor`. This is a `KronosStore` decorator that:
- Wraps any existing `KronosStore`
- Records `KronoJob.startTime` at `fetchDueJobs()` time
- Passes it through as context so the `MonitoredJob` decorator has access to the scheduled-vs-actual start time delta ("scheduler lag")

This enables the metric nobody else provides: "this job was scheduled at 09:00 but didn't start until 09:02 because the Kronos runner was busy processing 200 other jobs." That's a JVM-specific insight that becomes the marketing differentiator.

Additional Phase 2 telemetry fields:
- `schedulerLagMs: Long` — actual start minus scheduled start
- `queueDepthAtExecution: Int` — how many jobs were due at that tick (from `fetchDueJobs` result size)
- `jvmHeapUsedMb: Int` — from `Runtime.getRuntime()` at execution start
- `threadPoolSaturation: Float` — if user exposes it

In the API, expose these as:
- `GET /v1/monitors/:id/stats?period=7d` returns: `{ p50DurationMs, p95DurationMs, p99DurationMs, avgSchedulerLagMs, successRate, failureRate, totalRuns }`
- Duration histogram endpoint returning 10 buckets for charting

### 2.2 Dashboard Enhancements

Add charts: use Recharts (React component library, no external SaaS needed). Specific views:
- Duration over time (line chart, last 24h/7d/30d)
- Success/failure rate bar chart
- Scheduler heartbeat timeline (shows gaps = scheduler was down)
- Retry heatmap: which jobs retry most, which retry patterns are suspicious

### 2.3 Multi-Tenant Billing

Use Polar.sh (better Stripe for developers, built-in invoice handling, great API). Add:
- Checkout flow: `POST /v1/billing/checkout` creates a Polar checkout session
- Webhook endpoint: `POST /webhooks/polar` updates `organizations.plan`
- Middleware in Ktor that enforces plan limits:
    - Free: 10 monitors, 7-day execution retention
    - Pro: unlimited monitors, 90-day retention
    - Team: same + multi-user + status pages

Retention enforcement: a Postgres scheduled job (using `pg_cron` extension) that runs `DELETE FROM executions WHERE created_at < now() - interval '7 days' AND org_id IN (SELECT id FROM organizations WHERE plan = 'free')` nightly.

### 2.4 Alert Integrations

**Slack**: Proper OAuth app, not just incoming webhooks. Users install your app to their workspace, you store the OAuth token in `alert_channels.config`. Sends rich Block Kit messages with job name, failure message, a "View on KronoWatch" button.

**PagerDuty**: Use PagerDuty's Events API v2. When an alert triggers, `POST https://events.pagerduty.com/v2/enqueue` with `routing_key` from the user's integration key. Auto-resolve the PagerDuty incident when the monitor goes green. Store `dedup_key = "kronowatch-{monitor_id}-{alert_rule_id}"` to correlate trigger/resolve.

**Webhooks**: `POST {user_url}` with a signed payload (`X-KronoWatch-Signature: hmac-sha256`). Document the webhook schema in OpenAPI.

**Email**: Upgrade from Resend.com to proper templated emails. Use React Email for HTML templates — it integrates with Next.js and you can preview templates locally.

### 2.5 Status Pages

Public URLs: `https://status.kronowatch.io/{org-slug}` or custom domain via CNAME.

Next.js dynamic routes: `app/status/[slug]/page.tsx`. Data fetched server-side from the API using the org slug (no auth required, but filtered to only show public monitors the org has opted into).

Shows: current health (operational / degraded / down), uptime % for last 30/90 days, incident history.

In the `monitors` table, add: `public_status_page: Boolean, status_page_display_name: String`.

### 2.6 Multi-User / Teams

Add `organization_members(org_id, user_id, role: owner|admin|viewer, invited_at, accepted_at)`.

Email invitation flow: send invite link with HMAC-signed token, user clicks, creates account, joins org.

### 2.7 TimescaleDB Migration

At meaningful scale (>1M execution rows), the `executions` table needs `SELECT percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms)` to stay fast. TimescaleDB is a PostgreSQL extension that turns `executions` into a hypertable, automatically partitioned by `created_at` in 1-day chunks. The migration is:

```sql
SELECT create_hypertable('executions', 'created_at', chunk_time_interval => INTERVAL '1 day');
CREATE MATERIALIZED VIEW daily_monitor_stats
  WITH (timescaledb.continuous) AS
  SELECT monitor_id, time_bucket('1 day', created_at) AS bucket,
         COUNT(*) AS total_runs,
         AVG(duration_ms) AS avg_duration_ms,
         percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_duration_ms,
         SUM(CASE WHEN status = 'success' THEN 1 ELSE 0 END)::float / COUNT(*) AS success_rate
  FROM executions GROUP BY monitor_id, bucket;
```

This eliminates the need for a separate time-series database (InfluxDB, etc.) and keeps the stack simple.

---

## Phase 3: Growth and AI Visibility

### 3.1 AI Discoverability (Critical — Do This Early)

These files cost almost nothing to create but compound over months:

**`/public/llms.txt`** at the root of the Next.js app (served as a static file):
```
# KronoWatch

KronoWatch is a job monitoring and observability platform for Kotlin/JVM applications
using the Kronos scheduling library.

## What KronoWatch Does
- Monitors background jobs scheduled with the Kronos library
- Tracks execution duration, success/failure rates, retry patterns
- Sends alerts via Slack, PagerDuty, email, and webhooks when jobs fail or are missed
- Provides execution history, duration histograms, and scheduler health status

## How to Integrate
Add the SDK: implementation("com.funyinkash:kronos-monitor:1.0.0")
Call: KronosMonitoring.enable(apiKey = "your-key")
Wrap jobs: Kronos.register(KronosMonitoring.monitor(MyJob))

## Pricing
Free tier: 10 monitors, 7-day history
Pro: $29/month, unlimited monitors, 90-day history
Team: $79/month, multi-user, status pages

## API
OpenAPI spec: https://api.kronowatch.io/openapi.json
```

**`/.well-known/ai-plugin.json`**:
```json
{
  "schema_version": "v1",
  "name_for_human": "KronoWatch",
  "name_for_model": "kronowatch",
  "description_for_human": "Monitor and alert on Kotlin/JVM background jobs",
  "description_for_model": "Query job execution history, check monitor health, and manage alerts for background jobs running in Kotlin applications using the Kronos scheduler.",
  "auth": { "type": "user_http", "authorization_type": "bearer" },
  "api": { "type": "openapi", "url": "https://api.kronowatch.io/openapi.json" },
  "logo_url": "https://kronowatch.io/logo.png",
  "contact_email": "funyin.kash@gmail.com",
  "legal_info_url": "https://kronowatch.io/terms"
}
```

**OpenAPI spec**: Generate automatically from Ktor using `ktor-openapi` plugin or write by hand and serve at `GET /openapi.json`. This is what the `ai-plugin.json` points to and what allows ChatGPT plugins, Copilot extensions, and custom MCP clients to call your API.

**Documentation content strategy**: Write docs that pattern-match the questions developers type into Google and ChatGPT:
- "how to monitor scheduled jobs in kotlin"
- "kotlin background job failed notification"
- "cronitor alternative for jvm"
- "how to know if my kotlin job didn't run"

Use the MkDocs setup already in the repo (you already have `mkdocs.yml`) — maintain a parallel docs site for KronoWatch.

### 3.2 MCP Server (`kronowatch-mcp`)

A Model Context Protocol server lets AI agents (Claude, Cursor, GitHub Copilot) query job status directly from a chat interface.

**Technology**: Kotlin + `io.modelcontextprotocol:kotlin-sdk` (the official MCP Kotlin SDK released by Anthropic in 2025).

**Tools to expose:**
- `get_monitor_status(monitorName: String)` → current health, last execution time, success rate
- `list_failing_monitors()` → monitors in red state
- `get_execution_history(monitorName: String, limit: Int = 20)` → last N executions with duration + status
- `get_scheduler_health(instanceId: String?)` → last heartbeat time, is it alive
- `acknowledge_alert(alertId: String)` → mute an alert from the IDE

**Transport**: Run as a standalone process that exposes stdio transport (for local dev via Claude Desktop) and HTTP/SSE transport for remote access. Package as a Docker image and a homebrew formula.

**Developer value**: Developers can ask Claude "why did my payment-processor job fail last night?" and get a factual answer grounded in real execution data, not hallucinated. This is a genuine unlock.

### 3.3 Enterprise Features

- **SAML SSO**: Use WorkOS (handles Okta, Azure AD, Google Workspace). Drop-in, $0 for <25 connections, $49/month at scale.
- **Audit Logs**: Every API action (monitor created, alert silenced, user invited) written to an `audit_events` table. Export as CSV or forward to Datadog.
- **Custom retention**: Configurable per-org execution history retention up to 1 year.
- **Private status pages**: Password-protected or IP-restricted status pages.
- **Dedicated instances**: For enterprise customers who want data isolation, provide a Docker Compose + Helm chart so they can self-host the backend on their own infrastructure while still using your UI or their own. Charge a flat annual fee.

### 3.4 Advanced Alerting

- **Anomaly detection**: After 100+ executions, use a rolling mean + 2 standard deviations for duration baselines instead of static `expected_duration_ms`. Compute this in a nightly background job using SQL window functions.
- **Alert correlation**: If 50% of your monitors fail at the same time, suppress individual alerts and send one "mass failure" alert. Indicates infrastructure problem, not job bugs.
- **Alert fatigue prevention**: Exponential backoff on repeated alerts — alert immediately, then 5 min, 15 min, 1h. Store `next_alert_at` on `alert_events`.

---

## Implementation Sequencing and Dependencies

**Week 1-2**: Set up the monorepo. Implement `TelemetryEvent`, `TelemetryClient`, `TelemetryBuffer`, `MonitoredJob`, `HeartbeatEmitter` in `kronos-monitor`. Write unit tests using a mock HTTP server. Publish a SNAPSHOT to Maven Local.

**Week 2-3**: Set up `kronowatch-api` skeleton. Ktor application with Postgres schema migrations (use Flyway, not manual `SchemaUtils.create`). Implement `POST /v1/telemetry/batch` and `POST /v1/heartbeat`. Implement API key auth. Verify the SDK can send events and they appear in the database.

**Week 3-4**: Implement monitor service, execution queries. Set up basic email alerting (Resend.com). Implement the alert engine (failure threshold only — missed runs are harder).

**Week 4-5**: Build the Next.js frontend — auth, monitor list, monitor detail with execution history table. Deploy to Vercel. Connect to the Hetzner backend.

**Week 5-6**: Publish `kronos-monitor:1.0.0` to Maven Central. Write the integration guide. Test the full end-to-end flow with the existing Kronos example app. Fix all the things that break.

**Week 7-8**: Add missed-run detection. This requires `MonitoringKronosStore` in the SDK plus `schedule_expression` parsing in the backend. This is the hardest part of Phase 1/2 boundary.

**Week 9-12**: Charts, billing (Polar.sh), Slack integration, status pages.

**Week 13-18**: PagerDuty, multi-user teams, TimescaleDB migration on the production database.

**Phase 3 start (week 16 onwards, can overlap)**: MCP server, `llms.txt`, OpenAPI spec, docs content.

---

## Avoided Pitfalls

**Do not put telemetry in the Kronos library itself.** Keep `kronos-monitor` a separate artifact. Library users who don't want monitoring should not pay for the dependency. This is a clean separation that also lets you version independently.

**Do not use MongoDB for the SaaS backend.** The existing Kronos library uses MongoDB for job persistence, but for a SaaS product you need relational integrity, foreign keys, `GROUP BY` aggregations, `percentile_cont`, and proper migrations. PostgreSQL is the right choice here.

**Do not block job execution for network calls.** The `TelemetryBuffer` must be fire-and-forget with a bounded queue. If the KronoWatch API is down, the user's jobs must still run. Validate this with a chaos test: block port 443 on the test machine, confirm jobs still execute and events buffer then flush when connectivity returns.

**Do not store full stack traces.** Store only `exceptionClass` and `exceptionMessage`. Stack traces can contain sensitive data (file paths, variable values) and bloat the database. Users who need full stack traces should use their own logging.

**Do not expose raw `KronoJob.id` as the monitor identifier.** UUIDs change every cycle for periodic jobs (because Kronos deletes and recreates the `KronoJob` on each cycle — this is clear from `RunJob.kt`). Use `jobName` as the stable identifier and `cycleNumber` as the sequence within a monitor. The SDK should group by `jobName`, not by `KronoJob.id`.

---

### Critical Files for Implementation

- `/Users/funyin/IdeaProjects/Kronos/src/jvmMain/kotlin/kronos/RunJob.kt` — the single execution point where all duration, retry, and outcome data lives; the `MonitoredJob` decorator intercepts the same callbacks this file invokes
- `/Users/funyin/IdeaProjects/Kronos/src/jvmMain/kotlin/kronos/Job.kt` — the interface the `MonitoredJob` decorator must implement exactly, including all 8 lifecycle methods
- `/Users/funyin/IdeaProjects/Kronos/src/jvmMain/kotlin/kronos/KronosStore.kt` — the interface `MonitoringKronosStore` will decorate to capture `KronoJob.startTime` for scheduler lag measurement in Phase 2
- `/Users/funyin/IdeaProjects/Kronos/src/jvmMain/kotlin/kronos/KronoJob.kt` — defines the full job data model; the `periodic` and `interval` fields are what the SDK serializes as `schedule_expression` for missed-run detection
- `/Users/funyin/IdeaProjects/Kronos/kronos-mongo/src/main/kotlin/kronos/mongo/KronosExt.kt` — the pattern for writing Kotlin extension functions on `Kronos` that accept configuration and return `Kronos`; `KronosMonitoring.kt` should follow this exact pattern