from datetime import date

from core import plan_doc, plan_progress, save_adjustment, plan_view

T = {"id": "Easy", "en": "Easy"}
PLAN = plan_doc({
    "name": "Block", "goal": {"id": "g"}, "start": "2026-11-16", "end": "2026-11-22",
    "sessions": [
        {"date": "2026-11-17", "kind": "run", "type": "easy", "title": T, "target": {"distance_m": 6000}},
        {"date": "2026-11-19", "kind": "run", "type": "tempo", "title": T, "target": {"distance_m": 8000}},
        {"date": "2026-11-20", "kind": "strength", "type": "strength", "title": {"id": "Gym"}, "target": {"duration_s": 1800}},
        {"date": "2026-11-21", "kind": "run", "type": "easy", "title": T, "target": {"distance_m": 4000}},
        {"date": "2026-11-21", "kind": "run", "type": "easy", "title": T, "target": {"distance_m": 4000}},
        {"date": "2026-11-22", "kind": "run", "type": "race", "title": T, "target": {"distance_m": 10000}},
    ],
})


def act(i, d, km, t="running", start="06:00", secs=0):
    return {"_id": i, "date": d, "type": t, "distance_m": km * 1000, "duration_s": secs, "start_time": f"{d} {start}:00"}


def rows(view):
    return {(r["date"], r["n"]): r for r in view["sessions"]}


def test_a_run_a_day_late_counts_and_says_when():
    v = plan_progress(PLAN, [act(1, "2026-11-18", 6)], date(2026, 11, 20))
    r = rows(v)[("2026-11-17", 0)]
    assert (r["status"], r["done_on"], r["activity_ids"]) == ("done", "2026-11-18", [1])
    assert v["extras"] == []


def test_auto_match_never_takes_a_run_its_own_day_needs_or_reaches_two_days():
    acts = [act(1, "2026-11-19", 8), act(2, "2026-11-15", 6)]
    v = plan_progress(PLAN, acts, date(2026, 11, 20))
    s = rows(v)
    assert s[("2026-11-19", 0)]["activity_ids"] == [1]
    assert s[("2026-11-17", 0)]["status"] == "missed"   # the 15th is two days off
    assert [e["_id"] for e in v["extras"]] == []           # the 15th is before the plan, not an extra


def test_a_short_jog_next_day_does_not_count_as_the_moved_session():
    v = plan_progress(PLAN, [act(1, "2026-11-18", 0.9)], date(2026, 11, 20))
    assert rows(v)[("2026-11-17", 0)]["status"] == "missed"
    assert [e["_id"] for e in v["extras"]] == [1]


def test_today_and_future_sessions_do_not_borrow_yesterdays_run():
    v = plan_progress(PLAN, [act(1, "2026-11-18", 8)], date(2026, 11, 19))
    assert rows(v)[("2026-11-19", 0)]["status"] == "today"


def test_override_links_skips_and_unlinks():
    acts = [act(1, "2026-11-16", 6), act(2, "2026-11-17", 3)]
    link = [{"date": "2026-11-17", "kind": "run", "n": 0, "activity_ids": [1, 2]}]
    r = rows(plan_progress(PLAN, acts, date(2026, 11, 20), overrides=link))[("2026-11-17", 0)]
    assert (r["status"], r["pct"], r["activity_ids"], r["override"]) == ("done", 1.0, [1, 2], True)
    skip = [{"date": "2026-11-19", "kind": "run", "n": 0, "skipped": "sick"}]
    v = plan_progress(PLAN, acts, date(2026, 11, 20), overrides=skip)
    r = rows(v)[("2026-11-19", 0)]
    assert (r["status"], r["pct"], r["skipped"]) == ("skipped", None, "sick")
    assert v["weeks"][0]["skipped"] == 1 and v["weeks"][0]["planned"] == 5
    # unlinking is just no override: back to the plain matching
    assert rows(plan_progress(PLAN, acts, date(2026, 11, 20)))[("2026-11-17", 0)]["activity_ids"] == [2]


def test_adjusted_target_is_what_gets_scored():
    adj = [{"date": "2026-11-19", "kind": "run", "key": "quality_to_easy", "target": {"duration_s": 2400}}]
    v = plan_progress(PLAN, [act(1, "2026-11-19", 5, secs=2400)], date(2026, 11, 20), adjusts=adj)
    r = rows(v)[("2026-11-19", 0)]
    assert (r["status"], r["pct"], r["adjust"]["target"]) == ("done", 1.0, {"duration_s": 2400})


