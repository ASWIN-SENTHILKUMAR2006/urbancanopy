package com.urbancanopy.plan.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Adds a species to a plan, or increases its quantity if it is already there (FR-PLAN-03,
 * FR-PLAN-04).
 *
 * <p>An "add" that quietly becomes an increment is the behaviour BR-08 requires: a planner
 * who adds 50 Neem to a plan that already has 100 means 150, not an error dialogue and not
 * two rows that a later report would have to reconcile.</p>
 */
public record PlanItemRequest(

        @NotNull(message = "Choose a species.")
        Long speciesId,

        @NotNull(message = "A quantity is required.")
        @Min(value = 1, message = "Quantity must be at least 1 tree.")
        Integer quantity
) {
}
