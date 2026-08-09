package com.urbancanopy.plan.controller;

import com.urbancanopy.common.dto.ApiResponse;
import com.urbancanopy.plan.dto.EstimateResponse;
import com.urbancanopy.plan.dto.PreviewEstimateRequest;
import com.urbancanopy.plan.service.PlanEstimateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stateless "what-if" evaluation for the live plan editor (FR-PLAN-13).
 *
 * <p>Its own endpoint, outside {@code /plantation-plans}, because what it evaluates is not a
 * plan. There may be no plan yet — the planner is still typing — and there will be no plan
 * afterwards. Modelling this as a sub-resource of something that does not exist would be a
 * fiction the URL then has to maintain.</p>
 *
 * <p>{@code POST} despite being a pure read, because the composition is a structured body
 * rather than something that belongs in a query string, and because a URL long enough to hold
 * fifty species and quantities is a URL that will be truncated by something.</p>
 *
 * <p>Nothing is written and nothing is locked, so a keystroke-driven client can call it as often
 * as its debounce allows without leaving a trace in the database.</p>
 */
@RestController
@RequestMapping("/api/v1/estimates")
public class EstimatePreviewController {

    private final PlanEstimateService estimateService;

    public EstimatePreviewController(PlanEstimateService estimateService) {
        this.estimateService = estimateService;
    }

    /** Evaluates a transient composition and returns a full result without persisting it. */
    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<EstimateResponse>> preview(
            @Valid @RequestBody PreviewEstimateRequest request) {

        // No message: this response updates a panel 300 ms after a keystroke, and a toast on
        // every keystroke would be an assault rather than feedback.
        return ResponseEntity.ok(ApiResponse.ok(estimateService.preview(request)));
    }
}
