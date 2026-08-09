package com.urbancanopy.calculation.component;

import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Computes every per-species figure in one pass.
 *
 * <p>Carbon, oxygen and all five pollutants share exactly the same shape —
 * {@code effectiveTrees × ratePerTreePerYear}. Splitting them into a
 * {@code CarbonCalculator}, an {@code OxygenCalculator} and a {@code PollutantCalculator}
 * would look like separation of concerns but would in fact be the same concern written
 * seven times, and seven places for the survival adjustment to be forgotten. The single
 * responsibility here is "evaluate one species", and it is discharged once.</p>
 *
 * <p>Canopy area uses π r² per tree at <em>planted</em> quantity, not effective quantity:
 * canopy is a physical footprint question — how much ground will be shaded, and will the
 * trees fit — and it is answered by the trees that are actually put in the ground.
 * Sequestration is a performance question and uses the survival-adjusted count. Conflating
 * the two would either overstate benefit or understate crowding.</p>
 */
@Component
public class SpeciesContributionCalculator {

    /** π to a precision far beyond anything the inputs justify, so it is never the limit. */
    private static final BigDecimal PI = new BigDecimal("3.14159265358979323846");

    private static final MathContext MC = Decimals.DIVISION_CONTEXT;

    private final EffectiveTreeCalculator effectiveTreeCalculator;

    public SpeciesContributionCalculator(EffectiveTreeCalculator effectiveTreeCalculator) {
        this.effectiveTreeCalculator = effectiveTreeCalculator;
    }

    /**
     * Evaluates one species at maturity.
     *
     * <p>Share percentages are left null here and filled in by the engine once plan totals
     * are known — a species cannot know its own share of a total it has not seen.</p>
     */
    public CalculationResult.SpeciesContribution evaluate(
            CalculationInput.SpeciesInput species, CoefficientSet coefficients) {

        BigDecimal effectiveTrees = effectiveTreeCalculator.atMaturity(species.quantity(), coefficients);
        BigDecimal plantedTrees = BigDecimal.valueOf(species.quantity());

        BigDecimal pm25 = perYear(effectiveTrees, species.pm25GramsPerTreeYear());
        BigDecimal pm10 = perYear(effectiveTrees, species.pm10GramsPerTreeYear());
        BigDecimal no2 = perYear(effectiveTrees, species.no2GramsPerTreeYear());
        BigDecimal so2 = perYear(effectiveTrees, species.so2GramsPerTreeYear());
        BigDecimal co = perYear(effectiveTrees, species.coGramsPerTreeYear());

        return new CalculationResult.SpeciesContribution(
                species.speciesId(),
                species.commonName(),
                species.scientificName(),
                species.quantity(),
                Decimals.scaled(effectiveTrees, 2),
                perYear(effectiveTrees, species.co2KgPerTreeYear()),
                perYear(effectiveTrees, species.o2KgPerTreeYear()),
                pm25, pm10, no2, so2, co,
                Decimals.scaled(pm25.add(pm10).add(no2).add(so2).add(co), 3),
                canopyArea(plantedTrees, species.canopyRadiusMetres()),
                requiredArea(plantedTrees, species.spacingMetres()),
                null,
                null,
                species.sourceCitation());
    }

    /**
     * Canopy footprint of a species at maturity: {@code quantity × π × radius²}.
     *
     * <p>This is the geometric maximum — the area shaded if no two crowns overlapped.
     * Crowns do overlap in dense planting, which is precisely what the feasibility
     * assessment reports on, so the two figures are meant to be read together.</p>
     */
    public BigDecimal canopyArea(BigDecimal quantity, BigDecimal canopyRadiusMetres) {
        if (canopyRadiusMetres == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal areaPerTree = PI.multiply(canopyRadiusMetres.multiply(canopyRadiusMetres));
        return quantity.multiply(areaPerTree).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Ground a species demands at its recommended spacing: {@code quantity × spacing²}.
     *
     * <p>Spacing is centre-to-centre, so one tree occupies a notional square of
     * {@code spacing × spacing}. This is the standard planning approximation and it is
     * what makes the feasibility verdict comparable to how a site is actually laid out.</p>
     */
    public BigDecimal requiredArea(BigDecimal quantity, BigDecimal spacingMetres) {
        if (spacingMetres == null || spacingMetres.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return quantity.multiply(spacingMetres.multiply(spacingMetres))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** {@code effectiveTrees × ratePerTreePerYear}, at report precision. */
    private BigDecimal perYear(BigDecimal effectiveTrees, BigDecimal ratePerTreePerYear) {
        if (ratePerTreePerYear == null) {
            return BigDecimal.ZERO;
        }
        return effectiveTrees.multiply(ratePerTreePerYear, MC).setScale(3, RoundingMode.HALF_UP);
    }
}
