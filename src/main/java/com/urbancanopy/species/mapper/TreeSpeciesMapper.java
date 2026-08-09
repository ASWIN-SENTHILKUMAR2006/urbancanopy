package com.urbancanopy.species.mapper;

import com.urbancanopy.calculation.model.CalculationInput;
import com.urbancanopy.common.dto.EnumOption;
import com.urbancanopy.config.ApplicationProperties;
import com.urbancanopy.plan.entity.PlanItem;
import com.urbancanopy.species.dto.TreeSpeciesRequest;
import com.urbancanopy.species.dto.TreeSpeciesResponse;
import com.urbancanopy.species.dto.TreeSpeciesSummary;
import com.urbancanopy.species.entity.TreeSpecies;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Translates between {@link TreeSpecies} and its DTOs, and into the calculation engine's
 * input type.
 *
 * <p>Pure translation only. A mapper that makes a decision is a service in disguise, and
 * business rules hidden in mappers are the hardest kind to find.</p>
 */
@Component
public class TreeSpeciesMapper {

    private static final BigDecimal PI = new BigDecimal("3.14159265358979323846");

    private final ApplicationProperties properties;

    public TreeSpeciesMapper(ApplicationProperties properties) {
        this.properties = properties;
    }

    // ------------------------------------------------------------------ entity → DTO

    public TreeSpeciesResponse toResponse(TreeSpecies species, Long usedByPlanCount) {
        return new TreeSpeciesResponse(
                species.getId(),
                species.getCommonName(),
                species.getScientificName(),
                species.getFamily(),
                species.getLocalName(),
                species.getDescription(),
                resolveImageUrl(species),
                species.getCo2SequestrationKgPerYear(),
                species.getOxygenProductionKgPerYear(),
                species.getPm25RemovalGramsPerYear(),
                species.getPm10RemovalGramsPerYear(),
                species.getNo2RemovalGramsPerYear(),
                species.getSo2RemovalGramsPerYear(),
                species.getCoRemovalGramsPerYear(),
                species.getTotalPollutantRemovalGramsPerYear(),
                new EnumOption(species.getGrowthRate().name(), species.getGrowthRate().getLabel(), null),
                new EnumOption(species.getWaterRequirement().name(),
                        species.getWaterRequirement().getLabel(),
                        species.getWaterRequirement().getGuidance()),
                new EnumOption(species.getClimateZone().name(), species.getClimateZone().getLabel(), null),
                new EnumOption(species.getSoilType().name(), species.getSoilType().getLabel(), null),
                species.getCanopyRadiusMetres(),
                canopyArea(species.getCanopyRadiusMetres()),
                species.getMatureHeightMetres(),
                species.getLifeSpanYears(),
                species.getSpacingMetres(),
                species.isNativeSpecies(),
                species.getSourceCitation(),
                species.isActive(),
                usedByPlanCount,
                species.getCreatedAt(),
                species.getUpdatedAt());
    }

    public TreeSpeciesSummary toSummary(TreeSpecies species) {
        return new TreeSpeciesSummary(
                species.getId(),
                species.getCommonName(),
                species.getScientificName(),
                species.getLocalName(),
                resolveImageUrl(species),
                species.getCo2SequestrationKgPerYear(),
                species.getOxygenProductionKgPerYear(),
                species.getTotalPollutantRemovalGramsPerYear(),
                new EnumOption(species.getGrowthRate().name(), species.getGrowthRate().getLabel(), null),
                new EnumOption(species.getWaterRequirement().name(),
                        species.getWaterRequirement().getLabel(), null),
                species.getCanopyRadiusMetres(),
                species.getSpacingMetres(),
                species.isNativeSpecies(),
                species.isActive());
    }

    // ------------------------------------------------------------------ DTO → entity

