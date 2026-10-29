# DESIGN.md: Kinetik

Visual direction for the garmin-fit Android app. Source: the Claude Design handoff "Kinetik" (2026-09). Code truth lives in `android/app/src/main/java/dev/garminfit/ui/Theme.kt` (`K` colors, `T` type styles) and `ui/Components.kt`.

Dial: ENERGY 3 / RHYTHM 2 / MOTION 2

## Identity

- Sporty, for a young runner, read at a glance in the morning and right after a run.
- The Today screen answers one question first: **"Hari ini boleh latihan apa?"** Numbers come after, each compared to the personal baseline.
- Identity motif: the **baseline band**. A track, a volt band for the personal normal range, and a status-colored dot for today's value (`FactorBar`). The same idea shows up as the green band in Tren charts and the optimal zone on the load gauge.
- Copy: English by default, Bahasa Indonesia when the app language is set to it (Android Settings > Apps > garmin-fit > Language, from `res/xml/locales_config.xml`). Both casual and short, no em dashes. Garmin terms stay as they are (Body Battery, Training Readiness, VO2max, Training load).
- Every user-facing string is written at its call site as `tr("English", "Indonesia")` (`Format.kt`), so widgets and plain helpers need no Context. Numbers and dates follow the app locale: "1,234" and "1.05" in English, "1.234" and "1,05" in Indonesian. Formatters are getters because a language change keeps the process alive.
- Backend text follows the app language: flags are worded in the app from their `key`, muscle groups come as keys (`CHEST`, `BACK`, ...), and the weekly summary carries an optional `en` copy next to the Indonesian one.

## Principles

1. Answer "what can I train today" first, then show the numbers.
2. Compare every number to the personal baseline, not to an absolute value.
3. Color means something. Status colors appear only for good, caution, or low.

## Color (dark only)

The app is always dark. Reason: it sits next to a dark watch face and gets read early in the morning and right after a run, and big condensed numbers on near-black read fastest. System bar icons are forced light.

| Token | Hex | Use |
|---|---|---|
| base | `#0A0F0B` | screen background, inner box in the hero |
| navBg | `#0F130F` | bottom nav |
| surface | `#141A15` | cards, tiles |
| track | `#222823` | bar tracks, neutral chips, dividers, nav top border |
| border | `#2A2F2B` | chart baseline |
| ink | `#F4F6F2` | primary text |
| muted | `#A0A6A1` | labels, captions, inactive nav |
| body2 | `#B9C0BA` | factor names |
| body3 | `#C9D0CA` | insight text under charts |
| good / volt | `#B1EF4A` | good status, the single accent, active nav, CTA |
| fair | `#FFB330` | caution |
| low | `#FF645F` | low / danger |
| onAccent | `#0A0F0B` | text on good/fair/low fills |

- Volt is both the accent and "good", on purpose. A card filled with volt (weekly summary, training status) is the loudest element on its screen.
- Alphas: baseline band in factor bars good@0.30; chart normal band good@0.12; status chip and flag backgrounds status@0.12 to 0.14 (low 0.14); other days' load bars good@0.45; slower splits good@0.5; VO2 categories below current good@0.30; load gauge zones @0.60.
- HR zones Z1 to Z5: `#67ADDD`, `#37D8C9`, `#B1EF4A`, `#FFB330`, `#FF645F`.
- Sleep stages: Deep `#4671B7`, REM `#67ADDD`, Light `#93C7D9`, Awake `#FF645F`.

## Type

- **Barlow Condensed** (500 to 800, 800 italic): numbers, headlines, caps labels. It is narrow enough to set an 80sp distance on a phone and has an athletic character.
- **Barlow** (400 to 700): reading text. It pairs with the condensed cut.
- **JetBrains Mono** 500: only for chart axis dates and small load numbers, where it reads as measurement.
- Every number uses `tnum`.

| Style | Spec |
|---|---|
| hero | Condensed 80sp 800 italic, lh 0.85 (activity distance) |
| display | Condensed 52 to 56sp 800 (readiness, VO2max) |
| statusWord | Condensed 40 to 48sp 800 italic, uppercase ("GAS!", "PRODUKTIF") |
| screenTitle | Condensed 36sp 800 uppercase |
| metric L / M / S | Condensed 36 / 28 to 30 / 20 to 24sp |
| cardTitle | Condensed 20sp 700 uppercase |
| label | Condensed 13sp 600 uppercase, tracking 0.12em, muted |
| body | Barlow 15sp, lh 1.45 |
| bodyS / caption | Barlow 13 to 14sp / 11 to 12sp |
| mono | JetBrains Mono 10 to 11sp |

## Space and shape

- Screen padding 16dp sides, 16 top, 24 bottom. 12dp between cards, 10dp between tiles.
- Padding marks weight: hero 20, card 18, tile 14.
- Radius: card 24, tile 20, inner box 16, bars 3 to 6, chips and pills fully round.
- No shadows or elevation. Hierarchy comes from fill color and size.

