# UrbanCanopy

**Urban tree plantation planning with estimated annual carbon sequestration and air-quality
impact.**

Compose a planting from a sourced species catalogue and get its annual CO₂ sequestration,
oxygen generation, pollutant removal, canopy coverage and spatial feasibility — computed from
published scientific averages, with every figure traceable to the values and arithmetic behind
it.

---

## What it does

| | |
|---|---|
| **Plan** | Describe a site and compose a species mix, with a live estimate that updates as you type — no page reload, no half-built plans written to the database |
| **Estimate** | CO₂ and O₂ per year, PM2.5/PM10/NO₂/SO₂/CO removal, canopy area and coverage, spatial feasibility, a 0–100 impact score, species diversity, and a multi-year projection |
| **Explain** | Every result carries a calculation trace — the arithmetic with the real numbers substituted — and a literature citation for every species |
| **Compare** | Two to four plans side by side, with per-tree and per-100 m² intensities so a large plan cannot win merely by being large |
| **Report** | A multi-page PDF: cover, composition, per-species contribution, canopy assessment, projection, equivalences, and a methodology appendix |
| **Administer** | Curate the species catalogue, revise the scientific coefficients without a redeploy, manage accounts, and read the audit and report logs |

**Figures are estimates.** They come from published per-species averages adjusted for expected
survival and maturity, not from measurements at your site. Every screen and document that shows
a computed value says so.

---

## Quick start

### Prerequisites

- **JDK 21+** (the build targets Java 21 bytecode; it compiles and runs on later JDKs)
- **MySQL 8.0.16+** (the schema uses `CHECK` constraints)
- **Maven 3.9+**

### 1. Configure the database

The application creates its own schema on first run. It needs a MySQL account that may create
databases, supplied through environment variables:

```bash
export DB_HOST=localhost     # default: localhost
export DB_PORT=3306          # default: 3306 — set this if your MySQL is on another port
export DB_NAME=urban_canopy  # default: urban_canopy
export DB_USERNAME=root
export DB_PASSWORD=your-password
```

On Windows PowerShell:

```powershell
$env:DB_PORT="3306"; $env:DB_USERNAME="root"; $env:DB_PASSWORD="your-password"
```

### 2. Run

```bash
mvn spring-boot:run
```

Then open **<http://localhost:8080>**.

Flyway creates the schema and seeds 25 tree species, 21 environmental coefficients and two
accounts on first start.

### 3. Sign in

The seeded accounts ship **without a usable password** — the seed migration writes a
placeholder, because SQL cannot compute a BCrypt hash and committing one would be a backdoor.
The `dev` profile supplies a password so a fresh checkout is usable immediately:

| Account | Email | Password |
|---|---|---|
| Planner | `planner@urbancanopy.local` | `Planner@123` |
| Administrator | `admin@urbancanopy.local` | `Planner@123` |

The application logs a loud warning naming every account it opens this way.

> **Before deploying anywhere real:** run under the `prod` profile, where no seed password is
> configured. Left unset, the seeded accounts simply cannot authenticate — an installation with
> no usable administrator is recoverable; one with a publicly known administrator password is
> not. Set `UC_SEED_PASSWORD` once, sign in, and change it.

### 4. Test

```bash
mvn test
```

**87 tests.** They need no database and no running server: the repository tests use an in-memory
H2 instance, and everything else is a unit or slice test.

---

## Technology

| Layer | Choice | Why |
|---|---|---|
| Backend | Spring Boot 3.5, Java 21 | — |
| Persistence | Spring Data JPA + MySQL 8 | |
| Schema | Flyway versioned migrations, `ddl-auto=validate` | Reviewable SQL; the application refuses to start if entities and schema drift |
| Security | Spring Security, BCrypt, session + CSRF | |
| PDF | OpenPDF | LGPL/MPL — safe to submit and to commercialise, unlike AGPL iText 7 |
| Frontend | Static HTML + vanilla ES modules, Bootstrap 5, Chart.js | No bundler, no framework — a clean API boundary |
| Numbers | `BigDecimal` end to end, serialised as JSON **strings** | JavaScript would silently round a 16-digit figure into a report |

Bootstrap and Chart.js are **vendored locally**, so the application runs with no internet access.

---

## Project structure

```
src/main/java/com/urbancanopy/
├── calculation/     THE DOMAIN CORE — pure, no Spring, no JPA, no I/O
│   ├── component/       one calculator per concern
│   └── model/           immutable inputs and results
├── plan/            plantation plans: entity · repository · service · dto · mapper · controller
├── species/         the reference catalogue
├── coefficient/     the tunable scientific constants
├── dashboard/       read-only aggregation
├── report/          PDF generation and the report log
├── user/            accounts, registration, authentication
├── audit/           the audit trail
├── common/          cross-cutting: dto · exception · security seam · util
└── config/          typed configuration, security, JPA auditing, web

src/main/resources/
├── db/migration/    V1 schema · V2 seed data · V3 timestamp correction
└── static/          the browser client (15 pages, 25 ES modules)
```

**The dependency rule:** `controller → service → { calculation-engine, repository } → entity`.
A controller never touches a repository. A service never returns an entity across the web
boundary. The calculation engine depends on nothing — not Spring, not JPA, not the database —
which is what makes the scientific core testable with a plain JUnit test.

---

## Two things that shaped the design

**The calculation engine is pure.** It accepts a value object and returns a value object,
performing no persistence and no I/O. That single constraint is why the live editor can estimate
a plan that has not been saved, why a completed plan can be re-evaluated against the coefficients
frozen when it was completed, and why 23 tests exercise the whole scientific core in
milliseconds.

**Nothing scientific is hardcoded.** Survival rates, maturity periods, feasibility thresholds,
equivalence factors and scoring weights are rows in a table an administrator can edit at
runtime. Each carries its own plausible range, its published source, and its seeded default so
any change can be undone. Adding a new equivalence — "this offsets N domestic flights" — is one
database row and no Java at all.

---

## Documentation

| Document | Contents |
|---|---|
| [`docs/01-requirements-and-use-cases.md`](docs/01-requirements-and-use-cases.md) | Functional requirements, use cases, business rules |
| [`docs/02-system-architecture.md`](docs/02-system-architecture.md) | Layering, package structure, request flow, the security seam |
| [`docs/03-database-schema.md`](docs/03-database-schema.md) | ER diagram, tables, constraints, indexing |
| [`docs/04-ui-wireframes-and-design-system.md`](docs/04-ui-wireframes-and-design-system.md) | Screens, design tokens, accessibility |
| [`docs/05-api-reference.md`](docs/05-api-reference.md) | Every endpoint, with conventions and error codes |
| [`docs/06-implementation-and-traceability.md`](docs/06-implementation-and-traceability.md) | What was built, and every requirement traced to its code and its test |

---

## Troubleshooting

**`Failed to determine a suitable driver class`, or `0 JPA repository interfaces` at start-up**
— `target/` is in an inconsistent state, usually because a build was interrupted. Delete it and
rebuild:

```bash
rm -rf target && mvn test
```

**`Port 8080 was already in use`** — a previous instance is still running. Stop it, or set
`SERVER_PORT` to something else.

**Flyway reports a checksum mismatch** — an already-applied migration was edited. Migrations are
immutable once applied; add a new one instead. (`V3` in this project exists for exactly that
reason.)
