package com.urbancanopy.coefficient.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One tunable constant as the administration screen sees it (FR-ADM-04).
 *
 * <p>Carries far more than the value alone, because an editor of scientific constants needs
 * to know what a plausible figure is before typing one: the permitted range, the seeded
 * default, the unit, the literature source, and whether the value has already been changed
 * from its default. A form that showed only a number would invite exactly the implausible
 * edit the range check exists to reject.</p>
 *
 * @param permittedRange human-readable range, e.g. {@code "0.5 to 1"}, for the helper line
 * @param modified       {@code true} when the value differs from {@link #defaultValue} —
 *                       drives the "restore default" affordance (FR-ADM-06)
 */
public record CoefficientResponse(
        Long id,
        String coefficientKey,
        String label,
        BigDecimal value,
        BigDecimal defaultValue,
        BigDecimal minValue,
        BigDecimal maxValue,
        String permittedRange,
        String unit,
        EnumOption category,
        String description,
        String source,
        boolean modified,
        Instant updatedAt,
        String updatedBy
) {
}
