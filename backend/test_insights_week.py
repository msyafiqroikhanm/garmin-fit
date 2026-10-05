from datetime import date

from core import merge_muscles, weekly_dynamics


def test_merge_muscles_sums_and_lists_untrained_groups():
    a = {"muscles": {"CHEST": {"primary": 3, "secondary": 0}, "TRICEPS": {"primary": 0, "secondary": 3}}, "total_sets": 5}
    b = {"muscles": {"CHEST": {"primary": 2, "secondary": 0}, "QUADS": {"primary": 4, "secondary": 0}}, "total_sets": 6}
    m = merge_muscles([a, b])
    assert m["muscles"]["CHEST"] == {"primary": 5, "secondary": 0}
    assert m["sets"] == 11 and m["sessions"] == 2
    # triceps only had secondary work, so arms still count as untrained
    assert m["untrained"] == ["BACK", "SHOULDERS", "ARMS", "CORE"]


def test_weekly_dynamics_weights_by_duration_and_keeps_empty_weeks():
    runs = [
        {"date": "2026-09-29", "duration_s": 3000, "raw": {"averageRunningCadenceInStepsPerMinute": 170}},
        {"date": "2026-10-01", "duration_s": 1000, "raw": {"averageRunningCadenceInStepsPerMinute": 180}},
    ]
    w = weekly_dynamics(runs, date(2026, 10, 5), 2)
    assert [r["week"] for r in w] == ["2026-09-28", "2026-10-05"]
    assert w[0]["cadence"] == 172.5 and w[0]["runs"] == 2
    assert w[1]["cadence"] is None and w[1]["runs"] == 0
