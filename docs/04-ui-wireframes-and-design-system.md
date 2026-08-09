# Phase 4 — UI Wireframes & Design System

**Project:** UrbanCanopy
**Phase:** 4 of 14

---

## 1. Design Principles

1. **The number is the hero.** Every screen exists to make an estimate legible. Chrome recedes; figures, units and their uncertainty come forward.
2. **Never a bare number.** Every figure ships with its unit and, where computed, a route to "how was this calculated?".
3. **Honest, not persuasive.** The palette is calm and scientific — not an eco-marketing green. The disclaimer is present, not buried.
4. **Feedback is immediate.** Disable → await → patch DOM → toast. No spinners over the whole page; skeletons in the region that is loading.
5. **Accessible by construction.** Labels, focus rings, contrast and keyboard order are decided here, not retro-fitted.

---

## 2. Design Tokens

These map one-to-one onto `static/css/tokens.css`.

### 2.1 Colour — light (default)

| Token | Value | Use |
|---|---|---|
| `--uc-canvas` | `#F3F6F3` | page background |
| `--uc-surface` | `#FFFFFF` | cards, panels |
| `--uc-surface-alt` | `#F8FBF8` | table stripes, insets |
| `--uc-border` | `#DCE4DD` | hairlines, dividers |
| `--uc-border-strong` | `#B9C6BC` | input borders |
| `--uc-ink` | `#132119` | primary text |
| `--uc-ink-secondary` | `#4F6157` | secondary text |
| `--uc-ink-muted` | `#788A7F` | captions, hints |
| `--uc-primary` | `#1B6B47` | brand, primary actions *(4.9:1 on white)* |
| `--uc-primary-hover` | `#14512F` | hover/active |
| `--uc-primary-subtle` | `#E4F1E9` | selected rows, badges |
| `--uc-accent` | `#2A7DA8` | oxygen/water metrics *(4.6:1)* |
| `--uc-accent-subtle` | `#E3F0F7` | |
| `--uc-success` | `#2E7D32` | positive verdicts |
| `--uc-warning` | `#A65F00` | `TIGHT`, monoculture flag *(4.7:1)* |
| `--uc-danger` | `#B3261E` | `OVERCROWDED`, destructive |
| `--uc-info` | `#1565A0` | informational |

### 2.2 Colour — dark

Applied via `:root[data-theme="dark"]` **and** `@media (prefers-color-scheme: dark)`.
Every token above is redefined; no colour has its only definition inside a media block.

| Token | Dark value |
|---|---|
| `--uc-canvas` | `#0F1613` |
| `--uc-surface` | `#17211C` |
| `--uc-surface-alt` | `#1D2822` |
| `--uc-border` | `#2C3A32` |
| `--uc-ink` | `#E8EFE9` |
| `--uc-ink-secondary` | `#A8B8AD` |
| `--uc-primary` | `#4FB07E` |
| `--uc-accent` | `#5FAED6` |
| `--uc-warning` | `#E0A64A` |
| `--uc-danger` | `#F2837B` |

### 2.3 Metric colours

Each environmental metric keeps **one** colour everywhere — KPI card, chart, table accent, PDF. Consistency is what lets a reader scan a report without a legend.

| Metric | Token | Light | Dark |
|---|---|---|---|
| CO₂ sequestration | `--uc-m-carbon` | `#1B6B47` | `#4FB07E` |
| O₂ generation | `--uc-m-oxygen` | `#2A7DA8` | `#5FAED6` |
| PM2.5 | `--uc-m-pm25` | `#B4691F` | `#E0A64A` |
| PM10 | `--uc-m-pm10` | `#8A5A3B` | `#C79372` |
| NO₂ | `--uc-m-no2` | `#6B5B95` | `#A794D1` |
| SO₂ | `--uc-m-so2` | `#A4453C` | `#E08A80` |
| CO | `--uc-m-co` | `#4A6F7C` | `#89B4C0` |
| Canopy | `--uc-m-canopy` | `#5C8A3A` | `#93C46B` |

**Ratification (Phase 10) — these are accent colours, not a chart palette.**

