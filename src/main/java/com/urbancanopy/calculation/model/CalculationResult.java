package com.urbancanopy.calculation.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * The complete outcome of evaluating a plantation plan.
 *
 * <p>Immutable and self-describing: every figure carries its unit in its field name, and
 * quantities that may be undefined — a coverage percentage when no land area was given —
 * are {@code null} rather than a misleading zero. A reader of this record can tell the
 * difference between "nothing was captured" and "the question does not apply", which is
 * exactly the distinction the interface must also make.</p>
 *
 * <p>Nested as records inside one file on purpose: these types have no meaning apart from
 * this result, and scattering fourteen two-line files across a package would obscure the
 * shape of what the engine actually returns.</p>
 *
 * @param sufficientData {@code false} when the plan had no species; every figure is then
 *                       zero and the caller should render an empty state, not a report
 * @param totalTrees     trees as planned, before survival and maturity adjustment
 * @param effectiveTrees mature-equivalent trees after adjustment — the basis of every rate
 * @param warnings       advisory messages such as a monoculture risk; never blocking
 * @param disclaimer     mandatory estimation disclaimer (FR-CALC-08)
 */
public record CalculationResult(
        boolean sufficientData,
        int totalTrees,
        BigDecimal effectiveTrees,
        Carbon carbon,
        Oxygen oxygen,
        Pollutants pollutants,
        Canopy canopy,
        Feasibility feasibility,
        Diversity diversity,
        ImpactScore impactScore,
        List<SpeciesContribution> contributions,
        List<ProjectionPoint> projection,
        List<Equivalence> equivalences,
        List<String> warnings,
        List<TraceEntry> trace,
        String disclaimer
) {

    /**
     * Annual and horizon carbon sequestration.
     *
     * @param annualKg      kg CO₂ per year once the planting is mature
     * @param annualTonnes  the same figure in tonnes, precomputed so no client divides by
     *                      1000 and rounds differently from another client
     * @param horizonKg     cumulative kg CO₂ over the plan's projection horizon
     * @param horizonTonnes cumulative tonnes over the horizon
     */
    public record Carbon(
            BigDecimal annualKg,
            BigDecimal annualTonnes,
            BigDecimal horizonKg,
            BigDecimal horizonTonnes
    ) {}

    /** Annual oxygen generation, in kilograms per year at maturity. */
    public record Oxygen(BigDecimal annualKg) {}

    /**
     * Annual removal of each tracked pollutant.
     *
     * @param breakdown per-pollutant figures in a fixed order, ready for a chart without
     *                  the client having to know which fields are pollutants
     */
    public record Pollutants(
            BigDecimal pm25Grams,
            BigDecimal pm10Grams,
            BigDecimal no2Grams,
            BigDecimal so2Grams,
            BigDecimal coGrams,
            BigDecimal totalGrams,
            BigDecimal totalKg,
            List<PollutantBreakdown> breakdown
    ) {}

    /**
     * One pollutant's contribution.
     *
     * @param code       stable identifier, e.g. {@code PM25}
     * @param label      display label, e.g. {@code PM2.5}
     * @param grams      annual removal in grams
     * @param kilograms  the same in kilograms
     * @param colourToken design-system token so the same pollutant is the same colour in
     *                    every chart, table and report
     */
    public record PollutantBreakdown(
            String code,
            String label,
            BigDecimal grams,
            BigDecimal kilograms,
            String colourToken
    ) {}

    /**
     * Canopy extent.
     *
     * @param totalAreaSquareMetres canopy area at maturity, Σ q·π·r²
     * @param coveragePercent       canopy as a share of available land, capped at 100 for
     *                              display; {@code null} when no land area was supplied
     * @param uncappedCoveragePercent the same before capping — a plan whose canopy is
     *                              180 % of its site is meaningfully different from one at
     *                              exactly 100 %, and the verdict depends on knowing that
     * @param landAreaSquareMetres  the land the coverage is measured against
     */
    public record Canopy(
            BigDecimal totalAreaSquareMetres,
            BigDecimal coveragePercent,
            BigDecimal uncappedCoveragePercent,
            BigDecimal landAreaSquareMetres
    ) {}

    /**
     * Whether the planting physically fits (FR-CALC-05).
     *
     * @param verdict                   four-way advisory assessment
     * @param requiredAreaSquareMetres  area the recommended spacing demands, Σ q·s²
     * @param availableAreaSquareMetres land supplied
     * @param utilisationPercent        required ÷ available × 100; {@code null} when no
     *                                  land area was supplied
     * @param surplusAreaSquareMetres   spare land; negative when overcrowded
     * @param maxPlantableTrees         how many trees this species mix would fit
     */
    public record Feasibility(
            FeasibilityVerdict verdict,
            BigDecimal requiredAreaSquareMetres,
            BigDecimal availableAreaSquareMetres,
            BigDecimal utilisationPercent,
            BigDecimal surplusAreaSquareMetres,
            Integer maxPlantableTrees
    ) {}

    /**
     * Species mix diversity (FR-CALC-11).
     *
     * @param shannonIndex        Shannon–Wiener H′; 0 for a single species
     * @param speciesCount        distinct species in the plan
     * @param dominantSpeciesName the most numerous species
     * @param dominantSharePercent its share of all trees
     * @param monocultureRisk     {@code true} when that share exceeds the configured
     *                            threshold — a planting concentrated in one species is
     *                            one pest away from losing its entire estimated benefit
     */
    public record Diversity(
            BigDecimal shannonIndex,
            int speciesCount,
            String dominantSpeciesName,
            BigDecimal dominantSharePercent,
            boolean monocultureRisk
    ) {}

    /**
     * Composite 0–100 environmental impact score (FR-CALC-10).
     *
     * <p>The sub-scores are exposed alongside the total so the number can be explained
     * rather than merely asserted. A score is only useful if a planner can see which part
     * of their plan is dragging it down.</p>
     */
    public record ImpactScore(
            BigDecimal overall,
            BigDecimal carbonSubScore,
            BigDecimal airQualitySubScore,
            BigDecimal canopySubScore,
            BigDecimal diversitySubScore,
            BigDecimal carbonWeight,
            BigDecimal airQualityWeight,
            BigDecimal canopyWeight,
            BigDecimal diversityWeight
    ) {}

    /**
     * One species' share of the whole result.
     *
     * @param effectiveTrees      quantity after survival and maturity adjustment
     * @param co2SharePercent     this species' share of the plan's total sequestration —
     *                            the figure that tells a planner where their carbon is
     *                            actually coming from
     * @param sourceCitation      provenance, carried through to the report appendix
     */
    public record SpeciesContribution(
            Long speciesId,
            String commonName,
            String scientificName,
            int quantity,
            BigDecimal effectiveTrees,
            BigDecimal annualCo2Kg,
            BigDecimal annualO2Kg,
            BigDecimal annualPm25Grams,
            BigDecimal annualPm10Grams,
            BigDecimal annualNo2Grams,
            BigDecimal annualSo2Grams,
            BigDecimal annualCoGrams,
            BigDecimal annualTotalPollutantGrams,
            BigDecimal canopyAreaSquareMetres,
            BigDecimal requiredAreaSquareMetres,
            BigDecimal co2SharePercent,
            BigDecimal quantitySharePercent,
            String sourceCitation
    ) {}

    /**
     * One year of the multi-year projection (FR-CALC-07).
     *
     * @param year            1-based year from planting
     * @param calendarYear    the actual calendar year, so a chart axis reads 2026 not 1
     * @param annualCo2Kg     sequestration in that year, reduced by immaturity
     * @param cumulativeCo2Kg running total to the end of that year
     * @param averageMaturity mean maturity factor across the mix that year, 0–1
     */
    public record ProjectionPoint(
            int year,
            int calendarYear,
            BigDecimal annualCo2Kg,
            BigDecimal cumulativeCo2Kg,
            BigDecimal averageMaturity
    ) {}

    /**
     * A relatable comparison (FR-CALC-09).
     *
     * <p>Generated entirely from coefficient rows, so a new equivalence is a database
     * insert and no code change.</p>
     */
    public record Equivalence(
            String key,
            String label,
            BigDecimal value,
            String unit,
            String description
    ) {}

    /**
     * One line of the "how was this calculated?" explanation (FR-CALC-15).
     *
     * @param label      what is being explained, e.g. {@code CO₂ · Neem}
     * @param expression the arithmetic, written out with the actual numbers substituted
     * @param result     the outcome, with its unit
     */
    public record TraceEntry(String label, String expression, String result) {}

    // ------------------------------------------------------------------ factories

    /**
     * The result for a plan with no species (UC-06 alternate flow A1).
     *
     * <p>Returned rather than thrown, because an empty plan is a perfectly ordinary state
     * for a draft — the caller renders an empty state and the user carries on working.</p>
     */
    public static CalculationResult insufficientData(String disclaimer) {
        BigDecimal zero = BigDecimal.ZERO;
        return new CalculationResult(
                false, 0, zero,
                new Carbon(zero, zero, zero, zero),
                new Oxygen(zero),
                new Pollutants(zero, zero, zero, zero, zero, zero, zero, List.of()),
                new Canopy(zero, null, null, zero),
                new Feasibility(FeasibilityVerdict.NOT_APPLICABLE, zero, zero, null, null, null),
                new Diversity(zero, 0, null, null, false),
                new ImpactScore(zero, zero, zero, zero, zero, zero, zero, zero, zero),
                List.of(), List.of(), List.of(),
                List.of("Add at least one species to see an environmental estimate."),
                List.of(),
                disclaimer);
    }
}
