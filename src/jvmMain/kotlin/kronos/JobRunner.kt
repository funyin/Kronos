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

        val response =
            kacheController.getAll(collection = collection, serializer = KronoJob.serializer()) {
                find(Filters.empty()).toList()
            }

        for (job in response) {

            val dropJob: suspend () -> Unit = {
                kacheController.remove(job.id, collection = collection) {
                    deleteOne(Filters.eq("_id", job.id)).wasAcknowledged()
                }
            }

            if (validate(job, currentInstant) && job.locks == 0) {
                kacheController.set(collection, KronoJob.serializer()) {
                    findOneAndUpdate(Filters.eq("_id", job.id), Updates.inc(KronoJob::locks.name, 1))
                }
                val task = jobs[job.jobName]
                task?.let {
                    val jobParams = job.params.toMutableMap()
                    val cycleNumber = jobParams["cycleNumber"]?.toInt() ?: 1
                    jobParams["cycleNumber"] = cycleNumber.toString()

                    if (job.interval != null || job.periodic != null) {
                        val reschedule: suspend () -> Unit = {

                            val params = jobParams.toMutableMap()
                            params["cycleNumber"] = (cycleNumber + 1).toString()

                            if (job.interval != null) {
                                schedule(
                                    jobName = job.jobName,
                                    interval = job.interval,
                                    endTime = job.endTime,
                                    params = params,
                                    retries = job.retires
                                )
                            }

                            if (job.periodic != null) {
                                schedulePeriodic(
                                    jobName = job.jobName,
                                    delay = Duration.ZERO,
                                    periodic = job.periodic,
                                    endTime = job.endTime,
                                    params = params,
                                    retries = job.retires
                                )
                            }
                        }

                        //Reschedule
                        if (job.endTime != null) {
                            if (currentInstant < Instant.fromEpochMilliseconds(job.endTime))
                                reschedule()
                            dropJob()
                        } else
                            reschedule()
                    }
                    coroutineScope.launch {
                        val execute: suspend () -> Boolean = {
                            it.execute(cycleNumber, jobParams)
                        }

                        var success = execute()
                        var retries = job.retires
                        if (!success) {
                            it.onFail(cycleNumber = cycleNumber, jobParams)
                            while (!success && retries > 0) {
                                success = execute()
                                if (!success)
                                    it.onRetryFail(
                                        retryCount = (job.retires - retries),
                                        cycleNumber = cycleNumber,
                                        params = jobParams
                                    )
                                retries--
                            }
                        } else
                            it.onSuccess(cycleNumber = cycleNumber, jobParams)
                        dropJob()
                    }
                } ?: IllegalStateException("Job with name ${job.jobName} not registered")
            }
        }
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