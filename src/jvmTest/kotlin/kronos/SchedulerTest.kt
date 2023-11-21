package kronos

import KacheController
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerTest {
    @BeforeEach
    fun beforeEach() = runTest {
//        initKronos(Dispatchers.IO)
    }

    @AfterEach
    fun afterEach() = runBlocking {
        clearAllMocks()
    }

    @Test
    fun `scheduling adds job`() = runTest {
        val sampleJob = mockk<Job>()
        val kronos = mockk<Kronos>()
        mockkStatic(Kronos::getValidJob)

        every { sampleJob.name } returns "sample-job"
        every { sampleJob.retries } returns 0


        coEvery { kronos.addJob(any()) } returns ""
        every { kronos.getValidJob(any()) } returns sampleJob

        kronos.schedule(sampleJob.name, params = emptyMap())

        coVerifyOrder {
            kronos.getValidJob(any())
            kronos.addJob(any())
        }

        kronos.schedulePeriodic(sampleJob.name + "-periodic", periodic = Periodic.everyMinute(), params = emptyMap())

        coVerifyOrder {
            kronos.getValidJob(any())
            nextPeriodicTime(any(), any())
            kronos.addJob(any())
        }
    }

    @Test
    fun `schedule periodic job`() = runTest(timeout = 1.minutes) {
        val sampleJob = mockk<Job>()
        every { sampleJob.name } returns "one-time-job"
        every { sampleJob.retries } returns 0
        every { sampleJob.challengeRun(any(), any()) } returns false
        every { sampleJob.periodicJobLoaded(any(), any()) } answers {}
        every { sampleJob.onDrop(any(), any()) } answers {}
        every { sampleJob.onFail(any(), any()) } answers {}
        every { sampleJob.onSuccess(any(), any()) } answers {}

        coEvery { sampleJob.execute(any(), any()) } coAnswers {
            println("Mello")
            true
        }

        mockkObject(Kronos)
        every { Kronos.init(any(), any()) } returns Unit
        every { Kronos.coroutineScope.isActive } returns isActive


        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = sampleJob.name,
                startTime = Clock.System.now().toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyMinute()
            )
        )
//        val jobCreatedAt = jobStartTime.toEpochMilliseconds()
//        every { kronoJob.jobName } returns sampleJob.name
//        every { kronoJob.startTime } returns jobStartTime.toEpochMilliseconds()
//        every { kronoJob.params } returns mockk()
//        every { kronoJob.periodic } returns Periodic.everyMinute()
//        every { kronoJob.createdAt } returns jobCreatedAt

//        val kacheController = mockk<KacheController>()
        MongoClient.create("mongodb://localhost:27016")
        val collection = TestDataProvider.mongoClient.getDatabase("mongodb").getCollection<KronoJob>("Hello")
        every { Kronos.collection } returns spyk(collection)
        val kacheController = mockk<KacheController> {
            coEvery { getAll<KronoJob>(any(), any(), any(), any()) } returns listOf(
                kronoJob
            )
            coEvery { set<KronoJob>(any(), any(), any()) } returns kronoJob
            coEvery { remove<KronoJob>(any(), any(), any()) } returns true
        }
        every { kacheController["cacheKey"](any<MongoCollection<KronoJob>>()) } returns ""
        every { Kronos.kacheController } returns kacheController
        every { Kronos.coroutineScope } returns CoroutineScope(StandardTestDispatcher(testScheduler) as CoroutineContext)
        every { Kronos.jobs.get(any()) } returns sampleJob
        every { Kronos.jobs } returns mutableMapOf(sampleJob.name to sampleJob)


        Kronos.handleJobs(Instant.fromEpochMilliseconds(kronoJob.startTime).plus(1.minutes))

        advanceUntilIdle()

        coVerify {
            sampleJob.execute(
                1, mapOf(
                    "cycleNumber" to "1"
                )
            )
        }
    }
}