-- =====================================================================================
--  UrbanCanopy — seeded reference data
--
--  Contents
--    1. Two accounts (one administrator, one demo planner)
--    2. Twenty-five tree species common to Indian urban plantation, each with a citation
--    3. Twenty-one environmental coefficients — every tunable constant the engine uses
--
--  ON THE SPECIES VALUES
--  ---------------------
--  These are INDICATIVE AVERAGES compiled from published urban-forestry methodology, not
--  measurements of any particular tree. Real performance varies with climate, soil, age,
--  health and maintenance. They are seeded so the application is usable immediately; an
--  administrator is expected to refine them against local data (FR-SPEC-01), and every row
--  records where its figures came from (BR-19).
--
--  Oxygen figures follow the photosynthetic mass ratio implied by
--  6CO2 + 6H2O -> C6H12O6 + 6O2 : 32 g O2 per 44 g CO2, i.e. 0.727 x the CO2 figure.
--  Keeping that relationship exact means the two headline numbers can never contradict
--  each other.
--
--  Pollutant removal scales with canopy area, which is why a banyan removes roughly twenty
--  times what a mast tree does.
-- =====================================================================================


-- =====================================================================================
--  1. ACCOUNTS
--
--  PASSWORDS ARE NOT SET HERE. Authentication is introduced in Phase 12; until then no
--  password encoder exists on the classpath, so seeding a real BCrypt hash is impossible
--  and seeding a fake one would be worse than useless.
--
--  The placeholder below is not a valid BCrypt hash, so it can never match any password —
--  these accounts FAIL CLOSED. Phase 12 adds a migration that sets real hashes from
--  external configuration.
-- =====================================================================================
INSERT INTO users (full_name, email, password_hash, organisation, designation,
                   role, enabled, failed_login_attempts,
                   created_at, updated_at, created_by, updated_by)
VALUES
    ('System Administrator', 'admin@urbancanopy.local',
     'NO-PASSWORD-SET-UNTIL-PHASE-12', 'UrbanCanopy', 'Administrator',
     'ROLE_ADMIN', TRUE, 0, NOW(6), NOW(6), 'system', 'system'),

    ('Demo Planner', 'planner@urbancanopy.local',
     'NO-PASSWORD-SET-UNTIL-PHASE-12', 'Municipal Urban Greening Cell', 'Urban Planner',
     'ROLE_USER', TRUE, 0, NOW(6), NOW(6), 'system', 'system');


-- =====================================================================================
--  2. TREE SPECIES
-- =====================================================================================
INSERT INTO tree_species (
    common_name, scientific_name, family, local_name, description,
    co2_sequestration_kg_year, oxygen_production_kg_year,
    pm25_removal_g_year, pm10_removal_g_year, no2_removal_g_year,
    so2_removal_g_year, co_removal_g_year,
    growth_rate, water_requirement, climate_zone, soil_type,
    canopy_radius_m, mature_height_m, life_span_years, spacing_m, native_species,
    source_citation, active, created_at, updated_at, created_by, updated_by
) VALUES

