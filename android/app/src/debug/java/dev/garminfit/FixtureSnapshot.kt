package dev.garminfit

import java.io.File

/**
 * Where the fixture's live /today comes from. The cached /today counts as live only while the `fixture-live` marker
 * exists, and only a successful fetch with the fixture off writes it ([markLive]); switching a state on removes it
 * ([markOn]). A missing marker therefore means unknown provenance (an older debug build, or any fixture output), never
 * live: then only the snapshot file of an earlier verified capture stands in, or there is none and the caller refuses.
 * The snapshot file has a new name on purpose: `fixture-base.json` of earlier builds was written without that proof and is ignored.
 */
class FixtureSnapshot(dir: File) {
    @PublishedApi internal val file = File(dir, "fixture-snapshot.json")
    @PublishedApi internal val live = File(dir, "fixture-live")

    /** The snapshot JSON to build on, or null when there is none (the caller must refuse). */
    inline fun resolve(cachedLive: () -> String?): String? {
        if (live.exists()) cachedLive()?.let { file.writeText(it) }
        return file.takeIf { it.exists() }?.readText()
    }

    fun markOn() { live.delete() }
    fun markLive() { live.writeText("1") }
}
