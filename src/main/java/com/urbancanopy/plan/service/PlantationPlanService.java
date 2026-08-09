package com.urbancanopy.plan.service;

import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.common.exception.BusinessRuleViolationException;
import com.urbancanopy.common.exception.DuplicateResourceException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.plan.dto.PlanDetailResponse;
import com.urbancanopy.plan.dto.PlanItemRequest;
import com.urbancanopy.plan.dto.PlanSummary;
import com.urbancanopy.plan.dto.PlantationPlanRequest;
import com.urbancanopy.plan.entity.LandAreaUnit;
import com.urbancanopy.plan.entity.PlanItem;
import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.entity.PlantationPurpose;
import com.urbancanopy.plan.mapper.PlantationPlanMapper;
import com.urbancanopy.plan.repository.PlanItemRepository;
import com.urbancanopy.plan.repository.PlanResultSnapshotRepository;
import com.urbancanopy.plan.repository.PlantationPlanRepository;
import com.urbancanopy.species.entity.TreeSpecies;
import com.urbancanopy.species.repository.TreeSpeciesRepository;
import com.urbancanopy.user.entity.User;
import com.urbancanopy.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.joining;

/**
 * The plantation-planning use cases (FR-PLAN-01 … FR-PLAN-13).
 *
 * <p>This is where the domain rules that the entity cannot enforce alone are applied:
 * per-owner title uniqueness, the configured planning limits, the lifecycle state machine, and
 * the decision to freeze a result when a plan is completed. Ownership is delegated to
 * {@link PlanAccessService} so that no method here can accidentally omit it.</p>
 *
 * <p>Rules that <em>are</em> the entity's own — normalising land area to m², incrementing an
 * existing species rather than duplicating it — are left on {@link PlantationPlan}, where they
 * hold for every caller rather than only for the ones that remember.</p>
 */
@Service
public class PlantationPlanService {

    private static final Logger log = LoggerFactory.getLogger(PlantationPlanService.class);

    private static final String ITEM_RESOURCE = "Plan item";

    /** Attempts made to find a free "(copy N)" title before giving up. */
    private static final int MAX_COPY_ATTEMPTS = 50;

    private final PlantationPlanRepository planRepository;
    private final PlanItemRepository planItemRepository;
    private final PlanResultSnapshotRepository snapshotRepository;
    private final TreeSpeciesRepository speciesRepository;
    private final UserRepository userRepository;
    private final PlanAccessService planAccessService;
    private final PlanEstimateService planEstimateService;
    private final PlantationPlanMapper mapper;
    private final CurrentUserProvider currentUserProvider;
    private final ApplicationProperties properties;

    public PlantationPlanService(PlantationPlanRepository planRepository,
                                 PlanItemRepository planItemRepository,
                                 PlanResultSnapshotRepository snapshotRepository,
                                 TreeSpeciesRepository speciesRepository,
                                 UserRepository userRepository,
                                 PlanAccessService planAccessService,
                                 PlanEstimateService planEstimateService,
                                 PlantationPlanMapper mapper,
                                 CurrentUserProvider currentUserProvider,
                                 ApplicationProperties properties) {
        this.planRepository = planRepository;
        this.planItemRepository = planItemRepository;
        this.snapshotRepository = snapshotRepository;
        this.speciesRepository = speciesRepository;
        this.userRepository = userRepository;
        this.planAccessService = planAccessService;
        this.planEstimateService = planEstimateService;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
    }

    // ---------------------------------------------------------------------- queries

    /**
     * The caller's own plans, paginated and filterable (FR-PLAN-07).
     *
     * <p>Scoped to the caller in the query itself rather than filtered afterwards, so there is
     * no arrangement of parameters that returns another planner's row.</p>
     */
    @Transactional(readOnly = true)
    public PageResponse<PlanSummary> searchOwn(String search, PlanStatus status,
                                               PlantationPurpose purpose, Pageable pageable) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();

        Page<PlantationPlan> page = planRepository.searchForOwner(
                caller.id(), StringUtils.hasText(search) ? search.trim() : null,
                status, purpose, pageable);

