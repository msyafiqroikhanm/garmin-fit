package dev.garminfit

import dev.garminfit.ui.adjustedText
import dev.garminfit.ui.averageLine
import dev.garminfit.ui.countableFor
import dev.garminfit.ui.donePace
import dev.garminfit.ui.linkCandidates
import dev.garminfit.ui.resultText
import dev.garminfit.ui.sessionProgress
import dev.garminfit.ui.weekProgress
import dev.garminfit.ui.workLaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class PlanPhase2Test {
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun s(date: String, status: String, kind: String = "run", target: PlanTarget? = PlanTarget(distanceM = 6000.0), done: PlanTarget? = null) =
        PlanSession(date, kind, "easy", Bi("Easy", "Easy"), target = target, status = status, done = done)

    @Test fun countsLineAddsUp() {
        val list = listOf(s("2026-11-16", "done"), s("2026-11-17", "partial"), s("2026-11-18", "missed"), s("2026-11-19", "today"),
            s("2026-11-20", "upcoming"), s("2026-11-21", "skipped"), s("2026-11-22", "rest", "rest"))
        assertEquals("1 done · 1 short · 1 missed · 1 skipped · 2 to go", progressLine(planCounts(list)))
        assertEquals("15 done · 0 missed · 5 to go", progressLine(PlanCounts(15, 0, 0, 0, 5)))
    }

    @Test fun weekLineFallsBackForOlderServers() {
        assertEquals("This week 2 of 5 done", weekProgress(PlanWeek("2026-11-16", planned = 5, done = 2)))
        assertEquals("This week · 2 done · 1 short · 0 missed · 2 to go", weekProgress(PlanWeek("2026-11-16", 5, 2, partial = 1, missed = 0, skipped = 0)))
    }

    @Test fun averageOnlyWhenItSaysSomething() {
        assertNull(averageLine(0.96, 8))
        assertEquals("Average 84% of target across 16 scored sessions", averageLine(0.84, 16))
    }

    @Test fun shareSaysWhatItCounts() {
        assertEquals("96% of distance target", targetShare(s("2026-11-19", "done"), 0.955))
        assertEquals("100% of time target", targetShare(s("2026-11-19", "done", target = PlanTarget(durationS = 1800.0)), 1.0))
        val adjusted = s("2026-11-19", "done").copy(adjust = PlanAdjust("quality_to_easy", PlanTarget(durationS = 2400.0)))
        assertEquals("100% of time target", targetShare(adjusted, 1.0))
        assertEquals("Adjusted for recovery: 40m (plan 6 km)", adjustedText(adjusted))
    }

    @Test fun resultSaysWhatHappened() {
        assertEquals("done Wed · 6.02 km", resultText(s("2026-11-17", "done", done = PlanTarget(6020.0, 2900.0)).copy(doneOn = "2026-11-18")))
        assertEquals("done · 12 of 14 sets", resultText(s("2026-11-20", "done", "strength", done = PlanTarget(sets = 12, of = 14))))
        assertEquals("done · 23m 24s on the watch", resultText(s("2026-11-21", "done", "strength", PlanTarget(durationS = 1500.0), PlanTarget(0.0, 1404.0))))
        assertEquals("short · 5.4 of 6 km", resultText(s("2026-11-19", "partial", done = PlanTarget(5400.0, 2100.0))))
        assertEquals("short · 40m of 50m", resultText(s("2026-11-19", "partial", target = PlanTarget(durationS = 3000.0), done = PlanTarget(durationS = 2400.0))))
        assertEquals("short · 5.4 of 6 km · on Thu", resultText(s("2026-11-18", "partial", done = PlanTarget(5400.0, 2100.0)).copy(doneOn = "2026-11-19")))
        val moved = s("2026-11-20", "done", "strength", PlanTarget(durationS = 1800.0), PlanTarget(0.0, 1200.0)).copy(doneOn = "2026-11-21")
        assertEquals("done Sat · 20m on the watch", resultText(moved))
        assertEquals("short · 5.4 of 6 km", sessionProgress(s("2026-11-19", "partial", done = PlanTarget(5400.0, 2100.0))))
        assertNull(s("2026-11-21", "done", "strength").copy(pct = 1.0).share)
        val shortRun = s("2026-11-21", "partial", done = PlanTarget(5400.0, 2100.0))
        assertEquals("That's it for today, a bit under target. Rest up and eat well.", todayAdvice(null, PlanToday("P", sessions = listOf(shortRun)))!!.first)
        assertEquals("skipped · sick", resultText(s("2026-11-19", "skipped").copy(skipped = "sick")))
        assertEquals("missed", resultText(s("2026-11-19", "missed")))
    }

    @Test fun unplannedActivitiesFindTheirSession() {
        val extras = listOf(
            PlanExtra(1, "2026-11-18", "running"), PlanExtra(2, "2026-11-23", "running"), PlanExtra(3, "2026-11-18", "strength_training"),
        )
        val missed = s("2026-11-17", "missed")
        assertEquals(listOf(1L), linkCandidates(missed, extras).map { it.id })
        assertEquals(missed, countableFor(extras[0], listOf(missed, s("2026-11-21", "upcoming"))))
        assertNull(countableFor(extras[1], listOf(missed)))
        assertEquals(480.0, donePace(s("2026-11-17", "done", done = PlanTarget(5000.0, 2400.0)))!!, 0.01)
    }

    @Test fun repsAreTheLapsRunNearThePlannedPace() {
        val laps = listOf(Split(2000.0, 900.0), Split(2000.0, 656.0), Split(400.0, 150.0), Split(2000.0, 652.0), Split(1480.0, 830.0), Split(30.0, 5.0))
        assertEquals(listOf(328.0, 326.0), workLaps(laps, PlanPace(325.0, 335.0)))
    }

    @Test fun indonesianShortResult() {
        Locale.setDefault(Locale("in"))
        try {
            assertEquals("kurang · 5,4 dari 6 km", resultText(s("2026-11-19", "partial", done = PlanTarget(5400.0, 2100.0))))
            assertEquals("dikerjakan Sab · 20m di jam tangan", resultText(s("2026-11-20", "done", "strength", PlanTarget(durationS = 1800.0), PlanTarget(0.0, 1200.0)).copy(doneOn = "2026-11-21")))
        } finally { Locale.setDefault(Locale.ENGLISH) }
    }
}
