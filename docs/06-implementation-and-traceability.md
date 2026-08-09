# Phase 14 — Implementation & Requirements Traceability

**Project:** UrbanCanopy
**Phase:** 14 of 14

What was built in phases 5–13, every requirement traced to the code that satisfies it and the
test that proves it, and an honest account of what was not built.

---

## 1. What exists

| | Count |
|---|---|
| Java source files | 136 |
| Test source files | 9 (**87 tests**) |
| Browser ES modules | 25 |
| HTML pages | 15 |
| REST endpoints | 47 mapped, plus `login` / `logout` via filters |
| Flyway migrations | 3 |
| Seeded species | 25 |
| Environmental coefficients | 21 |

### 1.1 Phase outcomes

| Phase | Delivered |
|---|---|
| 1–4 | Requirements, architecture, schema, UI design system (`docs/01`–`04`) |
| 5 | Maven project, typed configuration, no security starter yet — deliberately |
| 6 | Entities, repositories, migrations, seed data |
| 7 | **Calculation engine** — pure, framework-free, 23 tests |
| 8 | REST backend: DTOs, services, controllers, global exception handling |
| 9 | Browser client: shell, design tokens, `api.js` seam, species, planner |
| 10 | Dashboard and charts; the metric palette ratified against the data-visualisation validator |
| 11 | PDF reporting with OpenPDF, plus the report log |
| 12 | Security: BCrypt, sessions, CSRF, lockout, registration — the identity seam replaced |
| 13 | Repository, service, entity and web-security tests (33 → 87) |
| 14 | README, API reference, this document |

---

## 2. Requirements traceability

**Legend:** ✅ implemented and tested · ◑ implemented, verified manually · ⛔ not implemented

### 2.1 Authentication & accounts (`FR-AUTH`)

| ID | Implementation | Verification |
|---|---|---|
| FR-AUTH-01 | `RegistrationService.register` | ✅ manual + boundary tests |
| FR-AUTH-02 | BCrypt via `SecurityConfig.passwordEncoder`; `UserResponse` has no password component at all | ✅ |
| FR-AUTH-03 | `RegistrationService.assertPasswordMeetsPolicy`, bound to `app.password-policy.*` | ◑ verified live: weak password → 422 naming every unmet rule |
| FR-AUTH-04 | `UserRepository.existsByEmail` | ◑ verified live: duplicate → 409 |
| FR-AUTH-05 | Spring Security form login, `UrbanCanopyUserDetailsService` | ✅ `SecurityBoundaryTest` |
| FR-AUTH-06 | `SecurityConfig` logout: session invalidated, cookie cleared, POST only | ◑ verified live |
| FR-AUTH-07 | Registration hardcodes `ROLE_USER`; role changes only via `/admin/users/{id}/role` | ✅ |
| FR-AUTH-08 | `BootstrapCredentialsInitializer` | ◑ verified live on a fresh database |
| FR-AUTH-09 | `UserService.updateOwnProfile`, `RegistrationService.changeOwnPassword` | ◑ |
| FR-AUTH-10 | `enabled` flag honoured by `UserDetails`; disabled accounts refused with a clear message | ◑ |
| FR-AUTH-11 | `LoginAttemptHandler` counts failures and locks | ◑ verified live: 5 failures → locked; correct password then refused |
| FR-AUTH-12 | `CookieCsrfTokenRepository` + `csrfCookieFilter` | ✅ `SecurityBoundaryTest` — write without token → 403 |

### 2.2 Species catalogue (`FR-SPEC`)

| ID | Implementation | Verification |
|---|---|---|
| FR-SPEC-01…05 | `TreeSpeciesService`, `AdminTreeSpeciesController`, `SpeciesImageService` | ◑ |
| FR-SPEC-06 | `sourceCitation`, required, carried into the report appendix | ✅ `ReportWriterTest.citesEverySpecies` |
| FR-SPEC-07 | `assertNamesAvailable` (BR-05) | ◑ verified live: duplicate → 409 |
| FR-SPEC-08…10 | `TreeSpeciesController`, `TreeSpeciesRepository.search` | ✅ + browser harness |
| FR-SPEC-11, 12 | Deactivation, and deletion refused when in use (BR-18) | ◑ verified live: → 422 naming the dependent plans |
| FR-SPEC-13 | `V2__seed_reference_data.sql` — 25 species, each cited | ✅ verified on a fresh database |
| FR-SPEC-14 | CSV bulk import | ⛔ **not implemented** (priority C) |

