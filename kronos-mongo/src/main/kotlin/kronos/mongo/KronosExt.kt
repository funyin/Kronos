@file:JvmName("KronosExt")

package kronos.mongo

import com.funyinkash.kachecontroller.cache.RedisCacheClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kronos.Kronos
import java.time.Duration

/**
 * Convenience extension that creates a [MongoKronosStore] with Redis cache
 * and initializes Kronos with it.
 *
 * This preserves the old connection-string-based API for users who don't
 * need full backend-swappability.
 */
fun Kronos.init(
    mongoConnectionString: String,
    redisConnectionString: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    jobsDbName: String = "jobsDb",
    cacheExpiry: Duration? = null,
): Kronos {
    val store = MongoKronosStore(
        mongoConnectionString = mongoConnectionString,
        cache = RedisCacheClient(redisConnectionString),
        jobsDbName = jobsDbName,
        cacheExpiry = cacheExpiry,
        onAsyncWriteError = { e -> this.onError?.invoke(e); Unit },
    )
    return init(
        store = store,
        dispatcher = dispatcher,
    )
}
