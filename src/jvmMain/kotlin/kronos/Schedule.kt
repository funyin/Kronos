package kronos

import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

//<editor-fold desc="Schedule Functions">

/**
 * @param jobName The unique name of a job that has already been registered
 * @param interval The time between jobs. Specify a value to make the job repeated.
 * If the period is **null** then the job will be treated asa one time Job
 * It is advised to use a minimum of 1 minute, so you don't choke your resources
 * @param delay if delay is not specified or is set to zero, The job will run at the next minute
 * @param endTime The job wil not be repeated if it is after this time
 * @param maxCycles The job wil not be repeated after this number of cycles
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params The data that will be made available to your job during execution. Note that
 * **'cycleNumber'** is a reserved name and should not be included
 * @param overshotAction What do you want to happen when the job runner finds that the job start time is in the past.
 * This can happen when the job does no run at the start time because the system is down or was
 * not handled by the job runner
 */
suspend fun Kronos.schedule(
    jobName: String,
    delay: Duration = Duration.ZERO,
    interval: Duration? = null,
    endTime: Long? = null,
    maxCycles: Int? = null,
    retries: Int? = null,
    params: Map<String, String>,
    overshotAction: OvershotAction = OvershotAction.Drop,
): String? {
    return schedule(
        jobName = jobName,
        startTime = delayToStartTime(delay),
        interval = interval,
        endTime = endTime,
        maxCycles = maxCycles,
        retries = retries,
        params = params,
        overshotAction = overshotAction
    )
}

/**
 * @param jobName The unique name of a job that has already been registered
 * @param interval The time between jobs. Specify a value to make the job repeated.
 * This will be set to 1 minute if it is null and [maxCycles] !=null
 * If the period is **null** then the job will be treated asa one time Job
 * It is advised to use a minimum of 1 minute which is the highest precision, so you don't choke your resources
 * @param startTime
 * @param endTime The job wil not be repeated if it is after this time
 * @param maxCycles The job wil not be repeated after this number of cycles.
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params the data that will be made available to your job during execution
 * @param overshotAction What do you want to happen when the job runner finds that the job start time is in the past.
 * This can happen when the job does no run at the start time because the system is down or was
 * not handled by the job runner
 */
suspend fun Kronos.schedule(
    jobName: String,
    startTime: Long,
    interval: Duration? = null,
    endTime: Long? = null,
    maxCycles: Int? = null,
    retries: Int? = null,
    params: Map<String, String>,
    overshotAction: OvershotAction = OvershotAction.Drop,
): String? {

    val job = getValidJob(jobName)
    val kronoJob = KronoJob(
        jobName = jobName,
        params = params,
        startTime = startTime,
        endTime = endTime,
        maxCycles = maxCycles,
        retries = retries ?: job.retries,
        interval = if (maxCycles != null && interval == null) 1.minutes else interval,
        overshotAction = overshotAction
    )
    return addJob(kronoJob)
}


/**
 * @param jobName The unique name of a job that has already been registered
 * @param delay This would influence the start time of the periodic task
 * @param periodic Specify tight constraints on the frequency and interval of execution. Take a look at [Periodic.Companion.everyDay]
 * @param endTime The job wil not be repeated if it is after this time
 * @param maxCycles The job wil not be repeated after this number of cycles
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params The data that will be made available to your job during execution. Note that
 * **'cycleNumber'** is a reserved name and should not be included
 * @param overshotAction What do you want to happen when the job runner finds that the job start time is in the past.
 * This can happen when the job does no run at the start time because the system is down or was
 * not handled by the job runner
 */
suspend fun Kronos.schedulePeriodic(
    jobName: String,
    delay: Duration = Duration.ZERO,
    periodic: Periodic,
    endTime: Long? = null,
    maxCycles: Int? = null,
    retries: Int? = null,
    params: Map<String, String>,
    overshotAction: OvershotAction = OvershotAction.Drop,
): String? {
    return schedulePeriodic(
        jobName = jobName,
        startTime = delayToStartTime(delay),
        periodic = periodic,
        endTime = endTime,
        maxCycles = maxCycles,
        retries = retries,
        params = params,
        overshotAction = overshotAction
    )
}


/**
 * @param jobName The unique name of a job that has already been registered
 * @param periodic Specify tight constraints on the frequency and interval of execution. Take a look at [Periodic.Companion.everyDay]
 * @param startTime
 * @param endTime The job wil not be repeated if it is after this time
 * @param maxCycles The job wil not be repeated after this number of cycles
 * @param retries The number of retries if the job execution fails.
 * Falls back to the [Job.retries] of the Job if not specified
 * @param params The data that will be made available to your job during execution. Note that
 * **'cycleNumber'** is a reserved name and should not be included
 * @param overshotAction What do you want to happen when the job runner finds that the job start time is in the past.
 * This can happen when the job does no run at the start time because the system is down or was
 * not handled by the job runner
 */
suspend fun Kronos.schedulePeriodic(
    jobName: String,
    startTime: Long,
    periodic: Periodic,
    endTime: Long? = null,
    maxCycles: Int? = null,
    retries: Int? = null,
    params: Map<String, String>,
    overshotAction: OvershotAction = OvershotAction.Drop,
): String? {

    val job = getValidJob(jobName)
    val kronoJob = KronoJob(
        jobName = jobName,
        params = params,
        startTime = nextPeriodicTime(startTime, periodic),
        endTime = endTime,
        maxCycles = maxCycles,
        retries = retries ?: job.retries,
        periodic = periodic,
        overshotAction = overshotAction
    )
    return addJob(kronoJob)
}

internal fun Kronos.getValidJob(jobName: String) =
    jobs[jobName] ?: throw IllegalStateException("Job with name '$jobName' has not been registered")

internal suspend fun Kronos.rescheduleJob(job: KronoJob): String? {
    //validate that job is registered
    getValidJob(jobName = job.jobName)
    return addJob(job)
}

internal fun delayToStartTime(delay: Duration) =
    Clock.System.now().plus(delay).toEpochMilliseconds()

/**
 * Returns the epoch millis of the smallest occurrence of [periodic] that is >= [startTime].
 *
 * Used both to compute a job's first occurrence at schedule-time, and (by passing
 * `lastFiredStartTime + 1 minute`) to compute the next occurrence strictly after one that
 * just fired.
 */
internal fun nextPeriodicTime(startTime: Long, periodic: Periodic): Long =
    alignToPeriodic(Instant.fromEpochMilliseconds(startTime), periodic).toEpochMilliseconds()
//</editor-fold>