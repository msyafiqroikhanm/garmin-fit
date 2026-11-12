# DESIGN.md: Kinetik, a personal training journal

Direction: **catatan latihan personal yang atletis**. Keep Kinetik's dark green-black, Barlow Condensed numbers, Plus Jakarta Sans text and personal baseline band. Change the composition, not the identity: a session, result, agenda or observation leads; supporting measurements do not all get their own card.

Code truth: `android/app/src/main/java/dev/garminfit/ui/Theme.kt` (`K`, `T`), `ui/Components.kt`, and the screen composables.

Dial: ENERGY 2 / RHYTHM 3 / MOTION 2

## References used

Visually inspected through [style.ai.id](https://style.ai.id/ui-components/), not installed as Android dependencies:

- [mapcn](https://mapcn.dev/): quiet ground, a dominant route and controls anchored to a corner. Today's result gives the pace-colored route its own wide canvas; the activity detail retains its native OSM map and expand flow. No map effects or web library.
- [Evil Charts](https://evilcharts.com/): put the data question before the plot, keep the series readable and reveal exact values through interaction. Trends uses one selected chart with a numerical observation, compact metric selection and optional explanation, rather than four identical chart cards.

These are composition references within one Kinetik system, not extra visual themes.

## Identity

- A runner's daily journal, read in the morning and after a session. Routes, worked muscles and actual training notes supply its visual character.
- Today answers what matters at this moment: before training, the planned session; after training, the result and what comes next. With no session, readiness leads. A supporting readiness score must not compete with the activity distance or the session title.
- Identity motif: the **baseline band**. A track, a volt band for the personal normal range, and a status-colored dot for today's value (`FactorBar`). The same idea shows up as the green band in Tren charts and the optimal zone on the load gauge.
- Copy: English by default, Bahasa Indonesia when the app language is set to it (Android Settings > Apps > garmin-fit > Language, from `res/xml/locales_config.xml`). Both casual and short, no em dashes. Garmin terms stay as they are (Body Battery, Training Readiness, VO2max, Training load).
- Every user-facing string is written at its call site as `tr("English", "Indonesia")` (`Format.kt`), so widgets and plain helpers need no Context. Numbers and dates follow the app locale: "1,234" and "1.05" in English, "1.234" and "1,05" in Indonesian. Formatters are getters because a language change keeps the process alive.
- Backend text follows the app language: flags are worded in the app from their `key`, muscle groups come as keys (`CHEST`, `BACK`, ...), and the weekly summary carries an optional `en` copy next to the Indonesian one.

## Principles

1. Give each screen one lead: Today's session/result, Workout's content and muscles, Training's plan, the plan's weekly agenda, Trends' selected observation.
2. Compare measurements with their recorded baseline or a clearly named period average. Do not turn a reference band into an unqualified health claim.
3. Use neutral ink/fill/shape for primary navigation and actions; retain status and zone colors for data.
4. Repeat equivalent objects, not an entire screen recipe. Activities, agenda sessions, records and body-focus routines need consistent rows. A route, a body map and a baseline comparison need different compositions.

## Color (dark only)

The app is always dark. Reason: it sits next to a dark watch face and gets read early in the morning and right after a run, and big condensed numbers on near-black read fastest. System bar icons are forced light.

| Token | Hex | Use |
|---|---|---|
| base | `#0A0F0B` | screen background and quiet canvas |
| surface | `#141A15` | bounded cards, selected metric row |
| track | `#222823` | bar tracks, neutral chips, dividers |
| line | `#F4F6F2` @ 7% | hairline edge on cards, the nav pill, segmented, the Sync pill |
| border | `#2A2F2B` | chart baseline |
| ink | `#F4F6F2` | primary text, primary button fill, interaction marks |
| muted | `#A0A6A1` | labels, captions, inactive nav |
| body2 | `#B9C0BA` | factor names |
| body3 | `#C9D0CA` | insight text under charts |
| good / volt | `#B1EF4A` | good status, baseline band, completed plan work, route finish and muscle data |
| fair | `#FFB330` | caution |
| low | `#FF645F` | low / danger |
| onAccent | `#0A0F0B` | text on good/fair/low fills |

- Primary buttons (`AccentButton`), back links and section actions use ink. The selected nav icon uses ink on a track pill; the avatar is neutral for every role. Ownership remains explicit in Account text. Material primary is ink. These controls do not imply a good body state.
- Weekly summaries and Garmin training status no longer use full status-colored fills. The summary is a surface card; Garmin's status word is small and status-colored. Alerts, status chips, HR zones, sleep stages, route pace and muscle intensity retain their data colors.
- Alphas: baseline band in factor bars good@0.30; chart normal band good@0.12; status chip and flag backgrounds status@0.12 to 0.14 (low 0.14); other days' load bars good@0.45; slower splits good@0.5; VO2 categories below current good@0.30; load gauge zones @0.60.
- HR zones Z1 to Z5: `#67ADDD`, `#37D8C9`, `#B1EF4A`, `#FFB330`, `#FF645F`.
- Sleep stages: Deep `#4671B7`, REM `#67ADDD`, Light `#93C7D9`, Awake `#FF645F`.

## Type

- **Barlow Condensed** (500 to 800, 800 italic): numbers and the one status word per screen. It is narrow enough to set an 80sp distance on a phone and has an athletic character.
- **Plus Jakarta Sans** (variable, 400 to 800): every heading, label and paragraph, in sentence case. Headings get tighter tracking as they grow. Caps were dropped because a screen full of condensed caps read as shouting; the numbers keep the sporty voice. (`barlow_semibold` stays only for the share image labels.)
- **JetBrains Mono** 500: only for chart axis dates and small load numbers, where it reads as measurement.
- Every number uses `tnum`.

| Style | Spec |
|---|---|
| hero | Condensed 80sp 800 italic, lh 0.85 (activity distance) |
| display | Condensed 52 to 56sp 800 (readiness, VO2max) |
| statusWord | Condensed 44 to 48sp 800 italic, uppercase ("GAS!", "PRODUKTIF"), the only caps left |
| screenTitle | Jakarta 32sp 700, tracking -0.035em |
| headline | Jakarta 28sp 700, tracking -0.03em, lh 1.12; Today's session and Trends observation |
| sectionTitle | Jakarta 20sp 700, tracking -0.025em (heading above a group of cards) |
| sheetTitle | Jakarta 24sp 700, tracking -0.03em |
| greeting | Jakarta 26sp 700, tracking -0.03em |
| metric L / M / S | Condensed 36 / 28 to 30 / 20 to 24sp |
| cardTitle | Jakarta 16sp 700, tracking -0.015em |
| label | Jakarta 12.5sp 600, muted, sentence case |
| body | Jakarta 15sp, lh 1.5 |
| bodyS / caption | Jakarta 13 to 14sp / 11 to 12sp |
| mono | JetBrains Mono 10 to 11sp |

## Space and shape

- Screen padding remains 16dp sides/top and 24dp bottom. Related elements sit 6–14dp apart; `SectionHeader` adds 28dp above the heading, in addition to its parent's normal gap. A new group must read farther apart than values within one group.
- Cards retain 18dp default padding, 24dp radius and a 1dp hairline. Compact grouped recovery content uses 16dp padding and 20dp radius; agenda date chips use 12dp radius. Controls remain round where their interaction warrants it.
- Use cards to bound an action/result group, expandable summary, calendar or dense visual analysis. Use the screen background for primary headlines, baseline factors, weekly load strip, body-focus routines, predictions, records and agenda days.
- No shadows, decorative glow, shaders or glass. Hierarchy comes from content, typography, spacing, restrained fill and a hairline when a boundary is needed.
- Repetition is correct for peers: Activities rows, body-focus rows, agenda days, records and prediction distances. Do not invent different styles for each peer or replace uniform cards with a decorative bento grid.

## Components

`Panel` (hairline edge), `Label`, `CardTitle`, `ScreenTitle`, `SectionHeader`, `Value`, `Stat`, `StatusChip`, `NeutralChip`, `ChipRow`, `DeltaPill`, `ReadinessRing` (size follows its hierarchy), `FactorBar`, `SpanBar`, `SegmentBar`, `StackedBar`, `LineChart` (band, optional dashed series, soft area fill, latest point and scrubbing), `ChartDates`, `Segmented`, `AccentButton`, `Glyph` and `BackLink` remain the house components. `T.headline` is the session/observation style. `ActivityRow` is reused for Today's last activity.

Bottom nav: solid floating pill, surface fill, hairline, radius 28, 12dp from the screen sides. Selected icon: ink on a 52×30 track pill; selected label bold ink, others muted. Content stops above it. Labels remain visible.

Primary action: ink-filled `AccentButton` with dark text. Back links and section actions are ink text. Selection is communicated by fill, weight and semantics, not a body-status color.

Icons are bundled Material Symbols Rounded vectors (`res/drawable/ic_*.xml`). Structure and activity types use ink/muted; real status marks retain status colors. Account avatars are neutral track circles with ink initials. Arrows use vector icons.

Card titles carry a muted icon (`CardTitle(icon = …)`) so cards are told apart while scrolling: route, show_chart, directions_run, timer, bar_chart, trending_up, bolt, monitor_heart, flag, fitness_center, calendar_month, emoji_events, speed, map, event_available. Plan sessions use check_circle (done, volt; partial, fair), cancel (missed, low), radio_button_unchecked (planned) and bedtime (rest). No sparkle or star icons.

Maps (`ui/MapView.kt`, osmdroid): OpenStreetMap's standard tiles, free with no key as long as the app sends its id as User-Agent, caches tiles and shows "© OpenStreetMap contributors". A color matrix turns them dark in the Kinetik greens (grayscale, inverted, squeezed between #101511 and #969E97). CARTO's dark tiles were the first choice but now need an API key. The card map is static so the page scroll never gets caught; tapping it or the expand button opens a full-screen map with pan and pinch, closed by the back link or the close button in the same corner. Routes are pace-colored polylines; the recap map draws every route at low alpha.

`drawRoute` (`ui/Route.kt`) draws a route on a native canvas with no map tiles (the share image still uses it): pace gradient low → fair → good, scaled to the run's own 10th to 90th percentile so every run shows contrast; white dot at start, volt dot at the finish. The same function draws the share image, in a single template color there.

`drawBody` (`ui/BodyMap.kt`, polygons in `ui/BodyData.kt` from react-body-highlighter, MIT) shows front and back figures: untouched muscles in `track`, secondary work as volt at 28%, primary work volt from 55% to full by set count. Muscles come from Garmin's exercise catalog via the backend; sets the watch could not name show as "Lainnya" and are not colored.

## Motion

Every animation runs once, when its data first appears, and has a job: it shows where a number came from or where you went. `rememberReveal(key)` drives most of them and is keyed to the data, so a background refresh with the same numbers does not replay anything. With the phone's animator scale off (`LocalReduceMotion`) everything snaps to its end state.

- Line charts draw in left to right (700 ms).
- Bars grow with a short stagger: Today's week, Recap distance, km splits. Segment bars light tick by tick.
- The readiness ring sweeps to its score; factor-bar dots slide from the middle of the normal band to today's value.
- Big numbers roll in where they explain a change: readiness, activity and recap distance, VO2max and the plan's completed-session count. Session day numbers are context, not animated heroes.
- Loading crossfades from card-shaped shimmer placeholders to content. After 3 s the placeholder counts the wait ("Waking the server · 4s") so a cold server reads as progress. The shimmer is the only thing that loops, and only while loading.
- Tabs and list↔detail fade with a small slide toward where you are going.
- A finished plan session's check pops in once.

Charts are touchable. Touch or drag sideways to read a point; release clears it; a mostly vertical drag is left to the page scroll. A haptic tick marks each new point. On Trends the selected metric's value and date chip follow the touched day; its observation describes the latest reading. In "Sepanjang sesi" all charts share one index, the header shows km and time, and a km ruler sits underneath. Weekly load bars and km split rows select the same way.

Tabs reopen instantly: `rememberLoad` keeps the last result per call site in memory and refreshes behind it. Loaders that pass a name (`rememberCachedLoad`) also keep it on disk, so a cold start paints yesterday's numbers right away and swaps in fresh ones; a failed refresh keeps the stale data.

## Screen map

- **Sign in**: the wordmark, the baseline band large with "low · your normal · high" under it as the one picture, the headline "Know what you can train today." and one line on what you get, then Google's dark "Continue with Google" button (Google only, no passwords). A new account with no access token lands on "No data linked yet": the band with an empty dashed dot, two numbered steps, one action, Add access token.
- **Account** (avatar at the right of the Today header): profile, "Data you can see" (owner or viewer, add a token, viewers can stop seeing it), Garmin connection status for the owner, "Shared access" for the owner (each token with who added it and when it was last used, Revoke, Create invite token whose token is shown once with Copy), language note, Sign out. Add token and Create invite are bottom sheets.
- **Today**: date/greeting, neutral Sync pill and Account avatar. After a session: name, dominant distance (or duration), a small time/pace/HR strip, wide route/body-map canvas opening activity detail, then a compact plan-status row and "What's next" advice. Before a session: its title leads directly on the background, target, any recovery adjustment/reason, aim with Show steps, strength Start when available, and a compact link to the plan/week. With no non-rest session: readiness ring, verdict, advice and chips lead; a rest day still links to its plan. Below the lead, a compact readiness verdict appears only if not already shown, followed by unboxed baseline factors and flags. Recovery groups Body Battery, steps and sleep in one bounded strip. This week's stats and scrubbable daily load bars sit on the background; the last activity reuses an Activities row when there was no activity today. The weekly summary is a neutral expandable headline card.
- **Workout**: Resume remains first when a workout is unfinished. Otherwise the next session title leads, with day/week/program in small context text, a 200dp body map beside time, exercise count and named muscles, a preview of main exercise names, and Start workout. The program card shows days completed, a compact progress strip and a collapsible 28-day calendar; difficulty remains visible to owners. Body-focus routines form a repeated divided list. This week's log strip, history, library, rest settings and voice coach remain available. Preview editing, swaps, player countdowns, rest, pause/restart/quit, partial/discard, offline queue, watch linking and hidden nav while playing are unchanged. A plan strength session with a Strength 28 day retains its Start action.
- **Trends**: 7/30/90 range picker, then one selected metric chart. The headline compares the latest HRV with the recorded normal band, or the selected metric with its period average. It describes numbers, not a new training recommendation. The value, delta, average, date and point scrubbing remain available. Four compact selectable rows expose available HRV, RHR, sleep score and peak Body Battery values; selecting one replaces the main chart. Missing metrics do not fabricate readings. Static explanation is behind "What does this show?". Weekly running form follows as an unboxed section with its existing charts.
- **Aktivitas**: list with a search field (name or type, over the loaded year), and detail with route card (GPS activities, loaded from `/activities/{id}`), body map and exercise list for strength, hero distance, stat grid (moving vs total time, fastest pace, steps), "Sepanjang sesi" line charts (pace plotted as speed so faster is up, HR, cadence, power, elevation; summaries show averages only because points are bucket averages), running technique with Garmin's running-dynamics bands, fastest splits, comparison with the last 5 similar-distance runs (pace, HR, meters per heartbeat), impact (Body Battery, sweat, intensity minutes, VO2 max), training effect, HR zones with the user's bpm ranges, per-km splits.
- **Bagikan** (from the detail header): live preview of the share image on top, options below. Backdrop Foto / Kartu / Stiker (transparent PNG), layouts Bold / Minimal / Grid / Rute (Otot for strength), 9:16 or 4:5, ink Putih / Hitam / Volt, stat toggles, KINETIK wordmark. Drag and pinch move the overlay. `renderShare` (`ui/ShareRender.kt`) draws both the preview and the 1080 px export, so they match. Routes lose their first and last 200 m in shared images.
- **Rekap** (card on Latihan): month or year with ‹ › steps, hero km with change vs the previous period, sessions, time, active days, runs, strength, active-week streak, distance bars per week or month, "Peta lari" heatmap (routes starting within 3 km of the busiest start, drawn at low alpha so often-run streets glow), longest run and PRs. Shares through the same editor; every route in the shared map loses its first and last 200 m.
- **Latihan** also has "Prediksi lomba" (Garmin's 5K to marathon predictions with the change vs 30 days ago and a half-marathon line, faster drawn higher) and "Otot 7 hari terakhir" (summed body map plus big muscle groups with no primary sets).
- **Tren** ends with "Teknik lari per minggu": duration-weighted weekly cadence, ground contact and vertical ratio with the latest week's Garmin band.
- **Training plan**: use the stored short plan name as the heading; keep the complete stored goal in a separate body paragraph. No stored title or objective is rewritten. Race countdown follows the heading. The lead progress is completed sessions out of all planned non-rest sessions, with a done/planned bar. The server's overall percentage is separately labeled as average target achieved across scored sessions, with their count. For example, 8 of 21 sessions done can legitimately coexist with 100% average across 8 scored sessions. Weekly progress likewise distinguishes 2 of 5 sessions done from 100% average target on scored sessions. Server scoring is unchanged: distance or duration against target, capped at 100%; 95% counts as done; rest, future sessions and unstarted sessions today have no score; missed past sessions count as zero.
- **Plan agenda**: opens on this week; horizontal week chips retain date and done/planned bar. Current week has an ink outline, picked week an ink fill. One week shows at a time, with tag/date range, goal and counts. Group sessions by date: weekday and date form the left column, today's date gets an ink pill, sessions share the right column with target/result, status mark, percentage and chevron. Rest notes still open. Outside-plan activities remain below. Session detail retains target-vs-done, Open activity, Result/Next time, notes, numbered steps, optional folded questions and target emphasis. No-plan and finished-plan screens remain; the latter uses the same honestly labeled progress header, race row and week-by-week look-back.
- **Training**: the plan week card leads, followed by a compact neutral Garmin training-status card with a 24sp status-colored word and description, then Recap. Predictions and personal records are divided sections on the background. Muscle analysis, VO2max/category scale and acute/chronic load retain bounded visual cards. PR computation and all activity/share links are unchanged.

## Known limits

- VO2 category scale only has the Cooper Institute rows for age 20 to 29; outside that band the scale hides.
- Widgets use the Kinetik colors but the system condensed font, since Glance cannot load bundled fonts. Labels are condensed caps like `Label`, without the tracking (Glance has no letter spacing). Each sits on a surface card with the 1dp `line` edge (`bg_widget`), like the app's cards.
- Widgets are responsive (`SizeMode.Responsive`): each adds detail as it grows, and every header shows freshness ("Sync 17:20", or "Data from <day>" when stale). They collect the cached /today (and /plan) as flows, so a widget whose Glance session is still open redraws on new data instead of keeping what it read when the session started.
  - Readiness 2×2: score, verdict word in its status color under it, tagline; taller adds HRV on the baseline band and today's plan session.
  - Today 4×2: verdict and score, HRV / RHR / Sleep / Battery, steps bar; tallest adds the first flag or the advice.
  - This week 4×2: sessions, distance (load when there is none), time, plan sessions done, and daily load bars Monday to Sunday, same as the Today card.
  - 7 day trend 4×2: HRV line in its status color over the normal band, RHR over a dashed 30 day average.
  - Last activity 4×1: type icon in a track circle, date and name, then distance, time and pace (HR only without a distance); two rows show a hero (distance, or time when there is none) and a stat row.
  - Today's plan 4×2: today's session with its status mark, target and % (bar in the status color), the recovery adjustment in fair; tall adds this week's strip (weekday over status mark, today on the base fill) like the plan card; bottom line "This week 2/5 done". The whole card opens the plan; the Sync control (last sync time plus refresh icon) pulls Garmin right away through `RefreshWorker.now` and reads "Syncing" until it ends. 4×1 keeps the session row and the refresh icon. The plan line in Readiness and Today also opens the plan.
- Widget picker previews are screenshots of the real widgets (`res/drawable-nodpi/preview_*.png`), so they show the owner's data from the day they were taken and are English only. Retake them when a widget layout changes: on a debug build, `adb shell appwidget grantbind --package dev.garminfit`, start `.WidgetPreviewActivity`, and pull `/sdcard/Android/data/dev.garminfit/files/previews` (instructions in the activity). Take them on a day with activities this week, or This week shows zeros.
