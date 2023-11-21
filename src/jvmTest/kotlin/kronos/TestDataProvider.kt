package kronos

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.lettuce.core.RedisClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlin.time.DurationUnit
import kotlin.time.toDuration

object TestDataProvider {
    val mongoClient: MongoClient = MongoClient.create("mongodb://localhost:27016")
    val redisClient: RedisClient = RedisClient.create("redis://127.0.0.1:6379")
    val sampleJob = object : Job {
        override val name: String
            get() = "sample-job"
    }

    fun registerSampleJob() {
        Kronos.register(sampleJob)
    }

    suspend fun scheduleSampleJob(): String? = Kronos.schedule(
        sampleJob.name,
        delay = 1.toDuration(DurationUnit.MINUTES),
        interval = 1.toDuration(DurationUnit.MINUTES),
        params = emptyMap()
    )

    fun initKronos(dispatcher: CoroutineDispatcher) {
        val connection = redisClient.connect()
        Kronos.init(
            mongoClient = mongoClient,
            redisConnection = connection,
            dispatcher = dispatcher,
            jobsDbName = "testJobsDb"
        )
    }
}