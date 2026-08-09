package com.urbancanopy.plan.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A plan as one row of the "my plans" list (FR-PLAN-07).
 *
 * <p>Deliberately free of any computed environmental figure. Producing even a CO₂ total for a
 * list row would mean evaluating every plan on the page — ten calculations, each fetching
 * every item and species, to populate a column the planner is about to click past. The list
 * shows what the plan <em>is</em>; the estimate is one request away.</p>
 *
 * <p>The one exception is {@code annualCo2Kg}, which is present only when the plan has been
 * completed and therefore already has a stored snapshot — no calculation, one join.</p>
 *
 * @param annualCo2Kg from the result snapshot; {@code null} for a draft, which is the
 *                    honest answer rather than a zero the interface would have to explain
 */
public record PlanSummary(
        Long id,
        String title,
        String siteName,
        EnumOption purpose,
        EnumOption status,
        BigDecimal landAreaInput,
        EnumOption landAreaUnit,
        BigDecimal landAreaSquareMetres,
        int targetStartYear,
        int projectionYears,
        int speciesCount,
        int totalTrees,
        BigDecimal annualCo2Kg,
        BigDecimal impactScore,
        Instant createdAt,
        Instant updatedAt
) {
}
