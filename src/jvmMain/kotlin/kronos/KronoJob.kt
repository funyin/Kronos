package kronos

import Model
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
     * Active processes executing a job on this job
     */
    val locks: Int = 0,
    val overshotAction: OvershotAction = OvershotAction.Fire,
) : Model

enum class OvershotAction {
    Fire, Drop
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
            return Periodic().apply {
                this.minute = minute
                every = Every.hour
            }
        }

        fun everyDay(hour: Int, minute: Int): Periodic {
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
            return Periodic().apply {
                this.minute = minute
                this.hour = hour
                this.dayOfMonth = dayOfMonth
                this.month = Month(month)
                every = Every.year
            }
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