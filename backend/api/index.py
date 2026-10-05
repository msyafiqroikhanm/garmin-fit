import os
import secrets
import sys
from datetime import date, timedelta
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Query

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import core  # noqa: E402

app = FastAPI(title="garmin-fit")

NO_RAW = {"raw": 0, "route": 0}


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
    return list(core.db().activities.find(q, NO_RAW).sort("start_time", -1))


@app.get("/activities/{activity_id}")
def activity(activity_id: int, user: dict = Depends(auth)):
    uid = user["data_user_id"]
    doc = core.db().activities.find_one({"_id": activity_id, "user_id": uid})
    if not doc:
        raise HTTPException(404, "not found")
    if not doc.get("route_checked") and core.dig(doc, "raw", "hasPolyline"):
        try:
            g, token = core.garmin_client(uid)
            core.ensure_route(g, doc)
            core.persist_if_refreshed(g, uid, token)
        except Exception:
            pass  # route is optional, the rest of the detail still renders
    if doc.get("type") == "strength_training":
        doc["strength"] = core.strength_summary(doc.get("raw") or {}, core.muscle_catalog())
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
