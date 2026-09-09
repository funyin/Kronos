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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * In-memory [KronosStore] that actually persists insert/delete/lock state, so tests can drive
 * multiple scheduling cycles (reschedule -> drop -> reschedule ...) the same way a real backend
 * would, instead of a static mock that always returns the same document.
 */
private class FakeKronosStore : KronosStore {
    val jobsById = mutableMapOf<String, KronoJob>()

    fun seed(job: KronoJob) {
        jobsById[job.id] = job
    }

    override suspend fun initialize() = Unit

    override suspend fun insert(job: KronoJob): KronoJob? {
        jobsById[job.id] = job
        return job
    }

    override suspend fun fetchDueJobs(nowMs: Long): List<KronoJob> =
        jobsById.values.filter { it.startTime <= nowMs && it.locks == 0 }

    override suspend fun findById(id: String): KronoJob? = jobsById[id]

    override suspend fun findAll(): List<KronoJob> = jobsById.values.toList()

    override suspend fun findByName(name: String): List<KronoJob> = jobsById.values.filter { it.jobName == name }

    override suspend fun countByName(name: String): Long = jobsById.values.count { it.jobName == name }.toLong()

    override suspend fun acquireLock(id: String): KronoJob? {
        val job = jobsById[id] ?: return null
        if (job.locks != 0) return null
        val locked = job.copy(locks = job.locks + 1)
        jobsById[id] = locked
        return locked
    }

    override suspend fun delete(id: String): KronoJob? = jobsById.remove(id)

    override suspend fun deleteByName(name: String): Boolean {
        jobsById.values.filter { it.jobName == name }.map { it.id }.forEach { jobsById.remove(it) }
        return true
    }

