package com.urbancanopy.species.dto;

import com.urbancanopy.common.dto.EnumOption;

import java.math.BigDecimal;

/**
 * Card-sized species projection for the catalogue grid and the plan editor's picker.
 *
 * <p>Separate from {@link TreeSpeciesResponse} because a 25-card grid does not need
 * descriptions, citations or five individual pollutant figures. Sending the full record
 * would multiply the payload for data the screen never displays.</p>
 */
public record TreeSpeciesSummary(
        Long id,
        String commonName,
        String scientificName,
        String localName,
        String imageUrl,
        BigDecimal co2SequestrationKgPerYear,
        BigDecimal oxygenProductionKgPerYear,
        BigDecimal totalPollutantRemovalGramsPerYear,
        EnumOption growthRate,
        EnumOption waterRequirement,
        BigDecimal canopyRadiusMetres,
        BigDecimal spacingMetres,
        boolean nativeSpecies,
        boolean active
) {
}
