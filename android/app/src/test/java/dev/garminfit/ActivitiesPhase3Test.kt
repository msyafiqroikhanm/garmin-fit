package dev.garminfit

import dev.garminfit.ui.isKmSplits
import dev.garminfit.ui.weekHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class ActivitiesPhase3Test {
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    @Test fun weekHeaderSumsTheWeek() {
        val acts = listOf(Activity(1, "2026-11-10", distanceM = 20000.0), Activity(2, "2026-11-12", distanceM = 12400.0), Activity(3, "2026-11-12"))
        val year = LocalDate.now().year
        val expected = if (year == 2026) "9–15 Nov · 32.40 km · 3 sessions" else "9–15 Nov 2026 · 32.40 km · 3 sessions"
        assertEquals(expected, weekHeader(LocalDate.parse("2026-11-09"), acts))
    }

    @Test fun kmSplitsVersusWorkoutLaps() {
        assertTrue(isKmSplits(listOf(Split(1000.0, 400.0), Split(1001.0, 398.0), Split(480.0, 190.0))))
        assertFalse(isKmSplits(listOf(Split(2000.0, 650.0), Split(400.0, 150.0), Split(2000.0, 652.0))))
        assertFalse(isKmSplits(listOf(Split(5000.0, 2000.0))))
    }

    @Test fun sportsReadTheirOwnSpeed() {
        assertEquals("2:00" to "/100 m", speedText("lap_swimming", 100 / 120.0))
        assertEquals("8:28" to "/km", speedText("walking", 1000 / 508.0))
    }
}
