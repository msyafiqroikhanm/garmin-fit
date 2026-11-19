import hashlib
import itertools
import json
import math
import os
import re
import secrets
import statistics
import time
import urllib.request
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from cryptography.fernet import Fernet
from garminconnect import Garmin, GarminConnectTooManyRequestsError
from pymongo import MongoClient

import workout_data as wd

# ponytail: single Garmin account; add real user ids when family members bring their own watches
OWNER = "owner"
TZ = ZoneInfo(os.environ.get("APP_TZ", "Asia/Jakarta"))

_db = None


def db():
    global _db
    if _db is None:
        _db = MongoClient(os.environ["MONGODB_URI"])[os.environ.get("MONGODB_DB", "garmin_fit")]
    return _db


def ensure_indexes() -> None:
    """Run from the CLI after a schema change; kept off the request path so cold starts skip the round trips."""
    d = db()
    d.daily.create_index([("user_id", 1), ("date", 1)])
    d.activities.create_index([("user_id", 1), ("date", -1)])
    d.activities.create_index([("user_id", 1), ("start_time", 1)])
    d.activities.create_index([("user_id", 1), ("type", 1), ("start_time", 1)])
    d.summaries.create_index([("user_id", 1), ("period", 1), ("generated_at", -1)])
    d.plans.create_index([("user_id", 1), ("status", 1), ("created_at", -1)])
    d.plan_overrides.create_index([("user_id", 1), ("date", 1)])
    d.plan_adjustments.create_index([("user_id", 1), ("date", 1)])
    d.users.create_index("google_sub", unique=True)
    d.users.create_index("access_id")
    d.sessions.create_index("user_id")
    d.workout_logs.create_index([("user_id", 1), ("start_time", -1)])
    d.workout_logs.create_index([("user_id", 1), ("activity_id", 1)])


def create_access_token(name: str, role: str, data_user_id: str = OWNER) -> tuple[str, str]:
    """Returns (token, id). The token is shown once; only its hash is stored and doubles as the id."""
    token = secrets.token_urlsafe(32)
    db().access_tokens.insert_one({"_id": sha256(token), "name": name, "role": role, "data_user_id": data_user_id,
                                   "created_at": datetime.now(timezone.utc), "revoked": False})
    return token, sha256(token)


def identity(user: dict, access: dict | None, session_id: str) -> dict:
    """A signed-in user seen through the access token linked to them; role and data_user_id stay None until they add one."""
    return {
        "kind": "session",
        "session_id": session_id,
        "user_id": user["_id"],
        "name": user.get("name"),
        "email": user.get("email"),
        "access_id": access["_id"] if access else None,
        "role": access["role"] if access else None,
        "data_user_id": access["data_user_id"] if access else None,
    }


def invite_rows(tokens: list[dict], users: list[dict]) -> list[dict]:
    """Owner's Family access list: each live token with the accounts that added it, newest first."""
    by_token: dict[str, list[str]] = {}
    for u in users:
        by_token.setdefault(u.get("access_id"), []).append(u.get("name") or u.get("email") or "?")
    rows = [{"id": t["_id"], "name": t["name"], "role": t["role"], "created_at": t.get("created_at"),
             "last_used": t.get("last_used"), "users": sorted(by_token.get(t["_id"], []))}
            for t in tokens if not t.get("revoked")]
    return sorted(rows, key=lambda r: r["created_at"] or datetime.min, reverse=True)


def today() -> date:
    return datetime.now(TZ).date()


def sha256(s: str) -> str:
    return hashlib.sha256(s.encode()).hexdigest()


def _fernet() -> Fernet:
    return Fernet(os.environ["FERNET_KEY"])


def save_session(user_id: str, token_json: str) -> None:
    db().garmin_sessions.replace_one(
        {"_id": user_id},
        {"token_enc": _fernet().encrypt(token_json.encode()).decode(), "updated_at": datetime.now(timezone.utc)},
        upsert=True,
    )


def garmin_client(user_id: str) -> tuple[Garmin, str]:
    doc = db().garmin_sessions.find_one({"_id": user_id})
    if not doc:
        raise RuntimeError(f"no Garmin session for {user_id}, run `cli.py login` first")
    token = _fernet().decrypt(doc["token_enc"].encode()).decode()
    g = Garmin()
    g.login(tokenstore=token)
    return g, token


def persist_if_refreshed(g: Garmin, user_id: str, old_token: str) -> None:
    new = g.client.dumps()
    if new != old_token:
        save_session(user_id, new)


def dig(d, *keys):
    for k in keys:
        if isinstance(d, dict):
            d = d.get(k)
        elif isinstance(d, list) and isinstance(k, int) and len(d) > k:
            d = d[k]
        else:
            return None
    return d


def _first_value(d):
    return next(iter(d.values()), None) if isinstance(d, dict) and d else None


def extract_daily(raw: dict) -> dict:
    stats, hrv, sleep, ready, ts = (raw.get(k) or {} for k in ("stats", "hrv", "sleep", "readiness", "training_status"))
    if isinstance(ready, list):
        ready = ready[0] if ready else {}
    status = _first_value(dig(ts, "mostRecentTrainingStatus", "latestTrainingStatusData")) or {}
    acute = status.get("acuteTrainingLoadDTO") or {}
    phrase = status.get("trainingStatusFeedbackPhrase")
    sleep_dto = sleep.get("dailySleepDTO") or {}
    return {
        "rhr": stats.get("restingHeartRate"),
        "max_hr": stats.get("maxHeartRate"),
        "stress_avg": stats.get("averageStressLevel"),
        "bb_high": stats.get("bodyBatteryHighestValue"),
        "bb_low": stats.get("bodyBatteryLowestValue"),
        "bb_now": stats.get("bodyBatteryMostRecentValue"),
        "steps": stats.get("totalSteps"),
        "hrv_last_night": dig(hrv, "hrvSummary", "lastNightAvg"),
        "hrv_weekly_avg": dig(hrv, "hrvSummary", "weeklyAvg"),
        "hrv_status": dig(hrv, "hrvSummary", "status"),
        "hrv_balanced_low": dig(hrv, "hrvSummary", "baseline", "balancedLow"),
        "hrv_balanced_high": dig(hrv, "hrvSummary", "baseline", "balancedUpper"),
        "sleep_score": dig(sleep_dto, "sleepScores", "overall", "value"),
        "sleep_s": sleep_dto.get("sleepTimeSeconds"),
        "deep_s": sleep_dto.get("deepSleepSeconds"),
        "rem_s": sleep_dto.get("remSleepSeconds"),
        "light_s": sleep_dto.get("lightSleepSeconds"),
        "awake_s": sleep_dto.get("awakeSleepSeconds"),
        "readiness": ready.get("score"),
        "recovery_time_min": ready.get("recoveryTime"),
        "step_goal": stats.get("dailyStepGoal"),
        "readiness_level": ready.get("level"),
        "training_status": re.sub(r"_\d+$", "", phrase) if phrase else None,
        "acute_load": acute.get("dailyTrainingLoadAcute"),
        "chronic_load": acute.get("dailyTrainingLoadChronic"),
        "vo2max": dig(ts, "mostRecentVO2Max", "generic", "vo2MaxPreciseValue")
        or dig(ts, "mostRecentVO2Max", "generic", "vo2MaxValue"),
    }


def extract_activity(a: dict, user_id: str) -> dict:
    zones = [a.get(f"hrTimeInZone_{i}") for i in range(1, 6)]
    start = a.get("startTimeLocal") or ""
    return {
        "_id": a["activityId"],
        "user_id": user_id,
        "date": start[:10],
        "start_time": start,
        "type": dig(a, "activityType", "typeKey"),
        "name": a.get("activityName"),
        "distance_m": a.get("distance"),
        "duration_s": a.get("duration"),
        "avg_hr": a.get("averageHR"),
        "max_hr": a.get("maxHR"),
        "avg_speed": a.get("averageSpeed"),
        "calories": a.get("calories"),
        "elevation_gain": a.get("elevationGain"),
        "aerobic_te": a.get("aerobicTrainingEffect"),
        "anaerobic_te": a.get("anaerobicTrainingEffect"),
        "training_load": a.get("activityTrainingLoad"),
        "hr_zones_s": zones if any(z is not None for z in zones) else None,
        "raw": a,
    }


DAY_SOURCES = {
    "stats": "get_stats",
    "hrv": "get_hrv_data",
    "sleep": "get_sleep_data",
    "readiness": "get_training_readiness",
    "training_status": "get_training_status",
}


def sync_day(g: Garmin, user_id: str, d: date) -> list[str]:
    iso = d.isoformat()
    raw, errors = {}, []
    for key, method in DAY_SOURCES.items():
        try:
            raw[key] = getattr(g, method)(iso)
        except GarminConnectTooManyRequestsError:
            raise
        except Exception as e:
            errors.append(f"{iso} {key}: {e}")
    db().daily.replace_one(
        {"_id": f"{user_id}:{iso}"},
        {"user_id": user_id, "date": iso, **extract_daily(raw), "raw": raw, "synced_at": datetime.now(timezone.utc)},
        upsert=True,
    )
    return errors


def extract_splits(raw: dict) -> list[dict]:
    return [{"distance_m": lap.get("distance"), "duration_s": lap.get("duration"), "avg_hr": lap.get("averageHR")}
            for lap in raw.get("lapDTOs") or []]


ROUTE_POINTS = 300


def _haversine_m(a: dict, b: dict) -> float:
    la1, lo1, la2, lo2 = map(math.radians, (a["lat"], a["lon"], b["lat"], b["lon"]))
    h = math.sin((la2 - la1) / 2) ** 2 + math.cos(la1) * math.cos(la2) * math.sin((lo2 - lo1) / 2) ** 2
    return 2 * 6_371_000 * math.asin(math.sqrt(h))


