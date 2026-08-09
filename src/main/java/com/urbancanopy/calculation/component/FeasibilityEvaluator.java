package com.urbancanopy.calculation.component;

import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.calculation.model.FeasibilityVerdict;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Answers "can this actually be planted here?" (FR-CALC-05).
 *
 * <p>This is the component that makes the application a decision-support system rather
 * than a calculator. A carbon figure tells a planner what a plantation would achieve; the
 * feasibility verdict tells them whether the plantation is possible on the land they
 * have — which is the very next question, and the one that changes what they do.</p>
 *
 * <p>The verdict is <strong>always advisory</strong> (BR-13). An overcrowded plan is
 * flagged in the strongest terms the interface has, together with the maximum plantable
 * count and the shortfall, and is then saved exactly as the planner specified. The system
 * informs; the planner decides.</p>
 */
@Component
public class FeasibilityEvaluator {

    private final SpeciesContributionCalculator contributionCalculator;

    public FeasibilityEvaluator(SpeciesContributionCalculator contributionCalculator) {
        this.contributionCalculator = contributionCalculator;
    }

    /**
     * Assesses how the planned trees fit the available land.
     *
     * @param input        the plan, whose land area may be absent
     * @param coefficients supplies the three verdict thresholds
     */
    public CalculationResult.Feasibility evaluate(CalculationInput input, CoefficientSet coefficients) {

        BigDecimal requiredArea = totalRequiredArea(input.items());
        BigDecimal availableArea = Decimals.orZero(input.landAreaSquareMetres());

        // No land area supplied: every other metric still computes, but this question has
        // no answer. Reporting zero would be a lie; reporting "not applicable" is the truth.
        if (!input.hasLandArea()) {
            return new CalculationResult.Feasibility(
                    FeasibilityVerdict.NOT_APPLICABLE,
                    requiredArea, BigDecimal.ZERO, null, null, null);
        }

        Optional<BigDecimal> utilisation = Decimals.percentage(requiredArea, availableArea);
        BigDecimal utilisationPercent = utilisation
                .map(value -> value.setScale(2, RoundingMode.HALF_UP))
                .orElse(null);

        BigDecimal surplus = availableArea.subtract(requiredArea).setScale(2, RoundingMode.HALF_UP);

        return new CalculationResult.Feasibility(
                verdictFor(utilisationPercent, coefficients),
                requiredArea,
                availableArea.setScale(2, RoundingMode.HALF_UP),
                utilisationPercent,
                surplus,
                maxPlantableTrees(input, availableArea));
    }

    /** Ground demanded by the whole plan at recommended spacing: Σ qᵢ·sᵢ². */
    public BigDecimal totalRequiredArea(List<CalculationInput.SpeciesInput> items) {
        return items.stream()
                .map(item -> contributionCalculator.requiredArea(
                        BigDecimal.valueOf(item.quantity()), item.spacingMetres()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Maps a utilisation percentage onto the four-way verdict.
     *
     * <p>The thresholds are coefficient rows, so a municipality that plants more densely
     * than the seeded defaults assume can retune the verdict to its own standards without
     * a code change.</p>
     */
    private FeasibilityVerdict verdictFor(BigDecimal utilisationPercent, CoefficientSet coefficients) {
        if (utilisationPercent == null) {
            return FeasibilityVerdict.NOT_APPLICABLE;
        }
        BigDecimal underMax = coefficients.require(CoefficientKeys.FEASIBILITY_UNDER_MAX);
        BigDecimal optimalMax = coefficients.require(CoefficientKeys.FEASIBILITY_OPTIMAL_MAX);
        BigDecimal tightMax = coefficients.require(CoefficientKeys.FEASIBILITY_TIGHT_MAX);

        if (utilisationPercent.compareTo(underMax) < 0) {
            return FeasibilityVerdict.UNDER_UTILISED;
        }
        if (utilisationPercent.compareTo(optimalMax) <= 0) {
            return FeasibilityVerdict.OPTIMAL;
        }
        if (utilisationPercent.compareTo(tightMax) <= 0) {
            return FeasibilityVerdict.TIGHT;
        }
        return FeasibilityVerdict.OVERCROWDED;
    }

    /**
     * How many trees the available land would hold <em>at this species mix</em>.
     *
     * <p>Computed from the quantity-weighted mean footprint rather than from any single
     * species, because the answer must reflect the plan the planner actually described. A
     * mix of banyans and mast trees has a mean footprint neither species has on its own,
     * and quoting either one would be misleading.</p>
     *
     * @return the maximum plantable count, or {@code null} if the mix has no footprint
     */
    private Integer maxPlantableTrees(CalculationInput input, BigDecimal availableArea) {
        int totalTrees = input.totalTrees();
        if (totalTrees <= 0) {
            return null;
        }
        BigDecimal requiredArea = totalRequiredArea(input.items());
        if (requiredArea.signum() <= 0) {
            return null;
        }
        BigDecimal meanFootprint = Decimals.divideOr(
                requiredArea, BigDecimal.valueOf(totalTrees), null);
        if (meanFootprint == null || meanFootprint.signum() <= 0) {
            return null;
        }
        return availableArea.divide(meanFootprint, 0, RoundingMode.DOWN).intValue();
    }
}
