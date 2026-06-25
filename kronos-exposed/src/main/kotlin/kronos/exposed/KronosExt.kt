@file:JvmName("KronosExposedExt")

package kronos.exposed

import com.funyinkash.kachecontroller.CacheClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kronos.Kronos
import java.time.Duration
import javax.sql.DataSource

fun Kronos.init(
    dataSource: DataSource,
    cache: CacheClient,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    cacheExpiry: Duration? = null,
): Kronos = init(
    store = ExposedKronosStore(
        dataSource = dataSource,
        cache = cache,
        cacheExpiry = cacheExpiry,
    ),
    dispatcher = dispatcher,
)
