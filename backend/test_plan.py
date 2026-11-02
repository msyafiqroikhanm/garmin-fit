from datetime import date

from core import plan_doc, plan_progress

T = {"id": "Easy 6 km", "en": "Easy 6 km"}
PLAN = {
    "name": "HM build",
    "goal": {"id": "Half marathon di bawah 2 jam", "en": "Half marathon under 2 hours"},
    "start": "2026-10-12",
    "end": "2026-10-18",
    "sessions": [
        {"date": "2026-10-14", "kind": "run", "type": "tempo", "title": T, "target": {"duration_s": 3600}},
        {"date": "2026-10-12", "kind": "run", "type": "easy", "title": T, "target": {"distance_m": 6000}, "hr_zone": 2},
        {"date": "2026-10-13", "kind": "rest", "type": "rest", "title": {"id": "Libur"}},
        {"date": "2026-10-15", "kind": "strength", "type": "strength", "title": {"id": "Gym"}},
        {"date": "2026-10-16", "kind": "run", "type": "easy", "title": T, "target": {"distance_m": 5000}},
        {"date": "2026-10-18", "kind": "run", "type": "long", "title": T, "target": {"distance_m": 16000}},
    ],
}


def run(i, d, km, kind="running", secs=0):
    return {"_id": i, "date": d, "type": kind, "distance_m": km * 1000, "duration_s": secs}


def by_date(view):
    return {s["date"]: s for s in view["sessions"]}


def test_plan_is_sorted_and_trimmed():
    doc = plan_doc({**PLAN, "junk": 1})
    assert [s["date"] for s in doc["sessions"]] == sorted(s["date"] for s in PLAN["sessions"])
    assert "junk" not in doc


def test_bad_plans_are_rejected():
    assert plan_doc({**PLAN, "sessions": []}) is None
    outside = {**PLAN["sessions"][0], "date": "2026-10-30"}
    assert plan_doc({**PLAN, "sessions": [outside]}) is None
    assert plan_doc({**PLAN, "sessions": [{**PLAN["sessions"][0], "target": {"distance_m": -1}}]}) is None
    assert plan_doc({**PLAN, "sessions": [{**PLAN["sessions"][0], "hr_zone": 7}]}) is None
    assert plan_doc({**PLAN, "goal": "faster"}) is None


def test_progress_scores_each_session():
    acts = [
        run(1, "2026-10-12", 5.9),                      # 98% of 6 km counts as done
        run(2, "2026-10-14", 8, secs=2700),             # 45 of 60 min tempo
        run(3, "2026-10-15", 0, "strength_training", 2400),
        run(4, "2026-10-15", 3),                        # extra run on gym day, not scored
    ]
    view = plan_progress(plan_doc(PLAN), acts, date(2026, 10, 16))
    s = by_date(view)
    assert s["2026-10-12"]["status"] == "done" and s["2026-10-12"]["pct"] == 0.983
    assert s["2026-10-13"]["status"] == "rest" and s["2026-10-13"]["pct"] is None
    assert s["2026-10-14"]["status"] == "partial" and s["2026-10-14"]["pct"] == 0.75
    assert s["2026-10-15"]["status"] == "done" and s["2026-10-15"]["pct"] == 1.0  # no target, any session counts
    assert s["2026-10-16"]["status"] == "today" and s["2026-10-16"]["pct"] is None
    assert s["2026-10-18"]["status"] == "upcoming"
    assert [e["_id"] for e in view["extras"]] == [4]
    assert view["overall"] == round((0.983 + 0.75 + 1.0) / 3, 3)
    assert view["weeks"] == [{"start": "2026-10-12", "planned": 5, "done": 2, "pct": view["overall"], "goal": None}]


def test_double_run_counts_together_and_missed_scores_zero():
    acts = [run(1, "2026-10-16", 2.5), run(2, "2026-10-16", 2.5)]
    view = plan_progress(plan_doc(PLAN), acts, date(2026, 10, 17))
    s = by_date(view)
    assert s["2026-10-16"]["status"] == "done" and s["2026-10-16"]["activity_ids"] == [1, 2]
    assert s["2026-10-12"]["status"] == "missed" and s["2026-10-12"]["pct"] == 0.0


def test_week_goals_ride_on_week_rows():
    goal = {"id": "Minggu ringan", "en": "Easy week"}
    doc = plan_doc({**PLAN, "weeks": [{"start": "2026-10-12", "goal": goal}]})
    view = plan_progress(doc, [], date(2026, 10, 16))
    assert view["weeks"][0]["goal"] == goal
    assert plan_progress(plan_doc(PLAN), [], date(2026, 10, 16))["weeks"][0]["goal"] is None
    assert plan_doc({**PLAN, "weeks": [{"start": "2026-10-13", "goal": goal}]}) is None  # not a Monday
    assert plan_doc({**PLAN, "weeks": [{"start": "2026-10-19", "goal": goal}]}) is None  # after the plan
    assert plan_doc({**PLAN, "weeks": [{"start": "2026-10-12", "goal": "easy"}]}) is None


def _days(n=20, tired_last=0, rhr_only=False, hrv_only=False):
    """n days at HRV 70 / RHR 50; the last `tired_last` days drop HRV under the floor and lift RHR."""
    out = []
    for i in range(n):
        tired = i >= n - tired_last
        out.append({"date": f"2026-11-{i + 1:02d}", "hrv_balanced_low": 63,
                    "hrv_last_night": 58 if tired and not rhr_only else 70,
                    "rhr": 54 if tired and not hrv_only else 50, "sleep_score": 30, "readiness": 20})
    return out


def _session(typ, date_="2026-11-20", target=None):
    return {"date": date_, "kind": "run", "type": typ, "title": T, "target": target or {"distance_m": 10000}}


def test_adjust_needs_two_low_nights_and_high_rhr():
    from core import plan_adjust
    fresh = _days()  # bad sleep and readiness every day, recovery numbers fine
    assert plan_adjust(fresh, _session("tempo")) is None
    one_night = _days(tired_last=2)
    one_night[-2]["hrv_last_night"] = 70
    assert plan_adjust(one_night, _session("tempo")) is None
    assert plan_adjust(_days(tired_last=2, hrv_only=True), _session("tempo")) is None
    assert plan_adjust(_days(tired_last=2, rhr_only=True), _session("tempo")) is None


def test_adjust_by_session_type():
    from core import plan_adjust
    tired = _days(tired_last=2)
    q = plan_adjust(tired, _session("interval"))
    assert q["key"] == "quality_to_easy" and q["target"] == {"duration_s": 2400} and q["hrv"] == [58, 58]
    assert plan_adjust(tired, _session("long", target={"distance_m": 15000}))["target"] == {"distance_m": 10500}
    assert plan_adjust(tired, _session("easy")) is None
    assert plan_adjust(tired, _session("tempo", date_="2026-11-21")) is None  # data isn't from today
    week = plan_adjust(_days(tired_last=4), _session("easy", target={"duration_s": 2700}))
    assert week["key"] == "easy_week" and week["target"] == {"duration_s": 1860} and week["streak"] == 3