### 2.3 Plantation planning (`FR-PLAN`)

| ID | Implementation | Verification |
|---|---|---|
| FR-PLAN-01, 02 | `PlantationPlan`, `PlantationPurpose` | ✅ `PlantationPlanTest` |
| FR-PLAN-03, 04 | `PlantationPlan.addSpecies` — increments rather than duplicating (BR-08) | ✅ `PlantationPlanTest.addingTwiceIncrements` |
| FR-PLAN-05, 06 | `PlantationPlanService`, `PlanStatus` state machine | ✅ `PlanStatusTest` (13 tests) |
| FR-PLAN-07 | `searchForOwner` — scoped in the query, not filtered after | ✅ `PlantationPlanRepositoryTest` |
| FR-PLAN-08 | `duplicate`, with collision-free "(copy N)" titles | ◑ |
| FR-PLAN-09 | Soft delete (BR-21) | ✅ repository tests + live |
| **FR-PLAN-10** | `PlanAccessService.requireOwned` | ✅ **13 tests**, plus a live cross-user check: every route → 403 |
| FR-PLAN-11 | `requireReadable` admits admins read-only and audits every access | ✅ `PlanAccessServiceTest.adminReadIsAudited` |
| FR-PLAN-12 | `LandAreaUnit` normalisation (BR-07) | ✅ `PlantationPlanTest.normalisesLandArea` |
| FR-PLAN-13 | `POST /estimates/preview` + 300 ms debounce | ◑ verified live: 10 ms round trip |

### 2.4 Calculation engine (`FR-CALC`)

The engine carries the densest test coverage in the project — 23 tests over a pure function.

| ID | Implementation | Verification |
|---|---|---|
| FR-CALC-01…04 | `SpeciesContributionCalculator`, canopy aggregation | ✅ |
| FR-CALC-05 | `FeasibilityEvaluator` — four-way verdict | ✅ 6 tests |
| FR-CALC-06 | `EffectiveTreeCalculator`, all factors from coefficients | ✅ |
| FR-CALC-07 | `ProjectionCalculator` | ✅ 3 tests |
| FR-CALC-08 | Disclaimer carried inside `CalculationResult`, so no caller can render figures without it | ✅ `alwaysCarriesDisclaimer` |
| FR-CALC-09 | `EquivalenceConverter`, driven entirely by coefficient rows | ✅ |
| FR-CALC-10, 11 | `ImpactScorer`, `DiversityCalculator` | ✅ 5 tests |
| FR-CALC-12 | The engine imports no Spring, no JPA, no I/O | ✅ proved by its tests running with no context |
| FR-CALC-13 | `BigDecimal` + `Decimals` policy | ✅ |
| FR-CALC-14 | `PlanEstimateService.writeSnapshot`, replayed through the frozen `CoefficientSet` | ◑ verified live: coefficient changed 0.85 → 0.60; completed plan held at 45,458 kg, draft moved to 32,640 kg |
| FR-CALC-15 | Trace entries with the real numbers substituted | ✅ `producesExplainableTrace` |

### 2.5 Comparison (`FR-CMP`)

| ID | Implementation | Verification |
|---|---|---|
| FR-CMP-01 | 2–4 plans, bounds from configuration | ◑ live: 1 plan → 422 |
| FR-CMP-02 | `PlanComparisonService` — three metric groups | ✅ browser harness |
| FR-CMP-03 | Per-tree and per-100 m² intensities; best-per-metric marked | ✅ |
| FR-CMP-04 | PDF export ✅; **grouped bar and radar charts** on the compare screen | ⛔ **partial** — see §3 |

