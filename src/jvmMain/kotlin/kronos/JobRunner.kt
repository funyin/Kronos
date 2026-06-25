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
    val currentDateTime = currentInstant.toLocalDateTime(TimeZone.UTC)

    //I am using whole minutes instead of milliseconds because there seems to be a millisecond
    // glitch when running in test
    val startMinutesDiff =
        (currentInstant.toEpochMilliseconds() - job.startTime).toDuration(DurationUnit.MILLISECONDS).inWholeMinutes
    job.endTime?.let {
        val diff =
            (job.endTime - currentInstant.toEpochMilliseconds()).toDuration(DurationUnit.MILLISECONDS).inWholeMinutes
        if (diff < 0) return ValidationResult.overshot
    }


    val valid = if (job.periodic == null) {
        when {
            //the start time is in the past
            startMinutesDiff > 0 -> {
                return ValidationResult.overshot
            }

            startMinutesDiff == 0L -> true
            else -> false
        }
    } else {
        val matchMinute = currentDateTime.time.minute == job.periodic.minute
        val matchHour = currentDateTime.time.hour == job.periodic.hour
        val matchDayOfWeek = currentDateTime.dayOfWeek == job.periodic.dayOfWeek
        val matchDayOfMonth = currentDateTime.dayOfMonth == job.periodic.dayOfMonth
        val matchMonth = currentDateTime.month == job.periodic.month


        when (job.periodic.every) {
            Periodic.Every.minute -> true

            Periodic.Every.hour -> matchMinute

            Periodic.Every.day -> {
                matchHour && matchMinute
            }

            Periodic.Every.week -> {
                matchDayOfWeek && matchHour && matchMinute
            }

            Periodic.Every.month -> {
                matchDayOfMonth && matchHour && matchMinute
            }

            Periodic.Every.year -> {
                matchMonth && matchDayOfMonth && matchHour && matchMinute
            }
        }
    }




    return if (valid) ValidationResult.valid
    else ValidationResult.scheduled
}

private enum class ValidationResult {
    valid, overshot, scheduled
}
