package com.urbancanopy.plan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.urbancanopy.calculation.EnvironmentalCalculationEngine;
import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.calculation.model.CoefficientSet;
import com.urbancanopy.coefficient.CoefficientKeys;
import com.urbancanopy.coefficient.service.CoefficientService;
import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.plan.dto.EstimateResponse;
import com.urbancanopy.plan.dto.PreviewEstimateRequest;
import com.urbancanopy.plan.entity.PlanResultSnapshot;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.mapper.PlantationPlanMapper;
import com.urbancanopy.plan.repository.PlanResultSnapshotRepository;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.species.mapper.TreeSpeciesMapper;
import com.urbancanopy.species.repository.TreeSpeciesRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs the calculation engine for a plan, for a hypothetical composition, and for the
 * snapshot that freezes a completed plan's figures.
 *
 * <p>Everything scientific happens in {@link EnvironmentalCalculationEngine}. What happens
 * here is the work the engine is deliberately incapable of: loading the plan, choosing
 * <em>which</em> coefficient set applies, and persisting the outcome. The boundary is worth
 * being precise about — steps 6 and 7 of the request flow touch no database, no Spring and no
 * HTTP, and that is what makes the formulae testable with a plain JUnit test.</p>
 *
 * <h2>Which coefficients apply</h2>
 * <p>A {@code COMPLETED} plan is evaluated against the coefficient set <strong>frozen at the
 * moment it was completed</strong>, read back from its snapshot; every other plan is evaluated
 * against the live registry. This is the whole point of passing {@link CoefficientSet} to the
 * engine as an argument rather than injecting it: an administrator revising the survival rate
 * changes what new estimates say without retroactively rewriting a figure already quoted in a
 * published report (FR-CALC-14, FR-ADM-05).</p>
 */
@Service
public class PlanEstimateService {

    private static final Logger log = LoggerFactory.getLogger(PlanEstimateService.class);

    /** Reads the frozen coefficient map back out of a snapshot's JSON column. */
    private static final TypeReference<LinkedHashMap<String, BigDecimal>> COEFFICIENT_MAP =
            new TypeReference<>() {};

    private final EnvironmentalCalculationEngine engine;
    private final CoefficientService coefficientService;
    private final PlanAccessService planAccessService;
    private final PlanResultSnapshotRepository snapshotRepository;
    private final TreeSpeciesRepository speciesRepository;
    private final PlantationPlanMapper planMapper;
    private final TreeSpeciesMapper speciesMapper;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationProperties properties;
    private final ObjectMapper objectMapper;