### 2.6 Dashboard (`FR-DASH`)

| ID | Implementation | Verification |
|---|---|---|
| FR-DASH-01 | `DashboardService.aggregate` | ✅ browser harness |
| FR-DASH-02…05 | Four charts via `/dashboard/charts/{key}` | ✅ verified rendered: correct types, point counts, colours |
| FR-DASH-06 | `findRecentForOwner` | ✅ repository test asserts the ordering |
| FR-DASH-07 | Admin block, present only for administrators | ✅ |
| FR-DASH-08 | JSON endpoints, scoped in-query | ✅ |
| FR-DASH-09 | Designed empty states everywhere | ✅ `states.js` four-state contract |

### 2.7 Reports (`FR-RPT`)

| ID | Implementation | Verification |
|---|---|---|
| FR-RPT-01, 02 | `PlanImpactReportWriter` — all nine sections | ✅ `ReportWriterTest.carriesEverySection` |
| FR-RPT-03 | Charts drawn as **PDF vectors**, not screenshots — sharp in print, no browser round trip | ✅ |
| FR-RPT-04 | `PageDecorator`, "Page X of Y" via a deferred template | ✅ `numbersPages` — this test caught a real off-by-one |
| FR-RPT-05 | `ComparisonReportWriter` | ◑ verified live |
| FR-RPT-06 | `ReportLog`, written after the bytes exist | ◑ verified live |
| FR-RPT-07 | Streaming with ASCII-slugified filename | ◑ verified live |
| FR-RPT-08 | Ownership via the same estimate/comparison paths the screens use | ◑ live: another user → 403 |
| FR-RPT-09 | CSV/Excel export | ⛔ **not implemented** (priority C) |

### 2.8 Administration (`FR-ADM`)

| ID | Implementation | Verification |
|---|---|---|
| FR-ADM-01, 02 | `UserService`, `AdminUserController` | ✅ |
| FR-ADM-03 | Self-lockout guard, and BR-22's last-administrator check | ◑ |
| FR-ADM-04…06 | `CoefficientAdminService`; range-validated, restorable | ◑ verified live, including restore |
| FR-ADM-07 | `AuditService`, read-only log | ◑ verified live: coefficient changes recorded `0.850000 → 0.60` |
| FR-ADM-08 | One rule over `/api/v1/admin/**`; styled refusal, not a redirect loop | ✅ `SecurityBoundaryTest` + browser harness |

### 2.9 System-wide (`FR-SYS`)

| ID | Implementation | Verification |
|---|---|---|
| FR-SYS-01 | Bean Validation server-side, mirrored by `validate.js` | ✅ |
| FR-SYS-02 | `applyServerErrors` attaches messages to fields and preserves input | ✅ |
| FR-SYS-03 | `GlobalExceptionHandler` — one exception taxonomy → one envelope | ✅ |
| FR-SYS-04 | `toast.js`, `aria-live` | ✅ |
| FR-SYS-05 | `AuditableEntity` + `@EnableJpaAuditing` | ✅ repository tests fail without it |
| FR-SYS-06 | `ApplicationProperties`, validated at start-up | ✅ |
| FR-SYS-07 | Flyway; `ddl-auto=validate` | ✅ |
| FR-SYS-08 | SLF4J at service boundaries; no credential is ever logged | ◑ |

### 2.10 Business rules

| Rule | Where it lives | Verification |
|---|---|---|
| BR-02 | BCrypt only | ✅ |
| BR-05 | Species name uniqueness | ◑ |
| **BR-06** | `PlanAccessService` | ✅ 13 tests |
| BR-07 | `LandAreaUnit.toSquareMetres` | ✅ |
| BR-08 | `PlantationPlan.addSpecies` **+ a database unique constraint** | ✅ |
| BR-09, 14 | Configured planning limits | ◑ |
| BR-10 | Completion requires species and land area | ✅ `PlanStatusTest`, live → 422 |
| BR-13 | Feasibility is advisory, never blocking | ✅ |
| BR-15 | No scientific value hardcoded — all 21 are table rows | ✅ start-up verification refuses to boot if one is missing |
| BR-18 | Deactivate, never delete a species in use | ◑ live → 422 |
| BR-19 | Every reference value carries a citation | ✅ |
| BR-20 | Scoring weights sum to 1.0 | ✅ checked at start-up and on every edit |
| BR-21 | Soft delete | ✅ |
| BR-22 | At least one enabled administrator | ◑ |

