package com.urbancanopy.plan.entity;

/**
 * Why a plantation is being undertaken (FR-PLAN-02).
 *
 * <p>Recorded rather than inferred: the purpose is what makes two plans with identical
 * numbers genuinely different proposals, and it is the axis the dashboard groups by. It
 * does not affect any calculation — the system reports what the chosen species will do,
 * not what the planner hoped they would do.</p>
 */
public enum PlantationPurpose {

    AIR_QUALITY_IMPROVEMENT("Air quality improvement",
            "Maximise particulate and gaseous pollutant capture near emission sources."),

    CARBON_OFFSET("Carbon offset",
            "Maximise annual carbon dioxide sequestration."),

    URBAN_COOLING("Urban cooling",
            "Reduce surface and ambient temperature through canopy shade."),

    NOISE_BARRIER("Noise barrier",
            "Dense roadside or boundary planting to attenuate traffic noise."),

    AESTHETIC_LANDSCAPING("Aesthetic landscaping",
            "Ornamental and amenity planting for public spaces."),

    BIODIVERSITY_RESTORATION("Biodiversity restoration",
            "Native, mixed-species planting to support urban wildlife."),

    AVENUE_ROADSIDE("Avenue / roadside",
            "Linear planting along roads and footpaths."),

    CAMPUS_GREENING("Campus greening",
            "Institutional grounds — schools, colleges, offices, hospitals."),

    MIYAWAKI_DENSE_FOREST("Miyawaki dense forest",
            "High-density native planting on a small footprint for rapid canopy closure.");

    private final String label;
    private final String description;

    PlantationPurpose(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }
}
