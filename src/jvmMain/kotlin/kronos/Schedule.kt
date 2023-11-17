package kronos

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

//<editor-fold desc="Schedule Functions">

/**
 * @param jobName The unique name of a job that has already been registered
 * @param interval The time between jobs. Specify a value to make the job repeated.
 * If the period is **null** then the job will be treated asa one time Job
 * It is advised to use a minimum of 1 minute, so you don't choke your resources
 * @param delay
 * @param endTime The job wil not be repeated if it is after this time
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params The data that will be made available to your job during execution. Note that
 * **'cycleNumber'** is a reserved name and should not be included
 */
suspend fun Kronos.schedule(
    jobName: String,
    delay: Duration = Duration.ZERO,
    interval: Duration? = null,
    endTime: Long? = null,
    retries: Int? = null,
    params: Map<String, String>,
): String? {
    return schedule(
        jobName = jobName,
        startTime = Clock.System.now().plus(delay.inWholeMilliseconds, DateTimeUnit.MILLISECOND)
            .toEpochMilliseconds(),
        interval = interval,
        endTime = endTime,
        retries = retries,
        params = params
    )
}

/**
 * @param jobName The unique name of a job that has already been registered
 * @param interval The time between jobs. Specify a value to make the job repeated.
 * If the period is **null** then the job will be treated asa one time Job
 * It is advised to use a minimum of 1 minute, so you don't choke your resources
 * @param startTime
 * @param endTime The job wil not be repeated if it is after this time
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params the data that will be made available to your job during execution
 */
suspend fun Kronos.schedule(
    jobName: String,
    startTime: Long,
    interval: Duration? = null,
    endTime: Long? = null,
    retries: Int? = null,
    params: Map<String, String>,
): String? {

    val job = jobs[jobName] ?: throw IllegalStateException("Job with name has not been registered")
    val kronoJob = KronoJob(
        jobName = jobName,
        params = params,
        startTime = startTime,
        endTime = endTime,
        retires = retries ?: job.retries,
        interval = interval,
    )
    return addJob(kronoJob)
}


/**
 * @param jobName The unique name of a job that has already been registered
 * @param delay This would influence the start time of the periodic task
 * @param periodic Specify tight constraints on the frequency and interval of execution. Take a look at [Periodic.Companion.everyDay]
 * @param endTime The job wil not be repeated if it is after this time
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params The data that will be made available to your job during execution. Note that
 * **'cycleNumber'** is a reserved name and should not be included
 */
suspend fun Kronos.schedulePeriodic(
    jobName: String,
    delay: Duration = Duration.ZERO,
    periodic: Periodic,
    endTime: Long? = null,
    retries: Int? = null,
    params: Map<String, String>,
): String? {
    return schedulePeriodic(
        jobName = jobName,
        startTime = Clock.System.now().plus(delay.inWholeMilliseconds, DateTimeUnit.MILLISECOND)
            .toEpochMilliseconds(),
        periodic = periodic,
        endTime = endTime,
        retries = retries,
        params = params
    )
}


/**
 * @param jobName The unique name of a job that has already been registered
 * @param periodic Specify tight constraints on the frequency and interval of execution. Take a look at [Periodic.Companion.everyDay]
 * @param startTime
 * @param endTime The job wil not be repeated if it is after this time
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params The data that will be made available to your job during execution. Note that
 * **'cycleNumber'** is a reserved name and should not be included
 */
suspend fun Kronos.schedulePeriodic(
    jobName: String,
    startTime: Long,
    periodic: Periodic,
    endTime: Long? = null,
    retries: Int? = null,
    params: Map<String, String>,
): String? {

    val job = jobs[jobName] ?: throw IllegalStateException("Job with name has not been registered")
    val kronoJob = KronoJob(
        jobName = jobName,
        params = params,
        startTime = nextPeriodicTime(startTime, periodic),
        endTime = endTime,
        retires = retries ?: job.retries,
        periodic = periodic,
    )
    return addJob(kronoJob)
}

private fun nextPeriodicTime(startTime: Long, periodic: Periodic?): Long {
    return startTime.let {
        when (periodic?.every) {
            Periodic.Every.minute -> {
                startTime.plus(1.toDuration(DurationUnit.MINUTES).toLong(DurationUnit.MILLISECONDS))
            }

            Periodic.Every.hour -> {
                startTime.plus(1.toDuration(DurationUnit.HOURS).toLong(DurationUnit.MILLISECONDS))
            }

            Periodic.Every.day -> {
                startTime.plus(1.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MILLISECONDS))
            }

            Periodic.Every.week -> {
                startTime.plus(7.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MILLISECONDS))
            }

            Periodic.Every.month -> {
                startTime.plus(30.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MILLISECONDS))
            }

            Periodic.Every.year -> {
                startTime.plus(365.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MILLISECONDS))
            }

            null -> it
        }
    }
}
//</editor-fold>