package com.urbancanopy.plan.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * A plan and its composition, as the editor loads it (FR-PLAN-01, FR-PLAN-05).
 *
 * <p>No environmental figures. The estimate is a separate representation behind
 * {@code /estimate} and {@code /estimates/preview}, for a reason that shows up immediately in
 * the editor: the composition is what the planner is editing, while the estimate is what
 * changes 300 ms after every keystroke. Bundling them would mean a title edit re-runs the
 * engine, and a quantity change re-sends the species list back to the browser that just sent
 * it.</p>
 *
 * <p>{@code landAreaInput} and {@code landAreaUnit} are returned alongside the normalised
 * {@code landAreaSquareMetres} so the form redisplays "1.2 hectares" rather than
 * "12000 m²" — a plan that comes back looking different from how it was entered reads as a
 * bug (BR-07).</p>
 *
 * @param editable        whether the current status permits changes — the interface disables
 *                        its controls from this rather than re-deriving the state machine
 * @param calculable      whether the plan has enough substance to produce an estimate (BR-10)
 * @param allowedStatuses transitions this plan may make right now, so the status menu offers
 *                        only legal moves instead of discovering them by 422
 */
public record PlanDetailResponse(
        Long id,
        String title,
        String description,
        String siteName,
        BigDecimal landAreaInput,
        EnumOption landAreaUnit,
        BigDecimal landAreaSquareMetres,
        EnumOption purpose,
        int targetStartYear,
        int projectionYears,
        EnumOption status,
        boolean editable,
        boolean calculable,
        List<EnumOption> allowedStatuses,
        int speciesCount,
        int totalTrees,
        List<PlanItemResponse> items,
        String ownerName,
        boolean ownedByCaller,
        Instant createdAt,
        Instant updatedAt
) {
}
