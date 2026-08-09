package com.urbancanopy.calculation.component;

import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Condenses four dimensions of a plan into a single 0–100 score (FR-CALC-10).
 *
 * <p>A single number is a compromise, and it is treated as one. Each sub-score is
 * returned alongside the total so the figure can be explained rather than merely asserted:
 * a planner who sees 62 needs to know whether the shortfall is carbon, air quality, canopy
 * or diversity before they can do anything about it.</p>
 *
 * <p>Each sub-score is the plan's performance as a percentage of a benchmark, capped at
 * 100. All four benchmarks and all four weights are coefficient rows, so a municipality
 * that cares more about particulates than about carbon can reweight the score without
 * touching the code.</p>
 *
 * <table border="1">
 *   <caption>Sub-scores</caption>
 *   <tr><th>Sub-score</th><th>Measure</th><th>Benchmark coefficient</th></tr>
 *   <tr><td>Carbon</td><td>CO₂ per tree per year</td><td>{@code score.benchmark.co2.kg.per.tree}</td></tr>
 *   <tr><td>Air quality</td><td>total pollutant removal per tree per year</td><td>{@code score.benchmark.pollutant.g.per.tree}</td></tr>
 *   <tr><td>Canopy</td><td>canopy coverage of the site</td><td>{@code score.target.canopy.coverage.percent}</td></tr>
 *   <tr><td>Diversity</td><td>Shannon H′</td><td>{@code score.max.diversity.index}</td></tr>
 * </table>
 *
 * <p><strong>Why per-tree, not absolute.</strong> Carbon and air-quality sub-scores are
 * intensity measures. Scoring absolute totals would mean the score only ever rewarded
 * planting more trees, which every plan can do and which tells a planner nothing about
 * whether their <em>species choices</em> were good. Intensity asks the useful question:
 * given the trees you are planting, are they the right ones?</p>
 */
@Component
public class ImpactScorer {

    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(100);

    /**
     * Scores a plan.
     *
     * @param annualCo2Kg            total annual sequestration
     * @param totalPollutantGrams    total annual pollutant removal
     * @param totalTrees             trees as planned
     * @param canopyCoveragePercent  coverage of the site; {@code null} when no land area
     * @param shannonIndex           diversity index
     * @param coefficients           supplies weights and benchmarks
     */
    public CalculationResult.ImpactScore score(
            BigDecimal annualCo2Kg,
            BigDecimal totalPollutantGrams,
            int totalTrees,
            BigDecimal canopyCoveragePercent,
            BigDecimal shannonIndex,
            CoefficientSet coefficients) {

        BigDecimal carbonWeight = coefficients.require(CoefficientKeys.SCORE_WEIGHT_CARBON);
        BigDecimal airWeight = coefficients.require(CoefficientKeys.SCORE_WEIGHT_AIR);
        BigDecimal canopyWeight = coefficients.require(CoefficientKeys.SCORE_WEIGHT_CANOPY);
        BigDecimal diversityWeight = coefficients.require(CoefficientKeys.SCORE_WEIGHT_DIVERSITY);

        BigDecimal carbonSubScore = intensityScore(
                annualCo2Kg, totalTrees,
                coefficients.require(CoefficientKeys.SCORE_BENCHMARK_CO2_PER_TREE));

        BigDecimal airSubScore = intensityScore(
                totalPollutantGrams, totalTrees,
                coefficients.require(CoefficientKeys.SCORE_BENCHMARK_POLLUTANT_PER_TREE));

        BigDecimal canopySubScore = ratioScore(
                canopyCoveragePercent,
                coefficients.require(CoefficientKeys.SCORE_TARGET_CANOPY_COVERAGE));

        BigDecimal diversitySubScore = ratioScore(
                shannonIndex,
                coefficients.require(CoefficientKeys.SCORE_MAX_DIVERSITY_INDEX));

        BigDecimal overall = carbonSubScore.multiply(carbonWeight)
                .add(airSubScore.multiply(airWeight))
                .add(canopySubScore.multiply(canopyWeight))
                .add(diversitySubScore.multiply(diversityWeight))
                .setScale(2, RoundingMode.HALF_UP);

        return new CalculationResult.ImpactScore(
                Decimals.clamp(overall, BigDecimal.ZERO, MAX_SCORE),
                carbonSubScore, airSubScore, canopySubScore, diversitySubScore,
                carbonWeight, airWeight, canopyWeight, diversityWeight);
    }

    /**
     * Scores a total normalised by tree count against a per-tree benchmark.
     * Returns 0 rather than dividing by zero when there are no trees.
     */
    private BigDecimal intensityScore(BigDecimal total, int totalTrees, BigDecimal benchmarkPerTree) {
        if (totalTrees <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal perTree = Decimals.divideOr(
                Decimals.orZero(total), BigDecimal.valueOf(totalTrees), BigDecimal.ZERO);
        return ratioScore(perTree, benchmarkPerTree);
    }

    /** Scores a value as a percentage of a target, capped at 100 and floored at 0. */
    private BigDecimal ratioScore(BigDecimal value, BigDecimal target) {
        if (value == null || target == null || target.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal score = Decimals
                .divideOr(value, target, BigDecimal.ZERO)
                .multiply(MAX_SCORE)
                .setScale(2, RoundingMode.HALF_UP);
        return Decimals.clamp(score, BigDecimal.ZERO, MAX_SCORE);
    }
}
