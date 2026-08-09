package com.urbancanopy.plan.service;

import com.urbancanopy.audit.entity.AuditEventType;
import com.urbancanopy.audit.service.AuditService;
import com.urbancanopy.common.exception.ForbiddenOperationException;
import com.urbancanopy.common.exception.ResourceNotFoundException;
import com.urbancanopy.common.security.AuthenticatedUser;
import com.urbancanopy.common.security.CurrentUserProvider;
import com.urbancanopy.plan.entity.PlantationPlan;
import com.urbancanopy.plan.repository.PlantationPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads a plan and decides, once, who is allowed to do what with it (FR-PLAN-10, BR-06).
 *
 * <p>Extracted into its own service because ownership is the rule most likely to be enforced
 * inconsistently: there are three plan services and a dozen endpoints, and a single method
 * that forgets the check is a data leak, not a bug. Every path into a plan goes through one of
 * the two methods here.</p>
 *
 * <h2>Read and write are not the same question</h2>
 * <p>An administrator may <strong>read</strong> any plan, for support and moderation, and that
 * access is recorded in the audit trail (FR-PLAN-11). An administrator may <strong>not
 * write</strong> to someone else's plan: nothing in the requirements asks for it, and a
 * support role that can silently alter a planner's figures is a liability rather than a
 * feature. {@link #requireOwned} therefore refuses even an administrator.</p>
 *
 * <h2>403, not 404</h2>
 * <p>Access to another user's plan is refused with 403 and the plan is not pretended to be
 * absent. Masking authorisation failures as "not found" hides our own bugs from us just as
 * effectively as it hides data from an attacker — and this application's threat model does not
 * include an adversary who learns something meaningful from the existence of plan 42.</p>
 */
@Service
public class PlanAccessService {

    private static final String RESOURCE = "Plantation plan";

    private final PlantationPlanRepository planRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    public PlanAccessService(PlantationPlanRepository planRepository,
                             CurrentUserProvider currentUserProvider,
                             AuditService auditService) {
        this.planRepository = planRepository;
        this.currentUserProvider = currentUserProvider;
        this.auditService = auditService;
    }

    /**
     * Loads a plan the caller may read — its owner, or an administrator.
     *
     * <p>An administrator reading a plan they do not own leaves an audit entry, every time.
     * The record is the thing that makes the access legitimate rather than merely possible.</p>
     *
     * @param detailed whether to fetch items and species in the same query; pass {@code true}
     *                 whenever the caller will touch the composition, so the fetch join
     *                 happens instead of an N+1
     */
    @Transactional(readOnly = true)
    public PlantationPlan requireReadable(Long planId, boolean detailed) {
        PlantationPlan plan = load(planId, detailed);
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();

        if (!caller.canAccessDataOf(plan.getOwner().getId())) {
            throw new ForbiddenOperationException(
                    "You do not have permission to access this plantation plan.");
        }

        if (!caller.owns(plan.getOwner().getId())) {
            auditService.record(AuditEventType.ADMIN_VIEWED_PLAN, "PlantationPlan", planId,
                    "Administrator viewed plan '%s' owned by user %d"
                            .formatted(plan.getTitle(), plan.getOwner().getId()));
        }

        return plan;
    }

    /**
     * Loads a plan the caller may modify — its owner, and no one else.
     *
     * @throws ForbiddenOperationException when the caller does not own the plan, including
     *                                     when the caller is an administrator
     */
    @Transactional(readOnly = true)
    public PlantationPlan requireOwned(Long planId, boolean detailed) {
        PlantationPlan plan = load(planId, detailed);
        AuthenticatedUser caller = currentUserProvider.requireCurrentUser();

        if (!caller.owns(plan.getOwner().getId())) {
            throw new ForbiddenOperationException(
                    "Only the planner who created this plan can change it.");
        }

        return plan;
    }

    /**
     * Loads a plan the caller may modify, and refuses if its status has closed it to changes.
     *
     * @throws com.urbancanopy.common.exception.ForbiddenOperationException when the plan is
     *         archived — the transition back to {@code COMPLETED} is the way to reopen it,
     *         which keeps "why did my edit apply to an archived plan?" from ever arising
     */
    @Transactional(readOnly = true)
    public PlantationPlan requireEditable(Long planId, boolean detailed) {
        PlantationPlan plan = requireOwned(planId, detailed);

        if (!plan.isEditable()) {
            throw new ForbiddenOperationException(
                    "This plan is %s and cannot be changed. Move it back to Completed first."
                            .formatted(plan.getStatus().getLabel().toLowerCase()));
        }

        return plan;
    }

    // ---------------------------------------------------------------------- internals

    private PlantationPlan load(Long planId, boolean detailed) {
        return (detailed
                ? planRepository.findDetailedById(planId)
                : planRepository.findByIdAndDeletedFalse(planId))
                .orElseThrow(() -> ResourceNotFoundException.of(RESOURCE, planId));
    }
}
