package dev.garminfit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class TodayLogicTest {
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    private val day = "2026-11-19"
    private fun act(id: Long, load: Double?, secs: Double) = Activity(id, day, trainingLoad = load, durationS = secs)
    private fun session(kind: String, status: String, ids: List<Long> = emptyList()) = PlanSession(
        date = day, kind = kind, type = if (kind == "run") "easy" else kind, title = (if (kind == "run") "Easy" else "Strength A").let { Bi(it, it) },
        target = PlanTarget(durationS = 1800.0), status = status, activityIds = ids,
    )

    @Test fun restOnlyOnceEverySessionIsDone() {
        val pending = PlanToday("p", session = session("run", "done"), sessions = listOf(session("run", "done"), session("strength", "today")))
        val advice = todayAdvice(null, pending)!!.first
        assertEquals("Next up: Strength A, 30m.", advice)
        assertEquals("strength", pending.pending()!!.kind)

        val allDone = pending.copy(sessions = listOf(session("run", "done"), session("strength", "partial")))
        assertNull(allDone.pending())
        assertEquals("Today's sessions are done. Rest up and eat well.", todayAdvice(null, allDone)!!.first)
    }

    @Test fun oldServerFallsBackToTheSingleSession() {
        val old = PlanToday("p", session = session("run", "today"))
        assertEquals(listOf("run"), old.daySessions.map { it.kind })
        assertEquals("Per plan: Easy, 30m.", todayAdvice(null, old)!!.first)
        val t = Today(role = "owner", lastActivity = act(1, 40.0, 1800.0))
        assertEquals(listOf(1L), t.doneToday(day).map { it.id })
        assertEquals(emptyList<Activity>(), t.doneToday("2026-11-20"))
    }

    @Test fun oldSummariesSayTheirAge() {
        val now = LocalDate.parse(day)
        assertNull(summaryAge("2026-11-11", now))
        assertEquals("1 week ago", summaryAge("2026-11-10", now))
        assertEquals("3 weeks ago", summaryAge("2026-10-25", now))
        Locale.setDefault(Locale("in"))
        assertEquals("3 minggu lalu", summaryAge("2026-10-25", now))
    }
}
