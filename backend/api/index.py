import os
import secrets
import sys
import uuid
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.middleware.gzip import GZipMiddleware
from pydantic import BaseModel, Field
from pymongo import ReturnDocument

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import core  # noqa: E402

app = FastAPI(title="garmin-fit")
app.add_middleware(GZipMiddleware, minimum_size=1000)

NO_RAW = {"raw": 0, "route": 0, "series": 0}


def _touch(access: dict | None) -> None:
    """Family access shows when a token was last used; one write an hour is enough for that."""
    now = datetime.now(timezone.utc).replace(tzinfo=None)
    if access and (not access.get("last_used") or now - access["last_used"] > timedelta(hours=1)):
        core.db().access_tokens.update_one({"_id": access["_id"]}, {"$set": {"last_used": now}})


def signed_in(authorization: str = Header("")) -> dict:
    """A session from Google sign-in, or (until every phone runs v0.9) a bare access token from the old setup screen."""
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(401, "invalid token")
    d, h = core.db(), core.sha256(token)
    session = d.sessions.find_one({"_id": h})
    if session:
        user = d.users.find_one({"_id": session["user_id"]})
        if not user:
            raise HTTPException(401, "invalid token")
        access = d.access_tokens.find_one({"_id": user["access_id"], "revoked": False}) if user.get("access_id") else None
        _touch(access)
        return core.identity(user, access, h)
    # ponytail: legacy access tokens; drop this branch in v0.10 once every phone has signed in
    access = d.access_tokens.find_one({"_id": h, "revoked": False})
    if not access:
        raise HTTPException(401, "invalid token")
    _touch(access)
    return {**access, "kind": "token", "access_id": access["_id"]}


def session_only(user: dict = Depends(signed_in)) -> dict:
    if user["kind"] != "session":
        raise HTTPException(401, "sign in first")
    return user


def auth(user: dict = Depends(signed_in)) -> dict:
    if not user["data_user_id"]:
        raise HTTPException(403, "no access")
    return user


def owner_only(user: dict = Depends(auth)) -> dict:
    if user["role"] != "owner":
        raise HTTPException(403, "owner only")
    return user


def _verify_google(token: str) -> dict:
    from google.auth.transport import requests as grequests
    from google.oauth2 import id_token
    try:
        info = id_token.verify_oauth2_token(token, grequests.Request(), os.environ["GOOGLE_CLIENT_ID"])
    except ValueError:
        raise HTTPException(401, "invalid Google token")
    if not info.get("email_verified"):
        raise HTTPException(401, "Google email not verified")
    return info


class GoogleSignIn(BaseModel):
    id_token: str
    device: str | None = Field(None, max_length=80)


class AccessIn(BaseModel):
    token: str = Field(max_length=200)


class InviteIn(BaseModel):
    name: str = Field(min_length=1, max_length=40)


@app.post("/auth/google")
def auth_google(body: GoogleSignIn):
    info = _verify_google(body.id_token)
    now = datetime.now(timezone.utc)
    user = core.db().users.find_one_and_update(
        {"google_sub": info["sub"]},
        {"$set": {"email": info["email"], "name": info.get("given_name") or info.get("name"), "seen_at": now},
         "$setOnInsert": {"_id": uuid.uuid4().hex, "created_at": now}},
        upsert=True,
        return_document=ReturnDocument.AFTER,
    )
    session = secrets.token_urlsafe(32)
    core.db().sessions.insert_one({"_id": core.sha256(session), "user_id": user["_id"], "device": body.device, "created_at": now})
    return {"session": session, "name": user["name"]}


@app.post("/auth/logout")
def auth_logout(user: dict = Depends(session_only)):
    core.db().sessions.delete_one({"_id": user["session_id"]})
    return {"ok": True}


@app.get("/account")
def account(user: dict = Depends(session_only)):
    d = core.db()
    access = d.access_tokens.find_one({"_id": user["access_id"]}) if user["access_id"] else None
    session = d.sessions.find_one({"_id": user["session_id"]}, {"device": 1})
    out = {
        "name": user["name"],
        "email": user["email"],
        "device": session and session.get("device"),
        "access": None,
        "garmin": None,
        "invites": None,
    }
    if access:
        owners = d.access_tokens.find({"data_user_id": access["data_user_id"], "role": "owner", "revoked": False}, {"_id": 1})
        owner = d.users.find_one({"access_id": {"$in": [t["_id"] for t in owners]}}, {"name": 1})
        out["access"] = {"name": access["name"], "role": access["role"], "owner_name": owner and owner.get("name")}
    if user["role"] == "owner":
        uid = user["data_user_id"]
        garmin = d.garmin_sessions.find_one({"_id": uid}, {"updated_at": 1})
        last = d.daily.find_one({"user_id": uid}, {"synced_at": 1}, sort=[("date", -1)])
        out["garmin"] = {"connected": garmin is not None, "last_sync": last and last.get("synced_at")}
        tokens = list(d.access_tokens.find({"data_user_id": uid, "revoked": False}))
        users = list(d.users.find({"access_id": {"$in": [t["_id"] for t in tokens]}}, {"name": 1, "email": 1, "access_id": 1}))
        out["invites"] = [{**r, "self": r["id"] == user["access_id"]} for r in core.invite_rows(tokens, users)]
    return out


