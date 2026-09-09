package kronos

import kotlinx.datetime.*
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Computes the smallest UTC instant >= [from] that satisfies [periodic].
 *
 * Uses calendar-correct arithmetic (not fixed-duration offsets) so that month/year
 * patterns land on the correct day even across months of differing lengths and leap years.
 * A day/month combination that doesn't exist (e.g. dayOfMonth=31 in April, or Feb 29
 * on a non-leap year) is skipped until it next occurs.
 */
internal fun alignToPeriodic(from: Instant, periodic: Periodic): Instant {
    val fromDateTime = from.toLocalDateTime(TimeZone.UTC)
    return when (periodic.every) {
        Periodic.Every.minute -> alignMinute(from, fromDateTime)
        Periodic.Every.hour -> alignHour(from, fromDateTime, periodic.minute!!)
        Periodic.Every.day -> alignDay(from, fromDateTime, periodic.hour!!, periodic.minute!!)
        Periodic.Every.week -> alignWeek(from, fromDateTime, periodic.dayOfWeek!!, periodic.hour!!, periodic.minute!!)
        Periodic.Every.month -> alignMonth(from, fromDateTime, periodic.dayOfMonth!!, periodic.hour!!, periodic.minute!!)
        Periodic.Every.year -> alignYear(
            from,
            fromDateTime,
            periodic.month!!,
            periodic.dayOfMonth!!,
            periodic.hour!!,
            periodic.minute!!
        )
    }
}

private fun candidateAt(date: LocalDate, hour: Int, minute: Int): Instant =
    LocalDateTime(date, LocalTime(hour, minute)).toInstant(TimeZone.UTC)

private fun alignMinute(from: Instant, fromDateTime: LocalDateTime): Instant {
    val truncated = LocalDateTime(fromDateTime.date, LocalTime(fromDateTime.hour, fromDateTime.minute))
        .toInstant(TimeZone.UTC)
    return if (truncated >= from) truncated else truncated.plus(1.minutes)
}

private fun alignHour(from: Instant, fromDateTime: LocalDateTime, minute: Int): Instant {
    val candidate = candidateAt(fromDateTime.date, fromDateTime.hour, minute)
    return if (candidate >= from) candidate else candidate.plus(1.hours)
}

private fun alignDay(from: Instant, fromDateTime: LocalDateTime, hour: Int, minute: Int): Instant {
    val candidate = candidateAt(fromDateTime.date, hour, minute)
    if (candidate >= from) return candidate
    return candidateAt(fromDateTime.date.plus(1, DateTimeUnit.DAY), hour, minute)
}

private fun alignWeek(
    from: Instant,
    fromDateTime: LocalDateTime,
    dayOfWeek: DayOfWeek,
    hour: Int,
    minute: Int,
): Instant {
    val daysUntil = (dayOfWeek.isoDayNumber - fromDateTime.date.dayOfWeek.isoDayNumber + 7) % 7
    val candidateDate = fromDateTime.date.plus(daysUntil, DateTimeUnit.DAY)
    val candidate = candidateAt(candidateDate, hour, minute)
    if (candidate >= from) return candidate
    return candidateAt(candidateDate.plus(7, DateTimeUnit.DAY), hour, minute)
}

private fun alignMonth(
    from: Instant,
    fromDateTime: LocalDateTime,
    dayOfMonth: Int,
    hour: Int,
    minute: Int,
): Instant {
    var year = fromDateTime.date.year
    var month = fromDateTime.date.monthNumber
    repeat(MAX_MONTH_LOOKAHEAD) {
        val date = tryLocalDate(year, month, dayOfMonth)
        if (date != null) {
            val candidate = candidateAt(date, hour, minute)
            if (candidate >= from) return candidate
        }
        month += 1
        if (month > 12) {
            month = 1
            year += 1
        }
    }
    throw IllegalStateException("Could not find a valid occurrence for dayOfMonth=$dayOfMonth within $MAX_MONTH_LOOKAHEAD months")
}

private fun alignYear(
    from: Instant,
    fromDateTime: LocalDateTime,
    month: Month,
    dayOfMonth: Int,
    hour: Int,
    minute: Int,
): Instant {
    var year = fromDateTime.date.year
    repeat(MAX_YEAR_LOOKAHEAD) {
        val date = tryLocalDate(year, month.number, dayOfMonth)
        if (date != null) {
            val candidate = candidateAt(date, hour, minute)
            if (candidate >= from) return candidate
        }
        year += 1
    }
    throw IllegalStateException("Could not find a valid occurrence for month=$month dayOfMonth=$dayOfMonth within $MAX_YEAR_LOOKAHEAD years")
}

private fun tryLocalDate(year: Int, month: Int, dayOfMonth: Int): LocalDate? = try {
    LocalDate(year, month, dayOfMonth)
} catch (e: IllegalArgumentException) {
    null
}

private const val MAX_MONTH_LOOKAHEAD = 60
private const val MAX_YEAR_LOOKAHEAD = 40
