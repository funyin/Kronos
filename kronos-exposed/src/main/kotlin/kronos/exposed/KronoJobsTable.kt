package kronos.exposed

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kronos.KronoJob
import kronos.OvershotAction
import kronos.Periodic
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.statements.InsertStatement
import kotlin.time.Duration

object KronoJobsTable : Table("kronos_jobs") {
    val id = varchar("id", 36)
    val jobName = varchar("job_name", 255).index()
    val params = text("params")
    val startTime = long("start_time").index()
    val endTime = long("end_time").nullable()
    val interval = varchar("interval_ms", 50).nullable()
    val periodic = text("periodic").nullable()
    val maxCycles = integer("max_cycles").nullable()
    val retries = integer("retries").default(0)
    val createdAt = long("created_at")
    val originCreatedAt = text("origin_created_at")
    val locks = integer("locks").default(0)
    val overshotAction = varchar("overshot_action", 10)

    override val primaryKey = PrimaryKey(id)
}

fun ResultRow.toKronoJob() = KronoJob(
    id = this[KronoJobsTable.id],
    jobName = this[KronoJobsTable.jobName],
    params = Json.decodeFromString(this[KronoJobsTable.params]),
    startTime = this[KronoJobsTable.startTime],
    endTime = this[KronoJobsTable.endTime],
    interval = this[KronoJobsTable.interval]?.let { Duration.parseIsoString(it) },
    periodic = this[KronoJobsTable.periodic]?.let { Json.decodeFromString(it) },
    maxCycles = this[KronoJobsTable.maxCycles],
    retries = this[KronoJobsTable.retries],
    createdAt = this[KronoJobsTable.createdAt],
    originCreatedAt = LocalDateTime.parse(this[KronoJobsTable.originCreatedAt]),
    locks = this[KronoJobsTable.locks],
    overshotAction = OvershotAction.valueOf(this[KronoJobsTable.overshotAction]),
)

fun InsertStatement<Number>.fromKronoJob(job: KronoJob) {
    this[KronoJobsTable.id] = job.id
    this[KronoJobsTable.jobName] = job.jobName
    this[KronoJobsTable.params] = Json.encodeToString(job.params)
    this[KronoJobsTable.startTime] = job.startTime
    this[KronoJobsTable.endTime] = job.endTime
    this[KronoJobsTable.interval] = job.interval?.let(Duration::toIsoString)
    this[KronoJobsTable.periodic] = job.periodic?.let { Json.encodeToString(it) }
    this[KronoJobsTable.maxCycles] = job.maxCycles
    this[KronoJobsTable.retries] = job.retries
    this[KronoJobsTable.createdAt] = job.createdAt
    this[KronoJobsTable.originCreatedAt] = job.originCreatedAt.toString()
    this[KronoJobsTable.locks] = job.locks
    this[KronoJobsTable.overshotAction] = job.overshotAction.name
}
