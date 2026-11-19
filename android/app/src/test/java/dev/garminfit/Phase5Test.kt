package dev.garminfit

import dev.garminfit.ui.bucketLabel
import dev.garminfit.ui.hoursSince
import dev.garminfit.ui.listIndexOfWeek
import dev.garminfit.ui.planWeekChange
import dev.garminfit.ui.predictionDelta
import dev.garminfit.ui.previousLabel
import dev.garminfit.ui.statusEvidence
import dev.garminfit.ui.switchKey
import dev.garminfit.ui.weeklyKm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class Phase5Test {
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test fun predictionsSayFasterOrSlower() {
        assertEquals("25 s slower", predictionDelta(25))
        assertEquals("1:12 faster", predictionDelta(-72))
    }

    private fun run(date: String, km: Double) = PlanSession(date, "run", "easy", Bi("E", "E"), target = PlanTarget(distanceM = km * 1000))

    @Test fun nextWeeksPlanIsComparedWithThisWeeks() {
        val today = LocalDate.parse("2026-11-19")
        val s = listOf(run("2026-11-17", 10.0), run("2026-11-22", 20.0), run("2026-11-24", 12.0), run("2026-11-28", 20.0))
        assertEquals("Next week's plan: 32.0 km, +7% on this week's 30.0 km." to false, planWeekChange(s, today))
        val jump = s + run("2026-11-26", 6.0)
        assertEquals(true, planWeekChange(jump, today)!!.second)
        assertNull(planWeekChange(s.take(2), today))
    }

    @Test fun weeklyKmCountsMondayWeeks() {
        val runs = listOf(Activity(1, "2026-11-17", distanceM = 9480.0), Activity(2, "2026-11-19", distanceM = 5640.0), Activity(3, "2026-11-15", distanceM = 15000.0))
        val (weeks, km) = weeklyKm(runs, 3, LocalDate.parse("2026-11-19"))
        assertEquals(listOf("2026-11-02", "2026-11-09", "2026-11-16"), weeks)
        assertEquals(listOf(0.0, 15.0, 15.12), km.map { Math.round(it!! * 100) / 100.0 })
    }

    @Test fun recapKeepsTheYearAndLabelsTheComparison() {
        val now = YearMonth.parse("2026-11")
        assertEquals("2025", switchKey("year", "2025-03", now))
        assertEquals("2025-12", switchKey("month", "2025", now))
        assertEquals("2026-11", switchKey("month", "2026", now))
        assertEquals("1–19 Oct", previousLabel(listOf("2026-10-01", "2026-10-19"), "month"))
        assertEquals("2025", previousLabel(listOf("2025-01-01", "2025-12-31"), "year"))
        assertEquals("1", bucketLabel("2026-10-26", "month", "2026-11-01", "2026-11-30"))
        assertEquals("2–8", bucketLabel("2026-11-02", "month", "2026-11-01", "2026-11-30"))
        assertEquals("30", bucketLabel("2026-11-30", "month", "2026-11-01", "2026-11-30"))
    }

    @Test fun syncAgeAndStatusEvidence() {
        assertEquals(50L, hoursSince("2026-11-17T08:00:00", Instant.parse("2026-11-19T10:00:00Z")))
        val days = listOf(Daily("2026-08-22", vo2max = 50.1), Daily("2026-11-19", vo2max = 48.7, acuteLoad = 372.0, chronicLoad = 269.0))
        assertEquals("VO2max 48.7, −1.4 in 90 days · load ratio 1.38", statusEvidence(days))
    }

    @Test fun recapJumpsToTheRightWeek() {
        val list = listOf(Activity(1, "2026-11-19"), Activity(2, "2026-11-17"), Activity(3, "2026-11-12"), Activity(4, "2026-10-30"))
        assertEquals(3, listIndexOfWeek(list, LocalDate.parse("2026-11-22")))
        assertEquals(6, listIndexOfWeek(list, LocalDate.parse("2026-11-15")))
        assertEquals(8, listIndexOfWeek(list, LocalDate.parse("2026-11-05")))
    }
}