Run against the data-visualisation validator, the five pollutant colours **fail** as a
categorical set, in both light and dark:

| Check | Result |
|---|---|
| Chroma floor | **FAIL** — `--uc-m-pm10`, `--uc-m-no2`, `--uc-m-co` fall below the floor: they read as grey |
| Normal-vision separation | **FAIL** — PM2.5 ↔ PM10 ΔE 9.4 (light) / 8.5 (dark) against a floor of 15 |
| CVD separation | PASS (8.1 light / 8.6 dark) — but only just, and irrelevant while the pair fails for full colour vision |

Re-ordering does not rescue them. Green and blue are reserved for carbon, canopy and oxygen,
which leaves five warm hues for the five pollutants, and **every ordering of those five fails
the normal-vision floor** (best case ΔE 12.9).

The resolution is not a new palette but the correct form. Every screen where the five would
appear together — the pollutant breakdown on the results page, the dashboard's pollutant
chart — asks the reader to *compare magnitudes across categories that are already named on the
axis*. That is a one-hue job; the colour was re-encoding what the label already said. Those
charts now use a single hue and let length carry the comparison.

The metric colours stay in `tokens.css`, where each is used as a **lone accent** — a KPI card's
rule, a figure's colour — never as a set to be told apart. Colour carries identity only on the
comparison screen, where the plans genuinely are the subject; that palette (categorical slots
1–4) passes every gate in both modes.

### 2.4 Spacing, radius, elevation, type

| Group | Scale |
|---|---|
| Space | `--uc-s1 4px · s2 8px · s3 12px · s4 16px · s5 24px · s6 32px · s7 48px · s8 64px` |
| Radius | `--uc-r-sm 6px · r-md 10px · r-lg 16px · r-pill 999px` |
| Shadow | `--uc-sh-1` card rest · `--uc-sh-2` raised · `--uc-sh-3` overlay |
| Font | `Inter, "Segoe UI", system-ui, sans-serif`; numerals `font-variant-numeric: tabular-nums` |
| Type scale | `display 2.25rem/700 · h1 1.75rem/700 · h2 1.375rem/650 · h3 1.125rem/600 · body 0.9375rem/400 · small 0.8125rem · metric 2rem/700 tabular` |
| Layout | max content width `1280px`; sidebar `256px`; grid gutter `24px` |

---

## 3. Navigation Map

```
PUBLIC                     AUTHENTICATED (Planner)              ADMIN (adds)
────────                   ──────────────────────               ────────────
index.html                 dashboard.html         ⌂             admin/species.html
species.html               plans.html             ▤             admin/users.html
species.html#detail        plan-editor.html       ✎             admin/coefficients.html
login.html   (Ph.12)       plan-results.html      ▦             admin/report-logs.html
register.html(Ph.12)       compare.html           ⇄             admin/audit.html
                           profile.html           ◍
```

```
plans.html ──[New plan]──▶ plan-editor.html ──[Save & estimate]──▶ plan-results.html
     │                            ▲                                      │
     └──[Compare 2–4]──▶ compare.html                            [Download PDF]
```

---

## 4. Application Shell

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ☰  🌳 UrbanCanopy                          🔎 Search…    ☾  ⚙  ◍ Aswin ▾     │  56px topbar
├────────────┬─────────────────────────────────────────────────────────────────┤
│            │                                                                 │
│ ⌂ Dashboard│   Page title                                    [ Primary CTA ] │
│ ▤ My Plans │   Breadcrumb › path › here                                      │
│ 🌿 Species │  ─────────────────────────────────────────────────────────────  │
│ ⇄ Compare  │                                                                 │
│            │   « page content »                                              │
│ ── ADMIN ──│                                                                 │
│ 🌱 Species │                                                                 │
│ 👥 Users   │                                                                 │
│ ⚖ Coeffs   │                                                                 │
│ 📄 Logs    │                                                                 │
│            │                                                                 │
│ ◍ Profile  │                                                                 │
│ ⎋ Log out  │                                                                 │
└────────────┴─────────────────────────────────────────────────────────────────┘
  256px                                                    toasts ↘ bottom-right
