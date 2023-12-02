package kronos

import com.mongodb.kotlin.client.coroutine.MongoClient
import com.redis.testcontainers.RedisContainer
import com.redis.testcontainers.RedisStackContainer
import io.lettuce.core.RedisClient
import io.mockk.spyk
import kotlinx.coroutines.CoroutineDispatcher
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import kotlin.time.DurationUnit
import kotlin.time.toDuration


object TestDataProvider {
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

    fun startContainers() {
        val mongoDBContainer = MongoDBContainer(DockerImageName.parse("mongo:4.0.10"))
        mongoDBContainer.start()
        mongoConnectionString = mongoDBContainer.connectionString

        val redisContainer =
            RedisStackContainer(RedisStackContainer.DEFAULT_IMAGE_NAME.withTag(RedisStackContainer.DEFAULT_TAG))
        redisContainer.start()
        redisConnectionString = redisContainer.redisURI
    }

    private lateinit var mongoConnectionString: String
    private lateinit var redisConnectionString: String

    fun initKronos(dispatcher: CoroutineDispatcher) {
        val mongoClient = MongoClient.create(mongoConnectionString)

        val redisClient = RedisClient.create(redisConnectionString)
        val redisConnection = redisClient.connect()
        Kronos.init(
            mongoClient = mongoClient,
            redisConnection = redisConnection,
            dispatcher = dispatcher,
            jobsDbName = "testJobsDb"
        )
    }

    val sampleSpyJob = spyk<Job>(object : Job {
        override val name: String
            get() = "one-time-job"
    })
}