def test_two_runs_on_one_day_split_and_a_race_takes_its_longest():
    acts = [act(1, "2026-11-21", 4, start="06:00"), act(2, "2026-11-21", 2, start="17:00"),
            act(3, "2026-11-22", 2, start="05:00"), act(4, "2026-11-22", 10.1, start="06:00")]
    v = plan_progress(PLAN, acts, date(2026, 11, 23))
    s = rows(v)
    assert (s[("2026-11-21", 0)]["activity_ids"], s[("2026-11-21", 0)]["status"]) == ([1], "done")
    assert (s[("2026-11-21", 1)]["activity_ids"], s[("2026-11-21", 1)]["status"]) == ([2], "partial")
    assert s[("2026-11-22", 0)]["activity_ids"] == [4]
    assert [e["_id"] for e in v["extras"]] == [3]


def test_strength_from_an_incomplete_app_log_counts_as_done():
    log = {"_id": "L1", "date": "2026-11-20", "completed": False, "duration_s": 1500,
           "sets": [{"skipped": False}] * 12 + [{"skipped": True}] * 2}
    r = rows(plan_progress(PLAN, [], date(2026, 11, 21), logs=[log]))[("2026-11-20", 0)]
    assert (r["status"], r["log_id"], r["done"], r["pct"]) == ("done", "L1", {"sets": 12, "of": 14, "duration_s": 1500}, 1.0)


def test_strength_shorter_than_target_counts_as_done_and_a_short_run_stays_partial():
    acts = [act(1, "2026-11-20", 0, "strength_training", secs=1200),   # 20 of 30 min
            act(2, "2026-11-19", 7.2)]                                   # 90% of the 8 km tempo
    r = rows(plan_progress(PLAN, acts, date(2026, 11, 21)))
    assert (r[("2026-11-20", 0)]["status"], r[("2026-11-20", 0)]["pct"]) == ("done", 1.0)
    assert (r[("2026-11-19", 0)]["status"], r[("2026-11-19", 0)]["pct"]) == ("partial", 0.9)


def test_adjustment_is_stored_once(mdb):
    s = {"date": "2026-11-19", "kind": "run"}
    assert save_adjustment("owner", s, {"key": "cut_long", "target": {"distance_m": 7000}})
    assert not save_adjustment("owner", s, {"key": "easy_week", "target": {"distance_m": 1}})
    assert mdb.plan_adjustments.find_one()["key"] == "cut_long"


def _seed(mdb):
    mdb.plans.insert_one({**PLAN, "user_id": "owner", "status": "active", "created_at": date(2026, 11, 1).isoformat()})
    mdb.activities.insert_many([{**act(11, "2026-11-16", 6), "user_id": "owner"},
                                {**act(12, "2026-11-16", 0, "strength_training", secs=1800), "user_id": "owner"}])


def test_override_endpoint_is_owner_only_validated_and_idempotent(api, mdb):
    _seed(mdb)
    body = {"date": "2026-11-17", "kind": "run", "activity_ids": [11]}
    assert api("viewer").post("/plan/override", json=body).status_code == 403
    owner = api("owner")
    assert owner.post("/plan/override", json={**body, "activity_ids": [12]}).status_code == 400  # strength for a run
    assert owner.post("/plan/override", json={**body, "date": "2026-11-18"}).status_code == 400  # no session that day
    assert owner.post("/plan/override", json={**body, "skipped": "sick"}).status_code == 400     # both
    for _ in range(2):
        r = owner.post("/plan/override", json=body)
        assert r.status_code == 200
    assert mdb.plan_overrides.count_documents({}) == 1
    s = {(x["date"], x["n"]): x for x in r.json()["sessions"]}
    assert s[("2026-11-17", 0)]["activity_ids"] == [11]
    taken = owner.post("/plan/override", json={"date": "2026-11-19", "kind": "run", "activity_ids": [11]})
    assert taken.status_code == 409
    r = owner.delete("/plan/override", params={"date": "2026-11-17", "kind": "run"})
    assert r.status_code == 200 and mdb.plan_overrides.count_documents({}) == 0


def test_past_plans_are_listed_and_readable(api, mdb):
    _seed(mdb)
    old = {**PLAN, "name": "Old", "start": "2026-10-12", "end": "2026-10-18", "sessions": PLAN["sessions"][:1],
           "user_id": "owner", "status": "replaced"}
    mdb.plans.insert_one({**old, "created_at": "2026-10-01"})
    mdb.plans.insert_one({**old, "name": "Old v0", "created_at": "2026-09-30"})  # an earlier revision of the same block
    c = api("viewer")
    lst = c.get("/plans").json()
    assert [p["name"] for p in lst] == ["Block", "Old"] and lst[0]["planned"] == 6
    v = c.get(f"/plans/{lst[1]['id']}").json()
    assert v["plan"]["name"] == "Old" and len(v["sessions"]) == 1
    assert c.get("/plans/nope").status_code == 404
