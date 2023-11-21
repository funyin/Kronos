package kronos

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
     * call back for failure during retries
     * @param retryCount the number of retries this will go from 0 -> ([Job.retries]-1)
     * That is because the firs call back [retryCount] will be 0 because the number of retries is 0
     * @param cycleNumber this would be greater than 1 for a repeated job [Job.retries]
     */
    fun onRetryFail(retryCount: Int, cycleNumber: Int, params: Map<String, Any>) {
        println("KRONOJOB($name) RetryFail: ")
        println("retries-> $retryCount")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
    }

    /**
     * call back for failure after retries
     * @param cycleNumber this would be greater than 1 for a repeated job [Job.retries]
     */
    fun onFail(cycleNumber: Int, params: Map<String, Any>) {
        println("KRONOJOB($name) Fail: ")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
    }

    /**
     * call back for when job is successful
     * @param cycleNumber this would be greater than 1 for a repeated job [Job.retries]
     */
    fun onSuccess(cycleNumber: Int, params: Map<String, Any>) {
        println("KRONOJOB($name) Success: ")
        println("cycle-> $cycleNumber")
        println("params-> $params")
        println("time-> ${LocalDateTime.now()}")
        println()
    }

    /**
     * Add your own validation that must return false for the job to run
     * This is called after the job has passed Kronos validation
     * If this returns [true] then the job will no run
     */
    fun challengeRun(cycleNumber: Int, params: Map<String, Any>): Boolean = false

    /**
     * Another job has been loaded for a particular schedule. at this point your original job
     * might still be in execution but the nex job has already been scheduled eagerly.
     * This will only happen for periodic jobs or jobs with intervals
     */
    fun periodicJobLoaded(originJobId: String, nextJobId: String) {}

    /**
     * A Job has executed and has been dropped from the db
     * This will only happen when Kronos drops the job, you will not get this
     * callback for [Kronos.dropJobId] or [Kronos.dropJob] because those are only triggerred by you
     */
    fun onDrop(jobId: String, lastJob: Boolean) {}
}