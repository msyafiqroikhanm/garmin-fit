---
name: training-plan
description: Build or revise the garmin-fit training plan with the user, then save it so the Android app shows it and scores each session against Garmin data. Use when the user says /training-plan, "bikin training plan", "susun program latihan", "revisi plan", or asks to plan weeks of training toward a race.
---

# Training plan

The plan is made in conversation. Nothing is saved until the user says the draft is good.

1. Pull the data (run from repo root):
   - `.venv/bin/python backend/cli.py export --days 84`: daily recovery (HRV, RHR, sleep, readiness, acute/chronic load, VO2max, training status) and every activity with distance, duration, pace (`avg_speed` m/s), HR, training load, splits.
   - `.venv/bin/python backend/cli.py plan-status`: the active plan with each session scored (`status`, `pct`), weekly %, overall %, and runs done outside the plan. `plan: null` means there is none yet.
   Race predictions and records are in the app (Training tab); ask the user if you need them and they are not in the export.

2. Ask before drafting, one short round:
   - Goal and date (race, distance, target time) or a general aim.
   - Which days they can train, and anything fixed (gym days, travel, fasting month).
   - For a revision: what went wrong or changed. Read `plan-status` first and start from the missed and partial sessions.

3. Hard rules for this user:
   - Mon to Fri is work. Weekday runs are easy, at most 6 km, or one quality session of about 65 minutes.
   - Long runs and race rehearsals go on Sat or Sun only. A missed long run moves to the next weekend, never to a weekday.
   - Judge recovery from overnight HRV and RHR. Garmin undercounts this user's sleep (they wake for Subuh and sleep again), so low sleep or low readiness alone is not a reason to cut volume. Ask before calling it sleep debt.
   - Raise weekly volume by about 10% at most, with a lighter week every 3 to 4 weeks, and a taper before a race.
   - Base paces on recent runs and the HR zones in the export, not on generic tables.

4. Grill the draft before saving: show it as a table per week (day, session, target, pace or zone, why). Push back on anything that breaks a rule above, even if the user asks for it, and say which rule. Iterate until the user approves.

5. Write `/tmp/garmin-fit-plan.json`. Text fields are `{"id": "...", "en": "..."}`: casual, short, no em dashes.
   ```json
   {
     "name": "HM Jakarta build",
     "goal": {"id": "Half marathon di bawah 2 jam", "en": "Half marathon under 2 hours"},
     "start": "YYYY-MM-DD",
     "end": "YYYY-MM-DD",
     "race_date": "YYYY-MM-DD",
     "sessions": [
       {"date": "YYYY-MM-DD", "kind": "run", "type": "easy",
        "title": {"id": "Easy run", "en": "Easy run"},
        "target": {"distance_m": 6000},
        "pace": {"min_s_km": 400, "max_s_km": 430},
        "hr_zone": 2,
        "notes": {"id": "Santai, bisa ngobrol.", "en": "Easy, conversational."}},
       {"date": "YYYY-MM-DD", "kind": "strength", "type": "strength", "title": {"id": "Gym: kaki dan core", "en": "Gym: legs and core"}, "target": {"duration_s": 2700}},
       {"date": "YYYY-MM-DD", "kind": "rest", "type": "rest", "title": {"id": "Istirahat", "en": "Rest"}}
     ]
   }
   ```
   - `kind`: `run`, `strength`, `other`, `rest`. It decides which activity of that day counts (Garmin running types, `strength_training`, anything else).
   - `type`: `easy`, `long`, `tempo`, `interval`, `recovery`, `race`, `strength`, `rest`, `other`.
   - `target`: `distance_m` or `duration_s`. The app scores done / target, capped at 100%, and calls 95% or more done. Without a target any activity of that kind counts as done.
   - `race_date`, `pace`, `hr_zone`, `notes` are optional. Every session date must sit between `start` and `end`.

6. Save: `.venv/bin/python backend/cli.py save-plan /tmp/garmin-fit-plan.json`. The previous active plan becomes `replaced`; there is one active plan at a time. The validator prints what it needs if the file is wrong.

7. Check `plan-status`, then tell the user in one line what is saved and that it shows in the app under Training (and on Today and the widgets for today's session).
