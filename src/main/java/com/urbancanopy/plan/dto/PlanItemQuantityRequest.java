package com.urbancanopy.plan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Sets an existing plan item's quantity to an absolute value (FR-PLAN-05).
 *
 * <p>Absolute rather than a delta. A quantity field in the editor holds a number the planner
 * can see; sending "+3" from a screen that displays "120" would make the outcome depend on
 * whether an earlier keystroke had already been delivered.</p>
 *
 * <p>Removing a species is {@code DELETE} on the item, not a quantity of zero — one meaning
 * per request, and a plan item with zero trees is not a state worth being able to reach.</p>
 */
public record PlanItemQuantityRequest(

        @NotNull(message = "A quantity is required.")
        @Min(value = 1, message = "Quantity must be at least 1 tree. Remove the species instead.")
        Integer quantity
) {
}
