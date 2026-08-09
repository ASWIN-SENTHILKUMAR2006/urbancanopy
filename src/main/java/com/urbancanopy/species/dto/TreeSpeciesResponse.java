package com.urbancanopy.species.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Full species detail (FR-SPEC-09).
 *
 * <p>Enumerated values are sent as {@link EnumOption} — constant name plus display label —
 * so the browser renders "Fast" without owning a translation table that would drift from
 * the enum.</p>
 */
public record TreeSpeciesResponse(
        Long id,
        String commonName,
        String scientificName,
        String family,
        String localName,
        String description,
        String imageUrl,

        BigDecimal co2SequestrationKgPerYear,
        BigDecimal oxygenProductionKgPerYear,
        BigDecimal pm25RemovalGramsPerYear,
        BigDecimal pm10RemovalGramsPerYear,
        BigDecimal no2RemovalGramsPerYear,
        BigDecimal so2RemovalGramsPerYear,
        BigDecimal coRemovalGramsPerYear,
        BigDecimal totalPollutantRemovalGramsPerYear,

        EnumOption growthRate,
        EnumOption waterRequirement,
        EnumOption climateZone,
        EnumOption soilType,

        BigDecimal canopyRadiusMetres,
        BigDecimal canopyAreaSquareMetres,
        BigDecimal matureHeightMetres,
        Integer lifeSpanYears,
        BigDecimal spacingMetres,
        boolean nativeSpecies,

        String sourceCitation,
        boolean active,

        /** How many live plans use this species — shown to an admin before deactivating. */
        Long usedByPlanCount,

        Instant createdAt,
        Instant updatedAt
) {
}