    override suspend fun deleteAll(): Boolean {
        jobsById.clear()
        return true
    }
}

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
        val periodic = Periodic.everyYear(month = 5, dayOfMonth = 8, hour = 5, minute = 5)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val firstFire = alignToPeriodic(Clock.System.now(), periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = firstFire.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Drop
            )
        )

        var current = firstFire
        repeat(2) {
            Kronos.handleJobs(current)
            runCurrent()
            current = Instant.fromEpochMilliseconds(store.jobsById.values.single().startTime)
        }

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(any(), any())
        }
    }

    @Test
    fun `every month at dayOfMonth, hour and minute`() = runTest(timeout = 20.seconds) {
        val periodic = Periodic.everyMonth(dayOfMonth = 8, hour = 5, minute = 5)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val start = LocalDateTime(
            date = LocalDate(year = 2024, month = Month.JANUARY, dayOfMonth = 1),
            time = LocalTime.fromSecondOfDay(1)
        ).toInstant(TimeZone.UTC)
        val firstFire = alignToPeriodic(start, periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = firstFire.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Drop
            )
        )

        var current = firstFire
        repeat(3) {
            Kronos.handleJobs(current)
            runCurrent()
            current = Instant.fromEpochMilliseconds(store.jobsById.values.single().startTime)
        }

        coVerify(exactly = 3) {
            TestDataProvider.sampleSpyJob.execute(any(), any())
        }
    }

    @Test
    fun `every week at weekday, hour and minute`() = runTest(timeout = 20.seconds) {
        val periodic = Periodic.everyWeek(dayOfWeek = 2, hour = 5, minute = 5)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val firstFire = alignToPeriodic(Clock.System.now(), periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = firstFire.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Drop
            )
        )

        var current = firstFire
        repeat(2) {
            Kronos.handleJobs(current)
            runCurrent()
            current = Instant.fromEpochMilliseconds(store.jobsById.values.single().startTime)
        }

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(any(), any())
        }
    }

    @Test
    fun `every day at hour and minute`() = runTest {
        val periodic = Periodic.everyDay(5, 5)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val firstFire = alignToPeriodic(Clock.System.now(), periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = firstFire.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Drop
            )
        )

        var current = firstFire
        repeat(2) {
            Kronos.handleJobs(current)
            runCurrent()
            current = Instant.fromEpochMilliseconds(store.jobsById.values.single().startTime)
        }

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(any(), any())
        }
    }

    @Test
    fun `every Hour at minute`() = runTest {
        val periodic = Periodic.everyHour(5)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val firstFire = alignToPeriodic(Clock.System.now(), periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = firstFire.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Drop
            )
        )

        var current = firstFire
        repeat(2) {
            Kronos.handleJobs(current)
            runCurrent()
            current = Instant.fromEpochMilliseconds(store.jobsById.values.single().startTime)
        }

        coVerify(exactly = 2) {
            TestDataProvider.sampleSpyJob.execute(any(), any())
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

        Kronos.handleJobs(Instant.fromEpochMilliseconds(kronoJob.startTime))
        runCurrent()
        Kronos.handleJobs(Instant.fromEpochMilliseconds(kronoJob.startTime))
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
    fun `every week fires on the correct day of week`() {
        // Jan 1 2024 = Monday. We target Friday (day 5).
        // With the old hardcoded-Monday bug the aligned occurrence would land on Jan 1 (Monday)
        // instead of Jan 5 (Friday).
        val from = LocalDateTime(
            date = LocalDate(year = 2024, month = kotlinx.datetime.Month.JANUARY, dayOfMonth = 1),
            time = LocalTime.fromSecondOfDay(1)
        ).toInstant(TimeZone.UTC)

        val periodic = Periodic.everyWeek(dayOfWeek = 5, hour = 5, minute = 5)
        val aligned = alignToPeriodic(from, periodic)
        val expected = LocalDateTime(
            date = LocalDate(year = 2024, month = kotlinx.datetime.Month.JANUARY, dayOfMonth = 5),
            time = LocalTime(5, 5)
        ).toInstant(TimeZone.UTC)

        assert(aligned == expected) { "Expected $expected but got $aligned" }
    }

    @Test
    fun `everyMonth dayOfMonth 31 skips months without a 31st day instead of drifting`() {
        // Just after Jan 31 07:00 - the next candidate month (Feb) has no 31st day, so this
        // should skip straight to March 31, not drift via a fixed 30-day add (which old
        // nextPeriodicTime() did, landing on Mar 2).
        val from = LocalDateTime(
            date = LocalDate(year = 2024, month = kotlinx.datetime.Month.JANUARY, dayOfMonth = 31),
            time = LocalTime(8, 0)
        ).toInstant(TimeZone.UTC)

        val periodic = Periodic.everyMonth(dayOfMonth = 31, hour = 7, minute = 0)
        val aligned = alignToPeriodic(from, periodic)
        val expected = LocalDateTime(
            date = LocalDate(year = 2024, month = kotlinx.datetime.Month.MARCH, dayOfMonth = 31),
            time = LocalTime(7, 0)
        ).toInstant(TimeZone.UTC)

        assert(aligned == expected) { "Expected $expected but got $aligned" }
    }

    @Test
    fun `everyYear Feb 29 only fires on leap years`() {
        // 2023 is not a leap year; the next Feb 29 is 2024.
        val from = LocalDateTime(
            date = LocalDate(year = 2023, month = kotlinx.datetime.Month.MARCH, dayOfMonth = 1),
            time = LocalTime(0, 0)
        ).toInstant(TimeZone.UTC)

        val periodic = Periodic.everyYear(month = 2, dayOfMonth = 29, hour = 0, minute = 0)
        val aligned = alignToPeriodic(from, periodic)
        val expected = LocalDateTime(
            date = LocalDate(year = 2024, month = kotlinx.datetime.Month.FEBRUARY, dayOfMonth = 29),
            time = LocalTime(0, 0)
        ).toInstant(TimeZone.UTC)

        assert(aligned == expected) { "Expected $expected but got $aligned" }
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

        Kronos.handleJobs(startTime)
        runCurrent()

        // Job still executes on its last cycle
        coVerify(exactly = 1) { TestDataProvider.sampleSpyJob.execute(any(), any()) }
        // But no new job is inserted
        assert(rescheduledJobs.isEmpty()) { "Expected no reschedule on last cycle but addJob was called with: $rescheduledJobs" }
    }

    // ── Overshoot handling for periodic jobs ────────────────────────────────

    @Test
    fun `periodic job with OvershotAction Fire catches up a missed occurrence exactly once`() = runTest {
        val periodic = Periodic.everyDay(7, 1)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val target = alignToPeriodic(Clock.System.now(), periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = target.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Fire
            )
        )

        // Process was down at the exact target minute; it only gets to check 2 hours later.
        val missedCheck = target.plus(2.hours)
        Kronos.handleJobs(missedCheck)
        runCurrent()

        coVerify(exactly = 1) { TestDataProvider.sampleSpyJob.execute(any(), any()) }

        // The missed occurrence's document is replaced by tomorrow's target.
        val rescheduled = store.jobsById.values.single()
        assert(rescheduled.startTime > target.toEpochMilliseconds()) {
            "Expected rescheduled startTime to move forward, was ${rescheduled.startTime}"
        }

        // A later poll (or a restart re-scanning due jobs) at the same instant must not re-fire it,
        // since the fired occurrence's document no longer exists.
        Kronos.handleJobs(missedCheck)
        runCurrent()
        coVerify(exactly = 1) { TestDataProvider.sampleSpyJob.execute(any(), any()) }
    }

    @Test
    fun `periodic job with OvershotAction Drop removes a missed occurrence without executing`() = runTest {
        val periodic = Periodic.everyDay(7, 1)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val target = alignToPeriodic(Clock.System.now(), periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = target.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Drop
            )
        )

        Kronos.handleJobs(target.plus(2.hours))
        runCurrent()

        coVerify(exactly = 0) { TestDataProvider.sampleSpyJob.execute(any(), any()) }
        assert(store.jobsById.isEmpty()) { "Expected the missed occurrence to be dropped" }
    }

    @Test
    fun `periodic job with OvershotAction Nothing leaves the missed occurrence pending`() = runTest {
        val periodic = Periodic.everyDay(7, 1)
        val store = FakeKronosStore()
        fakeMocks(store, TestDataProvider.sampleSpyJob)

        val target = alignToPeriodic(Clock.System.now(), periodic)
        store.seed(
            KronoJob(
                jobName = TestDataProvider.sampleSpyJob.name,
                startTime = target.toEpochMilliseconds(),
                params = emptyMap(),
                periodic = periodic,
                overshotAction = OvershotAction.Nothing
            )
        )

        Kronos.handleJobs(target.plus(2.hours))
        runCurrent()

        coVerify(exactly = 0) { TestDataProvider.sampleSpyJob.execute(any(), any()) }
        val pending = store.jobsById.values.single()
        assert(pending.startTime == target.toEpochMilliseconds()) {
            "Expected the missed occurrence to remain pending with its original target"
        }
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

    private fun TestScope.fakeMocks(store: KronosStore, sampleJob: Job) {
        mockkObject(Kronos)
        every { Kronos.init(any<KronosStore>(), any()) } returns Kronos
        every { Kronos.coroutineScope.isActive } returns isActive
        every { Kronos.store } returns store
        every { Kronos.coroutineScope } returns CoroutineScope(StandardTestDispatcher(testScheduler) as CoroutineContext)
        every { Kronos.jobs.get(any()) } returns sampleJob
        every { Kronos.jobs } returns mutableMapOf(sampleJob.name to sampleJob)
    }
}
