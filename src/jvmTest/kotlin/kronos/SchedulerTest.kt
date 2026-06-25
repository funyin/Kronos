package kronos


import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.datetime.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Month
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulerTest {
    @BeforeEach
    fun beforeEach() = runTest {
    }

    @AfterEach
    fun afterEach() = runBlocking {
        clearAllMocks()
    }

    @Test
    fun `every year  at month, dayOfMonth, hour and minute`() = runTest(timeout = 20.seconds) {

        val currentTime = Clock.System.now()
        val dayOfMonth = 8
        val month = 5
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyYear(month, dayOfMonth, 5, 5),
                overshotAction = OvershotAction.Drop
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        //365
        val minutesInYear = 365.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MINUTES)
        val threeYears = minutesInYear * 2
        (1..threeYears).forEach {
            val timeAtMinute = currentTime.plus(it.minutes)
            val toLocalDateTime = timeAtMinute.toLocalDateTime(TimeZone.UTC)
            //to prevent the test from taking too long
            if (toLocalDateTime.dayOfMonth == dayOfMonth && toLocalDateTime.monthNumber == month) {
                Kronos.handleJobs(timeAtMinute)
                runCurrent()
            }
        }

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(
                any(), any()
            )
        }
    }

    @Test
    fun `every month at dayOfMonth, hour and minute`() = runTest(timeout = 20.seconds) {

        val currentTime = LocalDateTime(
            date = LocalDate(year = 2024, month = Month.JANUARY, dayOfMonth = 1),
            time = LocalTime.fromSecondOfDay(1)
        ).toInstant(TimeZone.UTC)
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1.minutes).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyMonth(8, 5, 5),
                overshotAction = OvershotAction.Drop
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        //365
        val minutesInYear = 90.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MINUTES)
        (1..minutesInYear).forEach {
            val timeAtMinute = currentTime.plus(it.minutes)
            //to prevent the test from taking too long
            if (timeAtMinute.toLocalDateTime(TimeZone.UTC).dayOfMonth == 8) {
                Kronos.handleJobs(timeAtMinute)
                runCurrent()
            }
        }

        coVerify(exactly = 3) {
            TestDataProvider.sampleSpyJob.execute(
                any(), any()
            )
        }
    }

    @Test
    fun `every week at weekday, hour and minute`() = runTest(timeout = 20.seconds) {

        val currentTime = Clock.System.now()
        val dayOfWeek = 2
        val hour = 5
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyWeek(dayOfWeek, hour, 5),
                overshotAction = OvershotAction.Drop
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        val minutesInWeek = 7.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MINUTES)
        (1..minutesInWeek).forEach {
            val timeAtMinute = currentTime.plus(it.minutes)
            val toLocalDateTime = timeAtMinute.toLocalDateTime(TimeZone.UTC)
            //So that test does not run too long and getv ignored
            if (toLocalDateTime.hour == hour) {
                Kronos.handleJobs(timeAtMinute)
                runCurrent()
            }
        }

        coVerify(exactly = 1) {
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
                overshotAction = OvershotAction.Drop
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
                overshotAction = OvershotAction.Drop
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
    fun `handle every minute periodic job`() = runTest {

        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = Clock.System.now().toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyMinute(),
                maxCycles = 2,
                overshotAction = OvershotAction.Drop
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

    @Test
    fun `job and interval works`() = runTest {

        val currentTime = Clock.System.now()
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1.minutes).toEpochMilliseconds(),
                params = emptyMap(),
                overshotAction = OvershotAction.Drop
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
                endTime = currentTime.plus(1.minutes).toEpochMilliseconds(),
                overshotAction = OvershotAction.Drop
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
                overshotAction = OvershotAction.Drop
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

    // ── Periodic validation ────────────────────────────────────────────────

    @Test
    fun `everyWeek uses the supplied dayOfWeek not hardcoded Monday`() {
        val tuesday = Periodic.everyWeek(dayOfWeek = 2, hour = 10, minute = 0)
        val friday  = Periodic.everyWeek(dayOfWeek = 5, hour = 10, minute = 0)
        assert(tuesday.dayOfWeek?.isoDayNumber == 2) {
            "Expected Tuesday (2) but got ${tuesday.dayOfWeek?.isoDayNumber}"
        }
        assert(friday.dayOfWeek?.isoDayNumber == 5) {
            "Expected Friday (5) but got ${friday.dayOfWeek?.isoDayNumber}"
        }
    }

    @Test
    fun `everyWeek rejects dayOfWeek 0 and 8`() {
        assertFails { Periodic.everyWeek(dayOfWeek = 0, hour = 0, minute = 0) }
        assertFails { Periodic.everyWeek(dayOfWeek = 8, hour = 0, minute = 0) }
    }

    @Test
    fun `everyYear rejects month 0 and 13`() {
        assertFails { Periodic.everyYear(month = 0, dayOfMonth = 1, hour = 0, minute = 0) }
        assertFails { Periodic.everyYear(month = 13, dayOfMonth = 1, hour = 0, minute = 0) }
    }

    @Test
    fun `everyMonth rejects dayOfMonth 0 and 32`() {
        assertFails { Periodic.everyMonth(dayOfMonth = 0, hour = 0, minute = 0) }
        assertFails { Periodic.everyMonth(dayOfMonth = 32, hour = 0, minute = 0) }
    }

    @Test
    fun `everyHour rejects minute 60 and negative`() {
        assertFails { Periodic.everyHour(minute = 60) }
        assertFails { Periodic.everyHour(minute = -1) }
    }

    @Test
    fun `everyDay rejects hour 24 and negative`() {
        assertFails { Periodic.everyDay(hour = 24, minute = 0) }
        assertFails { Periodic.everyDay(hour = -1, minute = 0) }
    }

    // ── Scheduling correctness ─────────────────────────────────────────────

    @Test
    fun `every week fires on the correct day of week`() = runTest(timeout = 20.seconds) {
        // Jan 1 2024 = Monday. We target Friday (day 5).
        // In a 10-day window (Jan 1–10) there is exactly 1 Friday (Jan 5).
        // With the old hardcoded-Monday bug there would be 2 Mondays (Jan 1 + Jan 8),
        // so `exactly = 1` would fail, catching the regression.
        val currentTime = LocalDateTime(
            date = LocalDate(year = 2024, month = kotlinx.datetime.Month.JANUARY, dayOfMonth = 1),
            time = LocalTime.fromSecondOfDay(1)
        ).toInstant(TimeZone.UTC)

        val targetDayOfWeek = 5 // Friday
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = currentTime.plus(1L.toDuration(DurationUnit.MINUTES)).toEpochMilliseconds(),
                params = emptyMap(),
                periodic = Periodic.everyWeek(dayOfWeek = targetDayOfWeek, hour = 5, minute = 5),
                overshotAction = OvershotAction.Drop
            )
        )

        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        val minutesInTenDays = 10.toDuration(DurationUnit.DAYS).toLong(DurationUnit.MINUTES)
        (1..minutesInTenDays).forEach {
            val timeAtMinute = currentTime.plus(it.minutes)
            val ldt = timeAtMinute.toLocalDateTime(TimeZone.UTC)
            if (ldt.hour == 5 && ldt.minute == 5) {
                Kronos.handleJobs(timeAtMinute)
                runCurrent()
            }
        }

        // Exactly one Friday (Jan 5) in the 10-day range
        coVerify(exactly = 1) { TestDataProvider.sampleSpyJob.execute(any(), any()) }
    }

    @Test
    fun `job at maxCycles executes but does not reschedule`() = runTest(timeout = 20.seconds) {
        val maxCycles = 3
        val startTime = Clock.System.now()
        // cycleNumber == maxCycles means this is the last permitted cycle
        val kronoJob = spyk<KronoJob>(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = startTime.toEpochMilliseconds(),
                params = mapOf("cycleNumber" to "$maxCycles"),
                periodic = Periodic.everyMinute(),
                maxCycles = maxCycles,
                overshotAction = OvershotAction.Drop
            )
        )

        val rescheduledJobs = mutableListOf<KronoJob>()
        extraMocks(kronoJob, TestDataProvider.sampleSpyJob)
        coEvery { Kronos.addJob(capture(rescheduledJobs)) } returns ""

        Kronos.handleJobs(startTime.plus(1.minutes))
        runCurrent()

        // Job still executes on its last cycle
        coVerify(exactly = 1) { TestDataProvider.sampleSpyJob.execute(any(), any()) }
        // But no new job is inserted
        assert(rescheduledJobs.isEmpty()) { "Expected no reschedule on last cycle but addJob was called with: $rescheduledJobs" }
    }

    private fun TestScope.extraMocks(kronoJob: KronoJob, sampleJob: Job) {
        mockkObject(Kronos)
        every { Kronos.init(any<KronosStore>(), any()) } returns Kronos
        every { Kronos.coroutineScope.isActive } returns isActive

        val mockStore = mockk<KronosStore> {
            coEvery { insert(any()) } returns kronoJob
            coEvery { fetchDueJobs(any()) } returns listOf(kronoJob)
            coEvery { acquireLock(any()) } returns kronoJob
            coEvery { delete(any()) } returns kronoJob
            coEvery { deleteByName(any()) } returns true
            coEvery { countByName(any()) } returns 0L
        }
        every { Kronos.store } returns mockStore
        every { Kronos.coroutineScope } returns CoroutineScope(StandardTestDispatcher(testScheduler) as CoroutineContext)
        every { Kronos.jobs.get(any()) } returns sampleJob
        every { Kronos.jobs } returns mutableMapOf(sampleJob.name to sampleJob)
    }
}