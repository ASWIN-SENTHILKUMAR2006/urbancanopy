package com.urbancanopy.plan.service;

import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.common.exception.ForbiddenOperationException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.common.util.Decimals;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.plan.dto.PlanComparisonResponse;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.mapper.PlantationPlanMapper;
import com.urbancanopy.plan.repository.PlantationPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Compares two to four plans side by side (FR-CMP-01 … FR-CMP-03).
 *
 * <h2>Why normalisation is not optional</h2>
 * <p>A 2,400-tree plan sequesters more carbon than a 480-tree plan. Always. So does a worse
 * plan that is merely bigger. A comparison built only from totals would therefore rank plans by
 * size while appearing to rank them by quality, and a planner using it to choose between two
 * designs would be actively misled. Every absolute metric is accompanied by a per-tree and a
 * per-100 m² intensity, and the "best" marker on a total means only "largest total".</p>
 *
 * <h2>Best-per-metric</h2>
 * <p>A winner is declared only when there is a real one. If every plan reports the same value,
 * or the metric does not apply, no plan is starred — a star among identical figures is noise
 * that reads as a finding.</p>
 */
@Service
public class PlanComparisonService {

    /** Intensity denominator for area-normalised metrics, in square metres. */
    private static final BigDecimal PER_AREA_UNIT = BigDecimal.valueOf(100);

    private final PlantationPlanRepository planRepository;
    private final PlanEstimateService planEstimateService;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationProperties properties;

    public PlanComparisonService(PlantationPlanRepository planRepository,
                                 PlanEstimateService planEstimateService,
                                 CurrentUserProvider currentUserProvider,
                                 ApplicationProperties properties) {
        this.planRepository = planRepository;
        this.planEstimateService = planEstimateService;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
    }

    /**
     * Compares the given plans, in the order requested.
     *
     * <p>Every plan is evaluated against the <em>live</em> coefficient registry, including
     * completed ones that have a snapshot. That is deliberate and it is the opposite of what
     * the single-plan estimate does: a comparison is only meaningful if all its columns were
     * computed the same way. Two plans completed six months apart, under different survival
     * rates, would otherwise be presented as comparable when they are not.</p>
     *
     * @param planIds two to four plan ids the caller owns
     */
    @Transactional(readOnly = true)
    public PlanComparisonResponse compare(List<Long> planIds) {
        List<Long> requestedIds = assertComparable(planIds);
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();

        List<PlantationPlan> loaded = planRepository.findDetailedByIds(requestedIds);

        // Preserve the caller's column order rather than the database's row order — the
        // interface's chips are in the order the planner picked them.
        List<PlantationPlan> plans = requestedIds.stream()
                .map(id -> loaded.stream()
                        .filter(plan -> id.equals(plan.getId()))
                        .findFirst()
                        .orElseThrow(() -> ResourceNotFoundException.of("Plantation plan", id)))
                .toList();

        for (PlantationPlan plan : plans) {
            if (!caller.canAccessDataOf(plan.getOwner().getId())) {
                throw new ForbiddenOperationException(
                        "A plan in this comparison is not yours to view.");
            }
        }

        List<CalculationResult> results = plans.stream()
                .map(planEstimateService::computeFor)
                .toList();

        return new PlanComparisonResponse(
                describeColumns(plans, results),
                List.of(totals(plans, results), intensities(plans, results), profile(plans, results)),
                properties.disclaimer());
    }

    // ----------------------------------------------------------------------- columns

    private List<PlanComparisonResponse.ComparedPlan> describeColumns(
            List<PlantationPlan> plans, List<CalculationResult> results) {

        List<PlanComparisonResponse.ComparedPlan> columns = new ArrayList<>(plans.size());
        for (int i = 0; i < plans.size(); i++) {
            PlantationPlan plan = plans.get(i);
            columns.add(new PlanComparisonResponse.ComparedPlan(
                    plan.getId(),
                    plan.getTitle(),
                    plan.getSiteName(),
                    PlantationPlanMapper.option(plan.getPurpose()),
                    PlantationPlanMapper.option(plan.getStatus()),
                    plan.getTotalTrees(),
                    plan.getLandAreaSquareMetres(),
                    plan.getProjectionYears(),
                    results.get(i).sufficientData()));
        }
        return columns;
    }

    // ------------------------------------------------------------------ metric groups

