package kronos

/**
 * Storage abstraction used by Kronos. Implement this interface to add a new backend.
 *
 * The default implementation is `MongoKronosStore` from the `kronos-mongo` artifact.
 * Implementations are responsible for both persistence and any caching strategy they choose.
 */
interface KronosStore {

    /** Called once at startup — create indexes, run schema migrations, etc. */
    suspend fun initialize()

    /** Release any held resources (connections, scopes). Called on [Kronos.shutDown]. */
    fun close() = Unit

    /** Persist a new job. Returns the inserted job, or `null` if insertion failed. */
    suspend fun insert(job: KronoJob): KronoJob?

    /**
     * Return jobs eligible to run right now: `startTime <= nowMs` and `locks == 0`.
     * This query runs every minute — it must be efficient (index-backed).
     */
    suspend fun fetchDueJobs(nowMs: Long): List<KronoJob>

    /** Retrieve a single job by its ID, or `null` if not found. */
    suspend fun findById(id: String): KronoJob?

    /** Return all scheduled jobs. */
    suspend fun findAll(): List<KronoJob>

    /** Return all scheduled jobs with the given [name]. */
    suspend fun findByName(name: String): List<KronoJob>

    /** Return the count of jobs with the given [name]. */
    suspend fun countByName(name: String): Long

    /**
     * Atomically increment the `locks` counter on the job with this [id].
     * Returns the job or `null` if the document no longer exists.
     * A `null` return signals that execution should be aborted.
     */
    suspend fun acquireLock(id: String): KronoJob?

    /**
     * Delete the job with this [id].
     * Returns the deleted job (used for callback dispatch) or `null` if it did not exist.
     */
    suspend fun delete(id: String): KronoJob?

    /** Delete all jobs with the given [name]. Returns `true` if acknowledged. */
    suspend fun deleteByName(name: String): Boolean

    /** Delete all jobs. Returns `true` if acknowledged. */
    suspend fun deleteAll(): Boolean

}
