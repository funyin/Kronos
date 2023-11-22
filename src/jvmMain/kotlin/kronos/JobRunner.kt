package kronos

import com.mongodb.client.model.Filters
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration

internal suspend fun Kronos.runner() {
    while (coroutineScope.isActive) {
        delay(1.minutes)
        println()
        println("Kronos Ping")
        println()

        handleJobs()
    }
}

internal suspend fun Kronos.handleJobs(currentInstant: Instant = Clock.System.now()) {

    val response = kacheController.getAll(collection = collection, serializer = KronoJob.serializer()) {
        find(Filters.empty()).toList()
    }


    for (kronoJob in response) {
        coroutineScope.launch {
            handleJob(kronoJob, currentInstant)
        }
    }
}

private suspend fun Kronos.handleJob(kronoJob: KronoJob, currentInstant: Instant) {

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
        if (diff < 0)
            return ValidationResult.overshot
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
            Periodic.Every.hour -> {
                matchMinute
            }

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




    return if (valid)
        ValidationResult.valid
    else
        ValidationResult.scheduled
}

private enum class ValidationResult {
    valid,
    overshot,
    scheduled
}