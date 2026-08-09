package com.urbancanopy.species.entity;

/**
 * Irrigation demand once established — a practical planning constraint, since a
 * high-demand species on an unirrigated site will not survive to deliver the estimate.
 */
public enum WaterRequirement {

    LOW("Low", "Drought tolerant once established; rainfall is usually sufficient."),
    MODERATE("Moderate", "Needs supplementary irrigation through the dry season."),
    HIGH("High", "Requires regular irrigation; suited to sites with assured water.");

    private final String label;
    private final String guidance;

    WaterRequirement(String label, String guidance) {
        this.label = label;
        this.guidance = guidance;
    }

    public String getLabel() {
        return label;
    }

    public String getGuidance() {
        return guidance;
    }
}
