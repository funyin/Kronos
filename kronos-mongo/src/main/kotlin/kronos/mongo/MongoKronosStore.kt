package kronos.mongo

import com.funyinkash.kachecontroller.CacheClient
import com.funyinkash.kachecontroller.MongoKacheController
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.builtins.ListSerializer
import kronos.KronoJob
import kronos.KronosStore
import java.time.Duration

/**
 * [KronosStore] implementation backed by MongoDB (via KacheController) with an optional
 * Redis/SQLite/in-memory cache layer.
 *
 * @param mongoConnectionString MongoDB connection URI
 * @param cache cache backend — any [com.funyinkash.kachecontroller.CacheClient] implementation
 * @param jobsDbName name of the MongoDB database that holds the jobs collection (default `"jobsDb"`)
 * @param cacheExpiry TTL applied to all cached entries; `null` means no expiry
 * @param onAsyncWriteError callback invoked when an async cache write fails
 */
class MongoKronosStore(
    mongoConnectionString: String,
    private val cache: CacheClient,
    private val jobsDbName: String = "jobsDb",
    private val cacheExpiry: Duration? = null,
    private val onAsyncWriteError: ((Throwable) -> Unit) = {},
) : KronosStore {

    private val mongoClient = MongoClient.create(mongoConnectionString)
    private val collection = mongoClient.getDatabase(jobsDbName).getCollection<KronoJob>("jobs")
    private val asyncWriteScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val kache: MongoKacheController by lazy {
        MongoKacheController(
            cache = cache,
            asyncWriteScope = asyncWriteScope,
            onAsyncWriteError = onAsyncWriteError,
        )
    }

    override suspend fun initialize() {
        collection.createIndex(Indexes.ascending(KronoJob::startTime.name, KronoJob::locks.name))
    }

    override fun close() {
        asyncWriteScope.cancel()
        mongoClient.close()
    }

    override suspend fun insert(job: KronoJob): KronoJob? =
        kache.set(collection, serializer = KronoJob.serializer(), expire = cacheExpiry) {
            if (insertOne(job).wasAcknowledged()) job else null
        }

    override suspend fun fetchDueJobs(nowMs: Long): List<KronoJob> =
        collection.find(
            Filters.and(
                Filters.lte(KronoJob::startTime.name, nowMs),
                Filters.eq(KronoJob::locks.name, 0)
            )
        ).toList()

    override suspend fun findById(id: String): KronoJob? =
        kache.get(id, collection, serializer = KronoJob.serializer(), expire = cacheExpiry) {
            find(Filters.eq("_id", id)).firstOrNull()
        }

    override suspend fun findAll(): List<KronoJob> =
        kache.getAll(collection, serializer = KronoJob.serializer(), expire = cacheExpiry) {
            find().toList()
        }

    override suspend fun findByName(name: String): List<KronoJob> =
        kache.getVolatile(
            fieldName = "${collection.namespace.databaseName}:${collection.namespace.collectionName}:$name",
            collection = collection,
            serializer = ListSerializer(KronoJob.serializer()),
        ) {
            find(Filters.eq(KronoJob::jobName.name, name)).toList()
        }

    override suspend fun countByName(name: String): Long =
        collection.countDocuments(Filters.eq(KronoJob::jobName.name, name))

    override suspend fun acquireLock(id: String): KronoJob? =
        kache.set(collection, serializer = KronoJob.serializer(), expire = cacheExpiry) {
            findOneAndUpdate(
                Filters.and(Filters.eq("_id", id), Filters.eq(KronoJob::locks.name, 0)),
                Updates.inc(KronoJob::locks.name, 1),
            )
        }

    override suspend fun delete(id: String): KronoJob? {
        val deleted = collection.findOneAndDelete(Filters.eq("_id", id)) ?: return null
        kache.remove(id, collection = collection) { true }
        return deleted
    }

    override suspend fun deleteByName(name: String): Boolean =
        kache.removeAll(collection = collection) {
            deleteMany(Filters.eq(KronoJob::jobName.name, name)).wasAcknowledged()
        }

    override suspend fun deleteAll(): Boolean =
        kache.removeAll(collection = collection) {
            deleteMany(Filters.empty()).wasAcknowledged()
        }
}
