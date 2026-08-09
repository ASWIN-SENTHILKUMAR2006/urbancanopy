# Phase 3 — Database Schema & ER Diagram

**Project:** UrbanCanopy
**Phase:** 3 of 14
**DBMS:** MySQL 8.0 · InnoDB · `utf8mb4_0900_ai_ci`
**Schema management:** Flyway versioned migrations (`V1__baseline.sql`, `V2__seed_reference_data.sql`)

---

## 1. ER Diagram

```mermaid
erDiagram
    USERS ||--o{ PLANTATION_PLANS : "owns"
    USERS ||--o{ REPORT_LOGS : "requests"
    USERS ||--o{ AUDIT_EVENTS : "triggers"

    PLANTATION_PLANS ||--|{ PLAN_ITEMS : "contains"
    PLANTATION_PLANS ||--o| PLAN_RESULT_SNAPSHOTS : "freezes"
    PLANTATION_PLANS ||--o{ REPORT_LOGS : "documented by"

    TREE_SPECIES ||--o{ PLAN_ITEMS : "selected in"

    ENVIRONMENTAL_COEFFICIENTS }o..o{ PLAN_RESULT_SNAPSHOTS : "frozen into (JSON)"

    USERS {
        bigint    id PK
        varchar   full_name
        varchar   email UK
        varchar   password_hash
        varchar   organisation
        varchar   designation
        varchar   role "ROLE_USER | ROLE_ADMIN"
        boolean   enabled
        int       failed_login_attempts
        datetime  lock_expires_at
        datetime  last_login_at
        datetime  created_at
        datetime  updated_at
        varchar   created_by
        varchar   updated_by
    }

    TREE_SPECIES {
        bigint    id PK
        varchar   common_name UK
        varchar   scientific_name UK
        varchar   family
        varchar   local_name
        text      description
        varchar   image_path
        decimal   co2_sequestration_kg_year "kg CO2/tree/yr"
        decimal   oxygen_production_kg_year "kg O2/tree/yr"
        decimal   pm25_removal_g_year "g/tree/yr"
        decimal   pm10_removal_g_year "g/tree/yr"
        decimal   no2_removal_g_year "g/tree/yr"
        decimal   so2_removal_g_year "g/tree/yr"
        decimal   co_removal_g_year "g/tree/yr"
        varchar   growth_rate "SLOW|MODERATE|FAST"
        varchar   water_requirement "LOW|MODERATE|HIGH"
        varchar   climate_zone
        varchar   soil_type
        decimal   canopy_radius_m
        decimal   mature_height_m
        int       life_span_years
        decimal   spacing_m
        boolean   native_species
        varchar   source_citation
        boolean   active
        datetime  created_at
        datetime  updated_at
    }

    PLANTATION_PLANS {
        bigint    id PK
        bigint    user_id FK
        varchar   title
        text      description
        varchar   site_name
        decimal   land_area_sqm "normalised"
        decimal   land_area_input "as entered"
        varchar   land_area_unit "SQUARE_METRE|HECTARE|ACRE"
        varchar   purpose
        int       target_start_year
        int       projection_years
        varchar   status "DRAFT|COMPLETED|ARCHIVED"
        boolean   deleted
        datetime  created_at
        datetime  updated_at
    }

    PLAN_ITEMS {
        bigint    id PK
        bigint    plan_id FK
        bigint    species_id FK
        int       quantity
        datetime  created_at
        datetime  updated_at
    }

    PLAN_RESULT_SNAPSHOTS {
        bigint    id PK
        bigint    plan_id FK-UK
        int       total_trees
        decimal   effective_trees
        decimal   annual_co2_kg
        decimal   annual_o2_kg
        decimal   annual_pm25_g
        decimal   annual_pm10_g
        decimal   annual_no2_g
        decimal   annual_so2_g
        decimal   annual_co_g
        decimal   total_pollutant_g
        decimal   canopy_area_sqm
        decimal   canopy_coverage_percent
        decimal   required_area_sqm
        decimal   space_utilisation_percent
        varchar   feasibility
        decimal   diversity_index
        decimal   impact_score
        decimal   horizon_co2_kg
        json      coefficient_snapshot
        varchar   engine_version
        datetime  computed_at
    }

    ENVIRONMENTAL_COEFFICIENTS {
        bigint    id PK
        varchar   coefficient_key UK
        varchar   label
        decimal   coefficient_value
        decimal   default_value
        decimal   min_value
        decimal   max_value
        varchar   unit
        varchar   category
        varchar   description
        varchar   source
        datetime  created_at
        datetime  updated_at
    }

    REPORT_LOGS {
        bigint    id PK
        bigint    user_id FK
        bigint    plan_id FK "null for comparison"
        varchar   report_type
        varchar   plan_ids "csv, comparison only"
        varchar   file_name
        bigint    file_size_bytes
        datetime  generated_at
    }

    AUDIT_EVENTS {
        bigint    id PK
        bigint    actor_id FK
        varchar   event_type
        varchar   entity_type
        bigint    entity_id
        varchar   detail
        varchar   ip_address
        datetime  occurred_at
    }
```

