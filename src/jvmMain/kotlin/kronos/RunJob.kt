package kronos

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

        store.acquireLock(kronoJob.id) ?: return@let

        val underEndTime =
            kronoJob.endTime?.let {
                currentInstant < Instant.fromEpochMilliseconds(it)
            } ?: true
        val underMaxCycles = kronoJob.maxCycles?.let {
            cycleNumber < it
        } ?: true
        val canReschedule = underEndTime && underMaxCycles

        if (kronoJob.repeatedJob) {
            val reschedule: suspend () -> Unit = {

                val params = jobParams.toMutableMap()
                params["cycleNumber"] = (cycleNumber + 1).toString()
                val delay = kronoJob.interval ?: Duration.ZERO
                KronoJob(
                    jobName = kronoJob.jobName,
                    //This is what sets the start time for the next job
                    startTime = if (kronoJob.periodic != null)
                        nextPeriodicTime(kronoJob.startTime, kronoJob.periodic)
                    else
                        currentInstant.plus(delay).toEpochMilliseconds(),
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
            when {
                kronoJob.closedEnd && canReschedule -> reschedule()
                //open ended auto transactions
                !kronoJob.closedEnd -> reschedule()
            }

        }

        var exception: Exception? = null
        val execute: suspend () -> Boolean = {
            try {
                job.execute(cycleNumber, jobParams)
            } catch (e: Exception) {
                println(e)
                exception = e
                false
            }
        }

        var success = execute()
        var retries = kronoJob.retries
        if (!success) {
            while (!success && retries > 0) {
                retries--
                val retryCount = kronoJob.retries - retries
                jobParams["retryCount"] = "$retryCount"
                success = execute()
                if (!success)
                    job.onRetryFail(
                        retryCount = retryCount,
                        cycleNumber = cycleNumber,
                        params = jobParams,
                        exception = exception
                    )
            }
            if (!success)
                job.onFail(cycleNumber = cycleNumber, jobParams, exception)
        }
        if (success)
            job.onSuccess(cycleNumber = cycleNumber, jobParams)
        dropJob()
            .takeIf { it }
            .also {
                val lastCycle = if (kronoJob.repeatedJob) !canReschedule else true
                if (lastCycle)
                    job.onLasCycleDrop(kronoJob.id, jobParams)
            }

    } ?: throw IllegalStateException("Job with name '${kronoJob.jobName}' is not registered")
}
