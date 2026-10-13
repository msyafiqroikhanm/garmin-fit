# garmin-fit

Personal Garmin data (Forerunner 265S) → Vercel FastAPI + Mongo Atlas → Android app with home screen widgets.
Family members get read-only access through invite tokens.

## Setup

1. **Mongo Atlas**: create DB user `garmin-fit` (readWrite on `garmin_fit` only), Network Access `0.0.0.0/0`.
   Put the connection string in `.env` as `MONGODB_URI` (`FERNET_KEY` and `CRON_SECRET` are already generated).
2. **Python env**: `python3 -m venv .venv && .venv/bin/pip install -r backend/requirements.txt`
3. **Garmin login** (from the laptop, never from Vercel): `.venv/bin/python backend/cli.py login`
4. **Backfill**: test with `backend/cli.py backfill --from <7 days ago>`, check the numbers against Garmin Connect,
   then run `backend/cli.py backfill` for full history (resumable, ~1s per day).
5. **Vercel**: project `garmin-fit` is connected to this repo with Root Directory `backend`; every push to `main`
   deploys to https://garmin-fit.vercel.app. Env vars `MONGODB_URI`, `MONGODB_DB`, `FERNET_KEY`, `CRON_SECRET`, `APP_TZ`
   match `.env`. Cron runs daily at 03:00 UTC (10:00 WIB).
6. **Tokens**: `backend/cli.py token create saya --role owner`, `backend/cli.py token create ibu` (viewer by default).
   Tokens are shown once. `token list` / `token revoke <name>`.
7. **Android**: set `api.baseUrl=https://<project>.vercel.app/` in `android/local.properties`, then
   `cd android && ./gradlew assembleDebug` and install `app/build/outputs/apk/debug/app-debug.apk`.

## Weekly summary

Run `/fitness-summary` in Claude Code from this repo. It exports the last 35 days, writes the summary, and saves it to Mongo; the app shows it on the Hari Ini tab.

## CLI

| Command | What it does |
|---|---|
| `login` | Garmin login, stores the encrypted token in Mongo (password is not stored) |
| `backfill [--from DATE] [--force]` | Sync history, skips days already stored |
| `sync [--date DATE]` | Sync that day and the day before |
| `token create/list/revoke` | Manage app access tokens |
| `export --days N` | Compact JSON for the summary skill |
| `save-summary FILE` | Validate and store a summary |
| `save-plan FILE` | Validate and store a training plan (replaces the active one) |
| `plan-status` | Active plan with every session scored |
| `ensure-indexes` | Create Mongo indexes (run after index changes) |

## Training plan

Run `/training-plan` in Claude Code. Claude reads the last 84 days, drafts the plan with you, and saves it after you approve; the app shows it on Training, Today and the widgets.

Deploying and releasing: see [DEPLOY.md](DEPLOY.md).
