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
 */
class FixtureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DebugFixture.state = intent.getStringExtra("state")?.takeIf { it in DebugFixture.STATES }
        lifecycleScope.launch {
            val ok = runCatching { Repo.fetchToday(this@FixtureActivity); Repo.fetchPlan(this@FixtureActivity) }
                .onFailure { Log.w(TAG, "refresh failed", it) }.isSuccess
            Widgets.updateAll(this@FixtureActivity)
            Log.i(TAG, "state=${DebugFixture.state ?: "off"} refreshed=$ok")
            startActivity(Intent(this@FixtureActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
        }
    }

    private companion object { const val TAG = "Fixture" }
}
