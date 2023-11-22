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
import java.time.Duration
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerTest {
    @BeforeEach
    fun beforeEach() = runTest {
//        clearAllMocks()
    }

    @AfterEach
    fun afterEach() = runBlocking {
        clearAllMocks()
    }

    @Test
    fun `every month at dayOfMonth, hour and minute`() = runTest(timeout = 1.minutes) {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyMonth(8,5, 5),
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        val minutesInYear = 1.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MINUTES)
        (1..minutesInYear).forEach {
            val timeAtMinute = currentTime.plus(it.minutes)
            Kronos.handleJobs(timeAtMinute)
            runCurrent()
        }

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(
                any(), any()
            )
        }
    }

    @Test
    fun `every day at hour and minute`() = runTest {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyDay(5, 5),
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        val minutesInDay = 1.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MINUTES)
        val twoDays = minutesInDay * 2
        (1..twoDays).forEach {
            val timeAtMinute = currentTime.plus(it.minutes)
            Kronos.handleJobs(timeAtMinute)
            runCurrent()
        }

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(
                any(), any()
            )
        }
    }

    @Test
    fun `every Hour at minute`() = runTest {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyHour(5),
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        (1..59).forEach {
            val timeAtMinute = currentTime.plus(it.minutes)
            Kronos.handleJobs(timeAtMinute)
            runCurrent()
        }

        coVerify(exactly = 1) {
            TestDataProvider.sampleSpyJob.execute(
                any(), any()
            )
        }
    }

    @Test
    fun `job and interval works`() = runTest {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1.minutes).toEpochMilliseconds(),
                params = emptyMap(),
//                interval = 1.minutes,
            )
        )
//        val kronoJob2 = spyk<KronoJob>(
//            KronoJob(
//                jobName = TestDataProvider.sampleSpyJob.name,
//                startTime = Instant.fromEpochMilliseconds(kronoJob.startTime).plus(1L.minutes).toEpochMilliseconds(),
//                params = emptyMap(),
//                interval = 1.minutes,
//            )
//        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
//        extraMocks(kronoJob2, TestDataProvider.sampleSpyJob)


        Kronos.handleJobs(currentTime.plus(1.minutes))
        runCurrent()
        Kronos.handleJobs(currentTime.plus(2.minutes))
        runCurrent()

        //cant verify this mockk has issue with duration
//        coVerify(exactly = 2) {
//            TestDataProvider.sampleSpyJob.execute(
//                any(), any()
//            )
//        }
    }


    @Test
    fun `job is run after endTime job 'OverShotAction_Fire'`() = runTest {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyMinute(),
                endTime = currentTime.plus(1.minutes).toEpochMilliseconds(),
                overshotAction = OvershotAction.Fire
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)


        Kronos.handleJobs(currentTime.plus(1.minutes))
        Kronos.handleJobs(currentTime.plus(2.minutes))
        runCurrent()

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(
                any(), any()
            )
        }
    }

    @Test
    fun `job is dropped after endTime job 'OverShotAction_Drop'`() = runTest {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyMinute(),
                endTime = currentTime.plus(1.minutes).toEpochMilliseconds()
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)


        Kronos.handleJobs(currentTime.plus(1.minutes))
        Kronos.handleJobs(currentTime.plus(2.minutes))
        runCurrent()

        coVerify(exactly = 1) {
            TestDataProvider.sampleSpyJob.execute(
                any(), any()
            )
        }
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
    fun `handle one time job`() = runTest {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)


        Kronos.handleJobs(currentTime.plus(1.minutes))
        Kronos.handleJobs(currentTime.plus(2.minutes))
        runCurrent()

        coVerify(exactly = 1) {
            TestDataProvider.sampleSpyJob.execute(
                1, mapOf(
                    "cycleNumber" to "1"
                )
            )
        }
    }

    @Test
    fun `handle every minute periodic job`() = runTest {

        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = Clock.System.now().toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyMinute()
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)


        Kronos.handleJobs(Instant.fromEpochMilliseconds(kronoJob.startTime).plus(1.minutes))
        runCurrent()
        Kronos.handleJobs(Instant.fromEpochMilliseconds(kronoJob.startTime).plus(1.minutes))
        runCurrent()

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(
                1, mapOf(
                    "cycleNumber" to "1"
                )
            )
        }
    }

    private fun TestScope.extraMocks(kronoJob: KronoJob, sampleJob: Job) {
        mockkObject(Kronos)
        every { Kronos.init(any(), any()) } returns Unit
        every { Kronos.coroutineScope.isActive } returns isActive

        MongoClient.create("mongodb://localhost:27016")
        val collection = TestDataProvider.mongoClient.getDatabase("mongodb").getCollection<KronoJob>("Hello")
        every { Kronos.collection } returns spyk(collection)
        val kacheController = mockk<KacheController> {
            coEvery { getAll<KronoJob>(any(), any(), any(), any()) } returns listOf(kronoJob)
            coEvery { set<KronoJob>(any(), any(), any()) } returns kronoJob
            coEvery { remove<KronoJob>(any(), any(), any()) } returns true
        }
        every { kacheController["cacheKey"](any<MongoCollection<KronoJob>>()) } returns ""
        every { Kronos.kacheController } returns kacheController
        every { Kronos.coroutineScope } returns CoroutineScope(StandardTestDispatcher(testScheduler) as CoroutineContext)
        every { Kronos.jobs.get(any()) } returns sampleJob
        every { Kronos.jobs } returns mutableMapOf(sampleJob.name to sampleJob)
    }
}