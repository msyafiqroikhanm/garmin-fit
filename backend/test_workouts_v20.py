from datetime import date

import core
from core import _session, exercise_history, plan_doc, plan_workout, program_progress, routine_doc, watch_strength

T = {"id": "Strength B", "en": "Strength B"}


def session(items):
    return {"date": "2026-11-21", "kind": "strength", "type": "strength", "title": T, "workout": {"items": items}}


def test_plan_items_validate_and_expand_set_by_set():
    items = [{"ex": "push-ups", "sets": 3, "reps": 10}, {"ex": "side-plank", "sets": 2, "seconds": 30},
             {"ex": "dumbbell-row", "sets": 2, "reps": 12, "kg": 2.5}]
    s = _session(session(items), "2026-11-16", "2026-11-22")
    assert s["workout"] == {"items": items}
    w = plan_workout(s, 20)
    assert w["key"] == "plan:2026-11-21" and w["program"] == "plan"
    assert [(i["ex"], i["round"], i["rounds"]) for i in w["items"]] == [
        ("push-ups", 1, 3), ("push-ups", 2, 3), ("push-ups", 3, 3), ("side-plank", 1, 2), ("side-plank", 2, 2),
        ("dumbbell-row", 1, 2), ("dumbbell-row", 2, 2)]
    assert w["items"][-1]["kg"] == 2.5 and w["items"][3]["seconds"] == 30
    for bad in ([{"ex": "nope", "sets": 3, "reps": 10}], [{"ex": "push-ups", "sets": 3}],
                [{"ex": "push-ups", "sets": 3, "reps": 10, "seconds": 5}], [{"ex": "push-ups", "sets": 0, "reps": 10}], []):
        assert _session(session(bad), "2026-11-16", "2026-11-22") is None
    # the Strength 28 form still works, and a run cannot carry a workout
    assert _session({**session([]), "workout": {"program": "strength28", "day": 4}}, "2026-11-16", "2026-11-22")
    assert _session({**session(items), "kind": "run", "type": "easy"}, "2026-11-16", "2026-11-22") is None


def test_partial_strength28_days_are_reported():
    logs = [{"program": "strength28", "day": 1, "completed": True, "start_time": "2026-11-01 07:00:00"},
            {"program": "strength28", "day": 2, "completed": False, "start_time": "2026-11-03 07:00:00"}]
    p = program_progress(logs)
    assert (p["done_days"], p["partial_days"], p["next_day"]) == ([1], [2], 2)


def test_watch_only_strength_counts_and_history_lists_sets():
    acts = [{"_id": 5, "date": "2026-11-19", "start_time": "2026-11-19 15:54:00", "name": "Strength", "duration_s": 3100.4},
            {"_id": 6, "date": "2026-11-18", "start_time": "2026-11-18 18:50:00", "name": None, "duration_s": 1800}]
    logs = [{"activity_id": 6}]
    w = watch_strength(acts, logs)
    assert [x["id"] for x in w] == ["act:5"] and w[0]["source"] == "watch" and w[0]["duration_s"] == 3100
    log = {"date": "2026-11-12", "start_time": "2026-11-12 07:00:00", "sets": [
        {"ex": "dumbbell-row", "block": "main", "reps": 12, "kg": 2.5, "skipped": False},
        {"ex": "dumbbell-row", "block": "main", "reps": 10, "kg": 3, "skipped": False},
        {"ex": "dumbbell-row", "block": "main", "reps": None, "skipped": True},
        {"ex": "plank", "block": "warmup", "seconds": 30, "skipped": False}]}
    h = exercise_history([log])
    assert h == {"dumbbell-row": [{"date": "2026-11-12", "reps": [12, 10], "seconds": [], "kg": 3}]}


