package kronos

import KacheController
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Projections
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.FindFlow
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.sun.org.apache.xpath.internal.operations.Bool
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.*
import kotlinx.serialization.Serializable
import kronos.Kronos.coroutineScope

@Serializable
object Kronos {
    internal val jobs: MutableMap<String, Job> = mutableMapOf()
    private lateinit var mongoClient: MongoClient
    internal val collection by lazy { mongoClient.getDatabase("jobsDb").getCollection<KronoJob>("jobs") }
    internal lateinit var coroutineScope: CoroutineScope
    private lateinit var redisConnection: StatefulRedisConnection<String, String>

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    internal val kacheController by lazy {
        KacheController(
            cacheEnabled = { true },
            client = redisConnection.coroutines()
        )
    }

    fun init(mongoClient: MongoClient, redisConnection: StatefulRedisConnection<String, String>) {
        if (this::coroutineScope.isInitialized)
            throw IllegalStateException("Kronos already initialized")
        Kronos.mongoClient = mongoClient
        Kronos.redisConnection = redisConnection
        coroutineScope = CoroutineScope(Dispatchers.IO)
        coroutineScope.launch {
            runner()
        }
    }

    fun register(job: Job) {
        if (jobs.containsKey(job.name)) throw IllegalStateException("Job with name: ${job.name}")
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
        for (item in jobs) {
            val jobId: String = item["_id"].toString()
            dropJobId(jobId)
        }
        return find().count() == 0
    }

    internal suspend fun addJob(kronoJob: KronoJob): String? {
        return coroutineScope.async {
            kacheController.set(collection, serializer = KronoJob.serializer()) {
                if (insertOne(kronoJob).wasAcknowledged())
                    kronoJob
                else null
            }
        }.await()?.id
    }
}
