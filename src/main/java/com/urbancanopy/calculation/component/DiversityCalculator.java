package com.urbancanopy.calculation.component;

import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Measures how evenly a plan's trees are spread across species (FR-CALC-11).
 *
 * <p>An estimation tool that reported only carbon would reward the worst possible advice:
 * plant ten thousand of whichever single species has the highest sequestration rate. That
 * planting is one pest, one pathogen or one bad season away from losing its entire
 * estimated benefit — as Delhi's kikar monocultures and every elm-lined street in
 * twentieth-century Europe demonstrated. Diversity is therefore scored, and concentration
 * is warned about.</p>
 *
 * <p>The measure is the Shannon–Wiener index, H′ = −Σ pᵢ ln pᵢ, where pᵢ is species i's
 * share of all trees. It is 0 for a single species and rises with both the number of
 * species and the evenness of their distribution.</p>
 */
@Component
public class DiversityCalculator {

    /**
     * Computes the diversity profile of a species mix.
     *
     * @param input        the plan
     * @param coefficients supplies the monoculture warning threshold
     */
    public CalculationResult.Diversity evaluate(CalculationInput input, CoefficientSet coefficients) {

        List<CalculationInput.SpeciesInput> items = input.items();
        int totalTrees = input.totalTrees();

        if (items.isEmpty() || totalTrees <= 0) {
            return new CalculationResult.Diversity(BigDecimal.ZERO, 0, null, null, false);
        }

        BigDecimal shannonIndex = shannonIndex(items, totalTrees);

        CalculationInput.SpeciesInput dominant = items.stream()
                .max(Comparator.comparingInt(CalculationInput.SpeciesInput::quantity))
                .orElseThrow();

        BigDecimal dominantShare = Decimals
                .percentage(BigDecimal.valueOf(dominant.quantity()), BigDecimal.valueOf(totalTrees))
                .orElse(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal monocultureThreshold = coefficients.require(CoefficientKeys.MONOCULTURE_SHARE_PERCENT);
        boolean monocultureRisk = items.size() == 1
                || dominantShare.compareTo(monocultureThreshold) > 0;

        return new CalculationResult.Diversity(
                shannonIndex, items.size(), dominant.commonName(), dominantShare, monocultureRisk);
    }

    /**
     * Shannon–Wiener index, H′ = −Σ pᵢ ln pᵢ.
     *
     * <p><strong>The one place this application uses {@code double}.</strong> {@code
     * BigDecimal} has no natural logarithm, and implementing one to arbitrary precision
     * would add real complexity for no benefit here: H′ is a dimensionless index reported
     * to four decimal places, never a mass, never a currency, and never summed into
     * anything else. A {@code double} carries about fifteen significant figures, so the
     * error is some eleven orders of magnitude below what is displayed. The restriction
     * that matters — no floating point in any <em>reported quantity</em> — is intact.</p>
     */
    public BigDecimal shannonIndex(List<CalculationInput.SpeciesInput> items, int totalTrees) {
        if (items.size() <= 1 || totalTrees <= 0) {
            return BigDecimal.ZERO;
        }

        double index = 0.0;
        for (CalculationInput.SpeciesInput item : items) {
            if (item.quantity() <= 0) {
                continue;
            }
            double proportion = (double) item.quantity() / totalTrees;
            index -= proportion * Math.log(proportion);
        }

        return BigDecimal.valueOf(index).setScale(Decimals.INDEX_SCALE, RoundingMode.HALF_UP);
    }
}
