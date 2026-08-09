package com.urbancanopy.plan.dto;

import com.urbancanopy.calculation.model.CalculationResult;
import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A calculation result together with the plan it describes.
 *
 * <p>The engine's {@link CalculationResult} is embedded rather than copied field by field.
 * That is a considered exception to "never return a domain type across the web boundary", and
 * it holds because {@code CalculationResult} is not a domain <em>entity</em>: it is an
 * immutable value object with no identity, no persistence, no lazy association and no
 * behaviour — the same qualities a response DTO is supposed to have. Restating its fourteen
 * nested records here would produce two hundred lines of mechanical copying whose only
 * function would be to drift out of step with the engine.</p>
 *
 * <p>What this record adds is the context the engine deliberately does not know: which plan
 * this was, what state it is in, and — the part that matters for trust — whether the figures
 * were produced from the live coefficient registry or from a frozen snapshot.</p>
 *
 * @param planId              {@code null} for a stateless preview, which belongs to no plan
 * @param fromSnapshot        {@code true} when the figures were computed with the coefficient
 *                            set frozen at completion rather than the current one. A
 *                            {@code COMPLETED} plan must keep reporting what its report said
 *                            (FR-CALC-14), and a reader is entitled to know which of the two
 *                            they are looking at
 * @param snapshotComputedAt  when that snapshot was taken; {@code null} when there is none
 * @param engineVersion       the implementation that produced these figures, so a later
 *                            change to a formula is traceable
 * @param result              the figures themselves, including the mandatory disclaimer and
 *                            the calculation trace (FR-CALC-08, FR-CALC-15)
 */
public record EstimateResponse(
        Long planId,
        String planTitle,
        String siteName,
        EnumOption purpose,
        EnumOption status,
        BigDecimal landAreaInput,
        EnumOption landAreaUnit,
        BigDecimal landAreaSquareMetres,
        int targetStartYear,
        int projectionYears,
        boolean fromSnapshot,
        Instant snapshotComputedAt,
        String engineVersion,
        Instant computedAt,
        CalculationResult result
) {
}
