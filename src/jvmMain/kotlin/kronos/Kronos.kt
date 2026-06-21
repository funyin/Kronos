package kronos

import com.funyinkash.kachecontroller.KacheController
import com.funyinkash.kachecontroller.Model
import com.mongodb.client.model.Filters
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

@Serializable
object Kronos {
    internal val jobs: MutableMap<String, Job> = mutableMapOf()
    internal lateinit var mongoClient: MongoClient

    internal val collection by lazy { mongoClient.getDatabase(jobsDbName).getCollection<KronoJob>("jobs") }
    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        onError?.invoke(e)
    }
    internal lateinit var coroutineScope: CoroutineScope

    internal lateinit var redisConnection: StatefulRedisConnection<String, String>
    lateinit var jobsDbName: String
    var cacheExpiry: Duration? = null
    var onError: ((Throwable) -> Unit)? = null
    var lastPingTime: LocalDateTime? = null
        internal set

    internal val mongoClientInitialized
        get() = ::mongoClient.isInitialized
    internal val coroutineScopeInitialized
        get() = ::coroutineScope.isInitialized
    internal val redisConnectionInitialized
        get() = ::redisConnection.isInitialized

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    internal val kacheController: KacheController by lazy {
        val client = redisConnection.coroutines()
        KacheController(client = client)
    }

    /**
     * @throws IllegalStateException on attempting to initialize a second time
     */
    fun init(
        mongoConnectionString: String,
        redisConnectionString: String,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        jobsDbName: String = "jobsDb",
        cacheExpiry: Duration? = null,

        ): Kronos {
        if (this::coroutineScope.isInitialized) throw IllegalStateException("Kronos already initialized")


        this.jobsDbName = jobsDbName
        this.mongoClient = MongoClient.create(mongoConnectionString)
        this.redisConnection = RedisClient.create(redisConnectionString).connect()
        coroutineScope = CoroutineScope(dispatcher + exceptionHandler)
        this.cacheExpiry = cacheExpiry
        this.onError = onError
        //runner is not started for test dispatchers
        //so I can control the curren time passed to the runner
        if (dispatcher in listOf(Dispatchers.IO, Dispatchers.Main, Dispatchers.Unconfined, Dispatchers.Default)) {
            coroutineScope.launch {
                runner()
            }
        }
        return this
    }

    fun onError(listener: (e: Throwable) -> Unit) {
        this.onError = listener
    }

    internal fun shutDown() {
        val unsetField: (String) -> Unit = {
            javaClass.getDeclaredField(it).apply {
                isAccessible = false
                set(this@Kronos, null)
            }
        }

        coroutineScope.cancel()
        unsetField(::coroutineScope.name)
        unsetField(::redisConnection.name)
        unsetField(::mongoClient.name)
        jobs.clear()
    }

    fun register(job: Job) {
        if (jobs.containsKey(job.name)) throw IllegalStateException("Job with name: '${job.name}' already registered")
        jobs[job.name] = job
    }

    /**
     * Drop a job by the jobId
     */
    suspend fun dropJobId(id: String): Boolean {
        return kacheController.remove(id, collection = collection) {
            findOneAndDelete(Filters.eq("_id", id))?.let { job ->
                val count = countDocuments(Filters.eq(KronoJob::jobName.name, job.jobName))
                jobs[job.jobName]?.onDrop(id, lastJob = count == 0L)
                true
            } ?: false
        }
    }

    /**
     * Drop all jobs by [Job.name]
     */
    suspend fun dropJob(name: String): Boolean {
        val kronoJobs = kacheController.getAll(
            collection, serializer = KronoJob.serializer(), expire = cacheExpiry
        ) {
            collection.find(Filters.eq(KronoJob::jobName.name, name)).toList()
        }
        return kacheController.removeAll(collection = collection) {
            deleteMany(Filters.eq(KronoJob::jobName.name, name)).wasAcknowledged()
        }.takeIf { it }?.let {
            kronoJobs.forEachIndexed { index, kronoJob ->
                jobs[name]?.onDrop(kronoJob.id, lastJob = index == kronoJobs.lastIndex)
            }
            true
        } ?: false
    }

    suspend fun dropAll(): Boolean {
        for (job in jobs) {
            dropJob(job.key)
        }
        return kacheController.getAll(collection, KronoJob.serializer(), expire = cacheExpiry) {
            collection.find().toList()
        }.isEmpty()
    }

    internal suspend fun addJob(kronoJob: KronoJob): String? {
        val job = kacheController.set(collection, serializer = KronoJob.serializer()) {
            if (insertOne(kronoJob).wasAcknowledged()) kronoJob
            else null
        }
        return job?.id?.also {
            //Checks if the Job is valid to be executed a current time.
            //This solves the problem of scheduling an instant job inside a kronos window
            //i.e within the same minute
            val now = Clock.System.now()
            val currentMinute = now.toEpochMilliseconds().milliseconds.toLong(DurationUnit.MINUTES)
            lastPingTime?.let {
                if (kronoJob.startTime.milliseconds.inWholeMinutes == currentMinute/* && now.toLocalDateTime(TimeZone.UTC) > it*/) coroutineScope.launch {
                    handleJob(job)
                }
            }
        }
    }

    /**
     * Get The data about a job,
     * @return A json string of the job
     */
    suspend fun checkJob(jobId: String): String? {
        return kacheController.get(id = jobId, collection, serializer = KronoJob.serializer(), cacheExpiry) {
            find(Filters.eq("_id", jobId)).firstOrNull()
        }?.let {
            Json.encodeToString(it)
        }
    }

    /**
     * Returns all scheduled Jobs
     */
    suspend fun allJobs(): List<KronoJob> {
        return kacheController.getAll(
            collection, serializer = KronoJob.serializer(), expire = cacheExpiry
        ) {
            find().toList()
        }
    }

    /**
     * Returns all scheduled Jobs with jobName
     */
    suspend fun allJobs(name: String): List<KronoJob> {
        return kacheController.getAll(
            collection, serializer = KronoJob.serializer(), expire = cacheExpiry
        ) {
            find(Filters.eq(KronoJob::jobName.name, name)).toList()
        }.filter { it.jobName == name }
    }

//    private fun jobNameCacheKey(name: String) = "${collection.cacheKey()}:$name"

    private fun <T : Model> MongoCollection<T>.cacheKey() = "${namespace.databaseName}:${namespace.collectionName}"
}
