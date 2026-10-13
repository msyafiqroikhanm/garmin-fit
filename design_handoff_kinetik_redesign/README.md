# Handoff: garmin-fit "Kinetik" Redesign (Android / Jetpack Compose)

Target repo: `msyafiqroikhanm/garmin-fit` (branch `main`). Android app di `android/app/src/main/java/dev/garminfit/`, backend FastAPI di `backend/`.

## Overview
Redesign 4 tab aplikasi (Hari Ini, Tren, Aktivitas, Latihan) agar lebih informatif dan sporty untuk anak muda. Pertanyaan utama yang harus dijawab layar Hari Ini: **"Hari ini boleh latihan apa?"** Baru setelah itu angka pendukung, masing-masing dibandingkan dengan baseline pribadi.

## About the Design Files
`Garmin Fit Redesign.dc.html` adalah **referensi desain dalam HTML** (prototype yang menunjukkan tampilan dan perilaku), **bukan kode produksi**. Tugasnya: **buat ulang desain ini di Jetpack Compose** memakai pola yang sudah ada di repo (`Panel`, `Label`, `BigValue`, `StatusText`, `BaselineBar`, `LineChart`, `rememberLoad`/`LoadView`, `Repo`, `Format.kt`). Buka file HTML di browser untuk melihat desain; data di dalamnya adalah contoh.

## Fidelity
**High-fidelity.** Warna, tipografi, spacing, radius, dan copy sudah final. Recreate sedekat mungkin. Semua copy dalam Bahasa Indonesia, pakai persis seperti tertulis di sini.

---

## Design Tokens

### Warna (ganti `Theme.kt`)
App **hanya dark theme** (hapus light scheme / `isSystemInDarkTheme` branch, atau paksa dark).

| Token | Hex | Pemakaian |
|---|---|---|
| `base` | `#0A0F0B` | background layar |
| `navBg` | `#0F130F` | background bottom nav |
| `surface` | `#141A15` | card / panel |
| `track` | `#222823` | track bar, chip netral, bar kosong |
| `border` | `#2A2F2B` | divider, garis dasar chart, border nav atas (`#222823`) |
| `ink` | `#F4F6F2` | teks utama |
| `muted` | `#A0A6A1` | label, caption, nav tidak aktif |
| `body2` | `#B9C0BA` | nama faktor, teks sekunder |
| `body3` | `#C9D0CA` | teks insight di bawah chart |
| `good` / accent volt | `#B1EF4A` | status baik, aksen utama, nav aktif, CTA |
| `fair` | `#FFB330` | status waspada |
| `low` | `#FF645F` | status rendah / bahaya |
| `onAccent` | `#0A0F0B` | teks di atas good/fair/low solid |

Alpha yang dipakai (Compose: `Good.copy(alpha = x)`):
- Band baseline di bar faktor: `good @ 0.30`
- Band rentang normal di chart Tren: `good @ 0.12`
- Background chip status / flag: `status @ 0.12–0.14` (low `@ 0.14–0.16`)
- Bar hari lain di grafik mingguan & split lambat: `good @ 0.45–0.50`
- Segmen VO2 kategori di bawah posisi sekarang: `good @ 0.30`
- Zona gauge beban: warna zona `@ 0.60`

Zona HR (Z1–Z5): `#67ADDD`, `#37D8C9`, `#B1EF4A`, `#FFB330`, `#FF645F`.
Tahapan tidur: Deep `#4671B7`, REM `#67ADDD`, Ringan `#93C7D9`, Bangun `#FF645F`.

Aturan: warna status **hanya untuk makna** (baik/waspada/rendah). Accent volt sekaligus = "good"; itu disengaja.

### Tipografi
Tambahkan Google Fonts ke `res/font/` (atau downloadable fonts):
- **Barlow Condensed** 500/600/700/800 (+ 800 Italic) — angka, headline, label caps
- **Barlow** 400/500/600/700 — body
- **JetBrains Mono** 500 — angka kecil teknis (label sumbu chart, status bar)

Semua angka pakai `fontFeatureSettings = "tnum"`.

