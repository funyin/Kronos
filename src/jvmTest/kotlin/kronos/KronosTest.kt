@file:OptIn(ExperimentalCoroutinesApi::class)

package kronos

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.lettuce.core.RedisClient
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class KronosTest {


    val mongoClient: MongoClient = MongoClient.create("mongodb://localhost:27016")
    val redisClient: RedisClient = RedisClient.create("redis://127.0.0.1:6379")
    val sampleJob = object : Job {
        override val name: String
            get() = "sample-job"
    }


    @BeforeEach
    fun beforeEach() = runTest {
        initKronos()
    }

    @AfterEach
    fun afterEach() = runTest {
        Kronos.dropAll()
        Kronos.shutDown()
    }

    private fun TestScope.initKronos() {
        val connection = redisClient.connect()
        Kronos.init(
            mongoClient = mongoClient,
            redisConnection = connection,
            dispatcher = StandardTestDispatcher(testScheduler),
            jobsDbName = "testJobsDb"
        )
    }

    @Nested
    inner class Init {

        @Test
        fun `initialized necessary fields`() = runTest {
            assert(Kronos.coroutineScopeInitialized)
            assert(Kronos.redisConnectionInitialized)
            assert(Kronos.mongoClientInitialized)
        }

//        @Test
//        fun `assert Runner starts on Initialization`() = runTest {
//            initKronos()
//            assert(false)
//        }


        @Test
        fun `multiple initialization throws error`() = runTest {
            val exception = assertThrows<IllegalStateException> {
                initKronos()
            }
            assertEquals(exception.message, "Kronos already initialized")
        }
    }

    @Nested
    inner class TestJob {
        @Test
        fun `job registration`() = runTest {
            val job = object : Job {
                override val name: String
                    get() = "sample-job"
            }
            Kronos.register(job)
            assertEquals(Kronos.jobs, mutableMapOf<String, Job>(job.name to job))
        }

        @Test
        fun `throw error for duplicate job registration`() = runTest {
            registerSampleJob()
            val exception = assertThrows<IllegalStateException> { registerSampleJob() }
            assertEquals(exception.message, "Job with name: '${sampleJob.name}' already registered")
            assertEquals(Kronos.jobs, mutableMapOf<String, Job>(sampleJob.name to sampleJob))
        }

        @Test
        fun `check Job`() = runTest {
            backgroundScope.launch {
                registerSampleJob()

                assertEquals(Kronos.checkJob("0"), null)
                val jobId = scheduleSampleJob()
                Kronos.coroutineScope.cancel()
                val job = Kronos.checkJob(jobId = jobId!!)?.let {
                    Json.decodeFromString<KronoJob>(it)
                }
                assertEquals(job?.id, jobId)
            }
        }

        @Test
        fun `drop Job`() = runTest {
            registerSampleJob()
            backgroundScope.launch {
                val job1Id = scheduleSampleJob()
                val job2Id = scheduleSampleJob()
                assert(job1Id != null)
                assert(Kronos.dropJob(job1Id!!))
                assert(Kronos.checkJob(job1Id!!) == null)
                assert(job2Id != null)
                assert(Kronos.checkJob(job2Id!!) != null)

                assert(Kronos.dropJob(job2Id!!))
                assert(Kronos.checkJob(job2Id!!) != null)
            }
        }

        @Test
        fun `drop job by name`() = runTest {
            val jobType1 = mockk<Job>()
            every { jobType1.name } returns "type1"
            every { jobType1.retries } returns 0
            val jobType2 = mockk<Job>()
            every { jobType2.name } returns "type2"
            every { jobType2.retries } returns 0

            Kronos.register(jobType1)
            Kronos.register(jobType2)

            val job1Id = Kronos.schedule(jobType1.name, delay = 1.toDuration(DurationUnit.MINUTES), params = emptyMap())
            val job2Id = Kronos.schedule(jobType2.name, delay = 1.toDuration(DurationUnit.MINUTES), params = emptyMap())
            //both jobs are loaded
            assert(Kronos.checkJob(job1Id!!) != null)
            assert(Kronos.checkJob(job2Id!!) != null)

            assert(Kronos.dropJob(jobType1.name))
            //jobType 1 dropped
            assert(Kronos.checkJob(job1Id) == null)
            //jobType2 should still be there
            assert(Kronos.checkJob(job2Id) != null)

            assert(Kronos.dropJob(jobType2.name))
            //jobType 2 dropped
            assert(Kronos.checkJob(job2Id) == null)
        }

        @Test
        fun `drop all jobs`() = runTest {
            registerSampleJob()
            val jobs = listOf(
                scheduleSampleJob(),
                scheduleSampleJob(),
                scheduleSampleJob(),
                scheduleSampleJob(),
            )
            assertEquals(jobs.size, jobs.requireNoNulls().size)
            val dropped = Kronos.dropAll()
            dropped.toString()
            for (job in jobs.requireNoNulls()){
                val task = Kronos.checkJob(job)
                println(task)
                assert(task == null)
            }
        }
    }

    private fun registerSampleJob() {
        Kronos.register(sampleJob)
    }

    private suspend fun scheduleSampleJob(): String? = Kronos.schedule(
        sampleJob.name,
        delay = 1.toDuration(DurationUnit.MINUTES),
        interval = 1.toDuration(DurationUnit.MINUTES),
        params = emptyMap()
    )

    @Nested
    inner class Schedule {

    }


}