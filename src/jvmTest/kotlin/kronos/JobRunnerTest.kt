package kronos

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.spyk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.*
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit


class JobRunnerTest {
    companion object{
        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            TestDataProvider.startContainers()
        }
    }

    @BeforeEach
    fun initKronos() = runTest {
        TestDataProvider.initKronos(StandardTestDispatcher(testScheduler))
    }

    @AfterEach
    fun tearDown() {
        Kronos.shutDown()
    }

    @Test
    fun `test runner ticks every minute`() = runTest {
//        spyk(Kronos)
//        coEvery { Kronos.handleJobs(any()) }

    }


}