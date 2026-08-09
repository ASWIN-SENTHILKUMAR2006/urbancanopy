package com.urbancanopy.plan.dto;

import com.urbancanopy.plan.entity.LandAreaUnit;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

/**
 * A transient composition to be evaluated without being saved (FR-PLAN-13).
 *
 * <p>This is the payload behind {@code POST /api/v1/estimates/preview}, and the reason that
 * endpoint exists. The plan editor shows a live estimate as the planner types; the
 * alternative to a stateless evaluation is writing a row on every keystroke, which would fill
 * the database with half-built plans and make "undo" mean "delete".</p>
 *
 * <p>It carries no plan id and no title, because there may not be a plan yet. What it carries
 * is exactly what the calculation engine needs — the engine's purity (FR-CALC-12) made
 * visible at the API surface.</p>
 *
 * <p>{@code items} may be empty: an editor with no species yet asks for an estimate too, and
 * gets a well-formed "insufficient data" result rather than a validation error.</p>
 */
public record PreviewEstimateRequest(

        @NotNull(message = "Available land area is required.")
        @DecimalMin(value = "0", message = "Land area cannot be negative.")
        @DecimalMax(value = "1000000000", message = "Land area is implausibly large — check the unit.")
        BigDecimal landAreaInput,

        @NotNull(message = "Choose the unit the land area is expressed in.")
        LandAreaUnit landAreaUnit,

        @NotNull(message = "A projection horizon is required.")
        @Min(value = 1, message = "The projection horizon must be at least 1 year.")
        @Max(value = 200, message = "The projection horizon is implausibly long.")
        Integer projectionYears,

        @Min(value = 1900, message = "Target start year must be 1900 or later.")
        @Max(value = 2200, message = "Target start year must be 2200 or earlier.")
        Integer targetStartYear,

        @NotNull(message = "An items array is required; it may be empty.")
        List<@Valid PreviewItem> items
) {

    /**
     * One species and quantity in a preview.
     *
     * <p>Separate from {@link PlanItemRequest} despite the identical fields: that type
     * addresses a persistent sub-resource and its quantity is bounded by the per-item
     * planning limit, while this one is a line in a hypothetical. Merging them would couple
     * the live editor's payload to the plan-item API's future.</p>
     */
    public record PreviewItem(

            @NotNull(message = "Each item must name a species.")
            Long speciesId,

            @NotNull(message = "Each item must have a quantity.")
            @Min(value = 1, message = "Quantity must be at least 1 tree.")
            Integer quantity
    ) {}
}