| Style | Font | Size/Weight | Catatan |
|---|---|---|---|
| `displayHero` | Barlow Condensed | 80sp / 800 Italic, lineHeight 0.85× | jarak aktivitas "10,24" |
| `display` | Barlow Condensed | 52–56sp / 800, lh 0.9× | skor readiness, VO2max |
| `statusWord` | Barlow Condensed | 40–48sp / 800 Italic, UPPERCASE, lh 0.9× | "GAS!", "PRODUKTIF" |
| `screenTitle` | Barlow Condensed | 36sp / 800 UPPERCASE | "TREN TUBUH", "KEBUGARAN" |
| `metricL` | Barlow Condensed | 36sp / 800 | nilai tile, nilai chart |
| `metricM` | Barlow Condensed | 28–30sp / 800 | ringkasan minggu, beban |
| `metricS` | Barlow Condensed | 20–24sp / 700 | nilai faktor, stat aktivitas |
| `cardTitle` | Barlow Condensed | 20sp / 700 UPPERCASE | judul card |
| `greeting` | Barlow Condensed | 28sp / 700 | "Pagi, Syafiq" |
| `label` | Barlow Condensed | 13sp / 600, UPPERCASE, letterSpacing 0.12em, warna muted | label metrik |
| `body` | Barlow | 15sp / 400–600, lh 1.45 | rekomendasi, deskripsi |
| `bodyS` | Barlow | 13–14sp / 400–600, lh 1.4 | insight, faktor |
| `caption` | Barlow | 11–12sp / 400–600, muted | unit, catatan |
| `mono` | JetBrains Mono | 10–11sp / 500 | label sumbu, angka load kecil |

### Spacing, radius, shadow
- Padding layar horizontal 16dp; atas 16dp; bawah 24dp. Gap antar card **12dp**.
- Padding card: hero 20dp, card biasa 18dp, tile 14dp.
- Gap dalam card: 10–16dp (lihat per komponen).
- Radius: card besar **24dp**, tile **20dp**, inner box **16dp**, bar/segmen **3–6dp**, chip/pill **999 (CircleShape)**.
- Tanpa shadow/elevation di dalam app (shadow di HTML hanya untuk mockup frame).

---

## Komponen baru / diubah (`ui/Components.kt`)

1. **`Panel`** → background `surface`, radius 24dp, padding 18dp (param `padding` & `radius` untuk varian 20/14).
2. **`Label`** → style `label` di atas.
3. **`StatusChip(text, status)`** → pill, bg `status @0.14`, teks `status` 13sp/600, dot 7dp di kiri, padding 6×10dp, gap 6dp.
4. **`NeutralChip(text)`** → pill bg `track`, 13sp/600 ink, padding 5×10dp.
5. **`ReadinessRing(score, color)`** → 132dp, stroke 12dp, track `track`, progress `color`, `StrokeCap.Round`, mulai dari jam 12 searah jarum jam. Tengah: skor `display` 52sp + teks "READINESS" 12sp/600 letterSpacing 0.1em muted.
6. **`FactorBar(value, min, max, bandLow, bandHigh, status)`** → pengganti/versi baru `BaselineBar`. Tinggi 14dp; track 6dp radius 3 `track`; band `good@0.30`; dot 14dp warna status dengan ring 3dp warna `surface`. Posisi = `(v - min)/(max - min)`, clamp 0..1.
7. **`SegmentBar(value, of = 5, color)`** → 5 segmen `weight(1f)`, tinggi 8dp, radius 3dp, gap 3dp; segmen `k <= round(value)` berwarna, sisanya `track`.
8. **`StackedBar(parts: List<Pair<Float, Color>>)`** → untuk tahapan tidur (tinggi 14dp, gap 3dp, radius 4dp per segmen) dan zona HR (tinggi 18dp, gap 2dp, clip radius 6dp keseluruhan).
9. **`LineChart`** → tinggi 100dp; garis 2.2dp round; band opsional `good@0.12` full width; baseline `border` 1dp di bawah; **tanpa** label sumbu kiri (hapus kolom min/max). Di bawah chart: tanggal awal (mono 11sp) kiri, "Hari ini" kanan. Support `secondDashed` untuk garis kronis (1.6dp, dash 4/4, muted).
10. **Bottom nav** (ganti `NavigationBar` Material): bg `navBg`, border atas 1dp `track`, padding 10dp h 12dp / bawah 18dp, `SpaceAround`. Item teks saja (tanpa ikon) 13sp: aktif = pill `good` bg, teks `onAccent` 700, padding 8×14dp; tidak aktif = teks muted 600. Label: "Hari Ini", "Tren", "Aktivitas", "Latihan".

---

## Screens

### 1. Hari Ini (`ui/TodayScreen.kt`)
Kolom scroll, urutan dari atas:

