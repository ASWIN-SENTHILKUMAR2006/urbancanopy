package com.urbancanopy.plan.dto;

import com.urbancanopy.plan.entity.PlanStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Requests a lifecycle transition (FR-PLAN-06).
 *
 * <p>The target state is named rather than the action ({@code /complete}, {@code /archive}).
 * The permitted transitions are declared on {@link PlanStatus} itself, so one endpoint
 * consults one state machine; an action-per-transition surface would spread that machine
 * across as many endpoints as there are edges.</p>
 */
public record PlanStatusRequest(

        @NotNull(message = "A target status is required.")
        PlanStatus status
) {
}
