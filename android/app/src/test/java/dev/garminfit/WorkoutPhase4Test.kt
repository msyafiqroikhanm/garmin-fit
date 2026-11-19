package dev.garminfit

import dev.garminfit.ui.draftLog
import dev.garminfit.ui.strengthLead
import dev.garminfit.ui.visitText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class WorkoutPhase4Test {
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    private fun s(date: String, status: String, kind: String = "strength") = PlanSession(date, kind, kind, Bi("S", "S"), status = status)
    private val today = LocalDate.parse("2026-11-19")

    @Test fun leadIsTodaysStrengthElseTheNextWithinAWeek() {
        val done = s("2026-11-19", "done")
        val sat = s("2026-11-21", "upcoming")
        val lead = strengthLead(listOf(s("2026-11-18", "done"), done, s("2026-11-19", "done", "run"), sat), today)
        assertEquals(listOf(done), lead.doneToday)
        assertEquals(sat, lead.next)
        assertEquals(s("2026-11-19", "today"), strengthLead(listOf(s("2026-11-19", "today"), sat), today).next)
        assertNull(strengthLead(listOf(s("2026-11-28", "upcoming")), today).next)
    }

    @Test fun visitsFoldEqualSets() {
        assertEquals("3 × 10 · 5 kg", visitText(ExerciseVisit("2026-11-12", reps = listOf(10, 10, 10), kg = 5.0)))
        assertEquals("10 · 10 · 8", visitText(ExerciseVisit("2026-11-12", reps = listOf(10, 10, 8))))
        assertEquals("2 × 0:30", visitText(ExerciseVisit("2026-11-12", seconds = listOf(30, 30))))
    }

    @Test fun anUnfinishedDraftLogsUnreachedSetsAsSkipped() {
        val items = listOf(WorkoutItem("push-ups", reps = 10, block = "main"), WorkoutItem("plank", seconds = 30, block = "main"))
        val def = WorkoutDef("strength28:1", "strength28", day = 1, type = "A", title = Bi("Dada", "Chest"), items = items)
        val d = WorkoutDraft(def, 1, listOf(SetLog("push-ups", "main", 1, SetTarget(10), reps = 9), null), "2026-11-19 07:00:00", 61_000, "log-1")
        val log = draftLog(d, completed = false)
        assertEquals("Day 1 · Chest", log.title.en)
        assertEquals(61, log.durationS)
        assertTrue(log.sets[1].skipped && !log.sets[0].skipped)
    }
}
