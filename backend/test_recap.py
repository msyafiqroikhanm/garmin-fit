from datetime import date

from core import active_week_streak, home_routes, period_range, previous_key, recap


def test_period_range_and_previous():
    assert period_range("month", "2026-02") == (date(2026, 2, 1), date(2026, 2, 28))
    assert period_range("year", "2026") == (date(2026, 1, 1), date(2026, 12, 31))
    assert previous_key("month", "2026-01") == "2025-12"
    assert previous_key("year", "2026") == "2025"


def test_streak_counts_back_from_last_active_week():
    # Mondays 2026-09-14, 09-21, 09-28 active; the week of 10-05 has nothing yet
    dates = {"2026-09-15", "2026-09-22", "2026-09-30"}
    assert active_week_streak(dates, date(2026, 10, 5)) == 3
    assert active_week_streak(dates | {"2026-10-05"}, date(2026, 10, 5)) == 4
    assert active_week_streak({"2026-09-01"}, date(2026, 10, 5)) == 0


def test_recap_totals_buckets_and_prs():
    acts = [
        {"_id": 1, "date": "2026-10-01", "start_time": "2026-10-01 06:00", "type": "running", "distance_m": 5000, "duration_s": 1800},
        {"_id": 2, "date": "2026-10-02", "start_time": "2026-10-02 06:00", "type": "strength_training", "duration_s": 1200},
        {"_id": 3, "date": "2026-10-08", "start_time": "2026-10-08 06:00", "type": "running", "distance_m": 10000, "duration_s": 3600},
    ]
    r = recap("month", date(2026, 10, 1), date(2026, 10, 31), acts, [], {"2026-10-01"}, {3: [10000]})
    assert r["count"] == 3 and r["distance_m"] == 15000 and r["active_days"] == 3
    assert r["by_kind"] == {"run": 2, "strength": 1, "other": 0}
    assert r["longest_run"]["_id"] == 3
    assert [b["key"] for b in r["buckets"]][:2] == ["2026-09-28", "2026-10-05"]
    assert r["buckets"][0]["distance_m"] == 5000
    assert r["prs"] == [{"activity_id": 3, "date": "2026-10-08", "name": None, "distances": [10000]}]
    assert r["previous"]["count"] == 0


def test_home_routes_drops_far_starts_and_thins():
    home = [[[-6.17 + i * 1e-4, 106.73] for i in range(200)] for _ in range(3)]
    across_town = [[[-6.25, 106.80], [-6.26, 106.81]]]
    trip = [[[-7.80, 110.36], [-7.81, 110.37]]]
    out = home_routes(across_town + home + trip)
    assert len(out) == 3
    assert len(out[0]) <= 81 and out[0][-1] == home[0][-1]