**a. Header** (padding h 4dp; Row SpaceBetween, align bottom)
- Kiri: `Label` tanggal UPPERCASE ("SENIN, 28 SEP", format `EEEE, d MMM` locale id) + sapaan `greeting` "Pagi, {nama}" (Pagi <11:00, Siang <15:00, Sore <18:00, Malam; nama dari `/me`).
- Kanan: pill bg `surface`, 12sp muted: "Sync {HH:mm} ↻" (waktu `latest.synced_at`). Tetap pertahankan pull-to-refresh.
- Kalau `latest.date != today`, tampilkan caption lama di bawah header.

**b. Hero readiness** (`Panel` padding 20, gap 16)
- Row gap 16dp: `ReadinessRing` + Column gap 6dp: kata status `statusWord` 40sp berwarna status; tagline 15sp/600; "Level Garmin: {level}" 13sp muted.
- Inner box bg `base`, radius 16, padding 14, gap 10: label "SARAN LATIHAN HARI INI" (warna status), teks rekomendasi `body`, row `NeutralChip` (wrap, gap 6).

Mapping status (dari `readiness_level`, fallback skor):

| Level | Skor | Warna | Kata | Tagline | Rekomendasi | Chips |
|---|---|---|---|---|---|---|
| PRIME, HIGH | ≥70 | good | "Gas!" | "Siap latihan berat" | "Tubuh lagi fit. Pas buat sesi kualitas: tempo 40 menit atau interval 6×800 m." | "40–60 mnt", "Zona 3–4", "Pulih {x} jam" |
| MODERATE | 40–69 | fair | "Santai" | "Latihan ringan–sedang" | "Tubuh belum pulih penuh. Easy run 30–45 menit di zona 2, simpan interval buat besok." | "30–45 mnt", "Zona 2", "Pulih {x} jam" |
| LOW, POOR | <40 | low | "Pulih" | "Prioritaskan istirahat" | "Tubuh butuh recovery. Jalan santai 20 menit atau stretching, dan tidur lebih awal malam ini." | "≤ 20 mnt", "Zona 1", "Pulih {x} jam" |

"Pulih {x} jam" dari `recovery_time` (lihat Backend). Sembunyikan chip itu kalau null.

**c. "KENAPA SKORNYA SEGINI?"** (`Panel`, gap 16) — 5 baris faktor. Tiap baris (gap 6):
- Row: nama (14sp `body2`) kiri; kanan nilai `metricS` 22sp + unit 12sp muted.
- `FactorBar`.
- Row: teks status 12sp/600 warna status (kiri), catatan 12sp muted (kanan).

| Faktor | Nilai | min–max skala | Band | Status rule | Catatan kanan |
|---|---|---|---|---|---|
| HRV semalam (ms) | `hrv_last_night` | 35–75 (atau low−13 .. high+13) | `hrv_balanced_low..high` | dalam band → good "Seimbang"; di bawah band ≤3 → fair "Batas bawah normal"; lebih rendah → low "Di bawah normal"; di atas → good "Di atas normal" | "normal {lo}–{hi}" |
| Tidur (skor) | `sleep_score` | 30–100 | 70–100 | ≥80 good "Nyenyak", 70–79 good "Cukup", 50–69 fair "Kurang", <50 low "Buruk" | durasi `sleep(sleep_s)` |
| Resting HR (bpm) | `rhr` | mean−10 .. mean+12 | mean−4 .. mean+4 | < mean−1 good "Lebih rendah dari biasa"; ≤ mean+4 good "Normal"; ≤ mean+5 fair "Sedikit tinggi"; > mean+5 low "+{d} dari rata-rata" | "rata-rata {mean}" (`baseline.rhr.mean`) |
| Beban akut : kronis (rasio) | `baseline.load.ratio` | 0.5–1.8 | 0.8–1.3 | 0.8–1.3 good "Seimbang"; 1.2–1.3 boleh fair "Mendekati batas"; >1.3 low "Melonjak"; <0.8 fair "Di bawah biasanya" | "optimal 0,8–1,3" |
| Stres (/100) | `stress_avg` (hari kemarin kalau hari ini masih parsial) | 0–100 | 0–35 | ≤25 good "Rendah"; 26–50 fair "Sedang"; >50 low "Tinggi" | "kemarin" |

Desimal pakai koma (locale id): "1,05".