---

## 2. Relationship Summary

| Relationship | Cardinality | Delete behaviour | Rationale |
|---|---|---|---|
| `users` → `plantation_plans` | 1 : 0..N | `RESTRICT` | A user with plans is disabled, never deleted (BR-22 spirit) |
| `plantation_plans` → `plan_items` | 1 : 1..N | `CASCADE` | Items are a composition — meaningless without their plan |
| `tree_species` → `plan_items` | 1 : 0..N | `RESTRICT` | Protects historical plans; deletion is replaced by `active = false` (BR-18) |
| `plantation_plans` → `plan_result_snapshots` | 1 : 0..1 | `CASCADE` | One frozen result per plan |
| `plantation_plans` → `report_logs` | 1 : 0..N | `SET NULL` | The log survives the plan for audit purposes (BR-17) |
| `users` → `report_logs` / `audit_events` | 1 : 0..N | `SET NULL` | Audit history outlives account changes |

`plan_items` carries a composite unique key `(plan_id, species_id)` — the database-level
enforcement of BR-08 ("a species appears at most once per plan"). The service layer
converts a violation into an increment, but the constraint guarantees the invariant even
under concurrency.

---

## 3. Normalisation Analysis

| Table | Form | Justification |
|---|---|---|
| `users` | BCNF | Every non-key attribute depends on `id`; `email` is a candidate key |
| `tree_species` | BCNF | All attributes are single-valued facts about the species. Environmental rates are *stored measurements*, not derivable |
| `plantation_plans` | BCNF | `land_area_sqm` is a stored normalisation of `(land_area_input, land_area_unit)` — a deliberate, documented denormalisation for query performance |
| `plan_items` | BCNF | Associative entity resolving the M:N between plans and species, carrying `quantity` |
| `plan_result_snapshots` | BCNF | **Intentionally denormalised** — every column is derivable from `plan_items` × coefficients. It exists precisely to be *not* recomputed (FR-CALC-14) |
| `environmental_coefficients` | BCNF | Key–value–metadata registry |

### Two deliberate denormalisations, and why

1. **`land_area_sqm` alongside `land_area_input` + `land_area_unit`.**
   The user's input is preserved for faithful redisplay; the normalised m² value is stored
   so that every query, sort and calculation uses one unit. Recomputing on every read
   would be pure waste, and rounding it repeatedly would drift.

2. **`plan_result_snapshots` duplicating computable values.**
   This is the mechanism that makes a report reproducible after an administrator edits a
   coefficient. `coefficient_snapshot` (JSON) records the exact constants in force, so a
   report generated in March still reconciles in December. Normalising this away would
   destroy auditability — the point of the table is that it is frozen.

### Why roles are a column, not a table

The role set is **closed and fixed** (`ROLE_USER`, `ROLE_ADMIN`), a user has exactly one,
and `ROLE_ADMIN` inherits `ROLE_USER` through Spring Security's role hierarchy. A
`roles` + `user_roles` pair would remove no repeating group, eliminate no transitive
dependency, and add two joins to every authorisation check. A `VARCHAR` column with a
`CHECK` constraint is both normalised and honest. *(Should roles ever become open-ended, a
join table is an additive migration.)*

---

## 4. Index Strategy