    public PlanEstimateService(EnvironmentalCalculationEngine engine,
                               CoefficientService coefficientService,
                               PlanAccessService planAccessService,
                               PlanResultSnapshotRepository snapshotRepository,
                               TreeSpeciesRepository speciesRepository,
                               PlantationPlanMapper planMapper,
                               TreeSpeciesMapper speciesMapper,
                               CurrentUserProvider currentUserProvider,
                               ApplicationProperties properties,
                               ObjectMapper objectMapper) {
        this.engine = engine;
        this.coefficientService = coefficientService;
        this.planAccessService = planAccessService;
        this.snapshotRepository = snapshotRepository;
        this.speciesRepository = speciesRepository;
        this.planMapper = planMapper;
        this.speciesMapper = speciesMapper;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------------- estimate

    /** The full result for a saved plan (UC-06). Readable by its owner or an administrator. */
    @Transactional(readOnly = true)
    public EstimateResponse estimate(Long planId) {
        PlantationPlan plan = planAccessService.requireReadable(planId, true);
        Optional<PlanResultSnapshot> snapshot = snapshotRepository.findByPlanId(planId);

        CoefficientSet coefficients = snapshot
                .map(this::frozenCoefficients)
                .orElseGet(coefficientService::activeSet);

        CalculationResult result = evaluate(plan, coefficients);

        return new EstimateResponse(
                plan.getId(),
                plan.getTitle(),
                plan.getSiteName(),
                PlantationPlanMapper.option(plan.getPurpose()),
                PlantationPlanMapper.option(plan.getStatus()),
                plan.getLandAreaInput(),
                PlantationPlanMapper.option(plan.getLandAreaUnit()),
                plan.getLandAreaSquareMetres(),
                plan.getTargetStartYear(),
                plan.getProjectionYears(),
                snapshot.isPresent(),
                snapshot.map(PlanResultSnapshot::getComputedAt).orElse(null),
                snapshot.map(PlanResultSnapshot::getEngineVersion)
                        .orElseGet(properties::engineVersion),
                Instant.now(),
                result);
    }

    /**
     * Evaluates a composition that has not been saved (FR-PLAN-13).
     *
     * <p>Stateless: nothing is written, no plan is touched, and the caller need not own
     * anything. It does require an identified caller, because the species catalogue's
     * selectable subset is an authenticated concern and because an unauthenticated,
     * unthrottled calculation endpoint is an invitation.</p>
     */
    @Transactional(readOnly = true)
    public EstimateResponse preview(PreviewEstimateRequest request) {
        currentUserProvider.requireCurrentUser();

        int projectionYears = clampProjectionYears(request.projectionYears());
        int startYear = request.targetStartYear() == null
                ? Year.now().getValue()
                : request.targetStartYear();

        BigDecimal landAreaSqm = request.landAreaUnit().toSquareMetres(request.landAreaInput());

        CalculationInput input = new CalculationInput(
                landAreaSqm, projectionYears, resolvePreviewItems(request.items()));

        CalculationResult result = engine.evaluate(
                input, coefficientService.activeSet(), startYear, properties.disclaimer());

        return new EstimateResponse(
                null, null, null, null, null,
                request.landAreaInput(),
                PlantationPlanMapper.option(request.landAreaUnit()),
                landAreaSqm,
                startYear,
                projectionYears,
                false,
                null,
                properties.engineVersion(),
                Instant.now(),
                result);
    }

    /**
     * Evaluates a plan against the live registry.
     *
     * <p>For callers that already hold the plan entity and have already authorised access —
     * the comparison and dashboard services. It performs no authorisation of its own, which is
     * why it takes an entity rather than an id: an id-taking method that skips the ownership
     * check is the kind of thing that gets called by mistake.</p>
     */
    public CalculationResult computeFor(PlantationPlan plan) {
        return evaluate(plan, coefficientService.activeSet());
    }

    // ------------------------------------------------------------------------ snapshot

    /**
     * Freezes a plan's result (FR-CALC-14).
     *
     * <p>Called when a plan becomes {@code COMPLETED}. Records not only the figures but the
     * exact coefficient values that produced them and the engine version that computed them,
     * so a result quoted in March still reconciles in December. Replaces any previous snapshot,
     * because a plan has at most one — the row describes "the completed state of this plan",
     * not a history of attempts.</p>
     *
     * @return the persisted snapshot
     */
    @Transactional
    public PlanResultSnapshot writeSnapshot(PlantationPlan plan) {
        CoefficientSet coefficients = coefficientService.activeSet();
        CalculationResult result = evaluate(plan, coefficients);

        PlanResultSnapshot snapshot = snapshotRepository.findByPlanId(plan.getId())
                .orElseGet(() -> new PlanResultSnapshot(plan));

        snapshot.setComputedAt(Instant.now());
        snapshot.setEngineVersion(properties.engineVersion());
        snapshot.setCoefficientSnapshot(serialise(coefficients));

        snapshot.setTotalTrees(result.totalTrees());
        snapshot.setEffectiveTrees(result.effectiveTrees());
        snapshot.setAnnualCo2Kg(result.carbon().annualKg());
        snapshot.setAnnualO2Kg(result.oxygen().annualKg());
        snapshot.setAnnualPm25Grams(result.pollutants().pm25Grams());
        snapshot.setAnnualPm10Grams(result.pollutants().pm10Grams());
        snapshot.setAnnualNo2Grams(result.pollutants().no2Grams());
        snapshot.setAnnualSo2Grams(result.pollutants().so2Grams());
        snapshot.setAnnualCoGrams(result.pollutants().coGrams());
        snapshot.setTotalPollutantGrams(result.pollutants().totalGrams());
        snapshot.setCanopyAreaSquareMetres(result.canopy().totalAreaSquareMetres());
        snapshot.setCanopyCoveragePercent(result.canopy().coveragePercent());
        snapshot.setRequiredAreaSquareMetres(result.feasibility().requiredAreaSquareMetres());
        snapshot.setSpaceUtilisationPercent(result.feasibility().utilisationPercent());
        snapshot.setFeasibility(result.feasibility().verdict());
        snapshot.setDiversityIndex(result.diversity().shannonIndex());
        snapshot.setImpactScore(result.impactScore().overall());
        snapshot.setHorizonCo2Kg(result.carbon().horizonKg());

        PlanResultSnapshot saved = snapshotRepository.save(snapshot);
        log.info("Result snapshot written for plan {} — {} kg CO₂/yr, score {}, engine {}",
                plan.getId(), result.carbon().annualKg(), result.impactScore().overall(),
                properties.engineVersion());
        return saved;
    }

    /**
     * Discards a plan's snapshot.
     *
     * <p>Called when a completed plan is reopened as a draft. The snapshot describes a
     * composition that is about to change, and a draft displaying frozen figures that no longer
     * match its own species list would be worse than displaying none. Re-completing writes a
     * fresh one.</p>
     */
    @Transactional
    public void discardSnapshot(Long planId) {
        if (snapshotRepository.existsByPlanId(planId)) {
            snapshotRepository.deleteByPlanId(planId);
            log.info("Result snapshot discarded for plan {} — reopened for editing", planId);
        }
    }

    // ---------------------------------------------------------------------- internals

    private CalculationResult evaluate(PlantationPlan plan, CoefficientSet coefficients) {
        return engine.evaluate(
                planMapper.toCalculationInput(plan),
                coefficients,
                plan.getTargetStartYear(),
                properties.disclaimer());
    }

    /**
     * Resolves the species named in a preview into engine inputs.
     *
     * <p>Loads every species in one query, then rejects unknown, inactive and duplicated ids.
     * Rejecting rather than skipping matters: an editor that silently drops a species the
     * planner just picked would show an estimate that does not match the list on screen.</p>
     */
    private List<CalculationInput.SpeciesInput> resolvePreviewItems(
            List<PreviewEstimateRequest.PreviewItem> items) {

        if (items == null || items.isEmpty()) {
            return List.of();
        }

        List<Long> requestedIds = items.stream()
                .map(PreviewEstimateRequest.PreviewItem::speciesId)
                .toList();

        if (requestedIds.stream().distinct().count() != requestedIds.size()) {
            throw new BusinessRuleViolationException("BR-08",
                    "A species may appear only once. Combine the duplicated rows into a single "
                            + "quantity.");
        }

        Map<Long, TreeSpecies> bySpeciesId = speciesRepository.findByIdIn(requestedIds).stream()
                .collect(Collectors.toMap(TreeSpecies::getId, Function.identity()));

        List<CalculationInput.SpeciesInput> inputs = new ArrayList<>(items.size());
        for (PreviewEstimateRequest.PreviewItem item : items) {
            TreeSpecies species = bySpeciesId.get(item.speciesId());

            if (species == null) {
                throw new BusinessRuleViolationException(
                        "One of the selected species no longer exists. Refresh the species list "
                                + "and try again.");
            }
            if (!species.isActive()) {
                throw new BusinessRuleViolationException(
                        "'%s' has been withdrawn from the catalogue and cannot be added to a new "
                                + "plan.".formatted(species.getCommonName()));
            }

            inputs.add(speciesMapper.toCalculationInput(species, item.quantity()));
        }
        return inputs;
    }

    /**
     * Reads a snapshot's frozen coefficients.
     *
     * <p>Falls back to the live registry when the stored JSON is unreadable or no longer covers
     * every key the engine requires — which can only happen if a coefficient is introduced
     * after a snapshot was taken. Reporting slightly-too-current figures is a far better
     * failure than a results screen that throws, and the fallback is logged so the cause is
     * visible rather than merely survived.</p>
     */
    private CoefficientSet frozenCoefficients(PlanResultSnapshot snapshot) {
        String json = snapshot.getCoefficientSnapshot();
        if (json == null || json.isBlank()) {
            return coefficientService.activeSet();
        }

        try {
            Map<String, BigDecimal> frozen = objectMapper.readValue(json, COEFFICIENT_MAP);

            for (String requiredKey : CoefficientKeys.REQUIRED_KEYS) {
                if (!frozen.containsKey(requiredKey)) {
                    log.warn("Snapshot for plan {} predates coefficient '{}'; evaluating against "
                                    + "the live registry instead",
                            snapshot.getPlan().getId(), requiredKey);
                    return coefficientService.activeSet();
                }
            }

            return CoefficientSet.of(frozen);

        } catch (JsonProcessingException e) {
            log.warn("Coefficient snapshot for plan {} could not be read ({}); evaluating against "
                    + "the live registry", snapshot.getPlan().getId(), e.getOriginalMessage());
            return coefficientService.activeSet();
        }
    }

    private String serialise(CoefficientSet coefficients) {
        try {
            return objectMapper.writeValueAsString(coefficients.asOrderedMap());
        } catch (JsonProcessingException e) {
            // A map of strings to decimals cannot fail to serialise; if it somehow does, the
            // snapshot must not be written half-formed and silently lose its provenance.
            throw new IllegalStateException("The coefficient snapshot could not be recorded.", e);
        }
    }

    private int clampProjectionYears(int requested) {
        ApplicationProperties.Plan limits = properties.plan();
        return Math.min(Math.max(requested, limits.minProjectionYears()), limits.maxProjectionYears());
    }
}
