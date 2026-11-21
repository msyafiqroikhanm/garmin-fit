package dev.garminfit

import dev.garminfit.ui.FreeDay
import dev.garminfit.ui.freeDay
import dev.garminfit.ui.freeDayCaption
import dev.garminfit.ui.weekProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PlanStatesTest {
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    private val today = LocalDate.parse("2026-11-21")
    private val week = PlanWeek("2026-11-16", planned = 5, done = 4, partial = 0, missed = 0, skipped = 0)

    private fun s(date: String, kind: String = "run", status: String = "upcoming", title: String = "Long run", target: PlanTarget? = PlanTarget(distanceM = 17000.0)) =
        PlanSession(date, kind, "easy", Bi(title, title), target = target, status = status)

    private fun view(sessions: List<PlanSession>, end: String = "2026-11-29") =
        PlanView(plan = PlanMeta("B", Bi("g"), "2026-11-02", end), sessions = sessions)

    private fun plan(vararg day: PlanSession) = PlanToday("B", sessions = day.toList(), week = week)

    @Test fun freeDayTellsRestFromNothing() {
        assertEquals(FreeDay.REST, plan(s("2026-11-21", "rest", "rest")).freeDay())
        assertEquals(FreeDay.NONE, plan().freeDay())
        assertNull(plan(s("2026-11-21", "run", "today")).freeDay())
        val old = s("2026-11-21", "rest", "rest")
        assertEquals(FreeDay.REST, PlanToday("B", session = old).freeDay())
    }

    @Test fun captionNamesWeekAndNextSession() {
        val v = view(listOf(s("2026-11-22")))
        assertEquals("Week 3 · Next: Sun · Long run 17 km", freeDayCaption(plan(), v, today, withWeek = true))
        assertEquals("Next: Sun · Long run 17 km", freeDayCaption(plan(), v, today, withWeek = false))
        assertEquals("Week 3 · Next: Mon 30 Nov · Long run 17 km", freeDayCaption(plan(), view(listOf(s("2026-11-30"))), today, withWeek = true))
    }

    @Test fun captionSkipsWhatIsAlreadyInTheTitleAndWhatIsSkipped() {
        val named = view(listOf(s("2026-11-22", title = "Easy 6 km", target = PlanTarget(distanceM = 6000.0))))
        assertEquals("Week 3 · Next: Sun · Easy 6 km", freeDayCaption(plan(), named, today, withWeek = true))
        val skipped = view(listOf(s("2026-11-22", status = "skipped"), s("2026-11-23", title = "Tempo", target = null)))
        assertEquals("Week 3 · Next: Mon · Tempo", freeDayCaption(plan(), skipped, today, withWeek = true))
    }

    @Test fun captionSaysHowTheBlockEnds() {
        val rest = listOf(s("2026-11-22", "rest", "rest"))
        assertEquals("Week 3 · Last day of this block", freeDayCaption(plan(), view(rest, end = "2026-11-21"), today, withWeek = true))
        assertEquals("Week 3 · Block ends Mon 23 Nov", freeDayCaption(plan(), view(rest, end = "2026-11-23"), today, withWeek = true))
    }

    @Test fun captionFallsBackToTheWeekCounts() {
        val p = plan()
        assertEquals(weekProgress(week), freeDayCaption(p, null, today, withWeek = true))
        assertNull(freeDayCaption(p, null, today, withWeek = false))
        val other = PlanView(plan = PlanMeta("Other", Bi("g"), "2026-11-02", "2026-11-29"), sessions = listOf(s("2026-11-22")))
        assertEquals(weekProgress(week), freeDayCaption(p, other, today, withWeek = true))
    }

    @Test fun anEmptyDayCachedYesterdayIsNotToday() {
        val t = Today("owner", plan = PlanToday("B", date = "2026-11-21"))
        assertNull(t.asOf("2026-11-22").plan)
        assertEquals("B", t.asOf("2026-11-21").plan?.name)
    }

    @Test fun indonesianCaption() {
        Locale.setDefault(Locale("in"))
        try {
            assertEquals("Minggu 3 · Berikutnya: Min · Long run 17 km", freeDayCaption(plan(), view(listOf(s("2026-11-22"))), today, withWeek = true))
        } finally { Locale.setDefault(Locale.ENGLISH) }
    }
}