| Table | Index | Serves |
|---|---|---|
| `users` | `uq_users_email` | login, duplicate check (FR-AUTH-04) |
| `users` | `idx_users_role_enabled` | admin user listing filters |
| `tree_species` | `uq_species_common_name`, `uq_species_scientific_name` | BR-05 |
| `tree_species` | `idx_species_active` | catalogue listing excludes inactive |
| `tree_species` | `idx_species_growth_climate` | composite filter (FR-SPEC-10) |
| `tree_species` | `idx_species_co2` | "highest sequestration" sort |
| `plantation_plans` | `idx_plans_user_deleted` | "my plans" — the hottest query in the app |
| `plantation_plans` | `idx_plans_status`, `idx_plans_purpose` | list filters + dashboard grouping |
| `plan_items` | `uq_plan_item_plan_species` | BR-08 |
| `plan_items` | `idx_plan_items_species` | "is this species in use?" before delete (BR-18) |
| `plan_result_snapshots` | `uq_snapshot_plan` | 1:0..1 |
| `environmental_coefficients` | `uq_coefficient_key` | registry lookup |
| `report_logs` | `idx_report_logs_user_date` | audit listing |
| `audit_events` | `idx_audit_type_date` | audit filtering |

Composite index column order follows selectivity: `(user_id, deleted)` not `(deleted, user_id)`.

---

## 5. Constraint Catalogue

| Type | Examples |
|---|---|
| **PRIMARY KEY** | surrogate `BIGINT AUTO_INCREMENT` on every table — stable, compact, join-friendly |
| **UNIQUE** | `users.email`; `tree_species.common_name`, `.scientific_name`; `plan_items(plan_id, species_id)`; `coefficient_key`; `snapshot.plan_id` |
| **FOREIGN KEY** | all relationships in §2, each with an explicit `ON DELETE` policy |
| **CHECK — non-negative** | all seven environmental rate columns `>= 0` (FR-SPEC-08) |
| **CHECK — strictly positive** | `canopy_radius_m > 0`, `spacing_m > 0`, `life_span_years > 0`, `land_area_sqm > 0`, `quantity > 0` |
| **CHECK — enumerated** | `role`, `growth_rate`, `water_requirement`, `status`, `purpose`, `land_area_unit`, `feasibility` restricted to their value sets |
| **CHECK — range** | `projection_years BETWEEN 1 AND 50`; `impact_score BETWEEN 0 AND 100`; `canopy_coverage_percent >= 0` |
| **NOT NULL** | every audit column, every required business field |
| **DEFAULT** | `active = TRUE`, `enabled = TRUE`, `deleted = FALSE`, `quantity = 1`, timestamps |

Constraints live in the database, not only in Bean Validation — application code is one
client of the data, and the database is the last line of defence (NFR-12).

---

## 6. Column Type Decisions

| Concern | Type | Why |
|---|---|---|
| Per-tree environmental rates | `DECIMAL(10,3)` | Exact; 3 dp matches literature precision (e.g. `21.772` kg CO₂) |
| Aggregate results | `DECIMAL(16,3)` / `DECIMAL(18,3)` | A 100 000-tree plan over 50 years stays well inside range |
| Areas | `DECIMAL(14,2)` m² | 10¹² m² ceiling — far beyond any city |
| Percentages | `DECIMAL(7,2)` | Allows the uncapped >100 % utilisation figure needed for `OVERCROWDED` |
| Diversity index | `DECIMAL(6,4)` | Shannon H′ needs 4 dp to be meaningful |
| Coefficients | `DECIMAL(18,6)` | Accommodates both `0.121` and `4046.856422` |
| Timestamps | `DATETIME(6)` | Microsecond precision; UTC by application convention |
| Coefficient snapshot | `JSON` | Native MySQL 8 JSON — queryable, validated, no parsing on write |
| Booleans | `BOOLEAN` (`TINYINT(1)`) | MySQL idiom |
| Enumerated values | `VARCHAR` + `CHECK` | Portable and migratable, unlike MySQL `ENUM` which requires DDL to extend |

**No `FLOAT`/`DOUBLE` anywhere.** A report that says `12.999999999` instead of `13.00`
destroys credibility, and this application's entire output is numbers.

---

## 7. Seeded Reference Data (`V2`)

| Dataset | Rows | Notes |
|---|---|---|
| Users | 2 | one `ROLE_ADMIN`, one `ROLE_USER` demo planner; pre-computed BCrypt hashes (no encoder needed before Phase 12) |
| Tree species | 25 | Indian urban species, each with environmental rates **and a `source_citation`** (BR-19) |
| Environmental coefficients | 26 | five categories — see below |

