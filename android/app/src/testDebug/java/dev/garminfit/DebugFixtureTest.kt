package dev.garminfit

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The QA fixture must never let /today, /plan or a write reach the backend while a state is on. */
class DebugFixtureTest {
    private class Chain(private val req: Request) : Interceptor.Chain {
        var proceeded = 0
        override fun request() = req
        override fun proceed(request: Request): Response { proceeded++; error("reached the backend") }
        override fun call(): Call = error("unused")
        override fun connection(): Connection? = null
        override fun connectTimeoutMillis() = 0
        override fun readTimeoutMillis() = 0
        override fun writeTimeoutMillis() = 0
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }

    @After fun off() { DebugFixture.state = null; DebugFixture.base = null }

    private fun call(method: String, path: String): Pair<Response, Chain> {
        val body = if (method == "GET") null else okhttp3.RequestBody.create(null, "{}")
        val chain = Chain(Request.Builder().url("http://x$path").method(method, body).build())
        return DebugFixture.intercept(chain) to chain
    }

    @Test fun readsAndWritesStayOnThePhone() {
        DebugFixture.base = """{"role":"owner"}"""
        for (state in DebugFixture.STATES) {
            DebugFixture.state = state
            for ((m, p) in listOf("GET" to "/today", "GET" to "/plan", "POST" to "/sync", "POST" to "/plan/override", "POST" to "/workouts/logs")) {
                val (r, c) = call(m, p)
                assertEquals("$state $m $p", 0, c.proceeded)
                assertEquals("$state $m $p", if (m == "GET") 200 else 409, r.code)
            }
        }
    }

    @Test fun todayWithoutASnapshotIsRefusedNotForwarded() {
        DebugFixture.state = "rest"
        val (r, c) = call("GET", "/today")
        assertEquals(0, c.proceeded)
        assertEquals(409, r.code)
    }

    @Test fun fixtureOffPassesEverythingThrough() {
        val chain = Chain(Request.Builder().url("http://x/sync").post(okhttp3.RequestBody.create(null, "{}")).build())
        assertTrue(runCatching { DebugFixture.intercept(chain) }.isFailure)
        assertEquals(1, chain.proceeded)
        assertFalse(DebugFixture.state != null)
    }
}
