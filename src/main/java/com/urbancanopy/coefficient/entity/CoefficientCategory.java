package com.urbancanopy.coefficient.entity;

/** Grouping used to organise the coefficient registry in the admin screen. */
public enum CoefficientCategory {

    ADJUSTMENT("Adjustment factors",
            "Convert the number of trees planted into effective mature-equivalent trees."),

    FEASIBILITY("Feasibility thresholds",
            "Boundaries between under-utilised, optimal, tight and overcrowded plantings."),

    EQUIVALENCE("Equivalence factors",
            "Translate carbon and oxygen totals into everyday comparisons."),

    SCORING("Impact scoring",
            "Weights and benchmarks behind the 0–100 environmental impact score."),

    LIMIT("Planning limits",
            "Bounds applied when validating a plantation plan.");

    private final String label;
    private final String description;

    CoefficientCategory(String label, String description) {
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
