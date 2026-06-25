package kronos

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

object Kronos {
    internal val jobs: MutableMap<String, Job> = mutableMapOf()
    internal lateinit var store: KronosStore

    private val exceptionHandler = CoroutineExceptionHandler { _, e ->
        onError?.invoke(e)
    }
    internal lateinit var coroutineScope: CoroutineScope

    var onError: ((Throwable) -> Unit)? = null
    var lastPingTime: LocalDateTime? = null
        internal set

    internal val coroutineScopeInitialized
        get() = ::coroutineScope.isInitialized
    internal val storeInitialized
        get() = ::store.isInitialized

    /**
     * Initialize Kronos with a backend store. Call once at application startup.
     *
     * For MongoDB + Redis, use the `Kronos.init(mongoConnectionString, redisConnectionString)`
     * convenience extension from the `kronos-mongo` artifact instead.
     *
     * @throws IllegalStateException if called more than once without [shutDown] in between.
     */
    fun init(
        store: KronosStore,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): Kronos {
        if (this::coroutineScope.isInitialized) throw IllegalStateException("Kronos already initialized")

        this.store = store
        coroutineScope = CoroutineScope(dispatcher + exceptionHandler)

        coroutineScope.launch {
            store.initialize()
            //runner is not started for test dispatchers
            //so I can control the current time passed to the runner
            if (dispatcher in listOf(Dispatchers.IO, Dispatchers.Main, Dispatchers.Unconfined, Dispatchers.Default)) {
                runner()
            }
        }
        return this
    }

    internal fun shutDown() {
        coroutineScope.cancel()
        store.close()
        javaClass.getDeclaredField("coroutineScope").apply {
            isAccessible = true
            set(this@Kronos, null)
        }
        javaClass.getDeclaredField("store").apply {
            isAccessible = true
            set(this@Kronos, null)
        }
        jobs.clear()
    }

    /**
     * Register a [Job] implementation. Must be called before scheduling jobs with this name.
     * Registration is in-memory — call this on every application startup.
     *
     * @throws IllegalStateException if a job with the same [Job.name] is already registered.
     */
    fun register(job: Job) {
        if (jobs.containsKey(job.name)) throw IllegalStateException("Job with name: '${job.name}' already registered")
        jobs[job.name] = job
    }

    /**
     * Cancel and remove a single job by its ID. Triggers [Job.onDrop].
     * Returns `true` if the job was found and deleted.
     */
    suspend fun dropJobId(id: String): Boolean {
        val deleted = store.delete(id) ?: return false
        val count = store.countByName(deleted.jobName)
        jobs[deleted.jobName]?.onDrop(id, lastJob = count == 0L)
        return true
    }

    /**
     * Cancel and remove all scheduled jobs with the given [name]. Triggers [Job.onDrop] for each.
     * Returns `true` if the delete was acknowledged.
     */
    suspend fun dropJob(name: String): Boolean {
        val kronoJobs = store.findByName(name)
        return if (store.deleteByName(name)) {
            kronoJobs.forEachIndexed { index, kronoJob ->
                jobs[name]?.onDrop(kronoJob.id, lastJob = index == kronoJobs.lastIndex)
            }
            true
        } else false
    }

    suspend fun dropAll(): Boolean = jobs.keys.all { name -> dropJob(name) }

    internal suspend fun addJob(kronoJob: KronoJob): String? {
        val job = store.insert(kronoJob)
        return job?.id?.also { _ ->
            val now = Clock.System.now()
            val currentMinute = now.toEpochMilliseconds().milliseconds.toLong(DurationUnit.MINUTES)
            if (lastPingTime != null && kronoJob.startTime.milliseconds.inWholeMinutes == currentMinute) {
                coroutineScope.launch { handleJob(job) }
            }
        }
    }

    /**
     * Get The data about a job,
     * @return A json string of the job
     */
    suspend fun checkJob(jobId: String): String? {
        return store.findById(jobId)?.let {
            Json.encodeToString(it)
        }
    }

    /**
     * Returns all scheduled Jobs
     */
    suspend fun allJobs(): List<KronoJob> {
        return store.findAll()
    }

    /**
     * Returns all scheduled Jobs with jobName
     */
    suspend fun allJobs(name: String): List<KronoJob> {
        return store.findByName(name)
    }
}
