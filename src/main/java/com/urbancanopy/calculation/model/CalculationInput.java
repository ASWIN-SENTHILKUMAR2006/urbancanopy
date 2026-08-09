package com.urbancanopy.calculation.model;

import com.urbancanopy.species.entity.GrowthRate;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the calculation engine needs, and nothing else.
 *
 * <p>Deliberately not a {@code PlantationPlan}. The engine must be callable for a plan
 * that has been saved, for a plan being edited that has not, and for a purely
 * hypothetical composition typed into the live editor — so it accepts a plain value
 * object rather than a persistent entity. That single decision is what makes
 * {@code POST /api/v1/estimates/preview} possible without writing a row to the database
 * on every keystroke.</p>
 *
 * <p>Each {@link SpeciesInput} carries a <em>copy</em> of the species' reference values
 * rather than a reference to the species. The engine therefore cannot lazily load
 * anything, cannot touch a transaction, and cannot behave differently depending on when
 * it is called.</p>
 *
 * @param landAreaSquareMetres available land, normalised to m². May be {@code null} or
 *                             zero, in which case coverage and feasibility are reported
 *                             as not applicable rather than throwing
 * @param projectionYears      horizon for the multi-year projection
 * @param items                the species composition; may be empty
 */
public record CalculationInput(
        BigDecimal landAreaSquareMetres,
        int projectionYears,
        List<SpeciesInput> items
) {

    public CalculationInput {
        items = items == null ? List.of() : List.copyOf(items);
        projectionYears = Math.max(1, projectionYears);
    }

    /** {@code true} when there is enough information to produce meaningful figures. */
    public boolean hasSpecies() {
        return !items.isEmpty();
    }

    /** {@code true} when a land area was supplied, so spatial metrics can be computed. */
    public boolean hasLandArea() {
        return landAreaSquareMetres != null && landAreaSquareMetres.signum() > 0;
    }

    /** Total trees before any survival or maturity adjustment. */
    public int totalTrees() {
        return items.stream().mapToInt(SpeciesInput::quantity).sum();
    }

    /**
     * One species and its quantity, flattened together with the reference values that
     * describe it.
     *
     * <p>The values are copied out of {@link com.urbancanopy.species.entity.TreeSpecies}
     * at the boundary of the service layer. Flat rather than nested because these are the
     * exact fields the formulae consume, and a test that has to build a three-level object
     * graph to check one multiplication is a test nobody writes.</p>
     *
     * @param speciesId          identifier, echoed back so the caller can join results
     * @param commonName         display name
     * @param scientificName     botanical name
     * @param quantity           number of trees planned
     * @param co2KgPerTreeYear   kg CO₂ sequestered per tree per year
     * @param o2KgPerTreeYear    kg O₂ generated per tree per year
     * @param pm25GramsPerTreeYear  g PM2.5 removed per tree per year
     * @param pm10GramsPerTreeYear  g PM10 removed per tree per year
     * @param no2GramsPerTreeYear   g NO₂ removed per tree per year
     * @param so2GramsPerTreeYear   g SO₂ removed per tree per year
     * @param coGramsPerTreeYear    g CO removed per tree per year
     * @param growthRate         drives the maturity ramp
     * @param canopyRadiusMetres mature canopy radius, for π r²
     * @param spacingMetres      recommended spacing, for the feasibility assessment
     * @param sourceCitation     provenance, carried through to the report appendix
     */
    public record SpeciesInput(
            Long speciesId,
            String commonName,
            String scientificName,
            int quantity,
            BigDecimal co2KgPerTreeYear,
            BigDecimal o2KgPerTreeYear,
            BigDecimal pm25GramsPerTreeYear,
            BigDecimal pm10GramsPerTreeYear,
            BigDecimal no2GramsPerTreeYear,
            BigDecimal so2GramsPerTreeYear,
            BigDecimal coGramsPerTreeYear,
            GrowthRate growthRate,
            BigDecimal canopyRadiusMetres,
            BigDecimal spacingMetres,
            String sourceCitation
    ) {

        /** Total pollutant removal for one tree per year, in grams. */
        public BigDecimal totalPollutantGramsPerTreeYear() {
            return pm25GramsPerTreeYear
                    .add(pm10GramsPerTreeYear)
                    .add(no2GramsPerTreeYear)
                    .add(so2GramsPerTreeYear)
                    .add(coGramsPerTreeYear);
        }
    }
}
