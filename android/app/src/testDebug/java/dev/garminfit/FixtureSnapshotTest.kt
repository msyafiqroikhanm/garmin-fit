package dev.garminfit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FixtureSnapshotTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun noCacheAndNoFileMeansRefuse() {
        assertNull(FixtureSnapshot(tmp.root).resolve { null })
    }

    @Test fun aLiveCacheIsCapturedAndKept() {
        val s = FixtureSnapshot(tmp.root)
        assertEquals("live", s.resolve { "live" })
        assertEquals("live", FixtureSnapshot(tmp.root).resolve { null })
    }

    @Test fun whileAFixtureWasOnTheCacheIsNeverCaptured() {
        val s = FixtureSnapshot(tmp.root)
        assertEquals("live", s.resolve { "live" })
        s.markOn()
        assertEquals("live", s.resolve { """{"plan":null}""" }) // noplan output must not replace it
        val fresh = FixtureSnapshot(tmp.newFolder())
        fresh.markOn()
        assertNull(fresh.resolve { """{"plan":null}""" })
    }

    @Test fun aLiveFetchWithTheFixtureOffReopensCapture() {
        val s = FixtureSnapshot(tmp.root)
        s.markOn(); s.markLive()
        assertEquals("newer", s.resolve { "newer" })
    }
}
