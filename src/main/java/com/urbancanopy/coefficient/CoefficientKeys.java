package com.urbancanopy.coefficient;

/**
 * The vocabulary of coefficient keys.
 *
 * <p>The <em>values</em> of these coefficients live in the database and are editable at
 * runtime. Their <em>names</em> are part of the code's contract with the data, so they are
 * declared once here rather than being retyped as string literals at each lookup — a typo
 * in {@code "calc.survival.rate"} would otherwise fail silently at runtime instead of
 * loudly at compile time.</p>
 *
 * <p>Every constant below has a corresponding seeded row in
 * {@code V2__seed_reference_data.sql}. The startup check in
 * {@code CoefficientService} verifies that correspondence and refuses to start if a key
 * is missing.</p>
 */
public final class CoefficientKeys {

    private CoefficientKeys() {
        throw new AssertionError("Constants class");
    }

    // ------------------------------------------------------------------ ADJUSTMENT

    /** Fraction of planted trees expected to survive to maturity (0–1). */
    public static final String SURVIVAL_RATE = "calc.survival.rate";

    /** Years a slow-growing species takes to reach mature performance. */
    public static final String MATURITY_YEARS_SLOW = "calc.maturity.years.slow";

    /** Years a moderate-growing species takes to reach mature performance. */
    public static final String MATURITY_YEARS_MODERATE = "calc.maturity.years.moderate";

    /** Years a fast-growing species takes to reach mature performance. */
    public static final String MATURITY_YEARS_FAST = "calc.maturity.years.fast";

    /** Multiplier for overall site quality — soil, irrigation, maintenance (0.5–1.5). */
    public static final String SITE_CONDITION_FACTOR = "calc.site.condition.factor";

    // ----------------------------------------------------------------- FEASIBILITY

    /** Below this space utilisation percentage the site is under-utilised. */
    public static final String FEASIBILITY_UNDER_MAX = "feasibility.threshold.under.max";

    /** Up to this utilisation percentage the planting is optimal. */
    public static final String FEASIBILITY_OPTIMAL_MAX = "feasibility.threshold.optimal.max";

    /** Up to this utilisation percentage the planting is tight; beyond it, overcrowded. */
    public static final String FEASIBILITY_TIGHT_MAX = "feasibility.threshold.tight.max";

    // ----------------------------------------------------------------- EQUIVALENCE

    /** Average CO₂ emitted per kilometre by a passenger car, in kg. */
    public static final String EQUIV_CAR_CO2_PER_KM = "equiv.car.co2.kg.per.km";

    /** Average annual CO₂ from one household's electricity use, in kg. */
    public static final String EQUIV_HOUSEHOLD_CO2_PER_YEAR = "equiv.household.electricity.co2.kg.per.year";

    /** Oxygen one person consumes in a year, in kg. */
    public static final String EQUIV_PERSON_O2_PER_YEAR = "equiv.person.oxygen.kg.per.year";

    /** CO₂ released by burning one litre of petrol, in kg. */
    public static final String EQUIV_PETROL_CO2_PER_LITRE = "equiv.petrol.co2.kg.per.litre";

    // --------------------------------------------------------------------- SCORING

    public static final String SCORE_WEIGHT_CARBON = "score.weight.carbon";
    public static final String SCORE_WEIGHT_AIR = "score.weight.air";
    public static final String SCORE_WEIGHT_CANOPY = "score.weight.canopy";
    public static final String SCORE_WEIGHT_DIVERSITY = "score.weight.diversity";

    /** CO₂ per tree per year that scores 100 on the carbon sub-score, in kg. */
    public static final String SCORE_BENCHMARK_CO2_PER_TREE = "score.benchmark.co2.kg.per.tree";

    /** Total pollutant removal per tree per year that scores 100 on the air sub-score, in g. */
    public static final String SCORE_BENCHMARK_POLLUTANT_PER_TREE = "score.benchmark.pollutant.g.per.tree";

    /** Canopy coverage percentage that scores 100 on the canopy sub-score. */
    public static final String SCORE_TARGET_CANOPY_COVERAGE = "score.target.canopy.coverage.percent";

    /** Shannon diversity index that scores 100 on the diversity sub-score. */
    public static final String SCORE_MAX_DIVERSITY_INDEX = "score.max.diversity.index";

    // ---------------------------------------------------------------------- LIMITS

    /** Share of a plan a single species may occupy before a monoculture warning is raised. */
    public static final String MONOCULTURE_SHARE_PERCENT = "limit.monoculture.share.percent";

    /** Weights that must sum to 1.0 (BR-20). */
    public static final String[] SCORING_WEIGHTS = {
            SCORE_WEIGHT_CARBON, SCORE_WEIGHT_AIR, SCORE_WEIGHT_CANOPY, SCORE_WEIGHT_DIVERSITY
    };

    /** Every key the engine requires. Verified against the database at startup. */
    public static final String[] REQUIRED_KEYS = {
            SURVIVAL_RATE, MATURITY_YEARS_SLOW, MATURITY_YEARS_MODERATE, MATURITY_YEARS_FAST,
            SITE_CONDITION_FACTOR,
            FEASIBILITY_UNDER_MAX, FEASIBILITY_OPTIMAL_MAX, FEASIBILITY_TIGHT_MAX,
            EQUIV_CAR_CO2_PER_KM, EQUIV_HOUSEHOLD_CO2_PER_YEAR, EQUIV_PERSON_O2_PER_YEAR,
            EQUIV_PETROL_CO2_PER_LITRE,
            SCORE_WEIGHT_CARBON, SCORE_WEIGHT_AIR, SCORE_WEIGHT_CANOPY, SCORE_WEIGHT_DIVERSITY,
            SCORE_BENCHMARK_CO2_PER_TREE, SCORE_BENCHMARK_POLLUTANT_PER_TREE,
            SCORE_TARGET_CANOPY_COVERAGE, SCORE_MAX_DIVERSITY_INDEX,
            MONOCULTURE_SHARE_PERCENT
    };
}
