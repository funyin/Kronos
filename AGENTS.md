# AGENTS.md

## Security (DO NOT COMMIT)
- `gradle.properties` contains plaintext signing keys + OSSHR credentials — already in `.gitignore`, but never push if you change it
- `secret-key.ascii` is the GPG signing key — also gitignored

## Build & Test
```bash
./gradlew build                        # full check: test + kover (≥85% line coverage)
./gradlew test                         # all tests
./gradlew test --tests "*KronosTest*"  # single class
./gradlew test --tests "kronos.SchedulerTest.someTest"  # single method
./gradlew dokkaHtml                    # API docs
./gradlew publish                      # Maven Central (requires GPG)
```
- Tests require **Docker** (Testcontainers spins up real MongoDB + Redis — no fakes)
- Java toolchain 19, CI runs JDK 17

## Architecture
- **Multi-module**: root (`:`) is the core framework, `:kronos-mongo` and `:kronos-exposed` are backend adapters
- Root is multiplatform declared but **only JVM is active** (JS/Native commented out)
- Package `kronos` — 6 source files in `src/jvmMain/kotlin/kronos/`
- Subprojects use `api(project(":"))` so the core types are transitively exposed to consumers at compile scope
- **Entry**: `Kronos.init(mongoUri, redisUri)` (from `kronos-mongo`) with `Dispatchers.IO` by default
- Runner loop **only starts** for `Dispatchers.{IO, Main, Unconfined, Default}` — test dispatchers skip it (call `handleJobs()` manually)
- Lock field (atomic MongoDB `$inc`) prevents duplicate execution across instances
- `"cycleNumber"` is a **reserved param key** — never include in user params map
- Periodic months use 30-day and years use 365-day increments (approximate)

## Testing quirks
- `TestDataProvider.kt` manages container lifecycle (`startContainers` / `initKronos`)
- `KronosTest` / `JobRunnerTest` use `TestDataProvider.initKronos(StandardTestDispatcher(...))`
- `SchedulerTest` **bypasses** `TestDataProvider.initKronos` — creates spy KronoJobs directly and calls `Kronos.handleJobs()` at virtual minutes
- `JobRunnerTest` is mostly a skeleton (single empty test)
- After each test: `Kronos.dropAll()` + `Kronos.shutDown()` + `clearAllMocks()`

## Constraints
- All times **UTC** — adjust hour for timezone offset when using `Periodic`
- Minimum resolution: **1 minute**
- Job names must be unique; `Kronos.register()` throws `IllegalStateException` on duplicate
- Example app (run with `cd example && ./gradlew run`) needs `docker-compose.yaml` up (MongoDB :27016, Redis :6379)
- Docs use mkdocs-material + mike; versioned via `mike deploy`

## Publishing
- Publishes to `s01.oss.sonatype.org` (snapshots or staging)
- Requires `signing.secretKeyFile` and credentials in `gradle.properties`
- `publishToMavenLocal` works without credentials
- **Artifact layout**:
  - `com.funyinkash:kronos:0.0.8` / `kronos-jvm` — core framework (transitive dep, not needed directly)
  - `com.funyinkash:kronos-mongo:0.0.8` — MongoDB backend (the one users add)
  - `com.funyinkash:kronos-exposed:0.0.8` — SQL/Exposed backend
- Users only add the backend artifact; core is pulled in via `api`/`compile` scope
- All artifacts are signed, include `-sources.jar` and `-javadoc.jar`