```

- Sidebar collapses to an off-canvas drawer below `992px`; the topbar keeps the hamburger.
- The ADMIN group renders only when `isAdmin` — hidden, and additionally refused server-side.
- Active item: 3px left bar in `--uc-primary` + `--uc-primary-subtle` background.

---

## 5. Screen Wireframes

### 5.1 Dashboard (`dashboard.html`)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Dashboard                                                  [ + New plan ]    │
│ Estimated impact across your 6 plantation plans                              │
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌──────────────┐┌──────────────┐┌──────────────┐┌──────────────┐            │
│ │ ▤ PLANS      ││ 🌳 TREES     ││ ◉ CO₂ / YEAR ││ ◎ O₂ / YEAR  │            │
│ │      6       ││   12,450     ││   271.2      ││  198,300     │            │
│ │ 4 completed  ││ across plans ││ tonnes/yr    ││ kg/yr        │            │
│ └──────────────┘└──────────────┘└──────────────┘└──────────────┘            │
│ ┌──────────────┐┌──────────────┐┌──────────────┐┌──────────────┐            │
│ │ ☁ POLLUTANTS ││ ⬡ CANOPY     ││ ⌾ COVERAGE   ││ ★ AVG SCORE  │            │
│ │   3,182 kg/yr││ 84,220 m²    ││    38.4 %    ││    72 / 100  │            │
│ └──────────────┘└──────────────┘└──────────────┘└──────────────┘            │
├──────────────────────────────────┬───────────────────────────────────────────┤
│ CO₂ contribution by species      │ Annual pollutant removal                  │
│        ╭────────╮                │  PM2.5 ███████████████████ 812 kg         │
│      ╭─┤ doughnut│─╮  ▪ Neem 24% │  PM10  ████████████████████████ 1,104 kg  │
│      │ ╰────────╯ │  ▪ Peepal 19%│  NO₂   ██████████ 486 kg                  │
│      ╰───────────╯   ▪ Banyan 15%│  SO₂   ███████ 341 kg                     │
│                      ▪ Other 42% │  CO    ██████████ 439 kg                  │
├──────────────────────────────────┴───────────────────────────────────────────┤
│ Cumulative CO₂ sequestration projection            [ 10 yr ▾ ]               │
│  t                                                          ╭────────        │
│  │                                              ╭───────────╯                │
│  │                          ╭───────────────────╯                            │
│  │      ╭───────────────────╯                                                │
│  └──────┴────┴────┴────┴────┴────┴────┴────┴────┴────▶ year                  │
├──────────────────────────────────────────────────────────────────────────────┤
│ Recent plans                                              [ View all → ]     │
│ ┌──────────────────────────────────────────────────────────────────────────┐ │
│ │ Anna Nagar Avenue Drive   COMPLETED  3,200 trees  71.4 t/yr   2 days ago │ │
│ │ Campus Miyawaki Block     DRAFT        480 trees  10.2 t/yr   5 days ago │ │
│ └──────────────────────────────────────────────────────────────────────────┘ │
├──────────────────────────────────────────────────────────────────────────────┤
│ ⓘ Estimates are derived from published scientific averages. Actual results   │
│   vary with climate, soil, tree age, health and maintenance. Not a certified │
│   carbon-accounting instrument.                            [ Methodology → ] │
└──────────────────────────────────────────────────────────────────────────────┘
```

**Empty state** (no plans): the KPI band is replaced by a single centred panel — an illustration, *"No plans yet. Create your first plantation plan to see its estimated environmental impact."*, and a primary CTA. Never an empty chart canvas (FR-DASH-09).

### 5.2 Plan Editor (`plan-editor.html`) — the core screen

