# Deploying garmin-fit

Two parts ship separately: the backend deploys on every push to `main`, the Android app only when a version tag is pushed.

## Backend (Vercel)

- Project `garmin-fit` on Vercel, connected to `msyafiqroikhanm/garmin-fit`, Root Directory `backend`. Every push to `main` deploys to https://garmin-fit.vercel.app. The Vercel CLI from `backend/` no longer works for deploys; push instead.
- Region `sin1` (Singapore) in `backend/vercel.json`, next to the Mongo Atlas cluster in Jakarta (ap-southeast-3). Check with `curl -sI https://garmin-fit.vercel.app/today | grep x-vercel-id` (starts with `sin1`).
- Env vars on Vercel match the local `.env`: `MONGODB_URI`, `MONGODB_DB`, `FERNET_KEY`, `CRON_SECRET`, `APP_TZ`.
- Cron `0 3 * * *` (10:00 WIB) calls `/cron/sync`.
- After a change to indexes in `core.ensure_indexes`, run it once from the laptop: `.venv/bin/python backend/cli.py ensure-indexes`. Indexes are not created on requests.
- Garmin login only happens on the laptop (`cli.py login`), never on Vercel.

## Android app (GitHub Release + Obtainium)

1. Commit and push to `main`.
2. Tag and push: `git tag v0.X && git push origin v0.X` (bump from the latest tag, `git tag --sort=-creatordate | head -1`).
3. `.github/workflows/release.yml` builds `assembleRelease` with `versionName` from the tag and `versionCode` from the run number, signs it with the laptop's debug key (secret `DEBUG_KEYSTORE_B64`), checks the certificate SHA-256 `65623e22…324597`, and publishes `garmin-fit-v0.X.apk` as a GitHub Release.
4. The phone gets the update through Obtainium, which watches the repo's releases.

Watch the build: `gh run list -R msyafiqroikhanm/garmin-fit --limit 1`, then `gh run watch <id> -R msyafiqroikhanm/garmin-fit`. It takes about 15 minutes.

A release signed with any other key is rejected as an update on the phone, which is why CI checks the certificate.

## Pushing from this laptop

The global git config uses a work GitHub account, so a plain push to this personal repo fails. Push with:

```
git -c credential.helper= -c "credential.helper=!gh auth git-credential" -c credential.username=msyafiqroikhanm push
```

Same flags for pushing a tag (`… push origin v0.X`).

## Releases

| Tag | Date | What changed |
|---|---|---|
| v0.2 | 2026-10-05 | Responsive widgets, Monday week, English default with Indonesian per-app language |
| v0.3 | 2026-10-13 | Disk cache and shimmer loading, touchable charts, OSM route maps, motion and icons, training plan screens and widget line |
| v0.4 | 2026-10-13 | Units next to rolling numbers sit on the number's baseline again |
