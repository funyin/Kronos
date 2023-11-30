package kronos

import com.funyinkash.kachecontroller.KacheController
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
object Kronos {
    internal val jobs: MutableMap<String, Job> = mutableMapOf()
    internal lateinit var mongoClient: MongoClient

    internal val collection by lazy { mongoClient.getDatabase(jobsDbName).getCollection<KronoJob>("jobs") }
    internal lateinit var coroutineScope: CoroutineScope

    internal lateinit var redisConnection: StatefulRedisConnection<String, String>
    lateinit var jobsDbName: String

    internal val mongoClientInitialized
        get() = ::mongoClient.isInitialized
    internal val coroutineScopeInitialized
        get() = ::coroutineScope.isInitialized
    internal val redisConnectionInitialized
        get() = ::redisConnection.isInitialized

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    internal val kacheController: KacheController by lazy {
        KacheController(client = redisConnection.coroutines())
    }

    /**
     * @throws IllegalStateException on attempting to initialize a second time
     */
    fun init(
        mongoClient: MongoClient,
        redisConnection: StatefulRedisConnection<String, String>,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
        jobsDbName: String = "jobsDb",
    ) {
        if (this::coroutineScope.isInitialized)
            throw IllegalStateException("Kronos already initialized")
        this.jobsDbName = jobsDbName
        this.mongoClient = mongoClient
        this.redisConnection = redisConnection
        coroutineScope = CoroutineScope(dispatcher)
        //runner is not started for test dispatchers
        //so I can control the curren time passed to the runner
        if (dispatcher in listOf(Dispatchers.IO, Dispatchers.Main, Dispatchers.Unconfined, Dispatchers.Default)) {
            coroutineScope.launch {
                runner()
            }
        }
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
            deleteOne(Filters.eq("_id", id)).wasAcknowledged()
        }
    }

    /**
     * Drop all jobs by [Job.name]
     */
    suspend fun dropJob(name: String): Boolean {
        val find: () -> FindFlow<Map<*, *>> = {
            collection.find(Filters.eq(KronoJob::jobName.name, name), resultClass = Map::class.java)
        }
        val jobs = find().projection(Projections.include("_id")).toList()
//        collection.deleteMany(Filters.eq(KronoJob::jobName.name, name))
        //Doing this instead of dropping the documents so that the cache can be cleared as well
        for (item in jobs) {
            val jobId: String = item["_id"].toString()
            dropJobId(jobId)
        }
        return find().count() == 0
    }

    suspend fun dropAll(): Boolean {
        return kacheController.removeAll(collection) {
            collection.deleteMany(Filters.empty()).deletedCount > 0
        }
    }

    internal suspend fun addJob(kronoJob: KronoJob): String? {
        return kacheController.set(collection, serializer = KronoJob.serializer()) {
            if (insertOne(kronoJob).wasAcknowledged())
                kronoJob
            else null
        }?.id
    }

    /**
     * Get The data about a job,
     * @return A json string of the job
     */
    suspend fun checkJob(jobId: String): String? {
        return kacheController.get(id = jobId, collection, serializer = KronoJob.serializer()) {
            find(Filters.eq("_id", jobId)).firstOrNull()
        }?.let {
            Json.encodeToString(it)
        }
    }
}
