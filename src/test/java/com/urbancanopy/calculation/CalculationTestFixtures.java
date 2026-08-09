package com.urbancanopy.calculation;

import com.urbancanopy.calculation.component.DiversityCalculator;
import com.urbancanopy.calculation.component.EffectiveTreeCalculator;
import com.urbancanopy.calculation.component.EquivalenceConverter;
import com.urbancanopy.calculation.component.FeasibilityEvaluator;
import com.urbancanopy.calculation.component.ImpactScorer;
import com.urbancanopy.calculation.component.ProjectionCalculator;
import com.urbancanopy.calculation.component.SpeciesContributionCalculator;
import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.species.entity.GrowthRate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared fixtures for calculation-engine tests.
 *
 * <p>Note what is <em>not</em> here: no Spring context, no test database, no mocking
 * framework. The engine is a pure function, so its tests wire seven small objects together
 * by hand and pass in plain values. Assembling the whole scientific core of the
 * application takes eight lines, and every test runs in microseconds — which is the
 * practical payoff of the purity constraint in FR-CALC-12.</p>
 */
public final class CalculationTestFixtures {

    public static final String DISCLAIMER = "Estimates based on published scientific averages.";

    private CalculationTestFixtures() {
    }

    /** Assembles a real engine from real components — no test doubles. */
    public static EnvironmentalCalculationEngine engine() {
        EffectiveTreeCalculator effectiveTrees = new EffectiveTreeCalculator();
        SpeciesContributionCalculator contributions = new SpeciesContributionCalculator(effectiveTrees);
        return new EnvironmentalCalculationEngine(
                contributions,
                new FeasibilityEvaluator(contributions),
                new DiversityCalculator(),
                new ProjectionCalculator(effectiveTrees),
                new ImpactScorer(),
                new EquivalenceConverter());
    }

    /** The seeded coefficient values, matching {@code V2__seed_reference_data.sql}. */
    public static CoefficientSet defaultCoefficients() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();

        values.put(CoefficientKeys.SURVIVAL_RATE, new BigDecimal("0.85"));
        values.put(CoefficientKeys.MATURITY_YEARS_SLOW, new BigDecimal("20"));
        values.put(CoefficientKeys.MATURITY_YEARS_MODERATE, new BigDecimal("12"));
        values.put(CoefficientKeys.MATURITY_YEARS_FAST, new BigDecimal("7"));
        values.put(CoefficientKeys.SITE_CONDITION_FACTOR, new BigDecimal("1.00"));

        values.put(CoefficientKeys.FEASIBILITY_UNDER_MAX, new BigDecimal("40"));
        values.put(CoefficientKeys.FEASIBILITY_OPTIMAL_MAX, new BigDecimal("90"));
        values.put(CoefficientKeys.FEASIBILITY_TIGHT_MAX, new BigDecimal("100"));

        values.put(CoefficientKeys.EQUIV_CAR_CO2_PER_KM, new BigDecimal("0.121"));
        values.put(CoefficientKeys.EQUIV_HOUSEHOLD_CO2_PER_YEAR, new BigDecimal("1500"));
        values.put(CoefficientKeys.EQUIV_PERSON_O2_PER_YEAR, new BigDecimal("262.8"));
        values.put(CoefficientKeys.EQUIV_PETROL_CO2_PER_LITRE, new BigDecimal("2.31"));

        values.put(CoefficientKeys.SCORE_WEIGHT_CARBON, new BigDecimal("0.35"));
        values.put(CoefficientKeys.SCORE_WEIGHT_AIR, new BigDecimal("0.30"));
        values.put(CoefficientKeys.SCORE_WEIGHT_CANOPY, new BigDecimal("0.20"));
        values.put(CoefficientKeys.SCORE_WEIGHT_DIVERSITY, new BigDecimal("0.15"));
        values.put(CoefficientKeys.SCORE_BENCHMARK_CO2_PER_TREE, new BigDecimal("25"));
        values.put(CoefficientKeys.SCORE_BENCHMARK_POLLUTANT_PER_TREE, new BigDecimal("300"));
        values.put(CoefficientKeys.SCORE_TARGET_CANOPY_COVERAGE, new BigDecimal("40"));
        values.put(CoefficientKeys.SCORE_MAX_DIVERSITY_INDEX, new BigDecimal("2.5"));

        values.put(CoefficientKeys.MONOCULTURE_SHARE_PERCENT, new BigDecimal("60"));

        return CoefficientSet.of(values);
    }

    /** Neem, as seeded: 21.8 kg CO₂/tree/yr, fast growing, 4.5 m radius, 6 m spacing. */
    public static CalculationInput.SpeciesInput neem(int quantity) {
        return new CalculationInput.SpeciesInput(
                1L, "Neem", "Azadirachta indica", quantity,
                new BigDecimal("21.800"), new BigDecimal("15.850"),
                new BigDecimal("54.100"), new BigDecimal("133.600"), new BigDecimal("79.500"),
                new BigDecimal("50.900"), new BigDecimal("35.000"),
                GrowthRate.FAST,
                new BigDecimal("4.50"), new BigDecimal("6.00"),
                "Compiled from Nowak & Crane (2002).");
    }

    /** Banyan, as seeded: 31.5 kg CO₂/tree/yr, slow growing, 9 m radius, 12 m spacing. */
    public static CalculationInput.SpeciesInput banyan(int quantity) {
        return new CalculationInput.SpeciesInput(
                3L, "Banyan", "Ficus benghalensis", quantity,
                new BigDecimal("31.500"), new BigDecimal("22.900"),
                new BigDecimal("216.300"), new BigDecimal("534.400"), new BigDecimal("318.100"),
                new BigDecimal("203.600"), new BigDecimal("140.000"),
                GrowthRate.SLOW,
                new BigDecimal("9.00"), new BigDecimal("12.00"),
                "Compiled from i-Tree Eco v6 species database.");
    }

    /** Ashoka, as seeded: small columnar species, 2 m radius, 3 m spacing. */
    public static CalculationInput.SpeciesInput ashoka(int quantity) {
        return new CalculationInput.SpeciesInput(
                9L, "Ashoka", "Polyalthia longifolia", quantity,
                new BigDecimal("11.200"), new BigDecimal("8.140"),
                new BigDecimal("10.700"), new BigDecimal("26.400"), new BigDecimal("15.700"),
                new BigDecimal("10.100"), new BigDecimal("6.900"),
                GrowthRate.MODERATE,
                new BigDecimal("2.00"), new BigDecimal("3.00"),
                "Compiled from TERI urban green cover assessment.");
    }
}
