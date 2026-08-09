package com.urbancanopy.plan.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.plan.dto.PlanComparisonResponse;
import com.urbancanopy.plan.service.PlanComparisonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Side-by-side comparison of two to four plans (FR-CMP-01 … FR-CMP-03).
 *
 * <p>A {@code GET} with the ids in the query string, so a comparison is a bookmarkable,
 * shareable address: {@code /comparisons?planIds=7,12,19}. That matters more here than
 * anywhere else in the API, because a comparison is the thing a planner wants to send to
 * someone.</p>
 */
@RestController
@RequestMapping("/api/v1/comparisons")
public class PlanComparisonController {

    private final PlanComparisonService comparisonService;

    public PlanComparisonController(PlanComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    /**
     * Compares the given plans, in the order supplied.
     *
     * <p>The count is checked in the service against the configured bounds, so an out-of-range
     * selection returns 422 with the permitted range rather than a silently truncated
     * comparison.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PlanComparisonResponse>> compare(
            @RequestParam List<Long> planIds) {

        return ResponseEntity.ok(ApiResponse.ok(comparisonService.compare(planIds)));
    }
}