## Components

`Panel`, `Label`, `CardTitle`, `ScreenTitle`, `Value`, `Stat`, `StatusChip`, `NeutralChip`, `ChipRow`, `DeltaPill`, `ReadinessRing`, `FactorBar`, `SpanBar`, `SegmentBar`, `StackedBar`, `LineChart` (optional band, dashed second series, no axis column), `ChartDates`, `Segmented`, `AccentButton`, `Glyph`, `BackLink`, and the bottom nav in `MainActivity`.

Bottom nav: icon over label per tab. The selected tab gets a 56×32 volt pill behind its icon (icon `onAccent`, label `ink`); the others are `muted`. Labels stay visible because icons alone would make people guess.

Icons are Material Symbols Rounded (filled), copied as vector drawables into `res/drawable/ic_*.xml`, so there is no icon dependency. They are the native Android icon language and the rounded ends sit well with Barlow. Tint follows the color rule: `ink` or `muted` for structure, `good` only on volt back links and steppers, never a status color for a type. Activity rows carry a type icon in a neutral `track` circle (`activityIcon` in `Format.kt`). Arrows are icons, never text glyphs.

Card titles carry a muted icon (`CardTitle(icon = …)`) so cards are told apart while scrolling: route, show_chart, directions_run, timer, bar_chart, trending_up, bolt, monitor_heart, flag, fitness_center, calendar_month, emoji_events, speed, map, event_available. Plan sessions use check_circle (done, volt; partial, fair), cancel (missed, low), radio_button_unchecked (planned) and bedtime (rest). No sparkle or star icons.

Maps (`ui/MapView.kt`, osmdroid): OpenStreetMap's standard tiles, free with no key as long as the app sends its id as User-Agent, caches tiles and shows "© OpenStreetMap contributors". A color matrix turns them dark in the Kinetik greens (grayscale, inverted, squeezed between #101511 and #969E97). CARTO's dark tiles were the first choice but now need an API key. The card map is static so the page scroll never gets caught; tapping it or the expand button opens a full-screen map with pan and pinch, closed by the back link or the close button in the same corner. Routes are pace-colored polylines; the recap map draws every route at low alpha.

`drawRoute` (`ui/Route.kt`) draws a route on a native canvas with no map tiles (the share image still uses it): pace gradient low → fair → good, scaled to the run's own 10th to 90th percentile so every run shows contrast; white dot at start, volt dot at the finish. The same function draws the share image, in a single template color there.

`drawBody` (`ui/BodyMap.kt`, polygons in `ui/BodyData.kt` from react-body-highlighter, MIT) shows front and back figures: untouched muscles in `track`, secondary work as volt at 28%, primary work volt from 55% to full by set count. Muscles come from Garmin's exercise catalog via the backend; sets the watch could not name show as "Lainnya" and are not colored.

## Motion

Every animation runs once, when its data first appears, and has a job: it shows where a number came from or where you went. `rememberReveal(key)` drives most of them and is keyed to the data, so a background refresh with the same numbers does not replay anything. With the phone's animator scale off (`LocalReduceMotion`) everything snaps to its end state.

- Line charts draw in left to right (700 ms).
- Bars grow with a short stagger: Today's week, Recap distance, km splits. Segment bars light tick by tick.
- The readiness ring sweeps to its score; factor-bar dots slide from the middle of the normal band to today's value.
- Big numbers roll in like an odometer (`RollingText`): readiness, activity and recap distance, VO2max, plan %.
- Loading crossfades from card-shaped shimmer placeholders to content. After 3 s the placeholder counts the wait ("Waking the server · 4s") so a cold server reads as progress. The shimmer is the only thing that loops, and only while loading.
- Tabs and list↔detail fade with a small slide toward where you are going.
- A finished plan session's check pops in once.

Charts are touchable. Touch or drag sideways to read a point; release clears it; a mostly vertical drag is left to the page scroll. A haptic tick marks each new point. On Tren the card's big number and pill show the touched day. In "Sepanjang sesi" all charts share one index, the header shows the km and time, and a km ruler sits under the charts (distance comes from integrating speed, scaled to the recorded total). Bars (Today's week, Recap) and km split rows select the same way.

Tabs reopen instantly: `rememberLoad` keeps the last result per call site in memory and refreshes behind it. Loaders that pass a name (`rememberCachedLoad`) also keep it on disk, so a cold start paints yesterday's numbers right away and swaps in fresh ones; a failed refresh keeps the stale data.

## Screen map

