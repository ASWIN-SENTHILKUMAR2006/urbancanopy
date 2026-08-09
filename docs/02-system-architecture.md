# Phase 2 — System Architecture

**Project:** UrbanCanopy — Urban Tree Plantation Plan to Estimate Annual Carbon Sequestration and Improve Air Quality
**Phase:** 2 of 14

---

## 1. Architectural Style

**Layered (clean) architecture, organised package-by-feature, delivered as a REST backend + static browser client.**

```
┌──────────────────────────────────────────────────────────────────────┐
│  CLIENT TIER — browser                                               │
│  Static HTML5 · CSS3 · Vanilla JS (ES modules) · Bootstrap 5 · Chart.js│
│  No template engine. All data arrives as JSON and is patched into the │
│  DOM — navigation between pages is the only full load.               │
└───────────────────────────────┬──────────────────────────────────────┘
                                │  HTTP/JSON  (fetch, via api.js)
┌───────────────────────────────▼──────────────────────────────────────┐
│  APPLICATION TIER — Spring Boot 3                                    │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐ │
│  │ WEB LAYER      @RestController · request DTOs · response DTOs  │ │
│  │                Bean Validation · @RestControllerAdvice          │ │
│  │                Knows HTTP. Knows no business rules.             │ │
│  └───────────────────────────┬────────────────────────────────────┘ │
│  ┌───────────────────────────▼────────────────────────────────────┐ │
│  │ SERVICE LAYER  @Service · @Transactional · orchestration       │ │
│  │                authorisation (ownership) · mapping · use cases  │ │
│  │                Knows business rules. Knows no HTTP, no SQL.     │ │
│  └──────────┬──────────────────────────────────┬──────────────────┘ │
│             │                                  │                    │
│  ┌──────────▼───────────────┐   ┌──────────────▼─────────────────┐ │
│  │ DOMAIN LAYER             │   │ PERSISTENCE LAYER              │ │
│  │ Environmental            │   │ @Repository · Spring Data JPA  │ │
│  │ Calculation Engine       │   │ JPA entities · specifications  │ │
│  │ Pure. Stateless.         │   │ Knows SQL. Knows no rules.     │ │
│  │ Zero Spring/JPA imports. │   └──────────────┬─────────────────┘ │
│  └──────────────────────────┘                  │                   │
└─────────────────────────────────────────────────┼───────────────────┘
                                                  │ JDBC
┌─────────────────────────────────────────────────▼───────────────────┐
│  DATA TIER — MySQL 8 · schema managed by Flyway versioned migrations │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.1 The dependency rule

Dependencies point **inward and downward only**:

```
controller → service → { calculation-engine , repository } → entity
```

- A controller never touches a repository or an entity.
- A service never returns an entity across the web boundary — always a DTO.
- The calculation engine depends on **nothing** — not Spring, not JPA, not the database.
  It receives plain value objects and returns plain value objects (FR-CALC-12).
  This is the single most important constraint in the system: it makes the scientific
  core testable with a plain JUnit test and impossible to pollute with infrastructure.

### 1.2 Why package-by-feature, not package-by-layer

The brief asks for controllers, services, repositories, entities, DTOs, configuration,
exceptions and utilities to be separated. They are — but **within a feature**, not across
the whole application:

```
com.urbancanopy.species.controller     ← not  com.urbancanopy.controller.SpeciesController
com.urbancanopy.species.service
com.urbancanopy.species.repository
com.urbancanopy.species.entity
com.urbancanopy.species.dto
```

| Criterion | package-by-layer | package-by-feature ✅ |
|---|---|---|
| Files touched for one change | scattered across 5 top packages | co-located in one package |
| Coupling visibility | hidden | obvious — a cross-feature import stands out |
| Deleting a feature | archaeology | delete one directory |
| Scales to 8 modules | poorly | well |

Cross-cutting code lives in `common/` and truly shared configuration in `config/`.

---

## 2. Package Structure

```
com.urbancanopy
│
├── UrbanCanopyApplication.java              Spring Boot entry point
│
├── config/                                  ← "configuration"
│   ├── ApplicationProperties.java           @ConfigurationProperties("app") — typed config
│   ├── WebMvcConfig.java                    static resources, formatters, page defaults
│   ├── JpaAuditingConfig.java               @EnableJpaAuditing + AuditorAware
│   └── JacksonConfig.java                   BigDecimal/date serialisation policy
│
├── common/                                  ← cross-cutting, feature-agnostic
│   ├── domain/
│   │   └── AuditableEntity.java             @MappedSuperclass: createdAt/By, updatedAt/By
│   ├── dto/
│   │   ├── ApiResponse.java                 uniform success envelope
│   │   ├── PageResponse.java                uniform pagination envelope
│   │   └── ErrorResponse.java               uniform error body
│   ├── exception/                           ← "exceptions"
│   │   ├── ResourceNotFoundException.java
│   │   ├── DuplicateResourceException.java
│   │   ├── BusinessRuleViolationException.java
│   │   ├── ForbiddenOperationException.java
│   │   └── GlobalExceptionHandler.java      @RestControllerAdvice
│   ├── security/
│   │   ├── CurrentUserProvider.java         ← THE SEAM (see §6)
│   │   └── DevCurrentUserProvider.java      pre-Phase-12 implementation
│   └── util/                                ← "utilities"
│       ├── AreaUnitConverter.java           acre/hectare → m²
│       ├── Decimals.java                    BigDecimal scale + rounding policy
│       └── EnumLabels.java                  enum → human label
│
├── user/            entity · repository · service · dto · mapper · controller
├── species/         entity · repository · service · dto · mapper · controller
├── coefficient/     entity · repository · service (cached) · dto · controller
├── plan/            entity · repository · service · dto · mapper · controller
│
├── calculation/                             ← THE DOMAIN CORE — pure, no framework
│   ├── model/                               immutable inputs & results (records)
│   ├── component/                           one calculator per concern (SRP)
│   ├── CoefficientSet.java                  resolved constants, passed in
│   └── EnvironmentalCalculationEngine.java  orchestrator
│
├── dashboard/       dto · service · controller       (read-only aggregation)
├── report/          entity · repository · service · pdf/ · controller
└── audit/           entity · repository · service
```

---

## 3. Component Responsibilities

| Component | Responsibility | Explicitly NOT responsible for |
|---|---|---|
| `*Controller` | HTTP mapping, `@Valid`, DTO in/out, status codes | business rules, persistence, mapping entities |
| `*Service` | use-case orchestration, transactions, ownership checks, invariants | HTTP concerns, SQL, PDF layout |
| `*Repository` | Spring Data JPA queries, projections, specifications | rules, mapping to DTOs |
| `*Mapper` | entity ⇄ DTO translation only | any decision-making |
| `EnvironmentalCalculationEngine` | evaluate a plan against coefficients | loading data, persisting, formatting |
| `CoefficientService` | resolve + cache the coefficient registry | applying coefficients |
| `PdfReportService` | compose a PDF from a result | computing the result |
| `GlobalExceptionHandler` | map exception → HTTP status + `ErrorResponse` | logging business decisions |

---

## 4. REST API Design

### 4.1 Conventions

| Concern | Convention |
|---|---|
| Base path | `/api/v1` |
| Naming | plural, kebab-case nouns: `/plantation-plans`, `/tree-species` |
| Verbs | `GET` read · `POST` create · `PUT` full update · `PATCH` partial/state · `DELETE` remove |
| Success envelope | `{ "success": true, "message": "...", "data": { … }, "timestamp": "…" }` |
| Page envelope | `{ "content": [], "page", "size", "totalElements", "totalPages", "first", "last" }` |
| Error envelope | `{ "timestamp", "status", "error", "message", "path", "fieldErrors": [ {field, message} ] }` |
| Pagination | `?page=0&size=10&sort=commonName,asc` (defaults externalised) |
| Money-grade numbers | JSON **strings** for `BigDecimal` to avoid IEEE-754 drift in JS |
| Units | every numeric field is named with its unit (`annualCo2Kg`) or paired with a `unit` field |

### 4.2 Endpoint surface

| Method | Path | Purpose | Access |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | create account | public |
| `POST` | `/api/v1/auth/login` | authenticate *(Phase 12)* | public |
| `POST` | `/api/v1/auth/logout` | end session *(Phase 12)* | auth |
| `GET` | `/api/v1/auth/me` | current user | auth |
| `GET` | `/api/v1/tree-species` | paginated, searchable, filterable catalogue | public |
| `GET` | `/api/v1/tree-species/{id}` | species detail | public |
| `GET` | `/api/v1/tree-species/options` | lightweight list for pickers | auth |
| `GET` | `/api/v1/tree-species/filters` | distinct filter values + enums | public |
| `POST` | `/api/v1/admin/tree-species` | create | admin |
| `PUT` | `/api/v1/admin/tree-species/{id}` | update | admin |
| `PATCH` | `/api/v1/admin/tree-species/{id}/status` | activate/deactivate | admin |
| `DELETE` | `/api/v1/admin/tree-species/{id}` | delete if unused | admin |
| `POST` | `/api/v1/admin/tree-species/{id}/image` | upload image | admin |
| `GET` | `/api/v1/plantation-plans` | my plans, paginated | auth |
| `POST` | `/api/v1/plantation-plans` | create plan | auth |
| `GET` | `/api/v1/plantation-plans/{id}` | plan detail + items | owner |
| `PUT` | `/api/v1/plantation-plans/{id}` | update plan header | owner |
| `DELETE` | `/api/v1/plantation-plans/{id}` | soft delete | owner |
| `POST` | `/api/v1/plantation-plans/{id}/duplicate` | clone as draft | owner |
| `PATCH` | `/api/v1/plantation-plans/{id}/status` | draft ⇄ completed ⇄ archived | owner |
| `POST` | `/api/v1/plantation-plans/{id}/items` | add item (upsert quantity) | owner |
| `PUT` | `/api/v1/plantation-plans/{id}/items/{itemId}` | change quantity | owner |
| `DELETE` | `/api/v1/plantation-plans/{id}/items/{itemId}` | remove item | owner |
| `GET` | `/api/v1/plantation-plans/{id}/estimate` | full calculation result | owner |
| `POST` | `/api/v1/estimates/preview` | **stateless** what-if calculation (live editor) | auth |
| `GET` | `/api/v1/comparisons?planIds=1,2,3` | compare 2–4 plans | owner |
| `GET` | `/api/v1/dashboard/summary` | KPI cards | auth |
| `GET` | `/api/v1/dashboard/charts/{chartKey}` | chart datasets | auth |
| `GET` | `/api/v1/reports/plans/{id}/pdf` | download plan report | owner |
| `GET` | `/api/v1/reports/comparison/pdf?planIds=` | download comparison report | owner |
| `GET` | `/api/v1/admin/users` … | user administration | admin |
| `GET/PUT` | `/api/v1/admin/coefficients` … | coefficient registry | admin |
| `GET` | `/api/v1/admin/report-logs`, `/audit-events` | audit trails | admin |

### 4.3 The `/estimates/preview` endpoint — why it exists

The plan editor must show a **live estimate** as the user types (FR-PLAN-13) — including
for a plan that has not been saved yet. Rather than forcing a save on every keystroke,
`POST /estimates/preview` accepts a transient `{landAreaSqm, projectionYears, items[]}`
body and returns a full result without persisting anything. It is the calculation engine's
purity (FR-CALC-12) made visible at the API surface.

---

## 5. Request Flow — worked example

**`GET /api/v1/plantation-plans/42/estimate`**

```
Browser  ──fetch──▶  PlanEstimateController.estimate(42)
                       │ 1. no logic here; delegates
                       ▼
                     PlanEstimateService.estimate(42)
                       │ 2. currentUserProvider.requireCurrentUser()
                       │ 3. planRepository.findDetailedById(42)   ← fetch-join items+species (no N+1)
                       │ 4. assertOwnedBy(plan, user)  ──▶ 403 if not
                       │ 5. coefficientService.activeSet()         ← cached CoefficientSet
                       │ 6. build CalculationInput (pure value object)
                       ▼
                     EnvironmentalCalculationEngine.evaluate(input, coefficients)
                       │ 7. CarbonCalculator · OxygenCalculator · PollutantCalculator
                       │    CanopyCalculator · FeasibilityEvaluator · DiversityCalculator
                       │    ProjectionCalculator · ImpactScorer · EquivalenceConverter
                       ▼  returns CalculationResult (immutable record)
                     PlanEstimateService
                       │ 8. map result → EstimateResponse DTO (+ disclaimer text)
                       ▼
                     Controller wraps in ApiResponse → 200 JSON
                       ▼