---

## 3. What was not built

Stated plainly rather than left to be discovered.

| Requirement | Priority | Status |
|---|---|---|
| **FR-CMP-04** — grouped bar and radar charts on the compare screen | Should | **Partial.** The comparison table and its PDF export are complete; the two charts are not built. The data is already shaped for them — the `profile` metric group exists precisely to supply five radar axes — so this is a client-side addition, not a redesign |
| **FR-RPT-09** — plan-item CSV/Excel export | Could | Not implemented |
| **FR-SPEC-14** — CSV bulk species import | Could | Not implemented |

Nothing rated **Must** is outstanding.

### 3.1 Known limitations

- **Repository tests run on H2, not MySQL.** The migrations are deliberately MySQL-specific
  (InnoDB, utf8mb4 collation, a JSON column), so the slice tests generate the schema from the
  entities instead. They verify the JPQL, not that entities match the migrated schema — the
  application checks that itself on every real start-up via `ddl-auto=validate`.
- **No end-to-end test suite in the build.** The browser was driven through the DevTools
  Protocol during development to verify all 13 pages render with no console errors, but that
  harness is not part of `mvn test`.
- **Sessions are in-memory.** A restart signs everyone out, and the application cannot be run as
  more than one instance without a shared session store.

---

## 4. Decisions worth carrying forward

Five choices did more work than their size suggests.

**The identity seam.** `CurrentUserProvider` was introduced in Phase 5 and its real
implementation arrived in Phase 12 — seven phases later. Replacing the entire authentication
mechanism cost **one new class**: no service, controller or ownership rule changed. The
ownership rules written in Phase 8 against a stand-in identity were proved correct against real
users without modification.

**The pure calculation engine.** Because it depends on nothing, the same code serves the saved
plan, the unsaved live editor, and the historical replay against frozen coefficients. Its 23
tests need no Spring context and run in milliseconds.

**Coefficients as data.** Twenty-one scientific constants live in a table with ranges, sources
and defaults. An administrator revises one and the next estimate reflects it — while every
completed plan keeps its figures, because its snapshot froze the set that produced them.

**`BigDecimal` as JSON strings.** An unglamorous decision that prevents a lifetime-carbon figure
being silently rounded by JavaScript on its way into a PDF that calls itself a scientific
estimate.

**Migrations are immutable.** When the seeded timestamps were found to be stored in local time
while the application reads UTC, the fix was `V3`, not an edit to `V2`. Editing an applied
migration changes its checksum and stops every existing installation from starting — trading a
cosmetic defect for an outage.

---

## 5. Phase 14 review

**Self-review against the brief**

| Check | Status |
|---|---|
| Every Must-priority requirement implemented | ✅ |
| Layered architecture with separated concerns | ✅ controller · service · repository · entity · DTO · config · exception · util |
| Schema under versioned migration | ✅ 3 migrations, `validate` on start-up |
| Security: hashing, CSRF, sessions, method-level rules | ✅ |
| Tests | ✅ 87, covering the engine, entities, repositories, ownership and the web boundary |
| Documentation | ✅ six documents plus a README |
| Outstanding work stated rather than hidden | ✅ §3 |

**What I would do next, in order**

1. The two comparison charts (FR-CMP-04) — the data already has the right shape.
2. Promote the browser harness into the build as a proper end-to-end suite.
3. Externalise sessions, so the application can run as more than one instance.
4. CSV import and export (FR-SPEC-14, FR-RPT-09), the two Could-priority gaps.

---

**End of Phase 14. End of the fourteen-phase plan.**
