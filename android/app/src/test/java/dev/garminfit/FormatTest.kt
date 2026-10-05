package dev.garminfit

import dev.garminfit.ui.Status
import dev.garminfit.ui.project
import dev.garminfit.ui.trimEnds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test fun verdictFollowsLevelThenScore() {
        assertEquals("Gas!", readinessVerdict("PRIME", 20.0, null)!!.word)
        assertEquals(listOf("30–45 mnt", "Zona 2", "Pulih 18 jam"), readinessVerdict("MODERATE", 55.0, 1088.0)!!.chips)
        assertEquals(Status.LOW, readinessVerdict(null, 39.0, null)!!.status)
        assertNull(readinessVerdict(null, null, null))
    }

    @Test fun factorBoundaries() {
        assertEquals(Status.GOOD, hrvFactor(48.0, 48.0, 62.0).second)
        assertEquals("Batas bawah normal", hrvFactor(45.0, 48.0, 62.0).first)
        assertEquals(Status.LOW, hrvFactor(44.0, 48.0, 62.0).second)
        assertEquals("+7 dari rata-rata", rhrFactor(61.0, 54.0).first)
        assertEquals("Mendekati batas", loadFactor(1.24).first)
        assertEquals(Status.LOW, loadFactor(1.31).second)
        assertEquals(Status.FAIR, stressFactor(26.0).second)
    }

    @Test fun vo2Categories() {
        val c = vo2Category(51.3, male = true, age = 25)!!
        assertEquals("Sangat baik", VO2_CATEGORIES[c.index])
        assertEquals(4.7, c.toNext!!, 1e-9)
        assertNull(vo2Category(60.0, male = true, age = 25)!!.toNext)
        assertNull(vo2Category(45.0, male = false, age = 40))
    }

    @Test fun teLabels() {
        assertEquals("Meningkatkan daya tahan", teLabel(3.6, "daya tahan"))
        assertEquals("Efek minim ke kecepatan", teLabel(1.2, "kecepatan"))
    }

    @Test fun trimEndsDropsFirstAndLast200m() {
        // ~11 m per 1e-4 degree of latitude, 100 points ≈ 1.1 km
        val pts = (0 until 100).map { listOf(-6.0 + it * 1e-4, 106.0, 300.0) }
        val t = trimEnds(pts)
        assertEquals(true, t.first()[0] > -6.0 + 17e-4 && t.first()[0] < -6.0 + 20e-4)
        assertEquals(true, t.last()[0] < -6.0 + 82e-4)
        assertEquals(emptyList<List<Double>>(), trimEnds(pts.take(30)))
    }

    @Test fun projectFitsAndKeepsAspect() {
        val xy = project(listOf(listOf(0.0, 0.0), listOf(0.001, 0.0)), 200f, 100f)
        assertEquals(100f, xy[0].first, 0.01f)
        assertEquals(100f, xy[0].second, 0.01f)
        assertEquals(0f, xy[1].second, 0.01f)
    }
}
