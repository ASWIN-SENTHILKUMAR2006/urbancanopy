package com.urbancanopy.calculation.component;

import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Projects carbon sequestration year by year across the plan's horizon (FR-CALC-07).
 *
 * <p>The headline annual figure is a steady-state number: what the planting delivers once
 * grown. This projection shows the road to it, and it is where the choice of species stops
 * being an abstraction. A fast-growing mix reaches full performance in about seven years;
 * a banyan-heavy plan takes twenty. Over a ten-year horizon those two plans can have
 * identical headline figures and very different cumulative totals — which is precisely the
 * kind of trade-off a planner needs to see before committing.</p>
 *
 * <p>Each year's rate is computed from that year's maturity factor per species, so a mixed
 * planting ramps at a blended rate rather than all at once.</p>
 */
@Component
public class ProjectionCalculator {

    private final EffectiveTreeCalculator effectiveTreeCalculator;

    public ProjectionCalculator(EffectiveTreeCalculator effectiveTreeCalculator) {
        this.effectiveTreeCalculator = effectiveTreeCalculator;
    }

    /**
     * Builds one point per year from planting to the end of the horizon.
     *
     * @param input        the plan, supplying the horizon and species mix
     * @param startYear    calendar year of planting, so the chart axis reads 2026 not 1
     * @param coefficients active constants
     * @return one {@link CalculationResult.ProjectionPoint} per year, in order
     */
    public List<CalculationResult.ProjectionPoint> project(
            CalculationInput input, int startYear, CoefficientSet coefficients) {

        List<CalculationResult.ProjectionPoint> points = new ArrayList<>(input.projectionYears());
        BigDecimal cumulative = BigDecimal.ZERO;
        int totalTrees = input.totalTrees();

        for (int year = 1; year <= input.projectionYears(); year++) {
            BigDecimal annualCo2 = BigDecimal.ZERO;
            BigDecimal weightedMaturity = BigDecimal.ZERO;

            for (CalculationInput.SpeciesInput species : input.items()) {
                BigDecimal effectiveTrees = effectiveTreeCalculator
                        .inYear(species.quantity(), species.growthRate(), year, coefficients);

                annualCo2 = annualCo2.add(
                        effectiveTrees.multiply(Decimals.orZero(species.co2KgPerTreeYear())));

                // Weight each species' maturity by its share of the planting, so the
                // reported average reflects the plan rather than the species list.
                BigDecimal maturity = effectiveTreeCalculator
                        .maturityFactor(species.growthRate(), year, coefficients);
                weightedMaturity = weightedMaturity.add(
                        maturity.multiply(BigDecimal.valueOf(species.quantity())));
            }

            annualCo2 = annualCo2.setScale(3, RoundingMode.HALF_UP);
            cumulative = cumulative.add(annualCo2);

            BigDecimal averageMaturity = totalTrees > 0
                    ? Decimals.divideOr(weightedMaturity, BigDecimal.valueOf(totalTrees), BigDecimal.ZERO)
                    .setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            points.add(new CalculationResult.ProjectionPoint(
                    year,
                    startYear + year - 1,
                    annualCo2,
                    cumulative.setScale(3, RoundingMode.HALF_UP),
                    averageMaturity));
        }

        return List.copyOf(points);
    }

    /** Cumulative CO₂ over the whole horizon, in kilograms. */
    public BigDecimal horizonTotal(List<CalculationResult.ProjectionPoint> projection) {
        if (projection.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return projection.get(projection.size() - 1).cumulativeCo2Kg();
    }
}
