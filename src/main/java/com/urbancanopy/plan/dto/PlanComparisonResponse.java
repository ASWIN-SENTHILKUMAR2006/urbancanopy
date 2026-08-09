package com.urbancanopy.plan.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;
import java.util.List;

/**
 * A side-by-side comparison of two to four plans (FR-CMP-01 … FR-CMP-03).
 *
 * <p>Shaped as <strong>metrics × plans</strong> rather than plans each carrying their own
 * metrics, because that is the shape the screen and the charts both need. The comparison table
 * is read across a row ("CO₂: 71.42 · 10.24 · 28.60"), and a grouped bar chart wants one
 * series per metric. Sending four self-contained plan objects would force the client to
 * transpose them, and every client would transpose them slightly differently.</p>
 *
 * <p>Each {@link Metric} carries {@code values} positionally aligned with {@link #plans}, so
 * index {@code i} is always the same plan in every row.</p>
 *
 * <p>Normalised metrics are not a nicety. A 2,400-tree plan beats a 480-tree plan on every
 * total, always; without per-tree and per-100 m² rows the screen would consistently reward
 * whichever plan happened to be biggest and mislead the planner about which design is actually
 * better (FR-CMP-03).</p>
 */
public record PlanComparisonResponse(
        List<ComparedPlan> plans,
        List<MetricGroup> groups,
        String disclaimer
) {

    /**
     * Identifying detail for one column of the comparison.
     *
     * @param sufficientData {@code false} when the plan has no species; its cells are then
     *                       zero and the interface should mark the column rather than let it
     *                       look like a genuinely poor result
     */
    public record ComparedPlan(
            Long id,
            String title,
            String siteName,
            EnumOption purpose,
            EnumOption status,
            int totalTrees,
            BigDecimal landAreaSquareMetres,
            int projectionYears,
            boolean sufficientData
    ) {}

    /**
     * A titled block of rows — totals, normalised intensities, or the score profile.
     *
     * @param normalised {@code true} for the intensity block, which the interface renders under
     *                   its own "NORMALISED" divider
     */
    public record MetricGroup(
            String key,
            String label,
            String description,
            boolean normalised,
            List<Metric> metrics
    ) {}

    /**
     * One row: a metric measured across every compared plan.
     *
     * @param values        one value per plan, positionally aligned with {@link #plans};
     *                      {@code null} where the metric does not apply to that plan
     * @param higherIsBetter which direction counts as better, so the client need not hold a
     *                      table of metric semantics to decide where the star goes
     * @param bestPlanId    the winning plan's id, or {@code null} when every value is equal,
     *                      absent, or zero — a "best" among identical figures is noise
     * @param colourToken   design-system token, so a metric is the same colour in the table,
     *                      the chart and the PDF (§4 of the design system)
     */
    public record Metric(
            String key,
            String label,
            String unit,
            boolean higherIsBetter,
            List<BigDecimal> values,
            Long bestPlanId,
            String colourToken
    ) {}
}