Browser  ◀── JSON ── renderEstimate(data) patches the DOM; Chart.js updates in place
```

Steps 6–7 involve **no database, no Spring, no HTTP** — that boundary is the whole point.

---

## 6. The Security Seam (deferred authentication)

Authentication ships in Phase 12, but nothing will need rewriting, because every service
asks a single abstraction who the caller is:

```java
public interface CurrentUserProvider {
    Optional<AuthenticatedUser> currentUser();
    AuthenticatedUser requireCurrentUser();   // throws UnauthenticatedException
    boolean isAdmin();
}
```

| Phase | Active implementation | Behaviour |
|---|---|---|
| 5 – 11 | `DevCurrentUserProvider` | returns the seeded demo planner; `isAdmin()` honours a request header/dev toggle so admin screens are exercisable |
| 12 → | `SecurityContextUserProvider` | reads the authenticated principal from `SecurityContextHolder` |

**Ownership rules are NOT deferred.** `assertOwnedBy(plan, user)` is domain logic and is
written into services from Phase 8, satisfying FR-PLAN-10 / BR-06 independently of how
authentication happens.

Mirror seam on the client: **all** network I/O goes through `api.js`. Adding the CSRF
header and 401→login handling in Phase 12 is a change to one file.

`spring-boot-starter-security` is deliberately absent from `pom.xml` until Phase 12, so no
throwaway permissive configuration is written and later deleted.

---

## 7. Cross-Cutting Concerns

| Concern | Mechanism |
|---|---|
| **Validation** | Bean Validation on request DTOs (`@NotBlank`, `@Positive`, `@Min`); business invariants in services; DB `CHECK`/`UNIQUE` as the last line. Three layers, deliberately. |
| **Exception handling** | `@RestControllerAdvice` maps a small exception taxonomy → status codes. `ResourceNotFound`→404, `Duplicate`→409, `BusinessRuleViolation`→422, `Forbidden`→403, `MethodArgumentNotValid`→400 with `fieldErrors`, anything else→500 with a generated reference id (logged, never exposed). |
| **Transactions** | `@Transactional` at the service method; `readOnly = true` on queries. Never on controllers or repositories. |
| **Auditing** | `AuditableEntity` `@MappedSuperclass` + `@EnableJpaAuditing` + `AuditorAware` backed by `CurrentUserProvider`. Zero boilerplate per entity. |
| **Configuration** | `ApplicationProperties` bound to `app.*`; profiles `dev` / `prod`; secrets via environment variables. No literal in Java. |
| **Coefficients** | `CoefficientService` caches the registry in a `ConcurrentHashMap`, evicted on admin update — so changes take effect with no restart (FR-ADM-05). |
| **Schema** | Flyway `V1__…sql`, `V2__…sql`; `ddl-auto=validate` so drift fails fast at startup. |
| **Logging** | SLF4J at service boundaries; a `requestId` MDC value tying a log line to an error reference; never logs credentials or emails. |
| **N+1 prevention** | `@EntityGraph` / explicit fetch joins on every list-with-children query; verified in tests. |
| **Numeric policy** | `BigDecimal` end-to-end, scale 6 internally / 2 for presentation, `HALF_UP`, centralised in `Decimals`. |

---

## 8. Frontend Architecture

```
src/main/resources/static/
├── index.html                landing
├── dashboard.html            KPIs + charts
├── species.html              public catalogue
├── plans.html                my plans
├── plan-editor.html          create/edit + LIVE estimate
├── plan-results.html         full impact breakdown
├── compare.html              2–4 plan comparison
├── profile.html
├── login.html · register.html            (Phase 12)
├── admin/                    species · users · coefficients · logs
├── css/
│   ├── tokens.css            design tokens: colour, spacing, radius, shadow, type
│   ├── app.css               layout, components, utilities
│   └── charts.css
├── js/
│   ├── core/
│   │   ├── api.js            ★ single fetch wrapper — the client security seam
│   │   ├── dom.js            el(), mount(), clear(), delegate() — tiny render helpers
│   │   ├── toast.js          ARIA-live notifications (FR-SYS-04)
│   │   ├── format.js         numbers, units, dates — one formatting policy
│   │   ├── validate.js       mirrors server rules for instant feedback
│   │   └── state.js          per-page in-memory store + subscribe
│   ├── components/           speciesPicker · kpiCard · dataTable · chartFactory · emptyState
│   └── pages/                one module per HTML page
└── img/
```

**Conventions**
- ES modules (`<script type="module">`), no bundler, no framework — matches the stated stack.
- One page module per HTML file; the page module owns the page and nothing else.
- Rendering is explicit DOM construction via `dom.js` — no `innerHTML` with user data, which closes the XSS hole that string templating opens.
- Bootstrap 5 supplies grid and base components; `tokens.css` overrides its palette so the app looks purpose-built rather than default-Bootstrap.
- Every mutation → optimistic UI is avoided; instead: disable control → await response → patch DOM → toast. Predictable and honest.

---

## 9. Technology Decisions & Rationale

| Decision | Chosen | Rationale | Rejected alternative |
|---|---|---|---|
| View layer | Static HTML + `fetch` | Clean API boundary, no template/JSON duality, testable with MockMvc | Thymeleaf — two rendering paradigms |
| Persistence | Spring Data JPA | Mature, declarative, audit support | JDBC template — too much boilerplate |
| Schema | Flyway + `ddl-auto=validate` | Reviewable SQL, reproducible, submittable as documentation | `ddl-auto=update` — silent, unreviewable drift |
| Numbers | `BigDecimal` | Reproducible, no float drift in reported figures | `double` |
| PDF | OpenPDF | LGPL/MPL — safe to submit and to commercialise | iText 7 (AGPL) |
| Mapping | Hand-written mappers | Zero magic, no annotation processor, fully debuggable | MapStruct — build complexity for ~8 entities |
| Boilerplate | Plain Java + records | Java 21 records remove most of the need | Lombok — extra agent, IDE coupling |
| Roles | Single `role` column, enum | The role set is closed and fixed; a join table adds no normalisation benefit | `roles` + `user_roles` tables |
| Caching | In-process map for coefficients | Small, read-heavy, single-node | Redis — infrastructure without a need |
| Java level | Compile to release 21 on JDK 25 | Meets the stated Java 21 requirement; runs on the installed JDK | pinning to a JDK not present |

---

## 10. Deployment View

```
┌────────────┐   HTTPS   ┌────────────────────────────┐   JDBC   ┌──────────┐
│  Browser   │ ────────▶ │  urban-canopy.jar          │ ───────▶ │ MySQL 8  │
│            │           │  Spring Boot + embedded    │          │          │
│  static/   │ ◀──────── │  Tomcat (serves static/    │          │ Flyway-  │
│  assets    │           │  and /api/v1)              │          │ managed  │
└────────────┘           └────────────┬───────────────┘          └──────────┘
                                      │ file I/O
                                ┌─────▼──────┐
                                │ uploads/   │  species images (externalised path)
                                └────────────┘
