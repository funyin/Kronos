package kronos

import com.funyinkash.kachecontroller.Model
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import java.time.Instant
import kotlin.time.Duration

@Serializable
data class KronoJob(
    @SerialName("_id")
    override val id: String = ObjectId().toHexString(),
    /**
     * This should be unique for every job
     */
    val jobName: String,
    val params: Map<String, String>,
    val startTime: Long,
    val endTime: Long? = null,
    val interval: Duration? = null,
    val periodic: Periodic? = null,
    val maxCycles: Int? = null,
    val retries: Int = 0,
    val createdAt: Long = Instant.now().toEpochMilli(),
    /**
     * The creationDate of the original Job.
     * Decided to use LocalDateTime just so it is readable for debugging
     * since no action would be performed on this
     */
    val originCreatedAt: LocalDateTime = Clock.System.now().toLocalDateTime(TimeZone.UTC),
    /**
     * Active processes executing a job on this job.
     * This is to prevent race conditions when using microservices
     */
    val locks: Int = 0,
    val overshotAction: OvershotAction = OvershotAction.Drop,
) : Model {
    val repeatedJob: Boolean
        get() = periodic != null || interval != null
}

/**
 * What do you want to happen when the job runner finds that the job start time is in the past.
 * This can happen when the job does no run at the start time because the system is down or was
 * not handled by the job runner
 */
enum class OvershotAction {
    /**
     * Job is executed immediately. when it is found past it's end time
     */
    Fire,

    /**
     * Job is Dropped immediately. when it is found past it's end time
     */
    Drop,

    /**
     * Nothing happens to the job and it kep in the database.
     * It is recommended not to use this since this will cause stale jobs to pile up in your db.
     * IT is best to drop or execute it for optimal performance.
     *
     * Only use this if you deliberately want to keep the records.
     */
    Nothing
}

@Serializable
class Periodic private constructor() {
    var dayOfWeek: DayOfWeek? = null
    var hour: Int? = null
    var minute: Int? = null
    var dayOfMonth: Int? = null
    var month: Month? = null
    var every: Every = Every.minute

    companion object {
        fun everyMinute(): Periodic {
            return Periodic().apply {
                every = Every.minute
            }
        }

        fun everyHour(minute: Int): Periodic {
            validateMinute(minute)
            return Periodic().apply {
                this.minute = minute
                every = Every.hour
            }
        }

        /**
         * 24 hour format 0-23
         */
        fun everyDay(hour: Int, minute: Int): Periodic {
            validateHour(hour)
            validateMinute(minute)
            return Periodic().apply {
                this.minute = minute
                this.hour = hour
                every = Every.day
            }
        }

        /**
         * @param dayOfWeek the week from 1(Monday) to 7(Sunday)
         */
        fun everyWeek(dayOfWeek: Int, hour: Int, minute: Int): Periodic {
            require(dayOfWeek <= 7)
            validateHour(hour)
            validateMinute(minute)
            return Periodic().apply {
                this.minute = minute
                this.hour = hour
                this.dayOfWeek = DayOfWeek(1)
                every = Every.week
            }
        }

        /**
         * @param dayOfMonth the day of the month
         */
        fun everyMonth(dayOfMonth: Int, hour: Int, minute: Int): Periodic {
            validateDatOfMonth(dayOfMonth)
            validateHour(hour)
            validateMinute(minute)
            return Periodic().apply {
                this.minute = minute
                this.hour = hour
                this.dayOfMonth = dayOfMonth
                every = Every.month
            }
        }

        /**
         * @param month month Number from 1(January) to 12(December)
         */
        fun everyYear(month: Int, dayOfMonth: Int, hour: Int, minute: Int): Periodic {
            require(month <= 12)
            validateDatOfMonth(dayOfMonth)
            validateHour(hour)
            validateMinute(minute)
            return Periodic().apply {
                this.minute = minute
                this.hour = hour
                this.dayOfMonth = dayOfMonth
                this.month = Month(month)
                every = Every.year
            }
        }

        private fun validateDatOfMonth(dayOfMonth: Int) {
            require(dayOfMonth in 0..31)
        }

        private fun validateHour(hour: Int) {
            require(hour in 0..23)
        }

        private fun validateMinute(minute: Int) {
            require(minute in 0..59)
        }
    }

    enum class Every {
        minute,
        hour,
        day,
        week,
        month,
        year,
    }
}