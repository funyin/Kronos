package kronos

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.*
import kotlin.time.Duration

internal suspend fun Kronos.runner() {
    while (true) {
        delay(1000 * 60)
        println()
        println("Kronos Ping")
        println()
        val currentInstant = Clock.System.now()

        val response = kacheController.getAll(collection = collection, serializer = KronoJob.serializer()) {
            find(Filters.empty()).toList()
        }

        for (kronoJob in response) {
            coroutineScope.launch {
                runJob(kronoJob, currentInstant)
            }
        }

    }
}

private suspend fun Kronos.runJob(kronoJob: KronoJob, currentInstant: Instant) {
    val dropJob: suspend () -> Boolean = {
        kacheController.remove(kronoJob.id, collection = collection) {
            deleteOne(Filters.eq("_id", kronoJob.id)).wasAcknowledged()
        }
    }

    if (validate(kronoJob, currentInstant) && kronoJob.locks == 0) {
        val task = jobs[kronoJob.jobName]
        task?.let { job ->

            val jobParams = kronoJob.params.toMutableMap()
            val cycleNumber = jobParams["cycleNumber"]?.toInt() ?: 1
            jobParams["cycleNumber"] = cycleNumber.toString()

            if (!job.challenge(cycleNumber, jobParams))
                return@let

            kacheController.set(collection, KronoJob.serializer()) {
                findOneAndUpdate(Filters.eq("_id", kronoJob.id), Updates.inc(KronoJob::locks.name, 1))
            }

            if (kronoJob.interval != null || kronoJob.periodic != null) {
                val reschedule: suspend () -> Unit = {

                    val params = jobParams.toMutableMap()
                    params["cycleNumber"] = (cycleNumber + 1).toString()
                    val delay = kronoJob.interval ?: Duration.ZERO
                    KronoJob(
                        jobName = kronoJob.jobName,
                        //This is what sets the start time for the next job
                        startTime = delayToStartTime(delay = delay),
                        interval = kronoJob.interval,
                        endTime = kronoJob.endTime,
                        params = params,
                        periodic = kronoJob.periodic,
                        retries = kronoJob.retries,
                        maxCycles = kronoJob.maxCycles,
                        originCreatedAt = kronoJob.originCreatedAt,
                        overshotAction = kronoJob.overshotAction,
                    ).also {
                        rescheduleJob(it)?.also { jobId ->
                            job.periodicJobLoaded(kronoJob.id, jobId)
                        }
                    }
                }

                //Reschedule
                if (kronoJob.endTime != null || kronoJob.maxCycles != null) {
                    val underEndTime =
                        kronoJob.endTime?.let {
                            currentInstant < Instant.fromEpochMilliseconds(it)
                        } ?: true
                    val underMaxCycles = kronoJob.maxCycles?.let {
                        cycleNumber < it
                    } ?: true
                    if (underEndTime || underMaxCycles)
                        reschedule()
                    else
                        dropJob().takeIf { it }.also {
                            job.onDrop(kronoJob.id, lastJob = true)
                        }
                } else
                    reschedule()
            }
            coroutineScope.launch {
                val execute: suspend () -> Boolean = {
                    job.execute(cycleNumber, jobParams)
                }

                var success = execute()
                var retries = kronoJob.retries
                if (!success) {
                    job.onFail(cycleNumber = cycleNumber, jobParams)
                    while (!success && retries > 0) {
                        success = execute()
                        if (!success)
                            job.onRetryFail(
                                retryCount = (kronoJob.retries - retries),
                                cycleNumber = cycleNumber,
                                params = jobParams
                            )
                        retries--
                    }
                } else
                    job.onSuccess(cycleNumber = cycleNumber, jobParams)
                dropJob()
            }
        } ?: IllegalStateException("Job with name '${kronoJob.jobName}' is not registered")
    }
}

//Verify that it is time to run the job
private fun validate(
    job: KronoJob,
    currentInstant: Instant,
): Boolean {
    val currentDateTime = currentInstant.toLocalDateTime(TimeZone.UTC)
    val jobTime = Instant.fromEpochMilliseconds(job.startTime)

    return if (job.periodic == null) {
        val minutesDiff = currentInstant.periodUntil(jobTime, TimeZone.UTC).minutes
        minutesDiff == 0
    } else {
        val creationInstant = Instant.fromEpochMilliseconds(job.createdAt)
        val creationDateTime = creationInstant.toLocalDateTime(TimeZone.UTC)
        val matchMinute = currentDateTime.time.minute == job.periodic.minute
        val matchHour = currentDateTime.time.minute == job.periodic.minute
        val matchDayOfWeek = currentDateTime.dayOfWeek == job.periodic.dayOfWeek
        val matchDayOfMonth = currentDateTime.dayOfMonth == job.periodic.dayOfMonth
        val canStart = currentInstant.toEpochMilliseconds() >= job.startTime

        canStart && when (job.periodic.every) {
            Periodic.Every.minute -> true
            Periodic.Every.hour -> {
                val newHour = creationInstant
                    .periodUntil(currentInstant, TimeZone.UTC).hours > 0
                newHour && matchMinute
            }

            Periodic.Every.day -> {
                val newDay = creationInstant
                    .periodUntil(currentInstant, TimeZone.UTC).days > 0
                newDay && matchHour && matchMinute
            }

            Periodic.Every.week -> {
                val newWeek = creationInstant
                    .periodUntil(currentInstant, TimeZone.UTC).days > 7
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
}