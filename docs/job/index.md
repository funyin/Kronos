---
comments: true
---

# Job

A `Job` defines the logic and callbacks for a scheduled task. Each `Job` has a unique name that links it to its scheduled entries in the database.

## Defining a Job

```kotlin
object SendReport : Job {
    override val name = "send-report"
    override val retries = 2  // retry up to 2 times on failure

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        val reportId = params["reportId"] as String
        // do work...
        return true  // return false to signal failure and trigger retry
    }
}
```

- `cycleNumber` starts at 1 and increments for each repeated execution.
- `params` contains the values passed at scheduling time, plus `"cycleNumber"` (injected automatically).
- Return `true` for success, `false` to trigger retries.

## Callbacks

All callbacks have default no-op implementations.

### `onSuccess`
Called when `execute` returns `true`.
```kotlin
override fun onSuccess(cycleNumber: Int, params: Map<String, Any>) { }
```

### `onFail`
Called once after **all retries are exhausted** and the job has still not succeeded.
```kotlin
override fun onFail(cycleNumber: Int, params: Map<String, Any>, exception: Exception?) { }
```

### `onRetryFail`
Called after each individual retry attempt fails (before the final `onFail`).
`retryCount` goes from 1 up to `Job.retries`.
```kotlin
override fun onRetryFail(retryCount: Int, cycleNumber: Int, params: Map<String, Any>, exception: Exception?) { }
```

### `onDrop`
Called each time a job record is removed from the database (after execution, or after `dropJobId`).
`lastJob` is `true` when this was the last remaining job of this name.
```kotlin
override fun onDrop(jobId: String, lastJob: Boolean) { }
```

### `onLasCycleDrop`
Called when the **final** cycle of a bounded job (`maxCycles` or `endTime`) is reached and the job is dropped.
```kotlin
override fun onLasCycleDrop(jobId: String, params: Map<String, Any>) { }
```

### `periodicJobLoaded`
Called when the next occurrence of a repeating job is inserted into the database (eagerly, before the current cycle finishes). Use this to track the latest job ID if needed.
```kotlin
override fun periodicJobLoaded(originJobId: String, nextJobId: String) { }
```

## Retries

Set `retries` to automatically retry a failed execution:

```kotlin
object SendReport : Job {
    override val retries = 3  // try up to 3 additional times after the first failure
}
```

Execution order on repeated failure:
1. `execute()` → `false`
2. `onRetryFail(retryCount = 1, ...)`
3. `execute()` → `false`
4. `onRetryFail(retryCount = 2, ...)`
5. `execute()` → `false`
6. `onFail(...)` ← called only after all retries are exhausted

## Blocking Execution

Override `challengeRun` to veto execution at runtime (after Kronos's own validation):

```kotlin
override fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean {
    return isPublicHoliday()  // return true to skip this cycle
}
```

The job is skipped for this tick if `challengeRun` returns `true`.