def extract_route(details: dict) -> list[list] | None:
    """[[lat, lon, speed_cm_s], ...], speed smoothed over 5 points, thinned to ~ROUTE_POINTS."""
    pts = [p for p in dig(details, "geoPolylineDTO", "polyline") or [] if p.get("lat") is not None and p.get("lon") is not None]
    if len(pts) < 2:
        return None
    # Garmin leaves polyline speed at 0, so derive it from distance over time across a 9-point window
    seg = [0.0] + [_haversine_m(pts[i - 1], pts[i]) for i in range(1, len(pts))]
    cum = list(itertools.accumulate(seg))
    smooth = []
    for i in range(len(pts)):
        a, b = max(0, i - 4), min(len(pts) - 1, i + 4)
        dt = ((pts[b].get("time") or 0) - (pts[a].get("time") or 0)) / 1000
        smooth.append((cum[b] - cum[a]) / dt if dt > 0 else 0)
    # ponytail: uniform stride, Douglas-Peucker if sharp corners get cut
    step = max(1, -(-len(pts) // ROUTE_POINTS))
    keep = list(range(0, len(pts), step))
    if keep[-1] != len(pts) - 1:
        keep.append(len(pts) - 1)
    return [[round(pts[i]["lat"], 5), round(pts[i]["lon"], 5), round(smooth[i] * 100)] for i in keep]


SERIES_POINTS = 200
SERIES_KEYS = {
    "hr": "directHeartRate",
    "speed": "directSpeed",
    "cadence": "directDoubleCadence",
    "power": "directPower",
    "elev": "directElevation",
}


def extract_series(details: dict) -> dict | None:
    """Time series averaged into ~SERIES_POINTS buckets: t in timer seconds, speed in cm/s, elev in dm."""
    idx = {d["key"]: d["metricsIndex"] for d in details.get("metricDescriptors") or []}
    rows = [r.get("metrics") or [] for r in details.get("activityDetailMetrics") or []]
    if "sumDuration" not in idx or len(rows) < 2:
        return None

    def col(key):
        i = idx.get(key)
        return [r[i] if i is not None and i < len(r) else None for r in rows]

    t = col("sumDuration")
    raw = {name: col(key) for name, key in SERIES_KEYS.items() if key in idx}
    # a stopped watch reports 0 for speed, cadence and power; that is a gap, not a reading
    for name in ("speed", "cadence", "power", "hr"):
        if name in raw:
            raw[name] = [v if v else None for v in raw[name]]
    size = max(1, -(-len(rows) // SERIES_POINTS))
    scale = {"speed": 100, "elev": 10}
    out = {"t": []}
    for name in raw:
        out[name] = []
    for start in range(0, len(rows), size):
        end = min(len(rows), start + size)
        out["t"].append(round(next((x for x in reversed(t[start:end]) if x is not None), 0)))
        for name, vals in raw.items():
            vs = [v for v in vals[start:end] if v is not None]
            out[name].append(round(statistics.fmean(vs) * scale.get(name, 1)) if vs else None)
    return {k: v for k, v in out.items() if k == "t" or any(x is not None for x in v)}


DETAILS_V = 2  # bump to refetch details for every activity when the stored shape changes


def ensure_details(g: Garmin, doc: dict) -> dict:
    """Fetch Garmin's per-activity details once and keep only the route and a compact time series."""
    if doc.get("details_v") == DETAILS_V:
        return doc
    try:
        details = g.get_activity_details(doc["_id"], maxchart=2000, maxpoly=4000)
        route = extract_route(details) if dig(doc, "raw", "hasPolyline") else None
        series = extract_series(details)
    except GarminConnectTooManyRequestsError:
        raise
    except Exception:
        return doc  # not marked as fetched, so the next sync or detail view tries again
    fields = {"route": route, "series": series, "details_v": DETAILS_V}
    doc.update(fields)
    db().activities.update_one({"_id": doc["_id"]}, {"$set": fields, "$unset": {"route_checked": 1}})
    return doc


CATALOG_URL = "https://connect.garmin.com/web-data/exercises/Exercises.json"
CATALOG_MAX_AGE = timedelta(days=30)


def _muscles(entry: dict) -> dict | None:
    p, s = [m for m in entry.get("primaryMuscles") or [] if m], [m for m in entry.get("secondaryMuscles") or [] if m]
    return {"p": p, "s": s} if p or s else None


def trim_catalog(raw: dict) -> dict:
    out = {}
    for cat, v in (raw.get("categories") or {}).items():
        ex = {name: m for name, e in (v.get("exercises") or {}).items() if (m := _muscles(e))}
        out[cat] = {**(_muscles(v) or {"p": [], "s": []}), "ex": ex}
    return out


def muscle_catalog() -> dict:
    """Garmin's public exercise catalog reduced to category/exercise -> muscles, cached in Mongo for 30 days."""
    doc = db().meta.find_one({"_id": "exercise_catalog"})
    now = datetime.now(timezone.utc)
    if doc and now - doc["fetched_at"].replace(tzinfo=timezone.utc) < CATALOG_MAX_AGE:
        return doc["data"]
    try:
        req = urllib.request.Request(CATALOG_URL, headers={"User-Agent": "Mozilla/5.0"})
        data = trim_catalog(json.load(urllib.request.urlopen(req, timeout=10)))
    except Exception:
        return doc["data"] if doc else {}
    db().meta.replace_one({"_id": "exercise_catalog"}, {"data": data, "fetched_at": now}, upsert=True)
    return data


def strength_summary(raw: dict, catalog: dict) -> dict | None:
    """Per-exercise rows and set counts per muscle from Garmin's summarizedExerciseSets (auto-detected on the watch)."""
    rows = raw.get("summarizedExerciseSets") or []
    if not rows:
        return None
    exercises, muscles = [], {}
    for r in rows:
        cat, sub, sets = r.get("category") or "UNKNOWN", r.get("subCategory"), r.get("sets") or 0
        exercises.append({"category": cat, "sub": sub, "sets": sets, "reps": r.get("reps") or 0,
                          "duration_s": (r.get("duration") or 0) / 1000})
        c = catalog.get(cat) or {}
        m = (c.get("ex") or {}).get(sub) or c
        for kind, key in (("p", "primary"), ("s", "secondary")):
            for name in m.get(kind) or []:
                muscles.setdefault(name, {"primary": 0, "secondary": 0})[key] += sets
    exercises.sort(key=lambda e: (e["category"] == "UNKNOWN", -e["sets"]))
    return {"exercises": exercises, "muscles": muscles, "total_sets": raw.get("activeSets") or raw.get("totalSets"),
            "total_reps": raw.get("totalReps")}


EXTRA_FIELDS = {
    "elapsed_s": "elapsedDuration",
    "moving_s": "movingDuration",
    "max_speed": "maxSpeed",
    "gap_speed": "avgGradeAdjustedSpeed",
    "cadence": "averageRunningCadenceInStepsPerMinute",
    "cadence_max": "maxRunningCadenceInStepsPerMinute",
    "stride_cm": "avgStrideLength",
    "gct_ms": "avgGroundContactTime",
    "vo_cm": "avgVerticalOscillation",
    "vr_pct": "avgVerticalRatio",
    "power": "avgPower",
    "power_max": "maxPower",
    "elevation_loss": "elevationLoss",
    "steps": "steps",
    "bb_delta": "differenceBodyBattery",
    "water_ml": "waterEstimated",
    "moderate_min": "moderateIntensityMinutes",
    "vigorous_min": "vigorousIntensityMinutes",
    "te_label": "trainingEffectLabel",
    "vo2max": "vO2MaxValue",
}
BEST_SPLITS = (1000, 1609, 5000, 10000, 21098, 42195)


def activity_extra(raw: dict) -> dict:
    """Summary fields the detail screen shows beyond the stored basics, read straight from Garmin's summary."""
    out = {k: raw[src] for k, src in EXTRA_FIELDS.items() if raw.get(src) not in (None, "", 0)}
    best = [{"distance_m": d, "duration_s": raw[f"fastestSplit_{d}"]} for d in BEST_SPLITS if raw.get(f"fastestSplit_{d}")]
    if best:
        out["best_splits"] = best
    return out


def pr_history(acts: list[dict]) -> tuple[dict, dict]:
    """acts sorted by start_time. Returns ({activity_id: [distances it set a new best for]},
    {distance: current best as {duration_s, activity_id, date, name, times_broken}})."""
    marks, best = {}, {}
    for a in acts:
        for d in BEST_SPLITS:
            t = dig(a, "raw", f"fastestSplit_{d}")
            if not t or (d in best and t >= best[d]["duration_s"]):
                continue
            broken = best[d]["times_broken"] + 1 if d in best else 0
            best[d] = {"distance_m": d, "duration_s": t, "activity_id": a["_id"], "date": a["date"], "name": a.get("name"),
                       "times_broken": broken}
            if broken:  # the first run with a split is the baseline, not a record worth a badge
                marks.setdefault(a["_id"], []).append(d)
    return marks, best


def refresh_prs(user_id: str) -> tuple[dict, dict]:
    """Recompute PR history and store it, so request handlers skip the scan over every run."""
    marks, best = pr_history(runs_with_splits(user_id))
    # Mongo keys must be strings, so marks are stored as pairs
    db().meta.replace_one({"_id": f"pr:{user_id}"}, {"marks": [[k, v] for k, v in marks.items()], "best": list(best.values()),
                                                     "at": datetime.now(timezone.utc)}, upsert=True)
    return marks, best


def prs(user_id: str) -> tuple[dict, dict]:
    doc = db().meta.find_one({"_id": f"pr:{user_id}"})
    if not doc:
        return refresh_prs(user_id)
    return {k: v for k, v in doc["marks"]}, {b["distance_m"]: b for b in doc["best"]}


def runs_with_splits(user_id: str) -> list[dict]:
    proj = {"date": 1, "name": 1, "start_time": 1, **{f"raw.fastestSplit_{d}": 1 for d in BEST_SPLITS}}
    return list(db().activities.find({"user_id": user_id, "type": {"$regex": "running"}}, proj).sort("start_time", 1))


SIMILAR_RUNS = 5
SIMILAR_MIN = 3


def similar_runs(doc: dict, previous: list[dict], types: dict | None = None) -> dict | None:
    """Compare a run with the latest earlier runs of similar distance (+-25%). types maps a date to the plan session
    type run that day; when this run fulfilled one (an easy run, a tempo), runs of the same session type are
    preferred so an easy run is not judged against tempos."""
    dist, speed, hr = doc.get("distance_m") or 0, doc.get("avg_speed"), doc.get("avg_hr")
    if not dist or not speed:
        return None
    near = [p for p in previous if p.get("avg_speed") and abs((p.get("distance_m") or 0) - dist) <= dist * 0.25]
    kind = (types or {}).get(doc.get("date"))
    same = [p for p in near if kind and (types or {}).get(p.get("date")) == kind]
    match = (same if len(same) >= SIMILAR_MIN else near)[:SIMILAR_RUNS]
    if len(match) < SIMILAR_MIN:
        return None
    avg_speed = statistics.fmean(p["avg_speed"] for p in match)
    hrs = [p["avg_hr"] for p in match if p.get("avg_hr")]
    avg_hr = statistics.fmean(hrs) if hrs else None
    effs = [p["avg_speed"] * 60 / p["avg_hr"] for p in match if p.get("avg_hr")]
    return {
        "count": len(match),
        "avg_speed": avg_speed,
        "avg_hr": avg_hr,
        # meters per minute per heartbeat: higher means the same effort moves you further
        "efficiency": statistics.fmean(effs) if effs else None,
        "this_efficiency": speed * 60 / hr if hr else None,
        "plan_type": kind if len(same) >= SIMILAR_MIN else None,
        "runs": [{k: p.get(k) for k in ("_id", "date", "start_time", "name", "type", "distance_m", "duration_s", "avg_speed", "avg_hr")}
                 for p in match],
    }


def plan_run_types(user_id: str) -> dict:
    """date -> the type of the run planned that day, across every saved block (the newest revision wins)."""
    out = {}
    for p in db().plans.find({"user_id": user_id}, {"sessions.date": 1, "sessions.kind": 1, "sessions.type": 1}).sort("created_at", 1):
        for s in p["sessions"]:
            if s["kind"] == "run":
                out[s["date"]] = s["type"]
    return out


def period_range(period: str, key: str) -> tuple[date, date]:
    """'month' + 'YYYY-MM', 'year' + 'YYYY' or 'range' + 'YYYY-MM-DD..YYYY-MM-DD' (a plan block) -> first and last day."""
    if period == "range":
        a, b = key.split("..")
        start, end = date.fromisoformat(a), date.fromisoformat(b)
        if start > end or (end - start).days > 400:
            raise ValueError("bad range")
        return start, end
    if period == "year":
        y = int(key[:4])
        return date(y, 1, 1), date(y, 12, 31)
    y, m = int(key[:4]), int(key[5:7])
    nxt = date(y + 1, 1, 1) if m == 12 else date(y, m + 1, 1)
    return date(y, m, 1), nxt - timedelta(days=1)


def previous_key(period: str, key: str) -> str:
    start, end = period_range(period, key)
    prev = (start - timedelta(days=1))
    if period == "range":
        return f"{(start - (end - start) - timedelta(days=1)).isoformat()}..{prev.isoformat()}"
    return str(prev.year) if period == "year" else prev.isoformat()[:7]


def compare_range(period: str, key: str, today_: date) -> tuple[date, date]:
    """The previous period to compare with. A period still in progress is compared with the same number of days at
    the start of the previous one (1-19 Oct for 1-19 Nov), so a part month never looks short against a whole one."""
    start, end = period_range(period, key)
    pstart, pend = period_range(period, previous_key(period, key))
    if start <= today_ < end:
        pend = min(pend, pstart + (today_ - start))
    return pstart, pend


def _kind(t: str | None) -> str:
    t = t or ""
    return "run" if "running" in t else "strength" if t == "strength_training" else "other"


def period_totals(acts: list[dict]) -> dict:
    by = {"run": 0, "strength": 0, "other": 0}
    for a in acts:
        by[_kind(a.get("type"))] += 1
    run_m = sum(a.get("distance_m") or 0 for a in acts if _kind(a.get("type")) == "run")
    return {
        "count": len(acts),
        "distance_m": sum(a.get("distance_m") or 0 for a in acts),
        # running is the headline; rides and walks are told apart so the km mean one thing
        "run_distance_m": run_m,
        "duration_s": sum(a.get("duration_s") or 0 for a in acts),
        "active_days": len({a["date"] for a in acts}),
        "by_kind": by,
    }


def daily_load(acts: list[dict], dates: list[str]) -> list[dict]:
    """Training load per day, with each day's split by sport (oldest activity first) so a double day reads as run + strength."""
    out = []
    for d in dates:
        day = sorted((a for a in acts if a["date"] == d), key=lambda a: a.get("start_time") or "")
        parts = [{"kind": _kind(a.get("type")), "load": round(a.get("training_load") or 0)} for a in day if a.get("training_load")]
        # the total is the sum of the rounded parts, so "56 · run 44 + strength 12" always adds up
        out.append({"date": d, "load": sum(p["load"] for p in parts), "parts": parts})
    return out


def week_start(d: date) -> date:
    return d - timedelta(days=d.weekday())


def active_week_streak(dates: set[str], end: date) -> int:
    """Consecutive Monday-start weeks with at least one activity, counting back from the week of `end`
    (the current week counts only once it has an activity)."""
    weeks = {week_start(date.fromisoformat(d)) for d in dates}
    w = week_start(end)
    if w not in weeks:
        w -= timedelta(days=7)
    n = 0
    while w in weeks:
        n += 1
        w -= timedelta(days=7)
    return n


def recap(period: str, start: date, end: date, acts: list[dict], prev: list[dict], all_dates: set[str],
          marks: dict) -> dict:
    """acts: activities inside [start, end]; prev: the previous period's; all_dates: every activity date up to end."""
    runs = [a for a in acts if _kind(a.get("type")) == "run" and a.get("distance_m")]
    longest = max(runs, key=lambda a: a["distance_m"], default=None)
    buckets = {}
    for a in acts:
        d = date.fromisoformat(a["date"])
        k = d.isoformat()[:7] if period == "year" else week_start(d).isoformat()
        b = buckets.setdefault(k, {"key": k, "distance_m": 0, "run_distance_m": 0, "duration_s": 0, "count": 0})
        b["distance_m"] += a.get("distance_m") or 0
        if _kind(a.get("type")) == "run":
            b["run_distance_m"] += a.get("distance_m") or 0
        b["duration_s"] += a.get("duration_s") or 0
        b["count"] += 1
    if period == "year":
        keys = [f"{start.year}-{m:02d}" for m in range(1, 13)]
    else:
        keys, w = [], week_start(start)
        while w <= end:
            keys.append(w.isoformat())
            w += timedelta(days=7)
    return {
        "period": period,
        "start": start.isoformat(),
        "end": end.isoformat(),
        **period_totals(acts),
        "previous": period_totals(prev),
        "longest_run": {k: longest.get(k) for k in ("_id", "date", "name", "distance_m", "duration_s")} if longest else None,
        "streak_weeks": active_week_streak(all_dates, min(end, today())),
        "buckets": [buckets.get(k, {"key": k, "distance_m": 0, "run_distance_m": 0, "duration_s": 0, "count": 0}) for k in keys],
        "prs": [{"activity_id": a["_id"], "date": a["date"], "name": a.get("name"), "distances": marks[a["_id"]]}
                for a in sorted(acts, key=lambda a: a["start_time"]) if a["_id"] in marks],
    }


HEATMAP_POINTS = 80
HOME_RADIUS_M = 3_000


def home_routes(routes: list[list[list]]) -> list[list[list]]:
    """Routes thinned to [[lat, lon]] and limited to those starting within 3 km of the busiest start
    (the start with the most other starts nearby), so runs elsewhere do not spread the map thin."""
    routes = [r for r in routes if r and len(r) > 1]
    if not routes:
        return []
    starts = [{"lat": r[0][0], "lon": r[0][1]} for r in routes]
    # ponytail: O(n^2) over starts, fine for a few hundred routes per period
    centre = max(starts, key=lambda a: sum(_haversine_m(a, b) <= HOME_RADIUS_M for b in starts))
    out = []
    for r, st in zip(routes, starts):
        if _haversine_m(centre, st) > HOME_RADIUS_M:
            continue
        step = max(1, -(-len(r) // HEATMAP_POINTS))
        pts = r[::step] + ([r[-1]] if (len(r) - 1) % step else [])
        out.append([[p[0], p[1]] for p in pts])
    return out


PREDICTION_FIELDS = {"5k": "time5K", "10k": "time10K", "half": "timeHalfMarathon", "marathon": "timeMarathon"}


def store_predictions(user_id: str, rows: list[dict] | dict) -> int:
    rows = rows if isinstance(rows, list) else [rows]
    n = 0
    for r in rows:
        d = r.get("calendarDate")
        if not d:
            continue
        db().race_predictions.replace_one(
            {"_id": f"{user_id}:{d}"},
            {"user_id": user_id, "date": d, **{k: r.get(src) for k, src in PREDICTION_FIELDS.items()}},
            upsert=True,
        )
        n += 1
    return n


def backfill_predictions(user_id: str, start: date, end: date, log=print) -> int:
    """Garmin serves daily predictions for at most a year per call, so walk the range in yearly windows."""
    g, token = garmin_client(user_id)
    n, a = 0, start
    try:
        while a <= end:
            b = min(end, a + timedelta(days=364))
            n += store_predictions(user_id, g.get_race_predictions(a.isoformat(), b.isoformat(), "daily") or [])
            log(f"predictions {a}..{b}: {n}")
            a = b + timedelta(days=1)
    finally:
        persist_if_refreshed(g, user_id, token)
    return n


BIG_GROUPS = {
    "CHEST": ("CHEST",),
    "BACK": ("LATS", "TRAPS", "LOWER_BACK"),
    "SHOULDERS": ("SHOULDERS",),
    "ARMS": ("BICEPS", "TRICEPS", "FOREARM"),
    "CORE": ("ABS", "OBLIQUES"),
    "LEGS": ("QUADS", "HAMSTRINGS", "GLUTES", "CALVES", "ADDUCTORS", "ABDUCTORS", "HIPS"),
}


def merge_muscles(summaries: list[dict]) -> dict:
    """Sum per-muscle sets over several strength sessions and list the big groups with no primary work."""
    muscles = {}
    for s in summaries:
        for k, v in (s.get("muscles") or {}).items():
            m = muscles.setdefault(k, {"primary": 0, "secondary": 0})
            m["primary"] += v["primary"]
            m["secondary"] += v["secondary"]
    untrained = [g for g, keys in BIG_GROUPS.items() if not any(muscles.get(k, {}).get("primary") for k in keys)]
    return {"sessions": len(summaries), "muscles": muscles, "untrained": untrained,
            "sets": sum(s.get("total_sets") or 0 for s in summaries)}


DYNAMICS_FIELDS = {"cadence": "averageRunningCadenceInStepsPerMinute", "gct_ms": "avgGroundContactTime",
                   "vr_pct": "avgVerticalRatio", "vo_cm": "avgVerticalOscillation", "power": "avgPower"}


def weekly_dynamics(runs: list[dict], end: date, weeks: int) -> list[dict]:
    """Duration-weighted weekly averages of running dynamics, oldest week first; empty weeks keep None."""
    first = week_start(end) - timedelta(days=7 * (weeks - 1))
    out = []
    for i in range(weeks):
        ws = first + timedelta(days=7 * i)
        we = ws + timedelta(days=6)
        inside = [r for r in runs if ws.isoformat() <= r["date"] <= we.isoformat()]
        row = {"week": ws.isoformat(), "runs": len(inside)}
        for name, src in DYNAMICS_FIELDS.items():
            pairs = [(dig(r, "raw", src), r.get("duration_s") or 0) for r in inside if dig(r, "raw", src)]
            total = sum(w for _, w in pairs)
            row[name] = sum(v * w for v, w in pairs) / total if total else None
        out.append(row)
    return out


KEEP_ON_RESYNC = ("splits", "route", "series", "details_v")


def sync_activity(g: Garmin, doc: dict) -> bool:
    """Upsert one activity, keeping fetched extras. Returns True if Garmin was called beyond the list."""
    old = db().activities.find_one({"_id": doc["_id"]}, dict.fromkeys(KEEP_ON_RESYNC, 1)) or {}
    doc.update({k: old[k] for k in KEEP_ON_RESYNC if k in old})
    fetched = False
    if "running" in (doc["type"] or "") and not doc.get("splits"):
        doc["splits"] = extract_splits(g.get_activity_splits(doc["_id"]))
        fetched = True
    db().activities.replace_one({"_id": doc["_id"]}, doc, upsert=True)
    if doc.get("details_v") != DETAILS_V:
        ensure_details(g, doc)
        fetched = True
    return fetched


def sync_activities(g: Garmin, user_id: str, start: date, end: date, pause=0.0, log=print, backoff=()) -> int:
    acts = g.get_activities_by_date(start.isoformat(), end.isoformat())
    for a in acts:
        doc = extract_activity(a, user_id)
        if _with_backoff(lambda: sync_activity(g, doc), log, backoff):
            time.sleep(pause)
    return len(acts)


def sync_profile(g: Garmin, user_id: str) -> None:
    try:
        store_predictions(user_id, g.get_race_predictions() or {})
    except GarminConnectTooManyRequestsError:
        raise
    except Exception:
        pass  # predictions are a nice-to-have; the profile below matters more
    ud = (g.get_user_profile() or {}).get("userData") or {}
    zones = next(iter(g.get_heart_rate_zones() or []), {})
    floors = [zones.get(f"zone{i}Floor") for i in range(1, 6)]
    db().profile.replace_one({"_id": user_id}, {
        "gender": ud.get("gender"),
        "birth_date": ud.get("birthDate"),
        "hr_zone_floors": floors if all(floors) else None,
        "max_hr": zones.get("maxHeartRateUsed"),
    }, upsert=True)


def sync_range(user_id: str, start: date, end: date, skip_existing=False, pause=0.0, log=print,
               backoff=(60, 180, 600)) -> list[str]:
    g, token = garmin_client(user_id)
    errors = []
    try:
        n = sync_activities(g, user_id, start, end, pause, log, backoff)
        log(f"activities {start}..{end}: {n}")
        refresh_prs(user_id)
        try:
            link_workouts(user_id, start.isoformat(), end.isoformat())
        except Exception as e:
            errors.append(f"workouts: {e}")
        try:
            sync_profile(g, user_id)
        except GarminConnectTooManyRequestsError:
            raise
        except Exception as e:
            errors.append(f"profile: {e}")
        existing = set()
        if skip_existing:
            existing = {x["date"] for x in db().daily.find({"user_id": user_id}, {"date": 1})}
        d = start
        while d <= end:
            if d.isoformat() not in existing:
                errs = _with_backoff(lambda: sync_day(g, user_id, d), log, backoff)
                errors += errs
                log(f"{d} ok" + (f" ({len(errs)} errors)" if errs else ""))
                time.sleep(pause)
            d += timedelta(days=1)
    finally:
        persist_if_refreshed(g, user_id, token)
        db().sync_log.insert_one(
            {"user_id": user_id, "ran_at": datetime.now(timezone.utc), "range": [start.isoformat(), end.isoformat()],
             "ok": not errors, "errors": errors[:50]}
        )
    return errors


def _with_backoff(fn, log, waits):
    for w in waits:
        try:
            return fn()
        except GarminConnectTooManyRequestsError:
            log(f"429 from Garmin, waiting {w}s")
            time.sleep(w)
    return fn()


HRV_BASELINE_DAYS = 60
HRV_RECENT_DAYS = 7
HRV_SD = 1.0
RHR_BASELINE_DAYS = 30
RHR_DELTA_BPM = 5
LOAD_RATIO = 1.3
MIN_DAYS = 14


def _vals(days, key):
    return [d[key] for d in days if d.get(key) is not None]


def insights(days: list[dict]) -> dict:
    """days sorted by date ascending, last item is the most recent day."""
    baseline, flags = {}, []
    if len(days) < MIN_DAYS:
        return {"baseline": baseline, "flags": flags}
    latest = days[-1]

    hrv_base = _vals(days[-HRV_BASELINE_DAYS:], "hrv_last_night")
    hrv_recent = _vals(days[-HRV_RECENT_DAYS:], "hrv_last_night")
    if len(hrv_base) >= MIN_DAYS and hrv_recent:
        mean, sd, recent = statistics.fmean(hrv_base), statistics.pstdev(hrv_base), statistics.fmean(hrv_recent)
        baseline["hrv"] = {"mean": round(mean, 1), "sd": round(sd, 1), "recent_avg": round(recent, 1)}
        if recent < mean - HRV_SD * sd:
            flags.append({"key": "hrv_low", "level": "warn", "text": "HRV 7 hari terakhir di bawah rentang normal. Tubuh butuh pemulihan lebih."})
        elif recent > mean + HRV_SD * sd:
            flags.append({"key": "hrv_high", "level": "good", "text": "HRV 7 hari terakhir di atas rentang normal. Adaptasi latihanmu jalan."})

    rhr_base = _vals(days[-RHR_BASELINE_DAYS - 1:-1], "rhr")
    if len(rhr_base) >= MIN_DAYS:
        mean = statistics.fmean(rhr_base)
        baseline["rhr"] = {"mean": round(mean, 1)}
        if latest.get("rhr") is not None and latest["rhr"] > mean + RHR_DELTA_BPM:
            flags.append({"key": "rhr_high", "level": "warn",
                          "text": f"RHR {latest['rhr']} bpm, {round(latest['rhr'] - mean)} di atas rata-rata 30 hari. "
                                  "Bisa tanda kelelahan atau mau sakit."})

    acute, chronic = latest.get("acute_load"), latest.get("chronic_load")
    if acute and chronic:
        ratio = acute / chronic
        baseline["load"] = {"acute": acute, "chronic": chronic, "ratio": round(ratio, 2)}
        if ratio > LOAD_RATIO:
            flags.append({"key": "load_spike", "level": "warn", "text": "Beban latihan melonjak dibanding 4 minggu terakhir."})

    return {"baseline": baseline, "flags": flags}


PLAN_KINDS = ("run", "strength", "other", "rest")
PLAN_TYPES = ("easy", "long", "tempo", "interval", "recovery", "race", "strength", "rest", "other")
DONE_AT = 0.95  # GPS distance runs a little short, so 5.8 of a planned 6 km still counts as done


def _iso(d) -> bool:
    try:
        date.fromisoformat(d)
        return True
    except (TypeError, ValueError):
        return False


def _bi(t) -> bool:
    """Bilingual text: Indonesian in "id", optional English in "en"."""
    return isinstance(t, dict) and isinstance(t.get("id"), str) and ("en" not in t or isinstance(t["en"], str))


def _pos(v) -> bool:
    return isinstance(v, (int, float)) and not isinstance(v, bool) and v > 0


def _session(s, start: str, end: str) -> dict | None:
    if not (isinstance(s, dict) and _iso(s.get("date")) and start <= s["date"] <= end
            and s.get("kind") in PLAN_KINDS and s.get("type") in PLAN_TYPES and _bi(s.get("title"))):
        return None
    out = {k: s[k] for k in ("date", "kind", "type", "title")}
    tgt = s.get("target") or {}
    if not isinstance(tgt, dict) or any(k not in ("distance_m", "duration_s") or not _pos(v) for k, v in tgt.items()):
        return None
    if tgt:
        out["target"] = tgt
    pace = s.get("pace")
    if pace is not None:
        if not (isinstance(pace, dict) and _pos(pace.get("min_s_km")) and _pos(pace.get("max_s_km")) and pace["min_s_km"] <= pace["max_s_km"]):
            return None
        out["pace"] = {"min_s_km": pace["min_s_km"], "max_s_km": pace["max_s_km"]}
    zone = s.get("hr_zone")
    if zone is not None:
        if zone not in (1, 2, 3, 4, 5):
            return None
        out["hr_zone"] = zone
    if "notes" in s:
        if not _bi(s["notes"]):
            return None
        out["notes"] = s["notes"]
    if "workout" in s:
        w = s["workout"]
        if s["kind"] != "strength" or not isinstance(w, dict):
            return None
        if set(w) == {"program", "day"} and w["program"] == "strength28" and _int(w["day"], 1, 28):
            out["workout"] = {"program": "strength28", "day": w["day"]}
        elif set(w) == {"items"} and (items := _plan_items(w["items"])):
            out["workout"] = {"items": items}
        else:
            return None
    return out


def _plan_items(items) -> list[dict] | None:
    """A plan's own strength routine: [{ex, sets, reps | seconds, kg?}], exercises from the app's catalog."""
    if not (isinstance(items, list) and 1 <= len(items) <= 15):
        return None
    out = []
    for i in items:
        if not (isinstance(i, dict) and i.get("ex") in wd.EXERCISES and _int(i.get("sets"), 1, 10)
                and set(i) <= {"ex", "sets", "reps", "seconds", "kg"} and (("reps" in i) != ("seconds" in i))):
            return None
        if "reps" in i and not _int(i["reps"], 1, 100) or "seconds" in i and not _int(i["seconds"], 5, 600):
            return None
        kg = i.get("kg")
        if kg is not None and not (isinstance(kg, (int, float)) and not isinstance(kg, bool) and 0 < kg <= 200):
            return None
        out.append({k: i[k] for k in ("ex", "sets", "reps", "seconds", "kg") if k in i})
    return out


def plan_doc(p: dict) -> dict | None:
    """Validate a training plan written by Claude; returns the stored shape with sessions sorted by date."""
    if not (isinstance(p, dict) and isinstance(p.get("name"), str) and p["name"].strip() and _bi(p.get("goal"))
            and _iso(p.get("start")) and _iso(p.get("end")) and p["start"] <= p["end"]
            and isinstance(p.get("sessions"), list) and p["sessions"]):
        return None
    if "race_date" in p and not _iso(p["race_date"]):
        return None
    sessions = [_session(s, p["start"], p["end"]) for s in p["sessions"]]
    if any(s is None for s in sessions):
        return None
    weeks = p.get("weeks", [])
    first = week_start(date.fromisoformat(p["start"])).isoformat()
    if not isinstance(weeks, list) or not all(
            isinstance(w, dict) and _iso(w.get("start")) and date.fromisoformat(w["start"]).weekday() == 0
            and first <= w["start"] <= p["end"] and _bi(w.get("goal")) for w in weeks):
        return None
    doc = {k: p[k] for k in ("name", "goal", "start", "end")}
    if "race_date" in p:
        doc["race_date"] = p["race_date"]
    if weeks:
        doc["weeks"] = [{"start": w["start"], "goal": w["goal"]} for w in weeks]
    doc["sessions"] = sorted(sessions, key=lambda s: s["date"])
    return doc


def _mean(xs):
    return round(statistics.fmean(xs), 3) if xs else None


SKIP_REASONS = ("sick", "travel", "rest", "other")
MOVE_DAYS = 1  # a session done a day early or late still counts
MOVE_MIN = 0.5  # ...when the moved activity covers at least half the target


def _sid(s: dict) -> tuple:
    return s["date"], s["kind"], s.get("n", 0)


def _score(target: dict | None, dist: float, dur: float) -> float:
    tgt = target or {}
    if tgt.get("distance_m"):
        return min(1.0, dist / tgt["distance_m"])
    if tgt.get("duration_s"):
        return min(1.0, dur / tgt["duration_s"])
    return 1.0


def plan_progress(plan: dict, acts: list[dict], today_: date, logs=(), overrides=(), adjusts=()) -> dict:
    """Score each planned session against the activities that fulfilled it.
    pct = done / target on distance, else duration, capped at 1; no target means any activity counts fully. A recovery
    adjustment stored for the session replaces its target. Rest days and future sessions are not scored; neither is
    today's until something is done.
    Matching, in order: the owner's overrides (linked activities, or skipped with a reason, which leaves the scores);
    activities of the same day and kind (two sessions of one kind on a day take one activity each, in start order; a
    race takes its longest); then a past session with nothing gets an unused same-kind activity from a day before or
    after (done_on says when). A strength day with no watch activity is scored from the app's workout log of that day."""
    logs_by_day = {}
    for l in logs:
        logs_by_day.setdefault(l["date"], []).append(l)
    ovr = {(o["date"], o["kind"], o.get("n", 0)): o for o in overrides}
    adj = {(a["date"], a["kind"]): a for a in adjusts}
    t = today_.isoformat()
    by_id = {a["_id"]: a for a in acts}
    seen: dict[tuple, int] = {}
    rows = []
    for s in plan["sessions"]:
        k = (s["date"], s["kind"])
        n = seen.get(k, 0)
        seen[k] = n + 1
        rows.append({**s, "n": n, "status": "rest", "pct": None, "activity_ids": [], "done": None})
    used: set = set()
    picked: dict[int, list[dict]] = {}

    for i, r in enumerate(rows):
        o = ovr.get(_sid(r))
        if r["kind"] == "rest" or not o:
            continue
        r["override"] = True  # the app offers undo only on sessions the owner changed
        if o.get("skipped"):
            r.update(status="skipped", skipped=o["skipped"])
            continue
        mine = [by_id[x] for x in o.get("activity_ids") or [] if x in by_id and x not in used]
        if mine:
            picked[i] = mine
            used.update(a["_id"] for a in mine)

    free = lambda a, kind: a["_id"] not in used and _kind(a.get("type")) == kind
    groups: dict[tuple, list[int]] = {}
    for i, r in enumerate(rows):
        if r["kind"] != "rest" and r["status"] != "skipped" and i not in picked:
            groups.setdefault((r["date"], r["kind"]), []).append(i)
    for (d, kind), idx in groups.items():
        day = sorted((a for a in acts if a["date"] == d and free(a, kind)), key=lambda a: a.get("start_time") or "")
        if not day:
            continue
        if len(idx) == 1:
            r = rows[idx[0]]
            mine = [max(day, key=lambda a: a.get("distance_m") or 0)] if r["type"] == "race" else day
            picked[idx[0]] = mine
        else:
            for j, a in zip(idx, day):
                picked[j] = [a]
        used.update(a["_id"] for i in idx for a in picked.get(i, []))

    # a past session left empty borrows an unplanned activity of its kind from the day before or after
    for i, r in enumerate(rows):
        if r["kind"] == "rest" or r["status"] == "skipped" or i in picked or r["date"] >= t:
            continue
        if r["kind"] == "strength" and logs_by_day.get(r["date"]):
            continue
        d0 = date.fromisoformat(r["date"])
        target = ((adj.get((r["date"], r["kind"])) or {}).get("target")) or r.get("target")
        # a stray short jog is not the session moved; the owner can still link it by hand
        near = [a for a in acts if free(a, r["kind"]) and 0 < abs((date.fromisoformat(a["date"]) - d0).days) <= MOVE_DAYS
                and _score(target, a.get("distance_m") or 0, a.get("duration_s") or 0) >= MOVE_MIN]
        if near:
            a = min(near, key=lambda a: (abs((date.fromisoformat(a["date"]) - d0).days), a["date"] < r["date"]))
            picked[i] = [a]
            used.add(a["_id"])
            r["done_on"] = a["date"]

    for i, r in enumerate(rows):
        if r["kind"] == "rest" or r["status"] == "skipped":
            continue
        a_ = adj.get((r["date"], r["kind"])) if r.get("n", 0) == 0 else None
        if a_:
            r["adjust"] = {k: v for k, v in a_.items() if k not in ("_id", "user_id", "date", "kind", "created_at")}
        target = (a_ or {}).get("target") or r.get("target")
        match = picked.get(i)
        if match:
            dist = sum(a.get("distance_m") or 0 for a in match)
            dur = sum(a.get("duration_s") or 0 for a in match)
            pct = _score(target, dist, dur)
            r.update(status="done" if pct >= DONE_AT else "partial", pct=round(pct, 3),
                     activity_ids=[a["_id"] for a in match], done={"distance_m": dist, "duration_s": dur})
        elif r["kind"] == "strength" and logs_by_day.get(r["date"]):
            log = max(logs_by_day[r["date"]], key=lambda l: len(_done_sets(l)))
            sets = len(_done_sets(log))
            pct = 1.0 if log["completed"] else round(sets / len(log["sets"]), 3)
            r.update(status="done" if log["completed"] else "partial", pct=pct, log_id=log.get("_id"),
                     done={"sets": sets, "of": len(log["sets"]), "duration_s": log.get("duration_s")})
        elif r["date"] > t:
            r["status"] = "upcoming"
        elif r["date"] == t:
            r["status"] = "today"
        else:
            r.update(status="missed", pct=0.0)

    weeks = {}
    for r in rows:
        w = weeks.setdefault(week_start(date.fromisoformat(r["date"])).isoformat(),
                             {"planned": 0, "done": 0, "partial": 0, "missed": 0, "skipped": 0, "scores": []})
        if r["kind"] != "rest":
            if r["status"] == "skipped":
                w["skipped"] += 1
            else:
                w["planned"] += 1
            w["done"] += r["status"] == "done"
            w["partial"] += r["status"] == "partial"
            w["missed"] += r["status"] == "missed"
        if r["pct"] is not None:
            w["scores"].append(r["pct"])
    goals = {w["start"]: w["goal"] for w in plan.get("weeks", [])}
    week_rows = [{"start": k, "planned": v["planned"], "done": v["done"], "partial": v["partial"], "missed": v["missed"],
                  "skipped": v["skipped"], "pct": _mean(v["scores"]), "goal": goals.get(k)}
                 for k, v in sorted(weeks.items())]
    extras = [{"_id": a["_id"], "date": a["date"], "type": a.get("type"), "name": a.get("name"), "distance_m": a.get("distance_m"),
               "duration_s": a.get("duration_s"), "kind": _kind(a.get("type"))}
              for a in acts if a["_id"] not in used and plan["start"] <= a["date"] <= min(plan["end"], t)]
    return {"sessions": rows, "weeks": week_rows, "overall": _mean([r["pct"] for r in rows if r["pct"] is not None]),
            "extras": extras}


def active_plan(user_id: str) -> dict | None:
    return db().plans.find_one({"user_id": user_id, "status": "active"}, {"user_id": 0}, sort=[("created_at", -1)])


def plan_meta(plan: dict) -> dict:
    return {**{k: plan[k] for k in ("name", "goal", "start", "end", "race_date", "created_at") if k in plan},
            "id": str(plan["_id"]) if "_id" in plan else None}


def plan_view(user_id: str, plan: dict | None = None) -> dict:
    plan = plan or active_plan(user_id)
    if not plan:
        return {"plan": None, "sessions": [], "weeks": [], "overall": None, "extras": []}
    d = db()
    # a day either side, so a session moved off the plan's first or last day still finds its run
    lo = (date.fromisoformat(plan["start"]) - timedelta(days=MOVE_DAYS)).isoformat()
    hi = (date.fromisoformat(plan["end"]) + timedelta(days=MOVE_DAYS)).isoformat()
    proj = {"date": 1, "type": 1, "name": 1, "distance_m": 1, "duration_s": 1, "start_time": 1}
    acts = list(d.activities.find({"user_id": user_id, "date": {"$gte": lo, "$lte": hi}}, proj))
    rng = {"user_id": user_id, "date": {"$gte": plan["start"], "$lte": plan["end"]}}
    logs = list(d.workout_logs.find(rng, {"date": 1, "completed": 1, "sets.skipped": 1, "duration_s": 1}))
    overrides = list(d.plan_overrides.find(rng))
    adjusts = list(d.plan_adjustments.find(rng))
    return {"plan": plan_meta(plan), **plan_progress(plan, acts, today(), logs, overrides, adjusts)}


def save_adjustment(user_id: str, session: dict, adjust: dict) -> bool:
    """Keep the first recovery adjustment computed for a session, so later scoring and the agenda use the target the
    user was told to run. Idempotent: returns True only when this call stored it."""
    r = db().plan_adjustments.update_one(
        {"_id": f"{user_id}:{session['date']}:{session['kind']}"},
        {"$setOnInsert": {"user_id": user_id, "date": session["date"], "kind": session["kind"], **adjust,
                          "created_at": datetime.now(timezone.utc)}},
        upsert=True,
    )
    return r.upserted_id is not None


def override_doc(body: dict, plan: dict, acts: dict) -> dict | str:
    """Validate an owner's link or skip for one planned session. acts: the user's activities by id among those named.
    Returns the stored shape, or the reason it was refused."""
    b = body if isinstance(body, dict) else {}
    n = b.get("n", 0)
    if not (_iso(b.get("date")) and b.get("kind") in PLAN_KINDS and b["kind"] != "rest" and _int(n, 0, 9)):
        return "need date, kind (run, strength or other) and n"
    same = [s for s in plan["sessions"] if s["date"] == b["date"] and s["kind"] == b["kind"]]
    if n >= len(same):
        return "no such session in the active plan"
    ids, skip = b.get("activity_ids"), b.get("skipped")
    if (ids is None) == (skip is None):
        return "give activity_ids or skipped, not both"
    out = {"date": b["date"], "kind": b["kind"], "n": n}
    if skip is not None:
        if skip not in SKIP_REASONS:
            return f"skipped must be one of {', '.join(SKIP_REASONS)}"
        return {**out, "skipped": skip}
    if not (isinstance(ids, list) and 1 <= len(ids) <= 5 and all(_int(x, 1, 10**13) for x in ids)):
        return "activity_ids must be 1 to 5 activity ids"
    for x in ids:
        a = acts.get(x)
        if not a:
            return f"activity {x} not found"
        if _kind(a.get("type")) != b["kind"]:
            return f"activity {x} is not a {b['kind']} activity"
    return {**out, "activity_ids": list(dict.fromkeys(ids))}


def plan_list(user_id: str) -> list[dict]:
    """Every saved plan, newest first, with its session counts, so earlier blocks stay readable after a new one."""
    out, seen = [], set()
    for p in db().plans.find({"user_id": user_id}, {"user_id": 0}).sort("created_at", -1):
        # every revision of a block is saved as a new plan; the newest one per date range stands for the block
        if (p["start"], p["end"]) in seen:
            continue
        seen.add((p["start"], p["end"]))
        planned = sum(s["kind"] != "rest" for s in p["sessions"])
        out.append({**plan_meta(p), "status": p.get("status"), "planned": planned})
    return out


CUT = 0.7  # a long run or an easy-week session keeps 70% of its target
# RHR rise that, with two low HRV nights, changes a planned session. Lower than the flag's 5: in this user's
# strained weeks (10 Sep, 30 Sep to 9 Oct 2026) RHR rose 2 to 4 bpm and +5 never fired.
ADJUST_RHR_BPM = 3


def _rhr_mean(days: list[dict], i: int) -> float | None:
    base = _vals(days[max(0, i - RHR_BASELINE_DAYS):i], "rhr")
    return statistics.fmean(base) if len(base) >= MIN_DAYS else None


def _tired(days: list[dict], i: int) -> bool:
    """HRV under Garmin's normal floor that night and the one before, and RHR 3+ above its 30 day mean.
    Sleep and readiness are left out on purpose: Garmin undercounts this user's split sleep."""
    if i < 1:
        return False
    d, floor = days[i], days[i].get("hrv_balanced_low")
    hrv = [days[i - 1].get("hrv_last_night"), d.get("hrv_last_night")]
    mean = _rhr_mean(days, i)
    return (floor is not None and None not in hrv and max(hrv) < floor
            and mean is not None and d.get("rhr") is not None and d["rhr"] >= mean + ADJUST_RHR_BPM)


def _cut(target: dict | None) -> dict | None:
    """70% of a target, durations floored to whole minutes so the app reads "31m", not "31m 30s"."""
    return {k: int(v * CUT // 60 * 60) if k == "duration_s" else round(v * CUT) for k, v in (target or {}).items()} or None


def plan_adjust(days: list[dict], session: dict | None) -> dict | None:
    """How today's session should change when recovery is off. days ascend and must end today.
    Tired today: quality becomes a 40 min easy run, a long run keeps 70%. Tired 3 days running: easy week."""
    if not session or session["kind"] == "rest" or not days or days[-1]["date"] != session["date"]:
        return None
    streak = 0
    while streak < len(days) and _tired(days, len(days) - 1 - streak):
        streak += 1
    if not streak:
        return None
    if streak >= 3:
        key, target = "easy_week", _cut(session.get("target"))
    elif session["type"] in ("tempo", "interval"):
        key, target = "quality_to_easy", {"duration_s": 2400}
    elif session["type"] == "long":
        key, target = "cut_long", _cut(session.get("target"))
    else:
        return None
    d = days[-1]
    return {"key": key, "target": target, "streak": streak, "hrv": [days[-2]["hrv_last_night"], d["hrv_last_night"]],
            "hrv_low": d["hrv_balanced_low"], "rhr": d["rhr"], "rhr_mean": round(_rhr_mean(days, len(days) - 1), 1)}


def plan_today(view: dict, days: list[dict] | None = None) -> dict | None:
    """Today's session, this week's score and any recovery adjustment, small enough to ride along with /today."""
    if not view["plan"]:
        return None
    t = today().isoformat()
    week = week_start(today()).isoformat()
    sessions = [s for s in view["sessions"] if s["date"] == t]
    # "session" stays the run first for old app builds and plan_adjust; "sessions" carries the whole day
    session = next((s for s in sessions if s["kind"] == "run"), None) or next(iter(sessions), None)
    return {
        "name": view["plan"]["name"],
        "session": session,
        "sessions": sessions,
        "week": next((w for w in view["weeks"] if w["start"] == week), None),
        "overall": view["overall"],
        # the stored adjustment wins, so a recovery change told in the morning still stands at night
        "adjust": (session or {}).get("adjust") or plan_adjust(days or [], session),
    }


# Workouts

REP_SECONDS = 3
ROUND_REST_S = 60
REST_DEFAULT_S = 20
DIFFICULTY = {-1: 0.8, 0: 1.0, 1: 1.2}
LINK_BEFORE = timedelta(minutes=15)
LINK_AFTER = timedelta(minutes=5)
LOG_PROGRAMS = ("strength28", "focus", "homeworkout", "plan", "routine")
LOG_BLOCKS = ("warmup", "main", "finisher", "cooldown")
WORK_BLOCKS = ("main", "finisher")


def _scale(ex_id: str, target: dict, factor: float, block: str) -> dict:
    """Difficulty scales bodyweight work only: warmups, cooldowns and dumbbell reps stay as written."""
    if block in ("warmup", "cooldown") or factor == 1.0:
        return dict(target)
    if "seconds" in target:
        return {"seconds": max(10, round(target["seconds"] * factor))}
    if wd.EXERCISES[ex_id]["equipment"] == "dumbbell":
        return dict(target)
    return {"reps": max(1, round(target["reps"] * factor))}


def workout_items(warmup, blocks, cooldown, factor) -> list[dict]:
    """warmup/cooldown: [(ex, target)]; blocks: [{"block", "rounds": int, "items": [(ex, target)]}] already for the week."""
    one = lambda ex, tgt, block, r, n: {"ex": ex, **_scale(ex, tgt, factor, block), "block": block, "round": r, "rounds": n}
    out = [one(ex, t, "warmup", 1, 1) for ex, t in warmup]
    for b in blocks:
        for r in range(1, b["rounds"] + 1):
            out += [one(ex, t, b["block"], r, b["rounds"]) for ex, t in b["items"]]
    return out + [one(ex, t, "cooldown", 1, 1) for ex, t in cooldown]


def workout_minutes(items: list[dict], rest_s: int) -> int:
    work = sum(i.get("reps", 0) * REP_SECONDS + i.get("seconds", 0) for i in items)
    rounds = sum(1 for a, b in zip(items, items[1:]) if a["block"] == b["block"] and b["round"] == a["round"] + 1)
    return math.ceil((work + rest_s * max(0, len(items) - 1) + ROUND_REST_S * rounds) / 60)


def _workout(key, program, day, week, type_, title, items, rest_s) -> dict:
    return {"key": key, "program": program, "day": day, "week": week, "type": type_, "title": title,
            "minutes": workout_minutes(items, rest_s), "items": items}


def program_day(day: int, difficulty: int = 0, rest_s: int = REST_DEFAULT_S) -> dict:
    if not (isinstance(day, int) and 1 <= day <= wd.PROGRAM["days"]):
        raise ValueError(f"day {day} outside 1..{wd.PROGRAM['days']}")
    week, kind = (day - 1) // 7 + 1, "ABC"[(day - 1) % 3]
    t = wd.TEMPLATES[kind]
    blocks = [{"block": b["block"], "rounds": b["rounds"][week - 1],
               "items": [(i["ex"], {k: i[k] + i["step"] * (week - 1)}) for i in b["items"]
                         for k in ("reps", "seconds") if k in i
                         if i.get("weeks", [1, 4])[0] <= week <= i.get("weeks", [1, 4])[1]]}
              for b in t["blocks"]]
    items = workout_items(t["warmup"], blocks, t["cooldown"], DIFFICULTY[difficulty])
    return _workout(f"strength28:{day}", "strength28", day, week, kind, t["title"], items, rest_s)


def focus_workout(fid: str, difficulty: int = 0, rest_s: int = REST_DEFAULT_S) -> dict:
    f = next(f for f in wd.FOCUS if f["id"] == fid)
    blocks = [{"block": "main", "rounds": f["rounds"], "items": f["items"]}]
    items = workout_items(f["warmup"], blocks, f["cooldown"], DIFFICULTY[difficulty])
    return _workout(f"focus:{fid}", "focus", None, None, fid, f["title"], items, rest_s)


def plan_workout(session: dict, rest_s: int = REST_DEFAULT_S) -> dict:
    """A plan session's own routine as a playable workout: each exercise's sets run back to back as rounds, so the
    player rests ROUND_REST_S between sets and rest_s between exercises. No warm-up or cool-down blocks: the plan's
    notes carry those."""
    items = []
    for it in session["workout"]["items"]:
        tgt = {k: it[k] for k in ("reps", "seconds") if k in it}
        for r in range(1, it["sets"] + 1):
            items.append({"ex": it["ex"], **tgt, **({"kg": it["kg"]} if "kg" in it else {}), "block": "main", "round": r,
                          "rounds": it["sets"]})
    return _workout(f"plan:{session['date']}", "plan", None, None, "plan", session["title"], items, rest_s)


def routine_workout(r: dict, difficulty: int = 0, rest_s: int = REST_DEFAULT_S) -> dict:
    blocks = [{"block": "main", "rounds": r["rounds"], "items": [(i["ex"], {k: i[k] for k in ("reps", "seconds") if k in i}) for i in r["items"]]}]
    items = workout_items(wd.WARMUP, blocks, wd.TEMPLATES["A"]["cooldown"], DIFFICULTY[difficulty])
    return _workout(f"routine:{r['_id']}", "routine", None, None, "routine", {"id": r["title"], "en": r["title"]}, items, rest_s)


def routine_doc(body: dict) -> dict | None:
    """Validate a routine the owner saved from an edited workout: a title, 1-6 rounds, 1-20 exercises."""
    b = body if isinstance(body, dict) else {}
    title, rounds, items = b.get("title"), b.get("rounds"), b.get("items")
    if not (isinstance(title, str) and 1 <= len(title.strip()) <= 40 and _int(rounds, 1, 6)
            and isinstance(items, list) and 1 <= len(items) <= 20):
        return None
    out = []
    for i in items:
        if not (isinstance(i, dict) and i.get("ex") in wd.EXERCISES and set(i) <= {"ex", "reps", "seconds"}
                and (("reps" in i) != ("seconds" in i))):
            return None
        if "reps" in i and not _int(i["reps"], 1, 100) or "seconds" in i and not _int(i["seconds"], 5, 600):
            return None
        out.append(dict(i))
    return {"title": title.strip(), "rounds": rounds, "items": out}


def workout_muscles(log: dict) -> dict:
    """Same shape as strength_summary's muscles so merge_muscles takes both."""
    muscles, n = {}, 0
    for s in log.get("sets") or []:
        if s.get("block") not in WORK_BLOCKS or s.get("skipped"):
            continue
        n += 1
        ex = wd.EXERCISES.get(s["ex"]) or {}
        for kind, key in (("p", "primary"), ("s", "secondary")):
            for m in ex.get(kind, []):
                muscles.setdefault(m, {"primary": 0, "secondary": 0})[key] += 1
    return {"muscles": muscles, "total_sets": n}


def _int(v, lo, hi) -> bool:
    return isinstance(v, int) and not isinstance(v, bool) and lo <= v <= hi


def _stamp(s) -> datetime | None:
    try:
        return datetime.strptime(s, "%Y-%m-%d %H:%M:%S")
    except (TypeError, ValueError):
        return None


def _set(s) -> dict | None:
    if not (isinstance(s, dict) and s.get("ex") in wd.EXERCISES and s.get("block") in LOG_BLOCKS
            and _int(s.get("round"), 1, 100) and isinstance(s.get("skipped"), bool)):
        return None
    tgt = s.get("target")
    if not (isinstance(tgt, dict) and len(tgt) == 1 and next(iter(tgt)) in ("reps", "seconds")
            and _int(next(iter(tgt.values())), 1, 3600)):
        return None
    reps, secs, kg = s.get("reps"), s.get("seconds"), s.get("kg")
    if not ((reps is None or _int(reps, 0, 500)) and (secs is None or _int(secs, 0, 3600))
            and (kg is None or (isinstance(kg, (int, float)) and not isinstance(kg, bool) and 0 <= kg <= 200))):
        return None
    return {"ex": s["ex"], "block": s["block"], "round": s["round"], "target": dict(tgt),
            "reps": reps, "seconds": secs, "kg": kg, "skipped": s["skipped"]}


def log_doc(body: dict) -> dict | None:
    """Validate a workout log posted by the app (or the Home Workout import); returns the stored shape."""
    b = body if isinstance(body, dict) else {}
    start, end = _stamp(b.get("start_time")), _stamp(b.get("end_time"))
    sets = b.get("sets")
    if not (isinstance(b.get("id"), str) and 8 <= len(b["id"]) <= 64 and isinstance(b.get("key"), str)
            and b.get("program") in LOG_PROGRAMS and (b.get("day") is None or _int(b.get("day"), 1, 28))
            and _bi(b.get("title")) and start and end and start <= end and _int(b.get("duration_s"), 0, 14400)
            and isinstance(b.get("completed"), bool) and isinstance(sets, list) and 1 <= len(sets) <= 300):
        return None
    clean = [_set(s) for s in sets]
    if any(s is None for s in clean):
        return None
    return {"_id": b["id"], **{k: b.get(k) for k in ("key", "program", "day", "title", "start_time", "end_time",
                                                       "duration_s", "completed")},
            "date": b["start_time"][:10], "sets": clean, "source": "homeworkout" if b.get("source") == "homeworkout" else "app"}


def program_progress(logs: list[dict]) -> dict:
    done = sorted((l for l in logs if l.get("program") == "strength28" and l.get("completed")), key=lambda l: l["start_time"])
    last28 = max((i for i, l in enumerate(done) if l["day"] == 28), default=-1)
    days = sorted({l["day"] for l in done[last28 + 1:]})
    since = done[last28]["start_time"] if last28 >= 0 else ""
    # started but not finished in this run of the program, so the calendar can show them as partly done
    partial = sorted({l["day"] for l in logs if l.get("program") == "strength28" and not l.get("completed")
                      and l["start_time"] > since and l.get("day") and l["day"] not in days})
    nxt = next((d for d in range(1, 29) if d not in days), 1)
    return {"next_day": nxt, "done_days": days, "partial_days": partial, "runs_completed": sum(l["day"] == 28 for l in done)}


def weight_hints(logs: list[dict]) -> dict:
    """Last dumbbell weight per exercise; +1 kg when every set of the latest session beat the target by 2 reps."""
    out = {}
    for log in sorted(logs, key=lambda l: l["start_time"], reverse=True):
        for ex in dict.fromkeys(s["ex"] for s in log.get("sets") or []):
            if ex in out or wd.EXERCISES[ex]["equipment"] != "dumbbell":
                continue
            sets = [s for s in log["sets"] if s["ex"] == ex and not s["skipped"]]
            kgs = [s["kg"] for s in sets if (s.get("kg") or 0) > 0]
            if not kgs:
                continue
            easy = all((s.get("reps") or 0) >= (s["target"].get("reps") or 0) + 2 for s in sets)
            out[ex] = {"kg": kgs[-1], "suggest": kgs[-1] + 1 if easy else kgs[-1]}
    return out


def match_workouts(logs: list[dict], acts: list[dict]) -> list[tuple]:
    """Pair unlinked logs with the nearest strength activity that started 15 min before to 5 min after the log.
    acts must already exclude activities another log uses."""
    free = [a for a in acts if a.get("type") == "strength_training" and _stamp(a.get("start_time"))]
    pairs = []
    for log in sorted((l for l in logs if not l.get("activity_id")), key=lambda l: l["start_time"]):
        start, end = _stamp(log["start_time"]), _stamp(log["end_time"])
        near = [a for a in free if start - LINK_BEFORE <= _stamp(a["start_time"]) <= end + LINK_AFTER]
        if near:
            best = min(near, key=lambda a: abs(_stamp(a["start_time"]) - start))
            free.remove(best)
            pairs.append((log["_id"], best["_id"]))
    return pairs


def link_workouts(user_id: str, frm: str, to: str) -> int:
    d = db()
    logs = list(d.workout_logs.find({"user_id": user_id, "date": {"$gte": frm, "$lte": to}},
                                    {"start_time": 1, "end_time": 1, "activity_id": 1}))
    used = {x["activity_id"] for x in d.workout_logs.find({"user_id": user_id, "activity_id": {"$ne": None}}, {"activity_id": 1})}
    acts = [a for a in d.activities.find({"user_id": user_id, "type": "strength_training", "date": {"$gte": frm, "$lte": to}},
                                         {"start_time": 1, "type": 1}) if a["_id"] not in used]
    pairs = match_workouts(logs, acts)
    for log_id, act_id in pairs:
        d.workout_logs.update_one({"_id": log_id, "user_id": user_id}, {"$set": {"activity_id": act_id}})
    return len(pairs)


def workout_prefs(user_id: str) -> dict:
    doc = db().meta.find_one({"_id": f"workout_prefs:{user_id}"}) or {}
    return {"difficulty": doc.get("difficulty", 0), "rest_s": doc.get("rest_s", REST_DEFAULT_S)}


def _done_sets(log: dict) -> list[dict]:
    return [s for s in log.get("sets") or [] if not s.get("skipped")]


def log_summary(log: dict) -> dict:
    done = _done_sets(log)
    return {"id": log["_id"], **{k: log.get(k) for k in ("key", "program", "day", "title", "date", "start_time",
                                                          "duration_s", "completed", "source", "activity_id")},
            "reps": sum(s.get("reps") or 0 for s in done),
            "volume_kg": round(sum((s.get("reps") or 0) * (s.get("kg") or 0) for s in done), 1),
            "exercises": len({s["ex"] for s in done})}


def log_view(log: dict) -> dict:
    rows = {}
    for s in _done_sets(log):
        r = rows.setdefault(s["ex"], {"ex": s["ex"], "name": wd.EXERCISES[s["ex"]]["name"], "sets": 0, "reps": 0,
                                      "seconds": 0, "kg_max": None})
        r["sets"] += 1
        r["reps"] += s.get("reps") or 0
        r["seconds"] += s.get("seconds") or 0
        if s.get("kg"):
            r["kg_max"] = max(r["kg_max"] or 0, s["kg"])
    m = workout_muscles(log)
    return {**log_summary(log), "muscles": m["muscles"], "total_sets": m["total_sets"],
            "exercises": list(rows.values()), "sets": log.get("sets") or []}


def exercise_history(logs: list[dict]) -> dict:
    """Per exercise, its last 5 sessions newest first: date, reps or seconds per set done, heaviest kg."""
    out: dict[str, list] = {}
    for log in sorted(logs, key=lambda l: l["start_time"], reverse=True):
        sets: dict[str, list] = {}
        for x in log.get("sets") or []:
            if x.get("skipped") or x.get("block") not in WORK_BLOCKS:
                continue
            sets.setdefault(x["ex"], []).append(x)
        for ex, xs in sets.items():
            if len(out.setdefault(ex, [])) >= 5:
                continue
            kgs = [x["kg"] for x in xs if x.get("kg")]
            out[ex].append({"date": log["date"], "reps": [x.get("reps") or 0 for x in xs if x.get("reps") is not None],
                            "seconds": [x.get("seconds") or 0 for x in xs if x.get("seconds") is not None],
                            "kg": max(kgs) if kgs else None})
    return out


def watch_strength(acts: list[dict], logs: list[dict]) -> list[dict]:
    """Strength sessions recorded only on the watch (no app log linked), shaped like log summaries."""
    linked = {l.get("activity_id") for l in logs if l.get("activity_id")}
    return [{"id": f"act:{a['_id']}", "key": "", "program": "watch", "day": None,
             "title": {"id": a.get("name") or "Strength", "en": a.get("name") or "Strength"}, "date": a["date"],
             "start_time": a.get("start_time"), "duration_s": round(a.get("duration_s") or 0), "completed": True,
             "source": "watch", "activity_id": a["_id"], "reps": 0, "volume_kg": 0, "exercises": 0}
            for a in acts if a["_id"] not in linked]


def workouts_home(user_id: str, role: str | None = None) -> dict:
    d, t = db(), today()
    prefs = workout_prefs(user_id)
    args = (prefs["difficulty"], prefs["rest_s"])
    logs = list(d.workout_logs.find({"user_id": user_id}).sort("start_time", -1))
    since_w, since_kg = week_start(t).isoformat(), (t - timedelta(days=120)).isoformat()
    acts = list(d.activities.find({"user_id": user_id, "type": "strength_training", "date": {"$gte": (t - timedelta(days=60)).isoformat()}},
                                  {"date": 1, "start_time": 1, "name": 1, "duration_s": 1}).sort("start_time", -1))
    watch = watch_strength(acts, logs)
    week = [log_summary(l) for l in logs if l["date"] >= since_w] + [w for w in watch if w["date"] >= since_w]
    plan = active_plan(user_id)
    planned = [plan_workout(s, prefs["rest_s"]) for s in (plan or {}).get("sessions", [])
               if s["kind"] == "strength" and "items" in (s.get("workout") or {})]
    routines = list(d.workout_routines.find({"user_id": user_id}).sort("created_at", 1))
    recent = sorted([log_summary(l) for l in logs[:20]] + watch, key=lambda r: r["start_time"] or "", reverse=True)[:20]
    return {
        "exercises": wd.EXERCISES,
        "program": {**wd.PROGRAM, **program_progress(logs), "difficulty": prefs["difficulty"]},
        "workouts": [program_day(n, *args) for n in range(1, wd.PROGRAM["days"] + 1)]
                    + [focus_workout(f["id"], *args) for f in wd.FOCUS] + planned
                    + [routine_workout(r, *args) for r in routines],
        "prefs": prefs,
        # Monday-based like Training's muscle card, and counting watch-only strength sessions too
        "week": {"sessions": len(week), "minutes": round(sum(l["duration_s"] or 0 for l in week) / 60),
                 "reps": sum(l["reps"] for l in week), "dates": sorted({l["date"] for l in week}),
                 "watch": sum(l["source"] == "watch" for l in week)},
        "recent": recent,
        "weights": weight_hints([l for l in logs if l["date"] >= since_kg]),
        "history": exercise_history([l for l in logs if l["date"] >= since_kg]),
        "routines": [{"id": r["_id"], "title": r["title"], "rounds": r["rounds"], "items": r["items"]} for r in routines],
        # cached with the payload on the phone, so an offline gym visit still knows who may log
        "role": role,
    }