### Coefficient registry (the "never hardcode" enforcement)

| Category | Keys | Purpose |
|---|---|---|
| `ADJUSTMENT` | `calc.survival.rate`, `calc.maturity.years.slow/moderate/fast`, `calc.site.condition.factor` | Convert planted count → effective mature-equivalent trees (FR-CALC-06) |
| `FEASIBILITY` | `feasibility.threshold.under`, `.optimal.max`, `.tight.max` | The four-way verdict boundaries (FR-CALC-05) |
| `EQUIVALENCE` | `equiv.car.co2.kg.per.km`, `equiv.household.electricity.co2.kg.per.year`, `equiv.person.oxygen.kg.per.year`, `equiv.petrol.co2.kg.per.litre` | Relatable comparisons (FR-CALC-09) |
| `SCORING` | `score.weight.carbon/air/canopy/diversity` (Σ = 1.0), `score.benchmark.*`, `score.target.canopy.coverage.percent`, `score.max.diversity.index` | Impact score (FR-CALC-10, BR-20) |
| `LIMIT` | `limit.max.quantity.per.item`, `limit.min/max.projection.years`, `limit.monoculture.share.percent` | Bounds enforced by validation (BR-09, BR-14) |
| `UNIT` | `unit.hectare.to.sqm`, `unit.acre.to.sqm` | Area normalisation (BR-07) |

Adding a new equivalence — *"this offsets N domestic flights"* — is **one INSERT and zero
lines of Java**. That is what makes BR-15 architecturally real rather than aspirational.

---

## 8. Data Lifecycle

| Entity | Create | Update | Delete |
|---|---|---|---|
| `users` | self-registration / admin | profile, role, enabled | never — disabled instead |
| `tree_species` | admin | admin | soft (`active=false`) if referenced; hard only if unused (BR-18) |
| `plantation_plans` | owner | owner, while `DRAFT`/`COMPLETED` | soft (`deleted=true`) (BR-21) |
| `plan_items` | owner | owner | hard — cascade with plan |
| `plan_result_snapshots` | on `COMPLETED` | replaced on recompute | cascade |
| `environmental_coefficients` | seed only | admin, range-validated | never — restore-to-default instead |
| `report_logs` / `audit_events` | system | never | never (append-only) |

---

## 9. Migration Plan

| Version | File | Contents |
|---|---|---|
| `V1` | `V1__baseline_schema.sql` | 8 tables, all keys, constraints, indexes |
| `V2` | `V2__seed_reference_data.sql` | 2 users, 25 species, 26 coefficients |
| `V3+` | future | additive only — new column, new index, new coefficient row |

Runtime uses `spring.jpa.hibernate.ddl-auto=validate`: if an entity and the migrated schema
ever disagree, **the application refuses to start** rather than silently corrupting data.

---

## 10. Phase 3 Review

**Improvements made while designing**

1. **`(plan_id, species_id)` unique constraint** — BR-08 was a service-layer rule; pushing
   it into the schema makes it hold under concurrent requests too.
2. **`land_area_input` + `land_area_unit` retained** — an earlier draft stored only m²,
   which would have redisplayed "1 acre" as "4046.86 m²" and looked broken to the user.
3. **`coefficient_snapshot JSON`** — without it, snapshots record *what* was computed but
   not *why*, and reproducibility is only half-solved.
4. **`report_logs.plan_id` nullable + `plan_ids` CSV** — comparison reports span several
   plans; forcing a single FK would have made them unloggable.
5. **`DECIMAL` everywhere, `FLOAT` nowhere** — an explicit, documented rule.
6. **`ON DELETE` policy chosen per relationship** rather than accepting defaults — `CASCADE`
   for compositions, `RESTRICT` for reference data, `SET NULL` for audit trails.

**Risks & mitigations**

| Risk | Mitigation |
|---|---|
| Plan title uniqueness vs. soft delete | Enforced in the service among non-deleted plans; a DB unique key would collide with soft-deleted rows |
| MySQL ignoring `CHECK` before 8.0.16 | Target is MySQL 8.0.16+; `CHECK` is enforced. Bean Validation duplicates the rules regardless |
| JSON snapshot growing large | ~26 keys, < 2 KB per row — negligible |

---

**End of Phase 3.** Next: Phase 4 — UI wireframes & design system.