- **Sign in**: "SIGN IN" in the status-word style, one line on what you get, the baseline band as a quiet mark, and Google's dark "Continue with Google" button (Google only, no passwords). A new account with no access token lands on "No data linked yet" with one action, Add access token.
- **Account** (avatar at the right of the Today header): profile, "Data you can see" (owner or viewer, add a token, viewers can stop seeing it), Garmin connection status for the owner, "Shared access" for the owner (each token with who added it and when it was last used, Revoke, Create invite token whose token is shown once with Copy), language note, Sign out. Add token and Create invite are bottom sheets.
- **Hari Ini**: header (date, greeting, Sync pill), today's activity right under the header when there is one ("Done today" in volt, big distance, and the route in pace colors, the worked muscles for strength, or the type icon; otherwise the last activity stays near the bottom), readiness hero with training advice, "Why this score?" factor bars, flags, Body Battery and steps tiles, sleep tile, this week's load bars (Monday to Sunday), last activity, weekly summary card.
- **Tren**: 7/30/90 day picker, four line charts (HRV, RHR, sleep score, Body Battery peak) with delta pill and one insight line each.
- **Aktivitas**: list with a search field (name or type, over the loaded year), and detail with route card (GPS activities, loaded from `/activities/{id}`), body map and exercise list for strength, hero distance, stat grid (moving vs total time, fastest pace, steps), "Sepanjang sesi" line charts (pace plotted as speed so faster is up, HR, cadence, power, elevation; summaries show averages only because points are bucket averages), running technique with Garmin's running-dynamics bands, fastest splits, comparison with the last 5 similar-distance runs (pace, HR, meters per heartbeat), impact (Body Battery, sweat, intensity minutes, VO2 max), training effect, HR zones with the user's bpm ranges, per-km splits.
- **Bagikan** (from the detail header): live preview of the share image on top, options below. Backdrop Foto / Kartu / Stiker (transparent PNG), layouts Bold / Minimal / Grid / Rute (Otot for strength), 9:16 or 4:5, ink Putih / Hitam / Volt, stat toggles, KINETIK wordmark. Drag and pinch move the overlay. `renderShare` (`ui/ShareRender.kt`) draws both the preview and the 1080 px export, so they match. Routes lose their first and last 200 m in shared images.
- **Rekap** (card on Latihan): month or year with ‹ › steps, hero km with change vs the previous period, sessions, time, active days, runs, strength, active-week streak, distance bars per week or month, "Peta lari" heatmap (routes starting within 3 km of the busiest start, drawn at low alpha so often-run streets glow), longest run and PRs. Shares through the same editor; every route in the shared map loses its first and last 200 m.
- **Latihan** also has "Prediksi lomba" (Garmin's 5K to marathon predictions with the change vs 30 days ago and a half-marathon line, faster drawn higher) and "Otot 7 hari terakhir" (summed body map plus big muscle groups with no primary sets).
- **Tren** ends with "Teknik lari per minggu": duration-weighted weekly cadence, ground contact and vertical ratio with the latest week's Garmin band.
- **Latihan** "Training plan": this week's sessions Monday to Sunday with status marks and the week's progress ticks. Opens the plan screen: goal, race countdown, overall % achieved, then each week as expandable session rows (target vs done, notes, link to the matched activity) and runs outside the plan. Today shows today's session with its % once something is done; the Readiness and Today widgets show it in one line. The plan is written by Claude (`/training-plan`) and scored on the server: per session, distance (or time) done against target, capped at 100%, 95% counts as done.
- **Latihan**: training status card, personal records (computed from stored runs; a run that beats an earlier best gets a PR chip in the list, "Rekor baru" on its split row and a PR line on share images), VO2max with category scale, training load with gauge and acute/chronic chart.

## Known limits

- VO2 category scale only has the Cooper Institute rows for age 20 to 29; outside that band the scale hides.
- Widgets use the Kinetik colors but the system condensed font, since Glance cannot load bundled fonts. Labels are condensed caps like `Label`, without the tracking (Glance has no letter spacing).
- Widgets are responsive (`SizeMode.Responsive`): each adds detail as it grows, and every header shows freshness ("Sync 17:20", or "Data from <day>" when stale).
  - Readiness 2×2: score, verdict word in its status color under it, tagline; taller adds HRV on the baseline band and today's plan session.
  - Today 4×2: verdict and score, HRV / RHR / Sleep / Battery, steps bar; tallest adds the first flag or the advice.
  - This week 4×2: sessions, distance (load when there is none), time, plan sessions done, and daily load bars Monday to Sunday, same as the Today card.
  - 7 day trend 4×2: HRV line in its status color over the normal band, RHR over a dashed 30 day average.
  - Last activity 4×1: type icon in a track circle, date and name, then distance, time and pace (HR only without a distance); two rows show a hero (distance, or time when there is none) and a stat row.
- Widget picker previews are screenshots of the real widgets (`res/drawable-nodpi/preview_*.png`), so they show the owner's data from the day they were taken and are English only. Retake them when a widget layout changes: on a debug build, `adb shell appwidget grantbind --package dev.garminfit`, start `.WidgetPreviewActivity`, and pull `/sdcard/Android/data/dev.garminfit/files/previews` (instructions in the activity). Take them on a day with activities this week, or This week shows zeros.