def test_routine_doc_validates():
    ok = routine_doc({"title": " Push day ", "rounds": 3, "items": [{"ex": "push-ups", "reps": 10}, {"ex": "plank", "seconds": 40}]})
    assert ok == {"title": "Push day", "rounds": 3, "items": [{"ex": "push-ups", "reps": 10}, {"ex": "plank", "seconds": 40}]}
    assert routine_doc({"title": "", "rounds": 3, "items": [{"ex": "push-ups", "reps": 10}]}) is None
    assert routine_doc({"title": "x", "rounds": 9, "items": [{"ex": "push-ups", "reps": 10}]}) is None
    assert routine_doc({"title": "x", "rounds": 2, "items": [{"ex": "push-ups"}]}) is None


def _log(mdb, i="log-00001", d="2026-11-19"):
    mdb.workout_logs.insert_one({"_id": i, "user_id": "owner", "date": d, "start_time": f"{d} 07:00:00", "sets": [],
                                 "program": "focus", "title": T, "duration_s": 600, "completed": True})


def test_link_endpoint(api, mdb):
    _log(mdb)
    _log(mdb, "log-00002")
    mdb.activities.insert_many([{"_id": 9, "user_id": "owner", "type": "strength_training", "date": "2026-11-19"},
                                {"_id": 10, "user_id": "owner", "type": "running", "date": "2026-11-19"}])
    assert api("viewer").put("/workouts/logs/log-00001/activity", json={"activity_id": 9}).status_code == 403
    o = api("owner")
    assert o.put("/workouts/logs/log-00001/activity", json={"activity_id": 10}).status_code == 400
    assert o.put("/workouts/logs/nope/activity", json={"activity_id": 9}).status_code == 404
    for _ in range(2):
        r = o.put("/workouts/logs/log-00001/activity", json={"activity_id": 9})
        assert r.status_code == 200 and r.json()["activity_id"] == 9
    assert o.put("/workouts/logs/log-00002/activity", json={"activity_id": 9}).status_code == 409
    assert o.put("/workouts/logs/log-00001/activity", json={"activity_id": None}).json()["activity_id"] is None


def test_routines_endpoint_and_home(api, mdb):
    o = api("owner")
    body = {"id": "routine-abc1", "title": "Push day", "rounds": 2, "items": [{"ex": "push-ups", "reps": 10}]}
    assert api("viewer").post("/workouts/routines", json=body).status_code == 403
    assert o.post("/workouts/routines", json={**body, "rounds": 0}).status_code == 400
    for _ in range(2):
        assert o.post("/workouts/routines", json=body).status_code == 200
    assert mdb.workout_routines.count_documents({}) == 1
    home = o.get("/workouts").json()
    assert home["routines"][0]["title"] == "Push day"
    assert any(w["key"] == "routine:routine-abc1" and w["program"] == "routine" for w in home["workouts"])
    assert o.delete("/workouts/routines/routine-abc1").status_code == 200
    assert o.delete("/workouts/routines/routine-abc1").status_code == 404


def test_home_counts_watch_strength_and_plan_workouts(api, mdb, monkeypatch):
    monkeypatch.setattr(core, "today", lambda: date(2026, 11, 19))
    mdb.activities.insert_one({"_id": 5, "user_id": "owner", "type": "strength_training", "date": "2026-11-19",
                               "start_time": "2026-11-19 15:54:00", "name": "Strength A", "duration_s": 3100})
    plan = plan_doc({"name": "P", "goal": {"id": "g"}, "start": "2026-11-16", "end": "2026-11-22",
                     "sessions": [session([{"ex": "push-ups", "sets": 2, "reps": 8}])]})
    mdb.plans.insert_one({**plan, "user_id": "owner", "status": "active", "created_at": "2026-11-01"})
    home = api("owner").get("/workouts").json()
    assert home["week"]["sessions"] == 1 and home["week"]["watch"] == 1 and home["week"]["dates"] == ["2026-11-19"]
    assert home["recent"][0]["source"] == "watch"
    assert any(w["key"] == "plan:2026-11-21" for w in home["workouts"])
