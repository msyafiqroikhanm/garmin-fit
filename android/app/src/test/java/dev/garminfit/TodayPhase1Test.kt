package dev.garminfit

import dev.garminfit.ui.doneSecondary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.util.Locale

class TodayPhase1Test {
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)

    private val night = Daily("2026-11-19", rhr = 52.0, hrv = 64.0, hrvLow = 63.0, hrvHigh = 80.0, sleepScore = 60.0, sleepS = 16800.0)

    @Test fun lowReadinessNamesShortSleepAndClearsTheMarkers() {
        assertEquals(
            "Driven by short sleep (4h 40m). HRV and resting HR are in your normal range.",
            readinessReason(night, rhrMean = 51.0, loadRatio = 1.0, good = false),
        )
        assertNull(readinessReason(night, 51.0, 1.0, good = true))
    }

    @Test fun lowHrvAndALoadSpikeAreBothNamed() {
        val d = night.copy(hrv = 55.0, sleepScore = 82.0)
        assertEquals("Driven by HRV under your normal and a jump in training load.", readinessReason(d, 51.0, 1.4, good = false))
    }

    @Test fun loadBreakdownSplitsADoubleDay() {
        val d = DayLoad("2026-11-19", 56.0, listOf(LoadPart("run", 44.0), LoadPart("strength", 12.0)))
        assertEquals("Thursday · 56 · run 44 + strength 12", loadBreakdown(d))
        assertEquals("Thursday · load 30", loadBreakdown(DayLoad("2026-11-19", 30.0)))
    }

    @Test fun cardSecondLineFollowsTheSport() {
        val run = Activity(1, "2026-11-19", type = "running", distanceM = 5640.0, durationS = 2865.0, avgSpeed = 1000 / 508.0)
        assertEquals("47m 45s · 8:28 /km", doneSecondary(run, null))
        val gym = Activity(2, "2026-11-19", type = "strength_training", durationS = 3102.0, calories = 247.0)
        assertEquals("37 sets · 247 kcal", doneSecondary(gym, 37))
        val ride = Activity(3, "2026-11-19", type = "road_biking", distanceM = 20000.0, durationS = 3600.0, avgSpeed = 20000 / 3600.0)
        assertEquals("1h 0m · 20.0 km/h", doneSecondary(ride, null))
    }

    @Test fun yesterdaysCacheDoesNotReadAsToday() {
        val yday = "2026-11-19"
        val s = PlanSession(yday, "run", "easy", Bi("E", "E"), status = "done")
        val t = Today("owner", todayActivities = listOf(Activity(1, yday)), lastActivity = Activity(1, yday), plan = PlanToday("p", session = s, sessions = listOf(s)))
        val now = t.asOf("2026-11-20")
        assertEquals(emptyList<Activity>(), now.todayActivities)
        assertNull(now.plan)
        assertEquals(emptyList<Activity>(), now.doneToday("2026-11-20"))
        assertEquals(1, t.asOf(yday).todayActivities.size)
    }
}
