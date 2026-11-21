package dev.garminfit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FixtureSnapshotTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun noCacheAndNoFileMeansRefuse() {
        assertNull(FixtureSnapshot(tmp.root).resolve { null })
    }

    @Test fun aCacheOfUnknownProvenanceIsNeverCaptured() {
        // upgrade from a build that wrote fixture output and no marker
        val s = FixtureSnapshot(tmp.root)
        assertNull(s.resolve { """{"role":"owner","plan":{"name":"QA fixture block"},"today_activities":[]}""" })
        assertFalse(java.io.File(tmp.root, "fixture-snapshot.json").exists())
    }

    @Test fun aLegacySnapshotFileIsIgnored() {
        java.io.File(tmp.root, "fixture-base.json").writeText("""{"plan":{"name":"QA fixture block"}}""")
        assertNull(FixtureSnapshot(tmp.root).resolve { null })
        val s = FixtureSnapshot(tmp.root)
        s.markLive()
        assertEquals("live", s.resolve { "live" })
    }

    @Test fun aVerifiedLiveCacheIsCapturedAndKept() {
        val s = FixtureSnapshot(tmp.root)
        s.markLive()
        assertEquals("live", s.resolve { "live" })
        assertEquals("live", FixtureSnapshot(tmp.root).resolve { null })
    }

    @Test fun switchingAStateOnStopsCapturing() {
        val s = FixtureSnapshot(tmp.root)
        s.markLive()
        assertEquals("live", s.resolve { "live" })
        s.markOn()
        assertEquals("live", s.resolve { """{"plan":null}""" }) // noplan output must not replace it
        val fresh = FixtureSnapshot(tmp.newFolder())
        fresh.markOn()
        assertNull(fresh.resolve { """{"plan":null}""" })
    }

    @Test fun aLiveFetchWithTheFixtureOffReopensCapture() {
        val s = FixtureSnapshot(tmp.root)
        s.markLive(); s.resolve { "old" }; s.markOn(); s.markLive()
        assertEquals("newer", s.resolve { "newer" })
    }
}