**d. Flags** — tiap `today.flags` jadi box radius 16, padding 12×14, bg `status@0.12`, dot 8dp + teks 14sp lh 1.4. Mapping `level`: "good" → good, "warn" → fair; `rhr_high` dan `load_spike` → low. Copy flag dari backend boleh diperpanjang sesuai desain, mis. "HRV 7 hari terakhir di atas rentang normal. Adaptasi latihanmu jalan."

**e. Grid tile 2 kolom** (gap 10dp)
- **Body Battery** (tile radius 20, padding 14, gap 8): label, nilai `bb_now` 36sp + "sekarang"; bar 8dp track dengan isi dari `bb_low%` selebar `(bb_high−bb_low)%` warna status readiness; caption "Terendah {low} · tertinggi {high}".
- **Langkah**: `steps` 36sp (format `4.812`) + "/ {goal}"; bar 8dp isi ink `steps/goal`; caption "{pct}% target harian". Goal dari `daily_step_goal` (backend), default 8.000.
- **Tidur semalam** (span 2 kolom, gap 10): Row label kiri + kanan durasi 28sp + "skor {n}" 12sp; `StackedBar` deep/rem/light/awake; grid 4 kolom legend (kotak 7dp radius 2 + nama 11sp muted, durasi 14sp/600 di bawah).

**f. "7 HARI TERAKHIR"** (`Panel`, gap 14)
- Row judul + kanan "{n} hari aktif" 13sp/600 good.
- Grid 3 kolom: sesi / km (koma, 1 desimal) / durasi — nilai 30sp/800, unit 12sp muted.
- Grafik batang 7 hari (tinggi area 92dp, gap 8dp): tiap kolom weight 1, dari atas: angka load (mono 10sp muted, kosong kalau 0), bar tinggi `max(8, load/maxLoad*64)dp` radius 6 (hari tanpa aktivitas: 4dp `track`), label hari 11sp/600. Hari ini: bar `good`, label ink; hari lain: bar `good@0.45`, label muted.
- Caption: "Tinggi batang = training load per hari · total {sum}".
- Butuh load per hari → lihat Backend `week.daily_load`.

**g. Aktivitas terakhir** (`Panel`, gap 10, klik → detail): label "AKTIVITAS TERAKHIR · {hari}"; Row nama 24sp/700 + "›" muted; grid 4 kolom: km / pace "/km" / HR "bpm" / TE aerobik — nilai 20sp/700, unit 11sp muted.

**h. Ringkasan minggu** — card **bg `good`, teks `onAccent`**, radius 24, padding 18, gap 8, klik toggle (animateContentSize):
- Label 13sp/700 caps "RINGKASAN MINGGU · {from}–{to}".
- Headline 24sp/800 UPPERCASE lh 1.05 (`summary.headline`).
- Saat terbuka: tiap section judul 12sp/700 caps letterSpacing 0.08em + body 14sp lh 1.45.
- CTA 13sp/600: "Ketuk untuk baca lengkap ↓" / "Tutup ↑".

Tombol "Ganti token" tetap di paling bawah (TextButton, muted).

### 2. Tren (`TrendsScreen` di `ChartScreens.kt`)
- Judul "TREN TUBUH" `screenTitle`.
- Segmented pill: container bg `surface` radius 999 padding 4 gap 4; item weight 1, padding v 9dp, 14sp/700; aktif bg `good` teks `onAccent`, lainnya transparan muted. Opsi "7 hari", "30 hari", "90 hari" (default 30).
- 4 card chart (`Panel`, gap 10):
  - Header Row: kiri label + nilai terakhir 36sp/800 + unit 13sp muted; kanan pill delta + "rata-rata {avg}" 12sp muted.
  - Delta = nilai terakhir − nilai pertama di rentang. Teks "▲ +{d}" / "▼ {d}" / "= 0". Warna: good kalau arahnya bagus, fair kalau buruk, muted kalau 0. Arah bagus: HRV naik, RHR turun, Skor tidur naik, Body Battery naik.
  - `LineChart` + label tanggal.
  - Insight 13sp `body3`.

| Chart | Field | Unit | Warna garis | Band | Insight |
|---|---|---|---|---|---|
| HRV semalam | `hrv_last_night` | ms | good | hrv_balanced_low..high | "Pita hijau = rentang normal kamu. Tetap di dalam pita artinya pemulihan stabil." |
| Resting HR | `rhr` | bpm | ink | mean±4 | "Makin rendah biasanya makin bugar. Turun pelan-pelan = jantung makin efisien." |
| Skor tidur | `sleep_score` | /100 | `#67ADDD` | 70–100 | "Skor di atas 70 bikin readiness naik keesokan harinya." |
| Body Battery tertinggi | `bb_high` | – | fair | – | "Puncak energi harian setelah tidur. Di bawah 60 berarti tidur belum cukup mengisi ulang." |

