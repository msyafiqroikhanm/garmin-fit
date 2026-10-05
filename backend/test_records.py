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