Two columns: composition on the left, a **live estimate that never triggers a page reload** on the right (FR-PLAN-13).

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ‹ Plans   Anna Nagar Avenue Drive              DRAFT   [Save] [Save & Complete]│
├────────────────────────────────────────────┬─────────────────────────────────┤
│ ① PLAN DETAILS                             │  LIVE ESTIMATE          ⟳ 40 ms │
│ ┌────────────────────────────────────────┐ │ ┌─────────────────────────────┐ │
│ │ Title *      [ Anna Nagar Avenue…    ] │ │ │ CO₂ SEQUESTERED / YEAR      │ │
│ │ Site name    [ Anna Nagar 2nd Ave    ] │ │ │       71.42 t/yr            │ │
│ │ Purpose *    [ Air quality improv. ▾ ] │ │ │ ≈ 590,000 car-km offset     │ │
│ │ Land area *  [ 1.20 ] [ hectare    ▾ ] │ │ ├─────────────────────────────┤ │
│ │              = 12,000 m²               │ │ │ O₂ 52,100 kg  ·  ☁ 984 kg  │ │
│ │ Start year   [ 2026 ]                  │ │ ├─────────────────────────────┤ │
│ │ Projection   [ 10 ] years              │ │ │ CANOPY                      │ │
│ │ Description  [                       ] │ │ │ 9,640 m²   ▓▓▓▓▓▓▓▓░░ 80.3% │ │
│ └────────────────────────────────────────┘ │ ├─────────────────────────────┤ │
│                                            │ │ SPACE FEASIBILITY           │ │
│ ② SPECIES COMPOSITION                      │ │ ● OPTIMAL      86% utilised │ │
│ ┌────────────────────────────────────────┐ │ │ 1,680 m² spare · max 3,720  │ │
│ │ 🔎 [ Search species…            ] [+]  │ │ ├─────────────────────────────┤ │
│ │  ┌───────────────────────────────────┐ │ │ │ IMPACT SCORE                │ │
│ │  │🌳 Neem · Azadirachta indica       │ │ │ │  ▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░  72/100  │ │
│ │  │   21.8 kg CO₂/yr · FAST · r 4.5 m │ │ │ │ Diversity H′ 1.42  ✓ mixed  │ │
│ │  └───────────────────────────────────┘ │ │ └─────────────────────────────┘ │
│ ├────────────────────────────────────────┤ │  [ ⓘ How is this calculated? ]  │
│ │ SPECIES        QTY    CO₂/yr   CANOPY  │ │                                 │
│ │ 🌳 Neem      [ 800]  17.4 t   5,089 m² │ │  ⚠ Peepal is 46% of this plan.  │
│ │ 🌳 Peepal    [1200]  31.7 t   3,393 m² │ │    Consider more species mix.   │
│ │ 🌳 Gulmohar  [ 400]   6.1 t   1,158 m² │ │                                 │
│ │              ─────  ──────   ───────   │ │                                 │
│ │ TOTAL         2,400  71.4 t   9,640 m² │ │                                 │
│ └────────────────────────────────────────┘ │                                 │
└────────────────────────────────────────────┴─────────────────────────────────┘
```

**Interaction contract**
- Typing a quantity → 300 ms debounce → `POST /api/v1/estimates/preview` → patch **only** the estimate panel's text nodes and bars. No reload, no full re-render, no scroll jump.
- The `⟳ 40 ms` chip shows the round trip — a quiet signal that the figure is live.
- Adding a species already present increments its row and fires an info toast (UC-05 A1).
- `OVERCROWDED` turns the feasibility chip red with the deficit and the maximum plantable count — **advisory, never blocking** (BR-13).
- Below `992px` the estimate panel moves to the top of the column and becomes a sticky 2-metric summary bar.

### 5.3 Plan Results (`plan-results.html`)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ ‹ Plans   Anna Nagar Avenue Drive   COMPLETED     [⇄ Compare] [⬇ PDF report] │
│ Anna Nagar 2nd Ave · Air quality improvement · 1.20 ha · 2,400 trees · 2026  │
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌────────────┐┌────────────┐┌────────────┐┌────────────┐┌────────────┐       │
│ │ CO₂/yr     ││ O₂/yr      ││ POLLUTANTS ││ CANOPY     ││ SCORE      │       │
│ │ 71.42 t    ││ 52,100 kg  ││ 984 kg/yr  ││ 80.3 %     ││ 72/100     │       │
│ └────────────┘└────────────┘└────────────┘└────────────┘└────────────┘       │
├──────────────────────────────────────────────────────────────────────────────┤
│ Species contribution                                       [ table ⇄ chart ] │
│ SPECIES      QTY  EFFECTIVE  CO₂ t/yr  O₂ kg/yr  PM2.5 g  CANOPY m²  SHARE   │
│ Neem         800      680      17.42    12,240    54,400   5,089     24.4 %  │
│ Peepal      1200     1020      31.72    22,440    71,400   3,393     44.4 %  │
│ Gulmohar     400      340       6.12     4,760    17,000   1,158     11.2 %  │
│ ───────────────────────────────────────────────────────────────────────────  │
│ TOTAL       2400     2040      71.42    52,100   142,800   9,640      100 %  │
├────────────────────────────────┬─────────────────────────────────────────────┤
│ Pollutant removal (kg/yr)      │ Canopy & space assessment                   │
│ PM2.5 ██████████ 142.8         │  Land available      12,000 m²              │
│ PM10  ████████████████ 228.0   │  Canopy at maturity   9,640 m²   80.3 %     │
│ NO₂   ███████ 96.4             │  Area required       10,320 m²   86.0 %     │
│ SO₂   █████ 71.2               │  ● OPTIMAL — 1,680 m² spare                 │
│ CO    ███████████ 152.6        │  Max plantable at this mix: 3,720 trees     │
├────────────────────────────────┴─────────────────────────────────────────────┤
│ 10-year cumulative CO₂ projection            Equivalent to, each year:       │
│  t │                            ╭────        🚗 590,000 car-km not driven     │
│    │              ╭─────────────╯            🏠 47 households' electricity    │
│    │   ╭──────────╯                          🫁 271 people's annual oxygen    │
│    └───┴────┴────┴────┴────┴────┴──▶ yr      ⛽ 30,900 litres of petrol       │
├──────────────────────────────────────────────────────────────────────────────┤
│ ⓘ Estimates based on published scientific averages…        [ Methodology → ] │
└──────────────────────────────────────────────────────────────────────────────┘
```

