import os
import secrets
import sys
from datetime import date, timedelta
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Query

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import core  # noqa: E402

app = FastAPI(title="garmin-fit")

NO_RAW = {"raw": 0, "route": 0, "series": 0}


def auth(authorization: str = Header("")) -> dict:
    token = authorization.removeprefix("Bearer ").strip()
    doc = core.db().access_tokens.find_one({"_id": core.sha256(token), "revoked": False}) if token else None
    if not doc:
        raise HTTPException(401, "invalid token")
    return doc


def owner_only(user: dict = Depends(auth)) -> dict:
    if user["role"] != "owner":
        raise HTTPException(403, "owner only")
    return user


def _range(frm: date | None, to: date | None, default_days: int) -> tuple[str, str]:
    to = to or core.today()
    frm = frm or to - timedelta(days=default_days - 1)
    return frm.isoformat(), to.isoformat()


def _daily(uid: str, frm: str, to: str) -> list[dict]:
    return list(core.db().daily.find({"user_id": uid, "date": {"$gte": frm, "$lte": to}}, {**NO_RAW, "_id": 0}).sort("date", 1))


@app.get("/me")
def me(user: dict = Depends(auth)):
    profile = core.db().profile.find_one({"_id": user["data_user_id"]}, {"_id": 0})
    return {"name": user["name"], "role": user["role"], "profile": profile}


@app.get("/daily")
def daily(frm: date | None = Query(None, alias="from"), to: date | None = None, user: dict = Depends(auth)):
    return _daily(user["data_user_id"], *_range(frm, to, 30))


@app.get("/activities")
def activities(frm: date | None = Query(None, alias="from"), to: date | None = None, user: dict = Depends(auth)):
    f, t = _range(frm, to, 90)
    q = {"user_id": user["data_user_id"], "date": {"$gte": f, "$lte": t}}
    acts = list(core.db().activities.find(q, NO_RAW).sort("start_time", -1))
    marks, _ = core.pr_history(core.runs_with_splits(user["data_user_id"]))
    for a in acts:
        a["prs"] = marks.get(a["_id"], [])
    return acts


@app.get("/recap")
def recap_view(period: str = Query("month", pattern="^(month|year)$"), key: str | None = None, user: dict = Depends(auth)):
    uid = user["data_user_id"]
    key = key or (core.today().isoformat()[:7] if period == "month" else str(core.today().year))
    try:
        start, end = core.period_range(period, key)
        pstart, pend = core.period_range(period, core.previous_key(period, key))
    except ValueError:
        raise HTTPException(400, "bad period key")
    proj = {"date": 1, "start_time": 1, "type": 1, "name": 1, "distance_m": 1, "duration_s": 1}
    def acts(a, b):
        return list(core.db().activities.find({"user_id": uid, "date": {"$gte": a.isoformat(), "$lte": b.isoformat()}}, proj))
    all_dates = {d["date"] for d in core.db().activities.find({"user_id": uid, "date": {"$lte": end.isoformat()}}, {"date": 1})}
    marks, _ = core.pr_history(core.runs_with_splits(uid))
    return core.recap(period, start, end, acts(start, end), acts(pstart, pend), all_dates, marks)


@app.get("/routes")
def routes(frm: date | None = Query(None, alias="from"), to: date | None = None, user: dict = Depends(auth)):
    f, t = _range(frm, to, 30)
    q = {"user_id": user["data_user_id"], "date": {"$gte": f, "$lte": t}, "route": {"$type": "array"}}
    return core.home_routes([d["route"] for d in core.db().activities.find(q, {"route": 1})])


@app.get("/race-predictions")
def race_predictions(days: int = Query(90, ge=1, le=730), user: dict = Depends(auth)):
    since = (core.today() - timedelta(days=days - 1)).isoformat()
    return list(core.db().race_predictions.find({"user_id": user["data_user_id"], "date": {"$gte": since}}, {"_id": 0, "user_id": 0}).sort("date", 1))


@app.get("/muscles/week")
def muscles_week(user: dict = Depends(auth)):
    since = (core.today() - timedelta(days=6)).isoformat()
    docs = core.db().activities.find({"user_id": user["data_user_id"], "type": "strength_training", "date": {"$gte": since}}, {"raw": 1})
    catalog = core.muscle_catalog()
    return core.merge_muscles([s for d in docs if (s := core.strength_summary(d.get("raw") or {}, catalog))])


@app.get("/trends/running")
def running_trends(weeks: int = Query(12, ge=1, le=52), user: dict = Depends(auth)):
    end = core.today()
    since = (core.week_start(end) - timedelta(days=7 * (weeks - 1))).isoformat()
    proj = {"date": 1, "duration_s": 1, **{f"raw.{f}": 1 for f in core.DYNAMICS_FIELDS.values()}}
    runs = list(core.db().activities.find({"user_id": user["data_user_id"], "type": {"$regex": "running"}, "date": {"$gte": since}}, proj))
    return core.weekly_dynamics(runs, end, weeks)


