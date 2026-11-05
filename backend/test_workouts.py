from datetime import date

from core import _session, log_doc, match_workouts, plan_progress, program_day, program_progress, workout_muscles


def reps(day, ex):
    return [i.get("reps") for i in day["items"] if i["ex"] == ex]


def main(day):
    return [i for i in day["items"] if i["block"] == "main"]


def test_day_one_is_chest_week_one():
    d = program_day(1)
    assert (d["type"], d["week"], d["key"]) == ("A", 1, "strength28:1")
    assert d["items"][0] == {"ex": "jumping-jacks", "seconds": 30, "block": "warmup", "round": 1, "rounds": 1}
    assert {i["round"] for i in main(d)} == {1, 2}
    assert reps(d, "incline-push-ups") and not reps(d, "decline-push-ups")


def test_week_four_progresses_and_swaps_exercises():
    d = program_day(22)
    assert (d["type"], d["week"]) == ("A", 4)
    assert set(reps(d, "push-ups")) == {16} and set(reps(d, "decline-push-ups")) == {10}
    assert not reps(d, "incline-push-ups")
    b = program_day(23)
    assert (b["type"], b["week"], main(b)[-1]["rounds"]) == ("B", 4, 3)
    assert [i["seconds"] for i in b["items"] if i["ex"] == "plank"] == [60]


def test_harder_scales_bodyweight_only():
    d = program_day(1, difficulty=1)
    assert set(reps(d, "push-ups")) == {12}
    assert set(reps(d, "dumbbell-curl")) == {10}
    assert d["items"][0]["seconds"] == 30


def test_outside_program_raises():
    for bad in (0, 29):
        try:
            program_day(bad)
        except ValueError:
            continue
        raise AssertionError(bad)


SET = {"ex": "push-ups", "block": "main", "round": 1, "target": {"reps": 10}, "reps": 10, "seconds": None, "kg": None, "skipped": False}
LOG = {"id": "abcdefgh-1", "key": "strength28:1", "program": "strength28", "day": 1, "title": {"id": "Day 1"},
       "start_time": "2026-11-03 17:08:00", "end_time": "2026-11-03 17:40:00", "duration_s": 1900, "completed": True,
       "sets": [SET]}


def test_log_doc_validates():
    doc = log_doc(LOG)
    assert (doc["_id"], doc["date"], doc["source"]) == ("abcdefgh-1", "2026-11-03", "app")
    assert log_doc({**LOG, "sets": [{**SET, "ex": "nope"}]}) is None
    assert log_doc({**LOG, "start_time": "2026-11-03 18:00:00"}) is None
    assert log_doc({**LOG, "sets": [{**SET, "target": {"reps": 10, "seconds": 5}}]}) is None


def log(i, start, end):
    return {"_id": i, "start_time": start, "end_time": end}


def act(i, start):
    return {"_id": i, "type": "strength_training", "start_time": start}


def test_match_links_nearest_once():
    l = log("a", "2026-07-31 17:08:00", "2026-07-31 17:30:00")
    assert match_workouts([l], [act(1, "2026-07-31 17:08:54")]) == [("a", 1)]
    assert match_workouts([l], [act(1, "2026-07-31 16:48:00")]) == []
    assert match_workouts([l], [act(1, "2026-07-31 16:58:00"), act(2, "2026-07-31 17:10:00")]) == [("a", 2)]
    l2 = log("b", "2026-07-31 17:12:00", "2026-07-31 17:30:00")
    assert match_workouts([l, l2], [act(1, "2026-07-31 17:10:00")]) == [("a", 1)]


def p(day, start, completed=True):
    return {"program": "strength28", "day": day, "start_time": start, "completed": completed}


def test_program_progress():
    assert program_progress([])["next_day"] == 1
    three = [p(d, f"2026-11-0{d} 08:00:00") for d in (1, 2, 3)]
    assert program_progress(three + [p(4, "2026-11-05 08:00:00", False)])["next_day"] == 4
    again = program_progress([p(28, "2026-10-01 08:00:00"), p(1, "2026-10-02 08:00:00")])
    assert again == {"next_day": 2, "done_days": [1], "runs_completed": 1}


PLAN = {"start": "2026-11-02", "end": "2026-11-08", "sessions": [
    {"date": "2026-11-03", "kind": "strength", "type": "strength", "title": {"id": "Day 4"}}]}


def test_plan_scores_strength_from_log():
    done = {"date": "2026-11-03", "completed": True, "sets": [{"skipped": False}]}
    s = plan_progress(PLAN, [], date(2026, 11, 4), [done])["sessions"][0]
    assert (s["status"], s["pct"]) == ("done", 1.0)
    part = {"date": "2026-11-03", "completed": False, "sets": [{"skipped": False}] * 3 + [{"skipped": True}]}
    s = plan_progress(PLAN, [], date(2026, 11, 4), [part])["sessions"][0]
    assert (s["status"], s["pct"]) == ("partial", 0.75)


def test_workout_only_on_strength_sessions():
    run = {"date": "2026-11-03", "kind": "run", "type": "easy", "title": {"id": "Easy"}}
    w = {"program": "strength28", "day": 4}
    assert _session({**run, "workout": w}, "2026-11-02", "2026-11-08") is None
    assert _session({**PLAN["sessions"][0], "workout": w}, "2026-11-02", "2026-11-08")["workout"] == w
    assert _session({**PLAN["sessions"][0], "workout": {**w, "day": 29}}, "2026-11-02", "2026-11-08") is None


def test_muscles_skip_warmup_and_skipped_sets():
    sets = [SET, {**SET, "skipped": True}, {**SET, "ex": "jumping-jacks", "block": "warmup"}]
    m = workout_muscles({"sets": sets})
    assert m["total_sets"] == 1
    assert m["muscles"]["CHEST"] == {"primary": 1, "secondary": 0}
    assert "QUADS" not in m["muscles"]
