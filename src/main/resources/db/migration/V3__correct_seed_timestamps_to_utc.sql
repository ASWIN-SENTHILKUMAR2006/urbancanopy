-- =====================================================================================
--  V3 — Correct the seeded audit timestamps to UTC
--
--  WHY THIS EXISTS
--  V2 stamps every seeded row with NOW(6), which returns the MySQL session's *local*
--  time. The application, however, reads and writes every timestamp as UTC
--  (spring.jpa.properties.hibernate.jdbc.time_zone = UTC). On any server that is not
--  itself set to UTC the two disagree, and the seeded rows come back offset by the
--  server's timezone.
--
--  The symptom is visible rather than subtle: on a machine at UTC+05:30 the coefficient
--  administration screen reported "Last changed in 5 hours" — a change five and a half
--  hours in the future. Nothing is computed from these columns, so no estimate was ever
--  wrong; but a reference table that claims to have been edited tomorrow undermines
--  confidence in every other figure on the page.
--
--  WHY A NEW MIGRATION RATHER THAN A FIX TO V2
--  V2 has already been applied wherever this schema exists. Editing it would change its
--  checksum and make Flyway refuse to start against every existing database
--  (validate-on-migrate = true) — trading a cosmetic defect for an outage. Applied
--  migrations are immutable; corrections are additive.
--
--  CORRECTNESS
--  The offset is derived at migration time from the server itself rather than hardcoded,
--  so this is exact on any timezone and a no-op on a server already running UTC. On a
--  fresh install V3 runs moments after V2, under the same session timezone, so the
--  correction is exact there too.
--
--  Only rows still attributed to 'system' are touched. A coefficient an administrator has
--  already revised carries their address in updated_by and a genuine UTC timestamp
--  written by JPA; shifting that would corrupt a correct value.
-- =====================================================================================

SET @utc_offset_seconds := TIMESTAMPDIFF(SECOND, UTC_TIMESTAMP(), NOW());

-- ------------------------------------------------------------------------- users
UPDATE users
SET created_at = created_at - INTERVAL @utc_offset_seconds SECOND
WHERE created_by = 'system';

UPDATE users
SET updated_at = updated_at - INTERVAL @utc_offset_seconds SECOND
WHERE updated_by = 'system';

-- ------------------------------------------------------------------ tree_species
UPDATE tree_species
SET created_at = created_at - INTERVAL @utc_offset_seconds SECOND
WHERE created_by = 'system';

UPDATE tree_species
SET updated_at = updated_at - INTERVAL @utc_offset_seconds SECOND
WHERE updated_by = 'system';

-- ------------------------------------------------- environmental_coefficients
UPDATE environmental_coefficients
SET created_at = created_at - INTERVAL @utc_offset_seconds SECOND
WHERE created_by = 'system';

UPDATE environmental_coefficients
SET updated_at = updated_at - INTERVAL @utc_offset_seconds SECOND
WHERE updated_by = 'system';