    /** Absolute figures — the totals the plans actually deliver (FR-CMP-02). */
    private PlanComparisonResponse.MetricGroup totals(
            List<PlantationPlan> plans, List<CalculationResult> results) {

        List<PlanComparisonResponse.Metric> metrics = List.of(
                metric("totalTrees", "Trees", "trees", true, plans, results,
                        (plan, result) -> BigDecimal.valueOf(result.totalTrees()), "--uc-primary"),
                metric("landArea", "Land area", "m²", true, plans, results,
                        (plan, result) -> plan.getLandAreaSquareMetres(), "--uc-ink-secondary"),
                metric("annualCo2Tonnes", "CO₂ sequestered", "t/yr", true, plans, results,
                        (plan, result) -> result.carbon().annualTonnes(), "--uc-m-carbon"),
                metric("horizonCo2Tonnes", "CO₂ over horizon", "t", true, plans, results,
                        (plan, result) -> result.carbon().horizonTonnes(), "--uc-m-carbon"),
                metric("annualO2Kg", "O₂ generated", "kg/yr", true, plans, results,
                        (plan, result) -> result.oxygen().annualKg(), "--uc-m-oxygen"),
                metric("annualPollutantKg", "Pollutants removed", "kg/yr", true, plans, results,
                        (plan, result) -> result.pollutants().totalKg(), "--uc-m-pm25"),
                metric("canopyAreaSqm", "Canopy at maturity", "m²", true, plans, results,
                        (plan, result) -> result.canopy().totalAreaSquareMetres(), "--uc-m-canopy"),
                metric("canopyCoveragePercent", "Canopy coverage", "%", true, plans, results,
                        (plan, result) -> result.canopy().coveragePercent(), "--uc-m-canopy"),
                metric("impactScore", "Impact score", "/100", true, plans, results,
                        (plan, result) -> result.impactScore().overall(), "--uc-primary"),
                metric("diversityIndex", "Diversity H′", "index", true, plans, results,
                        (plan, result) -> result.diversity().shannonIndex(), "--uc-accent"));

        return new PlanComparisonResponse.MetricGroup(
                "totals", "Totals",
                "What each plan delivers in absolute terms. Larger plans lead these rows by "
                        + "construction — read the normalised block to compare designs.",
                false, metrics);
    }

    /** Per-tree and per-area intensities — the rows that make sizes comparable (FR-CMP-03). */
    private PlanComparisonResponse.MetricGroup intensities(
            List<PlantationPlan> plans, List<CalculationResult> results) {

        List<PlanComparisonResponse.Metric> metrics = List.of(
                metric("co2PerTree", "CO₂ per tree", "kg/yr", true, plans, results,
                        (plan, result) -> perTree(result.carbon().annualKg(), result), "--uc-m-carbon"),
                metric("co2PerArea", "CO₂ per 100 m²", "kg/yr", true, plans, results,
                        (plan, result) -> perArea(result.carbon().annualKg(), plan), "--uc-m-carbon"),
                metric("o2PerTree", "O₂ per tree", "kg/yr", true, plans, results,
                        (plan, result) -> perTree(result.oxygen().annualKg(), result), "--uc-m-oxygen"),
                metric("pollutantPerTree", "Pollutants per tree", "g/yr", true, plans, results,
                        (plan, result) -> perTree(result.pollutants().totalGrams(), result), "--uc-m-pm25"),
                metric("canopyPerTree", "Canopy per tree", "m²", true, plans, results,
                        (plan, result) -> perTree(result.canopy().totalAreaSquareMetres(), result),
                        "--uc-m-canopy"),
                metric("treesPerArea", "Trees per 100 m²", "trees", true, plans, results,
                        (plan, result) -> perArea(BigDecimal.valueOf(result.totalTrees()), plan),
                        "--uc-primary"));

        return new PlanComparisonResponse.MetricGroup(
                "normalised", "Normalised",
                "Intensity per tree and per 100 m² of site, so a small dense planting and a large "
                        + "sparse one can be judged on the same terms.",
                true, metrics);
    }