The **"How is this calculated?"** drawer slides from the right and shows the trace for a chosen figure:

```
  CO₂ · Neem
  800 planted  × 0.85 survival  × 1.00 maturity (mature)  = 680 effective trees
  680 × 21.772 kg CO₂/tree/yr = 14,805 kg/yr = 14.81 t/yr
  Coefficients: calc.survival.rate = 0.85 · calc.site.condition.factor = 1.00
  Source: Nowak & Crane (2002), Urban Forest Carbon Storage
```

### 5.4 Species Catalogue (`species.html`)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Tree species catalogue            25 species · reference values with sources │
├──────────────────────────────────────────────────────────────────────────────┤
│ 🔎[ Search name…    ] Growth[All▾] Water[All▾] Climate[All▾] Soil[All▾]      │
│ ☐ Native only        Sort[ CO₂ high→low ▾ ]                    ⊞ grid ▤ list │
├──────────────────────────────────────────────────────────────────────────────┤
│ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐ ┌───────────────┐     │
│ │  [  image  ]  │ │  [  image  ]  │ │  [  image  ]  │ │  [  image  ]  │     │
│ │ Neem   NATIVE │ │ Peepal NATIVE │ │ Banyan NATIVE │ │ Gulmohar      │     │
│ │ Azadirachta…  │ │ Ficus religi… │ │ Ficus bengha… │ │ Delonix regia │     │
│ │ ◉ 21.8 kg CO₂ │ │ ◉ 26.4 kg CO₂ │ │ ◉ 31.5 kg CO₂ │ │ ◉ 15.3 kg CO₂ │     │
│ │ ⚡FAST ⌾4.5 m │ │ ⚡MOD  ⌾6.0 m │ │ ⚡SLOW ⌾9.0 m │ │ ⚡FAST ⌾5.0 m │     │
│ │ 💧 Low        │ │ 💧 Moderate   │ │ 💧 Moderate   │ │ 💧 Low        │     │
│ └───────────────┘ └───────────────┘ └───────────────┘ └───────────────┘     │
│                          ‹ 1 2 3 ›   10 / page ▾                             │
└──────────────────────────────────────────────────────────────────────────────┘
```

Detail opens as a modal: full environmental table with units, horticultural profile, spacing guidance, and the **literature citation** (BR-19).

### 5.5 Compare (`compare.html`)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Compare plans        [Avenue Drive ×][Miyawaki Block ×][Park Belt ×] [+ add] │
├──────────────────────────────────────────────────────────────────────────────┤
│ METRIC                     AVENUE DRIVE   MIYAWAKI BLOCK    PARK BELT        │
│ Trees                            2,400         480 ▾           1,150         │
│ Land area (m²)                  12,000       1,200             8,400         │
│ CO₂ (t/yr)                     ★71.42       10.24             28.60          │
│ O₂ (kg/yr)                     ★52,100       7,880            21,400         │
│ Pollutants (kg/yr)                 984      ★1,102              610          │
│ Canopy coverage                  80.3 %    ★96.1 %            54.2 %         │
│ Impact score                        72        ★86                 64         │
│ Diversity H′                      1.42       ★2.01              1.18         │
│ ─── NORMALISED ────────────────────────────────────────────────────────────  │
│ CO₂ per tree (kg/yr)             29.76      ★21.33             24.87         │
│ CO₂ per 100 m² (kg/yr)          595.2      ★853.3             340.5          │
├──────────────────────────────────┬───────────────────────────────────────────┤
│ Grouped bars (normalised)        │ Radar — 5-axis profile                    │
│  ▐▌▐▌▐  ▐▌▐▌▐  ▐▌▐▌▐  ▐▌▐▌▐    │        carbon                             │
│  CO₂    O₂     Pollut  Canopy    │   diversity ◇ air                         │
│                                  │      canopy ─ space                       │
├──────────────────────────────────┴───────────────────────────────────────────┤
│ ★ = best on this metric                              [ ⬇ Comparison PDF ]    │
└──────────────────────────────────────────────────────────────────────────────┘
```