    /** Applies a request onto an entity. Used for both create and update. */
    public void apply(TreeSpeciesRequest request, TreeSpecies target) {
        target.setCommonName(request.commonName().trim());
        target.setScientificName(request.scientificName().trim());
        target.setFamily(trimToNull(request.family()));
        target.setLocalName(trimToNull(request.localName()));
        target.setDescription(trimToNull(request.description()));
        target.setImageUrl(trimToNull(request.imageUrl()));

        target.setCo2SequestrationKgPerYear(request.co2SequestrationKgPerYear());
        target.setOxygenProductionKgPerYear(request.oxygenProductionKgPerYear());
        target.setPm25RemovalGramsPerYear(request.pm25RemovalGramsPerYear());
        target.setPm10RemovalGramsPerYear(request.pm10RemovalGramsPerYear());
        target.setNo2RemovalGramsPerYear(request.no2RemovalGramsPerYear());
        target.setSo2RemovalGramsPerYear(request.so2RemovalGramsPerYear());
        target.setCoRemovalGramsPerYear(request.coRemovalGramsPerYear());

        target.setGrowthRate(request.growthRate());
        target.setWaterRequirement(request.waterRequirement());
        target.setClimateZone(request.climateZone());
        target.setSoilType(request.soilType());

        target.setCanopyRadiusMetres(request.canopyRadiusMetres());
        target.setMatureHeightMetres(request.matureHeightMetres());
        target.setLifeSpanYears(request.lifeSpanYears());
        target.setSpacingMetres(request.spacingMetres());
        target.setNativeSpecies(request.nativeSpecies());

        target.setSourceCitation(request.sourceCitation().trim());
    }

    // ----------------------------------------------------------- entity → engine input

    /**
     * Copies a plan item's species values into the calculation engine's input type.
     *
     * <p>A copy, not a reference: the engine must not be able to lazily load anything, and
     * must behave identically whether or not a transaction is open (FR-CALC-12).</p>
     */
    public CalculationInput.SpeciesInput toCalculationInput(PlanItem item) {
        TreeSpecies species = item.getSpecies();
        return new CalculationInput.SpeciesInput(
                species.getId(),
                species.getCommonName(),
                species.getScientificName(),
                item.getQuantity(),
                species.getCo2SequestrationKgPerYear(),
                species.getOxygenProductionKgPerYear(),
                species.getPm25RemovalGramsPerYear(),
                species.getPm10RemovalGramsPerYear(),
                species.getNo2RemovalGramsPerYear(),
                species.getSo2RemovalGramsPerYear(),
                species.getCoRemovalGramsPerYear(),
                species.getGrowthRate(),
                species.getCanopyRadiusMetres(),
                species.getSpacingMetres(),
                species.getSourceCitation());
    }

    /** As above, for a transient quantity that is not yet a persisted plan item. */
    public CalculationInput.SpeciesInput toCalculationInput(TreeSpecies species, int quantity) {
        return new CalculationInput.SpeciesInput(
                species.getId(),
                species.getCommonName(),
                species.getScientificName(),
                quantity,
                species.getCo2SequestrationKgPerYear(),
                species.getOxygenProductionKgPerYear(),
                species.getPm25RemovalGramsPerYear(),
                species.getPm10RemovalGramsPerYear(),
                species.getNo2RemovalGramsPerYear(),
                species.getSo2RemovalGramsPerYear(),
                species.getCoRemovalGramsPerYear(),
                species.getGrowthRate(),
                species.getCanopyRadiusMetres(),
                species.getSpacingMetres(),
                species.getSourceCitation());
    }

    // ----------------------------------------------------------------------- helpers

    /**
     * Prefers an uploaded file over an external URL, and returns {@code null} when neither
     * exists so the interface can render its own placeholder rather than a broken image.
     *
     * <p>Public because the plan mapper needs the same URL for its species rows. Resolving it
     * a second time there would mean two places decide how an uploaded path becomes a public
     * URL, and the day the upload path changes only one of them would be corrected.</p>
     */
    public String resolveImageUrl(TreeSpecies species) {
        if (StringUtils.hasText(species.getImagePath())) {
            return properties.upload().publicPath() + species.getImagePath();
        }
        return StringUtils.hasText(species.getImageUrl()) ? species.getImageUrl() : null;
    }

    private BigDecimal canopyArea(BigDecimal radius) {
        if (radius == null) {
            return BigDecimal.ZERO;
        }
        return PI.multiply(radius.multiply(radius)).setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
