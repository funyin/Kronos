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
}