        return summarise(page);
    }

    /** Every plan, for administrator support and moderation (FR-PLAN-11). */
    @Transactional(readOnly = true)
    public PageResponse<PlanSummary> searchAll(String search, PlanStatus status, Pageable pageable) {
        currentUserProvider.assertAdmin();

        Page<PlantationPlan> page = planRepository.searchAll(
                StringUtils.hasText(search) ? search.trim() : null, status, pageable);

        return summarise(page);
    }

    /** One plan with its composition. Readable by its owner, or read-only by an administrator. */
    @Transactional(readOnly = true)
    public PlanDetailResponse findById(Long planId) {
        PlantationPlan plan = planAccessService.requireReadable(planId, true);
        return mapper.toDetail(plan, isOwnedByCaller(plan));
    }

    /**
     * Every enumerated value the plan editor's selects need.
     *
     * <p>Served from the enums so that adding a plantation purpose updates the form without a
     * second, unrelated file having to be remembered.</p>
     */
    public Map<String, List<EnumOption>> editorOptions() {
        Map<String, List<EnumOption>> options = new LinkedHashMap<>();
        options.put("purpose", EnumOption.from(
                PlantationPurpose.class, PlantationPurpose::getLabel, PlantationPurpose::getDescription));
        options.put("landAreaUnit", EnumOption.from(
                LandAreaUnit.class, LandAreaUnit::getLabel, LandAreaUnit::getSymbol));
        options.put("status", EnumOption.from(PlanStatus.class, PlanStatus::getLabel));
        return options;
    }

    // ----------------------------------------------------------------- plan lifecycle

    /** Creates a plan as a {@code DRAFT} (FR-PLAN-01, FR-PLAN-05). */
    @Transactional
    public PlanDetailResponse create(PlantationPlanRequest request) {
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        User owner = userRepository.findById(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));

        String title = request.title().trim();
        assertTitleAvailable(caller.id(), title, null);
        assertProjectionHorizonPermitted(request.projectionYears());

        PlantationPlan plan = new PlantationPlan(
                owner, title, request.landAreaInput(), request.landAreaUnit(),
                request.purpose(), request.targetStartYear(), request.projectionYears());
        plan.setDescription(trimToNull(request.description()));
        plan.setSiteName(trimToNull(request.siteName()));

        PlantationPlan saved = planRepository.save(plan);
        log.info("Plan created: '{}' (id={}) for user {}", title, saved.getId(), caller.id());

        return mapper.toDetail(saved, true);
    }

    /**
     * Updates a plan's header (FR-PLAN-01).
     *
     * <p>Changing the land area or the horizon changes every figure the plan reports. That is
     * intended — but a {@code COMPLETED} plan's snapshot would then describe a plan that no
     * longer exists, so the plan must be reopened as a draft before its header can move.</p>
     */
    @Transactional
    public PlanDetailResponse update(Long planId, PlantationPlanRequest request) {
        PlantationPlan plan = planAccessService.requireEditable(planId, true);

        String title = request.title().trim();
        assertTitleAvailable(plan.getOwner().getId(), title, planId);
        assertProjectionHorizonPermitted(request.projectionYears());

        plan.setTitle(title);
        plan.setDescription(trimToNull(request.description()));
        plan.setSiteName(trimToNull(request.siteName()));
        plan.setLandArea(request.landAreaInput(), request.landAreaUnit());
        plan.setPurpose(request.purpose());
        plan.setTargetStartYear(request.targetStartYear());
        plan.setProjectionYears(request.projectionYears());

        PlantationPlan saved = planRepository.save(plan);
        return mapper.toDetail(saved, true);
    }

    /**
     * Soft-deletes a plan (FR-PLAN-09, BR-21).
     *
     * <p>The row and its snapshot stay. A plantation plan is a document someone wrote, and
     * "delete" in this interface means "remove from my list", not "destroy the record of a
     * decision". Every listing and aggregate filters on {@code deleted = FALSE}, so the effect
     * is indistinguishable from removal for the user who asked for it.</p>
     */
    @Transactional
    public void delete(Long planId) {
        PlantationPlan plan = planAccessService.requireOwned(planId, false);
        plan.markDeleted();
        planRepository.save(plan);
        log.info("Plan {} soft-deleted by its owner", planId);
    }

    /**
     * Copies a plan as a fresh {@code DRAFT} (FR-PLAN-08).
     *
     * <p>The point of this endpoint is exploring an alternative species mix without losing the
     * original, so the copy takes the composition but never the status or the result snapshot:
     * a duplicate has computed nothing yet.</p>
     */
    @Transactional
    public PlanDetailResponse duplicate(Long planId) {
        PlantationPlan source = planAccessService.requireReadable(planId, true);
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();
        User owner = userRepository.findById(caller.id())
                .orElseThrow(() -> ResourceNotFoundException.of("User", caller.id()));

        PlantationPlan copy = new PlantationPlan(
                owner,
                availableCopyTitle(caller.id(), source.getTitle()),
                source.getLandAreaInput(), source.getLandAreaUnit(), source.getPurpose(),
                source.getTargetStartYear(), source.getProjectionYears());
        copy.setDescription(source.getDescription());
        copy.setSiteName(source.getSiteName());

        for (PlanItem item : source.getItems()) {
            copy.addSpecies(item.getSpecies(), item.getQuantity());
        }

        PlantationPlan saved = planRepository.save(copy);
        log.info("Plan {} duplicated as {} for user {}", planId, saved.getId(), caller.id());

        return mapper.toDetail(saved, true);
    }

    /**
     * Moves a plan through its lifecycle (FR-PLAN-06).
     *
     * <p>Two rules are applied, and both of them are refusals worth being explicit about:</p>
     * <ul>
     *   <li>the transition must be one {@link PlanStatus} permits — the state machine lives on
     *       the enum, so this method consults it rather than restating it;</li>
     *   <li>a plan may only be completed if it has at least one species and a positive land
     *       area (BR-10). Completing an empty plan would freeze a snapshot full of zeroes and
     *       make a report out of nothing.</li>
     * </ul>
     */
    @Transactional
    public PlanDetailResponse changeStatus(Long planId, PlanStatus target) {
        PlantationPlan plan = planAccessService.requireOwned(planId, true);
        PlanStatus current = plan.getStatus();

        if (current == target) {
            return mapper.toDetail(plan, true);
        }

        if (!current.canTransitionTo(target)) {
            throw new BusinessRuleViolationException("FR-PLAN-06",
                    "A %s plan cannot become %s. Permitted next states: %s."
                            .formatted(current.getLabel().toLowerCase(),
                                    target.getLabel().toLowerCase(),
                                    describe(current.allowedTransitions())));
        }

        if (target == PlanStatus.COMPLETED && !plan.isCalculable()) {
            throw new BusinessRuleViolationException("BR-10",
                    "A plan needs at least one species and a positive land area before it can be "
                            + "completed. Add a species, then try again.");
        }

        plan.setStatus(target);
        PlantationPlan saved = planRepository.save(plan);

        if (target == PlanStatus.COMPLETED) {
            // Freeze the figures and the constants that produced them (FR-CALC-14).
            planEstimateService.writeSnapshot(saved);
        } else if (current == PlanStatus.COMPLETED && target == PlanStatus.DRAFT) {
            planEstimateService.discardSnapshot(planId);
        }

        log.info("Plan {} moved from {} to {}", planId, current, target);
        return mapper.toDetail(saved, true);
    }

    // -------------------------------------------------------------------- composition

    /**
     * Adds a species, or increases its quantity if the plan already contains it (FR-PLAN-03,
     * FR-PLAN-04, BR-08).
     *
     * <p>The increment is the entity's behaviour; what this method adds is the check that the
     * <em>resulting</em> quantity is still within the configured per-item limit, which the
     * entity cannot know.</p>
     */
    @Transactional
    public PlanDetailResponse addItem(Long planId, PlanItemRequest request) {
        PlantationPlan plan = planAccessService.requireEditable(planId, true);
        TreeSpecies species = requireSelectableSpecies(request.speciesId());

        int existingQuantity = plan.findItemForSpecies(species)
                .map(PlanItem::getQuantity)
                .orElse(0);

        if (existingQuantity == 0 && plan.getItems().size() >= properties.plan().maxItemsPerPlan()) {
            throw new BusinessRuleViolationException("BR-14",
                    "A plan may contain at most %d species. Remove one before adding another."
                            .formatted(properties.plan().maxItemsPerPlan()));
        }

        assertQuantityPermitted(existingQuantity + request.quantity());

        plan.addSpecies(species, request.quantity());
        PlantationPlan saved = planRepository.save(plan);

        return mapper.toDetail(saved, true);
    }

    /** Sets an existing item's quantity to an absolute value (FR-PLAN-05). */
    @Transactional
    public PlanDetailResponse updateItemQuantity(Long planId, Long itemId, int quantity) {
        PlantationPlan plan = planAccessService.requireEditable(planId, true);
        assertQuantityPermitted(quantity);

        PlanItem item = plan.findItemById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of(ITEM_RESOURCE, itemId));

        item.setQuantity(quantity);
        PlantationPlan saved = planRepository.save(plan);

        return mapper.toDetail(saved, true);
    }

    /**
     * Removes a species from a plan (FR-PLAN-05).
     *
     * <p>The item is looked up <em>within the plan</em> rather than by id alone. Fetching it
     * from the item repository and trusting its {@code plan_id} would make the ownership check
     * depend on the caller passing a matching pair, which is exactly the kind of assumption
     * that turns into a cross-tenant delete.</p>
     */
    @Transactional
    public PlanDetailResponse removeItem(Long planId, Long itemId) {
        PlantationPlan plan = planAccessService.requireEditable(planId, true);

        PlanItem item = plan.findItemById(itemId)
                .orElseThrow(() -> ResourceNotFoundException.of(ITEM_RESOURCE, itemId));

        plan.removeItem(item);
        PlantationPlan saved = planRepository.save(plan);

        return mapper.toDetail(saved, true);
    }

    // ---------------------------------------------------------------------- internals

    /**
     * Turns a page of plans into summaries, resolving both computed columns in two extra
     * queries rather than one per row.
     */
    private PageResponse<PlanSummary> summarise(Page<PlantationPlan> page) {
        List<Long> planIds = page.getContent().stream().map(PlantationPlan::getId).toList();

        if (planIds.isEmpty()) {
            return PageResponse.from(page.map(plan -> mapper.toSummary(plan, 0, 0, null, null)));
        }

        Map<Long, int[]> composition = new HashMap<>();
        for (Object[] row : planItemRepository.summariseByPlanIds(planIds)) {
            composition.put((Long) row[0], new int[]{
                    ((Number) row[1]).intValue(), ((Number) row[2]).intValue()});
        }

        Map<Long, BigDecimal[]> snapshots = new HashMap<>();
        for (Object[] row : snapshotRepository.summariseByPlanIds(planIds)) {
            snapshots.put((Long) row[0], new BigDecimal[]{
                    (BigDecimal) row[1], (BigDecimal) row[2]});
        }

        return PageResponse.from(page.map(plan -> {
            int[] counts = composition.getOrDefault(plan.getId(), new int[]{0, 0});
            BigDecimal[] frozen = snapshots.get(plan.getId());
            return mapper.toSummary(plan, counts[0], counts[1],
                    frozen == null ? null : frozen[0],
                    frozen == null ? null : frozen[1]);
        }));
    }

    /** A species may be added to a plan only while it is active (FR-SPEC-12). */
    private TreeSpecies requireSelectableSpecies(Long speciesId) {
        TreeSpecies species = speciesRepository.findById(speciesId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tree species", speciesId));

        if (!species.isActive()) {
            throw new BusinessRuleViolationException("FR-SPEC-12",
                    "'%s' has been withdrawn from the catalogue and cannot be added to a plan. "
                            + "Plans that already include it are unaffected."
                            .formatted(species.getCommonName()));
        }

        return species;
    }

    /**
     * Enforces per-owner title uniqueness among live plans.
     *
     * <p>Checked here rather than by a database constraint, because a unique key over
     * {@code (user_id, title)} would collide with soft-deleted rows: a planner who deleted
     * "Phase 1" could never create another plan by that name, for a reason the interface could
     * not explain.</p>
     */
    private void assertTitleAvailable(Long ownerId, String title, Long excludeId) {
        boolean taken = excludeId == null
                ? planRepository.existsByOwnerIdAndTitleIgnoreCaseAndDeletedFalse(ownerId, title)
                : planRepository.existsByOwnerIdAndTitleIgnoreCaseAndIdNotAndDeletedFalse(
                        ownerId, title, excludeId);

        if (taken) {
            throw new DuplicateResourceException("title",
                    "You already have a plan called '%s'. Choose a different title.".formatted(title));
        }
    }

    /** Finds an unused "(copy)" title, so duplicating twice does not fail on uniqueness. */
    private String availableCopyTitle(Long ownerId, String sourceTitle) {
        for (int attempt = 1; attempt <= MAX_COPY_ATTEMPTS; attempt++) {
            String candidate = attempt == 1
                    ? "%s (copy)".formatted(sourceTitle)
                    : "%s (copy %d)".formatted(sourceTitle, attempt);

            // Titles are capped at 150 characters, so a long original needs trimming rather
            // than a database error the planner cannot act on.
            candidate = candidate.length() <= 150 ? candidate : candidate.substring(0, 150);

            if (!planRepository.existsByOwnerIdAndTitleIgnoreCaseAndDeletedFalse(ownerId, candidate)) {
                return candidate;
            }
        }

        throw new BusinessRuleViolationException(
                "There are already too many copies of '%s'. Rename one before duplicating again."
                        .formatted(sourceTitle));
    }

    private void assertProjectionHorizonPermitted(int projectionYears) {
        ApplicationProperties.Plan limits = properties.plan();
        if (projectionYears < limits.minProjectionYears() || projectionYears > limits.maxProjectionYears()) {
            throw new BusinessRuleViolationException("BR-09",
                    "The projection horizon must be between %d and %d years."
                            .formatted(limits.minProjectionYears(), limits.maxProjectionYears()));
        }
    }

    private void assertQuantityPermitted(int quantity) {
        int max = properties.plan().maxQuantityPerItem();
        if (quantity < 1 || quantity > max) {
            throw new BusinessRuleViolationException("BR-14",
                    "Quantity per species must be between 1 and %,d trees.".formatted(max));
        }
    }

    private static String describe(Set<PlanStatus> statuses) {
        return statuses.stream().map(PlanStatus::getLabel).sorted().collect(joining(", "));
    }

    private boolean isOwnedByCaller(PlantationPlan plan) {
        return currentUserProvider.currentUser()
                .map(caller -> caller.owns(plan.getOwner().getId()))
                .orElse(false);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
