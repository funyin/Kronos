@file:OptIn(ExperimentalCoroutinesApi::class)

package kronos

import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
import kotlin.time.Duration.Companion.seconds


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
            assert(Kronos.storeInitialized)
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
            assertEquals(Kronos.jobs, mutableMapOf(sampleJob.name to sampleJob))
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
            val spyJob = spyk(sampleJob)
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
            every { jobType1.challengeRun(any(), any()) } returns false
            every { jobType1.onFail(any(), any(), any()) } just runs
            val jobType2 = mockk<Job>()
            every { jobType2.name } returns "type2"
            every { jobType2.retries } returns 0
            every { jobType2.onDrop(any(), any()) } returns Unit
            every { jobType2.challengeRun(any(), any()) } returns false
            every { jobType2.onFail(any(), any(), any()) } just runs
            Kronos.register(jobType1)
            Kronos.register(jobType2)

            val job1Id = Kronos.schedule(jobType1.name, delay = 1.minutes, params = emptyMap())
            val job2Id = Kronos.schedule(jobType2.name, delay = 1.minutes, params = emptyMap())
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
            assert(Kronos.allJobs(sampleJob.name).isEmpty())
            assert(Kronos.allJobs().isEmpty())
        }

        @Test
        fun allJobs() = runTest {
            registerSampleJob()
            val jobs = listOf(
                scheduleSampleJob(),
                scheduleSampleJob(),
                scheduleSampleJob(),
            )
            assert(Kronos.allJobs(sampleJob.name).size == jobs.size)
        }

        @Test
        fun failedJob() = runTest {
            val spyJob = spyk<Job>(sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers { false }
            registerSampleJob(spyJob)
            scheduleSampleJob()
            Kronos.handleJobs(Clock.System.now().plus(1.minutes))
            verify(atLeast = 1) { spyJob.onFail(any(), any(), any()) }
        }

        @Test
        fun `onFail is called after retries are exhausted not before`() = runTest {
            val callOrder = mutableListOf<String>()
            val spyJob = spyk<Job>(sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers {
                callOrder += "execute"
                false
            }
            every { spyJob.retries } returns 2
            every { spyJob.onFail(any(), any(), any()) } answers { callOrder += "onFail" }
            every { spyJob.onRetryFail(any(), any(), any(), any()) } answers { callOrder += "onRetryFail" }
            registerSampleJob(spyJob)
            scheduleSampleJob()
            Kronos.handleJobs(Clock.System.now().plus(1.minutes))

            // onFail must come after all retries, not before them
            assert(callOrder.last() == "onFail") { "onFail should be last but got: $callOrder" }
            assert(callOrder.count { it == "onRetryFail" } == 2) { "Expected 2 onRetryFail calls but got: $callOrder" }
            assert(callOrder.indexOf("onFail") > callOrder.lastIndexOf("onRetryFail")) {
                "onFail must come after last onRetryFail but got: $callOrder"
            }
        }

        @Test
        fun `retry failed jobs`() = runTest {
            val spyJob = spyk<Job>(sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers { false }
            every { spyJob.retries } returns 2
            registerSampleJob(spyJob)
            scheduleSampleJob()
            Kronos.handleJobs(Clock.System.now().plus(1.minutes))
            verify { spyJob.onFail(any(), any(), any()) }
            verify(exactly = 2) { spyJob.onRetryFail(any(), any(), any(), any()) }
        }

        @Test
        fun `onLasCycleDrop is not called when drop fails`() = runTest {
            val spyJob = spyk<Job>(sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers { true }
            registerSampleJob(spyJob)
            val jobId = scheduleSampleJob()!!

            // Delete from DB manually so dropJob returns false
            Kronos.dropJobId(jobId)

            Kronos.handleJobs(Clock.System.now().plus(1.minutes))

            // Job was already deleted so dropJob should return false -> onLasCycleDrop should NOT fire
            verify(exactly = 0) { spyJob.onLasCycleDrop(any(), any()) }
        }

        @Test
        fun `cycleNumber increments across repeated job cycles`() = runTest {
            val observedCycles = mutableListOf<Int>()
            val spyJob = spyk<Job>(sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers {
                val params = secondArg<Map<String, Any>>()
                observedCycles += (params["cycleNumber"] as String).toInt()
                true
            }
            registerSampleJob(spyJob)

            val jobId = Kronos.schedule(
                sampleJob.name,
                startTime = Clock.System.now().toEpochMilliseconds(),
                interval = 1.minutes,
                maxCycles = 3,
                params = emptyMap()
            )!!

            val base = Clock.System.now()
            Kronos.handleJobs(base)
            Kronos.handleJobs(base.plus(1.minutes))
            Kronos.handleJobs(base.plus(2.minutes))

            assertEquals(listOf(1, 2, 3), observedCycles)
        }

        @Test
        fun loadTest() = runTest(timeout = 60.seconds) {
            val spyJob = spyk<Job>(sampleJob)
            coEvery { spyJob.execute(any(), any()) } coAnswers { true }
            every { spyJob.retries } returns 1
            registerSampleJob(spyJob)
            val currentInstant = Clock.System.now()
            val jobCount = 10000
            repeat(jobCount) { index ->
                launch {
                    Kronos.schedule(
                        sampleJob.name,
                        startTime = currentInstant.toEpochMilliseconds(),
//                    delay = 1.toDuration(DurationUnit.MINUTES),
//                    interval = 1.minutes,
                        params = emptyMap()
                    )
                }
            }
            advanceUntilIdle()
            Kronos.handleJobs(currentInstant)
            coVerify(exactly = jobCount) { spyJob.execute(any(), any()) }
        }
    }


    @Nested
    inner class Schedule {

    }


}