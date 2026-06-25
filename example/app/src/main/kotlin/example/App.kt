package example


import kotlinx.coroutines.delay
import kronos.*
import kronos.mongo.init
import kotlin.time.Duration.Companion.minutes

suspend fun main() {

    //Initialize
    // make sure you have your container ready
    Kronos.init(
        mongoConnectionString = "mongodb://localhost:27016",
        redisConnectionString = "redis://localhost:6379"
    )
    //Register a Job
    Kronos.register(SayHello)
    Kronos.register(TestJob)
    //Schedule a one time job
    Kronos.schedule(
        jobName = SayHello.name,
        /*say-hello*/
//        startTime = Instant.now().plusSeconds(60).toEpochMilli(),
        params = mapOf(
            "firsName" to "Funyin",
            "lastName" to "Kash"
        ),
    )

    //Schedule a periodic job an get back the jobId
    val jobId = Kronos.schedulePeriodic(
        jobName = SayHello.name,
        /*say-hello*/
//        startTime = Instant.now().plusSeconds(60).toEpochMilli(),
        periodic = Periodic.everyMinute(),
//        periodic = Periodic.everyHour(minute = 5),
//        periodic = Periodic.everyWeek(dayOfWeek = 7, hour = 4, minute = 2),
//        periodic = Periodic.everyMonth(dayOfMonth = 12, hour = 4, minute = 2),
//        periodic = Periodic.everyYear(month = 1, dayOfMonth = 7, hour = 4, minute = 2),
        params = mapOf(
            "firsName" to "Funyin",
            "lastName" to "Kash"
        ),
    )

    delay(2.minutes)
    //Drop Job By Id
    jobId?.let { Kronos.dropJobId(it) }
    //Drop Job By Name
    jobId?.let { Kronos.dropJob(SayHello.name) }
//    Kronos.dropAll()
    delay(1000 * 60 * 7)
}

object SayHello : Job {
    override val name: String
        get() = "say-hello"

    override val retries: Int
        get() = 2

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        super.execute(cycleNumber, params)
        println("Hello ${params["firsName"]} ${params["lastName"]} $cycleNumber")
        //Testing functionality if scheduling an instant job within an execution window: PASSED
        Kronos.schedule(
            TestJob.name,
            maxCycles = 2,
            params = mapOf()
        )
        return true
    }

}

object TestJob : Job {
    override val name: String
        get() = "test-hello"

    override val retries: Int
        get() = 2

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        super.execute(cycleNumber, params)
        println("TESTJOB ${params["firsName"]} ${params["lastName"]} $cycleNumber")
        return true
    }

}
