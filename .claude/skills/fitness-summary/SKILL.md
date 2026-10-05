---
name: fitness-summary
description: Generate the weekly garmin-fit training and recovery summary from Mongo data and save it for the Android app. Use when the user says /fitness-summary, "bikin summary mingguan", or "weekly summary".
---

# Weekly fitness summary

1. Export data (run from repo root):
   `.venv/bin/python backend/cli.py export --days 35`
   Output: `today`, `daily[]` (rhr, hrv_last_night, hrv_status, sleep_score, sleep_s, bb_high, stress_avg, readiness, training_status, acute_load, chronic_load, vo2max, steps), `activities[]`, `insights` (baseline + flags).

2. Pick the range: the last full Monday..Sunday week before `today`. The 4 weeks before it are the comparison baseline.

3. Analyse, citing only numbers present in the export:
   - Recovery: HRV avg vs baseline and vs `hrv_balanced_low/high`, RHR trend, sleep score and duration, body battery peaks.
   - Training: session count, total distance and time per activity type, training load, acute/chronic ratio, training status, VO2max change.
   - How recovery and load relate this week (e.g. HRV dropped after the two hardest sessions).
   - One to three concrete suggestions for next week, tied to a number above.
   Skip any metric that is null for the whole week. No medical claims or diagnoses.

4. Write `/tmp/garmin-fit-summary.json`. The top level is Bahasa Indonesia; `en` is the same summary in English (the app shows whichever matches its language). Both casual and direct, no em dashes:
   ```json
   {
     "period": "weekly",
     "range": {"from": "YYYY-MM-DD", "to": "YYYY-MM-DD"},
     "headline": "one sentence, max ~90 chars",
     "sections": [
       {"title": "Recovery", "body": "2-4 sentences"},
       {"title": "Latihan", "body": "2-4 sentences"},
       {"title": "Minggu depan", "body": "bulleted suggestions, one per line starting with '- '"}
     ],
     "en": {
       "headline": "same headline in English",
       "sections": [
         {"title": "Recovery", "body": "..."},
         {"title": "Training", "body": "..."},
         {"title": "Next week", "body": "- ..."}
       ]
     }
   }
   ```

5. Save it: `.venv/bin/python backend/cli.py save-summary /tmp/garmin-fit-summary.json`, then show the user the headline.
