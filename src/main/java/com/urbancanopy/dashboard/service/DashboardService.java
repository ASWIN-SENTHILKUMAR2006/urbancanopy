package com.urbancanopy.dashboard.service;

import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.common.util.Decimals;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.dashboard.dto.ChartData;
import com.urbancanopy.dashboard.dto.DashboardSummary;
import com.urbancanopy.plan.dto.PlanSummary;
import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.mapper.PlantationPlanMapper;
import com.urbancanopy.plan.repository.PlanItemRepository;
import com.urbancanopy.plan.repository.PlantationPlanRepository;
import com.urbancanopy.plan.service.PlanEstimateService;
import com.urbancanopy.species.repository.TreeSpeciesRepository;
import com.urbancanopy.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates a planner's plans into the dashboard's figures and charts
 * (FR-DASH-01 … FR-DASH-08).
 *
 * <h2>One basis for every figure</h2>
 * <p>Each plan is evaluated against the <strong>current</strong> coefficient registry, even
 * where a completed plan carries a frozen snapshot. Summing figures computed under different
 * constants would produce a total that is not a total of anything — the same reasoning the
 * comparison screen applies. An individual plan's own results screen continues to show its
 * frozen figures, which is where reproducibility matters.</p>
 *
 * <h2>Scope</h2>
 * <p>Every query is scoped to the caller in the query itself rather than filtered afterwards,
 * so there is no arrangement of parameters that leaks another planner's totals (FR-DASH-08).
 * The system-wide block is populated only for an administrator.</p>
 */
@Service
public class DashboardService {

    /** Species shown individually in the contribution chart before the tail is folded. */
    private static final int SPECIES_CHART_LIMIT = 8;

    /** Rows in the administrator's most-used-species table (FR-DASH-07). */
    private static final int TOP_SPECIES_LIMIT = 10;

    /** Plans listed as recent activity (FR-DASH-06). */
    private static final int RECENT_PLAN_LIMIT = 5;

    private final PlantationPlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final TreeSpeciesRepository speciesRepository;
    private final UserRepository userRepository;
    private final PlanEstimateService estimateService;
    private final PlantationPlanMapper planMapper;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationProperties properties;

    public DashboardService(PlantationPlanRepository planRepository,
                            PlanItemRepository planItemRepository,
                            TreeSpeciesRepository speciesRepository,
                            UserRepository userRepository,
                            PlanEstimateService estimateService,
                            PlantationPlanMapper planMapper,
                            CurrentUserProvider currentUserProvider,
                            ApplicationProperties properties) {
        this.planRepository = planRepository;
        this.planItemRepository = planItemRepository;
        this.speciesRepository = speciesRepository;
        this.userRepository = userRepository;
        this.estimateService = estimateService;
        this.planMapper = planMapper;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
    }

    // ------------------------------------------------------------------------- summary

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        List<Evaluated> evaluated = evaluateOwnPlans(caller.id());

