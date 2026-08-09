package com.urbancanopy.coefficient.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * A new value for one coefficient (FR-ADM-05).
 *
 * <p>Only the value is accepted. The key, label, category, range and source describe what
 * the constant <em>is</em> and are seeded by migration; letting an HTTP request rewrite them
 * would allow the range that guards the value to be widened in the same call that supplies
 * an implausible value.</p>
 *
 * <p>The plausibility bound itself is not expressed here as an annotation, because it
 * differs per coefficient: a survival rate must lie in 0–1 while a car-emissions factor
 * does not. The check is made in the service against the row's own
 * {@code minValue}/{@code maxValue} (BR-19).</p>
 */
public record CoefficientUpdateRequest(

        @NotNull(message = "A value is required.")
        BigDecimal value
) {
}
