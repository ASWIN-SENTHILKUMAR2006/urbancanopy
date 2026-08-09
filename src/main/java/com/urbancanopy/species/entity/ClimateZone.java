package com.urbancanopy.species.entity;

/** Broad climate suitability, used to filter the catalogue to a planner's region. */
public enum ClimateZone {

    TROPICAL("Tropical"),
    SUBTROPICAL("Subtropical"),
    ARID("Arid / semi-arid"),
    SEMI_ARID("Semi-arid"),
    TEMPERATE("Temperate"),
    COASTAL("Coastal"),
    ALL_ROUND("Widely adaptable");

    private final String label;

    ClimateZone(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
