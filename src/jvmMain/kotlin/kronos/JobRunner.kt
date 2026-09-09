package kronos

import co.touchlab.kermit.Logger
import kotlinx.coroutines.*
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal suspend fun Kronos.runner() {
    while (coroutineScope.isActive) {
        println()
        Logger.d("Kronos Ping")
        println()
        handleJobs()
        //Handling tasks before the delay so IT can start work as soon as it boots up
        delay(1.minutes)
    }
}

internal suspend fun Kronos.handleJobs(currentInstant: Instant = Clock.System.now()) {
    try {
        val response = store.fetchDueJobs(currentInstant.toEpochMilliseconds())
        lastPingTime = Clock.System.now().toLocalDateTime(TimeZone.UTC)
        supervisorScope {
            for (kronoJob in response) {
                launch {
                    handleJob(kronoJob, currentInstant)
                }
            }
        }
    } catch (e: Throwable) {
        onError?.invoke(e)
        Logger.e("Runner error: ${e.message}", e)
    }
}

suspend fun Kronos.handleJob(kronoJob: KronoJob, currentInstant: Instant = Clock.System.now()) {

    val validationResult = validate(kronoJob, currentInstant)

    when {
        validationResult == ValidationResult.overshot -> {
            when (kronoJob.overshotAction) {
                OvershotAction.Fire -> runJob(kronoJob, currentInstant)
                OvershotAction.Drop -> dropJobId(kronoJob.id)
                OvershotAction.Nothing -> {}
            }
        }

        validationResult == ValidationResult.valid && kronoJob.locks == 0 -> {
            runJob(kronoJob, currentInstant)
        }
    }
}

//Verify that it is time to run the job
private fun validate(
    job: KronoJob,
    currentInstant: Instant,
): ValidationResult {

    //I am using whole minutes instead of milliseconds because there seems to be a millisecond
    // glitch when running in test
    val startMinutesDiff =
        (currentInstant.toEpochMilliseconds() - job.startTime).toDuration(DurationUnit.MILLISECONDS).inWholeMinutes
    job.endTime?.let {
        val diff =
            (job.endTime - currentInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS).inWholeMinutes
        if (diff < 0) return ValidationResult.overshot
    }

    //job.startTime is always kept aligned to the exact target occurrence (see
    //nextPeriodicTime/alignToPeriodic), so periodic and one-off jobs are validated the same way:
    //this also means a missed periodic occurrence is now correctly reported as overshot instead
    //of silently waiting for the next matching occurrence.
    val valid = when {
        //the start time is in the past
        startMinutesDiff > 0 -> return ValidationResult.overshot
        startMinutesDiff == 0L -> true
        else -> false
    }

    return if (valid) ValidationResult.valid
    else ValidationResult.scheduled
}

private enum class ValidationResult {
    valid, overshot, scheduled
}