    /**
     * The score's own sub-scores, plus space utilisation — the five radar axes (FR-CMP-04).
     *
     * <p>All five are already 0–100 and directionally consistent, which is what a radar chart
     * needs: axes on different scales produce a shape that means nothing.</p>
     */
    private PlanComparisonResponse.MetricGroup profile(
            List<PlantationPlan> plans, List<CalculationResult> results) {

        List<PlanComparisonResponse.Metric> metrics = List.of(
                metric("carbonSubScore", "Carbon", "/100", true, plans, results,
                        (plan, result) -> result.impactScore().carbonSubScore(), "--uc-m-carbon"),
                metric("airQualitySubScore", "Air quality", "/100", true, plans, results,
                        (plan, result) -> result.impactScore().airQualitySubScore(), "--uc-m-pm25"),
                metric("canopySubScore", "Canopy", "/100", true, plans, results,
                        (plan, result) -> result.impactScore().canopySubScore(), "--uc-m-canopy"),
                metric("diversitySubScore", "Diversity", "/100", true, plans, results,
                        (plan, result) -> result.impactScore().diversitySubScore(), "--uc-accent"),
                // Space is the one axis where more is not better without limit: 100 % utilised is
                // ideal and 180 % is overcrowded, so it is reported as-is and the client reads the
                // verdict alongside it rather than treating the maximum as the winner.
                metric("spaceUtilisationPercent", "Space utilised", "%", false, plans, results,
                        (plan, result) -> result.feasibility().utilisationPercent(),
                        "--uc-ink-secondary"));

        return new PlanComparisonResponse.MetricGroup(
                "profile", "Score profile",
                "The four weighted components of the impact score, with space utilisation as a "
                        + "fifth axis.",
                false, metrics);
    }

    // ---------------------------------------------------------------------- internals

    /**
     * Builds one row: extracts the value for every plan and decides which of them wins.
     *
     * @param extractor how to read this metric from a plan and its result — passed as a
     *                  function so that eighteen rows are eighteen one-line declarations
     *                  rather than eighteen near-identical blocks
     */
    private PlanComparisonResponse.Metric metric(
            String key, String label, String unit, boolean higherIsBetter,
            List<PlantationPlan> plans, List<CalculationResult> results,
            MetricExtractor extractor, String colourToken) {

        List<BigDecimal> values = new ArrayList<>(plans.size());
        for (int i = 0; i < plans.size(); i++) {
            BigDecimal value = extractor.extract(plans.get(i), results.get(i));
            values.add(value == null ? null : Decimals.present(value));
        }

        return new PlanComparisonResponse.Metric(
                key, label, unit, higherIsBetter, values,
                bestPlanId(plans, values, higherIsBetter), colourToken);
    }

    /**
     * The winning plan's id, or {@code null} when there is no meaningful winner.
     *
     * <p>Absent when fewer than two plans reported a value, or when every reported value is
     * identical.</p>
     */
    private Long bestPlanId(List<PlantationPlan> plans, List<BigDecimal> values, boolean higherIsBetter) {
        List<Integer> present = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) != null) {
                present.add(i);
            }
        }

        if (present.size() < 2) {
            return null;
        }

        boolean allEqual = present.stream()
                .map(values::get)
                .distinct()
                .count() == 1;
        if (allEqual) {
            return null;
        }

        Comparator<Integer> byValue = Comparator.comparing(values::get);
        return present.stream()
                .max(higherIsBetter ? byValue : byValue.reversed())
                .map(index -> plans.get(index).getId())
                .orElse(null);
    }

    /** Divides by effective trees, which is the count the figures were actually derived from. */
    private BigDecimal perTree(BigDecimal total, CalculationResult result) {
        return Decimals.divide(total, result.effectiveTrees()).orElse(null);
    }

    private BigDecimal perArea(BigDecimal total, PlantationPlan plan) {
        return Decimals.divide(total, plan.getLandAreaSquareMetres())
                .map(perSquareMetre -> perSquareMetre.multiply(PER_AREA_UNIT))
                .orElse(null);
    }

    /**
     * Validates the selection (FR-CMP-01, BR-14).
     *
     * <p>Duplicates are removed before counting, so asking to compare plan 7 with plan 7 is a
     * request to compare one plan and is refused as such rather than producing two identical
     * columns.</p>
     */
    private List<Long> assertComparable(List<Long> planIds) {
        int min = properties.plan().minPlansPerComparison();
        int max = properties.plan().maxPlansPerComparison();

        List<Long> distinct = planIds == null
                ? List.of()
                : planIds.stream().filter(Objects::nonNull).distinct().toList();

        if (distinct.size() < min || distinct.size() > max) {
            throw new BusinessRuleViolationException("FR-CMP-01",
                    "Choose between %d and %d different plans to compare; %d were selected."
                            .formatted(min, max, distinct.size()));
        }

        return distinct;
    }

    /**
     * Reads one metric out of a plan and its computed result.
     *
     * <p>Its own interface rather than a {@code BiFunction} purely for readability: eighteen
     * metric declarations read better against a name that says what the lambda is for.</p>
     */
    @FunctionalInterface
    private interface MetricExtractor {

        BigDecimal extract(PlantationPlan plan, CalculationResult result);
    }
}
