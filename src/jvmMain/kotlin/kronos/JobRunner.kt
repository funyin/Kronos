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
        val creationInstant = Instant.fromEpochMilliseconds(job.createdAt)
        val creationDateTime = creationInstant.toLocalDateTime(TimeZone.UTC)
        val matchMinute = currentDateTime.time.minute == job.periodic.minute
        val matchHour = currentDateTime.time.hour == job.periodic.hour
        val matchDayOfWeek = currentDateTime.dayOfWeek == job.periodic.dayOfWeek
        val matchDayOfMonth = currentDateTime.dayOfMonth == job.periodic.dayOfMonth
        //I am using whole minutes instead of milliseconds because there seems to be a millisecond
        // glitch when running in test


        when (job.periodic.every) {
            Periodic.Every.minute -> true
            Periodic.Every.hour -> {
                val newHour = creationInstant
                    .periodUntil(currentInstant, TimeZone.UTC).hours >= 0
                newHour && matchMinute
            }

            Periodic.Every.day -> {
                val newDay = creationInstant
                    .periodUntil(currentInstant, TimeZone.UTC).days >= 0
                newDay && matchHour && matchMinute
            }

            Periodic.Every.week -> {
                val newWeek = creationInstant
                    .periodUntil(currentInstant, TimeZone.UTC).days >= 7
                newWeek && matchDayOfWeek && matchHour && matchMinute
            }

            Periodic.Every.month -> {
                val newMonth = creationDateTime.month != currentDateTime.month
                newMonth && matchDayOfMonth && matchHour && matchMinute
            }

            Periodic.Every.year -> {
                val newYear = creationDateTime.year != currentDateTime.year
                newYear && matchDayOfMonth && matchHour && matchMinute
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