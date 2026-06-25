package kronos.exposed

import com.funyinkash.kachecontroller.CacheClient
import com.funyinkash.kachecontroller.ExposedKacheController
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kronos.KronoJob
import kronos.KronosStore
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import javax.sql.DataSource
import java.time.Duration

class ExposedKronosStore(
    private val dataSource: DataSource,
    private val cache: CacheClient,
    private val cacheExpiry: Duration? = null,
) : KronosStore {

    private val db: Database = Database.connect(dataSource)

    private val kache = ExposedKacheController(cache = cache)

    override suspend fun initialize() {
        transaction(db) {
            SchemaUtils.createMissingTablesAndColumns(KronoJobsTable)
            exec("CREATE INDEX IF NOT EXISTS idx_kronos_due ON kronos_jobs(start_time, locks)")
        }
    }

    override suspend fun fetchDueJobs(nowMs: Long): List<KronoJob> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            KronoJobsTable
                .select { (KronoJobsTable.startTime lessEq nowMs) and (KronoJobsTable.locks eq 0) }
                .map { it.toKronoJob() }
        }

    override suspend fun insert(job: KronoJob): KronoJob? =
        kache.set(KronoJobsTable, job.id, KronoJob.serializer(), cacheExpiry ?: Duration.ZERO) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.insert { it.fromKronoJob(job) }
            }
            job
        }

    override suspend fun findById(id: String): KronoJob? =
        kache.get(id, KronoJobsTable, KronoJob.serializer(), cacheExpiry ?: Duration.ZERO) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.select { KronoJobsTable.id eq id }.firstOrNull()?.toKronoJob()
            }
        }

    override suspend fun findAll(): List<KronoJob> =
        kache.getAll(KronoJobsTable, KronoJob.serializer(), cacheExpiry ?: Duration.ZERO) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.selectAll().map { it.toKronoJob() }
            }
        }

    override suspend fun findByName(name: String): List<KronoJob> =
        kache.getVolatile("kronos_jobs:${name}", KronoJobsTable, ListSerializer(KronoJob.serializer())) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.select { KronoJobsTable.jobName eq name }.map { it.toKronoJob() }
            }
        }

    override suspend fun countByName(name: String): Long =
        newSuspendedTransaction(Dispatchers.IO, db) {
            KronoJobsTable.select { KronoJobsTable.jobName eq name }.count()
        }

    override suspend fun acquireLock(id: String): KronoJob? =
        kache.set(KronoJobsTable, id, KronoJob.serializer(), cacheExpiry ?: Duration.ZERO) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.update({ (KronoJobsTable.id eq id) and (KronoJobsTable.locks eq 0) }) {
                    with(SqlExpressionBuilder) {
                        it.update(KronoJobsTable.locks, KronoJobsTable.locks + 1)
                    }
                }
                KronoJobsTable.select { KronoJobsTable.id eq id }.firstOrNull()?.toKronoJob()
            }
        }

    override suspend fun delete(id: String): KronoJob? {
        val job = findById(id) ?: return null
        kache.remove(id, KronoJobsTable) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.deleteWhere { Op.build { KronoJobsTable.id.eq(id) } } > 0
            }
        }
        return job
    }

    override suspend fun deleteByName(name: String): Boolean =
        kache.removeAll(KronoJobsTable) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.deleteWhere { Op.build { KronoJobsTable.jobName.eq(name) } } > 0
            }
        }

    override suspend fun deleteAll(): Boolean =
        kache.removeAll(KronoJobsTable) {
            newSuspendedTransaction(Dispatchers.IO, db) {
                KronoJobsTable.deleteAll() > 0
            }
        }
}
