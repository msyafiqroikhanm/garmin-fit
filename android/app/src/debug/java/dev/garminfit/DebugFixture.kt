package dev.garminfit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.time.LocalDate

/**
 * Debug only: fakes plan states the owner's real plan does not have today. /plan answers a made-up block named
 * "QA fixture block"; /today keeps its real readings and gets that block's day as its plan. Set by FixtureActivity;
 * null passes every call through. The real plan, sessions and overrides in Mongo are never touched.
 */
object DebugFixture : Interceptor {
    val STATES = setOf("rest", "none", "noplan", "lastday", "ending", "stale", "short")
    @Volatile var state: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val st = state
        val req = chain.request()
        val path = req.url.encodedPath
        if (st == null || req.method != "GET" || (path != "/today" && path != "/plan")) return chain.proceed(req)
        val today = LocalDate.now()
        val v = view(st, today)
        val body = if (path == "/plan") json.encodeToString(PlanView.serializer(), v) else {
            val real = chain.proceed(req)
            if (!real.isSuccessful) return real
            val obj = json.parseToJsonElement(real.body!!.string()).jsonObject.toMutableMap()
            obj["plan"] = planToday(st, v, today)?.let { json.encodeToJsonElement(PlanToday.serializer(), it) } ?: JsonNull
            // free-day states need "nothing done today"; "short" keeps the real activities so Today shows its result
            if (st != "short") { obj["today_activities"] = JsonArray(emptyList()); obj["last_activity"] = JsonNull }
            JsonObject(obj).toString()
        }
        return Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body.toResponseBody("application/json".toMediaType())).build()
    }

    private fun s(d: LocalDate, kind: String, type: String, title: String, status: String, target: PlanTarget? = null) =
        PlanSession(date = d.toString(), kind = kind, type = type, title = Bi(title, title), target = target, status = status)

    private fun rest(d: LocalDate) = s(d, "rest", "rest", "Rest", "rest")

    private fun view(st: String, t: LocalDate): PlanView {
        if (st == "noplan") return PlanView()
        val monday = t.minusDays(t.dayOfWeek.value - 1L)
        val start = monday.minusWeeks(2) // today sits in week 3
        val end = when (st) { "lastday" -> t; "ending" -> t.plusDays(2); else -> t.plusDays(8) }
        val past = listOf(
            s(t.minusDays(2), "run", "easy", "Easy run", "done", PlanTarget(distanceM = 6000.0)).copy(pct = 1.0, done = PlanTarget(distanceM = 6100.0, durationS = 2280.0)),
            rest(t.minusDays(1)),
        )
        val day = when (st) {
            "rest", "lastday", "ending" -> listOf(rest(t))
            "short" -> listOf(
                s(t, "run", "easy", "Easy run", "partial", PlanTarget(distanceM = 6000.0)).copy(pct = 0.9, done = PlanTarget(distanceM = 5400.0, durationS = 2100.0)),
                s(t, "strength", "strength", "Strength B", "done", PlanTarget(durationS = 1500.0)).copy(pct = 1.0, done = PlanTarget(distanceM = 0.0, durationS = 1404.0)),
            )
            else -> emptyList() // none, stale
        }
        val future = when (st) {
            "lastday" -> emptyList()
            "ending" -> listOf(rest(t.plusDays(1)), rest(t.plusDays(2)))
            else -> listOf(s(t.plusDays(1), "run", "long", "Long run", "upcoming", PlanTarget(distanceM = 17000.0)), rest(t.plusDays(2)))
        }
        val sessions = past + day + future
        val weeks = generateSequence(start) { it.plusWeeks(1) }.takeWhile { it <= end }.map { w ->
            val rows = sessions.filter { LocalDate.parse(it.date) >= w && LocalDate.parse(it.date) < w.plusWeeks(1) && it.kind != "rest" }
            PlanWeek(w.toString(), planned = rows.size, done = rows.count { it.status == "done" }, partial = rows.count { it.status == "partial" }, missed = 0, skipped = 0)
        }.toList()
        val meta = PlanMeta("QA fixture block", Bi("Fixture untuk status hari plan", "Fixture for plan day states"), start.toString(), end.toString(), id = "qa-fixture")
        return PlanView(plan = meta, sessions = sessions, weeks = weeks)
    }

    private fun planToday(st: String, v: PlanView, t: LocalDate): PlanToday? {
        val meta = v.plan ?: return null
        val week = v.weeks.lastOrNull { LocalDate.parse(it.start) <= t }
        if (st == "stale") {
            val y = s(t.minusDays(1), "run", "easy", "Easy run", "today", PlanTarget(distanceM = 6000.0))
            return PlanToday(meta.name, date = t.minusDays(1).toString(), session = y, sessions = listOf(y), week = week)
        }
        val day = v.sessions.filter { it.date == t.toString() }
        return PlanToday(meta.name, date = t.toString(), session = day.firstOrNull { it.kind == "run" } ?: day.firstOrNull(), sessions = day, week = week)
    }
}
