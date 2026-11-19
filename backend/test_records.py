from core import pr_history, similar_runs


def _run(i, date, **splits):
    return {"_id": i, "date": date, "name": f"r{i}", "raw": {f"fastestSplit_{d}": t for d, t in splits.items()}}


def test_pr_history_marks_only_new_bests_in_order():
    acts = [_run(1, "2026-01-01", **{"1000": 400, "5000": 2100}), _run(2, "2026-01-08", **{"1000": 410}),
            _run(3, "2026-01-15", **{"1000": 390, "5000": 2100})]
    marks, best = pr_history(acts)
    assert marks == {3: [1000]}
    assert best[1000]["activity_id"] == 3 and best[1000]["times_broken"] == 1
    assert best[5000]["activity_id"] == 1


def test_similar_runs_needs_three_matches_of_similar_distance():
    doc = {"distance_m": 10000, "avg_speed": 3.0, "avg_hr": 150}
    prev = [{"distance_m": 10500, "avg_speed": 2.8, "avg_hr": 155}, {"distance_m": 9000, "avg_speed": 2.9, "avg_hr": 152},
            {"distance_m": 21000, "avg_speed": 2.5, "avg_hr": 160}]
    assert similar_runs(doc, prev) is None
    prev.append({"distance_m": 11000, "avg_speed": 2.7, "avg_hr": 150})
    s = similar_runs(doc, prev)
    assert s["count"] == 3 and abs(s["avg_speed"] - 2.8) < 1e-9
    assert s["this_efficiency"] > s["efficiency"]


def test_similar_runs_prefer_the_same_plan_session_type():
    from core import similar_runs
    run = {"date": "2026-11-19", "distance_m": 6000, "avg_speed": 2.5, "avg_hr": 140}
    prev = [{"_id": i, "date": f"2026-11-{10 - i:02d}", "distance_m": 6000, "avg_speed": 3.0 if i % 2 else 2.4, "avg_hr": 150}
            for i in range(1, 9)]
    types = {"2026-11-19": "easy", **{p["date"]: "tempo" if p["_id"] % 2 else "easy" for p in prev}}
    s = similar_runs(run, prev, types)
    assert s["plan_type"] == "easy" and [r["_id"] for r in s["runs"]] == [2, 4, 6, 8]
    assert s["avg_speed"] == 2.4
    plain = similar_runs(run, prev)
    assert plain["plan_type"] is None and plain["count"] == 5
