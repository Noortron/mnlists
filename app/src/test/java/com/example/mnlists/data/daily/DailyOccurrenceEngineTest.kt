package com.example.mnlists.data.daily

import com.example.mnlists.data.local.entity.Daily
import com.example.mnlists.data.model.RepeatType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure unit tests for the Daily recurrence math (no Android/Room). */
class DailyOccurrenceEngineTest {

    private fun day(iso: String): Long = LocalDate.parse(iso).toEpochDay()

    private fun labels(epochDays: List<Long>): List<String> =
        epochDays.map { LocalDate.ofEpochDay(it).toString() }

    private fun daily(
        start: String,
        repeat: RepeatType = RepeatType.DAILY,
        everyN: Int = 1,
        weekdays: Set<DayOfWeek> = emptySet(),
    ) = Daily(
        id = 1L,
        title = "Test daily",
        startDate = day(start),
        repeatType = repeat,
        everyN = everyN,
        weekdays = weekdays,
    )

    @Test
    fun dailyGeneratesEveryDayFromItsStartDate() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2026-11-01"),
            fromDate = day("2026-11-01"),
            toDate = day("2026-11-05"),
        )

        assertEquals(
            listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05"),
            labels(dates),
        )
    }

    @Test
    fun dailyNeverGeneratesBeforeTheStartDate() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2026-11-01"),
            fromDate = day("2026-10-28"),
            toDate = day("2026-11-02"),
        )

        assertEquals(listOf("2026-11-01", "2026-11-02"), labels(dates))
    }

    @Test
    fun everyNDailyGeneratesOnlyMatchingDays() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2026-11-01", everyN = 3),
            fromDate = day("2026-11-01"),
            toDate = day("2026-11-11"),
        )

        assertEquals(
            listOf("2026-11-01", "2026-11-04", "2026-11-07", "2026-11-10"),
            labels(dates),
        )
    }

    @Test
    fun weeklyGeneratesOnlyThePickedWeekdays() {
        // 2026-11-01 is a Sunday; the picked days are Mon/Wed/Fri.
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(
                start = "2026-11-01",
                repeat = RepeatType.WEEKLY,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
            fromDate = day("2026-11-01"),
            toDate = day("2026-11-15"),
        )

        assertEquals(
            listOf(
                "2026-11-02", "2026-11-04", "2026-11-06",
                "2026-11-09", "2026-11-11", "2026-11-13",
            ),
            labels(dates),
        )
    }

    @Test
    fun weeklyEveryNGeneratesOnMatchingWeeksOnly() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(
                start = "2026-11-02",
                repeat = RepeatType.WEEKLY,
                everyN = 2,
                weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            ),
            fromDate = day("2026-11-02"),
            toDate = day("2026-11-23"),
        )

        assertEquals(
            listOf("2026-11-02", "2026-11-05", "2026-11-16", "2026-11-19"),
            labels(dates),
        )
    }

    @Test
    fun weeklyWithoutPickedDaysUsesTheStartDateWeekday() {
        // 2026-11-04 is a Wednesday.
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2026-11-04", repeat = RepeatType.WEEKLY),
            fromDate = day("2026-11-01"),
            toDate = day("2026-11-18"),
        )

        assertEquals(listOf("2026-11-04", "2026-11-11", "2026-11-18"), labels(dates))
    }

    @Test
    fun monthlyUsesTheStartDayAndClampsShortMonths() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2026-01-31", repeat = RepeatType.MONTHLY),
            fromDate = day("2026-01-01"),
            toDate = day("2026-04-30"),
        )

        assertEquals(
            listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30"),
            labels(dates),
        )
    }

    @Test
    fun monthlyEveryNGeneratesOnMatchingMonthsOnly() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2026-01-15", repeat = RepeatType.MONTHLY, everyN = 2),
            fromDate = day("2026-01-01"),
            toDate = day("2026-07-31"),
        )

        assertEquals(
            listOf("2026-01-15", "2026-03-15", "2026-05-15", "2026-07-15"),
            labels(dates),
        )
    }

    @Test
    fun datesToGenerateBackfillsOnlyDatesAfterTheLastGeneratedOne() {
        val series = daily(start = "2026-11-01")

        assertEquals(
            listOf("2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06"),
            labels(DailyOccurrenceEngine.datesToGenerate(series, lastGeneratedDate = null, today = day("2026-11-06"))),
        )
        assertEquals(
            listOf("2026-11-04", "2026-11-05", "2026-11-06"),
            labels(
                DailyOccurrenceEngine.datesToGenerate(
                    series,
                    lastGeneratedDate = day("2026-11-03"),
                    today = day("2026-11-06"),
                ),
            ),
        )
        assertTrue(
            DailyOccurrenceEngine.datesToGenerate(
                series,
                lastGeneratedDate = day("2026-11-06"),
                today = day("2026-11-06"),
            ).isEmpty(),
        )
    }

    @Test
    fun futureStartAndBackwardsClockGenerateNothing() {
        val future = daily(start = "2026-11-10")
        assertTrue(DailyOccurrenceEngine.datesToGenerate(future, null, day("2026-11-06")).isEmpty())

        val existing = daily(start = "2026-11-01")
        assertTrue(
            DailyOccurrenceEngine.datesToGenerate(
                existing,
                lastGeneratedDate = day("2026-11-06"),
                today = day("2026-11-04"),
            ).isEmpty(),
        )
    }

    @Test
    fun invalidEveryNFallsBackToOne() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2026-11-01", everyN = 0),
            fromDate = day("2026-11-01"),
            toDate = day("2026-11-03"),
        )

        assertEquals(listOf("2026-11-01", "2026-11-02", "2026-11-03"), labels(dates))
    }

    @Test
    fun catchUpIsCappedForVeryOldSeries() {
        val dates = DailyOccurrenceEngine.scheduledDates(
            daily = daily(start = "2020-01-01"),
            fromDate = day("2020-01-01"),
            toDate = day("2026-11-06"),
        )

        assertEquals(DailyOccurrenceEngine.MAX_CATCH_UP_DATES, dates.size)
        assertEquals(day("2020-01-01"), dates.first())
    }
}
