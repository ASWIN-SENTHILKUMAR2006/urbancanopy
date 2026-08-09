package com.urbancanopy.dashboard.dto;

import com.urbancanopy.plan.dto.PlanSummary;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything the dashboard's headline area needs, in one response (FR-DASH-01, FR-DASH-06,
 * FR-DASH-07).
 *
 * <p>One request rather than seven. The KPI row is read as a single unit — a screen that
 * populated its cards one at a time would flicker through seven layout shifts, and each card
 * would need its own loading and error state for no benefit to the reader.</p>
 *
 * @param admin system-wide figures, present only when the caller is an administrator and
 *              {@code null} otherwise (FR-DASH-08). Absent rather than empty, so the client
 *              renders the admin block on the field's presence instead of on a role check it
 *              would have to duplicate
 */
public record DashboardSummary(
        Kpis kpis,
        List<PlanSummary> recentPlans,
        AdminKpis admin,
        String disclaimer
) {

    /**
     * The planner's own totals, aggregated across every live plan.
     *
     * <p>Every plan is evaluated against the <strong>current</strong> coefficient registry,
     * including completed ones that carry a frozen snapshot. That is deliberate, and it is the
     * same reasoning the comparison screen uses: a total summed from figures computed under
     * different constants is not a total of anything. The individual plan's results screen
     * still shows its frozen figures, and the disclaimer on this page says which basis is in
     * use.</p>
     *
     * @param averageImpactScore mean across plans that have any species; {@code null} when
     *                           none do, because an average of nothing is not zero
     */
    public record Kpis(
            long totalPlans,
            long completedPlans,
            long draftPlans,
            long totalTrees,
            BigDecimal annualCo2Tonnes,
            BigDecimal annualO2Kg,
            BigDecimal annualPollutantKg,
            BigDecimal canopyAreaSquareMetres,
            BigDecimal averageImpactScore
    ) {}

    /** System-wide figures for an administrator (FR-DASH-07). */
    public record AdminKpis(
            long totalUsers,
            long activeUsers,
            long totalSpecies,
            long activeSpecies,
            long totalPlans,
            List<TopSpecies> topSpecies
    ) {}

    /** One row of the most-used-species table (FR-DASH-07). */
    public record TopSpecies(
            Long speciesId,
            String commonName,
            long planCount,
            long totalTrees
    ) {}
}
