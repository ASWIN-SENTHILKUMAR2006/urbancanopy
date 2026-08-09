package com.urbancanopy.species.dto;

import com.urbancanopy.species.entity.ClimateZone;
import com.urbancanopy.species.entity.GrowthRate;
import com.urbancanopy.species.entity.SoilType;
import com.urbancanopy.species.entity.WaterRequirement;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import java.math.BigDecimal;

/**
 * Payload for creating or updating a tree species (FR-SPEC-01).
 *
 * <p>Validation here mirrors the {@code CHECK} constraints in the schema. That duplication
 * is intentional: the annotations give the user a helpful field-level message, and the
 * database constraint guarantees the rule even against a client that never asks this
 * application.</p>
 *
 * <p>Upper bounds on the environmental rates are set generously — roughly five times the
 * highest value in the seeded catalogue — so a legitimate outlier is accepted while a
 * misplaced decimal point or a units mix-up (grams entered where kilograms were expected)
 * is caught before it silently inflates every plan that uses the species.</p>
 */
public record TreeSpeciesRequest(

        @NotBlank(message = "Common name is required.")
        @Size(max = 120, message = "Common name must be at most 120 characters.")
        String commonName,

        @NotBlank(message = "Scientific name is required.")
        @Size(max = 160, message = "Scientific name must be at most 160 characters.")
        String scientificName,

        @Size(max = 100, message = "Family must be at most 100 characters.")
        String family,

        @Size(max = 120, message = "Local name must be at most 120 characters.")
        String localName,

        @Size(max = 4000, message = "Description must be at most 4000 characters.")
        String description,

        @URL(message = "Image URL must be a valid web address.")
        @Size(max = 500, message = "Image URL must be at most 500 characters.")
        String imageUrl,

        // ---------------------------------------------------- environmental performance

        @NotNull(message = "Annual CO₂ sequestration is required.")
        @PositiveOrZero(message = "CO₂ sequestration cannot be negative.")
        @DecimalMax(value = "500.000", message = "CO₂ sequestration above 500 kg/tree/year is implausible — check the units.")
        BigDecimal co2SequestrationKgPerYear,

        @NotNull(message = "Annual oxygen production is required.")
        @PositiveOrZero(message = "Oxygen production cannot be negative.")
        @DecimalMax(value = "500.000", message = "Oxygen production above 500 kg/tree/year is implausible — check the units.")
        BigDecimal oxygenProductionKgPerYear,

        @NotNull(message = "PM2.5 removal is required.")
        @PositiveOrZero(message = "PM2.5 removal cannot be negative.")
        @DecimalMax(value = "5000.000", message = "PM2.5 removal above 5000 g/tree/year is implausible — check the units.")
        BigDecimal pm25RemovalGramsPerYear,

        @NotNull(message = "PM10 removal is required.")
        @PositiveOrZero(message = "PM10 removal cannot be negative.")
        @DecimalMax(value = "5000.000", message = "PM10 removal above 5000 g/tree/year is implausible — check the units.")
        BigDecimal pm10RemovalGramsPerYear,

        @NotNull(message = "NO₂ removal is required.")
        @PositiveOrZero(message = "NO₂ removal cannot be negative.")
        @DecimalMax(value = "5000.000", message = "NO₂ removal above 5000 g/tree/year is implausible — check the units.")
        BigDecimal no2RemovalGramsPerYear,

        @NotNull(message = "SO₂ removal is required.")
        @PositiveOrZero(message = "SO₂ removal cannot be negative.")
        @DecimalMax(value = "5000.000", message = "SO₂ removal above 5000 g/tree/year is implausible — check the units.")
        BigDecimal so2RemovalGramsPerYear,

        @NotNull(message = "CO removal is required.")
        @PositiveOrZero(message = "CO removal cannot be negative.")
        @DecimalMax(value = "5000.000", message = "CO removal above 5000 g/tree/year is implausible — check the units.")
        BigDecimal coRemovalGramsPerYear,

        // ------------------------------------------------------- horticultural profile

        @NotNull(message = "Growth rate is required.")
        GrowthRate growthRate,

        @NotNull(message = "Water requirement is required.")
        WaterRequirement waterRequirement,

        @NotNull(message = "Climate zone is required.")
        ClimateZone climateZone,

        @NotNull(message = "Soil type is required.")
        SoilType soilType,

        @NotNull(message = "Canopy radius is required.")
        @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "Canopy radius must be greater than zero.")
        @DecimalMax(value = "50.0", message = "Canopy radius above 50 m is implausible.")
        BigDecimal canopyRadiusMetres,

        @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "Mature height must be greater than zero.")
        @DecimalMax(value = "150.0", message = "Mature height above 150 m is implausible.")
        BigDecimal matureHeightMetres,

        @NotNull(message = "Life span is required.")
        @Min(value = 1, message = "Life span must be at least 1 year.")
        @Max(value = 5000, message = "Life span above 5000 years is implausible.")
        Integer lifeSpanYears,

        @NotNull(message = "Recommended spacing is required.")
        @jakarta.validation.constraints.DecimalMin(value = "0.1", message = "Spacing must be greater than zero.")
        @DecimalMax(value = "100.0", message = "Spacing above 100 m is implausible.")
        BigDecimal spacingMetres,

        boolean nativeSpecies,

        // ------------------------------------------------------------------ provenance

        @NotBlank(message = "A literature source is required — every reference value must be traceable.")
        @Size(max = 500, message = "Source citation must be at most 500 characters.")
        String sourceCitation
) {
}