```

Single self-contained executable JAR. Externalised: datasource URL/credentials, upload
directory, page-size defaults, CORS origins. Profiles: `dev` (verbose SQL, seeded demo
data) and `prod` (`validate` only, minimal logging).

---

## 11. Quality Attributes → Architectural Tactics

| Attribute | Tactic |
|---|---|
| Testability | Pure engine (no Spring context); services depend on interfaces; `@DataJpaTest` for repositories; MockMvc for controllers |
| Performance | Pagination everywhere; fetch joins; coefficient cache; the engine is O(n) over plan items |
| Security (Phase 12) | BCrypt, CSRF, session fixation protection, method security, DTO boundary prevents entity over-posting |
| Maintainability | One formula lives in exactly one calculator class; feature packages; the dependency rule |
| Extensibility | New pollutant = one column + one field in a record + one line in `PollutantCalculator`. New equivalence = one row in the coefficient table, **zero code**. |
| Auditability | `AuditableEntity` on all mutable entities; result snapshots freeze the coefficient set |

---

## 12. Phase 2 Review

**Improvements made while designing**

1. **`/estimates/preview`** added — the live editor requirement was otherwise going to
   force premature persistence of half-built plans.
2. **`CoefficientSet` passed as an argument** rather than injected into the engine — keeps
   the engine pure *and* makes snapshot-based recomputation trivial (pass the frozen set).
3. **BigDecimal serialised as JSON strings** — prevents JavaScript silently rounding a
   16-digit figure in a report.
4. **`dom.js` instead of `innerHTML`** — a deliberate XSS-prevention choice at the
   architectural level, not a code-review afterthought.
5. **Extensibility test applied to the design:** adding an equivalence ("this equals N
   flights") requires *no code change* — only a coefficient row. That is the proof that
   BR-15 ("never hardcode") is architecturally enforced rather than merely stated.

**Risks & mitigations**

| Risk | Mitigation |
|---|---|
| No bundler → many small JS requests | ES modules with few, coarse modules; HTTP/2 keep-alive; assets cached |
| Deferred auth leaves ownership untested | Ownership implemented in Phase 8 with unit tests using a stubbed `CurrentUserProvider` |
| JDK 25 vs. stated Java 21 | `maven.compiler.release=21` — Java-21 bytecode, verified by the build |

---

**End of Phase 2.** Next: Phase 3 — Database schema & ER diagram.
