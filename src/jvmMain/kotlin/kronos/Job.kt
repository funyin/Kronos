package kronos

import java.lang.Exception
import java.time.LocalDateTime

/**
 * Kronos Job For Scheduling tasks
 */
interface Job {
    /**
     * The Job Name. this should be unique
     */
    val name: String

    /**
     * The number of times the job should be retired,
     * overridden by 'retries' in [Kronos.schedulePeriodic]
     */
    val retries: Int
        get() = 0


    /**
     * execute task for job, return true if job was successful
     */
    suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        println("KRONOJOB($name) Exec: ")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
        return true
    }

    /**
     * Called after each individual retry attempt fails. [retryCount] goes from 1 to [retries].
     * [onFail] is called once all retries are exhausted.
     */
    fun onRetryFail(retryCount: Int, cycleNumber: Int, params: Map<String, Any>, exception: Exception?) {
        println("KRONOJOB($name) RetryFail: ")
        println("retries-> $retryCount")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
    }

    /**
     * Called once after all retries are exhausted and the job has still not succeeded.
     */
    fun onFail(cycleNumber: Int, params: Map<String, Any>, exception: Exception?) {
        println("KRONOJOB($name) Fail: ")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
    }

    /**
     * Called when [execute] returns `true`.
     */
    fun onSuccess(cycleNumber: Int, params: Map<String, Any>) {
        println("KRONOJOB($name) Success: ")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
    }

    /**
     * Custom pre-run gate called after Kronos's own validation. Return `true` to skip this cycle.
     * Use this for runtime conditions (e.g. skip on public holidays) that can't be expressed in the schedule.
     */
    fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean = false

    /**
     * Called when the next occurrence of a repeating job has been inserted into the database.
     * Useful for tracking the current job ID when you hold a reference to it.
     */
    fun periodicJobLoaded(originJobId: String, nextJobId: String) {}

    /**
     * Called each time a job record is removed from the database.
     * [lastJob] is `true` when no further jobs with this name remain.
     */
    fun onDrop(jobId: String, lastJob: Boolean) {}

    /**
     * Called when the final cycle of a bounded job (`maxCycles` or `endTime`) completes and the job is dropped.
     */
    fun onLasCycleDrop(jobId: String, params: Map<String, Any>) {}
}