import hashlib
import itertools
import json
import math
import os
import re
import statistics
import time
import urllib.request
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

from cryptography.fernet import Fernet
from garminconnect import Garmin, GarminConnectTooManyRequestsError
from pymongo import MongoClient

# ponytail: single Garmin account; add real user ids when family members bring their own watches
OWNER = "owner"
TZ = ZoneInfo(os.environ.get("APP_TZ", "Asia/Jakarta"))

_db = None


def db():
    global _db
    if _db is None:
        _db = MongoClient(os.environ["MONGODB_URI"])[os.environ.get("MONGODB_DB", "garmin_fit")]
        _db.daily.create_index([("user_id", 1), ("date", 1)])
        _db.activities.create_index([("user_id", 1), ("date", -1)])
        _db.summaries.create_index([("user_id", 1), ("period", 1), ("generated_at", -1)])
    return _db


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


def ensure_route(g: Garmin, doc: dict) -> dict:
    """Fetch and store the GPS route once per activity. Failures are stored as checked so they are not retried."""
    if doc.get("route_checked") or not dig(doc, "raw", "hasPolyline"):
        return doc
    try:
        route = extract_route(g.get_activity_details(doc["_id"], maxchart=10, maxpoly=4000))
    except GarminConnectTooManyRequestsError:
        raise
    except Exception:
        route = None
    doc.update(route=route, route_checked=True)
    db().activities.update_one({"_id": doc["_id"]}, {"$set": {"route": route, "route_checked": True}})
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


KEEP_ON_RESYNC = ("splits", "route", "route_checked")


def sync_activity(g: Garmin, doc: dict) -> bool:
    """Upsert one activity, keeping fetched extras. Returns True if Garmin was called beyond the list."""
    old = db().activities.find_one({"_id": doc["_id"]}, dict.fromkeys(KEEP_ON_RESYNC, 1)) or {}
    doc.update({k: old[k] for k in KEEP_ON_RESYNC if k in old})
    fetched = False
    if "running" in (doc["type"] or "") and not doc.get("splits"):
        doc["splits"] = extract_splits(g.get_activity_splits(doc["_id"]))
        fetched = True
    db().activities.replace_one({"_id": doc["_id"]}, doc, upsert=True)
    if not doc.get("route_checked") and dig(doc, "raw", "hasPolyline"):
        ensure_route(g, doc)
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
