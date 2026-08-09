package com.urbancanopy.plan.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.common.dto.PageResponse;
import com.urbancanopy.plan.dto.EstimateResponse;
import com.urbancanopy.plan.dto.PlanDetailResponse;
import com.urbancanopy.plan.dto.PlanItemQuantityRequest;
import com.urbancanopy.plan.dto.PlanItemRequest;
import com.urbancanopy.plan.dto.PlanStatusRequest;
import com.urbancanopy.plan.dto.PlanSummary;
import com.urbancanopy.plan.dto.PlantationPlanRequest;
import com.urbancanopy.plan.entity.PlanStatus;
import com.urbancanopy.plan.entity.PlantationPurpose;
import com.urbancanopy.plan.service.PlanEstimateService;
import com.urbancanopy.plan.service.PlantationPlanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Plantation plans and their composition (FR-PLAN-01 … FR-PLAN-13).
 *
 * <p>Items are addressed as sub-resources of their plan — {@code /plantation-plans/{id}/items}
 * — because a plan item has no independent existence: it is part of the plan's composition and
 * its ownership is the plan's ownership. A top-level {@code /plan-items/{itemId}} would make
 * that ownership a lookup rather than a fact of the URL.</p>
 *
 * <p>Every mutating item endpoint returns the whole plan rather than the changed item. The
 * editor's totals, the species count and the {@code calculable} flag all move when one quantity
 * changes, so returning the item alone would leave the client to either recompute those or
 * fetch the plan again — one round trip instead of two, and no chance of the two disagreeing.</p>
 */
@RestController
@RequestMapping("/api/v1/plantation-plans")
public class PlantationPlanController {

    private final PlantationPlanService planService;
    private final PlanEstimateService estimateService;

    public PlantationPlanController(PlantationPlanService planService,
                                    PlanEstimateService estimateService) {
        this.planService = planService;
        this.estimateService = estimateService;
    }

    // ------------------------------------------------------------------------ queries

    /** The caller's own plans (FR-PLAN-07). Newest activity first, which is what a list is for. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PlanSummary>>> search(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PlanStatus status,
            @RequestParam(required = false) PlantationPurpose purpose,
            @PageableDefault(sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
                planService.searchOwn(search, status, purpose, pageable)));
    }

    /** Enumerated values for the editor's selects. */
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<Map<String, List<EnumOption>>>> options() {
        return ResponseEntity.ok(ApiResponse.ok(planService.editorOptions()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(planService.findById(id)));
    }

    /**
     * The full calculation result for a saved plan (UC-06).
     *
     * <p>A separate representation from the plan itself, so editing a title does not re-run the
     * engine and reading an estimate does not resend the species list.</p>
     */
    @GetMapping("/{id}/estimate")
    public ResponseEntity<ApiResponse<EstimateResponse>> estimate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(estimateService.estimate(id)));
    }

    // ---------------------------------------------------------------- plan lifecycle

    @PostMapping
    public ResponseEntity<ApiResponse<PlanDetailResponse>> create(
            @Valid @RequestBody PlantationPlanRequest request) {

        PlanDetailResponse created = planService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created,
                "'%s' has been created as a draft.".formatted(created.title())));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> update(
            @PathVariable Long id, @Valid @RequestBody PlantationPlanRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                planService.update(id, request), "Your changes have been saved."));
    }

    /** Soft delete (FR-PLAN-09). The record survives; it leaves every listing and aggregate. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        planService.delete(id);
        return ResponseEntity.ok(ApiResponse.message("The plan has been deleted."));
    }

    /** Clones a plan as a new draft, for exploring an alternative mix (FR-PLAN-08). */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> duplicate(@PathVariable Long id) {
        PlanDetailResponse copy = planService.duplicate(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(copy,
                "Copied as '%s'. Adjust the species mix and compare the two.".formatted(copy.title())));
    }

    /**
     * Moves the plan through its lifecycle (FR-PLAN-06).
     *
     * <p>Completing a plan freezes its figures and the coefficients that produced them, which
     * the confirmation says explicitly — a planner should know that this is the moment the
     * numbers stop moving.</p>
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> changeStatus(
            @PathVariable Long id, @Valid @RequestBody PlanStatusRequest request) {

        PlanDetailResponse updated = planService.changeStatus(id, request.status());

        String message = switch (request.status()) {
            case COMPLETED -> "Plan completed. Its figures are now fixed and a report can be "
                    + "generated.";
            case DRAFT -> "Plan reopened for editing. Its saved figures have been cleared and will "
                    + "be recalculated when you complete it again.";
            case ARCHIVED -> "Plan archived. It stays available for reference but can no longer be "
                    + "edited.";
        };

        return ResponseEntity.ok(ApiResponse.ok(updated, message));
    }

    // ------------------------------------------------------------------- composition

    /** Adds a species, or increases its quantity if already present (FR-PLAN-04, BR-08). */
    @PostMapping("/{id}/items")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> addItem(
            @PathVariable Long id, @Valid @RequestBody PlanItemRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                planService.addItem(id, request), "The species has been added to your plan."));
    }

    @PutMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> updateItemQuantity(
            @PathVariable Long id, @PathVariable Long itemId,
            @Valid @RequestBody PlanItemQuantityRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                planService.updateItemQuantity(id, itemId, request.quantity()),
                "The quantity has been updated."));
    }

    @DeleteMapping("/{id}/items/{itemId}")
    public ResponseEntity<ApiResponse<PlanDetailResponse>> removeItem(
            @PathVariable Long id, @PathVariable Long itemId) {

        return ResponseEntity.ok(ApiResponse.ok(
                planService.removeItem(id, itemId), "The species has been removed from your plan."));
    }
}
