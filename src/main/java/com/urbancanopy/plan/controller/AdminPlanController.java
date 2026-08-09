package com.urbancanopy.plan.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.plan.dto.PlanDetailResponse;
import com.urbancanopy.plan.dto.PlanSummary;
import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.service.PlantationPlanService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of every planner's plans, for support and moderation (FR-PLAN-11).
 *
 * <p><strong>Read-only, deliberately and completely.</strong> There is no update, no delete and
 * no status change here, and their absence is the design: a support role that can silently alter
 * a planner's figures is a liability, and nothing in the requirements asks for one. The
 * ownership check in {@code PlanAccessService.requireOwned} refuses administrators for the same
 * reason, so even a future controller could not open that door by accident.</p>
 *
 * <p>Each detail read leaves an {@code ADMIN_VIEWED_PLAN} entry in the audit trail. The record
 * is what makes support access accountable rather than merely available.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/plantation-plans")
public class AdminPlanController {

    private final PlantationPlanService planService;

    public AdminPlanController(PlantationPlanService planService) {
        this.planService = planService;
    }

    /** Every live plan across all owners. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PlanSummary>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PlanStatus status,
            @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(planService.searchAll(search, status, pageable)));
    }

    /**
     * One plan in full.
     *
     * <p>The same service method the owner's endpoint uses; the authorisation seam decides what
     * an administrator may see and records that they saw it. The response's
     * {@code ownedByCaller} flag will be {@code false}, which is how the interface knows to
     * render without edit controls.</p>
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(planService.findById(id)));
    }
}