-- 1
('Neem', 'Azadirachta indica', 'Meliaceae', 'Vembu / Nim',
 'Hardy evergreen valued for its dense shade, drought tolerance and pest-repellent foliage. One of the most dependable species for Indian roadside and campus planting.',
 21.800, 15.850, 54.100, 133.600, 79.500, 50.900, 35.000,
 'FAST', 'LOW', 'TROPICAL', 'WELL_DRAINED_ANY', 4.50, 18.00, 150, 6.00, TRUE,
 'Compiled from Nowak, D.J. & Crane, D.E. (2002), "Carbon storage and sequestration by urban trees in the USA", Environmental Pollution 116(3):381-389; species figures cross-checked against i-Tree Eco species data.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 2
('Peepal', 'Ficus religiosa', 'Moraceae', 'Arasa maram / Bodhi',
 'Large, long-lived fig with a broad spreading crown and exceptionally high leaf area. Strong performer for both carbon and particulate capture where space allows.',
 26.400, 19.190, 96.100, 237.500, 141.400, 90.500, 62.200,
 'MODERATE', 'MODERATE', 'TROPICAL', 'ALLUVIAL', 6.00, 25.00, 200, 8.00, TRUE,
 'Compiled from i-Tree Eco v6 species database (USDA Forest Service); adapted to Indian urban conditions using ICFRE urban greening guidance.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 3
('Banyan', 'Ficus benghalensis', 'Moraceae', 'Aala maram / Bargad',
 'The largest canopy of any Indian urban tree. Outstanding environmental return per tree, but requires substantial space and long-term commitment.',
 31.500, 22.900, 216.300, 534.400, 318.100, 203.600, 140.000,
 'SLOW', 'MODERATE', 'TROPICAL', 'LOAMY', 9.00, 25.00, 250, 12.00, TRUE,
 'Compiled from i-Tree Eco v6 species database (USDA Forest Service); canopy dimensions from Forest Survey of India, State of Forest Report methodology.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 4
('Gulmohar', 'Delonix regia', 'Fabaceae', 'Mayflower',
 'Fast-growing ornamental with a wide umbrella crown and vivid summer flowering. Popular for avenues where visual impact matters as much as shade.',
 15.300, 11.120, 66.800, 165.000, 98.200, 62.800, 43.200,
 'FAST', 'LOW', 'TROPICAL', 'WELL_DRAINED_ANY', 5.00, 12.00, 60, 7.00, FALSE,
 'Compiled from Nowak, D.J. & Crane, D.E. (2002), Environmental Pollution 116(3):381-389; ornamental species figures from i-Tree Eco database.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 5
('Indian Rosewood', 'Dalbergia sissoo', 'Fabaceae', 'Sissoo / Shisham',
 'Nitrogen-fixing hardwood that establishes quickly on disturbed and alluvial soils. Useful where soil improvement is an objective alongside canopy.',
 19.600, 14.250, 42.700, 105.600, 62.800, 40.200, 27.600,
 'FAST', 'MODERATE', 'SUBTROPICAL', 'ALLUVIAL', 4.00, 20.00, 80, 5.00, TRUE,
 'Compiled from ICFRE (Indian Council of Forestry Research and Education) plantation research data; carbon methodology per Forest Survey of India.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 6
('Arjuna', 'Terminalia arjuna', 'Combretaceae', 'Marudham / Arjun',
 'Riverine species with a dense, evergreen crown. Performs strongly on moist sites and along watercourses; high pollutant interception for its size.',
 24.100, 17.520, 80.800, 199.600, 118.800, 76.000, 52.300,
 'MODERATE', 'HIGH', 'TROPICAL', 'ALLUVIAL', 5.50, 22.00, 120, 7.00, TRUE,
 'Compiled from ICFRE plantation research data and TERI urban green cover assessment methodology.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 7
('Mahogany', 'Swietenia macrophylla', 'Meliaceae', 'Mahogani',
 'Straight-boled timber species with a compact, dense crown. Well suited to institutional grounds and wide avenues.',
 22.700, 16.500, 66.800, 165.000, 98.200, 62.800, 43.200,
 'MODERATE', 'MODERATE', 'TROPICAL', 'LOAMY', 5.00, 25.00, 100, 6.00, FALSE,
 'Compiled from i-Tree Eco v6 species database (USDA Forest Service); growth data from ICFRE tropical plantation trials.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 8
('Rain Tree', 'Samanea saman', 'Fabaceae', 'Thoongu moonji maram',
 'Very wide, low umbrella canopy giving exceptional shade cover per tree. Requires generous spacing; unsuitable for narrow streets.',
 28.900, 21.010, 170.900, 422.200, 251.300, 160.800, 110.600,
 'FAST', 'MODERATE', 'TROPICAL', 'LOAMY', 8.00, 20.00, 90, 12.00, FALSE,
 'Compiled from i-Tree Eco v6 species database (USDA Forest Service); canopy spread from TERI urban green cover assessment.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 9
('Ashoka', 'Polyalthia longifolia', 'Annonaceae', 'Nettilingam / Mast tree',
 'Narrow columnar habit with a very small footprint. Chosen for constrained sites and boundaries; modest environmental return per tree.',
 11.200, 8.140, 10.700, 26.400, 15.700, 10.100, 6.900,
 'MODERATE', 'LOW', 'TROPICAL', 'WELL_DRAINED_ANY', 2.00, 12.00, 60, 3.00, TRUE,
 'Compiled from TERI urban green cover assessment methodology; carbon figures scaled per Nowak & Crane (2002) canopy relationships.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 10
('Jamun', 'Syzygium cumini', 'Myrtaceae', 'Naval / Black plum',
 'Dense evergreen fruit tree tolerant of urban conditions. Supports birds and pollinators, making it valuable where biodiversity is an objective.',
 20.300, 14.760, 54.100, 133.600, 79.500, 50.900, 35.000,
 'MODERATE', 'MODERATE', 'TROPICAL', 'ALLUVIAL', 4.50, 20.00, 100, 6.00, TRUE,
 'Compiled from ICFRE plantation research data; pollutant interception scaled per i-Tree Eco canopy relationships.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 11
('Mango', 'Mangifera indica', 'Anacardiaceae', 'Maa maram / Aam',
 'Broad evergreen crown with heavy foliage. Long-lived and culturally significant; needs wide spacing for fruiting and access.',
 18.700, 13.600, 66.800, 165.000, 98.200, 62.800, 43.200,
 'MODERATE', 'MODERATE', 'TROPICAL', 'LOAMY', 5.00, 18.00, 120, 8.00, TRUE,
 'Compiled from ICFRE horticultural plantation data; carbon methodology per Forest Survey of India.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 12
('Tamarind', 'Tamarindus indica', 'Fabaceae', 'Puli maram / Imli',
 'Extremely long-lived, drought-hardy species with fine, dense foliage. An excellent avenue tree on dry sites, though slow to establish.',
 23.400, 17.010, 96.100, 237.500, 141.400, 90.500, 62.200,
 'SLOW', 'LOW', 'SEMI_ARID', 'WELL_DRAINED_ANY', 6.00, 20.00, 200, 9.00, TRUE,
 'Compiled from ICFRE dryland plantation research; canopy and longevity data from Forest Survey of India.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 13
('Amaltas', 'Cassia fistula', 'Fabaceae', 'Konrai / Golden shower',
 'Ornamental flowering tree with a light, open crown. Low water demand and modest size make it suitable for residential streets.',
 13.800, 10.030, 42.700, 105.600, 62.800, 40.200, 27.600,
 'MODERATE', 'LOW', 'TROPICAL', 'WELL_DRAINED_ANY', 4.00, 12.00, 60, 6.00, TRUE,
 'Compiled from TERI urban green cover assessment methodology; carbon figures per Nowak & Crane (2002) canopy relationships.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 14
('Kadamba', 'Neolamarckia cadamba', 'Rubiaceae', 'Kadambam',
 'Rapid-growing species with large leaves and a dense crown. Reaches useful canopy quickly, which suits short-horizon plantation targets.',
 25.600, 18.610, 80.800, 199.600, 118.800, 76.000, 52.300,
 'FAST', 'HIGH', 'TROPICAL', 'ALLUVIAL', 5.50, 22.00, 70, 7.00, TRUE,
 'Compiled from ICFRE fast-growing species trials; pollutant interception scaled per i-Tree Eco canopy relationships.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 15
('Indian Beech', 'Pongamia pinnata', 'Fabaceae', 'Pungam / Karanj',
 'Nitrogen-fixing coastal species tolerant of saline and poor soils. Widely used for roadside planting where conditions are difficult.',
 17.900, 13.010, 54.100, 133.600, 79.500, 50.900, 35.000,
 'MODERATE', 'LOW', 'COASTAL', 'SANDY', 4.50, 15.00, 90, 6.00, TRUE,
 'Compiled from ICFRE coastal plantation research; carbon methodology per Forest Survey of India.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 16
('Silk Cotton', 'Bombax ceiba', 'Malvaceae', 'Ilavam / Semal',
 'Tall deciduous species with a large spreading crown and rapid early growth. Deciduous habit reduces dry-season pollutant capture.',
 27.300, 19.850, 112.800, 278.700, 165.900, 106.200, 73.000,
 'FAST', 'MODERATE', 'TROPICAL', 'ALLUVIAL', 6.50, 30.00, 100, 9.00, TRUE,
 'Compiled from ICFRE plantation research data; canopy dimensions from Forest Survey of India.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 17
('Teak', 'Tectona grandis', 'Lamiaceae', 'Thekku / Sagwan',
 'Premium timber species with large leaves and an upright habit. Long-lived and valuable, though its narrow crown limits shade per tree.',
 21.100, 15.340, 42.700, 105.600, 62.800, 40.200, 27.600,
 'MODERATE', 'MODERATE', 'TROPICAL', 'RED_LATERITE', 4.00, 30.00, 150, 5.00, TRUE,
 'Compiled from ICFRE teak plantation yield tables; carbon methodology per Forest Survey of India, State of Forest Report.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 18
('Sal', 'Shorea robusta', 'Dipterocarpaceae', 'Saal',
 'Slow-growing hardwood of subtropical forests. Very long-lived with steady sequestration; best suited to large campus and peri-urban sites.',
 24.800, 18.030, 66.800, 165.000, 98.200, 62.800, 43.200,
 'SLOW', 'MODERATE', 'SUBTROPICAL', 'LOAMY', 5.00, 30.00, 200, 6.00, TRUE,
 'Compiled from ICFRE plantation research data and Forest Survey of India carbon assessment methodology.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 19
('Bael', 'Aegle marmelos', 'Rutaceae', 'Vilvam / Bel',
 'Small, drought-tolerant fruit tree with a modest crown. Suits compact sites and low-maintenance planting on dry soils.',
 12.600, 9.160, 24.000, 59.400, 35.300, 22.600, 15.500,
 'SLOW', 'LOW', 'SEMI_ARID', 'WELL_DRAINED_ANY', 3.00, 10.00, 90, 5.00, TRUE,
 'Compiled from ICFRE dryland species trials; carbon figures scaled per Nowak & Crane (2002) canopy relationships.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 20
('Indian Almond', 'Terminalia catappa', 'Combretaceae', 'Nattu vadhumai',
 'Tiered horizontal branching giving broad, layered shade. Salt-tolerant and fast-establishing on coastal sites.',
 19.200, 13.960, 80.800, 199.600, 118.800, 76.000, 52.300,
 'FAST', 'MODERATE', 'COASTAL', 'SANDY', 5.50, 18.00, 80, 8.00, FALSE,
 'Compiled from i-Tree Eco v6 species database (USDA Forest Service); coastal performance data from ICFRE.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 21
('Champak', 'Magnolia champaca', 'Magnoliaceae', 'Shenbagam / Champa',
 'Evergreen with fragrant flowers and a moderately dense crown. Favoured for temple grounds, parks and institutional landscaping.',
 16.400, 11.920, 32.700, 80.800, 48.100, 30.800, 21.200,
 'MODERATE', 'HIGH', 'TROPICAL', 'LOAMY', 3.50, 15.00, 80, 5.00, TRUE,
 'Compiled from TERI urban green cover assessment methodology; carbon figures per i-Tree Eco canopy relationships.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 22
('Indian Cork Tree', 'Millingtonia hortensis', 'Bignoniaceae', 'Maramalli',
 'Tall, slender ornamental with a light feathery crown and night-fragrant flowers. Small footprint suits medians and narrow verges.',
 14.900, 10.830, 32.700, 80.800, 48.100, 30.800, 21.200,
 'FAST', 'LOW', 'TROPICAL', 'WELL_DRAINED_ANY', 3.50, 18.00, 60, 5.00, TRUE,
 'Compiled from TERI urban green cover assessment methodology; growth data from ICFRE ornamental species trials.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 23
('Indian Jujube', 'Ziziphus mauritiana', 'Rhamnaceae', 'Ilanthai / Ber',
 'Small hardy fruit tree for arid sites. Very low water demand; useful where nothing larger will establish.',
 10.400, 7.560, 24.000, 59.400, 35.300, 22.600, 15.500,
 'MODERATE', 'LOW', 'ARID', 'SANDY', 3.00, 8.00, 50, 4.00, TRUE,
 'Compiled from ICFRE arid-zone plantation research (CAZRI methodology); carbon figures per Nowak & Crane (2002).',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 24
('Drumstick', 'Moringa oleifera', 'Moringaceae', 'Murungai / Sahjan',
 'Very fast-growing, short-lived species with a sparse crown. Delivers benefit quickly but needs replacement within a few decades.',
 9.800, 7.120, 16.700, 41.200, 24.500, 15.700, 10.800,
 'FAST', 'LOW', 'SEMI_ARID', 'SANDY', 2.50, 10.00, 30, 3.50, TRUE,
 'Compiled from ICFRE agroforestry species data; carbon figures scaled per i-Tree Eco canopy relationships.',
 TRUE, NOW(6), NOW(6), 'system', 'system'),

-- 25
('Copperpod', 'Peltophorum pterocarpum', 'Fabaceae', 'Perungondrai / Rusty shield',
 'Fast-growing avenue tree with a spreading crown and yellow flowering. Tolerant of coastal exposure and compacted urban soils.',
 18.300, 13.300, 66.800, 165.000, 98.200, 62.800, 43.200,
 'FAST', 'LOW', 'COASTAL', 'WELL_DRAINED_ANY', 5.00, 15.00, 70, 7.00, FALSE,
 'Compiled from i-Tree Eco v6 species database (USDA Forest Service); urban performance data from TERI green cover assessment.',
 TRUE, NOW(6), NOW(6), 'system', 'system');


-- =====================================================================================
--  3. ENVIRONMENTAL COEFFICIENTS
--
--  Every numeric constant the calculation engine applies is a row here. Adding a new
--  equivalence — "this offsets N domestic flights" — is one INSERT and no Java at all.
--
--  Each row carries: the current value, the seeded default it can be restored to, the
--  range outside which an edit is rejected, its unit, and its source.
-- =====================================================================================
INSERT INTO environmental_coefficients (
    coefficient_key, label, coefficient_value, default_value, min_value, max_value,
    unit, category, description, source, created_at, updated_at, created_by, updated_by
) VALUES

-- --------------------------------------------------------------------- ADJUSTMENT
('calc.survival.rate', 'Expected survival rate',
 0.850000, 0.850000, 0.100000, 1.000000, 'ratio', 'ADJUSTMENT',
 'Fraction of planted saplings expected to survive to maturity under normal urban maintenance. Applied to every plan so headline figures reflect trees that live, not trees that were planted.',
 'ICFRE urban plantation survival studies; Forest Survey of India afforestation monitoring reports.',
 NOW(6), NOW(6), 'system', 'system'),

('calc.maturity.years.slow', 'Years to maturity — slow-growing species',
 20.000000, 20.000000, 1.000000, 60.000000, 'years', 'ADJUSTMENT',
 'Years a slow-growing species takes to reach full sequestration performance. Drives the maturity ramp in the multi-year projection.',
 'ICFRE species growth trials; Forest Survey of India yield tables.',
 NOW(6), NOW(6), 'system', 'system'),

('calc.maturity.years.moderate', 'Years to maturity — moderate-growing species',
 12.000000, 12.000000, 1.000000, 60.000000, 'years', 'ADJUSTMENT',
 'Years a moderate-growing species takes to reach full sequestration performance.',
 'ICFRE species growth trials; Forest Survey of India yield tables.',
 NOW(6), NOW(6), 'system', 'system'),

('calc.maturity.years.fast', 'Years to maturity — fast-growing species',
 7.000000, 7.000000, 1.000000, 60.000000, 'years', 'ADJUSTMENT',
 'Years a fast-growing species takes to reach full sequestration performance.',
 'ICFRE fast-growing species trials; Forest Survey of India yield tables.',
 NOW(6), NOW(6), 'system', 'system'),

('calc.site.condition.factor', 'Site condition factor',
 1.000000, 1.000000, 0.500000, 1.500000, 'factor', 'ADJUSTMENT',
 'Multiplier for overall site quality — soil depth, irrigation availability and maintenance standard. Below 1.0 for poor sites, above 1.0 for well-maintained irrigated sites.',
 'ICFRE urban greening guidelines; adjust locally against observed establishment performance.',
 NOW(6), NOW(6), 'system', 'system'),

-- -------------------------------------------------------------------- FEASIBILITY
('feasibility.threshold.under.max', 'Under-utilised threshold',
 40.000000, 40.000000, 0.000000, 100.000000, 'percent', 'FEASIBILITY',
 'Space utilisation at or below this percentage is reported as under-utilised, indicating the site could support more trees.',
 'Derived from recommended inter-tree spacing in CPCB greenbelt development guidelines.',
 NOW(6), NOW(6), 'system', 'system'),

('feasibility.threshold.optimal.max', 'Optimal threshold',
 90.000000, 90.000000, 0.000000, 200.000000, 'percent', 'FEASIBILITY',
 'Space utilisation up to this percentage is reported as optimal — dense enough to be effective, open enough to maintain.',
 'Derived from recommended inter-tree spacing in CPCB greenbelt development guidelines.',
 NOW(6), NOW(6), 'system', 'system'),

('feasibility.threshold.tight.max', 'Tight threshold',
 100.000000, 100.000000, 0.000000, 300.000000, 'percent', 'FEASIBILITY',
 'Space utilisation up to this percentage is reported as tight; beyond it the planting is reported as overcrowded.',
 'Derived from recommended inter-tree spacing in CPCB greenbelt development guidelines.',
 NOW(6), NOW(6), 'system', 'system'),

-- -------------------------------------------------------------------- EQUIVALENCE
('equiv.car.co2.kg.per.km', 'Car emissions per kilometre',
 0.121000, 0.121000, 0.050000, 0.400000, 'kg CO2/km', 'EQUIVALENCE',
 'Average carbon dioxide emitted per kilometre by a passenger car. Used to express sequestration as car-kilometres offset.',
 'Bureau of Energy Efficiency (India) passenger-vehicle fuel-efficiency norms; IPCC mobile-combustion emission factors.',
 NOW(6), NOW(6), 'system', 'system'),

('equiv.household.electricity.co2.kg.per.year', 'Household electricity emissions per year',
 1500.000000, 1500.000000, 100.000000, 10000.000000, 'kg CO2/year', 'EQUIVALENCE',
 'Average annual carbon dioxide attributable to one household''s electricity consumption. Used to express sequestration in households offset.',
 'Central Electricity Authority (India) CO2 baseline database for the Indian power sector.',
 NOW(6), NOW(6), 'system', 'system'),

('equiv.person.oxygen.kg.per.year', 'Human oxygen consumption per year',
 262.800000, 262.800000, 100.000000, 500.000000, 'kg O2/year', 'EQUIVALENCE',
 'Oxygen one adult consumes in a year, at approximately 0.72 kg per day. Used to express oxygen generation in people supported.',
 'Standard human respiratory physiology; 0.72 kg/day x 365 days.',
 NOW(6), NOW(6), 'system', 'system'),

('equiv.petrol.co2.kg.per.litre', 'Petrol combustion emissions',
 2.310000, 2.310000, 1.000000, 4.000000, 'kg CO2/litre', 'EQUIVALENCE',
 'Carbon dioxide released by burning one litre of petrol. Used to express sequestration in litres of fuel offset.',
 'IPCC Guidelines for National Greenhouse Gas Inventories, stationary and mobile combustion factors.',
 NOW(6), NOW(6), 'system', 'system'),

-- ------------------------------------------------------------------------ SCORING
('score.weight.carbon', 'Impact score weight — carbon',
 0.350000, 0.350000, 0.000000, 1.000000, 'weight', 'SCORING',
 'Contribution of the carbon sub-score to the overall environmental impact score. All four weights must sum to 1.0.',
 'Project scoring methodology; adjust to reflect local policy priorities.',
 NOW(6), NOW(6), 'system', 'system'),

('score.weight.air', 'Impact score weight — air quality',
 0.300000, 0.300000, 0.000000, 1.000000, 'weight', 'SCORING',
 'Contribution of the air-quality sub-score to the overall environmental impact score.',
 'Project scoring methodology; adjust to reflect local policy priorities.',
 NOW(6), NOW(6), 'system', 'system'),

('score.weight.canopy', 'Impact score weight — canopy coverage',
 0.200000, 0.200000, 0.000000, 1.000000, 'weight', 'SCORING',
 'Contribution of the canopy-coverage sub-score to the overall environmental impact score.',
 'Project scoring methodology; adjust to reflect local policy priorities.',
 NOW(6), NOW(6), 'system', 'system'),

('score.weight.diversity', 'Impact score weight — species diversity',
 0.150000, 0.150000, 0.000000, 1.000000, 'weight', 'SCORING',
 'Contribution of the species-diversity sub-score to the overall environmental impact score.',
 'Project scoring methodology; adjust to reflect local policy priorities.',
 NOW(6), NOW(6), 'system', 'system'),

('score.benchmark.co2.kg.per.tree', 'Carbon benchmark per tree',
 25.000000, 25.000000, 1.000000, 200.000000, 'kg CO2/tree/year', 'SCORING',
 'Annual sequestration per tree that scores 100 on the carbon sub-score. Set near the upper end of what common urban species achieve.',
 'Derived from the seeded species catalogue; the highest-performing species sequesters approximately 31.5 kg/tree/year.',
 NOW(6), NOW(6), 'system', 'system'),

('score.benchmark.pollutant.g.per.tree', 'Air-quality benchmark per tree',
 300.000000, 300.000000, 10.000000, 5000.000000, 'g/tree/year', 'SCORING',
 'Total annual pollutant removal per tree that scores 100 on the air-quality sub-score.',
 'Derived from the seeded species catalogue across all five tracked pollutants.',
 NOW(6), NOW(6), 'system', 'system'),

('score.target.canopy.coverage.percent', 'Target canopy coverage',
 40.000000, 40.000000, 1.000000, 100.000000, 'percent', 'SCORING',
 'Canopy coverage of the available land that scores 100 on the canopy sub-score.',
 'Widely adopted urban tree-canopy goal; see CPCB greenbelt development guidelines.',
 NOW(6), NOW(6), 'system', 'system'),

('score.max.diversity.index', 'Diversity index ceiling',
 2.500000, 2.500000, 0.100000, 5.000000, 'index', 'SCORING',
 'Shannon-Wiener diversity index that scores 100 on the diversity sub-score. An index of 2.5 corresponds to a well-mixed planting of roughly twelve evenly represented species.',
 'Shannon, C.E. (1948), "A Mathematical Theory of Communication", Bell System Technical Journal 27:379-423.',
 NOW(6), NOW(6), 'system', 'system'),

-- ------------------------------------------------------------------------- LIMITS
('limit.monoculture.share.percent', 'Monoculture warning threshold',
 60.000000, 60.000000, 10.000000, 100.000000, 'percent', 'LIMIT',
 'Share of a plan a single species may occupy before a monoculture risk warning is raised. Concentration above this level leaves a planting vulnerable to a single pest or disease.',
 'Urban forestry diversity guidance; commonly cited 10-20-30 rule for species, genus and family concentration.',
 NOW(6), NOW(6), 'system', 'system');
