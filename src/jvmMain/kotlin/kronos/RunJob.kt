package kronos

import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import kotlinx.datetime.Instant
import kotlin.time.Duration

internal suspend fun Kronos.runJob(
    kronoJob: KronoJob,
    currentInstant: Instant,
) {


    val dropJob: suspend () -> Boolean = {
        dropJobId(kronoJob.id)
    }


    val task = jobs[kronoJob.jobName]
    task?.let { job ->

        val jobParams = kronoJob.params.toMutableMap()
        val cycleNumber = jobParams["cycleNumber"]?.toInt() ?: 1
        jobParams["cycleNumber"] = cycleNumber.toString()

        if (job.challengeRun(cycleNumber, jobParams))
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
                    startTime = if (kronoJob.periodic != null)
                        nextPeriodicTime(currentInstant.toEpochMilliseconds(), kronoJob.periodic)
                    else
                        delayToStartTime(delay = delay),
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

    } ?: IllegalStateException("Job with name '${kronoJob.jobName}' is not registered")
}