@app.post("/account/access")
def add_access(body: AccessIn, user: dict = Depends(session_only)):
    access = core.db().access_tokens.find_one({"_id": core.sha256(body.token.strip()), "revoked": False})
    if not access:
        raise HTTPException(400, "invalid or revoked token")
    core.db().users.update_one({"_id": user["user_id"]}, {"$set": {"access_id": access["_id"]}})
    return {"role": access["role"]}


@app.delete("/account/access")
def remove_access(user: dict = Depends(session_only)):
    core.db().users.update_one({"_id": user["user_id"]}, {"$unset": {"access_id": ""}})
    return {"ok": True}


@app.post("/account/invites")
def create_invite(body: InviteIn, user: dict = Depends(owner_only)):
    token, tid = core.create_access_token(body.name.strip(), "viewer", user["data_user_id"])
    return {"id": tid, "name": body.name.strip(), "token": token}


@app.delete("/account/invites/{invite_id}")
def revoke_invite(invite_id: str, user: dict = Depends(owner_only)):
    if invite_id == user["access_id"]:
        raise HTTPException(400, "can't revoke the token you're signed in with")
    r = core.db().access_tokens.update_one({"_id": invite_id, "data_user_id": user["data_user_id"]}, {"$set": {"revoked": True}})
    if not r.matched_count:
        raise HTTPException(404, "not found")
    return {"ok": True}


def _range(frm: date | None, to: date | None, default_days: int) -> tuple[str, str]:
    to = to or core.today()
    frm = frm or to - timedelta(days=default_days - 1)
    return frm.isoformat(), to.isoformat()


def _daily(uid: str, frm: str, to: str) -> list[dict]:
    return list(core.db().daily.find({"user_id": uid, "date": {"$gte": frm, "$lte": to}}, {**NO_RAW, "_id": 0}).sort("date", 1))


@app.get("/me")
def me(user: dict = Depends(signed_in)):
    profile = core.db().profile.find_one({"_id": user["data_user_id"]}, {"_id": 0}) if user["data_user_id"] else None
    return {"name": user["name"], "role": user["role"], "kind": user["kind"], "profile": profile}


@app.get("/daily")
def daily(frm: date | None = Query(None, alias="from"), to: date | None = None, user: dict = Depends(auth)):
    return _daily(user["data_user_id"], *_range(frm, to, 30))


@app.get("/activities")
def activities(frm: date | None = Query(None, alias="from"), to: date | None = None, user: dict = Depends(auth)):
    f, t = _range(frm, to, 90)
    q = {"user_id": user["data_user_id"], "date": {"$gte": f, "$lte": t}}
    acts = list(core.db().activities.find(q, NO_RAW).sort("start_time", -1))
    marks, _ = core.prs(user["data_user_id"])
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
    marks, _ = core.prs(uid)
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
    _, best = core.prs(user["data_user_id"])
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
        marks, _ = core.prs(uid)
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
    # calendar week, Monday first, so "this week" resets every Monday
    monday = core.week_start(core.today())
    week_start = monday.isoformat()
    week_acts = list(core.db().activities.find({"user_id": uid, "date": {"$gte": week_start}}, NO_RAW).sort("start_time", -1))
    last_act = week_acts[0] if week_acts else core.db().activities.find_one({"user_id": uid}, NO_RAW, sort=[("start_time", -1)])
    summary = core.db().summaries.find_one({"user_id": uid, "period": "weekly"}, {"_id": 0}, sort=[("generated_at", -1)])
    last7 = days[-7:]
    load_by_date = {}
    for a in week_acts:
        load_by_date[a["date"]] = load_by_date.get(a["date"], 0) + (a.get("training_load") or 0)
    week_dates = [(monday + timedelta(days=i)).isoformat() for i in range(7)]
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
        "plan": core.plan_today(core.plan_view(uid)),
    }


@app.get("/plan")
def plan(user: dict = Depends(auth)):
    return core.plan_view(user["data_user_id"])


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