        return new DashboardSummary(
                aggregate(evaluated),
                recentPlans(caller.id()),
                caller.admin() ? adminKpis() : null,
                properties.disclaimer());
    }

    private DashboardSummary.Kpis aggregate(List<Evaluated> evaluated) {
        long completed = evaluated.stream()
                .filter(row -> row.plan().getStatus() == PlanStatus.COMPLETED).count();
        long drafts = evaluated.stream()
                .filter(row -> row.plan().getStatus() == PlanStatus.DRAFT).count();

        BigDecimal co2Kg = BigDecimal.ZERO;
        BigDecimal o2Kg = BigDecimal.ZERO;
        BigDecimal pollutantGrams = BigDecimal.ZERO;
        BigDecimal canopy = BigDecimal.ZERO;
        BigDecimal scoreTotal = BigDecimal.ZERO;
        long trees = 0;
        int scored = 0;

        for (Evaluated row : evaluated) {
            CalculationResult result = row.result();
            trees += result.totalTrees();
            co2Kg = co2Kg.add(result.carbon().annualKg());
            o2Kg = o2Kg.add(result.oxygen().annualKg());
            pollutantGrams = pollutantGrams.add(result.pollutants().totalGrams());
            canopy = canopy.add(result.canopy().totalAreaSquareMetres());

            // Only plans with species carry a meaningful score; averaging in an empty draft's
            // zero would drag the figure down for a plan that has not been composed yet.
            if (result.sufficientData()) {
                scoreTotal = scoreTotal.add(result.impactScore().overall());
                scored++;
            }
        }

        return new DashboardSummary.Kpis(
                evaluated.size(),
                completed,
                drafts,
                trees,
                Decimals.present(Decimals.kilogramsToTonnes(co2Kg)),
                Decimals.present(o2Kg),
                Decimals.present(Decimals.gramsToKilograms(pollutantGrams)),
                Decimals.present(canopy),
                scored == 0 ? null
                        : Decimals.present(Decimals.divideOr(
                                scoreTotal, BigDecimal.valueOf(scored), BigDecimal.ZERO)));
    }

    private List<PlanSummary> recentPlans(Long ownerId) {
        return planRepository.findRecentForOwner(ownerId, PageRequest.of(0, RECENT_PLAN_LIMIT)).stream()
                // The listing query does not fetch items, and this row does not show them:
                // the counts are left at zero rather than triggering a query per plan.
                .map(plan -> planMapper.toSummary(plan, 0, plan.getTotalTrees(), null, null))
                .toList();
    }

    private DashboardSummary.AdminKpis adminKpis() {
        List<DashboardSummary.TopSpecies> top =
                planItemRepository.findMostUsedSpecies(PageRequest.of(0, TOP_SPECIES_LIMIT)).stream()
                        .map(row -> new DashboardSummary.TopSpecies(
                                (Long) row[0],
                                (String) row[1],
                                ((Number) row[2]).longValue(),
                                ((Number) row[3]).longValue()))
                        .toList();

        return new DashboardSummary.AdminKpis(
                userRepository.count(),
                userRepository.countByEnabledTrue(),
                speciesRepository.count(),
                speciesRepository.countByActiveTrue(),
                planRepository.countByDeletedFalse(),
                top);
    }

    // -------------------------------------------------------------------------- charts

    /**
     * One chart's data (FR-DASH-02 … FR-DASH-05).
     *
     * @param chartKey which chart; an unknown key is a 404 rather than an empty chart, so a
     *                 typo in the client surfaces as a mistake instead of as "no data"
     */
    @Transactional(readOnly = true)
    public ChartData chart(String chartKey) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        List<Evaluated> evaluated = evaluateOwnPlans(caller.id());

        return switch (chartKey) {
            case "co2-by-species" -> co2BySpecies(evaluated);
            case "pollutant-breakdown" -> pollutantBreakdown(evaluated);
            case "co2-projection" -> co2Projection(evaluated);
            case "plans-by-purpose" -> plansByPurpose(evaluated);
            default -> throw new ResourceNotFoundException("There is no chart called '" + chartKey + "'.");
        };
    }

    /**
     * CO₂ contribution per species across every plan (FR-DASH-02).
     *
     * <p>Ranked highest first and folded past {@value #SPECIES_CHART_LIMIT} entries. A planner
     * with twenty species would otherwise get twenty near-identical slivers; the tail is
     * summed into "Other" and the footnote says how many it covers, so the truncation is
     * stated rather than left to be inferred.</p>
     */
    private ChartData co2BySpecies(List<Evaluated> evaluated) {
        Map<String, BigDecimal> byScientificName = new LinkedHashMap<>();
        Map<String, String> commonNames = new LinkedHashMap<>();

        for (Evaluated row : evaluated) {
            for (CalculationResult.SpeciesContribution contribution : row.result().contributions()) {
                byScientificName.merge(
                        contribution.scientificName(), contribution.annualCo2Kg(), BigDecimal::add);
                commonNames.putIfAbsent(contribution.scientificName(), contribution.commonName());
            }
        }

        List<Map.Entry<String, BigDecimal>> ranked = new ArrayList<>(byScientificName.entrySet());
        ranked.sort(Map.Entry.<String, BigDecimal>comparingByValue().reversed());

        List<ChartData.Point> points = new ArrayList<>();
        String footnote = null;

        if (ranked.size() > SPECIES_CHART_LIMIT) {
            BigDecimal tail = ranked.subList(SPECIES_CHART_LIMIT, ranked.size()).stream()
                    .map(Map.Entry::getValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            ranked.subList(SPECIES_CHART_LIMIT, ranked.size()).clear();

            points.addAll(toSpeciesPoints(ranked, commonNames));
            points.add(ChartData.Point.of("Other", Decimals.present(Decimals.kilogramsToTonnes(tail))));
            footnote = "The %d smallest contributors are grouped as \"Other\"."
                    .formatted(byScientificName.size() - SPECIES_CHART_LIMIT);
        } else {
            points.addAll(toSpeciesPoints(ranked, commonNames));
        }

        return new ChartData(
                "co2-by-species",
                "CO₂ contribution by species",
                "Annual sequestration attributable to each species across all your plans.",
                "t/yr",
                "--uc-m-carbon",
                points,
                footnote);
    }

    private List<ChartData.Point> toSpeciesPoints(
            List<Map.Entry<String, BigDecimal>> ranked, Map<String, String> commonNames) {
        return ranked.stream()
                .map(entry -> new ChartData.Point(
                        commonNames.get(entry.getKey()),
                        entry.getKey(),
                        Decimals.present(Decimals.kilogramsToTonnes(entry.getValue()))))
                .toList();
    }

    /** Annual removal of each tracked pollutant, summed across plans (FR-DASH-03). */
    private ChartData pollutantBreakdown(List<Evaluated> evaluated) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();

        for (Evaluated row : evaluated) {
            for (CalculationResult.PollutantBreakdown breakdown : row.result().pollutants().breakdown()) {
                totals.merge(breakdown.label(), breakdown.grams(), BigDecimal::add);
            }
        }

        List<ChartData.Point> points = totals.entrySet().stream()
                .map(entry -> ChartData.Point.of(
                        entry.getKey(), Decimals.present(Decimals.gramsToKilograms(entry.getValue()))))
                .toList();

        return new ChartData(
                "pollutant-breakdown",
                "Pollutant removal",
                "Annual removal of each tracked pollutant, at maturity.",
                "kg/yr",
                "--uc-m-pm25",
                points,
                null);
    }

    /**
     * Cumulative CO₂ over the horizon, summed across plans (FR-DASH-04).
     *
     * <p>Aligned on calendar year rather than year-from-planting, because plans starting in
     * different years would otherwise be added together as though they began at once.</p>
     */
    private ChartData co2Projection(List<Evaluated> evaluated) {
        Map<Integer, BigDecimal> byCalendarYear = new java.util.TreeMap<>();

        for (Evaluated row : evaluated) {
            for (CalculationResult.ProjectionPoint point : row.result().projection()) {
                byCalendarYear.merge(point.calendarYear(), point.cumulativeCo2Kg(), BigDecimal::add);
            }
        }

        List<ChartData.Point> points = byCalendarYear.entrySet().stream()
                .map(entry -> ChartData.Point.of(
                        String.valueOf(entry.getKey()),
                        Decimals.present(Decimals.kilogramsToTonnes(entry.getValue()))))
                .toList();

        return new ChartData(
                "co2-projection",
                "Cumulative CO₂ projection",
                "Total carbon sequestered to the end of each year, as the plantings mature.",
                "t",
                "--uc-m-carbon",
                points,
                null);
    }

    /** How the planner's plans distribute across purposes (FR-DASH-05). */
    private ChartData plansByPurpose(List<Evaluated> evaluated) {
        Map<String, BigDecimal> counts = new LinkedHashMap<>();
        for (Evaluated row : evaluated) {
            counts.merge(row.plan().getPurpose().getLabel(), BigDecimal.ONE, BigDecimal::add);
        }

        List<ChartData.Point> points = counts.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> ChartData.Point.of(entry.getKey(), entry.getValue()))
                .toList();

        return new ChartData(
                "plans-by-purpose",
                "Plans by purpose",
                "What your plantings are intended to achieve.",
                "plans",
                "--uc-primary",
                points,
                null);
    }

    // ---------------------------------------------------------------------- internals

    /** Loads and evaluates every live plan the caller owns, in one fetch-joined query. */
    private List<Evaluated> evaluateOwnPlans(Long ownerId) {
        return planRepository.findDetailedForOwner(ownerId).stream()
                .sorted(Comparator.comparing(PlantationPlan::getId))
                .map(plan -> new Evaluated(plan, estimateService.computeFor(plan)))
                .toList();
    }

    /** A plan paired with its computed result, so neither is recomputed per chart section. */
    private record Evaluated(PlantationPlan plan, CalculationResult result) {}
}
