package com.urbancanopy.plan.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;

/**
 * One species and its quantity within a plan, as the editor renders a row.
 *
 * <p>Carries the species' reference values alongside the quantity so the editor's table can
 * show "100 × 22 kg CO₂/yr" without a second request per row. The figures are the species'
 * published per-tree rates — <em>not</em> this item's contribution to the plan, which is
 * survival- and maturity-adjusted and arrives in the estimate's
 * {@code contributions} list. Confusing the two is the easiest way to publish a number that
 * is 15 % too high, so the field names say {@code PerTreeYear} without exception.</p>
 */
public record PlanItemResponse(
        Long id,
        Long speciesId,
        String commonName,
        String scientificName,
        String imageUrl,
        int quantity,
        BigDecimal co2KgPerTreeYear,
        BigDecimal o2KgPerTreeYear,
        BigDecimal totalPollutantGramsPerTreeYear,
        EnumOption growthRate,
        BigDecimal canopyRadiusMetres,
        BigDecimal spacingMetres,
        boolean speciesActive
) {
}