Selecting fewer than 2 keeps the view disabled with a tooltip; the 5th checkbox disables with a message that 4 is the readable maximum.

### 5.6 Admin — Coefficients (`admin/coefficients.html`)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│ Environmental coefficients   ⚠ Changes affect all NEW calculations. Existing │
│                                snapshots are unaffected.                     │
├──────────────────────────────────────────────────────────────────────────────┤
│ [ Adjustment ][ Feasibility ][ Equivalence ][ Scoring ][ Limits ][ Units ]   │
├──────────────────────────────────────────────────────────────────────────────┤
│ KEY                          LABEL              VALUE      UNIT   RANGE      │
│ calc.survival.rate           Expected survival [ 0.85  ]  ratio  0.10–1.00 ⟲ │
│ calc.maturity.years.fast     Years to maturity [ 7     ]  years  1–40      ⟲ │
│ calc.site.condition.factor   Site condition    [ 1.00  ]  factor 0.50–1.50 ⟲ │
│   ↳ Multiplier for soil/irrigation quality. Source: ICFRE (2019) guidelines. │
│ ────────────────────────────────────────────────────────────────────────────│
│ SCORING WEIGHTS                                   Σ = 1.00 ✓                 │
│ score.weight.carbon          Carbon weight     [ 0.35  ]  weight 0.00–1.00 ⟲ │
│ score.weight.air             Air-quality weight[ 0.30  ]  weight 0.00–1.00 ⟲ │
├──────────────────────────────────────────────────────────────────────────────┤
│                                        [ Restore defaults ]  [ Save changes ]│
└──────────────────────────────────────────────────────────────────────────────┘
```

`⟲` restores one coefficient to its seeded default. Saving is blocked while the weight
sum ≠ 1.00, with the live sum shown (BR-20).

---

## 6. Component Inventory

| Component | Behaviour |
|---|---|
| `kpiCard` | label · metric (tabular numerals) · unit · delta/subtext · metric-colour accent bar |
| `dataTable` | sticky header, zebra rows, right-aligned numerics, per-column sort, totals row, horizontal scroll inside its own container |
| `speciesPicker` | debounced search, keyboard-navigable listbox, `aria-activedescendant`, Enter to add |
| `metricBar` | labelled proportional bar with the value at the end — used for pollutants and canopy |
| `verdictChip` | `UNDER_UTILISED` info · `OPTIMAL` success · `TIGHT` warning · `OVERCROWDED` danger; icon + text, never colour alone |
| `scoreGauge` | 0–100 segmented bar with the four weighted sub-scores on hover |
| `toast` | bottom-right, `role="status"` / `aria-live="polite"`, auto-dismiss 4 s, pause on hover, max 3 stacked |
| `emptyState` | icon + headline + one sentence + primary action |
| `skeleton` | shimmer blocks matching the final layout — no layout shift on load |
| `disclaimerBanner` | mandatory on every screen showing computed values (FR-CALC-08) |
| `traceDrawer` | right-hand drawer showing the calculation trace (FR-CALC-15) |
| `confirmDialog` | focus-trapped, Esc to cancel, destructive action styled `--uc-danger` |

### State matrix — every data region implements all four

| State | Treatment |
|---|---|
| Loading | skeleton in place, no page-level spinner |
| Empty | `emptyState` with a call to action |
| Error | inline panel with the message and a **Retry** button; the rest of the page keeps working (NFR-08) |
| Loaded | content |

---

## 7. Responsive Behaviour

| Breakpoint | Layout |
|---|---|
| `< 576px` | single column; KPI cards 1-up; sidebar off-canvas; tables become stacked definition lists; estimate panel is a sticky bottom bar showing CO₂ + coverage |
| `576–991px` | KPI cards 2-up; editor columns stack, estimate panel above composition |
| `992–1199px` | sidebar visible; KPI 4-up; editor 60/40 split |
| `≥ 1200px` | full layout; charts side by side; max width 1280px centred |

Wide tables and charts scroll inside their own `overflow-x: auto` container — the page body never scrolls horizontally.

---

## 8. Accessibility Specification (NFR-07 → WCAG 2.1 AA)

| Requirement | Implementation |
|---|---|
| Contrast | All tokens verified ≥ 4.5:1 for text, ≥ 3:1 for UI boundaries, in **both** themes |
| Labels | Every control has a visible `<label for>`; placeholders are never the only label |
| Errors | `aria-invalid` + `aria-describedby` pointing at the message; focus moves to the first invalid field |
| Focus | 2px `--uc-primary` outline with 2px offset; never `outline: none` |
| Keyboard | Full operation without a mouse; skip-to-content link; focus trapped in modals and returned on close |
| Toasts | `aria-live="polite"` region announced to screen readers |
| Charts | Every chart has an adjacent, toggleable data table — the chart is never the sole carrier of information (NFR-07) |
| Colour | Verdicts and statuses always pair colour with an icon and text |
| Motion | All transitions disabled under `prefers-reduced-motion: reduce` |
| Structure | One `<h1>` per page, ordered headings, `<main>`/`<nav>`/`<aside>` landmarks |

---

## 9. Phase 4 Review

**Improvements made while designing**

1. **Metric-colour tokens** — fixing one colour per environmental metric across UI, charts and PDF turns a styling detail into a comprehension aid.
2. **The `⟳ 40 ms` chip on the live estimate** — makes the no-reload behaviour visible instead of merely true.
3. **Normalised rows in the comparison table** — a 2,400-tree plan will always beat a 480-tree plan on totals; without per-tree and per-area rows the screen would actively mislead.
4. **`traceDrawer` as a first-class component** — the strongest available answer to "is this just a black box?".
5. **Mandatory four-state contract** on every data region — empty and error states designed up front rather than discovered in testing.
6. **Dark theme defined in the token table**, not bolted on — every colour has a light *and* dark definition, so no screen can inherit an unreadable palette.

**Risks & mitigations**

| Risk | Mitigation |
|---|---|
| Live estimate feeling laggy | 300 ms debounce + a stateless endpoint that touches no database on the write path |
| Dense results screen overwhelming | Progressive disclosure — KPIs first, table second, trace on demand |
| Bootstrap defaults making it look generic | `tokens.css` loads after Bootstrap and overrides its CSS variables |

---

**End of Phase 4.** Next: Phase 5 — Maven project structure.
