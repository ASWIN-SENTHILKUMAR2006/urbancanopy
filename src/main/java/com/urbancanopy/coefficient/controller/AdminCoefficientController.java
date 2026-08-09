package com.urbancanopy.coefficient.controller;

import com.urbancanopy.coefficient.dto.CoefficientResponse;
import com.urbancanopy.coefficient.dto.CoefficientUpdateRequest;
import com.urbancanopy.coefficient.service.CoefficientAdminService;
import com.urbancanopy.common.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The coefficient registry as an editable surface (FR-ADM-04 … FR-ADM-06).
 *
 * <p>This is the endpoint that makes "never hardcode a scientific value" (BR-15) a property
 * of the running system rather than a claim in a document: revising a survival rate here
 * changes every subsequent calculation without a redeploy, and a plan already marked
 * {@code COMPLETED} keeps its original figures because its snapshot froze the constants that
 * produced them (FR-CALC-14).</p>
 *
 * <p>No {@code POST} to create and no {@code DELETE}. The registry's membership is defined by
 * migration and verified at startup — a missing key stops the application from starting — so
 * allowing rows to be added or removed over HTTP would only create a way to break that
 * check.</p>
 */
@RestController
@RequestMapping("/api/v1/admin/coefficients")
public class AdminCoefficientController {

    private final CoefficientAdminService coefficientAdminService;

    public AdminCoefficientController(CoefficientAdminService coefficientAdminService) {
        this.coefficientAdminService = coefficientAdminService;
    }

    /** The whole registry, ordered by category then key. Around twenty rows; no pagination. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CoefficientResponse>>> findAll() {
        return ResponseEntity.ok(ApiResponse.ok(coefficientAdminService.findAll()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CoefficientResponse>> findById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(coefficientAdminService.findById(id)));
    }

    /** Revises one value (FR-ADM-05). Out-of-range values are refused with 422. */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CoefficientResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CoefficientUpdateRequest request) {

        CoefficientResponse updated = coefficientAdminService.updateValue(id, request.value());
        return ResponseEntity.ok(ApiResponse.ok(updated,
                "'%s' is now %s. New estimates use this value immediately."
                        .formatted(updated.label(), updated.value().stripTrailingZeros().toPlainString())));
    }

    /**
     * Restores one value to its seeded default (FR-ADM-06).
     *
     * <p>{@code POST} to a sub-resource rather than {@code DELETE} on the coefficient: nothing
     * is removed, an action is invoked.</p>
     */
    @PostMapping("/{id}/restore-default")
    public ResponseEntity<ApiResponse<CoefficientResponse>> restoreDefault(@PathVariable Long id) {
        CoefficientResponse restored = coefficientAdminService.restoreDefault(id);
        return ResponseEntity.ok(ApiResponse.ok(restored,
                "'%s' has been restored to its published default of %s."
                        .formatted(restored.label(),
                                restored.defaultValue().stripTrailingZeros().toPlainString())));
    }
}
