from core import insights


def _days(n, **overrides):
    return [{"date": f"d{i}", "hrv_last_night": 50 + (i % 3), "rhr": 50, **overrides} for i in range(n)]


def test_not_enough_data():
    assert insights(_days(5)) == {"baseline": {}, "flags": []}


def test_normal_has_no_flags():
    assert insights(_days(60))["flags"] == []


def test_hrv_drop_and_rhr_rise_flagged():
    days = _days(60)
    for d in days[-7:]:
        d["hrv_last_night"] = 35
    days[-1]["rhr"] = 58
    keys = {f["key"] for f in insights(days)["flags"]}
    assert keys == {"hrv_low", "rhr_high"}


def test_load_spike():
    days = _days(30)
    days[-1].update(acute_load=900, chronic_load=500)
    assert "load_spike" in {f["key"] for f in insights(days)["flags"]}
