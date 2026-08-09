package com.urbancanopy.calculation;

import com.urbancanopy.calculation.component.DiversityCalculator;
import com.urbancanopy.calculation.component.EquivalenceConverter;
import com.urbancanopy.calculation.component.FeasibilityEvaluator;
import com.urbancanopy.calculation.component.ImpactScorer;
import com.urbancanopy.calculation.component.ProjectionCalculator;
import com.urbancanopy.calculation.component.SpeciesContributionCalculator;
import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.common.util.Decimals;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The scientific core of UrbanCanopy: turns a plantation composition into a complete set
 * of environmental estimates.
 *
 * <h2>What this class is</h2>
 * <p>A <strong>pure function</strong>. It takes a {@link CalculationInput} and a
 * {@link CoefficientSet} and returns a {@link CalculationResult}. It reads no database,
 * writes nothing, holds no state, starts no transaction, and touches no HTTP. The same
 * inputs produce the same outputs, today and in ten years (FR-CALC-12).</p>
 *
 * <p>That constraint is not decoration. It is what allows:</p>
 * <ul>
 *   <li>the live editor to estimate an unsaved plan on every keystroke without writing a
 *       row ({@code POST /api/v1/estimates/preview});</li>
 *   <li>a historical report to be reproduced exactly by passing the coefficient set stored
 *       in its snapshot;</li>
 *   <li>the whole of the project's scientific logic to be tested with plain JUnit, with no
 *       Spring context and no database.</li>
 * </ul>
 *
 * <h2>What this class is not</h2>
 * <p>Not a model, not a predictor, not an inference of any kind. Every figure it produces
 * is a multiplication of a user-supplied quantity by a curated reference value, adjusted
 * by constants an administrator can inspect and change. Any figure can be re-derived by
 * hand from the {@link CalculationResult#trace()} it returns.</p>
 *
 * <h2>Order of operations</h2>
 * <ol>
 *   <li>Evaluate each species independently — quantities × per-tree rates, adjusted for
 *       survival, maturity and site condition.</li>
 *   <li>Aggregate those contributions into plan totals.</li>
 *   <li>Derive spatial metrics — canopy area, coverage, feasibility.</li>
 *   <li>Derive composition metrics — diversity, monoculture risk.</li>
 *   <li>Project year by year across the horizon.</li>
 *   <li>Score, convert to equivalences, and record the trace.</li>
 * </ol>
 *
 * @see com.urbancanopy.calculation.component.EffectiveTreeCalculator the survival and
 *      maturity adjustment that makes these figures defensible
 */
@Service
public class EnvironmentalCalculationEngine {

    /*
     * EffectiveTreeCalculator is deliberately absent from this list. The survival and
     * maturity adjustment is applied inside SpeciesContributionCalculator and
     * ProjectionCalculator, which own it; injecting it here as well would imply the
     * orchestrator adjusts quantities itself, and would leave two places where the
     * adjustment could be applied — or forgotten.
     */
    private final SpeciesContributionCalculator contributionCalculator;
    private final FeasibilityEvaluator feasibilityEvaluator;
    private final DiversityCalculator diversityCalculator;
    private final ProjectionCalculator projectionCalculator;
    private final ImpactScorer impactScorer;
    private final EquivalenceConverter equivalenceConverter;

    public EnvironmentalCalculationEngine(
            SpeciesContributionCalculator contributionCalculator,
            FeasibilityEvaluator feasibilityEvaluator,
            DiversityCalculator diversityCalculator,
            ProjectionCalculator projectionCalculator,
            ImpactScorer impactScorer,
            EquivalenceConverter equivalenceConverter) {
        this.contributionCalculator = contributionCalculator;
        this.feasibilityEvaluator = feasibilityEvaluator;
        this.diversityCalculator = diversityCalculator;
        this.projectionCalculator = projectionCalculator;
        this.impactScorer = impactScorer;
        this.equivalenceConverter = equivalenceConverter;
    }

    /**
     * Evaluates a plantation plan.
     *
     * @param input        the composition, land area and horizon
     * @param coefficients the constants in force
     * @param startYear    calendar year of planting, for the projection axis
     * @param disclaimer   mandatory estimation disclaimer, carried into the result so no
     *                     caller can render figures without it (FR-CALC-08)
     * @return a complete, immutable result; never {@code null}, never throwing for an
     *         empty or land-less plan
     */
    public CalculationResult evaluate(
            CalculationInput input, CoefficientSet coefficients, int startYear, String disclaimer) {

        // An empty plan is an ordinary state for a draft, not an error. The caller renders
        // an empty state and the user carries on working (UC-06 alternate flow A1).
        if (!input.hasSpecies()) {
            return CalculationResult.insufficientData(disclaimer);
        }

        // ---- 1. per-species contributions -------------------------------------------
        List<CalculationResult.SpeciesContribution> rawContributions = input.items().stream()
                .map(species -> contributionCalculator.evaluate(species, coefficients))
                .toList();

        // ---- 2. plan totals ----------------------------------------------------------
        Totals totals = Totals.from(rawContributions);
        int totalTrees = input.totalTrees();

        List<CalculationResult.SpeciesContribution> contributions =
                withShares(rawContributions, totals.co2Kg(), totalTrees);

        // ---- 3. spatial metrics ------------------------------------------------------
        CalculationResult.Canopy canopy = evaluateCanopy(
                totals.canopyAreaSqm(), input.landAreaSquareMetres());

        CalculationResult.Feasibility feasibility = feasibilityEvaluator.evaluate(input, coefficients);

        // ---- 4. composition metrics --------------------------------------------------
        CalculationResult.Diversity diversity = diversityCalculator.evaluate(input, coefficients);

        // ---- 5. projection -----------------------------------------------------------
        List<CalculationResult.ProjectionPoint> projection =
                projectionCalculator.project(input, startYear, coefficients);
        BigDecimal horizonCo2Kg = projectionCalculator.horizonTotal(projection);

        // ---- 6. score, equivalences, trace -------------------------------------------
        CalculationResult.ImpactScore impactScore = impactScorer.score(
                totals.co2Kg(), totals.totalPollutantGrams(), totalTrees,
                canopy.coveragePercent(), diversity.shannonIndex(), coefficients);

        List<CalculationResult.Equivalence> equivalences =
                equivalenceConverter.convert(totals.co2Kg(), totals.o2Kg(), coefficients);

        return new CalculationResult(
                true,
                totalTrees,
                Decimals.scaled(totals.effectiveTrees(), 2),
                carbon(totals.co2Kg(), horizonCo2Kg),
                new CalculationResult.Oxygen(scale3(totals.o2Kg())),
                pollutants(totals),
                canopy,
                feasibility,
                diversity,
                impactScore,
                contributions,
                projection,
                equivalences,
                buildWarnings(diversity, feasibility),
                buildTrace(input, contributions, coefficients),
                disclaimer);
    }

    // ------------------------------------------------------------------ aggregation

    /**
     * Plan-level sums, accumulated in one pass over the per-species contributions.
     *
     * <p>A local record rather than nine loose variables: the totals travel together
     * through four subsequent steps, and passing them as a unit removes any chance of a
     * mismatched pair.</p>
     */
    private record Totals(
            BigDecimal effectiveTrees,
            BigDecimal co2Kg,
            BigDecimal o2Kg,
            BigDecimal pm25Grams,
            BigDecimal pm10Grams,
            BigDecimal no2Grams,
            BigDecimal so2Grams,
            BigDecimal coGrams,
            BigDecimal canopyAreaSqm
    ) {

        static Totals from(List<CalculationResult.SpeciesContribution> contributions) {
            BigDecimal effectiveTrees = BigDecimal.ZERO;
            BigDecimal co2 = BigDecimal.ZERO;
            BigDecimal o2 = BigDecimal.ZERO;
            BigDecimal pm25 = BigDecimal.ZERO;
            BigDecimal pm10 = BigDecimal.ZERO;
            BigDecimal no2 = BigDecimal.ZERO;
            BigDecimal so2 = BigDecimal.ZERO;
            BigDecimal co = BigDecimal.ZERO;
            BigDecimal canopy = BigDecimal.ZERO;

            for (CalculationResult.SpeciesContribution c : contributions) {
                effectiveTrees = effectiveTrees.add(c.effectiveTrees());
                co2 = co2.add(c.annualCo2Kg());
                o2 = o2.add(c.annualO2Kg());
                pm25 = pm25.add(c.annualPm25Grams());
                pm10 = pm10.add(c.annualPm10Grams());
                no2 = no2.add(c.annualNo2Grams());
                so2 = so2.add(c.annualSo2Grams());
                co = co.add(c.annualCoGrams());
                canopy = canopy.add(c.canopyAreaSquareMetres());
            }
            return new Totals(effectiveTrees, co2, o2, pm25, pm10, no2, so2, co, canopy);
        }

        BigDecimal totalPollutantGrams() {
            return pm25Grams.add(pm10Grams).add(no2Grams).add(so2Grams).add(coGrams);
        }
    }

    /** Fills in each species' share of the plan, now that plan totals are known. */
    private List<CalculationResult.SpeciesContribution> withShares(
            List<CalculationResult.SpeciesContribution> contributions,
            BigDecimal totalCo2Kg,
            int totalTrees) {

        List<CalculationResult.SpeciesContribution> result = new ArrayList<>(contributions.size());
        for (CalculationResult.SpeciesContribution c : contributions) {
            BigDecimal co2Share = Decimals.percentage(c.annualCo2Kg(), totalCo2Kg)
                    .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            BigDecimal quantityShare = Decimals
                    .percentage(BigDecimal.valueOf(c.quantity()), BigDecimal.valueOf(totalTrees))
                    .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

            result.add(new CalculationResult.SpeciesContribution(
                    c.speciesId(), c.commonName(), c.scientificName(), c.quantity(),
                    c.effectiveTrees(), c.annualCo2Kg(), c.annualO2Kg(),
                    c.annualPm25Grams(), c.annualPm10Grams(), c.annualNo2Grams(),
                    c.annualSo2Grams(), c.annualCoGrams(), c.annualTotalPollutantGrams(),
                    c.canopyAreaSquareMetres(), c.requiredAreaSquareMetres(),
                    co2Share, quantityShare, c.sourceCitation()));
        }
        return List.copyOf(result);
    }

    private CalculationResult.Carbon carbon(BigDecimal annualKg, BigDecimal horizonKg) {
        return new CalculationResult.Carbon(
                scale3(annualKg),
                scale3(Decimals.kilogramsToTonnes(annualKg)),
                scale3(horizonKg),
                scale3(Decimals.kilogramsToTonnes(horizonKg)));
    }

    private CalculationResult.Pollutants pollutants(Totals totals) {
        BigDecimal total = totals.totalPollutantGrams();
        List<CalculationResult.PollutantBreakdown> breakdown = List.of(
                breakdown("PM25", "PM2.5", totals.pm25Grams(), "--uc-m-pm25"),
                breakdown("PM10", "PM10", totals.pm10Grams(), "--uc-m-pm10"),
                breakdown("NO2", "NO₂", totals.no2Grams(), "--uc-m-no2"),
                breakdown("SO2", "SO₂", totals.so2Grams(), "--uc-m-so2"),
                breakdown("CO", "CO", totals.coGrams(), "--uc-m-co"));

        return new CalculationResult.Pollutants(
                scale3(totals.pm25Grams()), scale3(totals.pm10Grams()), scale3(totals.no2Grams()),
                scale3(totals.so2Grams()), scale3(totals.coGrams()),
                scale3(total), scale3(Decimals.gramsToKilograms(total)),
                breakdown);
    }

    private CalculationResult.PollutantBreakdown breakdown(
            String code, String label, BigDecimal grams, String colourToken) {
        return new CalculationResult.PollutantBreakdown(
                code, label, scale3(grams), scale3(Decimals.gramsToKilograms(grams)), colourToken);
    }

    /**
     * Canopy extent and coverage.
     *
     * <p>Coverage is reported twice: capped at 100 % for display, and uncapped for the
     * feasibility narrative. A plan whose canopy would be 180 % of its site is genuinely
     * different from one at exactly 100 %, and flattening both to "100 %" would hide the
     * problem the planner most needs to see (BR-12).</p>
     */
    private CalculationResult.Canopy evaluateCanopy(BigDecimal canopyAreaSqm, BigDecimal landAreaSqm) {
        BigDecimal area = Decimals.scaled(canopyAreaSqm, 2);

        if (landAreaSqm == null || landAreaSqm.signum() <= 0) {
            return new CalculationResult.Canopy(area, null, null, BigDecimal.ZERO);
        }

        BigDecimal uncapped = Decimals.percentage(area, landAreaSqm)
                .orElse(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal capped = Decimals.clamp(uncapped, BigDecimal.ZERO, BigDecimal.valueOf(100));

        return new CalculationResult.Canopy(
                area, capped, uncapped, Decimals.scaled(landAreaSqm, 2));
    }

    // ---------------------------------------------------------------------- advisory

    /**
     * Builds advisory messages. These never block an action (BR-13); they tell a planner
     * what a reviewer would tell them.
     */
    private List<String> buildWarnings(
            CalculationResult.Diversity diversity, CalculationResult.Feasibility feasibility) {

        List<String> warnings = new ArrayList<>(2);

        if (diversity.monocultureRisk() && diversity.dominantSpeciesName() != null) {
            warnings.add(("%s makes up %s%% of this plan. A planting concentrated in one species "
                    + "is vulnerable to a single pest or disease — consider adding more species.")
                    .formatted(diversity.dominantSpeciesName(), diversity.dominantSharePercent()));
        }

        switch (feasibility.verdict()) {
            case OVERCROWDED -> warnings.add((
                    "This planting needs %s m² at the recommended spacing but only %s m² is "
                            + "available. At most %s trees fit this site at your current species mix.")
                    .formatted(feasibility.requiredAreaSquareMetres(),
                            feasibility.availableAreaSquareMetres(),
                            feasibility.maxPlantableTrees()));
            case UNDER_UTILISED -> warnings.add((
                    "Only %s%% of the available land is used. The site could support more trees "
                            + "or a wider species mix.")
                    .formatted(feasibility.utilisationPercent()));
            default -> { /* OPTIMAL, TIGHT and NOT_APPLICABLE need no warning. */ }
        }

        return List.copyOf(warnings);
    }

    /**
     * Builds the "how was this calculated?" explanation (FR-CALC-15).
     *
     * <p>Written out with the actual numbers substituted, so a reader can check the
     * arithmetic by hand. This is the most direct answer available to the question every
     * reviewer of an environmental estimate should ask: where did that number come from?</p>
     */
    private List<CalculationResult.TraceEntry> buildTrace(
            CalculationInput input,
            List<CalculationResult.SpeciesContribution> contributions,
            CoefficientSet coefficients) {

        List<CalculationResult.TraceEntry> trace = new ArrayList<>();

        BigDecimal survivalRate = coefficients.require(CoefficientKeys.SURVIVAL_RATE);
        BigDecimal siteFactor = coefficients.require(CoefficientKeys.SITE_CONDITION_FACTOR);

        trace.add(new CalculationResult.TraceEntry(
                "Adjustment factors applied to every species",
                "effective trees = planted × survival rate (%s) × site condition factor (%s) × maturity factor"
                        .formatted(plain(survivalRate), plain(siteFactor)),
                "Headline figures use a maturity factor of 1.00, i.e. performance at maturity."));

        for (int i = 0; i < input.items().size(); i++) {
            CalculationInput.SpeciesInput species = input.items().get(i);
            CalculationResult.SpeciesContribution contribution = contributions.get(i);

            trace.add(new CalculationResult.TraceEntry(
                    "CO₂ · " + species.commonName(),
                    "%d planted × %s survival × %s site = %s effective trees;  %s × %s kg/tree/yr"
                            .formatted(species.quantity(), plain(survivalRate), plain(siteFactor),
                                    plain(contribution.effectiveTrees()),
                                    plain(contribution.effectiveTrees()),
                                    plain(species.co2KgPerTreeYear())),
                    plain(contribution.annualCo2Kg()) + " kg CO₂/year"));

            trace.add(new CalculationResult.TraceEntry(
                    "Canopy · " + species.commonName(),
                    "%d trees × π × (%s m)² — canopy uses planted count, not survival-adjusted, "
                            .formatted(species.quantity(), plain(species.canopyRadiusMetres()))
                            + "because footprint is a physical question",
                    plain(contribution.canopyAreaSquareMetres()) + " m²"));

            trace.add(new CalculationResult.TraceEntry(
                    "Space required · " + species.commonName(),
                    "%d trees × (%s m spacing)²"
                            .formatted(species.quantity(), plain(species.spacingMetres())),
                    plain(contribution.requiredAreaSquareMetres()) + " m²"));

            if (species.sourceCitation() != null) {
                trace.add(new CalculationResult.TraceEntry(
                        "Source · " + species.commonName(), species.sourceCitation(), ""));
            }
        }

        return List.copyOf(trace);
    }

    // ----------------------------------------------------------------------- helpers

    private static BigDecimal scale3(BigDecimal value) {
        return Decimals.scaled(value, 3);
    }

    /** Formats a decimal without exponent or trailing zeros, for trace text. */
    private static String plain(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
    }
}