@app.get("/records")
def records(user: dict = Depends(auth)):
    _, best = core.pr_history(core.runs_with_splits(user["data_user_id"]))
    return [best[d] for d in core.BEST_SPLITS if d in best]


@app.get("/activities/{activity_id}")
def activity(activity_id: int, user: dict = Depends(auth)):
    uid = user["data_user_id"]
    doc = core.db().activities.find_one({"_id": activity_id, "user_id": uid})
    if not doc:
        raise HTTPException(404, "not found")
    if doc.get("details_v") != core.DETAILS_V:
        try:
            g, token = core.garmin_client(uid)
            core.ensure_details(g, doc)
            core.persist_if_refreshed(g, uid, token)
        except Exception:
            pass  # route is optional, the rest of the detail still renders
    if doc.get("type") == "strength_training":
        doc["strength"] = core.strength_summary(doc.get("raw") or {}, core.muscle_catalog())
    doc["extra"] = core.activity_extra(doc.get("raw") or {})
    if "running" in (doc.get("type") or ""):
        marks, _ = core.pr_history(core.runs_with_splits(uid))
        doc["prs"] = marks.get(doc["_id"], [])
        since = (date.fromisoformat(doc["date"]) - timedelta(days=120)).isoformat()
        prev = core.db().activities.find(
            {"user_id": uid, "type": doc["type"], "start_time": {"$lt": doc["start_time"]}, "date": {"$gte": since}},
            {"distance_m": 1, "avg_speed": 1, "avg_hr": 1},
        ).sort("start_time", -1)
        doc["similar"] = core.similar_runs(doc, list(prev))
    doc.pop("raw", None)
    return doc


@app.get("/today")
def today_view(user: dict = Depends(auth)):
    uid = user["data_user_id"]
    days = _daily(uid, *_range(None, None, core.HRV_BASELINE_DAYS + 1))
    latest = days[-1] if days else None
    week_start = (core.today() - timedelta(days=6)).isoformat()
    week_acts = list(core.db().activities.find({"user_id": uid, "date": {"$gte": week_start}}, NO_RAW).sort("start_time", -1))
    last_act = week_acts[0] if week_acts else core.db().activities.find_one({"user_id": uid}, NO_RAW, sort=[("start_time", -1)])
    summary = core.db().summaries.find_one({"user_id": uid, "period": "weekly"}, {"_id": 0}, sort=[("generated_at", -1)])
    last7 = days[-7:]
    load_by_date = {}
    for a in week_acts:
        load_by_date[a["date"]] = load_by_date.get(a["date"], 0) + (a.get("training_load") or 0)
    week_dates = [(core.today() - timedelta(days=i)).isoformat() for i in range(6, -1, -1)]
    return {
        "role": user["role"],
        "name": user["name"],
        "latest": latest,
        **core.insights(days),
        "spark": {k: [d.get(k) for d in last7] for k in ("date", "hrv_last_night", "rhr", "sleep_score", "bb_high", "stress_avg")},
        "week": {
            "count": len(week_acts),
            "distance_m": sum(a.get("distance_m") or 0 for a in week_acts),
            "duration_s": sum(a.get("duration_s") or 0 for a in week_acts),
            "load": sum(a.get("training_load") or 0 for a in week_acts),
            "active_days": len({a["date"] for a in week_acts}),
            "dates": sorted({a["date"] for a in week_acts}),
            "daily_load": [{"date": d, "load": round(load_by_date.get(d, 0))} for d in week_dates],
        },
        "last_activity": last_act,
        "summary": summary,
    }


@app.get("/summaries/latest")
def latest_summary(period: str = "weekly", user: dict = Depends(auth)):
    doc = core.db().summaries.find_one({"user_id": user["data_user_id"], "period": period}, {"_id": 0}, sort=[("generated_at", -1)])
    if not doc:
        raise HTTPException(404, "no summary yet")
    return doc


def _sync_recent(uid: str, d: date | None) -> dict:
    end = d or core.today()
    try:
        errors = core.sync_range(uid, end - timedelta(days=1), end, log=lambda *_: None, backoff=())
    except Exception as e:
        raise HTTPException(502, f"Garmin sync failed: {e}")
    return {"ok": not errors, "errors": errors}


@app.post("/sync")
def sync(d: date | None = Query(None, alias="date"), user: dict = Depends(owner_only)):
    return _sync_recent(user["data_user_id"], d)


@app.get("/cron/sync")
def cron_sync(authorization: str = Header("")):
    expected = f"Bearer {os.environ.get('CRON_SECRET', '')}"
    if not os.environ.get("CRON_SECRET") or not secrets.compare_digest(authorization, expected):
        raise HTTPException(401, "invalid cron secret")
    return _sync_recent(core.OWNER, None)