(Chart Durasi tidur & Stres dari versi lama dihapus; stres sudah ada di faktor Hari Ini.)

### 3. Aktivitas — detail (`ActivitiesScreen.kt` → `ActivityDetail`)
List tetap (restyle: judul Barlow Condensed 20sp/700, divider `track`). Detail:
- "‹ Semua aktivitas" 14sp/600 good (back).
- Label "{tipe} · {hari} {tgl}, {jam}" + nama 32sp/800 UPPERCASE.
- **Stat card** (gap 16): jarak `displayHero` 80sp + "km" 18sp muted (hanya jika ada jarak); grid 3 kolom gap 14×8: durasi, pace /km, HR rata-rata, HR maks, kkal, elevasi naik — nilai 24sp/700, label 12sp muted. Sembunyikan yang null.
- **"EFEK KE TUBUH"** (gap 14): 2 baris (Aerobik warna good, Anaerobik warna `#67ADDD`): Row nama 14sp + nilai 24sp/800 "/ 5"; `SegmentBar`; label 13sp/600. Label TE (Garmin): 0–0.9 "Tanpa efek", 1–1.9 "Efek minim", 2–2.9 "Menjaga", 3–3.9 "Meningkatkan", 4–4.9 "Sangat meningkatkan", 5 "Berlebihan"; tambahkan objek: aerobik "… daya tahan", anaerobik "… ke kecepatan". Label pakai warna aksennya bila ≥2, muted bila <2.
  - Footer (border atas `track`, padding top 12, 13sp `body2`): "Training load **{load}**" + konteks singkat.
- **"ZONA DETAK JANTUNG"** (gap 12): `StackedBar` 5 zona; lalu 5 baris grid `[28dp | 1fr | 64dp | 40dp]` gap 8: badge "Z{n}" (bg warna zona, teks onAccent 15sp/800, radius 6), nama + rentang bpm (11sp muted), durasi "12m 00d" (13sp muted, kanan), persen 18sp/700 kanan. Nama zona: Pemanasan, Mudah, Aerobik, Ambang, Maksimal. Rentang bpm dari zona HR user (Garmin `get_heart_rate_zones` atau hitung dari max HR); kalau tidak ada, sembunyikan baris bpm.
- **"SPLIT PER KM"** (gap 8): kanan atas "negative split ✓" 12sp muted (hanya kalau paruh kedua lebih cepat). Baris grid `[22dp | 1fr | 40dp]`: km (mono 11sp), bar 10dp radius 5 dalam track — lebar relatif (tercepat = paling panjang, range 40–100%), split lebih cepat dari rata-rata `good`, lainnya `good@0.5`; pace 16sp/700 kanan. Butuh data split (lihat Backend).

### 4. Latihan (`TrainingScreen` di `ChartScreens.kt`)
- Judul "KEBUGARAN".
- **Training status** card bg `good` teks onAccent, padding 20, gap 6: label "TRAINING STATUS", kata status 48sp/800 Italic UPPERCASE, deskripsi 15sp/500 (pakai `trainingStatus()` di Format.kt). Warna card: PRODUCTIVE/PEAKING/MAINTAINING → good; RECOVERY/DETRAINING/UNPRODUCTIVE → fair; OVERREACHING/STRAINED → low.
- **VO2max** (`Panel`, gap 12): label + nilai 56sp/800 + "ml/kg/mnt"; kanan pill good "+{delta} / 90 hari" (fair bila negatif). Skala 5 kategori (weight 1, gap 3, bar 10dp radius 3, nama 10sp/600 center di bawah): Kurang, Cukup, Baik, Sangat baik, Unggul. Kategori aktif `good` (nama good), di bawahnya `good@0.3`, di atasnya `track` (nama muted). Caption: "\"{kategori}\" untuk {pria/wanita} usia {rentang}. Kurang {x} poin lagi ke kategori {berikutnya}."
  - Ambang (Cooper Institute, pria 20–29): <38, 38–43.9, 44–50.9, 51–55.9, ≥56. Wanita 20–29: <32, 32–36.9, 37–41.9, 42–46.9, ≥47. Simpan umur & jenis kelamin di konfigurasi (atau dari Garmin `get_user_profile`).
