-- =====================================================================================
--  UrbanCanopy — baseline schema
--  Urban Tree Plantation Plan to Estimate Annual Carbon Sequestration and Improve Air Quality
--
--  Target: MySQL 8.0.16+ (CHECK constraints are enforced from 8.0.16 onward)
--  Engine: InnoDB   Charset: utf8mb4   Collation: utf8mb4_0900_ai_ci
--
--  Design notes
--  ------------
--  * Surrogate BIGINT keys throughout: stable, compact, and join-friendly.
--  * DECIMAL everywhere, FLOAT/DOUBLE nowhere. Every figure this application reports is
--    the product of a chain of multiplications; binary floating point drifts through such
--    a chain and a report reading 12.999999999998 destroys its own credibility.
--  * Constraints live here, not only in Bean Validation. Application code is one client
--    of this data; the database is the last line of defence.
--  * ON DELETE policy is chosen per relationship rather than defaulted:
--      CASCADE  for compositions (a plan's items)
--      RESTRICT for reference data (a species a plan depends on)
--      SET NULL for audit trails (history outlives its subject)
-- =====================================================================================


-- =====================================================================================
--  users
-- =====================================================================================
CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    full_name             VARCHAR(120) NOT NULL,
    email                 VARCHAR(160) NOT NULL,
    password_hash         VARCHAR(100) NOT NULL,
    organisation          VARCHAR(150)     NULL,
    designation           VARCHAR(100)     NULL,
    role                  VARCHAR(20)  NOT NULL DEFAULT 'ROLE_USER',
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    lock_expires_at       DATETIME(6)      NULL,
    last_login_at         DATETIME(6)      NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(160)     NULL,
    updated_by            VARCHAR(160)     NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_role  CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN')),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_login_attempts >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_users_role_enabled ON users (role, enabled);


-- =====================================================================================
--  tree_species — the scientific reference catalogue
--
--  Carbon and oxygen rates are kilograms per tree per year; pollutant removal rates are
--  grams per tree per year, because published particulate and gas figures are an order of
--  magnitude smaller and grams keep them legible. Every column name states its unit.
-- =====================================================================================
CREATE TABLE tree_species (
    id                        BIGINT        NOT NULL AUTO_INCREMENT,

    -- identity
    common_name               VARCHAR(120)  NOT NULL,
    scientific_name           VARCHAR(160)  NOT NULL,
    family                    VARCHAR(100)      NULL,
    local_name                VARCHAR(120)      NULL,
    description               TEXT              NULL,
    image_path                VARCHAR(255)      NULL,
    image_url                 VARCHAR(500)      NULL,

    -- environmental performance, per tree per year
    co2_sequestration_kg_year DECIMAL(10,3) NOT NULL,
    oxygen_production_kg_year DECIMAL(10,3) NOT NULL,
    pm25_removal_g_year       DECIMAL(10,3) NOT NULL,
    pm10_removal_g_year       DECIMAL(10,3) NOT NULL,
    no2_removal_g_year        DECIMAL(10,3) NOT NULL,
    so2_removal_g_year        DECIMAL(10,3) NOT NULL,
    co_removal_g_year         DECIMAL(10,3) NOT NULL,

    -- horticultural profile
    growth_rate               VARCHAR(20)   NOT NULL,
    water_requirement         VARCHAR(20)   NOT NULL,
    climate_zone              VARCHAR(30)   NOT NULL,
    soil_type                 VARCHAR(30)   NOT NULL,
    canopy_radius_m           DECIMAL(6,2)  NOT NULL,
    mature_height_m           DECIMAL(6,2)      NULL,
    life_span_years           INT           NOT NULL,
    spacing_m                 DECIMAL(6,2)  NOT NULL,
    native_species            BOOLEAN       NOT NULL DEFAULT TRUE,

    -- provenance and lifecycle
    source_citation           VARCHAR(500)  NOT NULL,
    active                    BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,
    created_by                VARCHAR(160)      NULL,
    updated_by                VARCHAR(160)      NULL,

    CONSTRAINT pk_tree_species PRIMARY KEY (id),
    CONSTRAINT uq_species_common_name     UNIQUE (common_name),
    CONSTRAINT uq_species_scientific_name UNIQUE (scientific_name),

    -- environmental rates may be zero (a species may remove no measurable SO2)
    -- but never negative: a tree does not emit these
    CONSTRAINT ck_species_co2   CHECK (co2_sequestration_kg_year >= 0),
    CONSTRAINT ck_species_o2    CHECK (oxygen_production_kg_year >= 0),
    CONSTRAINT ck_species_pm25  CHECK (pm25_removal_g_year >= 0),
    CONSTRAINT ck_species_pm10  CHECK (pm10_removal_g_year >= 0),
    CONSTRAINT ck_species_no2   CHECK (no2_removal_g_year >= 0),
    CONSTRAINT ck_species_so2   CHECK (so2_removal_g_year >= 0),
    CONSTRAINT ck_species_co    CHECK (co_removal_g_year >= 0),

    -- physical dimensions must be strictly positive: they appear as divisors and as
    -- the radius in pi*r^2, where zero would silently erase a species' canopy
    CONSTRAINT ck_species_canopy_radius CHECK (canopy_radius_m > 0),
    CONSTRAINT ck_species_spacing       CHECK (spacing_m > 0),
    CONSTRAINT ck_species_life_span     CHECK (life_span_years > 0),
    CONSTRAINT ck_species_height        CHECK (mature_height_m IS NULL OR mature_height_m > 0),

    CONSTRAINT ck_species_growth_rate CHECK (growth_rate IN ('SLOW', 'MODERATE', 'FAST')),
    CONSTRAINT ck_species_water       CHECK (water_requirement IN ('LOW', 'MODERATE', 'HIGH')),
    CONSTRAINT ck_species_climate     CHECK (climate_zone IN
        ('TROPICAL', 'SUBTROPICAL', 'ARID', 'SEMI_ARID', 'TEMPERATE', 'COASTAL', 'ALL_ROUND')),
    CONSTRAINT ck_species_soil        CHECK (soil_type IN
        ('LOAMY', 'SANDY', 'CLAY', 'ALLUVIAL', 'RED_LATERITE', 'BLACK_COTTON', 'ROCKY',
         'WELL_DRAINED_ANY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_species_active          ON tree_species (active);
CREATE INDEX idx_species_growth_climate  ON tree_species (growth_rate, climate_zone);
CREATE INDEX idx_species_co2             ON tree_species (co2_sequestration_kg_year);


-- =====================================================================================
--  environmental_coefficients — the tunable scientific constants
--
--  This table is what makes "never hardcode values" an enforceable property rather than a
--  coding aspiration. Every constant the calculation engine applies is a row here, with
--  its own permitted range, its literature source, and the seeded default it can always be
--  restored to.
-- =====================================================================================
CREATE TABLE environmental_coefficients (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    coefficient_key   VARCHAR(80)    NOT NULL,
    label             VARCHAR(150)   NOT NULL,
    coefficient_value DECIMAL(18,6)  NOT NULL,
    default_value     DECIMAL(18,6)  NOT NULL,
    min_value         DECIMAL(18,6)      NULL,
    max_value         DECIMAL(18,6)      NULL,
    unit              VARCHAR(40)        NULL,
    category          VARCHAR(40)    NOT NULL,
    description       VARCHAR(500)       NULL,
    source            VARCHAR(500)       NULL,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(160)       NULL,
    updated_by        VARCHAR(160)       NULL,

    CONSTRAINT pk_environmental_coefficients PRIMARY KEY (id),
    CONSTRAINT uq_coefficient_key UNIQUE (coefficient_key),
    CONSTRAINT ck_coefficient_category CHECK (category IN
        ('ADJUSTMENT', 'FEASIBILITY', 'EQUIVALENCE', 'SCORING', 'LIMIT')),
    CONSTRAINT ck_coefficient_range CHECK (
        min_value IS NULL OR max_value IS NULL OR min_value <= max_value)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_coefficient_category ON environmental_coefficients (category);


-- =====================================================================================
--  plantation_plans
--
--  Land area is stored twice on purpose. land_area_sqm is the normalised figure every
--  query and calculation uses; land_area_input + land_area_unit preserve what the planner
--  actually typed, so a plan entered as "1.2 hectares" does not redisplay as "12000 m2"
--  and look broken.
--
--  Title uniqueness per user is enforced in the service layer rather than by a unique key,
--  because a unique key would also collide with soft-deleted rows.
-- =====================================================================================
CREATE TABLE plantation_plans (
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    user_id           BIGINT         NOT NULL,
    title             VARCHAR(150)   NOT NULL,
    description       TEXT               NULL,
    site_name         VARCHAR(180)       NULL,
    land_area_sqm     DECIMAL(14,2)  NOT NULL,
    land_area_input   DECIMAL(14,4)  NOT NULL,
    land_area_unit    VARCHAR(20)    NOT NULL DEFAULT 'SQUARE_METRE',
    purpose           VARCHAR(40)    NOT NULL,
    target_start_year INT            NOT NULL,
    projection_years  INT            NOT NULL,
    status            VARCHAR(20)    NOT NULL DEFAULT 'DRAFT',
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(160)       NULL,
    updated_by        VARCHAR(160)       NULL,

    CONSTRAINT pk_plantation_plans PRIMARY KEY (id),
    CONSTRAINT fk_plans_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT ck_plans_land_area   CHECK (land_area_sqm > 0),
    CONSTRAINT ck_plans_land_input  CHECK (land_area_input > 0),
    CONSTRAINT ck_plans_start_year  CHECK (target_start_year BETWEEN 1900 AND 2200),
    CONSTRAINT ck_plans_projection  CHECK (projection_years BETWEEN 1 AND 50),
    CONSTRAINT ck_plans_status      CHECK (status IN ('DRAFT', 'COMPLETED', 'ARCHIVED')),
    CONSTRAINT ck_plans_unit        CHECK (land_area_unit IN ('SQUARE_METRE', 'HECTARE', 'ACRE')),
    CONSTRAINT ck_plans_purpose     CHECK (purpose IN
        ('AIR_QUALITY_IMPROVEMENT', 'CARBON_OFFSET', 'URBAN_COOLING', 'NOISE_BARRIER',
         'AESTHETIC_LANDSCAPING', 'BIODIVERSITY_RESTORATION', 'AVENUE_ROADSIDE',
         'CAMPUS_GREENING', 'MIYAWAKI_DENSE_FOREST'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_plans_user_deleted ON plantation_plans (user_id, deleted);
CREATE INDEX idx_plans_status       ON plantation_plans (status);
CREATE INDEX idx_plans_purpose      ON plantation_plans (purpose);


-- =====================================================================================
--  plan_items — the associative entity resolving plans to species
--
--  uq_plan_item_plan_species is the database-level enforcement of BR-08: a species appears
--  at most once per plan. The service increments an existing item rather than inserting a
--  duplicate; the constraint guarantees the rule holds even when two requests race.
--
--  species_id is RESTRICT, not CASCADE: deleting a species must not silently rewrite the
--  history of every plan that used it. Species are deactivated instead.
-- =====================================================================================
CREATE TABLE plan_items (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    plan_id    BIGINT      NOT NULL,
    species_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_by VARCHAR(160)    NULL,
    updated_by VARCHAR(160)    NULL,

    CONSTRAINT pk_plan_items PRIMARY KEY (id),
    CONSTRAINT uq_plan_item_plan_species UNIQUE (plan_id, species_id),
    CONSTRAINT fk_plan_items_plan FOREIGN KEY (plan_id) REFERENCES plantation_plans (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_plan_items_species FOREIGN KEY (species_id) REFERENCES tree_species (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT ck_plan_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_plan_items_species ON plan_items (species_id);


-- =====================================================================================
--  plan_result_snapshots — frozen results
--
--  Every column here is derivable from plan_items and the coefficient registry. The table
--  exists precisely so that it does NOT have to be re-derived: without it, an
--  administrator revising calc.survival.rate would silently rewrite every report the
--  system has ever produced.
--
--  coefficient_snapshot records the exact constants in force at computation time, so a
--  historical result captures not only what was computed but why.
-- =====================================================================================
CREATE TABLE plan_result_snapshots (
    id                        BIGINT         NOT NULL AUTO_INCREMENT,
    plan_id                   BIGINT         NOT NULL,

    total_trees               INT            NOT NULL,
    effective_trees           DECIMAL(14,2)  NOT NULL,

    annual_co2_kg             DECIMAL(16,3)  NOT NULL,
    annual_o2_kg              DECIMAL(16,3)  NOT NULL,
    annual_pm25_g             DECIMAL(16,3)  NOT NULL,
    annual_pm10_g             DECIMAL(16,3)  NOT NULL,
    annual_no2_g              DECIMAL(16,3)  NOT NULL,
    annual_so2_g              DECIMAL(16,3)  NOT NULL,
    annual_co_g               DECIMAL(16,3)  NOT NULL,
    total_pollutant_g         DECIMAL(16,3)  NOT NULL,

    canopy_area_sqm           DECIMAL(16,2)  NOT NULL,
    canopy_coverage_percent   DECIMAL(7,2)       NULL,
    required_area_sqm         DECIMAL(16,2)  NOT NULL,
    space_utilisation_percent DECIMAL(9,2)       NULL,
    feasibility               VARCHAR(20)    NOT NULL,

    diversity_index           DECIMAL(6,4)       NULL,
    impact_score              DECIMAL(5,2)       NULL,
    horizon_co2_kg            DECIMAL(18,3)  NOT NULL,

    coefficient_snapshot      JSON               NULL,
    engine_version            VARCHAR(20)    NOT NULL,
    computed_at               DATETIME(6)    NOT NULL,

    CONSTRAINT pk_plan_result_snapshots PRIMARY KEY (id),
    CONSTRAINT uq_snapshot_plan UNIQUE (plan_id),
    CONSTRAINT fk_snapshot_plan FOREIGN KEY (plan_id) REFERENCES plantation_plans (id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT ck_snapshot_trees      CHECK (total_trees >= 0),
    CONSTRAINT ck_snapshot_effective  CHECK (effective_trees >= 0),
    CONSTRAINT ck_snapshot_co2        CHECK (annual_co2_kg >= 0),
    CONSTRAINT ck_snapshot_o2         CHECK (annual_o2_kg >= 0),
    CONSTRAINT ck_snapshot_canopy     CHECK (canopy_area_sqm >= 0),
    CONSTRAINT ck_snapshot_coverage   CHECK (canopy_coverage_percent IS NULL
                                             OR canopy_coverage_percent >= 0),
    CONSTRAINT ck_snapshot_score      CHECK (impact_score IS NULL
                                             OR (impact_score >= 0 AND impact_score <= 100)),
    CONSTRAINT ck_snapshot_diversity  CHECK (diversity_index IS NULL OR diversity_index >= 0),
    CONSTRAINT ck_snapshot_feasibility CHECK (feasibility IN
        ('UNDER_UTILISED', 'OPTIMAL', 'TIGHT', 'OVERCROWDED', 'NOT_APPLICABLE'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- =====================================================================================
--  report_logs — append-only record of generated documents
--
--  Both foreign keys are nullable with ON DELETE SET NULL so the log outlives the plan and
--  the account it refers to. An audit trail that disappears when its subject does is not
--  an audit trail.
--
--  plan_id is null for comparison reports, which span several plans; plan_ids carries the
--  comma-separated identifiers instead.
-- =====================================================================================
CREATE TABLE report_logs (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT           NULL,
    plan_id         BIGINT           NULL,
    report_type     VARCHAR(30)  NOT NULL,
    plan_ids        VARCHAR(255)     NULL,
    file_name       VARCHAR(255) NOT NULL,
    file_size_bytes BIGINT           NULL,
    generated_at    DATETIME(6)  NOT NULL,

    CONSTRAINT pk_report_logs PRIMARY KEY (id),
    CONSTRAINT fk_report_logs_user FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_report_logs_plan FOREIGN KEY (plan_id) REFERENCES plantation_plans (id)
        ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT ck_report_logs_type CHECK (report_type IN ('PLAN_IMPACT', 'PLAN_COMPARISON')),
    CONSTRAINT ck_report_logs_size CHECK (file_size_bytes IS NULL OR file_size_bytes >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_report_logs_user_date ON report_logs (user_id, generated_at);
CREATE INDEX idx_report_logs_plan      ON report_logs (plan_id);


-- =====================================================================================
--  audit_events — append-only security and reference-data trail
--
--  detail holds a short human-readable summary such as "survival rate 0.85 -> 0.80".
--  It must never contain a password, a token, or anything else that would turn the audit
--  log into a liability.
-- =====================================================================================
CREATE TABLE audit_events (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    actor_id    BIGINT            NULL,
    event_type  VARCHAR(50)   NOT NULL,
    entity_type VARCHAR(50)       NULL,
    entity_id   BIGINT            NULL,
    detail      VARCHAR(1000)     NULL,
    ip_address  VARCHAR(45)       NULL,
    occurred_at DATETIME(6)   NOT NULL,

    CONSTRAINT pk_audit_events PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_audit_type_date ON audit_events (event_type, occurred_at);
CREATE INDEX idx_audit_actor     ON audit_events (actor_id);
