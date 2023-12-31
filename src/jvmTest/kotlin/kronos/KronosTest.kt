@file:OptIn(ExperimentalCoroutinesApi::class)

package kronos

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kronos.TestDataProvider.initKronos
import kronos.TestDataProvider.registerSampleJob
import kronos.TestDataProvider.sampleJob
import kronos.TestDataProvider.scheduleSampleJob
import org.junit.jupiter.api.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit
import kotlin.time.toDuration


class KronosTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            TestDataProvider.startContainers()
        }
    }

    @BeforeEach
    fun beforeEach() = runTest {
        initKronos(StandardTestDispatcher(testScheduler))
    }

    @AfterEach
    fun afterEach() = runTest {
        Kronos.dropAll()
        Kronos.shutDown()
        clearAllMocks()
    }


    @Nested
    inner class Init {

        @Test
        fun `initialized necessary fields`() = runTest {
            assert(Kronos.coroutineScopeInitialized)
            assert(Kronos.redisConnectionInitialized)
            assert(Kronos.mongoClientInitialized)
        }


        @Test
        fun `multiple initialization throws error`() = runTest {
            val exception = assertThrows<IllegalStateException> {
                initKronos(StandardTestDispatcher(testScheduler))
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
            registerSampleJob()

            assertEquals(Kronos.checkJob("0"), null)
            val jobId = scheduleSampleJob()
            Kronos.coroutineScope.cancel()
            val job = Kronos.checkJob(jobId = jobId!!)?.let {
                Json.decodeFromString<KronoJob>(it)
            }
            assertEquals(job?.id, jobId)
        }

        @Test
        fun `drop Job`() = runTest {
            val spyJob = spyk(TestDataProvider.sampleJob)
            registerSampleJob(spyJob)
            val job1Id = scheduleSampleJob()
            val job2Id = scheduleSampleJob()
            assert(job1Id != null)
            assert(Kronos.dropJobId(job1Id!!))

            assert(Kronos.checkJob(job1Id) == null)

            assert(job2Id != null)
            assert(Kronos.checkJob(job2Id!!) != null)


            assert(Kronos.dropJobId(job2Id))
            assert(Kronos.checkJob(job2Id) == null)

            verify(exactly = 2) { spyJob.onDrop(any(), any()) }
        }

        @Test
        fun `drop job by name`() = runTest {
            val jobType1 = mockk<Job>()
            every { jobType1.name } returns "type1"
            every { jobType1.retries } returns 0
            every { jobType1.onDrop(any(), any()) } returns Unit
            val jobType2 = mockk<Job>()
            every { jobType2.name } returns "type2"
            every { jobType2.retries } returns 0
            every { jobType2.onDrop(any(), any()) } returns Unit
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
            for (job in jobs.requireNoNulls()) {
                val task = Kronos.checkJob(job)
                println(task)
                assert(task == null)
            }
        }

        @Test
        fun failedJob() = runTest {
            val spyJob = spyk<Job>(TestDataProvider.sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers { false }
            registerSampleJob(spyJob)
            scheduleSampleJob()
            Kronos.handleJobs(Clock.System.now().plus(1.minutes))
//            while (Kronos.coroutineScope.) {
//
//            }
            verify { spyJob.onFail(any(), any()) }
        }

        @Test
        fun RetryFailed() = runTest {
            val spyJob = spyk<Job>(TestDataProvider.sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers { false }
            every { spyJob.retries } returns 2
            registerSampleJob(spyJob)
            scheduleSampleJob()
            Kronos.handleJobs(Clock.System.now().plus(1.minutes))
//            while (Kronos.coroutineScope.) {
//
//            }
            verify { spyJob.onFail(any(), any()) }
            verify(exactly = 2) { spyJob.onRetryFail(any(), any(), any()) }
        }
    }


    @Nested
    inner class Schedule {

    }


}