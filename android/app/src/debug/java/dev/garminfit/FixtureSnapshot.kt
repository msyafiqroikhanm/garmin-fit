package dev.garminfit

import java.io.File

/**
 * Where the fixture's live /today comes from. A flag file marks every moment a fixture was on; while it exists the
 * cached /today may be fixture output (including `noplan`, whose plan is null), so only the snapshot file of the last
 * live capture counts. The flag is removed once a fetch with the fixture off succeeds, which makes the cache live again.
 */
class FixtureSnapshot(dir: File) {
    @PublishedApi internal val file = File(dir, "fixture-base.json")
    @PublishedApi internal val flag = File(dir, "fixture-on")

    /** The snapshot JSON to build on, or null when there is none (the caller must refuse). */
    inline fun resolve(cachedLive: () -> String?): String? {
        if (!flag.exists()) cachedLive()?.let { file.writeText(it) }
        return file.takeIf { it.exists() }?.readText()
    }

    fun markOn() { flag.writeText("1") }
    fun markLive() { flag.delete() }
}
