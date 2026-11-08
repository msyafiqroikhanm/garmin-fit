---
name: training-plan
description: Build or revise the garmin-fit training plan with the user, then save it so the Android app shows it and scores each session against Garmin data. Use when the user says /training-plan, "bikin training plan", "susun program latihan", "revisi plan", or asks to plan weeks of training toward a race.
---

# Training plan

The plan is made in conversation. Nothing is saved until the user says the draft is good.

1. Pull the data (run from repo root):
   - `.venv/bin/python backend/cli.py export --days 84`: daily recovery (HRV, RHR, sleep, readiness, acute/chronic load, VO2max, training status), every activity with distance, duration, pace (`avg_speed` m/s), HR, training load, splits, the app's workout logs in `workouts` (date, day, completed, reps, volume) and Strength 28 progress in `program` (`next_day`, `done_days`, `runs_completed`).
   - `.venv/bin/python backend/cli.py plan-status`: the active plan with each session scored (`status`, `pct`), weekly %, overall %, and runs done outside the plan. `plan: null` means there is none yet.
   Race predictions and records are in the app (Training tab); ask the user if you need them and they are not in the export.

2. Ask before drafting, one short round:
   - Goal and date (race, distance, target time) or a general aim.
   - Which days they can train, and anything fixed (gym days, travel, fasting month).
   - For a revision: what went wrong or changed. Read `plan-status` first and start from the missed and partial sessions.

3. Hard rules for this user:
   - Mon to Fri is work. Weekday runs are easy, at most 6 km, or one quality session of about 65 minutes.
   - Long runs and race rehearsals go on Sat or Sun only. A missed long run moves to the next weekend, never to a weekday.
   - Today applies the recovery rule on its own (`plan_adjust` in `core.py`: two HRV nights under Garmin's floor plus RHR 3+ over its mean turns quality into easy, cuts a long run 30%, and after 3 days calls an easy week). Don't repeat it in session notes; tell the user it exists.
   - Judge recovery from overnight HRV and RHR. Garmin undercounts this user's sleep (they wake for Subuh and sleep again), so low sleep or low readiness alone is not a reason to cut volume. Ask before calling it sleep debt.
   - Raise weekly volume by about 10% at most, with a lighter week every 3 to 4 weeks, and a taper before a race.
   - Base paces on recent runs and the HR zones in the export, not on generic tables.
   - Strength sessions follow the app's program Strength 28 (Workout tab) and carry `"workout": {"program": "strength28", "day": N}`. Days run in order from `program.next_day` in the export. Template C days (shoulders, back, triceps and legs, `day % 3 == 0`) never fall on the day before a long run or a quality run. Never put two strength days back to back. Use `target.duration_s` = the day's minutes × 60 (the Workout tab shows minutes per day). Garmin's exercise names for these sessions are mostly wrong (push-ups read as curls), so trust the watch for date, duration and HR only; the app's workout log has the real sets.

4. Grill the draft before saving: show it as a table per week (day, session, target, pace or zone, why). Push back on anything that breaks a rule above, even if the user asks for it, and say which rule. Iterate until the user approves.

5. Write `/tmp/garmin-fit-plan.json`. Text fields are `{"id": "...", "en": "..."}`: casual, short, no em dashes.
   ```json
   {
     "name": "HM Jakarta build",
     "goal": {"id": "Half marathon di bawah 2 jam", "en": "Half marathon under 2 hours"},
     "start": "YYYY-MM-DD",
     "end": "YYYY-MM-DD",
     "race_date": "YYYY-MM-DD",
     "weeks": [
       {"start": "YYYY-MM-DD", "goal": {"id": "Minggu ringan biar badan nyerap 3 minggu sebelumnya.", "en": "Easy week so the body absorbs the last three."}}
     ],
     "sessions": [
       {"date": "YYYY-MM-DD", "kind": "run", "type": "easy",
        "title": {"id": "Easy run", "en": "Easy run"},
        "target": {"distance_m": 6000},
        "pace": {"min_s_km": 400, "max_s_km": 430},
        "hr_zone": 2,
        "notes": {"id": "Santai, bisa ngobrol.", "en": "Easy, conversational."}},
       {"date": "YYYY-MM-DD", "kind": "strength", "type": "strength", "title": {"id": "Strength 28 Day 4", "en": "Strength 28 Day 4"}, "target": {"duration_s": 960}, "workout": {"program": "strength28", "day": 4}},
       {"date": "YYYY-MM-DD", "kind": "rest", "type": "rest", "title": {"id": "Istirahat", "en": "Rest"}}
     ]
   }
   ```
   - `kind`: `run`, `strength`, `other`, `rest`. It decides which activity of that day counts (Garmin running types, `strength_training`, anything else). Only `kind: strength` may carry `workout`; a strength day with no watch activity is scored from the app's workout log of that day.
   - `type`: `easy`, `long`, `tempo`, `interval`, `recovery`, `race`, `strength`, `rest`, `other`.
   - `target`: `distance_m` or `duration_s`. The app scores done / target, capped at 100%, and calls 95% or more done. Without a target any activity of that kind counts as done.
   - `weeks`: one entry per plan week, `start` is that week's Monday. The goal says what the week is for and why its sessions look like that, in 1 to 2 short sentences. The app shows it under each week. Always write them.
   - `notes` is what the user follows on the phone, so write every session out in full (rest days too). The app reads a small markup, one item per line (`\n`):
     - first line(s), before any heading: the session's aim in one line. Today shows only this until "Show steps" is tapped.
     - `## Heading` for a section; `1. text` for numbered steps; `- text` for bullets. Wrapped lines hang under the text, not the number.
     - Runs: aim, warm up, main set (pace, HR, duration or distance), cool down, cues, when to stop or ease off, and a short version for a busy day.
     - Strength: ground rules (effort, rest, how to progress), warm up, then one `## 1. Move · sets×reps` heading per exercise with tempo, cues and knee or ankle notes as bullets, then a busy-day version.
     - Rest: what's fine to do (easy walk, mobility, the daily balance work).
     - After a week review, append the verdict to each past session as the last section: `## Hasil` (id) / `## Result` (en) with 2–3 bullets: what was done against the target, what went right or wrong with numbers (HR per km, pace), and one fix for next time. Replace an existing one rather than adding a second.
   - `race_date`, `pace`, `hr_zone`, `notes`, `weeks` are optional to the validator. Every session date must sit between `start` and `end`.

6. Save: `.venv/bin/python backend/cli.py save-plan /tmp/garmin-fit-plan.json`. The previous active plan becomes `replaced`; there is one active plan at a time. The validator prints what it needs if the file is wrong.

7. Check `plan-status`, then tell the user in one line what is saved and that it shows in the app under Training (and on Today and the widgets for today's session).
