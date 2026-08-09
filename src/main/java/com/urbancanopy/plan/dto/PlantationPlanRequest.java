package com.urbancanopy.plan.dto;

import com.urbancanopy.plan.entity.LandAreaUnit;
import com.urbancanopy.plan.entity.PlantationPurpose;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Payload for creating or updating a plan's header (FR-PLAN-01, FR-PLAN-12).
 *
 * <p>Items are <strong>not</strong> part of this payload. A plan's composition is edited
 * through {@code /items} sub-resources, so changing a title cannot silently discard a
 * species list, and adding a tree does not require resubmitting the whole plan.</p>
 *
 * <p>Land area arrives as the planner typed it, paired with its unit; normalisation to m²
 * happens in the entity (BR-07). Accepting a pre-normalised figure instead would put a unit
 * conversion in the browser, where it could disagree with the server's.</p>
 *
 * <p>The bounds here are structural sanity checks. The <em>configured</em> limits — maximum
 * projection horizon, maximum items — are enforced in the service against
 * {@code app.plan.*}, because they are operational policy rather than a property of the
 * request format.</p>
 */
public record PlantationPlanRequest(

        @NotBlank(message = "A plan title is required.")
        @Size(max = 150, message = "Title must be at most 150 characters.")
        String title,

        @Size(max = 4000, message = "Description must be at most 4000 characters.")
        String description,

        @Size(max = 180, message = "Site name must be at most 180 characters.")
        String siteName,

        @NotNull(message = "Available land area is required.")
        @DecimalMin(value = "0.0001", message = "Land area must be greater than zero.")
        @DecimalMax(value = "1000000000", message = "Land area is implausibly large — check the unit.")
        BigDecimal landAreaInput,

        @NotNull(message = "Choose the unit the land area is expressed in.")
        LandAreaUnit landAreaUnit,

        @NotNull(message = "A plantation purpose is required.")
        PlantationPurpose purpose,

        @NotNull(message = "A target start year is required.")
        @Min(value = 1900, message = "Target start year must be 1900 or later.")
        @Max(value = 2200, message = "Target start year must be 2200 or earlier.")
        Integer targetStartYear,

        @NotNull(message = "A projection horizon is required.")
        @Min(value = 1, message = "The projection horizon must be at least 1 year.")
        @Max(value = 200, message = "The projection horizon is implausibly long.")
        Integer projectionYears
) {
}
