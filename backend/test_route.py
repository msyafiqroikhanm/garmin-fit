from core import extract_route


def _details(n):
    # ~11.1 m per point, one point every 4 s ≈ 2.78 m/s
    return {"geoPolylineDTO": {"polyline": [{"lat": -6.0 + i * 1e-4, "lon": 106.0, "time": i * 4000} for i in range(n)]}}


def test_thins_and_keeps_endpoints():
    r = extract_route(_details(571))
    assert len(r) <= 301
    assert r[0][:2] == [-6.0, 106.0]
    assert r[-1][0] == round(-6.0 + 570 * 1e-4, 5)


def test_speed_smoothed_to_cm_s():
    r = extract_route(_details(50))
    assert all(270 <= p[2] <= 285 for p in r)


def test_no_gps():
    assert extract_route({"geoPolylineDTO": None}) is None
    assert extract_route({"geoPolylineDTO": {"polyline": [{"lat": None, "lon": None}]}}) is None
