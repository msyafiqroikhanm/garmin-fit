package dev.garminfit

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dev.garminfit.widget.Widgets
import kotlinx.coroutines.launch

/**
 * Debug only: switches the plan fixture, refreshes the caches and widgets, then reopens the app on Today.
 *   adb shell am start -n dev.garminfit/.FixtureActivity --es state rest
 * States: rest | none | noplan | lastday | ending | stale | short; anything else (or "off") goes back to live data.
 * Turning a state on snapshots the cached live /today into filesDir/fixture-base.json (kept across process death, so no
 * backend is needed once one exists); the fixture rewrites that copy and never calls /today.
 */
class FixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val want = intent.getStringExtra("state")?.takeIf { it in DebugFixture.STATES }
        lifecycleScope.launch {
            val snaps = FixtureSnapshot(filesDir)
            if (want != null) {
                if (DebugFixture.base == null) {
                    DebugFixture.base = snaps.resolve { Repo.cachedToday(this@FixtureActivity)?.takeIf { it.plan?.name != DebugFixture.NAME }?.let { json.encodeToString(Today.serializer(), it) } }
                }
                if (DebugFixture.base == null) {
                    // refuse before any fetch: /today on the real backend may store a recovery adjustment
                    DebugFixture.state = null
                    Log.i(TAG, "state=off refreshed=false reason=no live /today snapshot, run state off with the backend reachable first")
                    finish()
                    return@launch
                }
                snaps.markOn()
                DebugFixture.state = want
            } else { DebugFixture.state = null; DebugFixture.base = null }
            val ok = runCatching { Repo.fetchToday(this@FixtureActivity); Repo.fetchPlan(this@FixtureActivity) }
                .onFailure { Log.w(TAG, "refresh failed", it) }.isSuccess
            if (want == null && ok) snaps.markLive()
            Widgets.updateAll(this@FixtureActivity)
            Log.i(TAG, "state=${DebugFixture.state ?: "off"} refreshed=$ok")
            startActivity(Intent(this@FixtureActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    private companion object { const val TAG = "Fixture" }
}
