import argparse
import getpass
import json
import secrets
import sys
from datetime import date, datetime, timedelta, timezone
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")
import core  # noqa: E402
from garminconnect import Garmin  # noqa: E402

COMPACT_EXCLUDE = {"raw": 0, "_id": 0, "user_id": 0, "synced_at": 0}


def cmd_login(_):
    email = input("Garmin email: ").strip()
    g = Garmin(email, getpass.getpass("Garmin password: "), prompt_mfa=lambda: input("MFA code: ").strip())
    g.login()
    core.save_session(core.OWNER, g.client.dumps())
    print(f"logged in as {g.get_full_name()}, token saved (password not stored)")


def cmd_backfill(a):
    end = core.today()
    if a.from_:
        start = date.fromisoformat(a.from_)
    else:
        g, _ = core.garmin_client(core.OWNER)
        acts = g.get_activities_by_date((end - timedelta(days=365 * 3)).isoformat(), end.isoformat())
        start = min((date.fromisoformat(x["startTimeLocal"][:10]) for x in acts), default=end - timedelta(days=30))
    print(f"backfill {start} .. {end} ({(end - start).days + 1} days)")
    errors = core.sync_range(core.OWNER, start, end, skip_existing=not a.force, pause=1.0)
    print(f"done, {len(errors)} errors")
    for e in errors[:20]:
        print("  ", e)


def cmd_predictions(a):
    start = date.fromisoformat(a.from_) if a.from_ else core.today() - timedelta(days=364)
    print(f"done, {core.backfill_predictions(core.OWNER, start, core.today())} days")


def cmd_sync(a):
    d = date.fromisoformat(a.date) if a.date else core.today()
    errors = core.sync_range(core.OWNER, d - timedelta(days=1), d)
    print("ok" if not errors else "\n".join(errors))


def cmd_token(a):
    t = core.db().access_tokens
    if a.action == "create":
        if not a.name:
            sys.exit("token create needs a name")
        token = secrets.token_urlsafe(32)
        t.insert_one({"_id": core.sha256(token), "name": a.name, "role": a.role, "data_user_id": core.OWNER,
                      "created_at": datetime.now(timezone.utc), "revoked": False})
        print(f"{a.name} ({a.role}) token, shown once:\n{token}")
    elif a.action == "list":
        for doc in t.find({}, {"_id": 0}):
            print(f"{doc['name']:<15} {doc['role']:<7} {'REVOKED' if doc['revoked'] else 'active':<8} {doc['created_at']:%Y-%m-%d}")
    elif a.action == "revoke":
        r = t.update_many({"name": a.name}, {"$set": {"revoked": True}})
        print(f"revoked {r.modified_count}")


def cmd_export(a):
    frm = (core.today() - timedelta(days=a.days - 1)).isoformat()
    q = {"user_id": core.OWNER, "date": {"$gte": frm}}
    out = {
        "today": core.today().isoformat(),
        "daily": list(core.db().daily.find(q, COMPACT_EXCLUDE).sort("date", 1)),
        "activities": list(core.db().activities.find(q, {**COMPACT_EXCLUDE, "_id": 1}).sort("start_time", 1)),
    }
    days_all = list(core.db().daily.find({"user_id": core.OWNER}, COMPACT_EXCLUDE).sort("date", -1).limit(core.HRV_BASELINE_DAYS + 1))
    out["insights"] = core.insights(days_all[::-1])
    print(json.dumps(out, default=str, ensure_ascii=False, indent=1))


def cmd_save_summary(a):
    s = json.loads(Path(a.file).read_text())
    ok = (
        isinstance(s.get("headline"), str)
        and s.get("period") in ("weekly", "daily")
        and all(isinstance(s.get("range", {}).get(k), str) for k in ("from", "to"))
        and isinstance(s.get("sections"), list)
        and all(isinstance(x, dict) and isinstance(x.get("title"), str) and isinstance(x.get("body"), str) for x in s["sections"])
    )
    if not ok:
        sys.exit("invalid summary: need period, range{from,to}, headline, sections[{title,body}]")
    doc = {k: s[k] for k in ("period", "range", "headline", "sections")}
    core.db().summaries.insert_one({**doc, "user_id": core.OWNER, "generated_at": datetime.now(timezone.utc)})
    print(f"saved {doc['period']} summary {doc['range']['from']}..{doc['range']['to']}")


def main():
    p = argparse.ArgumentParser(prog="cli.py")
    sub = p.add_subparsers(required=True)
    sub.add_parser("login").set_defaults(fn=cmd_login)
    b = sub.add_parser("backfill")
    b.add_argument("--from", dest="from_")
    b.add_argument("--force", action="store_true")
    b.set_defaults(fn=cmd_backfill)
    pr = sub.add_parser("predictions", help="backfill Garmin race predictions")
    pr.add_argument("--from", dest="from_")
    pr.set_defaults(fn=cmd_predictions)
    s = sub.add_parser("sync")
    s.add_argument("--date")
    s.set_defaults(fn=cmd_sync)
    t = sub.add_parser("token")
    t.add_argument("action", choices=["create", "list", "revoke"])
    t.add_argument("name", nargs="?")
    t.add_argument("--role", choices=["owner", "viewer"], default="viewer")
    t.set_defaults(fn=cmd_token)
    e = sub.add_parser("export")
    e.add_argument("--days", type=int, default=35)
    e.set_defaults(fn=cmd_export)
    ss = sub.add_parser("save-summary")
    ss.add_argument("file")
    ss.set_defaults(fn=cmd_save_summary)
    a = p.parse_args()
    a.fn(a)


if __name__ == "__main__":
    main()