- **"BEBAN LATIHAN"** (`Panel`, gap 12): grid 3 kolom: akut "(7 hari)", kronis "(28 hari)", rasio (warna status) — 30sp/800. Gauge: 4 segmen (flex 30/50/20/30 → skala 0.5–1.8) warna `#67ADDD`, good, fair, low @0.6, tinggi 12 radius 3 gap 3; marker 4×20dp ink radius 2 di posisi rasio. Legend 11sp muted: "Kurang", "Optimal 0,8–1,3", "Tinggi", "Rawan cedera". Lalu `LineChart` 110dp akut (good 2.4dp) + kronis (muted dashed 1.6dp); legend "Akut"/"Kronis"/"90 hari". Caption rekomendasi: rasio <0.8 "Beban turun. Boleh naikkan volume bertahap.", 0.8–1.3 "Aman menambah volume sampai ±10% minggu depan.", 1.3–1.5 "Tahan dulu volume minggu ini.", >1.5 "Kurangi intensitas, risiko cedera naik."
- Chart Training Readiness lama boleh dihapus (sudah di Hari Ini).

---

## Interactions & Behavior
- Pull-to-refresh di Hari Ini tetap (owner → `POST /sync`, lalu reload). Pill "Sync" di header juga memicu refresh.
- Ringkasan minggu: tap toggle expand/collapse, `animateContentSize()` (default spring).
- Tren: ganti rentang → reload `/daily?from=`.
- Aktivitas terakhir di Hari Ini → buka tab Aktivitas + detail (perilaku `onOpenActivity` sekarang).
- Loading/error/empty tetap memakai `LoadView`; restyle: spinner warna good, tombol "Coba lagi" bg good teks onAccent radius 999.
- Widget (`widget/Widgets.kt`): samakan palet (base/surface/good/fair/low) dan font; tidak wajib di scope ini.

## State Management
- Tidak ada state baru di luar: `range` (Tren), `summaryOpen` (Hari Ini), `selected` (Aktivitas).
- Semua turunan (status faktor, kata status, delta, kategori VO2) dihitung di client — taruh helper di `Format.kt` (mis. `readinessVerdict(level, score)`, `factorStatus(...)`, `teLabel(v)`, `vo2Category(v, sex, age)`).

## Backend changes (`backend/core.py`, `backend/api/index.py`)
1. `extract_daily`: tambahkan
   - `recovery_time_min`: dari `readiness.recoveryTime` (menit) → chip "Pulih {jam} jam".
   - `step_goal`: `stats.dailyStepGoal`.
   - `synced_at` sudah ada; ekspos di `/today.latest`.
2. `/today.week`: tambahkan `daily_load`: list 7 item `{date, load}` (sum `training_load` per tanggal, 0 bila tidak ada), urut lama → hari ini.
3. `/today`: sertakan `name` user (dari token) untuk sapaan.
4. Split per km: saat `sync_activities`, panggil `g.get_activity_splits(activity_id)` untuk lari; simpan `splits: [{distance_m, duration_s, avg_hr}]` di dokumen activity (backfill opsional). Detail aktivitas menyembunyikan card split kalau kosong.
5. (Opsional) Zona HR: `g.get_heart_rate_zones()` simpan sekali di koleksi `profile`; ekspos di `/me`.
6. Setelah field ditambah, update data class di `Api.kt` (nullable semua).

## Assets
Tidak ada gambar/ikon. Ikon bottom nav lama (`ic_*.xml`) tidak dipakai lagi di tab bar (boleh tetap untuk widget/launcher). Font: Google Fonts (OFL) Barlow, Barlow Condensed, JetBrains Mono.

## Files
- `Garmin Fit Redesign.dc.html` — referensi desain (buka di browser). Panel Tweaks "scenario" = prime / moderate / low untuk melihat 3 kondisi Hari Ini. Data di dalamnya contoh.
- Kode yang diubah: `ui/Theme.kt`, `ui/Components.kt`, `ui/TodayScreen.kt`, `ui/ChartScreens.kt`, `ui/ActivitiesScreen.kt`, `MainActivity.kt` (bottom nav), `Format.kt`, `Api.kt`, `backend/core.py`, `backend/api/index.py`.

## Urutan implementasi yang disarankan
1. Theme + fonts + komponen dasar (Panel, Label, chips, FactorBar, SegmentBar, StackedBar, bottom nav).
2. Backend fields + `Api.kt`.
3. Hari Ini → Latihan → Tren → Aktivitas.
4. Cek di device: kontras teks, angka tnum, dark only.
