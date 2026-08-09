package com.urbancanopy.species.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Activation state for a species (FR-SPEC-11).
 *
 * <p>A single-field body rather than a query parameter or two separate endpoints
 * ({@code /activate}, {@code /deactivate}): the resource has one state field, so one
 * {@code PATCH} sets it. {@code Boolean} rather than {@code boolean} so that an omitted
 * field is rejected as a validation error instead of silently defaulting to
 * "deactivate".</p>
 */
public record SpeciesStatusRequest(

        @NotNull(message = "Specify whether the species should be active.")
        Boolean active
) {

    /** Unboxed accessor for callers that have passed validation. */
    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
