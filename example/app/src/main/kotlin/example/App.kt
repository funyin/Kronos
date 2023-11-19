package example

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.lettuce.core.RedisClient
import kotlinx.coroutines.delay
import kronos.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

suspend fun main() {
    val mongoClient = MongoClient.create("mongodb://localhost:27016")
    val redisClient = RedisClient.create("redis://127.0.0.1:6379")
    val connection = redisClient.connect()
    Kronos.init(mongoClient = mongoClient, redisConnection = connection)
    Kronos.register(SayHello)
//    Kronos.schedule(
//        SayHello.name,/*say-hello*/
//        startTime = Instant.now().plusSeconds(60).toEpochMilli(),
//        params = mapOf(
//            "firsName" to "Funyin",
//            "lastName" to "Kash"
//        ),
//    )
    val jobId = Kronos.schedulePeriodic(
        jobName = SayHello.name,
        /*say-hello*/
//        startTime = Instant.now().plusSeconds(60).toEpochMilli(),
        periodic = Periodic.everyMinute(),
        params = mapOf(
            "firsName" to "Funyin",
            "lastName" to "Kash"
        ),
    )
//    delay(1000 * 40)
//    jobId?.let {
//        Kronos.checkJob(it)?.let {
//            println(it)
//        }
//    }
//    val droppedId = Kronos.dropJobId(jobId!!)
//    delay(1000 * 60)
//    val droppedJob = Kronos.dropJob(SayHello.name)
    delay(1000 * 60 * 7)
}

object SayHello : Job {
    override val name: String
        get() = "say-hello"

    override val retries: Int
        get() = 2

    override suspend fun execute(cycleNumber: Int, params: Map<String, Any>): Boolean {
        super.execute(cycleNumber, params)
        println("Hello ${params["firsName"]} ${params["lastName"]} ")
        return true
    }

}
